package com.etendoerp.copilot.rest;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CopilotJwtServlet extends HttpBaseServlet {
  private static final Logger log4j = LogManager.getLogger(CopilotJwtServlet.class);
  private static RestService instance;

  public static RestService getInstance() {
    if (instance == null) {
      instance = new RestService();
    }
    return instance;
  }

  private String obtainToken(HttpServletRequest request) {
    String authStr = request.getHeader("Authorization");
    String token = null;
    if (authStr != null && authStr.startsWith("Bearer ")) {
      token = authStr.substring(7);
    }
    return token;
  }

  private void checkJwt(HttpServletRequest request) throws Exception {
    DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(obtainToken(request));
    String userId = decodedToken.getClaim("user").asString();
    String roleId = decodedToken.getClaim("role").asString();
    String orgId = decodedToken.getClaim("organization").asString();
    String warehouseId = decodedToken.getClaim("warehouse").asString();
    String clientId = decodedToken.getClaim("client").asString();
    if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty()) {
      throw new OBException("SWS - Token is not valid");
    }
    log4j.debug("SWS accessed by userId {}", userId);
    OBContext.setOBContext(
        SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId));
    OBContext.setOBContextInSession(request, OBContext.getOBContext());
  }

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
