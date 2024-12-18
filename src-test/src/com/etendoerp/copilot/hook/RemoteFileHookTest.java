package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.attachment.AttachImplementationManager;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;

public class RemoteFileHookTest {

    @Mock
    private CopilotFile mockCopilotFile;

    @Mock
    private AttachImplementationManager mockAttachManager;

    private RemoteFileHook remoteFileHook;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        remoteFileHook = new RemoteFileHook();
    }

    @Test
    public void testExec_Success() throws Exception {
        // Given
        String url = "http://example.com/file.txt";
        String fileName = "file.txt";
        when(mockCopilotFile.getUrl()).thenReturn(url);
        when(mockCopilotFile.getFilename()).thenReturn(fileName);

        // When
        remoteFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile).getUrl();
        verify(mockCopilotFile).getFilename();
    }

    @Test
    public void testExec_IOException() throws Exception {
        // Given
        String url = "http://example.com/file.txt";
        when(mockCopilotFile.getUrl()).thenReturn(url);
        doThrow(new IOException()).when(mockCopilotFile).getUrl();

        // Expect
        expectedException.expect(OBException.class);
        expectedException.expectMessage("ETCOP_FileDownErr");

        // When
        remoteFileHook.exec(mockCopilotFile);
    }

    @Test
    public void testDownloadFile_Success() throws Exception {
        // Given
        String url = "http://example.com/file.txt";
        String customName = "customFile.txt";

        // When
        Path result = RemoteFileHook.downloadFile(url, customName);

        // Then
        assertNotNull(result);
        assertTrue(result.toFile().exists());
    }

    @Test
    public void testGetFinalName_WithCustomName() throws Exception {
        // Given
        String customName = "customFile";
        URL url = new URL("http://example.com/file.txt");

        // When
        String result = RemoteFileHook.getFinalName(customName, url);

        // Then
        assertEquals("customFile.txt", result);
    }

    @Test
    public void testGetFinalName_WithoutCustomName() throws Exception {
        // Given
        String customName = "";
        URL url = new URL("http://example.com/file.txt");

        // When
        String result = RemoteFileHook.getFinalName(customName, url);

        // Then
        assertEquals("file.txt", result);
    }

    @Test
    public void testTypeCheck_ValidType() {
        // When
        boolean result = remoteFileHook.typeCheck("RF");

        // Then
        assertTrue(result);
    }

    @Test
    public void testTypeCheck_InvalidType() {
        // When
        boolean result = remoteFileHook.typeCheck("INVALID");

        // Then
        assertFalse(result);
    }
}
