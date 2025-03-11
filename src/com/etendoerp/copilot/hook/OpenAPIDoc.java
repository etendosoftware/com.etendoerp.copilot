package com.etendoerp.copilot.hook;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.etendoerp.openapi.model.OpenAPIEndpoint;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;

/**
 * Class responsible for adding Copilot-specific endpoints to the OpenAPI documentation.
 */
public class OpenAPIDoc implements OpenAPIEndpoint {
  private static final List<String> COPILOT_TAG = Collections.singletonList("Copilot");

  /**
   * Checks if the provided tag is valid for this endpoint.
   *
   * @param tag
   *     The tag to check.
   * @return true if the tag is "copilot", false otherwise.
   */
  @Override
  public boolean isValid(String tag) {
    return StringUtils.equalsIgnoreCase(tag, "copilot");
  }

  /**
   * Adds the Copilot endpoints to the provided OpenAPI object.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoints will be added.
   */
  @Override
  public void add(OpenAPI openAPI) {
    var paths = openAPI.getPaths();
    if (paths == null) {
      paths = new Paths();
      openAPI.setPaths(paths);
    }
    addTranscriptionEndpoint(openAPI);
  }

  /**
   * Adds the transcription endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows for the transcription of audio files to text.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addTranscriptionEndpoint(OpenAPI openAPI) {
    // Add the copilot endpoints for the REST API
    // 1. add the endpoint of /transcription, that receives a file and returns a transcription
    var transcription = new io.swagger.v3.oas.models.Operation();
    transcription.setSummary("Transcribe an audio file to text");
    transcription.setTags(COPILOT_TAG);
    RequestBody request = new RequestBody();
    request.setDescription("The audio file to transcribe");
    request.content(new Content()
        .addMediaType("multipart/form-data", new MediaType()
            .schema(new ObjectSchema()
                .addProperties("file",
                    new StringSchema()
                        .format("binary")))));
    transcription.setRequestBody(request);
    var response = new io.swagger.v3.oas.models.responses.ApiResponse();
    response.setDescription("The transcription of the audio file");
    response.content(new Content().
        addMediaType("application/json", new MediaType()
            .schema(new StringSchema())));
    transcription.responses(new io.swagger.v3.oas.models.responses.ApiResponses().addApiResponse("200", response));
    var pathItem = new io.swagger.v3.oas.models.PathItem();
    pathItem.setPost(transcription);
    openAPI.getPaths().put("/sws/copilot/transcription", pathItem);
  }
}
