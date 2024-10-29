package com.etendoerp.copilot.rest;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import com.etendoerp.copilot.rest.RestService;

import com.etendoerp.copilot.util.CopilotConstants;

import static com.etendoerp.copilot.rest.RestService.CACHED_QUESTION;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link RestService} class.
 */
public class RestServiceTest {

  private RestService restService;
  private HttpServletRequest mockRequest;
  private HttpServletResponse mockResponse;
  private HttpSession mockSession;

  /**
   * Sets up the necessary mocks and spies before each test.
   */
  @Before
  public void setUp() {
    restService = spy(new RestService());
    mockRequest = mock(HttpServletRequest.class);
    mockResponse = mock(HttpServletResponse.class);
    mockSession = mock(HttpSession.class);
  }

  /**
   * Tests the {@link RestService#handleQuestion(HttpServletRequest, HttpServletResponse)} method when
   * handling a synchronous request with valid parameters.
   *
   * @throws IOException if there is an issue with the request or response I/O operations.
   * @throws JSONException if there is an issue with JSON processing.
   */
  @Test
  public void testHandleQuestionWithSyncRequest() throws IOException, JSONException {
    when(mockRequest.getSession()).thenReturn(mockSession);
    when(mockRequest.getPathInfo()).thenReturn("/test");
    when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{ \"key\": \"value\" }")));
    when(mockRequest.getSession().getAttribute(CACHED_QUESTION)).thenReturn("");

    when(mockRequest.getParameter(CopilotConstants.PROP_QUESTION)).thenReturn("Hello, this is a question");
    when(mockRequest.getParameter(CopilotConstants.PROP_APP_ID)).thenReturn("PROP_APP_ID");
    when(mockRequest.getParameter(CopilotConstants.PROP_CONVERSATION_ID)).thenReturn("PROP_CONVERSATION_ID");

    JSONObject json = new JSONObject();
    json.put((CopilotConstants.PROP_QUESTION), "Hello, this is a question");
    json.put((CopilotConstants.PROP_APP_ID), "PROP_APP_ID");
    json.put((CopilotConstants.PROP_CONVERSATION_ID), "PROP_CONVERSATION_ID");

    doNothing().when(restService).processSyncRequest(any(HttpServletResponse.class), any(JSONObject.class));
    doReturn(false).when(restService).isAsyncRequest(mockRequest);

    restService.handleQuestion(mockRequest, mockResponse);
  }

  /**
   * Tests that {@link RestService#handleQuestion(HttpServletRequest, HttpServletResponse)} throws
   * a {@link JSONException} when the request body extraction fails.
   *
   * @throws IOException if there is an issue with the request or response I/O operations.
   * @throws JSONException if there is an issue with JSON processing.
   * @throws NullPointerException if any of the required objects are null.
   */
  @Test(expected = JSONException.class)
  public void testHandleQuestionThrowsJSONException() throws IOException, JSONException, NullPointerException {
    when(mockRequest.getReader()).thenThrow(new IOException("Body request extraction failed."));

    restService.handleQuestion(mockRequest, mockResponse);
  }

  /**
   * Tests that {@link RestService#handleQuestion(HttpServletRequest, HttpServletResponse)} throws
   * a {@link JSONException} when an invalid JSON is provided in the request body.
   *
   * @throws IOException if there is an issue with the request or response I/O operations.
   * @throws JSONException if there is an issue with JSON processing.
   * @throws NullPointerException if any of the required objects are null.
   */
  @Test(expected = JSONException.class)
  public void testHandleQuestionWithInvalidJson() throws IOException, JSONException, NullPointerException {
    when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

    restService.handleQuestion(mockRequest, mockResponse);
  }
}
