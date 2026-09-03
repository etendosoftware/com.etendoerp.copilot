package com.etendoerp.copilot.engine;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * Executes an agent request and produces the answer that
 * {@link com.etendoerp.copilot.rest.RestServiceUtil#processResponseAndTrack} expects.
 * <p>
 * The input is intentionally the very same {@link JSONObject} produced by
 * {@link com.etendoerp.copilot.rest.RestServiceUtil#buildRequestJson}: both implementations
 * consume one identical contract, so they cannot drift apart. An engine must never re-read
 * the agent configuration from the database — everything it needs is already in the payload.
 * <p>
 * Implementations are selected by {@link CopilotEngineSelector}.
 */
public interface CopilotEngine {

  /**
   * Runs the request and, when streaming, writes the server-sent events to the client.
   *
   * @param asyncRequest
   *     {@code true} when the caller expects server-sent events on {@code queue}
   * @param queue
   *     the servlet response used to stream the events; only touched when {@code asyncRequest}
   * @param request
   *     the payload built by {@code buildRequestJson}
   * @param app
   *     the agent serving the request, for logging and engine capability checks
   * @return in synchronous mode the full body ({@code {"answer": {...}}}); in asynchronous mode
   *     the bare {@code answer} object, mirroring what
   *     {@link com.etendoerp.copilot.rest.RestServiceUtil#serverSideEvents} returns
   * @throws IOException
   *     on network or servlet IO errors
   * @throws JSONException
   *     when the payload or the answer cannot be parsed
   */
  JSONObject execute(boolean asyncRequest, HttpServletResponse queue, JSONObject request, CopilotApp app)
      throws IOException, JSONException;
}
