package com.etendoerp.copilot.hook;

import org.codehaus.jettison.json.JSONObject;

/**
 * Receives the usage of a tool that called a model while serving {@code /executeTool} (for
 * example SimpleOcrTool), so another module can record it. Copilot itself stores nothing.
 *
 * <p>Runs on the request thread, with the caller's {@code OBContext}, after Copilot answered and
 * before the answer is written. It must not throw and must not block: a failure here is logged
 * and never reaches the caller.
 */
public interface CopilotToolUsageHook {

  /**
   * Record the usage of one tool call that used a model.
   *
   * @param toolName
   *     the tool that ran, as requested ({@code tool_name})
   * @param agentId
   *     the agent whose tool configuration was used, or null
   * @param usage
   *     what the tool reported: {@code model}, {@code inputTokens}, {@code outputTokens} and,
   *     when the provider priced the call, {@code costUsd}
   * @param durationMs
   *     how long Copilot took to answer, in milliseconds
   */
  void exec(String toolName, String agentId, JSONObject usage, long durationMs);
}
