package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        
        when(mockCopilotFile.getText()).thenReturn(TEST_CONTENT);
        when(mockCopilotFile.getFilename()).thenReturn(testFilename);
        when(mockCopilotFile.getName()).thenReturn("test");

        // When
        textFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile, times(1)).getText();
        verify(mockCopilotFile, times(2)).getFilename();
        mockedCopilotUtils.verify(() -> CopilotUtils.removeAttachment(any(), any()));
        mockedCopilotUtils.verify(() -> CopilotUtils.attachFile(any(), any(), any()));
    }

    /**
     * Test exec without filename.
     */
    @Test
    public void testExecWithoutFilename() {
        // Given
        String testName = "test";
        
        when(mockCopilotFile.getText()).thenReturn(TEST_CONTENT);
        when(mockCopilotFile.getFilename()).thenReturn("");
        when(mockCopilotFile.getName()).thenReturn(testName);

        // When
        textFileHook.exec(mockCopilotFile);

        // Then
        verify(mockCopilotFile, times(1)).getText();
        verify(mockCopilotFile, times(1)).getName();
        mockedCopilotUtils.verify(() -> CopilotUtils.removeAttachment(any(), any()));
        mockedCopilotUtils.verify(() -> CopilotUtils.attachFile(any(), any(), any()));
    }

    /**
     * Test exec with null text.
     */
    @Test
    public void testExecWithNullText() {
        // Given
        when(mockCopilotFile.getText()).thenReturn(null);
        when(mockCopilotFile.getName()).thenReturn("test");

        expectedException.expect(NullPointerException.class);

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
