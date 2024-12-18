package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.attachment.AttachImplementationManager;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;

@RunWith(MockitoJUnitRunner.class)
public class TextFileHookTest {

    @Mock
    private AttachImplementationManager attachImplementationManager;

    @Mock
    private CopilotFile copilotFile;

    @InjectMocks
    private TextFileHook textFileHook;

    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testExec_GeneratesTextFileAndAttaches() throws IOException {
        // Given
        String text = "Sample text content";
        String fileName = "sampleFile.txt";
        when(copilotFile.getText()).thenReturn(text);
        when(copilotFile.getFilename()).thenReturn(fileName);

        // When
        textFileHook.exec(copilotFile);

        // Then
        verify(copilotFile).getText();
        verify(copilotFile).getFilename();
        verify(attachImplementationManager, times(1)).removeAttachment(any(), eq(copilotFile));
        verify(attachImplementationManager, times(1)).attachFile(eq(copilotFile), any(), any(File.class));
    }

    @Test(expected = OBException.class)
    public void testExec_ThrowsOBExceptionOnIOException() throws IOException {
        // Given
        String text = "Sample text content";
        String fileName = "sampleFile.txt";
        when(copilotFile.getText()).thenReturn(text);
        when(copilotFile.getFilename()).thenReturn(fileName);

        // Simulate IOException
        doThrow(new IOException()).when(attachImplementationManager).attachFile(any(), any(), any(File.class));

        // When
        textFileHook.exec(copilotFile);
    }

    @Test
    public void testTypeCheck_ReturnsTrueForTXT() {
        assertTrue(textFileHook.typeCheck("TXT"));
    }

    @Test
    public void testTypeCheck_ReturnsFalseForNonTXT() {
        assertFalse(textFileHook.typeCheck("PDF"));
    }
}
