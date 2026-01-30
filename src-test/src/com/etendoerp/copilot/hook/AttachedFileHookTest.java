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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
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
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.FileUtils;

/**
 * Attached file hook test.
 */
public class AttachedFileHookTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AttachedFileHook attachedFileHook;
    private MockedStatic<WeldUtils> mockedWeldUtils;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<FileUtils> mockedFileUtils;
    private AutoCloseable mocks;

    @Mock
    private CopilotFile mockCopilotFile;

    @Mock
    private AttachImplementationManager mockAttachManager;

    @Mock
    private Attachment mockAttachment;

    private static final String TEST_FILENAME = "test.pdf";
    private static final String TEST_ATTACHMENT_ID = "test-attachment-id";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        attachedFileHook = new AttachedFileHook();

        // Inject the mocked AttachImplementationManager using reflection
        Field aimField = AttachedFileHook.class.getDeclaredField("aim");
        aimField.setAccessible(true);
        aimField.set(attachedFileHook, mockAttachManager);

        // Setup static mocks
        mockedWeldUtils = mockStatic(WeldUtils.class);
        mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
                .thenReturn(mockAttachManager);

        mockedCopilotUtils = mockStatic(CopilotUtils.class);
        mockedFileUtils = mockStatic(FileUtils.class);
        
        // Setup FileUtils mock behaviors
        mockedFileUtils.when(() -> FileUtils.createSecureTempFile(any(), any())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            String suffix = invocation.getArgument(1);
            return Files.createTempFile(prefix, suffix);
        });
        mockedFileUtils.when(() -> FileUtils.processFileAttachment(any(), any(), anyBoolean())).thenAnswer(invocation -> null);
        mockedFileUtils.when(() -> FileUtils.cleanupTempFileIfNeeded(any(), any())).thenAnswer(invocation -> null);
        mockedFileUtils.when(() -> FileUtils.getAttachment(any())).thenReturn(mockAttachment);
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
     * Test type check valid type F.
     */
    @Test
    public void testTypeCheckValidTypeF() {
        assertTrue("Should return true for F type", attachedFileHook.typeCheck(CopilotConstants.FILE_TYPE_F));
    }

    /**
     * Test type check valid type attached.
     */
    @Test
    public void testTypeCheckValidTypeAttached() {
        assertTrue("Should return true for KBF attached type", attachedFileHook.typeCheck(CopilotConstants.KBF_TYPE_ATTACHED));
    }

    /**
     * Test type check invalid type.
     */
    @Test
    public void testTypeCheckInvalidType() {
        assertFalse("Should return false for TXT type", attachedFileHook.typeCheck("TXT"));
    }

    /**
     * Test exec with valid attachment.
     */
    @Test
    public void testExecWithValidAttachment() throws Exception {
        // Given
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));
        when(mockCopilotFile.getName()).thenReturn("test");
        when(mockAttachment.getId()).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn(TEST_FILENAME);

        doNothing().when(mockAttachManager).download(anyString(), any(ByteArrayOutputStream.class));

        // When
        attachedFileHook.exec(mockCopilotFile);

        // Then
        mockedFileUtils.verify(() -> FileUtils.getAttachment(eq(mockCopilotFile)), times(1));
        verify(mockAttachManager, times(1)).download(eq(TEST_ATTACHMENT_ID), any(ByteArrayOutputStream.class));
        mockedFileUtils.verify(() -> FileUtils.processFileAttachment(eq(mockCopilotFile), any(), anyBoolean()));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
    }

    /**
     * Test exec with null attachment.
     */
    @Test
    public void testExecWithNullAttachment() {
        // Given
        mockedFileUtils.when(() -> FileUtils.getAttachment(any())).thenReturn(null);
        mockedFileUtils.when(() -> FileUtils.throwMissingAttachException(any())).thenThrow(new OBException("Missing attachment"));

        when(mockCopilotFile.getName()).thenReturn("test");

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Missing attachment");

        // When
        attachedFileHook.exec(mockCopilotFile);
    }

    /**
     * Test exec with download error.
     */
    @Test
    public void testExecWithDownloadError() throws Exception {
        // Given
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));
        when(mockCopilotFile.getName()).thenReturn("test");
        when(mockAttachment.getId()).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn(TEST_FILENAME);

        doThrow(new RuntimeException("Download failed"))
                .when(mockAttachManager).download(anyString(), any(ByteArrayOutputStream.class));

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Error processing attached file");

        // When
        attachedFileHook.exec(mockCopilotFile);
    }

    /**
     * Test exec with filename without extension.
     */
    @Test
    public void testExecWithFilenameWithoutExtension() throws Exception {
        // Given
        String filenameWithoutExt = "testfile";
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));
        when(mockCopilotFile.getName()).thenReturn("test");
        when(mockAttachment.getId()).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn(filenameWithoutExt);

        doNothing().when(mockAttachManager).download(anyString(), any(ByteArrayOutputStream.class));

        // When
        attachedFileHook.exec(mockCopilotFile);

        // Then
        mockedFileUtils.verify(() -> FileUtils.getAttachment(eq(mockCopilotFile)), times(1));
        verify(mockAttachManager, times(1)).download(eq(TEST_ATTACHMENT_ID), any(ByteArrayOutputStream.class));
        mockedFileUtils.verify(() -> FileUtils.createSecureTempFile(eq(filenameWithoutExt), eq("")));
        mockedFileUtils.verify(() -> FileUtils.processFileAttachment(eq(mockCopilotFile), any(), anyBoolean()));
        mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
    }

    /**
     * Test exec cleans up temp file on error.
     */
    @Test
    public void testExecCleansUpTempFileOnError() throws Exception {
        // Given
        when(mockCopilotFile.getClient()).thenReturn(mock(org.openbravo.model.ad.system.Client.class));
        when(mockCopilotFile.getName()).thenReturn("test");
        when(mockAttachment.getId()).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn(TEST_FILENAME);

        doNothing().when(mockAttachManager).download(anyString(), any(ByteArrayOutputStream.class));
        mockedFileUtils.when(() -> FileUtils.processFileAttachment(any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("Processing error"));

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Error processing attached file");

        // When
        try {
            attachedFileHook.exec(mockCopilotFile);
        } finally {
            // Then - verify cleanup was called even on error
            mockedFileUtils.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(mockCopilotFile), any()));
        }
    }
}
