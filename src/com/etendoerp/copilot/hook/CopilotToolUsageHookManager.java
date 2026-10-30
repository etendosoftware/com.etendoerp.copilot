package com.etendoerp.copilot.hook;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

/**
 * Hands the usage reported by {@code /executeTool} to every {@link CopilotToolUsageHook}. A hook
 * that fails is logged and skipped: recording usage never changes the tool's answer.
 */
public class CopilotToolUsageHookManager {

  private static final Logger log = LogManager.getLogger(CopilotToolUsageHookManager.class);

  @Inject
  @Any
  private Instance<CopilotToolUsageHook> usageHooks;

  /**
   * Hand the usage of one tool call to every deployed {@link CopilotToolUsageHook}. A failing hook
   * is logged and skipped; this method never throws.
   *
   * @param toolName the tool that ran, as requested
   * @param agentId the agent whose tool configuration was used, or null
   * @param usage what the tool reported (model, tokens and, when priced, costUsd)
   * @param durationMs how long Copilot took to answer, in milliseconds
   * @see CopilotToolUsageHook#exec(String, String, JSONObject, long)
   */
  public void executeHooks(String toolName, String agentId, JSONObject usage, long durationMs) {
    for (CopilotToolUsageHook hook : usageHooks) {
      try {
        hook.exec(toolName, agentId, usage, durationMs);
      } catch (Exception e) {
        log.error("Tool usage hook {} failed for {}", hook.getClass().getSimpleName(), toolName, e);
      }
    }
  }
}
