package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

@RunWith(MockitoJUnitRunner.class)
public class ProcessHQLAppSourceTest {

    @InjectMocks
    private ProcessHQLAppSource processHQLAppSource;

    @Mock
    private CopilotAppSource mockAppSource;

    @Mock
    private CopilotFile mockFile;

    @Before
    public void setUp() {
        OBContext.setOBContext("0", "0", "0", "0");
        when(mockAppSource.getFile()).thenReturn(mockFile);
    }

    @Test
    public void testGenerate_Success() throws IOException {
        when(mockFile.getHql()).thenReturn("SELECT e FROM Entity e");
        when(mockFile.getFilename()).thenReturn("test.csv");

        File result = processHQLAppSource.generate(mockAppSource);

        assertNotNull(result);
        assertTrue(result.exists());
        assertEquals("test.csv", result.getName());
    }

    @Test(expected = OBException.class)
    public void testGenerate_IOException() throws IOException {
        when(mockFile.getHql()).thenReturn("SELECT e FROM Entity e");
        when(mockFile.getFilename()).thenReturn("test.csv");

        doThrow(new IOException()).when(mockFile).getHql();

        processHQLAppSource.generate(mockAppSource);
    }

    @Test
    public void testGetFileName_WithFilename() {
        when(mockFile.getFilename()).thenReturn("test.csv");

        String fileName = ProcessHQLAppSource.getFileName(mockAppSource);

        assertEquals("test.csv", fileName);
    }

    @Test
    public void testGetFileName_WithoutFilename() {
        when(mockFile.getName()).thenReturn("testName");

        String fileName = ProcessHQLAppSource.getFileName(mockAppSource);

        assertEquals("testName.csv", fileName);
    }

    @Test
    public void testGetFileName_Default() {
        String fileName = ProcessHQLAppSource.getFileName(mockAppSource);

        assertTrue(fileName.startsWith("result"));
        assertTrue(fileName.endsWith(".csv"));
    }

    // Additional tests for getHQLResult, printObject, and printBaseOBObject can be added here.
}
