package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.EntityAccessChecker;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;
import org.openbravo.model.ad.system.Language;

public class RemoteFileHookTest extends WeldBaseTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private RemoteFileHook remoteFileHook;
    private AutoCloseable mocks;

    @Mock
    private CopilotFile mockCopilotFile;
    @Mock
    private AttachImplementationManager mockAttachManager;
    private MockedStatic<WeldUtils> mockedWeldUtils;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private Path tempDirectory;

    private MockedStatic<OBContext> mockedOBContext;

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

        mockedCopilotUtils = mockStatic(CopilotUtils.class);
    }

    @After
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        if (mockedWeldUtils != null) {
            mockedWeldUtils.close();
        }
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
    }


    @Test
    public void testTypeCheck_ValidType() {
        assertTrue(remoteFileHook.typeCheck("RF"));
    }

    @Test
    public void testTypeCheck_InvalidType() {
        assertFalse(remoteFileHook.typeCheck("INVALID"));
    }

    @Test
    public void testGetFinalName_WithCustomName() throws Exception {
        URL url = new URL("https://example-files.online-convert.com/document/txt/example.txt");
        String finalName = RemoteFileHook.getFinalName("custom", url);
        assertEquals("custom.txt", finalName);
    }

    @Test
    public void testGetFinalName_WithoutCustomName() throws Exception {
        URL url = new URL("https://example-files.online-convert.com/document/txt/example.txt");
        String finalName = RemoteFileHook.getFinalName("", url);
        assertEquals("example.txt", finalName);
    }

    @Test
    public void testGetFinalName_CustomNameWithExtension() throws Exception {
        URL url = new URL("https://example-files.online-convert.com/document/txt/example.txt");
        String finalName = RemoteFileHook.getFinalName("custom.txt", url);
        assertEquals("custom.txt", finalName);
    }

    @Test
    public void testExec_SuccessfulDownload() throws Exception {
        // Given
        String testUrl = "https://example-files.online-convert.com/document/txt/example.txt";
        String fileName = "example.txt";
        when(mockCopilotFile.getUrl()).thenReturn(testUrl);
        when(mockCopilotFile.getFilename()).thenReturn(fileName);
        mockedCopilotUtils.when(() -> CopilotUtils.replaceCopilotPromptVariables(testUrl))
                .thenReturn(testUrl);

        // Mock file operations
        mockedCopilotUtils.when(() -> CopilotUtils.attachFile(any(), any(), any())).thenAnswer(invocation -> null);
        mockedCopilotUtils.when(() -> CopilotUtils.removeAttachment(any(), any())).thenAnswer(invocation -> null);

        // When
        remoteFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile).getUrl();
        verify(mockCopilotFile).getFilename();
        mockedCopilotUtils.verify(() -> CopilotUtils.removeAttachment(any(), any()));
        mockedCopilotUtils.verify(() -> CopilotUtils.attachFile(any(), any(), any()));
    }

    @Test
    public void testExec_DownloadError() throws Exception {
        // Given
        String invalidUrl = "invalid://url";
        when(mockCopilotFile.getUrl()).thenReturn(invalidUrl);
        when(mockCopilotFile.getFilename()).thenReturn("");
        mockedCopilotUtils.when(() -> CopilotUtils.replaceCopilotPromptVariables(invalidUrl))
                .thenReturn(invalidUrl);

        expectedException.expect(OBException.class);
        expectedException.expectMessage(String.format(OBMessageUtils.messageBD("ETCOP_FileDownErr"), invalidUrl));

        // When
        remoteFileHook.exec(mockCopilotFile);
    }

    @Test
    public void testDownloadFile_InvalidUrl() throws Exception {
        expectedException.expect(IOException.class);
        RemoteFileHook.downloadFile("invalid://url", "test.txt");
    }
}