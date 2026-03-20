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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.etendoerp.copilot.util.CopilotVarReplacerUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.FileUtils;

import org.openbravo.model.ad.system.Language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Remote file hook test.
 */
public class RemoteFileHookTest extends WeldBaseTest {
    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private CopilotFile mockCopilotFile;

    @Mock
    private AttachImplementationManager mockAttachManager;

    private MockedStatic<WeldUtils> mockedWeldUtils;
    private MockedStatic<CopilotVarReplacerUtil> mockedCopilotVarReplacerUtil;
    private MockedStatic<FileUtils> mockedFileUtils;
    private MockedStatic<OBContext> mockedOBContext;
    private RemoteFileHook remoteFileHook;
    private AutoCloseable mocks;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    private static final String EXAMPLE_FILE_URL = "https://example-files.online-convert.com/document/txt/example.txt";
    private static final String CUSTOM_TXT = "custom.txt";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        remoteFileHook = new RemoteFileHook();

        // Mock OBContext
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(mock(OBContext.class));

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(OBContext.getOBContext().getLanguage()).thenReturn(mockLanguage);

        // Setup static mocks
        mockedWeldUtils = mockStatic(WeldUtils.class);
        mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
                .thenReturn(mockAttachManager);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedCopilotVarReplacerUtil = mockStatic(CopilotVarReplacerUtil.class);
        mockedFileUtils = mockStatic(FileUtils.class);
        mockedFileUtils.when(() -> FileUtils.createSecureTempDirectory(any(String.class))).thenAnswer(invocation -> Files.createTempDirectory(invocation.getArgument(0).toString()));
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        if (mockedWeldUtils != null) {
            mockedWeldUtils.close();
        }
        if (mockedCopilotVarReplacerUtil != null) {
            mockedCopilotVarReplacerUtil.close();
        }
        if (mockedFileUtils != null) {
            mockedFileUtils.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }


    /**
     * Test type check valid type.
     */
    @Test
    public void testTypeCheckValidType() {
        assertTrue(remoteFileHook.typeCheck("RF"));
    }

    /**
     * Test type check invalid type.
     */
    @Test
    public void testTypeCheckInvalidType() {
        assertFalse(remoteFileHook.typeCheck("INVALID"));
    }

    /**
     * Test get final name with custom name.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetFinalNameWithCustomName() throws Exception {
        URL url = new URL(EXAMPLE_FILE_URL);
        String finalName = RemoteFileHook.getFinalName("custom", url);
        assertEquals(CUSTOM_TXT, finalName);
    }

    /**
     * Test get final name without custom name.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetFinalNameWithoutCustomName() throws Exception {
        URL url = new URL(EXAMPLE_FILE_URL);
        String finalName = RemoteFileHook.getFinalName("", url);
        assertEquals("example.txt", finalName);
    }

    /**
     * Test get final name custom name with extension.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetFinalNameCustomNameWithExtension() throws Exception {
        URL url = new URL(EXAMPLE_FILE_URL);
        String finalName = RemoteFileHook.getFinalName(CUSTOM_TXT, url);
        assertEquals(CUSTOM_TXT, finalName);
    }

    /**
     * Test exec successful download.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecSuccessfulDownload() throws Exception {
        // Given
        String testUrl = EXAMPLE_FILE_URL;
        String fileName = "example.txt";
        when(mockCopilotFile.getUrl()).thenReturn(testUrl);
        when(mockCopilotFile.getFilename()).thenReturn(fileName);
        mockedCopilotVarReplacerUtil.when(() -> CopilotVarReplacerUtil.replaceCopilotPromptVariables(testUrl))
                .thenReturn(testUrl);

        // Mock file operations
        mockedFileUtils.when(() -> FileUtils.processFileAttachment(any(), any(), anyBoolean())).thenAnswer(invocation -> null);
        mockedFileUtils.when(() -> FileUtils.cleanupTempFileIfNeeded(any(), any())).thenAnswer(invocation -> null);

        // When
        remoteFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile).getUrl();
        verify(mockCopilotFile).getFilename();
        mockedFileUtils.verify(() -> FileUtils.processFileAttachment(eq(mockCopilotFile), any(), anyBoolean()));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
    }

    /**
     * Test exec download error.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecDownloadError() throws Exception {
        // Given
        String invalidUrl = "invalid://url";
        when(mockCopilotFile.getUrl()).thenReturn(invalidUrl);
        when(mockCopilotFile.getFilename()).thenReturn("");
        mockedCopilotVarReplacerUtil.when(() -> CopilotVarReplacerUtil.replaceCopilotPromptVariables(invalidUrl))
                .thenReturn(invalidUrl);

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_FileDownErr"))
                .thenReturn("Error downloading file from URL: " + invalidUrl);

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Error downloading file from URL: " + invalidUrl);

        // When
        remoteFileHook.exec(mockCopilotFile);
    }

    /**
     * Test download file invalid url.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDownloadFileInvalidUrl() throws Exception {
        expectedException.expect(IOException.class);
        RemoteFileHook.downloadFile("invalid://url", "test.txt");
    }
}
