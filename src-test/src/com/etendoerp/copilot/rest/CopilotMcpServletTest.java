/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License.
 *************************************************************************
 */
package com.etendoerp.copilot.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

public class CopilotMcpServletTest {
  @Test
  public void rejectsMissingSwsBearerTokenWithProtectedResourceChallenge() throws Exception {
    CopilotMcpServlet servlet = new CopilotMcpServlet();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/agent-id/mcp");
    when(request.getContentLengthLong()).thenReturn(0L);
    when(request.getScheme()).thenReturn("http");
    when(request.getServerName()).thenReturn("localhost");
    when(request.getServerPort()).thenReturn(8082);
    when(request.getContextPath()).thenReturn("/etendo");

    servlet.doPost(request, response);

    verify(response).setHeader("WWW-Authenticate",
        "Bearer resource_metadata=\"http://localhost:8082/etendo/sws/copilot/mcp/.well-known/oauth-protected-resource\"");
    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void rejectsNonPostStreamingRequests() throws Exception {
    CopilotMcpServlet servlet = new CopilotMcpServlet();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    servlet.doGet(request, response);

    verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    verify(response).setHeader("Allow", "POST, OPTIONS");
  }
}
