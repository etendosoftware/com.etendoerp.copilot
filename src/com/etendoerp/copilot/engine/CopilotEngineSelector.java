package com.etendoerp.copilot.engine;

import static com.etendoerp.copilot.util.CopilotUtils.readPropertyWithLegacyCompatibility;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry.ResolvedTools;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Chooses the {@link CopilotEngine} that will serve a request.
 * <p>
 * The choice is made by the {@code copilot.engine} property in {@code Openbravo.properties}:
 * <ul>
 *   <li>{@code python} (default) — always delegate to the Python container.</li>
 *   <li>{@code java} — run in-process with LangChain4j when the request is supported,
 *       otherwise fall back to Python and log why.</li>
 *   <li>{@code java-strict} — run in-process always; fail loudly instead of falling back.
 *       Use this to prove that no request is silently reaching the Python container.</li>
 * </ul>
 * The Java engine supports langchain/multimodel agents, in-process team graphs, registered server
 * tools, client-side WebMCP tools, configured MCP servers, and Java-backed knowledge-base retrieval.
 * Anything outside that surface is reported by {@link #unsupportedReason}.
 */
public class CopilotEngineSelector {

  private static final Logger log = LogManager.getLogger(CopilotEngineSelector.class);

  public static final String PROPERTY_ENGINE = "copilot.engine";
  public static final String ENGINE_PYTHON = "python";
  public static final String ENGINE_JAVA = "java";
  public static final String ENGINE_JAVA_STRICT = "java-strict";

  private CopilotEngineSelector() {
  }

  /**
   * Resolve the engine for a request.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @param app
   *     the agent serving the request
   * @return the engine to use; never {@code null}
   * @throws OBException
   *     in {@code java-strict} mode when the request cannot be served in Java
   */
  public static CopilotEngine get(JSONObject request, CopilotApp app) {
    String configured = configuredEngine();

    if (StringUtils.equalsIgnoreCase(configured, ENGINE_PYTHON)) {
      warnIfClientToolsWillBeIgnored(request, app);
      logSelection(configured, app, "python", "the default engine is configured");
      return new PythonCopilotEngine();
    }

    String reason = unsupportedReason(request, app);
    if (reason == null) {
      logSelection(configured, app, "java (LangChain4j)", "no Python container involved");
      return new LangChain4jEngine();
    }

    if (StringUtils.equalsIgnoreCase(configured, ENGINE_JAVA_STRICT)) {
      throw new OBException(
          String.format("The Java Copilot engine cannot serve this request: %s. Set %s=%s to allow "
              + "falling back to the Python container.", reason, PROPERTY_ENGINE, ENGINE_JAVA));
    }

    warnIfClientToolsWillBeIgnored(request, app);
    // A fallback is a degradation, not routine: always warn, whatever the configured engine is.
    log.warn("Copilot engine: python for agent '{}' (falling back, {})", app.getName(), reason);
    return new PythonCopilotEngine();
  }

  /**
   * Client-executed tools are implemented by the Java engine only. If the client offered some and
   * the request is going to Python, they are dropped without a trace, which looks to the user like
   * the agent ignoring its own tools. Say so.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @param app
   *     the agent serving the request
   */
  private static void warnIfClientToolsWillBeIgnored(JSONObject request, CopilotApp app) {
    JSONArray clientTools = request.optJSONArray(ClientToolSupport.PROP_CLIENT_TOOLS);
    if (clientTools != null && clientTools.length() > 0) {
      log.warn("The client offered {} client-executed tool(s) for agent '{}', but they are only "
          + "supported by the Java engine and will be ignored.", clientTools.length(), app.getName());
    }
  }

  /**
   * Report which engine served a request.
   * <p>
   * The line is logged at INFO only when {@code copilot.engine} has been changed from its default,
   * so a standard installation stays as quiet as it was before this feature existed, while anyone
   * running or testing the Java engine can see the decision for every request without turning on
   * debug logging.
   *
   * @param configured
   *     the configured value of {@code copilot.engine}
   * @param app
   *     the agent serving the request
   * @param engine
   *     the engine that was selected
   * @param detail
   *     why it was selected
   */
  private static void logSelection(String configured, CopilotApp app, String engine, String detail) {
    if (StringUtils.equalsIgnoreCase(configured, ENGINE_PYTHON)) {
      log.debug("Copilot engine: {} for agent '{}' ({})", engine, app.getName(), detail);
    } else {
      log.info("Copilot engine: {} for agent '{}' ({})", engine, app.getName(), detail);
    }
  }

  /**
   * @return the configured engine name, defaulting to {@link #ENGINE_PYTHON}
   */
  public static String configuredEngine() {
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return readPropertyWithLegacyCompatibility(properties, PROPERTY_ENGINE, ENGINE_PYTHON);
  }

  /**
   * Explain why a request cannot be served by the Java engine.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @param app
   *     the agent serving the request
   * @return a human readable reason, or {@code null} when the request is supported
   */
  public static String unsupportedReason(JSONObject request, CopilotApp app) {
    return unsupportedReason(request, app, new ServerToolRegistry());
  }


  /**
   * Explain why a request cannot be served by the Java engine.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @param app
   *     the agent serving the request
   * @param serverToolRegistry
   *     the Java implementations available in this runtime
   * @return a human readable reason, or {@code null} when the request is supported
   */
  public static String unsupportedReason(JSONObject request, CopilotApp app,
      ServerToolRegistry serverToolRegistry) {
    String type = request.optString(RestServiceUtil.PROP_TYPE, null);
    if (!StringUtils.equalsIgnoreCase(type, CopilotConstants.APP_TYPE_LANGCHAIN)
        && !StringUtils.equalsIgnoreCase(type, CopilotConstants.APP_TYPE_MULTIMODEL)
        && !StringUtils.equalsIgnoreCase(type, CopilotConstants.APP_TYPE_LANGGRAPH)) {
      return String.format("agent type '%s' is only implemented in the Python runtime", type);
    }
    if (StringUtils.equalsIgnoreCase(type, CopilotConstants.APP_TYPE_LANGGRAPH)) {
      JSONArray members = request.optJSONArray("assistants");
      if (members == null || members.length() == 0) return "the team has no members";
      for (int i = 0; i < members.length(); i++) {
        JSONObject member = members.optJSONObject(i);
        if (member == null) return "the team contains an invalid member configuration";
        String memberType = member.optString("type", null);
        if (StringUtils.equalsIgnoreCase(memberType, CopilotConstants.APP_TYPE_OPENAI)) {
          return "openai-assistant team members are not implemented in the Python graph runtime";
        }
        if (!StringUtils.equalsIgnoreCase(memberType, CopilotConstants.APP_TYPE_LANGCHAIN)
            && !StringUtils.equalsIgnoreCase(memberType, CopilotConstants.APP_TYPE_MULTIMODEL)) {
          return String.format("team member type '%s' is unsupported by the Java team engine", memberType);
        }
        ResolvedTools memberTools = serverToolRegistry.resolve(member, false,
            new com.etendoerp.copilot.engine.tool.openapi.OpenApiToolGenerator()
                .tools(member.optJSONArray("specs")));
        if (!memberTools.missingNames().isEmpty()) {
          return "team member declares tools that have no Java implementation: "
              + String.join(", ", memberTools.missingNames());
        }
      }
    }
    if (hasEntries(request.optJSONArray(RestServiceUtil.PROP_TOOLS))) {
      ResolvedTools tools = serverToolRegistry.resolve(request, false,
          new com.etendoerp.copilot.engine.tool.openapi.OpenApiToolGenerator()
              .tools(request.optJSONArray("specs")));
      if (!tools.missingNames().isEmpty()) {
        return "the agent declares tools that have no Java implementation: "
            + String.join(", ", tools.missingNames());
      }
    }
    if (request.optBoolean(RestServiceUtil.PROP_CODE_EXECUTION, false)) {
      return "the agent runs code execution (CodeAct), which is not implemented in the Java engine";
    }
    return null;
  }

  private static boolean hasEntries(JSONArray array) {
    return array != null && array.length() > 0;
  }
}
