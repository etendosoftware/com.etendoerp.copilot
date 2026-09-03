/**
 * WebMCP bridge.
 *
 * Etendo Classic registers tools in the page through the WebMCP API. Because WebMCP scopes tools
 * by *origin* rather than by document, this same-origin iframe sees them through its own
 * `document.modelContext` — no `window.parent` access is needed, so the chat stays decoupled from
 * whatever page hosts it.
 *
 * The model decides which tool to call, the backend answers with a `tool_call` envelope, and the
 * tool actually runs here, in the user's own tab, under the user's own session.
 *
 * API facts this module depends on, verified against Chrome 152 (the public documentation is out
 * of date on all of them):
 *  - the namespace is `document.modelContext`, not `navigator.modelContext` (deprecated in 150);
 *  - `getTools()` is asynchronous;
 *  - `tool.inputSchema` is a JSON *string*, not an object;
 *  - `executeTool()` takes the `RegisteredTool` object (not its name) and its arguments as a JSON
 *    string, and returns its result as a JSON string too.
 */

/** A tool descriptor as sent to the backend. */
export interface ClientToolDescriptor {
  name: string;
  description?: string;
  /** JSON Schema. Forwarded verbatim, string included: the backend accepts both forms. */
  inputSchema?: unknown;
}

/** One tool call requested by the model. */
export interface ToolCall {
  id: string;
  name: string;
  /** Arguments as a JSON string, the way the provider emits them. */
  arguments: string;
}

/** The outcome of executing one call, as reported back to the backend. */
export interface ToolResult {
  id: string;
  name: string;
  result: string;
}

/** One completed round of the exchange. */
export interface ToolRound {
  calls: ToolCall[];
  results: ToolResult[];
}

/**
 * @returns the WebMCP context of this realm, or null when the browser does not expose it
 */
function getModelContext(): any | null {
  const context = (document as any)?.modelContext;
  return context && typeof context.getTools === 'function' ? context : null;
}

/**
 * @returns true when this browser exposes WebMCP
 */
export function isWebMcpAvailable(): boolean {
  return getModelContext() !== null;
}

/**
 * Discover the tools registered for this origin.
 *
 * Never throws: WebMCP is an optional capability and a browser without it, or a page that
 * registered nothing, must simply produce a chat with no tools.
 *
 * @returns the descriptors to offer the model, empty when there are none
 */
export async function discoverClientTools(): Promise<ClientToolDescriptor[]> {
  const context = getModelContext();
  if (!context) return [];

  try {
    const tools = await context.getTools();
    if (!Array.isArray(tools)) return [];
    return tools
      .filter((tool: any) => tool && typeof tool.name === 'string')
      .map((tool: any) => ({
        name: tool.name,
        description: tool.description,
        // Kept exactly as the browser reports it; the backend parses either a string or an object.
        inputSchema: tool.inputSchema,
      }));
  } catch (error) {
    console.warn('WebMCP: could not read the registered tools', error);
    return [];
  }
}

/**
 * Subscribe to registration changes, so a cached descriptor set is never served stale.
 *
 * @param onChange called whenever the set of registered tools changes
 * @returns a function that removes the listener, or null when unsupported
 */
export function onToolsChanged(onChange: () => void): (() => void) | null {
  const context = getModelContext();
  if (!context || !('ontoolchange' in context)) return null;
  context.addEventListener?.('toolchange', onChange);
  return () => context.removeEventListener?.('toolchange', onChange);
}

/**
 * Execute the calls the model asked for, in order, and collect what to report back.
 *
 * A failing tool is reported as a result, not raised: the model asked for it and must be told what
 * happened so it can recover, and every call the model made needs an answer or the provider
 * rejects the conversation.
 *
 * @param calls the calls requested by the model
 * @returns one result per call, in the same order
 */
export async function executeClientTools(calls: ToolCall[]): Promise<ToolResult[]> {
  const context = getModelContext();
  const results: ToolResult[] = [];

  for (const call of calls) {
    results.push({
      id: call.id,
      name: call.name,
      result: context
        ? await executeOne(context, call)
        : 'This client cannot execute tools: WebMCP is not available in this browser.',
    });
  }
  return results;
}

/**
 * Execute a single call and reduce its outcome to the text the model will read.
 *
 * @param context the WebMCP context
 * @param call the call to execute
 * @returns the result text
 */
async function executeOne(context: any, call: ToolCall): Promise<string> {
  try {
    const registered = (await context.getTools()).find((tool: any) => tool.name === call.name);
    if (!registered) {
      return `No tool named "${call.name}" is registered in this page.`;
    }
    // executeTool takes the RegisteredTool object and a JSON *string*; passing a plain object
    // fails with "Failed to parse input arguments".
    const raw = await context.executeTool(registered, call.arguments || '{}');
    return readToolOutput(raw);
  } catch (error: any) {
    console.warn(`WebMCP: tool "${call.name}" failed`, error);
    return `The tool failed: ${error?.message ?? String(error)}`;
  }
}

/**
 * Unwrap the MCP result envelope, which arrives as a JSON string.
 *
 * A tool that mutates data asks the user to confirm before it runs, and reports a refusal the
 * same way it reports a crash: `isError`. Those two must not read alike to the model. A crash
 * invites a retry; a refusal is a decision, and retrying it would pop the very dialog the user
 * just dismissed. So a refusal is reported as an outcome with an explicit instruction not to
 * retry, and only a real failure is labelled as one.
 *
 * A refusal is recognised by the `declined` flag when the tool sets it, falling back to the
 * wording of the confirmation dialog. The flag is the contract; the wording is a safety net.
 *
 * @param raw whatever executeTool returned
 * @returns the text to hand to the model
 */
function readToolOutput(raw: unknown): string {
  let payload: any = raw;
  if (typeof raw === 'string') {
    try {
      payload = JSON.parse(raw);
    } catch {
      return raw;
    }
  }
  const text = payload?.content?.[0]?.text;
  if (typeof text !== 'string') {
    return typeof payload === 'string' ? payload : JSON.stringify(payload);
  }
  if (!payload?.isError) {
    return text;
  }
  if (payload?.declined === true || /\bdeclin(e|ed|es)\b/i.test(text)) {
    return `The user declined this action, so it was not performed. Do not retry it. `
      + `Ask what they would like to do instead. Reported reason: ${reason(text)}`;
  }
  // A genuine failure: the model has to know, or it will treat the message as data.
  return `The tool failed: ${reason(text)}`;
}

/**
 * Reduce a failure text to the sentence worth showing the model.
 *
 * A tool may report the failure as a JSON envelope of its own rather than as prose. Handing that
 * envelope over raw would nest JSON inside the sentence the model reads at the exact point where
 * it decides whether to retry. Only unwrapped for failures: an ordinary result is often JSON on
 * purpose and must reach the model untouched.
 *
 * @param text the reported text
 * @returns the message to show
 */
function reason(text: string): string {
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed.message === 'string') {
      return parsed.message;
    }
  } catch {
    // Not JSON: it is already the sentence we want.
  }
  return text;
}
