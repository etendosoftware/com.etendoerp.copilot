package com.etendoerp.copilot.hook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.PathItem;

/**
 * Test class for OpenAPIDoc
 */
class OpenAPIDocTest {

    private static final String COPILOT_TAG = "copilot";
    private static final String COPILOT_TAG_UPPERCASE = "Copilot";
    private static final String COPILOT_TRANSCRIPTION_PATH = "/sws/copilot/transcription";
    private static final String COPILOT_ASSISTANTS_PATH = "/sws/copilot/assistants";
    private static final String COPILOT_AQUESTION_PATH = "/sws/copilot/aquestion";
    private static final String COPILOT_QUESTION_PATH = "/sws/copilot/question";
    private static final String COPILOT_CACHE_QUESTION_PATH = "/sws/copilot/cacheQuestion";
    private static final String COPILOT_FILE_PATH = "/sws/copilot/file";
    private static final String COPILOT_CONFIG_CHECK_PATH = "/sws/copilot/configCheck";
    private static final String COPILOT_STRUCTURE_PATH = "/sws/copilot/structure";

    private OpenAPIDoc openAPIDoc;

    @Mock
    private OpenAPI mockOpenAPI;

    @Mock
    private Paths mockPaths;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        openAPIDoc = new OpenAPIDoc();
    }

    @Test
    @DisplayName("isValid should return true for 'copilot' tag (case insensitive)")
    void isValidWithCopilotTagShouldReturnTrue() {
        // Test exact match
        assertTrue(openAPIDoc.isValid(COPILOT_TAG));

        // Test case insensitive
        assertTrue(openAPIDoc.isValid("COPILOT"));
        assertTrue(openAPIDoc.isValid(COPILOT_TAG_UPPERCASE));
        assertTrue(openAPIDoc.isValid("CoPiLoT"));
    }

    @ParameterizedTest
    @DisplayName("isValid should return false for non-copilot tags")
    @ValueSource(strings = {"", "invalid", "copilot123", "123copilot", "co-pilot", "pilot", "null"})
    void isValidWithInvalidTagsShouldReturnFalse(String tag) {
        assertFalse(openAPIDoc.isValid(tag));
    }

    @Test
    @DisplayName("isValid should return false for null tag")
    void isValidWithNullTagShouldReturnFalse() {
        assertFalse(openAPIDoc.isValid(null));
    }



    @Test
    @DisplayName("add should use existing Paths when openAPI.getPaths() returns non-null")
    void addWithExistingPathsShouldUseExistingPaths() {
        // Arrange
        when(mockOpenAPI.getPaths()).thenReturn(mockPaths);

        // Act
        openAPIDoc.add(mockOpenAPI);

        // Assert
        verify(mockOpenAPI, never()).setPaths(any(Paths.class));
        verify(mockOpenAPI, atLeastOnce()).getPaths();
    }

    @Test
    @DisplayName("add should create new Paths when openAPI.getPaths() returns null")
    void addWithNullPathsShouldCreateNewPaths() {
        // Arrange
        when(mockOpenAPI.getPaths()).thenReturn(null).thenReturn(new Paths());

        // Act
        openAPIDoc.add(mockOpenAPI);

        // Assert
        verify(mockOpenAPI, times(1)).setPaths(any(Paths.class));
        verify(mockOpenAPI, times(9)).getPaths(); // Once for null check, 8 times for each endpoint method
    }

    @Test
    @DisplayName("add should handle null OpenAPI gracefully")
    void addWithNullOpenAPIShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> openAPIDoc.add(null));
    }

    @Test
    @DisplayName("add should be idempotent - multiple calls should not cause issues")
    void addMultipleCallsShouldBeIdempotent() {
        // Arrange
        when(mockOpenAPI.getPaths()).thenReturn(mockPaths);

        // Act - Call add method multiple times
        openAPIDoc.add(mockOpenAPI);
        openAPIDoc.add(mockOpenAPI);
        openAPIDoc.add(mockOpenAPI);

        // Assert - Each endpoint should be added 3 times (once per call)
        verify(mockPaths, times(3)).put(eq(COPILOT_TRANSCRIPTION_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_ASSISTANTS_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_AQUESTION_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_QUESTION_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_CACHE_QUESTION_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_FILE_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_CONFIG_CHECK_PATH), any(PathItem.class));
        verify(mockPaths, times(3)).put(eq(COPILOT_STRUCTURE_PATH), any(PathItem.class));

        // Total of 24 calls (8 endpoints × 3 calls)
        verify(mockPaths, times(24)).put(anyString(), any(PathItem.class));
    }

    @Test
    @DisplayName("OpenAPIDoc should implement OpenAPIEndpoint interface")
    void openAPIDocShouldImplementOpenAPIEndpoint() {
        // Assert that the class implements the expected interface
        assertTrue(openAPIDoc instanceof com.etendoerp.openapi.model.OpenAPIEndpoint);
    }

    @Test
    @DisplayName("add should create transcription endpoint with correct structure")
    void addShouldCreateTranscriptionEndpointWithCorrectStructure() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem transcriptionPath = realPaths.get(COPILOT_TRANSCRIPTION_PATH);
        assertNotNull(transcriptionPath);
        assertNotNull(transcriptionPath.getPost());
        assertEquals("Transcribe an audio file to text", transcriptionPath.getPost().getSummary());
        assertTrue(transcriptionPath.getPost().getTags().contains(COPILOT_TAG_UPPERCASE));
        assertNotNull(transcriptionPath.getPost().getRequestBody());
        assertEquals("The audio file to transcribe", transcriptionPath.getPost().getRequestBody().getDescription());
    }

    @Test
    @DisplayName("add should create assistants endpoint with correct structure")
    void addShouldCreateAssistantsEndpointWithCorrectStructure() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem assistantsPath = realPaths.get(COPILOT_ASSISTANTS_PATH);
        assertNotNull(assistantsPath);
        assertNotNull(assistantsPath.getGet());
        assertEquals("List available assistants for the current user", assistantsPath.getGet().getSummary());
        assertTrue(assistantsPath.getGet().getTags().contains(COPILOT_TAG_UPPERCASE));
        assertNull(assistantsPath.getPost()); // Should only have GET
    }

    @Test
    @DisplayName("add should create aquestion endpoint with correct parameters")
    void addShouldCreateAQuestionEndpointWithCorrectParameters() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem aquestionPath = realPaths.get(COPILOT_AQUESTION_PATH);
        assertNotNull(aquestionPath);
        assertNotNull(aquestionPath.getGet());
        assertEquals("Ask a question to a selected assistant", aquestionPath.getGet().getSummary());

        // Verify required parameters
        var parameters = aquestionPath.getGet().getParameters();
        assertNotNull(parameters);
        assertEquals(4, parameters.size());

        // Check specific parameters
        boolean hasAppId = parameters.stream().anyMatch(p -> "app_id".equals(p.getName()) && p.getRequired());
        boolean hasQuestion = parameters.stream().anyMatch(p -> "question".equals(p.getName()) && p.getRequired());
        boolean hasConversationId = parameters.stream().anyMatch(p -> "conversation_id".equals(p.getName()) && !p.getRequired());
        boolean hasFile = parameters.stream().anyMatch(p -> "file".equals(p.getName()) && !p.getRequired());

        assertTrue(hasAppId);
        assertTrue(hasQuestion);
        assertTrue(hasConversationId);
        assertTrue(hasFile);
    }

    @Test
    @DisplayName("add should create question endpoint with correct request body")
    void addShouldCreateQuestionEndpointWithCorrectRequestBody() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem questionPath = realPaths.get(COPILOT_QUESTION_PATH);
        assertNotNull(questionPath);
        assertNotNull(questionPath.getPost());
        assertEquals("Ask a question to a selected assistant (JSON body)", questionPath.getPost().getSummary());

        var requestBody = questionPath.getPost().getRequestBody();
        assertNotNull(requestBody);
        assertEquals("JSON object containing the question, assistant ID, and optional parameters", requestBody.getDescription());

        var content = requestBody.getContent();
        assertNotNull(content);
        assertTrue(content.containsKey("application/json"));
    }

    @Test
    @DisplayName("add should create cacheQuestion endpoint with correct responses")
    void addShouldCreateCacheQuestionEndpointWithCorrectResponses() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem cacheQuestionPath = realPaths.get(COPILOT_CACHE_QUESTION_PATH);
        assertNotNull(cacheQuestionPath);
        assertNotNull(cacheQuestionPath.getPost());
        assertEquals("Cache a question", cacheQuestionPath.getPost().getSummary());

        var responses = cacheQuestionPath.getPost().getResponses();
        assertNotNull(responses);
        assertTrue(responses.containsKey("200"));
        assertTrue(responses.containsKey("400"));

        assertEquals("The question was cached successfully", responses.get("200").getDescription());
        assertEquals("Bad request (missing or invalid question)", responses.get("400").getDescription());
    }

    @Test
    @DisplayName("add should create file endpoint with multipart form data")
    void addShouldCreateFileEndpointWithMultipartFormData() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem filePath = realPaths.get(COPILOT_FILE_PATH);
        assertNotNull(filePath);
        assertNotNull(filePath.getPost());
        assertEquals("Upload a file", filePath.getPost().getSummary());

        var requestBody = filePath.getPost().getRequestBody();
        assertNotNull(requestBody);
        assertEquals("Multipart form with the file to upload", requestBody.getDescription());

        var content = requestBody.getContent();
        assertNotNull(content);
        assertTrue(content.containsKey("multipart/form-data"));
    }

    @Test
    @DisplayName("add should create configCheck endpoint with correct responses")
    void addShouldCreateConfigCheckEndpointWithCorrectResponses() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem configCheckPath = realPaths.get(COPILOT_CONFIG_CHECK_PATH);
        assertNotNull(configCheckPath);
        assertNotNull(configCheckPath.getPost());
        assertEquals("Check server configuration", configCheckPath.getPost().getSummary());

        var responses = configCheckPath.getPost().getResponses();
        assertNotNull(responses);
        assertTrue(responses.containsKey("200"));
        assertTrue(responses.containsKey("500"));

        assertEquals("Configuration check successful", responses.get("200").getDescription());
        assertEquals("Internal server error", responses.get("500").getDescription());
    }

    @Test
    @DisplayName("add should create structure endpoint with app_id parameter")
    void addShouldCreateStructureEndpointWithAppIdParameter() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert
        PathItem structurePath = realPaths.get(COPILOT_STRUCTURE_PATH);
        assertNotNull(structurePath);
        assertNotNull(structurePath.getGet());
        assertEquals("Get structure for an assistant", structurePath.getGet().getSummary());

        var parameters = structurePath.getGet().getParameters();
        assertNotNull(parameters);
        assertEquals(1, parameters.size());

        var appIdParam = parameters.get(0);
        assertEquals("app_id", appIdParam.getName());
        assertEquals("query", appIdParam.getIn());
        assertTrue(appIdParam.getRequired());
        assertEquals("The assistant ID", appIdParam.getDescription());
    }

    @Test
    @DisplayName("isValid should handle edge cases properly")
    void isValidShouldHandleEdgeCases() {
        // Test whitespace variations
        assertFalse(openAPIDoc.isValid(" copilot"));
        assertFalse(openAPIDoc.isValid("copilot "));
        assertFalse(openAPIDoc.isValid(" copilot "));
        assertFalse(openAPIDoc.isValid("\tcopilot"));
        assertFalse(openAPIDoc.isValid("copilot\n"));

        // Test special characters
        assertFalse(openAPIDoc.isValid("copilot!"));
        assertFalse(openAPIDoc.isValid("@copilot"));
        assertFalse(openAPIDoc.isValid("copilot#"));
        assertFalse(openAPIDoc.isValid("co-pilot"));
        assertFalse(openAPIDoc.isValid("co_pilot"));
        assertFalse(openAPIDoc.isValid("co.pilot"));

        // Test numeric variations
        assertFalse(openAPIDoc.isValid("copilot1"));
        assertFalse(openAPIDoc.isValid("1copilot"));
        assertFalse(openAPIDoc.isValid("cop1lot"));

        // Test Unicode and special characters
        assertFalse(openAPIDoc.isValid("copilöt"));
        assertFalse(openAPIDoc.isValid("çopilot"));
        assertFalse(openAPIDoc.isValid("copilót"));
    }

    @Test
    @DisplayName("add should handle OpenAPI with existing paths containing other endpoints")
    void addShouldHandleExistingPathsWithOtherEndpoints() {
        // Arrange
        Paths existingPaths = new Paths();
        existingPaths.put("/existing/endpoint", new PathItem().get(new io.swagger.v3.oas.models.Operation()));
        existingPaths.put("/another/endpoint", new PathItem().post(new io.swagger.v3.oas.models.Operation()));

        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(existingPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - Existing endpoints should still be there
        assertNotNull(realOpenAPI.getPaths().get("/existing/endpoint"));
        assertNotNull(realOpenAPI.getPaths().get("/another/endpoint"));

        // Assert - New copilot endpoints should be added
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_TRANSCRIPTION_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_ASSISTANTS_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_AQUESTION_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_QUESTION_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_CACHE_QUESTION_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_FILE_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_CONFIG_CHECK_PATH));
        assertNotNull(realOpenAPI.getPaths().get(COPILOT_STRUCTURE_PATH));

        // Total paths should be 2 existing + 8 copilot = 10
        assertEquals(10, realOpenAPI.getPaths().size());
    }

    @Test
    @DisplayName("add should preserve existing copilot endpoints and replace them")
    void addShouldReplaceExistingCopilotEndpoints() {
        // Arrange
        Paths existingPaths = new Paths();
        PathItem existingCopilotPath = new PathItem().get(new io.swagger.v3.oas.models.Operation().summary("Old transcription"));
        existingPaths.put(COPILOT_TRANSCRIPTION_PATH, existingCopilotPath);

        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(existingPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - The endpoint should be replaced with new implementation
        PathItem transcriptionPath = realOpenAPI.getPaths().get(COPILOT_TRANSCRIPTION_PATH);
        assertNotNull(transcriptionPath);
        assertNotNull(transcriptionPath.getPost());
        assertEquals("Transcribe an audio file to text", transcriptionPath.getPost().getSummary());
        // Should not be the old operation
        assertNotEquals("Old transcription", transcriptionPath.getPost().getSummary());
    }

    @Test
    @DisplayName("all endpoints should have Copilot tag")
    void allEndpointsShouldHaveCopilotTag() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - Check that all endpoints have the Copilot tag
        realPaths.forEach((path, pathItem) -> {
            if (pathItem.getGet() != null) {
                assertTrue(pathItem.getGet().getTags().contains(COPILOT_TAG_UPPERCASE),
                    "GET endpoint " + path + " should have Copilot tag");
            }
            if (pathItem.getPost() != null) {
                assertTrue(pathItem.getPost().getTags().contains(COPILOT_TAG_UPPERCASE),
                    "POST endpoint " + path + " should have Copilot tag");
            }
        });
    }

    @Test
    @DisplayName("all POST endpoints should have request bodies")
    void allPostEndpointsShouldHaveRequestBodies() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - Check POST endpoints have request bodies (except configCheck which has no body)
        PathItem transcriptionPath = realPaths.get(COPILOT_TRANSCRIPTION_PATH);
        assertNotNull(transcriptionPath.getPost().getRequestBody());

        PathItem questionPath = realPaths.get(COPILOT_QUESTION_PATH);
        assertNotNull(questionPath.getPost().getRequestBody());

        PathItem cacheQuestionPath = realPaths.get(COPILOT_CACHE_QUESTION_PATH);
        assertNotNull(cacheQuestionPath.getPost().getRequestBody());

        PathItem filePath = realPaths.get(COPILOT_FILE_PATH);
        assertNotNull(filePath.getPost().getRequestBody());

        // configCheck doesn't have a request body, but it should have the operation
        PathItem configCheckPath = realPaths.get(COPILOT_CONFIG_CHECK_PATH);
        assertNotNull(configCheckPath.getPost());
    }

    @Test
    @DisplayName("all endpoints should have 200 responses")
    void allEndpointsShouldHave200Responses() {
        // Arrange
        Paths realPaths = new Paths();
        OpenAPI realOpenAPI = new OpenAPI();
        realOpenAPI.setPaths(realPaths);

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - Check that all endpoints have 200 responses
        realPaths.forEach((path, pathItem) -> {
            if (pathItem.getGet() != null) {
                assertTrue(pathItem.getGet().getResponses().containsKey("200"),
                    "GET endpoint " + path + " should have 200 response");
            }
            if (pathItem.getPost() != null) {
                assertTrue(pathItem.getPost().getResponses().containsKey("200"),
                    "POST endpoint " + path + " should have 200 response");
            }
        });
    }

    @Test
    @DisplayName("integration test - complete OpenAPI object should be properly structured")
    void integrationTestCompleteOpenAPIShouldBeProperlyStructured() {
        // Arrange
        OpenAPI openAPI = new OpenAPI();
        openAPI.setOpenapi("3.0.1");
        openAPI.info(new io.swagger.v3.oas.models.info.Info().title("Test API").version("1.0"));

        // Act
        openAPIDoc.add(openAPI);

        // Assert - Verify the complete structure
        assertNotNull(openAPI.getPaths());
        assertEquals(8, openAPI.getPaths().size());

        // Verify each endpoint exists and has correct HTTP methods
        assertTrue(openAPI.getPaths().containsKey(COPILOT_TRANSCRIPTION_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_TRANSCRIPTION_PATH).getPost());
        assertNull(openAPI.getPaths().get(COPILOT_TRANSCRIPTION_PATH).getGet());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_ASSISTANTS_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_ASSISTANTS_PATH).getGet());
        assertNull(openAPI.getPaths().get(COPILOT_ASSISTANTS_PATH).getPost());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_AQUESTION_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_AQUESTION_PATH).getGet());
        assertNull(openAPI.getPaths().get(COPILOT_AQUESTION_PATH).getPost());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_QUESTION_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_QUESTION_PATH).getPost());
        assertNull(openAPI.getPaths().get(COPILOT_QUESTION_PATH).getGet());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_CACHE_QUESTION_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_CACHE_QUESTION_PATH).getPost());
        assertNull(openAPI.getPaths().get(COPILOT_CACHE_QUESTION_PATH).getGet());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_FILE_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_FILE_PATH).getPost());
        assertNull(openAPI.getPaths().get(COPILOT_FILE_PATH).getGet());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_CONFIG_CHECK_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_CONFIG_CHECK_PATH).getPost());
        assertNull(openAPI.getPaths().get(COPILOT_CONFIG_CHECK_PATH).getGet());

        assertTrue(openAPI.getPaths().containsKey(COPILOT_STRUCTURE_PATH));
        assertNotNull(openAPI.getPaths().get(COPILOT_STRUCTURE_PATH).getGet());
        assertNull(openAPI.getPaths().get(COPILOT_STRUCTURE_PATH).getPost());

        // Verify OpenAPI metadata is preserved
        assertEquals("3.0.1", openAPI.getOpenapi());
        assertEquals("Test API", openAPI.getInfo().getTitle());
        assertEquals("1.0", openAPI.getInfo().getVersion());
    }

    @Test
    @DisplayName("isValid should return false for various invalid inputs")
    void isValidShouldReturnFalseForVariousInvalidInputs() {
        // Test common misspellings
        assertFalse(openAPIDoc.isValid("copiot"));
        assertFalse(openAPIDoc.isValid("copilott"));
        assertFalse(openAPIDoc.isValid("copliot"));
        assertFalse(openAPIDoc.isValid("coplot"));

        // Test similar words
        assertFalse(openAPIDoc.isValid("pilot"));
        assertFalse(openAPIDoc.isValid("auto"));
        assertFalse(openAPIDoc.isValid("bot"));
        assertFalse(openAPIDoc.isValid("assistant"));

        // Test concatenations
        assertFalse(openAPIDoc.isValid("copilotsystem"));
        assertFalse(openAPIDoc.isValid("systemcopilot"));
        assertFalse(openAPIDoc.isValid("copilotapi"));

        // Test empty-like strings
        assertFalse(openAPIDoc.isValid(""));
        assertFalse(openAPIDoc.isValid("   "));
        assertFalse(openAPIDoc.isValid("\t\n"));
    }

    @Test
    @DisplayName("add should work correctly with real OpenAPI instance")
    void addShouldWorkCorrectlyWithRealOpenAPIInstance() {
        // Arrange - Create a real OpenAPI instance without mocking
        OpenAPI realOpenAPI = new OpenAPI();

        // Act
        openAPIDoc.add(realOpenAPI);

        // Assert - Verify new Paths object was created
        assertNotNull(realOpenAPI.getPaths());
        assertEquals(8, realOpenAPI.getPaths().size());

        // Verify all expected paths are present
        String[] expectedPaths = {
            COPILOT_TRANSCRIPTION_PATH,
            COPILOT_ASSISTANTS_PATH,
            COPILOT_AQUESTION_PATH,
            COPILOT_QUESTION_PATH,
            COPILOT_CACHE_QUESTION_PATH,
            COPILOT_FILE_PATH,
            COPILOT_CONFIG_CHECK_PATH,
            COPILOT_STRUCTURE_PATH
        };

        for (String expectedPath : expectedPaths) {
            assertTrue(realOpenAPI.getPaths().containsKey(expectedPath),
                "OpenAPI should contain path: " + expectedPath);
        }
    }
}
