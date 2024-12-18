package com.etendoerp.copilot.process;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotTool;

public class SyncToolStructureTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private MockedStatic<DbUtility> mockedDbUtility;

    private SyncToolStructure syncToolStructure;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        syncToolStructure = new SyncToolStructure();
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
        mockedDbUtility = mockStatic(DbUtility.class);
    }

    @After
    public void tearDown() {
        mockedOBDal.close();
        mockedOBMessageUtils.close();
        mockedDbUtility.close();
    }

    @Test
    public void testDoExecute_Success() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        request.put("recordIds", Collections.singletonList("toolId"));
        when(mockHttpResponse.body()).thenReturn("{\"answer\":{\"toolId\":{\"description\":\"Test Description\",\"parameters\":{}}}}\n");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockHttpResponse);

        // When
        JSONObject result = syncToolStructure.doExecute(Map.of(), request.toString());

        // Then
        assertNotNull(result);
        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testDoExecute_NoSelectedRecords() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        request.put("recordIds", Collections.emptyList());
        expectedException.expect(OBException.class);
        expectedException.expectMessage("ETCOP_NoSelectedRecords");

        // When
        syncToolStructure.doExecute(Map.of(), request.toString());
    }

    @Test
    public void testDoExecute_ConnectionError() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        request.put("recordIds", Collections.singletonList("toolId"));
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new ConnectException());
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ConnCopilotError"))
            .thenReturn("Connection Error");

        // When
        JSONObject result = syncToolStructure.doExecute(Map.of(), request.toString());

        // Then
        assertNotNull(result);
        assertEquals("Connection Error", result.getJSONObject("message").getString("text"));
    }

    @Test
    public void testDoExecute_InterruptedException() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        request.put("recordIds", Collections.singletonList("toolId"));
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException());
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ConnError"))
            .thenReturn("Interrupted Error");

        // When
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Interrupted Error");
        syncToolStructure.doExecute(Map.of(), request.toString());
    }

    @Test
    public void testDoExecute_GeneralException() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        request.put("recordIds", Collections.singletonList("toolId"));
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new RuntimeException("General Error"));
        mockedDbUtility.when(() -> DbUtility.getUnderlyingSQLException(any()))
            .thenReturn(new SQLException("SQL Error"));
        mockedOBMessageUtils.when(() -> OBMessageUtils.translateError("SQL Error"))
            .thenReturn(new OBError("Error", "SQL Error", "error"));

        // When
        JSONObject result = syncToolStructure.doExecute(Map.of(), request.toString());

        // Then
        assertNotNull(result);
        assertEquals("SQL Error", result.getJSONObject("message").getString("text"));
    }
}
