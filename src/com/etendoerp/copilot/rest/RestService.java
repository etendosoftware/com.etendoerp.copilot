package com.etendoerp.copilot.rest;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

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
    //parse the json
    JSONObject jsonrequest = new JSONObject(sb.toString());
    //get the question
    JSONObject jsonresponse = new JSONObject();
    //check id assistant is set and is some of the valid assistants IDIDID1, IDIDID2, IDIDID3
    String jsonAssistanID = jsonrequest.optString(ASSISTANT_ID);
    if (jsonrequest.has(ASSISTANT_ID) && (StringUtils.equalsIgnoreCase(jsonAssistanID,
        "IDIDID1") || StringUtils.equalsIgnoreCase(jsonAssistanID, "IDIDID2") || StringUtils.equalsIgnoreCase(
        jsonAssistanID, "IDIDID3"))) {
      jsonresponse.put(ASSISTANT_ID, jsonAssistanID);
    } else {
      jsonresponse.put("error", "Invalid assistant_id");
      response.getWriter().write(jsonresponse.toString());
      return;
    }
    jsonresponse.put("answer", "This is the answer to your question: 42");
    //add timestamp
    jsonresponse.put("timestamp", System.currentTimeMillis());
    //if the conversation_id is not set, set it random uuid . if it is set, use it
    if (!jsonrequest.has(CONVERSATION_ID)) {
      jsonresponse.put(CONVERSATION_ID, java.util.UUID.randomUUID().toString());
    } else {
      jsonresponse.put(CONVERSATION_ID, jsonrequest.getString(CONVERSATION_ID));
    }
    response.getWriter().write(jsonresponse.toString());
  }


  private void handleAssistants(HttpServletResponse response) {
    try {
      //send json of assistants
      JSONArray assistants = new JSONArray();
      JSONObject assistant = new JSONObject();
      //first assistant
      assistant.put(ASSISTANT_ID, "IDIDID1");
      assistant.put("name", "Assistant 1 ");
      assistant.put(IMAGE_BASE_64, IMAGE_EXAMPLE);
      assistants.put(assistant);
      //create another assistant
      assistant = new JSONObject();
      assistant.put(ASSISTANT_ID, "IDIDID2");
      assistant.put("name", "Assistant example 2");
      assistant.put(IMAGE_BASE_64, IMAGE_EXAMPLE);
      assistants.put(assistant);
      //create another assistant
      assistant = new JSONObject();
      assistant.put(ASSISTANT_ID, "IDIDID3");
      assistant.put("name", "Assistant example NR3");
      assistant.put(IMAGE_BASE_64, IMAGE_EXAMPLE);
      assistants.put(assistant);
      response.getWriter().write(assistants.toString());

    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
