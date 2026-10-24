/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License.
 *************************************************************************
 */
package com.etendoerp.copilot.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.engine.tool.ToolExecutionException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.engine.CopilotEngine;
import com.etendoerp.copilot.engine.CopilotEngineSelector;
import com.etendoerp.copilot.engine.mcp.McpProtocolHandler;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.openapi.OpenApiToolGenerator;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry.ResolvedTools;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.ToolsUtil;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/** In-process, SWS-protected Streamable HTTP MCP endpoint for Copilot agents. */
public class CopilotMcpServlet extends HttpBaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger LOG = LogManager.getLogger(CopilotMcpServlet.class);
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final String PROTECTED_RESOURCE_METADATA_PATH =
      "/.well-known/oauth-protected-resource";
  private static final String MCP_SERVLET_PATH = "/sws/copilot/mcp";
  private final ServerToolRegistry registry = new ServerToolRegistry();

  @Override
  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, MCP-Protocol-Version, MCP-Session-Id");
    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = request.getPathInfo();
    if ("/.well-known/oauth-protected-resource".equals(path)) {
      response.setContentType("application/json");
      response.getWriter().write("{\"resource\":\"/sws/copilot/mcp\",\"authorization_servers\":[]}");
      return;
    }
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    response.setHeader("Allow", "POST, OPTIONS");
  }
  private String protectedResourceMetadataUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    int port = request.getServerPort();
    boolean standardPort = ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
    String origin = scheme + "://" + request.getServerName() + (standardPort ? "" : ":" + port);
    return origin + request.getContextPath() + MCP_SERVLET_PATH + PROTECTED_RESOURCE_METADATA_PATH;
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json; charset=utf-8");
    response.setHeader("Cache-Control", "no-store");
    if (request.getContentLengthLong() > McpProtocolHandler.MAX_REQUEST_BYTES) {
      response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return;
    }
    OBContext previous = OBContext.getOBContext();
    try {
      String[] route = route(request.getPathInfo());
      if (route == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      authenticate(request);
      CopilotApp app = resolveAuthorizedAgent(route[0]);
      String body = readBounded(request);
      JSONObject rpc = new JSONObject(body);
      String requestedVersion = request.getHeader("MCP-Protocol-Version");
      if (StringUtils.isNotBlank(requestedVersion)
          && !McpProtocolHandler.supportsProtocolVersion(requestedVersion)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported MCP protocol version");
        return;
      }
      McpProtocolHandler protocol = new McpProtocolHandler(provider(app, "direct".equals(route[1])));
      JSONObject result = protocol.handle(rpc);
      if (result == null) {
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
      } else {
        String responseVersion = "initialize".equals(rpc.optString("method"))
            ? protocol.responseVersion(rpc)
            : StringUtils.defaultIfBlank(requestedVersion, McpProtocolHandler.PROTOCOL_VERSION);
        response.setHeader("MCP-Protocol-Version", responseVersion);
        response.getWriter().write(result.toString());
      }
    } catch (UnauthorizedException e) {
      response.setHeader("WWW-Authenticate",
          "Bearer resource_metadata=\"" + protectedResourceMetadataUrl(request) + "\"");
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    } catch (JSONException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON-RPC request");
    } catch (Exception e) {
      LOG.warn("Copilot MCP request failed ({})", e.getClass().getSimpleName());
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "MCP request could not be processed");
    } finally {
      OBContext.setOBContext(previous);
    }
  }

  private void authenticate(HttpServletRequest request) throws UnauthorizedException {
    String token = bearer(request.getHeader("Authorization"));
    if (StringUtils.isBlank(token)) {
      token = StringUtils.defaultIfBlank(request.getHeader("etendo-token"), request.getHeader("x-etendo-token"));
    }
    if (StringUtils.isBlank(token)) {
      token = request.getParameter("token");
    }
    if (StringUtils.isBlank(token)) {
      throw new UnauthorizedException();
    }
    try {
      DecodedJWT decoded = SecureWebServicesUtils.decodeToken(token);
      String userId = claim(decoded, "ad_user_id", "user");
      String roleId = claim(decoded, "ad_role_id", "role");
      String orgId = claim(decoded, "ad_org_id", "organization");
      String warehouseId = claim(decoded, "m_warehouse_id", "warehouse");
      String clientId = claim(decoded, "ad_client_id", "client");
      OBContext context = SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId);
      if (context == null) {
        throw new UnauthorizedException();
      }
      OBContext.setOBContext(context);
    } catch (JWTDecodeException | OBException e) {
      throw new UnauthorizedException();
    } catch (Exception e) {
      throw new UnauthorizedException();
    }
  }

  private String bearer(String authorization) {
    return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
        ? authorization.substring(7).trim() : null;
  }

  private String claim(DecodedJWT jwt, String primary, String alternative) throws UnauthorizedException {
    String value = jwt.getClaim(primary).asString();
    if (StringUtils.isBlank(value)) {
      value = jwt.getClaim(alternative).asString();
    }
    if (StringUtils.isBlank(value)) {
      throw new UnauthorizedException();
    }
    return value;
  }

  private CopilotApp resolveAuthorizedAgent(String id) throws UnauthorizedException {
    try {
      OBContext.setAdminMode();
      CopilotApp app = OBDal.getInstance().get(CopilotApp.class, id);
      if (app == null || !app.isActive()) {
        throw new UnauthorizedException();
      }
      Role role = OBContext.getOBContext().getRole();
      CopilotRoleApp assignment = (CopilotRoleApp) OBDal.getInstance().createCriteria(CopilotRoleApp.class)
          .add(org.hibernate.criterion.Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, app))
          .add(org.hibernate.criterion.Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role))
          .setMaxResults(1).uniqueResult();
      if (assignment == null) {
        throw new UnauthorizedException();
      }
      return app;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private McpProtocolHandler.ToolProvider provider(CopilotApp app, boolean direct)
      throws JSONException, IOException {
    JSONObject request = new JSONObject();
    JSONArray descriptors = ToolsUtil.getToolSet(app);
    request.put(RestServiceUtil.PROP_TOOLS, descriptors);
    request.put("specs", CopilotUtils.getAppSpecs(app));
    ResolvedTools tools = registry.resolve(request, false,
        new OpenApiToolGenerator().tools(request.optJSONArray("specs")));
    ToolExecutionContext context = ToolExecutionContext.capture(app, null, null);
    List<CopilotApp> teamAgents = new ArrayList<>();
    if (direct && app.getETCOPTeamMemberList() != null) {
      for (TeamMember member : app.getETCOPTeamMemberList()) {
        if (member.getMember() != null) {
          teamAgents.add(member.getMember());
        }
      }
    }
    boolean directSupervisor = !teamAgents.isEmpty();
    List<JSONObject> definitions = new ArrayList<>();
    for (var specification : tools.specifications()) {
      JSONObject inputSchema = inputSchema(descriptors, specification.name());
      JSONObject definition = new JSONObject().put("name", specification.name())
          .put("description", specification.description() == null ? "" : specification.description())
          .put("inputSchema", inputSchema);
      definitions.add(definition);
    }
    if (directSupervisor) {
      definitions.clear();
      for (CopilotApp member : teamAgents) {
        definitions.add(askAgentDefinition(member));
      }
    } else if (direct) {
      definitions.add(new JSONObject().put("name", "get_agent_prompt")
          .put("description", "Returns the configured system prompt for this agent.")
          .put("inputSchema", new JSONObject().put("type", "object").put("properties", new JSONObject())));
    } else {
      definitions.clear();
      definitions.add(askAgentDefinition(app));
    }
    JSONArray listed = new JSONArray();
    for (JSONObject definition : definitions) {
      listed.put(definition);
    }
    return new McpProtocolHandler.ToolProvider() {
      @Override
      public JSONArray list() {
        return listed;
      }

      @Override
      public McpProtocolHandler.ToolResult call(String name, JSONObject arguments) {
        if (directSupervisor) {
          for (CopilotApp member : teamAgents) {
            if (askAgentToolName(member).equals(name)) {
              return askAgent(member, arguments);
            }
          }
          return null;
        }
        if (direct && "get_agent_prompt".equals(name)) {
          return new McpProtocolHandler.ToolResult(app.getPrompt(), false);
        }
        if (direct) {
          for (var specification : tools.specifications()) {
            if (specification.name().equals(name)) {
              var tool = tools.tool(name);
              if (tool.needsWebService() && !context.webServiceEnabled()) {
                return new McpProtocolHandler.ToolResult("Tool requires a role with web services enabled.", true);
              }
              try {
                String text = context.call(tool.timeout(), () -> tool.execute(arguments, context));
                return new McpProtocolHandler.ToolResult(text, false);
              } catch (Exception e) {
                String message = safeToolFailure(e);
                LOG.warn("MCP server tool execution failed ({}): {}", e.getClass().getSimpleName(), message);
                return new McpProtocolHandler.ToolResult(message, true);
              }
            }
          }
          return null;
        }
        return askAgentToolName(app).equals(name) ? askAgent(app, arguments) : null;
      }
    };
  }
  private static String safeToolFailure(Exception failure) {
    Throwable detail = failure;
    if (failure.getCause() instanceof IllegalArgumentException
        || failure.getCause() instanceof java.io.FileNotFoundException) {
      detail = failure.getCause();
    } else if (!(failure instanceof IllegalArgumentException)
        && !(failure instanceof java.io.FileNotFoundException)
        && !(failure instanceof com.etendoerp.copilot.engine.tool.ToolExecutionException)) {
      return "Tool execution failed.";
    }
    String message = detail.getMessage();
    if (StringUtils.isBlank(message)) return "Tool execution failed.";
    return message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [REDACTED]")
        .replaceAll("(?i)(token|api[_-]?key|password)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
  }

  private JSONObject askAgentDefinition(CopilotApp app) throws JSONException {
    return new JSONObject().put("name", askAgentToolName(app))
        .put("description", "Ask the configured Copilot agent a question.")
        .put("inputSchema", new JSONObject().put("type", "object")
            .put("properties", new JSONObject().put("question", new JSONObject().put("type", "string"))
                .put("conversation_id", new JSONObject().put("type", "string")))
            .put("required", new JSONArray().put("question")));
  }

  private String askAgentToolName(CopilotApp app) {
    String name = StringUtils.defaultIfBlank(app.getName(), "unknown_agent")
        .replaceAll("[^0-9a-zA-Z]+", "_").replaceAll("^_+|_+$", "");
    if (StringUtils.isBlank(name)) {
      name = "unknown_agent";
    }
    if (Character.isDigit(name.charAt(0))) {
      name = "agent_" + name;
    }
    return "ask_agent_" + name;
  }

  private McpProtocolHandler.ToolResult askAgent(CopilotApp app, JSONObject arguments) {
    try {
      JSONObject questionRequest = new JSONObject();
      CopilotUtils.buildLangchainRequestForCopilot(app, arguments.optString("conversation_id", null),
          questionRequest, app.getAppType());
      questionRequest.put("question", arguments.optString("question", ""));
      CopilotEngine engine = CopilotEngineSelector.get(questionRequest, app);
      JSONObject answer = engine.execute(false, null, questionRequest, app);
      JSONObject answerContent = answer.optJSONObject("answer");
      return new McpProtocolHandler.ToolResult(answerContent == null
          ? answer.optString("response", answer.toString()) : answerContent.optString("response"), false);
    } catch (Exception e) {
      return new McpProtocolHandler.ToolResult("The agent could not answer this request.", true);
    }
  }

  private JSONObject inputSchema(JSONArray descriptors, String toolName) throws JSONException {
    for (int i = 0; i < descriptors.length(); i++) {
      JSONObject descriptor = descriptors.optJSONObject(i);
      JSONObject function = descriptor == null ? null : descriptor.optJSONObject("function");
      if (function != null && toolName.equals(function.optString("name"))) {
        JSONObject parameters = function.optJSONObject("parameters");
        if (parameters != null) {
          return parameters;
        }
      }
    }
    return new JSONObject().put("type", "object").put("properties", new JSONObject());
  }

  private String[] route(String path) {
    if (path == null) {
      return null;
    }
    String[] segments = path.split("/");
    if (segments.length == 3 && IDENTIFIER.matcher(segments[1]).matches() && "mcp".equals(segments[2])) {
      return new String[] { segments[1], "standard" };
    }
    if (segments.length == 4 && IDENTIFIER.matcher(segments[1]).matches()
        && "direct".equals(segments[2]) && "mcp".equals(segments[3])) {
      return new String[] { segments[1], "direct" };
    }
    return null;
  }

  private String readBounded(HttpServletRequest request) throws IOException {
    StringBuilder body = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      char[] buffer = new char[4096];
      int count;
      while ((count = reader.read(buffer)) >= 0) {
        if (body.length() + count > McpProtocolHandler.MAX_REQUEST_BYTES) {
          throw new IOException("MCP request is too large");
        }
        body.append(buffer, 0, count);
      }
    }
    return body.toString();
  }

  private static final class UnauthorizedException extends Exception {
    private static final long serialVersionUID = 1L;
  }
}
