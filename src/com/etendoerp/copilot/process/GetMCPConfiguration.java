/*
 * Copyright (c) 2022 Futit Services SL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.etendoerp.copilot.process;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.webhookevents.data.DefinedwebhookToken;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Data;
import com.smf.jobs.Result;

/**
 * This action handles the generation of MCP (Multi-Cloud Platform) configuration,
 * displaying it to the user in a formatted HTML snippet.
 * It fetches agent details, generates an API key, and creates a JSON configuration
 * for a specific agent, which can then be used to connect to the Copilot service.
 */
public class GetMCPConfiguration extends Action {
  private static final Logger log = LogManager.getLogger();
  private static final String DIV_CLOSE = "</div>\n";
  private static final String LOCALHOST_PREFIX = "http://localhost";

  @Override
  /**
   * Prepares the input data before the action runs.
   *
   * @param jsonContent raw JSON content from the request; the method removes internal fields and
   *                    converts to a {@link Data} instance.
   * @return a {@link Data} object built from the cleaned JSON, or null on parse error.
   */
  public Data preRun(JSONObject jsonContent) {
    jsonContent.remove("_entityName");
    Data tmp = null;
    try {
      tmp = new Data(jsonContent, getInputClass());
    } catch (JSONException e) {
      log.error(e.getMessage());
    }
    return tmp;
  }

