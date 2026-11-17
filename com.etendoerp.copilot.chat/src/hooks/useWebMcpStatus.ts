import { useCallback, useEffect, useState } from 'react';
import { ClientToolDescriptor, discoverClientTools, isWebMcpAvailable, onToolsChanged } from '../utils/webmcp';

/**
 * Why the agent can or cannot act on the page.
 *
 * These are three different problems with three different fixes, so they are three different
 * states rather than one boolean:
 *  - `unavailable`: the browser does not expose WebMCP at all. The user has to enable a flag.
 *  - `empty`: the API is there but the page registered nothing, so the host page is the problem.
 *  - `active`: the agent has tools.
 */
export type WebMcpState = 'unavailable' | 'empty' | 'active';

export interface WebMcpStatus {
  state: WebMcpState;
  tools: ClientToolDescriptor[];
}

/** How often the tool set is re-checked while it is still empty. */
const POLL_MS = 3000;
/** How long to keep re-checking before accepting that nothing will be registered. */
const POLL_TIMEOUT_MS = 60000;

/**
 * Track whether the agent can use the tools of the page it is embedded in.
 *
 * Registration is racy by nature: Etendo Classic registers its tools once the menu has loaded,
 * which may well be after this chat has mounted. A status read once at mount would therefore
 * report "no tools" for a page that ends up having them. The hook subscribes to registration
 * changes when the browser supports it and, when it does not, re-checks while the set is still
 * empty — the only state that can still change — until it gives up.
 *
 * @returns the current state and the discovered descriptors
 */
export function useWebMcpStatus(): WebMcpStatus {
  const [status, setStatus] = useState<WebMcpStatus>({
    state: isWebMcpAvailable() ? 'empty' : 'unavailable',
    tools: [],
  });

  const refresh = useCallback(async () => {
    if (!isWebMcpAvailable()) {
      setStatus({ state: 'unavailable', tools: [] });
      return;
    }
    const tools = await discoverClientTools();
    setStatus({ state: tools.length > 0 ? 'active' : 'empty', tools });
  }, []);

  useEffect(() => {
    let cancelled = false;
    const check = () => {
      if (!cancelled) void refresh();
    };

    check();
    const unsubscribe = onToolsChanged(check);
    window.addEventListener('focus', check);

    // Fallback for browsers without the change event. Registration happens shortly after load, so
    // the poll is bounded: past the timeout the answer is not going to change on its own, and a
    // later `focus` still picks up anything registered afterwards.
    const poll = window.setInterval(check, POLL_MS);
    const stopPolling = window.setTimeout(() => window.clearInterval(poll), POLL_TIMEOUT_MS);

    return () => {
      cancelled = true;
      unsubscribe?.();
      window.removeEventListener('focus', check);
      window.clearInterval(poll);
      window.clearTimeout(stopPolling);
    };
  }, [refresh]);

  return status;
}
