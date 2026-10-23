/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool;

import java.time.Duration;

import org.codehaus.jettison.json.JSONObject;

import dev.langchain4j.agent.tool.ToolSpecification;

/** A Java implementation of one {@code ETCOP_TOOL.VALUE}. */
public interface ServerTool {

  /** @return the exact {@code ETCOP_TOOL.VALUE} name used in agent declarations */
  String name();

  /** @return the tool's LangChain4j specification */
  ToolSpecification specification();

  /** Execute the requested operation under the supplied user's context. */
  String execute(JSONObject arguments, ToolExecutionContext context) throws Exception;

  /** True only when this tool calls an Etendo endpoint over loopback HTTP. */
  default boolean needsWebService() {
    return false;
  }

  /** Maximum time allowed for one invocation. */
  default Duration timeout() {
    return Duration.ofMinutes(2);
  }
}