  @Override
  /**
   * Main action handler invoked by the jobs framework.
   * It builds the MCP configuration JSON per agent and returns an HTML snippet embedded in the
   * action response.
   *
   * @param parameters input parameters as JSON (expects recordIds, Direct and mcp_remote_mode)
   * @param isStopped  mutable flag that can be set to stop execution
   * @return an {@link ActionResult} containing the generated HTML or an error status
   */
  public ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    var result = new ActionResult();
    try {
      List<CopilotApp> agents = parseAgents(parameters);
      // Validation: only one agent allowed
      if (agents.size() > 1) {
        result.setType(Result.Type.ERROR);
        result.setMessage(OBMessageUtils.messageBD("ETCOP_Multiple_Agents_Not_Allowed"));
        return result;
      }
      String fullHTML = getHTMLConfigurations(parameters, agents);
      var jsonMessage = new JSONObject();
      jsonMessage.put("message", fullHTML);
      result.setResponseActionsBuilder(getResponseBuilder().addCustomResponseAction("smartclientSay", jsonMessage));
      result.setOutput(getInput());
      result.setType(Result.Type.SUCCESS);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      result.setType(Result.Type.ERROR);
      result.setMessage(e.getMessage());
    }
    return result;
  }

  /**
   * Builds the full HTML that contains the MCP configuration blocks for the provided agents.
   *
   * @param parameters input JSON parameters controlling generation (Direct, mcp_remote_mode, etc.)
   * @param agents     list of {@link CopilotApp} instances to generate configurations for
   * @return an HTML string ready to be displayed to the user
   * @throws JSONException when JSON construction fails or when underlying JSON operations fail.
   */
  public String getHTMLConfigurations(JSONObject parameters, List<CopilotApp> agents) throws Exception {
    var direct = parameters.getBoolean("Direct");
    var mcpRemoteCompatibilityMode = parameters.getBoolean("mcp_remote_mode");

    String token = CopilotUtils.generateEtendoToken();

    String customName = readOptString(parameters, "custom_name");
    String customUrl = readOptString(parameters, "custom_url");
    var contextUrlMcp = getContextUrlMCP(customUrl);
    boolean prefixMode = parameters.optBoolean("prefixMode", false);

    List<JSONObject> configs = buildConfigsFromAgents(agents, direct, mcpRemoteCompatibilityMode, token,
        contextUrlMcp, customName, prefixMode);

    boolean hasLocalhost = detectLocalhostFromConfigs(configs);
    StringBuilder htmlCodeSB = new StringBuilder();
    htmlCodeSB.append(buildFixedMessage());
    if (hasLocalhost) {
      // use generic localhost warning (context URL is not required here)
      htmlCodeSB.append(buildLocalhostWarning(LOCALHOST_PREFIX));
    }

    for (JSONObject cfg : configs) {
      htmlCodeSB.append(buildConfigFragment(cfg));
      htmlCodeSB.append("<br>");
    }

    String fullHTML = buildMessage(htmlCodeSB.toString());
    return fullHTML;
  }

  /**
   * Reads an optional string property from a {@link JSONObject}. The literal string {@code "null"}
   * (case-insensitive) is treated as {@code null}.
   *
   * @param parameters source {@link JSONObject}
   * @param prop       property name to read
   * @return the string value, or {@code null} if the property is missing, blank, or the literal {@code "null"}
   */
  public static String readOptString(JSONObject parameters, String prop) {
    String s = parameters.optString(prop, null);
    if (StringUtils.isNotBlank(s) && StringUtils.equalsIgnoreCase("null", s)) {
      return null;
    }
    return s;
  }

  /**
   * Parses incoming parameters and loads CopilotApp agents from the DAL.
   *
   * @param parameters JSON object containing a {@code recordIds} array with agent ids.
   * @return a list of {@link CopilotApp} instances matching the provided ids.
   * @throws JSONException when {@code recordIds} is missing or malformed.
   */
  public List<CopilotApp> parseAgents(JSONObject parameters) throws JSONException {
    var agentIDsArr = parameters.getJSONArray("recordIds");
    List<String> agentsIDs = new ArrayList<>();
    for (int i = 0; i < agentIDsArr.length(); i++) {
      agentsIDs.add(agentIDsArr.getString(i));
    }
    return OBDal.getInstance().createCriteria(CopilotApp.class).add(
        Restrictions.in(BaseOBObject.ID, agentsIDs)).list();
  }

  /**
   * Builds per-agent configuration JSON objects.
   *
   * @param agents list of agents to process.
   * @param direct whether to use direct endpoint paths.
   * @param mcpRemoteCompatibilityMode toggles npx-compatible remote example generation.
   * @param token authentication token to include in the generated config.
   * @param contextUrlMcp base MCP context URL.
   * @param customName optional custom name to use for the generated key/display name.
   * @param prefixMode when true and customName is set, the agent name is appended to the key.
   * @return a list of {@link JSONObject} configurations (one per agent).
   * @throws JSONException when JSON construction fails.
   */
  public List<JSONObject> buildConfigsFromAgents(List<CopilotApp> agents, boolean direct,
      boolean mcpRemoteCompatibilityMode, String token, String contextUrlMcp, String customName,
      boolean prefixMode) throws JSONException {
    List<JSONObject> configs = new ArrayList<>();
    for (CopilotApp agent : agents) {
      ConfigRequest req = new ConfigRequest(agent.getId(), agent.getName());
      req.setOptions(direct, mcpRemoteCompatibilityMode, token, contextUrlMcp, customName, prefixMode);
      configs.add(getConfig(req));
    }
    return configs;
  }

  /**
   * Inspects a list of configuration objects and determines if any reference a localhost URL.
   *
   * @param configs a list of configuration JSONObjects.
   * @return {@code true} if at least one config references a URL that starts with the configured localhost prefix, otherwise {@code false}.
   */
  public boolean detectLocalhostFromConfigs(List<JSONObject> configs) {
    for (JSONObject cfg : configs) {
      String serverUrl = getServerUrlFromConfig(cfg);
      if (serverUrl != null && serverUrl.startsWith(LOCALHOST_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the server URL from a single configuration JSONObject.
   * The config is expected to have a single top-level key whose value either contains a {@code url}
   * property (standard mode) or an {@code args} array (remote example) where the URL is at index 1.
   *
   * @param cfg a configuration JSONObject with a single top-level key.
   * @return the server URL if present, otherwise {@code null}.
   */
  public String getServerUrlFromConfig(JSONObject cfg) {
    String key = null;
    java.util.Iterator<String> keys = cfg.keys();
    if (keys.hasNext()) {
      key = keys.next();
    }
    JSONObject cfgInternal = key != null ? cfg.optJSONObject(key) : null;
    if (cfgInternal == null) {
      return null;
    }
    if (cfgInternal.has("url")) {
      return readOptString(cfgInternal, "url");
    }
    if (cfgInternal.has("args")) {
      JSONArray tmpArgs = cfgInternal.optJSONArray("args");
      if (tmpArgs != null && tmpArgs.length() > 1) {
        return tmpArgs.optString(1, null);
      }
    }
    return null;
  }

  /**
   * Builds a fragment containing the install badge and the JSON code block for a single
   * configuration object.
   *
   * @param config a configuration JSONObject with a single top-level key.
   * @return an HTML fragment with the badge and JSON block.
   * @throws JSONException when JSON processing fails.
   */
  public String buildConfigFragment(JSONObject config) throws JSONException {
    String key = null;
    java.util.Iterator<String> keys = config.keys();
    if (keys.hasNext()) {
      key = keys.next();
    }
    JSONObject cfgInternal = key != null ? config.getJSONObject(key) : new JSONObject();

    String json = config.toString(2);
    // remove first and last curly braces
    json = json.substring(1, json.length() - 1).trim();
  // Unescape forward slashes for nicer display (Jettison may escape them as \/)
  json = json.replace("\\/", "/");
  // Ensure encoded JSON uses unescaped slashes to avoid backslashes in the final link
  String rawCfg = cfgInternal.toString().replace("\\/", "/");
  String encodedJson = URLEncoder.encode(rawCfg, StandardCharsets.UTF_8);
  encodedJson = encodedJson.replace("+", "%20");
    String buttonLink = "vscode:mcp/install?" + encodedJson;
    String idSuffix = normalize(key != null ? key : "cfg");

    StringBuilder html = new StringBuilder();
    html.append(buildInstallBadge(buttonLink));
    html.append(buildCodeBlock(json, idSuffix));
    return html.toString();
  }

  /**
   * Returns the fixed explanatory message HTML placed above the configuration block.
   *
   * @return an HTML fragment with the explanatory text (localized via OBMessageUtils).
   */
  public String buildFixedMessage() {
    return "  <div style=\"margin-bottom: 0.5rem;\">" +
        "    <span style=\"color:#333; font-size:13px;\">" + OBMessageUtils.messageBD(
        "ETCOP_MCPInstallation") + "</span>" +
        "  </div>";
  }

  /**
   * Returns an informational warning HTML if the provided serverUrl points to localhost.
   *
   * @param serverUrl the server URL to inspect; may be null.
   * @return an HTML fragment with a warning or an empty string when not applicable.
   */
  public String buildLocalhostWarning(String serverUrl) {
    if (serverUrl != null && serverUrl.startsWith(LOCALHOST_PREFIX)) {
      return "  <div style=\"margin-top:0.5rem; padding:.5rem; background:#fff3cd; border:1px solid #ffeeba; color:#856404; border-radius:4px; font-size:13px;\">" +
          OBMessageUtils.messageBD("ETCOP_ContextURLMCP_Warning") +
          DIV_CLOSE;
    }
    return "";
  }

  /**
   * Builds the install badge HTML that links to the custom vscode:mcp install URL.
   *
   * @param buttonLink the fully encoded install link to be used in the anchor href.
   * @return an HTML fragment with the badge anchor element.
   */
  public String buildInstallBadge(String buttonLink) {
    StringBuilder b = new StringBuilder();
    b.append("  <div style=\"margin-bottom: 0.5rem; text-align: right;\">");
    b.append("    <a href=\"").append(buttonLink).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
    b.append("      <img alt=\"Static Badge\" src=\"https://img.shields.io/badge/Install%20in%20-VSCode-blue\">");
    b.append("    </a>");
    b.append(DIV_CLOSE);
    return b.toString();
  }

  /**
   * Builds the HTML code block that contains the pretty-printed JSON inside a {@code <pre><code>} element,
   * includes a copy button and appends a fallback script that wires the copy handler when the HTML is
   * inserted dynamically.
   *
   * @param json the pretty-printed JSON text to include inside the {@code <code>} element.
   * @param idSuffix a unique suffix for the HTML element IDs to avoid collisions.
   * @return an HTML fragment with the {@code <pre><code>} block, a copy button and a small inline script as fallback.
   */
  public String buildCodeBlock(String json, String idSuffix) {
    StringBuilder cb = new StringBuilder();
    cb.append(
        "  <div style=\"position: relative; background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px; padding: 1rem 1rem 1.25rem;\" role=\"region\" aria-label=\"JSON code block\">\n");

    // copy button placed inside the code block (top-right)
    String btnId = "copilotMCP_btnCopiar_" + idSuffix;
    String codeId = "copilotMCP_json_" + idSuffix;

    cb.append(
        "    <button style=\"position: absolute; top: .6rem; right: .6rem; border: 1px solid #d0d7de; background: #fff; border-radius: 6px; padding: .25rem .5rem; cursor: pointer; font-size: 12px;\" id=\""
            + btnId
            + "\" aria-label=\"Copy JSON\" title=\"Copy JSON to clipboard\" onclick=\"(function(btn){const code=document.getElementById('"
            + codeId
            + "'); if(!code)return; const myText=code.innerText.trim(); try{ if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(myText);} else {const ta=document.createElement('textarea'); ta.value=myText; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove();} btn.textContent='Copied!'; setTimeout(()=>btn.textContent='Copy',1500);}catch(e){btn.textContent='Error'; console.error('Could not copy:',e);} })(this)\">Copy</button>\n");

    cb.append(
        "    <pre style=\"margin: 0; overflow: auto; min-height: 200px;\"><code style=\"font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; font-size: 14px; line-height: 1.5; user-select: text; display: block; white-space: pre;\" id=\""
            + codeId + "\">"
    ).append(json).append("</code></pre>\n");

    // fallback script in case inline handlers are not executed when inserted via innerHTML
    cb.append("  <script>\n");
    cb.append("    (function() {\n");
    cb.append("      var btn = document.getElementById('" + btnId + "');\n");
    cb.append("      var code = document.getElementById('" + codeId + "');\n");
    cb.append("      if (btn && code) {\n");
    cb.append("        btn.addEventListener('click', async function() {\n");
    cb.append("          var myText = code.innerText.trim();\n");
    cb.append("          try {\n");
    cb.append("            if (navigator.clipboard && navigator.clipboard.writeText) {\n");
    cb.append("              await navigator.clipboard.writeText(myText);\n");
    cb.append("            } else {\n");
    cb.append("              var ta = document.createElement('textarea');\n");
    cb.append("              ta.value = myText;\n");
    cb.append("              document.body.appendChild(ta);\n");
    cb.append("              ta.select();\n");
    cb.append("              document.execCommand('copy');\n");
    cb.append("              ta.remove();\n");
    cb.append("            }\n");
    cb.append("            btn.textContent = 'Copied!';\n");
    cb.append("            setTimeout(function() { btn.textContent = 'Copy'; }, 1500);\n");
    cb.append("          } catch (e) {\n");
    cb.append("            btn.textContent = 'Error';\n");
    cb.append("            console.error('Could not copy:', e);\n");
    cb.append("          }\n");
    cb.append("        });\n");
    cb.append("      }\n");
    cb.append("    })();\n");
    cb.append("  </script>\n");

    cb.append(DIV_CLOSE);
    return cb.toString();
  }

  /**
   * Resolves the MCP context URL using configuration properties. The method checks
   * {@code context.url.copilot.mcp}, falls back to {@code context.url} + port or finally to
   * localhost with the configured MCP port.
   *
   * @param customContextUrl an optional URL to use instead of the configured properties.
   * @return the resolved MCP base URL (including port when applicable).
   */
  public String getContextUrlMCP(String customContextUrl) {
    if (StringUtils.isNotBlank(customContextUrl)) {
      return customContextUrl;
    }
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String contextUrl = properties.getProperty("context.url.copilot.mcp", null);
    if (StringUtils.isNotBlank(contextUrl)) {
      //we have context url for MCP, so must return it
      return contextUrl;
    }
    //fallback using standard context.url and MCP port(COPILOT_PORT_MCP)
    String mcpPort = properties.getProperty("copilot.port.mcp", "5006");
    //fallback using localhost and MCP port(COPILOT_PORT_MCP)
    return LOCALHOST_PREFIX + ":" + mcpPort;
  }

  /**
   * Wraps the provided HTML content into a fixed-width span used by the response builder.
   *
   * @param content HTML content to be wrapped.
   * @return a wrapped HTML string ready to be placed in the response.
   */
  public String buildMessage(String content) {
    String prefix = "<span style=\"width:300px; word-wrap:break-word; display:inline-block;\"> \n";
    String suffix = "</span>\n";

    return prefix + content + suffix;
  }

  @Override
  /**
   * Returns the input class expected by this action.
   *
   * @return the {@link DefinedwebhookToken} class.
   */
  public Class<DefinedwebhookToken> getInputClass() {
    return DefinedwebhookToken.class;
  }

  /**
   * Builds the MCP configuration JSON object for a given agent.
   * The method supports two modes:
   * <ul>
   * <li>Remote compatibility mode: returns an object suitable to run via {@code npx mcp-remote ...}</li>
   * <li>Standard mode: returns an HTTP server configuration with headers</li>
   * </ul>
   * Naming behavior when a {@link ConfigRequest#customName} is provided:
   * <ul>
   * <li>If {@link ConfigRequest#prefixMode} is true, the key will be
   * {@code normalize(customName) + "-" + normalize(agentName)} and the displayed name will be
   * {@code customName + "-" + agentName}.</li>
   * <li>If {@link ConfigRequest#prefixMode} is false, the key will be {@code normalize(customName)}
   * and the displayed name will be {@code customName}.</li>
   * </ul>
   * When no custom name is provided, the key and displayed name fallback to the normalized agent name.
   *
   * @param req
   *     a request holder containing all inputs used to generate the configuration.
   * @return a {@link JSONObject} containing the configuration keyed by the resolved name.
   * @throws JSONException
   *     when JSON construction fails.
   */
  public static JSONObject getConfig(ConfigRequest req) throws JSONException {
    JSONObject config = new JSONObject();

    // Build URL depending on "direct" (shared)
    String url = req.contextUrlMcp + "/" + req.agentId;
    if (req.direct) {
      url += "/direct/mcp";
    } else {
      url += "/mcp";
    }

    // Determine key and display name using customName and prefixMode rules
    String key;
    String displayName;
    if (StringUtils.isNotBlank(req.customName)) {
      if (req.prefixMode) {
        key = normalize(req.customName) + "-" + normalize(req.agentName);
        displayName = req.customName + "-" + req.agentName;
      } else {
        key = normalize(req.customName);
        displayName = req.customName;
      }
    } else {
      key = normalize(req.agentName);
      displayName = req.agentName;
    }

    if (req.mcpRemoteCompatibilityMode) {
      // MCP Remote Compatibility Mode: build remote example with npx args
      JSONObject remoteExample = new JSONObject();
      remoteExample.put("name", displayName);
      remoteExample.put("command", "npx");

      JSONArray args = new JSONArray();
      args.put("mcp-remote");
      args.put(url);
      args.put("--header");
      args.put("Authorization: Bearer " + req.token);

      remoteExample.put("args", args);
      config.put(key, remoteExample);
    } else {
      // Standard MCP server config
      JSONObject myMcpServer = new JSONObject();
      myMcpServer.put("name", displayName);
      myMcpServer.put("url", url);
      myMcpServer.put("type", "http");

      JSONObject headers = new JSONObject();
      headers.put("Authorization", "Bearer " + req.token);
      myMcpServer.put("headers", headers);

      config.put(key, myMcpServer);
    }

    return config;
  }

  /**
   * Normalizes a human-readable name into a safe key by lowercasing and replacing non
   * alphanumeric characters with dashes.
   *
   * @param name input name to normalize.
   * @return a normalized key safe for use as a JSON object key.
   */
  public static String normalize(String name) {
    //remove all chars thath not are alphanumeric or dash or underscore
    return name.toLowerCase().replaceAll("[^a-z0-9-_]", "-");
  }

  /**
   * Helper class to hold parameters for getConfig to avoid long parameter lists.
   */
  public static class ConfigRequest {
    final String agentId;
    final String agentName;
    boolean direct;
    boolean mcpRemoteCompatibilityMode;
    String token;
    String contextUrlMcp;
    String customName;
    boolean prefixMode;

    /**
     * Creates a new ConfigRequest for the given agent id and name.
     *
     * @param agentId   agent identifier
     * @param agentName human readable agent name
     */
    public ConfigRequest(String agentId, String agentName) {
      this.agentId = agentId;
      this.agentName = agentName;
    }

    /**
     * Sets the options used to generate the configuration.
     *
     * @param direct                      whether to use direct endpoint
     * @param mcpRemoteCompatibilityMode  remote compatibility mode flag
     * @param token                       authentication token
     * @param contextUrlMcp               base context URL
     * @param customName                  optional custom name
     * @param prefixMode                  prefix mode flag
     */
    public void setOptions(boolean direct, boolean mcpRemoteCompatibilityMode, String token, String contextUrlMcp,
        String customName, boolean prefixMode) {
      this.direct = direct;
      this.mcpRemoteCompatibilityMode = mcpRemoteCompatibilityMode;
      this.token = token;
      this.contextUrlMcp = contextUrlMcp;
      this.customName = customName;
      this.prefixMode = prefixMode;
    }
  }
}
