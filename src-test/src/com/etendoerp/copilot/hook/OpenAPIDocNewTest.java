package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Tests for OpenAPIDoc that exercise real code paths with actual OpenAPI objects.
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenAPIDocNewTest {

  private OpenAPIDoc openAPIDoc;

  @Before
  public void setUp() {
    openAPIDoc = new OpenAPIDoc();
  }

  // --- isValid tests ---

  @Test
  public void testIsValidCopilotLowerCase() {
    assertTrue(openAPIDoc.isValid("copilot"));
  }

  @Test
  public void testIsValidCopilotUpperCase() {
    assertTrue(openAPIDoc.isValid("COPILOT"));
  }

  @Test
  public void testIsValidCopilotMixedCase() {
    assertTrue(openAPIDoc.isValid("CoPiLoT"));
  }

  @Test
  public void testIsValidNull() {
    assertFalse(openAPIDoc.isValid(null));
  }

  @Test
  public void testIsValidEmpty() {
    assertFalse(openAPIDoc.isValid(""));
  }

  @Test
  public void testIsValidWrongTag() {
    assertFalse(openAPIDoc.isValid("assistant"));
  }

  @Test
  public void testIsValidPartialMatch() {
    assertFalse(openAPIDoc.isValid("copilot2"));
  }

  // --- add tests with real OpenAPI objects ---

  @Test
  public void testAddCreatesAllEightEndpoints() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    assertNotNull(openAPI.getPaths());
    assertEquals(8, openAPI.getPaths().size());
  }

  @Test
  public void testAddCreatesNewPathsIfNull() {
    OpenAPI openAPI = new OpenAPI();
    assertNull(openAPI.getPaths());
    openAPIDoc.add(openAPI);
    assertNotNull(openAPI.getPaths());
  }

  @Test
  public void testAddUsesExistingPaths() {
    OpenAPI openAPI = new OpenAPI();
    Paths existing = new Paths();
    existing.put("/custom/path", new PathItem());
    openAPI.setPaths(existing);

    openAPIDoc.add(openAPI);

    assertEquals(9, openAPI.getPaths().size());
    assertNotNull(openAPI.getPaths().get("/custom/path"));
  }

  @Test
  public void testTranscriptionEndpointIsPost() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/transcription");
    assertNotNull(path);
    assertNotNull(path.getPost());
    assertNull(path.getGet());
    assertEquals("Transcribe an audio file to text", path.getPost().getSummary());
    assertTrue(path.getPost().getTags().contains("Copilot"));
  }

  @Test
  public void testTranscriptionEndpointHasMultipartBody() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/transcription");
    assertNotNull(path.getPost().getRequestBody());
    assertTrue(path.getPost().getRequestBody().getContent().containsKey("multipart/form-data"));
  }

  @Test
  public void testAssistantsEndpointIsGet() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/assistants");
    assertNotNull(path);
    assertNotNull(path.getGet());
    assertNull(path.getPost());
    assertEquals("List available assistants for the current user", path.getGet().getSummary());
  }

  @Test
  public void testAQuestionEndpointHasFourParameters() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/aquestion");
    assertNotNull(path.getGet());

    List<Parameter> params = path.getGet().getParameters();
    assertEquals(4, params.size());

    // Check parameter names
    assertEquals("app_id", params.get(0).getName());
    assertEquals("question", params.get(1).getName());
    assertEquals("conversation_id", params.get(2).getName());
    assertEquals("file", params.get(3).getName());

    // Check required flags
    assertTrue(params.get(0).getRequired());
    assertTrue(params.get(1).getRequired());
    assertFalse(params.get(2).getRequired());
    assertFalse(params.get(3).getRequired());
  }

  @Test
  public void testAQuestionParametersAreQueryParams() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/aquestion");
    for (Parameter param : path.getGet().getParameters()) {
      assertEquals("query", param.getIn());
    }
  }

  @Test
  public void testQuestionEndpointIsPost() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/question");
    assertNotNull(path.getPost());
    assertNull(path.getGet());
    assertNotNull(path.getPost().getRequestBody());
    assertTrue(path.getPost().getRequestBody().getContent().containsKey("application/json"));
  }

  @Test
  public void testCacheQuestionEndpointHasTwoResponses() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/cacheQuestion");
    assertNotNull(path.getPost());
    assertTrue(path.getPost().getResponses().containsKey("200"));
    assertTrue(path.getPost().getResponses().containsKey("400"));
  }

  @Test
  public void testFileEndpointHasMultipartFormData() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/file");
    assertNotNull(path.getPost());
    assertTrue(path.getPost().getRequestBody().getContent().containsKey("multipart/form-data"));
  }

  @Test
  public void testConfigCheckEndpointHasTwoResponses() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/configCheck");
    assertNotNull(path.getPost());
    assertTrue(path.getPost().getResponses().containsKey("200"));
    assertTrue(path.getPost().getResponses().containsKey("500"));
  }

  @Test
  public void testStructureEndpointIsGet() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/structure");
    assertNotNull(path.getGet());
    assertNull(path.getPost());
    assertEquals(1, path.getGet().getParameters().size());
    assertEquals("app_id", path.getGet().getParameters().get(0).getName());
    assertTrue(path.getGet().getParameters().get(0).getRequired());
  }

  @Test
  public void testAllEndpointsHaveCopilotTag() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    for (PathItem pathItem : openAPI.getPaths().values()) {
      if (pathItem.getGet() != null) {
        assertTrue(pathItem.getGet().getTags().contains("Copilot"));
      }
      if (pathItem.getPost() != null) {
        assertTrue(pathItem.getPost().getTags().contains("Copilot"));
      }
    }
  }

  @Test
  public void testAllEndpointsHave200Response() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    for (PathItem pathItem : openAPI.getPaths().values()) {
      if (pathItem.getGet() != null) {
        assertTrue(pathItem.getGet().getResponses().containsKey("200"));
      }
      if (pathItem.getPost() != null) {
        assertTrue(pathItem.getPost().getResponses().containsKey("200"));
      }
    }
  }

  @Test
  public void testOpenAPIDocImplementsInterface() {
    assertTrue(openAPIDoc instanceof com.etendoerp.openapi.model.OpenAPIEndpoint);
  }

  @Test
  public void testAddPreservesOpenAPIMetadata() {
    OpenAPI openAPI = new OpenAPI();
    openAPI.setOpenapi("3.0.1");
    openAPI.info(new Info().title("My API").version("2.0"));

    openAPIDoc.add(openAPI);

    assertEquals("3.0.1", openAPI.getOpenapi());
    assertEquals("My API", openAPI.getInfo().getTitle());
    assertEquals("2.0", openAPI.getInfo().getVersion());
    assertEquals(8, openAPI.getPaths().size());
  }

  @Test
  public void testQuestionEndpointResponseSchema() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/question");
    assertNotNull(path.getPost().getResponses().get("200"));
    assertEquals("The answer to the user's question",
        path.getPost().getResponses().get("200").getDescription());
  }

  @Test
  public void testAssistantsEndpointResponseIsArray() {
    OpenAPI openAPI = new OpenAPI();
    openAPIDoc.add(openAPI);

    PathItem path = openAPI.getPaths().get("/sws/copilot/assistants");
    Schema<?> schema = path.getGet().getResponses().get("200")
        .getContent().get("application/json").getSchema();
    assertNotNull(schema);
    // ArraySchema has items
    assertTrue(schema instanceof io.swagger.v3.oas.models.media.ArraySchema);
  }
}
