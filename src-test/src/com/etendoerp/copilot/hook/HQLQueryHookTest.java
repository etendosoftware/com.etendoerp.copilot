/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotVarReplacerUtil;
import com.etendoerp.copilot.util.FileUtils;

/**
 * HQL query hook test.
 */
public class HQLQueryHookTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private HQLQueryHook hqlQueryHook;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private MockedStatic<CopilotVarReplacerUtil> mockedVarReplacer;
    private MockedStatic<FileUtils> mockedFileUtils;
    private MockedStatic<ProcessHQLAppSource> mockedProcessHQL;
    private AutoCloseable mocks;

    @Mock
    private CopilotFile mockCopilotFile;

    @Mock
    private OBDal mockOBDalInstance;

    @Mock
    private OBCriteria<Client> mockCriteria;

    @Mock
    private Client mockClient1;

    @Mock
    private Client mockClient2;

    private static final String TEST_HQL = "SELECT e FROM Entity e";
    private static final String TEST_FILENAME = "result.json";
    private static final String TEST_URL = "http://example.com";
    private static final String TEST_HQL_RESULT = "{\"result\": \"data\"}";
    private static final String CLIENT_ID_1 = "client-1";
    private static final String CLIENT_ID_2 = "client-2";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        hqlQueryHook = new HQLQueryHook();

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
        mockedVarReplacer = mockStatic(CopilotVarReplacerUtil.class);
        mockedFileUtils = mockStatic(FileUtils.class);
        mockedProcessHQL = mockStatic(ProcessHQLAppSource.class);

        // Setup OBDal mock
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDalInstance);
        when(mockOBDalInstance.createCriteria(Client.class)).thenReturn(mockCriteria);

        // Setup OBContext mock
        OBContext mockContext = mock(OBContext.class);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

        // Setup OBMessageUtils mock
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(anyString())).thenReturn("Error message");

        // Setup mock clients
        when(mockClient1.getId()).thenReturn(CLIENT_ID_1);
        when(mockClient2.getId()).thenReturn(CLIENT_ID_2);

        // Setup FileUtils mock behaviors
        mockedFileUtils.when(() -> FileUtils.createSecureTempFile(anyString(), anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            String suffix = invocation.getArgument(1);
            return Files.createTempFile(prefix, suffix);
        });
        mockedFileUtils.when(() -> FileUtils.refreshFileForNonMultiClient(any(), any())).thenAnswer(invocation -> null);
        mockedFileUtils.when(() -> FileUtils.cleanupTempFileIfNeeded(any(), any())).thenAnswer(invocation -> null);

        // Setup CopilotVarReplacerUtil mock
        mockedVarReplacer.when(() -> CopilotVarReplacerUtil.replaceCopilotPromptVariables(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mockedVarReplacer != null) {
            mockedVarReplacer.close();
        }
        if (mockedFileUtils != null) {
            mockedFileUtils.close();
        }
        if (mockedProcessHQL != null) {
            mockedProcessHQL.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test type check valid type.
     */
    @Test
    public void testTypeCheckValidType() {
        assertTrue("Should return true for HQL type", hqlQueryHook.typeCheck("HQL"));
    }

    /**
     * Test type check invalid type.
     */
    @Test
    public void testTypeCheckInvalidType() {
        assertFalse("Should return false for non-HQL type", hqlQueryHook.typeCheck("TXT"));
    }

    /**
     * Test is multi client.
     */
    @Test
    public void testIsMultiClient() {
        assertTrue("HQLQueryHook should support multi-client", hqlQueryHook.isMultiClient());
    }

    /**
     * Test exec with valid HQL query.
     */
    @Test
    public void testExecWithValidHQLQuery() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);
        clientList.add(mockClient2);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("json"), anyString()
        )).thenReturn(TEST_HQL_RESULT);

        // When
        hqlQueryHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile, times(1)).getUrl();
        verify(mockCopilotFile, times(1)).getFilename();
        verify(mockCopilotFile, times(1)).getHql();
        mockedVarReplacer.verify(() -> CopilotVarReplacerUtil.replaceCopilotPromptVariables(eq(TEST_URL)), times(1));
        mockedProcessHQL.verify(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("json"), eq(CLIENT_ID_1)
        ), times(1));
        mockedProcessHQL.verify(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("json"), eq(CLIENT_ID_2)
        ), times(1));
        mockedFileUtils.verify(() -> FileUtils.createSecureTempFile(anyString(), eq(".json")), times(2));
        mockedFileUtils.verify(() -> FileUtils.refreshFileForNonMultiClient(eq(mockCopilotFile), any(Map.class)), times(1));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any(Path.class)), times(2));
    }

    /**
     * Test exec with single client.
     */
    @Test
    public void testExecWithSingleClient() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("json"), eq(CLIENT_ID_1)
        )).thenReturn(TEST_HQL_RESULT);

        // When
        hqlQueryHook.exec(mockCopilotFile);

        // Then
        mockedProcessHQL.verify(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("json"), eq(CLIENT_ID_1)
        ), times(1));
        mockedFileUtils.verify(() -> FileUtils.createSecureTempFile(anyString(), eq(".json")), times(1));
        mockedFileUtils.verify(() -> FileUtils.refreshFileForNonMultiClient(eq(mockCopilotFile), any(Map.class)), times(1));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any(Path.class)), times(1));
    }

    /**
     * Test exec with different file extension.
     */
    @Test
    public void testExecWithDifferentExtension() throws Exception {
        // Given
        String xmlFilename = "result.xml";
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(xmlFilename);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        String xmlResult = "<result>data</result>";
        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("xml"), eq(CLIENT_ID_1)
        )).thenReturn(xmlResult);

        // When
        hqlQueryHook.exec(mockCopilotFile);

        // Then
        mockedProcessHQL.verify(() -> ProcessHQLAppSource.getHQLResult(
                eq(TEST_HQL), eq("e"), eq("xml"), eq(CLIENT_ID_1)
        ), times(1));
        mockedFileUtils.verify(() -> FileUtils.createSecureTempFile(anyString(), eq(".xml")), times(1));
    }

    /**
     * Test exec with HQL error.
     */
    @Test
    public void testExecWithHQLError() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new RuntimeException("HQL execution failed"));

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("HQL execution failed");

        // When
        hqlQueryHook.exec(mockCopilotFile);
    }

    /**
     * Test exec with file write error.
     */
    @Test
    public void testExecWithFileWriteError() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(TEST_HQL_RESULT);

        mockedFileUtils.when(() -> FileUtils.createSecureTempFile(anyString(), anyString()))
                .thenThrow(new IOException("Cannot create temp file"));

        expectedException.expect(OBException.class);

        // When
        hqlQueryHook.exec(mockCopilotFile);
    }

    /**
     * Test exec cleans up temp files on error.
     */
    @Test
    public void testExecCleansUpTempFilesOnError() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();
        clientList.add(mockClient1);

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        mockedProcessHQL.when(() -> ProcessHQLAppSource.getHQLResult(
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(TEST_HQL_RESULT);

        mockedFileUtils.when(() -> FileUtils.refreshFileForNonMultiClient(any(), any()))
                .thenThrow(new RuntimeException("Refresh failed"));

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Refresh failed");

        // When
        try {
            hqlQueryHook.exec(mockCopilotFile);
        } finally {
            // Then - verify cleanup was called even on error
            mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any(Path.class)), times(1));
        }
    }

    /**
     * Test exec with empty client list.
     */
    @Test
    public void testExecWithEmptyClientList() throws Exception {
        // Given
        List<Client> clientList = new ArrayList<>();

        when(mockCopilotFile.getUrl()).thenReturn(TEST_URL);
        when(mockCopilotFile.getFilename()).thenReturn(TEST_FILENAME);
        when(mockCopilotFile.getHql()).thenReturn(TEST_HQL);
        when(mockCopilotFile.getName()).thenReturn("test");

        when(mockCriteria.list()).thenReturn(clientList);

        // When
        hqlQueryHook.exec(mockCopilotFile);

        // Then - no HQL queries should be executed
        mockedProcessHQL.verify(() -> ProcessHQLAppSource.getHQLResult(
                anyString(), anyString(), anyString(), anyString()
        ), times(0));
        mockedFileUtils.verify(() -> FileUtils.refreshFileForNonMultiClient(eq(mockCopilotFile), any(Map.class)), times(1));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any(Path.class)), times(0));
    }
}
