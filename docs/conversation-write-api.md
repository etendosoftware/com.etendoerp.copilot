# Conversation write API (no agent run)

`POST /sws/copilot/question` creates and fills `ETCOP_CONVERSATION` / `ETCOP_MESSAGE` as a side effect
of running the legacy agent (`TrackingUtil`). Clients that run their own agent loop (for example the
Schema Forge `ai-bff`) need to persist a chat into the same tables **without** running an agent. These
two endpoints do that. Reads, rename, archive, restore and delete keep using the existing
`/conversations`, `/conversationMessages`, `/archivedConversations`, `/renameConversation`,
`/deleteConversation`, `/restoreConversation`, `/permanentDeleteConversation` and
`/generateTitleConversation` endpoints.

Both are routed in `RestService.routePostRequest` to `ConversationUtils` (delegating to `ConversationWriteUtils`), run in admin mode like their
siblings, and report errors the same way (`400` with the message as the error text).

## `POST /sws/copilot/createConversation`

Request body (all fields optional):

| Field | Type | Notes |
|---|---|---|
| `title` | string | max 255 chars |
| `app_id` | string | `ETCOP_APP` id or name; unknown app is an error. Omit for an app-less conversation |
| `external_id` | string | max 255 chars, client-chosen stable id. Generated (UUID) when omitted |

Response: `{"success": true, "conversation_id": "<external id>", "created": true}`

* The owner is the calling user; client and organization come from the session.
* `external_id` is unique across the whole table. Re-sending one the caller already owns is
  idempotent (`created: false`, nothing is modified). One owned by another user is rejected with
  `external_id is not available`.
* The returned `conversation_id` is the external id, which is what the read endpoints and
  `/question` use as the conversation id.

## `POST /sws/copilot/appendConversationMessages`

Request body:

```json
{
  "conversation_id": "<external id or primary key>",
  "messages": [
    { "role": "user", "text": "How many invoices are overdue?", "external_id": "turn-1-user" },
    { "role": "assistant", "text": "There are 3.", "metadata": {"any": "json"}, "external_id": "turn-1-assistant" }
  ]
}
```

| Field | Rules |
|---|---|
| `conversation_id` | required; must exist, be active and be owned by the caller |
| `messages` | required, 1 to 100 entries, stored in array order |
| `messages[].role` | `user` or `assistant` (case-insensitive); stored as `USER` / `ASSISTANT` |
| `messages[].text` | required, not blank |
| `messages[].metadata` | optional JSON object, stored as JSON text in `METADATA` |
| `messages[].external_id` | optional, max 255 chars; makes the message idempotent |

Response:

```json
{
  "success": true,
  "conversation_id": "<external id>",
  "saved": 2,
  "skipped": 0,
  "messages": [ { "external_id": "turn-1-user", "lineno": 10, "duplicate": false }, ... ]
}
```

* `LINENO` continues after the current maximum in steps of 10 (same numbering as `TrackingUtil`), and
  `LAST_MSG` is refreshed when at least one message was saved.
* A message whose `external_id` is already stored in that conversation (or repeated inside the same
  call) is skipped and reported with `duplicate: true`, so retrying a finished turn never duplicates it.
* The whole batch is validated before anything is written: one bad message rejects the call and saves
  nothing.
* A conversation that does not exist and one owned by someone else produce the same
  `Conversation not found` error.

## Conversation list and `app_id`

`GET /conversations` and `GET /archivedConversations` take an optional `app_id`:

* **With `app_id`** (legacy clients): unchanged. The current user's conversations of that app, newest
  `LAST_MSG` first. An unknown app is an error.
* **Without `app_id`** (absent or blank): the current user's conversations with **no app**
  (`ETCOP_APP_ID IS NULL`), same JSON shape and ordering. Conversations created through
  `createConversation` without `app_id` are listed here. The list is always filtered by the calling
  user, so another user's app-less conversations are never returned.

## Cookie sessions: `/sws/agent-chat/*`

`/sws/copilot/*` only accepts a bearer JWT, so the Etendo Go SPA (cookie session) cannot call the
endpoints above. `com.etendoerp.go` serves the same operations at `/sws/agent-chat/*`
(`AgentChatConversationsServlet`, see its `docs/agent-chat-api.md`), calling the public,
owner-checked methods of `ConversationWriteUtils`: `createConversation`, `appendMessages`,
`getOwnedConversationMessages`, `renameOwnedConversation`, `setOwnedConversationActive`,
`deleteOwnedConversation`. Unlike the legacy by-id handlers, those enforce that the conversation
belongs to the current user and report a foreign conversation as `Conversation not found`.

## Tests

`ConversationWriteEndpointsTest` (plain Mockito, no database) covers the two write handlers and the `ConversationWriteUtils` operations;
`ConversationUtilsTest` covers the list endpoints with and without `app_id`; `RestServiceRoutingTest`
covers the routes.
