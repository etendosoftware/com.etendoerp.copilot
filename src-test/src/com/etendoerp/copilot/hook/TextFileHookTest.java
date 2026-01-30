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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.FileUtils;

/**
 * Text file hook test.
 */
public class TextFileHookTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private TextFileHook textFileHook;
    private MockedStatic<WeldUtils> mockedWeldUtils;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<FileUtils> mockedFileUtils;
    private AutoCloseable mocks;

    @Mock
    private CopilotFile mockCopilotFile;

    @Mock
    private AttachImplementationManager mockAttachManager;

    private static final String TEST_CONTENT = "test content";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        textFileHook = new TextFileHook();

        // Setup static mocks
        mockedWeldUtils = mockStatic(WeldUtils.class);
        mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
                .thenReturn(mockAttachManager);

        mockedCopilotUtils = mockStatic(CopilotUtils.class);
        mockedFileUtils = mockStatic(FileUtils.class);
        mockedFileUtils.when(() -> FileUtils.createSecureTempFile(any(), any())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            String suffix = invocation.getArgument(1);
            return Files.createTempFile(prefix, suffix);
        });
        mockedFileUtils.when(() -> FileUtils.processFileAttachment(any(), any(), anyBoolean())).thenAnswer(invocation -> null);
        mockedFileUtils.when(() -> FileUtils.cleanupTempFileIfNeeded(any(), any())).thenAnswer(invocation -> null);
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception {
        if (mockedWeldUtils != null) {
            mockedWeldUtils.close();
        }
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mockedFileUtils != null) {
            mockedFileUtils.close();
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
        assertTrue("Should return true for TXT type", textFileHook.typeCheck("TXT"));
    }

    /**
     * Test type check invalid type.
     */
    @Test
    public void testTypeCheckInvalidType() {
        assertFalse("Should return false for non-TXT type", textFileHook.typeCheck("PDF"));
    }

    /**
     * Test exec with valid text and filename.
     */
    @Test
    public void testExecWithValidTextAndFilename() {
        // Given
        String testFilename = "test.txt";
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));

        when(mockCopilotFile.getText()).thenReturn(TEST_CONTENT);
        when(mockCopilotFile.getFilename()).thenReturn(testFilename);
        when(mockCopilotFile.getName()).thenReturn("test");

        // When
        textFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile, times(1)).getText();
        verify(mockCopilotFile, times(2)).getFilename();
        mockedFileUtils.verify(() -> FileUtils.processFileAttachment(eq(mockCopilotFile), any(), anyBoolean()));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
    }

    /**
     * Test exec without filename.
     */
    @Test
    public void testExecWithoutFilename() {
        // Given
        String testName = "test";
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));

        when(mockCopilotFile.getText()).thenReturn(TEST_CONTENT);
        when(mockCopilotFile.getFilename()).thenReturn("");
        when(mockCopilotFile.getName()).thenReturn(testName);

        // When
        textFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile, times(1)).getText();
        verify(mockCopilotFile, times(1)).getName();
        mockedFileUtils.verify(() -> FileUtils.processFileAttachment(eq(mockCopilotFile), any(), anyBoolean()));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
    }

    /**
     * Test exec with null text.
     */
    @Test
    public void testExecWithNullText() {
        // Given
        when(mockCopilotFile.getText()).thenReturn(null);
        when(mockCopilotFile.getName()).thenReturn("test");

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Error refreshing file");

        // When
        textFileHook.exec(mockCopilotFile);
    }

    /**
     * Test exec with non txt extension.
     */
    @Test
    public void testExecWithNonTxtExtension() {
        // Given
        String testFilename = "test.pdf";

        when(mockCopilotFile.getText()).thenReturn(TEST_CONTENT);
        when(mockCopilotFile.getFilename()).thenReturn(testFilename);
        when(mockCopilotFile.getName()).thenReturn("test");

        // When
        try {
            textFileHook.exec(mockCopilotFile);
        } catch (OBException e) {
            // Expected exception for non-txt extension
        }

        // Then
        // Adjust the verification to allow two invocations
        verify(mockCopilotFile, times(2)).getFilename(); // Expected to be called twice
        verify(mockCopilotFile, times(1)).getText();     // Verify text retrieval
    }
}
