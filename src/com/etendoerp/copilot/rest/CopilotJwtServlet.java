package com.etendoerp.copilot.rest;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
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

  /**
   * Get the instance of the RestService
   *
   * @return
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
   * @return
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
    try{
      DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(obtainToken(request));
      String userId = getRequiredClaim(decodedToken, "user");
      String roleId = getRequiredClaim(decodedToken, "role");
      String orgId = getRequiredClaim(decodedToken, "organization");
      String warehouseId = getRequiredClaim(decodedToken, "warehouse");
      String clientId = getRequiredClaim(decodedToken, "client");

      OBContext context = SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId);
      OBContext.setOBContext(context);
      OBContext.setOBContextInSession(request, context);
    } catch (JWTDecodeException e) {
      log4j.warn("Invalid token format: " + e.getMessage(), e);
      throw new OBException(OBMessageUtils.messageBD("ETCOP_SWS_TokenInvalid"));
    }
  }

  private String getRequiredClaim(DecodedJWT token, String claimName) throws OBException {
    String claimValue = token.getClaim(claimName).asString();
    if (claimValue == null || claimValue.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_SWS_TokenInvalid"));
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
   * @param request  an {@link HttpServletRequest} object
   * @param response an {@link HttpServletResponse} object
   * @param method   the HTTP method ("GET" or "POST")
   * @throws IOException
   */
  private void processRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
    try {
      checkJwt(request);
    } catch (Exception e) {
      log4j.warn("Unauthorized request: " + e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }

    try {
      if ("GET".equals(method)) {
        getInstance().doGet(request, response);
      } else if ("POST".equals(method)) {
        getInstance().doPost(request, response);
      }
    } catch (Exception e) {
      log4j.error("Error during " + method + " request: " + e.getMessage(), e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error("Error while sending error response: " + ioException.getMessage(), ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
      }
    }
  }
}
