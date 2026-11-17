import { WebMcpStatus } from '../hooks/useWebMcpStatus';
import { ILabels } from '../interfaces';

/** Chrome flag that turns the API on while it is still behind an origin trial. */
const WEBMCP_FLAG = 'chrome://flags/#enable-webmcp-testing';

/**
 * Tell the user, at a glance, whether the agent can act on the page it is embedded in.
 *
 * Without this the degradation is invisible: a browser with no WebMCP support makes tool discovery
 * return nothing, the chat quietly falls back to a plain conversation, and the agent looks broken
 * rather than unequipped. The indicator is deliberately quiet when everything works and only draws
 * attention in the one state the user can actually do something about.
 *
 * @param status the current WebMCP state
 * @param labels the translated messages of the copilot module
 */
const WebMcpIndicator = ({ status, labels }: { status: WebMcpStatus; labels: ILabels }) => {
  const { state, tools } = status;

  const active = state === 'active';
  // Only a missing API is actionable by the user, so only that state is highlighted.
  const actionable = state === 'unavailable';

  const text = active
    ? String(tools.length)
    : labels?.ETCOP_WebMcp_Off ?? 'No tools';

  const title = buildTitle(state, tools.map(tool => tool.name), labels);

  return (
    <span
      id="webmcp-indicator"
      title={title}
      aria-label={title}
      className={`inline-flex items-center gap-1 text-[10px] leading-none rounded-full px-2 py-1 ${
        actionable
          ? 'bg-amber-50 text-amber-700 border border-amber-200'
          : 'bg-gray-100 text-gray-500 border border-gray-200'
      }`}
    >
      <span
        aria-hidden="true"
        className={`inline-block w-1.5 h-1.5 rounded-full ${
          active ? 'bg-emerald-500' : actionable ? 'bg-amber-500' : 'bg-gray-400'
        }`}
      />
      {text}
    </span>
  );
};

/**
 * Build the tooltip: what is happening and, when there is one, what to do about it.
 *
 * @param state the current state
 * @param names the names of the available tools
 * @param labels the translated messages of the copilot module
 * @returns the tooltip text
 */
function buildTitle(state: WebMcpStatus['state'], names: string[], labels: ILabels): string {
  if (state === 'unavailable') {
    return labels?.ETCOP_WebMcp_Unavailable
      ?? `This agent cannot use the tools of this page: your browser does not expose WebMCP. `
      + `Enable ${WEBMCP_FLAG} in Chrome 152 or later and reload.`;
  }
  if (state === 'empty') {
    return labels?.ETCOP_WebMcp_Empty
      ?? 'This page has not registered any tools, so the agent can talk but cannot act on it.';
  }
  const header = labels?.ETCOP_WebMcp_Active ?? 'The agent can use the tools of this page:';
  return `${header}\n${names.join('\n')}`;
}

export default WebMcpIndicator;
