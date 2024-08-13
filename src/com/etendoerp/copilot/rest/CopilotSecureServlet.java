package com.etendoerp.copilot.rest;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

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
public class CopilotSecureServlet extends HttpSecureAppServlet {
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
   * @param request  an {@link HttpServletRequest} object that
   *                 contains the request the client has made
   *                 of the servlet
   * @param response an {@link HttpServletResponse} object that
   *                 contains the response the servlet sends
   *                 to the client
   * @throws IOException
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    getInstance().doGet(request, response);
  }

  /**
   * Obtain the token from the request
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
    getInstance().doPost(request, response);
  }

}
