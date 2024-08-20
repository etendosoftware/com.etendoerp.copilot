package com.etendoerp.copilot.rest;

import com.auth0.jwt.interfaces.DecodedJWT;
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
    DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(obtainToken(request));
    String userId = decodedToken.getClaim("user").asString();
    String roleId = decodedToken.getClaim("role").asString();
    String orgId = decodedToken.getClaim("organization").asString();
    String warehouseId = decodedToken.getClaim("warehouse").asString();
    String clientId = decodedToken.getClaim("client").asString();
    if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_SWS_TokenInvalid"));
    }
    OBContext.setOBContext(
        SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId));
    OBContext.setOBContextInSession(request, OBContext.getOBContext());
  }

  /**
   * Handle the GET request
   *
   * @param request
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      checkJwt(request);
    } catch (Exception e) {
      log4j.warn(e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    try {
      getInstance().doGet(request, response);
    } catch (Exception e) {
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
  }

  /**
   * Handle the POST request
   *
   * @param request  an {@link HttpServletRequest} object that
   *                 contains the request the client has made
   *                 of the servlet
   * @param response an {@link HttpServletResponse} object that
   *                 contains the response the servlet sends
   *                 to the client
   * @throws IOException
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      checkJwt(request);
    } catch (Exception e) {
      log4j.warn(e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    try {
      getInstance().doPost(request, response);
    } catch (Exception e) {
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
  }
}
