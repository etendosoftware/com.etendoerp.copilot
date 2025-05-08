package com.etendoerp.copilot.rest;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * This servlet has two main responsibilities: 1) authenticate, 2) set the
 * correct {@link OBContext} , and 3) translate Exceptions into the correct Http
 * response code.
 * <p>
 * In regard to authentication: there is support for basic-authentication as
 * well as url parameter based authentication.
 * </p>
 */
public class CopilotJwtServlet extends HttpBaseServlet {
  private static final Logger log4j = LogManager.getLogger(CopilotJwtServlet.class);
  private static RestService instance;
  private static final String TOKEN_INVALID_MESSAGE = "ETCOP_SWS_TokenInvalid";


  /**
   * Get the instance of the RestService
   */
  public static RestService getInstance() {
    if (instance == null) {
      instance = new RestService();
    }
    return instance;
  }

  /**
   * Obtain the token from the request
   *
   * @param request
   */
  private String obtainToken(HttpServletRequest request) {
    String authStr = request.getHeader("Authorization");
    String token = null;
    if (authStr != null && authStr.startsWith("Bearer ")) {
      token = authStr.substring(7);
    }
    return token;
  }

  /**
   * Check the JWT token
   *
   * @param request
   * @throws Exception
   */
  private void checkJwt(HttpServletRequest request) throws Exception {
    try {
      DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(obtainToken(request));
      String userId = getRequiredClaim(decodedToken, "ad_user_id", "user");
      String roleId = getRequiredClaim(decodedToken, "ad_role_id", "role");
      String orgId = getRequiredClaim(decodedToken, "ad_org_id", "organization");
      String warehouseId = getRequiredClaim(decodedToken, "m_warehouse_id", "warehouse");
      String clientId = getRequiredClaim(decodedToken, "ad_client_id", "client");

      OBContext context = SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId);
      OBContext.setOBContext(context);
      OBContext.setOBContextInSession(request, context);
    } catch (JWTDecodeException e) {
      log4j.warn("Invalid token format: " + e.getMessage(), e);
      throw new OBException(OBMessageUtils.messageBD(TOKEN_INVALID_MESSAGE));
    }
  }

  private String getRequiredClaim(DecodedJWT token, String claimName, String alternativeClaimName) throws OBException {
    String claimValue = token.getClaim(claimName).asString();
    if (StringUtils.isEmpty(claimValue) && alternativeClaimName != null) {
      claimValue = token.getClaim(alternativeClaimName).asString();
    }
    if (StringUtils.isEmpty(claimValue)) {
      throw new OBException(OBMessageUtils.messageBD(TOKEN_INVALID_MESSAGE));
    }
    return claimValue;
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    processRequest(request, response, "GET");
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    processRequest(request, response, "POST");
  }

  /**
   * Process the request for both GET and POST methods.
   *
   * @param request
   *     an {@link HttpServletRequest} object
   * @param response
   *     an {@link HttpServletResponse} object
   * @param method
   *     the HTTP method ("GET" or "POST")
   * @throws IOException
   */
  private void processRequest(HttpServletRequest request, HttpServletResponse response,
      String method) throws IOException {
    try {
      checkJwt(request);
    } catch (Exception e) {
      log4j.warn("Unauthorized request: " + e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }

    try {
      switch (method) {
        case "GET":
          getInstance().doGet(request, response);
          break;
        case "POST":
          getInstance().doPost(request, response);
          break;
        default:
          log4j.warn("Unsupported HTTP method: " + method);
          response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          break;
      }
    } catch (Exception e) {
      log4j.error("Error during " + method + " request: " + e.getMessage(), e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error("Error while sending error response: " + ioException.getMessage(), ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
  }
}
