/**
 * The client-executed tool loop.
 *
 * The streaming endpoint is a GET whose parameters are whitelisted server-side, so tool
 * descriptors and results cannot travel over it — and results are large anyway (an unfiltered
 * window listing is several KB). Turns that offer tools therefore run over the synchronous POST
 * endpoint instead, one request per round:
 *
 *   POST { question, app_id, client_tools }          -> { answer: { tool_calls } }
 *   execute the calls here, in this tab
 *   POST { question, app_id, client_tools, tool_exchange } -> the final answer
 *
 * The exchange is stateless: every round is echoed back and the backend rebuilds the conversation
 * from the stored history plus those rounds.
 */

import { References } from './references';
import { RestUtils } from './environment';
import { ClientToolDescriptor, ToolCall, ToolRound, executeClientTools } from './webmcp';

/**
 * Rounds allowed for a single question. Mirrors ClientToolSupport.DEFAULT_MAX_TOOL_ROUNDS on the
 * backend, which enforces the same cap; this one only avoids a pointless final request.
 *
 * It is a backstop against a model that never stops calling tools, so it sits well above what real
 * work needs: creating a single record already spends most of a dozen rounds — read the screen,
 * find the window, open it, start the record, list the fields, write them, save, read the screen
 * back — and more when a validation error sends it round again. If the backend is configured with
 * a different limit (copilot.engine.java.max.tool.rounds), the backend wins.
 */
export const MAX_TOOL_ROUNDS = 25;

/** Prefix of every diagnostic line, so the whole tool path can be filtered in the console. */
const LOG = '[copilot-tools]';

/** Reported while the tools of a round are running, so the user is not left staring at nothing. */
export interface ToolProgress {
  calls: ToolCall[];
  /** Any text the model produced alongside the calls. */
  text?: string;
}

/**
 * Run a question that may use client-executed tools, and return the final answer.
 *
 * @param requestBody the base body (question, app_id, conversation_id, ...)
 * @param clientTools the tools discovered in this page
 * @param onProgress called before each round of tools is executed
 * @returns the final answer object, normalised to the flat shape the chat renders
 */
export async function askWithClientTools(
  requestBody: Record<string, any>,
  clientTools: ClientToolDescriptor[],
  onProgress?: (progress: ToolProgress) => void,
): Promise<any> {
  const exchange: ToolRound[] = [];

  for (let round = 0; round <= MAX_TOOL_ROUNDS; round++) {
    const body: Record<string, any> = { ...requestBody, client_tools: clientTools };
    if (exchange.length > 0) {
      body.tool_exchange = exchange;
    }

    console.log(`${LOG} round ${round}: posting ${clientTools.length} tool(s) and `
      + `${exchange.length} previous round(s)`);
    const answer = await postQuestion(body);
    const calls: ToolCall[] = Array.isArray(answer?.tool_calls) ? answer.tool_calls : [];
    if (calls.length === 0) {
      console.log(`${LOG} round ${round}: the backend answered with text, the loop ends`, answer);
      return answer;
    }
    console.log(`${LOG} round ${round}: the backend asked for`, calls.map(call => call.name));

    onProgress?.({ calls, text: answer.response });
    exchange.push({ calls, results: await executeClientTools(calls) });
  }

  throw new Error(
    `The assistant used ${MAX_TOOL_ROUNDS} rounds of tools without reaching an answer, so it was `
    + `stopped. Anything it already did was applied. Try asking for one step at a time.`,
  );
}

/**
 * Post one round and normalise the response.
 *
 * The two server paths differ in shape: a tool-call turn returns `{ answer: {...} }` while a
 * completed turn returns the flat tracked response. Accept both.
 *
 * @param body the request body
 * @returns the answer object
 */
async function postQuestion(body: Record<string, any>): Promise<any> {
  const response = await RestUtils.fetch(References.url.SEND_QUESTION, {
    method: References.method.POST,
    body: JSON.stringify(body),
  });

  const data = await response.json();
  if (data?.error) {
    throw new Error(data.error);
  }
  const answer = data?.answer ?? data;
  if (!answer) {
    throw new Error('The assistant returned an empty response.');
  }
  return answer;
}
