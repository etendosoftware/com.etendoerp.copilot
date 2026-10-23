# Tools System

An "ETCOP_TOOL" is a function the LLM may call. Its *availability* is decided in Etendo; legacy
implementations live in Python, while the Java engine can execute CDI-contributed server tools.
Understanding both paths is a prerequisite for the UI Tools plan.

## 1. Declaration in the Application Dictionary

| Table | Meaning |
|---|---|
| `ETCOP_TOOL` | the catalogue. Columns: `VALUE` (the Python class name — this is the join key), `NAME`, `DESCRIPTION`, `JSON_STRUCTURE`, `USE_MODEL`, `SYNC_STRUCTURE` |
| `ETCOP_APP_TOOL` | many-to-many: which assistant may use which tool |
| `ETCOP_TOOL_WBHK` | webhooks a tool is allowed to call |

`ToolsUtil.getToolSet(app)` (`src/com/etendoerp/copilot/util/ToolsUtil.java:35`) turns the
`ETCOP_APP_TOOL` rows into the `tools` array of the request:

```json
[ { "type": "function", "function": { "name": "HelloWorldTool" } } ]
```

If `JSON_STRUCTURE` is filled and is not `{}`, that raw JSON is sent instead — so a tool
can ship a full OpenAI function schema from the database.

## 2. Implementation in Python

Base classes in `copilot/core/tool_wrapper.py`:

- `CopilotTool(BaseTool)` — marker.
- `ToolWrapper(CopilotTool)` — the one real tools extend. Implements `_run` / `_arun`,
  wraps exceptions into `ToolOutputError`, and validates the input against
  `args_schema`.
- Return types: `ToolOutputMessage {message}`, `ToolOutputError {error}`,
  `ToolOutputContent {content}`; `parse_response()` flattens them to a string.

Reference implementations live in `tools/` (`HelloWorldTool.py`, `ExampleTool.py`,
`CustomHelloWorldMultiArgsTool.py`) plus `copilot/core/langgraph/tool_utils/TaskManagementTool.py`.

## 3. Loading (`copilot/core/tool_loader.py`)

`ToolLoader` is a singleton with a class-level cache:

1. `load_configured_tools()` — imports `tools/`, instantiates every concrete
   `ToolWrapper`/`CopilotTool` subclass, caches the list **process-wide**.
2. `_get_filtered_base_tools(enabled_tools)` — keeps only tools whose `name` appears in
   the `tools` array sent by Java. **An empty/absent array means zero base tools.**
3. `get_all_tools(...)` additionally appends:
   - the knowledge-base retrieval tool (`_add_kb_tool`),
   - tools generated from each `specs[].spec` of type `FLOW`
     (`toolgen/openapi_tool_gen.py`),
   - MCP tools from `agent_configuration.mcp_servers`
     (`MultiServerMCPClient`, 45 s timeout).
4. Sets `tool.agent_id` on every tool that has the attribute.

The important consequence: **the tool list is rebuilt per request** from data that
arrived in the request body. A tool does *not* have to exist as a file in `tools/` —
OpenAPI and MCP tools are proof that tools can be synthesised at request time from
request data. That is precisely the mechanism the UI Tools plan reuses.

## 4. How a tool reads request context

Anything a tool needs beyond its own arguments comes from `ThreadContext`:

```python
from copilot.core.threadcontext import ThreadContext
extra = ThreadContext.get_data("extra_info") or {}
token = extra.get("auth", {}).get("ETENDO_TOKEN")
```

## 5. Java server-tool implementations

The Java engine resolves every name in the request's `tools` array by CDI bean name. A Java
implementation may live in the Copilot module or in another discovered module; Copilot does not
maintain a per-tool class list. The bean name and `ServerTool.name()` must both equal
`ETCOP_TOOL.VALUE`.

```java
import java.util.Arrays;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

@ApplicationScoped
@Named("AddNumbersTool") // equals ETCOP_TOOL.VALUE
public class AddNumbersTool implements ServerTool {
  @Override
  public String name() {
    return "AddNumbersTool";
  }

  @Override
  public ToolSpecification specification() {
    return ToolSpecification.builder().name(name()).description("Add two integers")
        .parameters(JsonObjectSchema.builder()
            .addProperty("left", JsonRawSchema.from("{\"type\":\"integer\"}"))
            .addProperty("right", JsonRawSchema.from("{\"type\":\"integer\"}"))
            .required(Arrays.asList("left", "right"))
            .build())
        .build();
  }

  @Override
  public String execute(JSONObject arguments, ToolExecutionContext context) throws Exception {
    return Integer.toString(arguments.getInt("left") + arguments.getInt("right"));
  }
}
```

`specification()` supplies the default model schema. When the request carries a non-empty
`ETCOP_TOOL.JSON_STRUCTURE`, its OpenAI function `parameters` schema takes precedence, matching
Python's configured-tool behavior. `execute()` receives parsed object arguments and the captured
context. The engine wraps it in `context.call(tool.timeout(), ...)`; implementations must not
manually bind or clean `OBContext`/DAL sessions. Use `context.admin(...)` only for a narrow balanced
privileged operation. Set `needsWebService()` to `true` only for loopback-HTTP tools; those calls
are rejected with a model-readable tool error if the asking role has web services disabled.

`ToolExecutionContext` exposes immutable identity IDs, a defensive read-only `extraInfo()` snapshot,
a per-question concurrent `attributes()` map, and on-demand `mintToken()` for a real SWS callback.
The token is null for a role without web services and must never be logged or returned to the model.
Tool failures become safe result messages, and the bounded server loop uses the same configured
round limit as client-executed tools.

