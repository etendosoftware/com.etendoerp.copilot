package com.etendoerp.copilot.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

public class OpenAIUtilsTest {

    @Mock
    private CopilotApp mockApp;

    @Mock
    private CopilotAppSource mockAppSource;

    @Mock
    private CopilotFile mockFile;

    private MockedStatic<OBDal> mockedOBDal;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedOBDal = mockStatic(OBDal.class);
    }

    @After
    public void tearDown() {
        mockedOBDal.close();
    }

    @Test
    public void testSyncAssistant_throwsOBExceptionOnJSONException() throws Exception {
        // Given
        doThrow(new JSONException("JSON error")).when(mockApp).getOpenaiIdAssistant();

        // Expect
        expectedException.expect(OBException.class);
        expectedException.expectMessage("JSON error");

        // When
        OpenAIUtils.syncAssistant("apiKey", mockApp);
    }

    @Test
    public void testSyncAppSource_skipsSyncIfFileNotChanged() throws Exception {
        // Given
        when(mockAppSource.getFile()).thenReturn(mockFile);
        when(mockFile.getOpenaiIdFile()).thenReturn("fileId");
        when(mockFile.getLastSync()).thenReturn(new java.util.Date());
        when(mockFile.getUpdated()).thenReturn(new java.util.Date());

        // When
        OpenAIUtils.syncAppSource(mockAppSource, "apiKey");

        // Then
        verify(mockFile, never()).setOpenaiIdFile(anyString());
    }

    @Test
    public void testMakeRequestToOpenAI_throwsOBExceptionOnErrorResponse() throws Exception {
        // Given
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error", "Some error");

        // Expect
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Some error");

        // When
        OpenAIUtils.makeRequestToOpenAI("apiKey", "/endpoint", null, "GET", null);
    }

    @Test
    public void testCheckIfAppCanUseAttachedFiles_throwsOBExceptionIfNotConfigured() {
        // Given
        when(mockApp.isCodeInterpreter()).thenReturn(false);
        when(mockApp.isRetrieval()).thenReturn(false);

        // Expect
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Knowledge Base");

        // When
        OpenAIUtils.checkIfAppCanUseAttachedFiles(mockApp, Collections.singletonList(mockAppSource));
    }

    // Additional tests for other methods can be added here following the same pattern.
}
