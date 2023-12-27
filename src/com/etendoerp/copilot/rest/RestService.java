package com.etendoerp.copilot.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.copilot.data.CopilotApp;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBDal;

public class RestService extends HttpSecureAppServlet {

  public static final String QUESTION = "/question";
  public static final String GET_ASSISTANTS = "/assistants";
  public static final String CONVERSATION_ID = "conversation_id";
  public static final String ASSISTANT_ID = "assistant_id";
  public static final String IMAGE_BASE_64 = "image-base64";
  public static final String IMAGE_EXAMPLE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAFwAAABcCAMAAADUMSJqAAAApVBMVEX/zE3///9mRQD/zk7/ykT/y0lbOgD/0U//1FH/yT3/4qX/z1r//PZkQwBfPgD/8dXWrD7/yDb/9uT/78//5Kz/13//6bv/4J//1HD/+ezNpDpWNQD/1ndeOQD1xkpSMQBXLgBSJgDjtkNtSwx/XRa/lzSkfymRbSCwiS6bdiW4kDGGZBmDbE6XhXDf2tO6r6h1WjppSRuyppaHcFnMxLvv7OmdjH1TXNZ1AAAE50lEQVRogdWZa3eiMBCG0QgBgaj10q6WiEAVrddW+/9/2oarJhmQipyz+35qDTzMmUwmk4nSalDKvwC3XoaTUZdpNBm+WM+DW73u2DQGhtGJZbA/zXG3V+EL9+DW5L1jdFRV4aSq7Mf3yT1+OXw4HhgC9+YLxmA8fBg+MYvJGd+cPASfqPfQCV4txhfBe6Zxn5zIMHu/glvjQQWrc+sHY3hqQfhQ6VRHR+oo4MxC8NffmJ0Z/1oN/lbZ27cy3irALfOXLsnUMSXHi/A/4mKsLlX9Uw6vwQboPNyqw47oVgncrMVmdLMY/vbgXF7VeSuCvz4Ug7yMVxg+HNRnK8pgCMEtpabDE6mKBcDHtR2eqDOW4b2nOCXSoCfB60bhVdd4zOCTJ0RKJmMiwJ9mdySVhz/T8KvpSpnHEdZ1HSOYUDKYeT2BD0HDEV36mh9SEIBoyAaX8KAxvIGPQcOpTey2TXwIgKgfD9oUNH18hVtgjOsBaUciKx0YXKWDATDIYt3K4eB0IjpvJ5orkulIyQdBxyRTGsPfIa+gqZa+r/VleL9kkEl9z+AWmFWqwqfglHasFN6DY6UW3Oil8G4TlndTOByI9eBxMCrFy7Me3EzgFpxXUH+Wvj/zZLiXD4LRwpxuxfCXgm0CZ+9rGBjMLJ8Bg5EGLzEcTizs/TChzzYQfJMOhgXwKL0oJekWr2eEkNkafB0vSwaVZI0y+KhwZ9a9TbjxwORxZ5DF4iiGw2EeC2FclM7vDMaBXg6vof8eXjyhteCj4lAsnqmKD6ehKC8ihD2PFu/7/KM6pp4nP5ouImn5IxpoGvFXmymNwg0jJNrGfkhG6HSz8ommBdJmly5/KXFhP9p8bZtocxKsw2nfozSuUlIxJvX603AdkLlGbDvap31xpaaJS0y513QafYIQbabZvu8HwefnavX5GQTsH5v9SIh9fU5My1nKFTcLHMaGtznZnISxyHQhgeWbhRDoeMng9nbhOkTEiGKuc9zFlj1Fljw83+aEDTqGk11rf9gdT47rOCz78V9hTEIcNnQ67g771o7I8HyDFkoLvInc4h7iWuyyP3/97L6PJ+IuUrnkdPze/Xyd95e4rjq4kVuEpJ+XFkJRlE6oe25JsoCmzdkFJvRaFAlrFNFkk1kcZJKswyLZkfhAvynnhEJUDxIPu7v77J2bzIJQj94UokIw5kWFcwJcw7nk5KRhzhcYtyW0kF6Q4mdh4X7vi9H7bzcLIp9PEFzxLyxS/JGvUeIeDxeIfDkcFyRfnx9crPDHFjHtIv8a17bjbL/O3Acu56+t49w8ImQW4cAlHBWvBVXGdxen7e6Habc9sbXrcItKLMmEo6JoOg61tqBoVcaSUoIWwoYXHs+zM08FiWcm+XguNhYQCirSSYB4pwCNBaklUpHO2Px7UEtEbubgleR3WdpK2ILgZo7UhkKszL2Xz1mRKxgOt6GABpre90uN1/y+WIgWNdCA1h9C4bzQ82QeInHPL279AacjhOmaaIBzbI2sqVStlDUtoXYr0unGn/MrxyZzf0N1qRAqb7fCjWKMaVL6JIrKJYqBE8WdRnFhGzou2qYfTFOPsn+gZ+62uMua8yir7WBVaM63Gr1WaDV7IdLsVU6zl1CtRq/PIjV48Rfjm7uyjNTgZWukBq+JEzV2wZ2pqav5x9Uo/C8XwGOZ82ZPZAAAAABJRU5ErkJggg==";


  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
      handleAssistants(response);
      return;
    }
    //if not a valid path, throw a error status
    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
      try {
        handleQuestion(request, response);
      } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      }
      return;
    }
    //if not a valid path, throw a error status
    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private void handleQuestion(HttpServletRequest request,
      HttpServletResponse response) throws IOException, JSONException {
    // read the json sent
    BufferedReader reader = request.getReader();
    StringBuilder sb = new StringBuilder();
    for (String line; (line = reader.readLine()) != null; ) {
      sb.append(line);
    }
    HttpResponse<String> jsonresponse = null;
    try {
      var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
      HttpClient client = HttpClient.newBuilder().build();
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI("http://localhost:" + properties.getProperty("COPILOT_PORT") + "/question"))
          .headers("Content-Type", "application/json;charset=UTF-8")
          .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
          .build();

      jsonresponse = client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (URISyntaxException | InterruptedException e) {
      log4j.error(e);
      throw new OBException("Cannot connect to Copilot service");
    }
    JSONObject responseJson = new JSONObject(jsonresponse.body());
    JSONObject response2 = new JSONObject();
    response2.put("assistant_id", ((JSONObject) responseJson.get("answer")).get("assistant_id"));
    response2.put("conversation_id", ((JSONObject) responseJson.get("answer")).get("conversation_id"));
    response2.put("answer", ((JSONObject) responseJson.get("answer")).get("message"));
    Date date = new Date();
    //getting the object of the Timestamp class
    Timestamp tms = new Timestamp(date.getTime());
    response2.put("timestamp", tms.toString());

    response.getWriter().write(response2.toString());
  }


  private void handleAssistants(HttpServletResponse response) {
    try {
      //send json of assistants
      JSONArray assistants = new JSONArray();
      for (CopilotApp copilotApp : OBDal.getInstance().createQuery(CopilotApp.class, "").list()) {
        JSONObject assistantJson = new JSONObject();
        assistantJson.put(ASSISTANT_ID, copilotApp.getOpenaiIdAssistant());
        assistantJson.put("name", copilotApp.getName());
        assistants.put(assistantJson);
      }
      response.getWriter().write(assistants.toString());
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