An agent with a declared tool name that has no CDI implementation is routed to Python (or rejected
in `java-strict`); the fallback reason names the missing `ETCOP_TOOL.VALUE`.


## 6. Client-executed tools (WebMCP)

WebMCP tools are declared separately in the request's `client_tools` field, offered alongside
server tools to the model, and executed by the browser. They are not entries in `ETCOP_TOOL` and do
not need a Java `ServerTool` bean. The browser echoes each completed round through
`tool_exchange`; mixed server/client result handling is documented in [java-engine.md](java-engine.md#6-client-executed-tools-webmcp).

## 7. Java tool implementation status

Java implementations are named CDI beans in the Copilot module or a dependent extension module.
The following mappings describe the currently ported tools; tools not listed remain Python-only.

| Python class | Java class | Module | Execution |
|---|---|---|---|
| ReadFileTool | ReadFileTool | toolpack | in-process |
| WriteFileTool | WriteFileTool | toolpack | in-process |
| PrintDirectoryTool | PrintDirectoryTool | toolpack | in-process |
| FileCopyTool | FileCopyTool | toolpack | in-process |
| APICallTool | APICallTool | toolpack | loopback HTTP for Etendo; direct HTTP externally |
| TestRunTool | TestRunTool | devassistant | in-process |
| ProcessDefinitionJasperTool | ProcessDefinitionJasperTool | devassistant | loopback HTTP |
| ClientInitTool | ClientInitTool | devassistant | loopback HTTP |
| OrgInitTool | OrgInitTool | devassistant | loopback HTTP |
| EtendoSQLToCSVTool | EtendoSQLToCSVTool | toolpack | loopback HTTP |
| DockerTool | DockerTool | toolpack | Docker |
| SimpleOcrTool | SimpleOcrTool | copilot | in-process |
| OcrTool | OcrTool | toolpack | in-process |
| XMLTranslationTool | XMLTranslationTool | devassistant | in-process |
| PdfToImagesTool | PdfToImagesTool | toolpack | in-process |
| XLSTool | XLSTool | toolpack | in-process |
| UncompressTool | UncompressTool | toolpack | in-process |
| AudioTool | AudioTool | toolpack | in-process |
| MemoryTool | — | — | deferred: KB package |
| TaskManagementTool | — | — | deferred: teams package |
| OpenAPI ApiTool | — | — | dropped by decision |

| Python class | Java class | Module | Execution |
|---|---|---|---|
| AttachFileTool | AttachFileTool | toolpack | loopback HTTP |
| CodbarTool | CodbarTool | toolpack | in-process |
| ExcelOCRTool | ExcelOCRTool | toolpack | in-process |
| GoogleDriveTool | GoogleDriveTool | toolpack | HTTP |
| GoogleSpreadsheetsTool | GoogleSpreadsheetsTool | toolpack | HTTP |
| LoadOAuthTokenTool | LoadOAuthTokenTool | toolpack | in-process |
| SendEmailTool | SendEmailTool | toolpack | SMTP/HTTP |
| TaskCreatorTool | TaskCreatorTool | toolpack | loopback HTTP |
| TavilySearchTool | TavilySearchTool | toolpack | external HTTP |
| OCRExampleCreatorTool | — | — | dropped by decision |
| TemplateTool | — | — | dropped by decision |
| HelloWorldTool | — | — | dropped by decision |
| CustomHelloWorldMultiArgsTool | — | — | dropped by decision |
| ExampleTool | — | — | dropped by decision |
| MemoryTool | — | — | deferred: KB package |
| TaskManagementTool | — | — | deferred: teams package |
| ApiTool (OpenAPI generator) | — | — | dropped by decision |

## 8. File-tool filesystem policy and threat model

File tools treat agent-supplied paths, archive entries, and downloaded content as untrusted.
`ToolWorkspace` applies the policy centrally and `FileToolSupport` performs guarded reads and
writes. Reads and writes use separate comma-separated absolute directory lists:
`copilot.tools.fs.read.roots` and `copilot.tools.fs.write.roots`. If unset, each defaults to the
per-conversation workspace. The older `copilot.tools.fs.roots` setting is a fallback for either
list not explicitly configured. The workspace defaults to
`/tmp/etendo-copilot/<conversation>` and must remain outside the deployed webapp and protected
configuration paths.

The hard deny-list cannot be overridden by configured roots: the deployed webapp, Tomcat
`webapps` and `conf`, the Etendo `config` directory, `Openbravo.properties`,
`gradle.properties`, `.git` internals, and dotfiles under the JVM user home. Path traversal,
symlink escapes, and hard-linked files are rejected, including for write destinations. Writes
have no executable permission; no file tool executes a file.

Defaults are 100 MiB and 1,000 files per conversation, 25 MiB per file, 1,000 archive entries,
100 MiB expanded archive data, and a 100:1 maximum archive expansion ratio. Configure the
quotas with `copilot.tools.fs.max.conversation.bytes`, `copilot.tools.fs.max.conversation.files`,
and `copilot.tools.fs.max.file.bytes`; archive limits use
`copilot.tools.fs.archive.max.entries`, `copilot.tools.fs.archive.max.bytes`, and
`copilot.tools.fs.archive.max.ratio`. ZIP extraction rejects path escapes and symlink entries.

`FileDownloaderTool` permits HTTP(S) only, blocks resolved private, loopback, link-local,
multicast, and metadata addresses, validates redirect targets (at most three redirects), and
applies connection/request timeouts and the per-file download cap. These controls reduce
server-side request forgery risk; downloaded content remains inert data and is never executed.
