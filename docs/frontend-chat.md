# Front-end: Chat UI and the Classic bridge

## Pieces

| Path | What it is |
|---|---|
| `web/com.etendoerp.copilot/js/copilotToolbarButton.js` | SmartClient toolbar button + `isc.Window` + iframe host (428 lines, all imperative DOM) |
| `web/com.etendoerp.copilot/js/assistantAutoRegistration.js` | registers the assistant selected by the window |
| `com.etendoerp.copilot.chat/` | React 18 + Vite source |
| `web/com.etendoerp.copilot.dist/` | the committed build output the iframe loads |
| `src/com/etendoerp/copilot/widget/CopilotWidgetComponent.java` | the same chat as a dashboard widget |

Both JS files are injected on every Classic page by
`CopilotComponentProvider.getGlobalComponentResources()`.

## The iframe bridge

The iframe URL is built in `openCopilotWindow()`:

```
web/com.etendoerp.copilot.dist/?question=…&assistant_id=…&context_title=…
```

Same origin as the host page ⇒ the chat can reach `window.parent` freely. Today it only
*receives*:

```js
window.addEventListener('message', e => {
  if (e.data.type === 'COPILOT_CONTEXT') { setContextValue(e.data.data); … }
});                                            // App.tsx:673-682
```

There is **no message flowing iframe → parent** yet. The UI Tools plan adds that
direction.

## React app structure (`com.etendoerp.copilot.chat/src`)

- `App.tsx` (861 lines) — everything: state, SSE, rendering, context handling.
- `hooks/useAssistants`, `useConversations`, `useArchivedConversations`, `useMaximized`.
- `components/AssistantDropdown`, `ConversationsSidebar`, `ContextNamePreview`,
  `CodeComponent`.
- `utils/references.ts` — the endpoint table; `PROD: '../../copilot/'`,
  `DEV: 'http://localhost:8080/etendo/copilot/'`.
- `utils/environment.ts` — `RestUtils.fetch`, which injects
  `Authorization: Basic admin:admin` when `NODE_ENV !== 'production'`.
- `utils/constants.ts` — the role constants: `user`, `bot`, `tool`, `node`, `wait`,
  `error`, `assistant`.

## Message pipeline in the UI

1. `handleSendMessage` (`App.tsx:445`)
   - `prepareQuestionWithContext` wraps the text in `<Context>…</Context><Question>…</Question>`
   - `buildRequestBody` → `{question, app_id, conversation_id?, file?}`
   - params are URL-encoded into an **EventSource GET**
2. `eventSource.onmessage` parses `data`, requires `answer.response`, and routes by
   `conversation_id` (`routeMessageToConversation`) so that answers belonging to another
   conversation only mark it unread.
3. `handleNewMessage(role, message)` prefixes an emoji for `wait`/`tool`/`node`, and
   **replaces** the previous message when it was a transient role — that is how the
   "🤖 thinking…" line updates in place.
4. `renderMessage` switches on `sender`. Unknown senders fall through to `default` and
   are rendered as a **right-aligned user bubble** — new roles must be added to this
   switch or they will look like the user is talking to themselves.

## Rendering capabilities today

Bot messages go through `TextMessage` from `etendo-ui-library`, which renders Markdown
(`react-markdown` + `remark-gfm`) and code blocks (`react-syntax-highlighter`). There is
**no** raw-HTML/SVG rendering path and no `dangerouslySetInnerHTML` anywhere in the app —
which is good news for security and means an SVG renderer must be added deliberately,
with sanitisation.

## Build

`npm run build` in `com.etendoerp.copilot.chat/` (tsc + vite) and the output is copied to
`web/com.etendoerp.copilot.dist/`. The dist is committed, so a change to the chat is not
live until the bundle is rebuilt and committed.
