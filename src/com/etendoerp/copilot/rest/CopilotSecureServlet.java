package com.etendoerp.copilot.rest;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CopilotSecureServlet extends HttpSecureAppServlet {
  private static RestService instance;

  public static RestService getInstance() {
    if(instance == null) {
      instance = new RestService();
    }
    return instance;
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    getInstance().doGet(request, response);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    getInstance().doPost(request, response);
  }

}
