package com.etendoerp.copilot.hook;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

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
  private static final String APPLICATION_JSON = "application/json";
  private static final String APP_ID = "app_id";
  private static final String QUERY = "query";
  private static final String QUESTION = "question";
  private static final String CONVERSATION_ID = "conversation_id";

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
    addAssistantsEndpoint(openAPI);
    addAQuestionEndpoint(openAPI);
    addQuestionEndpoint(openAPI);
    addCacheQuestionEndpoint(openAPI);
    addFileEndpoint(openAPI);
    addConfigCheckEndpoint(openAPI);
    addStructureEndpoint(openAPI);
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
        addMediaType(APPLICATION_JSON, new MediaType()
            .schema(new StringSchema())));
    transcription.responses(new io.swagger.v3.oas.models.responses.ApiResponses().addApiResponse("200", response));
    var pathItem = new io.swagger.v3.oas.models.PathItem();
    pathItem.setPost(transcription);
    openAPI.getPaths().put("/sws/copilot/transcription", pathItem);
  }

  /**
   * Adds the assistants endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows clients to retrieve a list of available assistants.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addAssistantsEndpoint(OpenAPI openAPI) {
    // /assistants endpoint (GET)
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("List available assistants for the current user");
    operation.setTags(OpenAPIDoc.COPILOT_TAG);
    var response = new io.swagger.v3.oas.models.responses.ApiResponse();
    response.setDescription("A list of available assistants for the current user");
    var itemSchema = new ObjectSchema()
            .addProperties("id", new StringSchema().description("Assistant unique ID"))
            .addProperties("name", new StringSchema().description("Assistant name"));
    var arraySchema = new io.swagger.v3.oas.models.media.ArraySchema()
            .items(itemSchema);
    response.content(new Content().addMediaType(APPLICATION_JSON,
            new MediaType().schema(arraySchema)));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", response));
    var pathItem = new io.swagger.v3.oas.models.PathItem();
    pathItem.setGet(operation);
    openAPI.getPaths().put("/sws/copilot/assistants", pathItem);
  }

  /**
   * Adds the aquestion endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows clients to send a question to an assistant and receive a response.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addAQuestionEndpoint(OpenAPI openAPI) {
    // Create the GET /aquestion operation
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Ask a question to a selected assistant");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Sends a question to the selected assistant and returns the answer. " +
            "All parameters are sent as URL query parameters.");
    // Define query parameters
    operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
            .name(APP_ID)
            .in(QUERY)
            .required(true)
            .description("The ID of the assistant to use")
            .schema(new StringSchema()));
    operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
            .name(QUESTION)
            .in(QUERY)
            .required(true)
            .description("The question to ask")
            .schema(new StringSchema()));
    operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
            .name(CONVERSATION_ID)
            .in(QUERY)
            .required(false)
            .description("The conversation ID, if continuing an existing conversation")
            .schema(new StringSchema()));
    operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
            .name("file")
            .in(QUERY)
            .required(false)
            .description("Optional file attachment")
            .schema(new StringSchema()));
    // Response: application/json
    var responseSchema = new ObjectSchema()
            .addProperties(APP_ID, new StringSchema())
            .addProperties(CONVERSATION_ID, new StringSchema())
            .addProperties("response", new StringSchema())
            .addProperties("timestamp", new StringSchema().description("ISO-8601 timestamp"));
    var apiResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("The answer to the user's question")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(responseSchema)));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", apiResponse));
    var pathItem = new io.swagger.v3.oas.models.PathItem().get(operation);
    openAPI.getPaths().put("/sws/copilot/aquestion", pathItem);
  }

  /**
   * Adds the question endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows clients to send a question as a JSON payload and receive a response.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addQuestionEndpoint(OpenAPI openAPI) {
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Ask a question to a selected assistant (JSON body)");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Sends a question to the selected assistant and returns the answer. " +
            "Parameters must be sent in the JSON request body.");
    // Define request body schema
    var requestSchema = new ObjectSchema()
            .addProperties(APP_ID, new StringSchema().description("ID of the assistant to use"))
            .addProperties(QUESTION, new StringSchema().description("The question to ask"))
            .addProperties(CONVERSATION_ID, new StringSchema().description("Optional conversation ID"))
            .addProperties("file", new StringSchema().description("Optional file attachment"))
            .required(List.of(APP_ID, QUESTION));
    var requestBody = new RequestBody()
            .description("JSON object containing the question, assistant ID, and optional parameters")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(requestSchema)));
    operation.setRequestBody(requestBody);
    // Define response schema
    var responseSchema = new ObjectSchema()
            .addProperties(APP_ID, new StringSchema())
            .addProperties(CONVERSATION_ID, new StringSchema())
            .addProperties("response", new StringSchema())
            .addProperties("timestamp", new StringSchema().description("ISO-8601 timestamp"));
    var apiResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("The answer to the user's question")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(responseSchema)));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", apiResponse));
    var pathItem = new io.swagger.v3.oas.models.PathItem().post(operation);
    openAPI.getPaths().put("/sws/copilot/question", pathItem);
  }

  /**
   * Adds the cacheQuestion endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows clients to cache a question by sending it as a JSON payload.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addCacheQuestionEndpoint(OpenAPI openAPI) {
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Cache a question");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Stores a question in the user session for later use.");
    // Request body schema
    var requestSchema = new ObjectSchema()
            .addProperties(QUESTION, new StringSchema().description("The question to cache"))
            .required(List.of(QUESTION));
    var requestBody = new RequestBody()
            .description("JSON object containing the question to cache")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(requestSchema)));

    operation.setRequestBody(requestBody);
    // 200 OK response
    var successResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("The question was cached successfully")
            .content(new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(
                            new ObjectSchema().addProperties("message", new StringSchema())
                    )
            ));
    // 400 error response
    var errorResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("Bad request (missing or invalid question)")
            .content(new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(
                            new ObjectSchema().addProperties("error", new StringSchema())
                    )
            ));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", successResponse)
            .addApiResponse("400", errorResponse)
    );
    var pathItem = new io.swagger.v3.oas.models.PathItem().post(operation);
    openAPI.getPaths().put("/sws/copilot/cacheQuestion", pathItem);
  }

  /**
   * Adds the file endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint allows clients to upload a file using multipart/form-data.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addFileEndpoint(OpenAPI openAPI) {
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Upload a file");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Uploads a file using multipart/form-data.");
    // Request body: multipart/form-data with a "file" field
    var requestSchema = new ObjectSchema()
            .addProperties("file", new StringSchema().format("binary").description("The file to upload"))
            .required(List.of("file"));
    var requestBody = new RequestBody()
            .description("Multipart form with the file to upload")
            .content(new Content().addMediaType("multipart/form-data",
                    new MediaType().schema(requestSchema)));
    operation.setRequestBody(requestBody);
    // Response: assume JSON (you can adjust properties as needed)
    var responseSchema = new ObjectSchema()
            .addProperties("fileId", new StringSchema().description("Uploaded file ID"))
            .addProperties("fileName", new StringSchema().description("Original file name"));
    var apiResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("File upload result")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(responseSchema)));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", apiResponse));
    var pathItem = new io.swagger.v3.oas.models.PathItem().post(operation);
    openAPI.getPaths().put("/sws/copilot/file", pathItem);
  }

  /**
   * Adds the configCheck endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint is used internally to verify connectivity and configuration.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addConfigCheckEndpoint(OpenAPI openAPI) {
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Check server configuration");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Checks the connectivity and configuration of the Etendo host. Used internally.");
    // No request body
    // 200 OK response (empty JSON object)
    var successResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("Configuration check successful")
            .content(new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(new ObjectSchema())
            ));
    // 500 error response
    var errorResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("Internal server error")
            .content(new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(
                            new ObjectSchema().addProperties("error", new StringSchema())
                    )
            ));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", successResponse)
            .addApiResponse("500", errorResponse)
    );
    var pathItem = new io.swagger.v3.oas.models.PathItem().post(operation);
    openAPI.getPaths().put("/sws/copilot/configCheck", pathItem);
  }

  /**
   * Adds the structure endpoint to the provided OpenAPI object.
   * <p>
   * This endpoint returns the structure for the specified assistant.
   *
   * @param openAPI
   *     The OpenAPI object to which the endpoint will be added.
   */
  private static void addStructureEndpoint(OpenAPI openAPI) {
    var operation = new io.swagger.v3.oas.models.Operation();
    operation.setSummary("Get structure for an assistant");
    operation.setTags(COPILOT_TAG);
    operation.setDescription("Returns the structure (configuration or schema) for the assistant specified by app_id.");
    // Query parameter: app_id (required)
    operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
            .name(APP_ID)
            .in(QUERY)
            .required(true)
            .description("The assistant ID")
            .schema(new StringSchema()));
    // Response: application/json, generic object
    var responseSchema = new ObjectSchema()
            .description("Structure object for the specified assistant");
    var apiResponse = new io.swagger.v3.oas.models.responses.ApiResponse()
            .description("Structure response")
            .content(new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(responseSchema)));
    operation.responses(new io.swagger.v3.oas.models.responses.ApiResponses()
            .addApiResponse("200", apiResponse));
    var pathItem = new io.swagger.v3.oas.models.PathItem().get(operation);
    openAPI.getPaths().put("/sws/copilot/structure", pathItem);
  }

}