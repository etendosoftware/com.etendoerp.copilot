package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.FileUtils;

/**
 * Text file hook test.
 */
public class ProcessHQLAppSourceTest extends WeldBaseTest {
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
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<FileUtils> mockedFileUtils;
  private MockedStatic<OBContext> mockedOBContext;
  private ProcessHQLAppSource processHqlAppSource;
  private AutoCloseable mocks;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  private static final String EXAMPLE_FILE_URL = "select e.table.dBTableName as bd_table_name,\n" +
      "e.dBColumnName as bd_column_name\n" +
      "from ADColumn e ";
  private static final String FILENAMETXT = "resulthql.txt";
  private CopilotAppSource mockCopilotAppSource;

  public void setUp() throws Exception {
    super.setUp();
    mocks = MockitoAnnotations.openMocks(this);
    processHqlAppSource = new ProcessHQLAppSource();

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN
    );
    VariablesSecureApp vsa = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);


    // Setup static mocks
    mockedWeldUtils = mockStatic(WeldUtils.class);
    mockedWeldUtils.when(
        () -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class)).thenReturn(
        mockAttachManager);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedFileUtils = mockStatic(FileUtils.class);
  }

  /**
   * Tear down.
   *
   * @throws Exception
   *     the exception
   */
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

  @Test
  public void textSuccessfullHQL() throws Exception {
    // Given
    String testUrl = EXAMPLE_FILE_URL;
    String fileName = "example.txt";
    when(mockCopilotFile.getHql()).thenReturn(testUrl);
    when(mockCopilotFile.getFilename()).thenReturn(fileName);

    mockCopilotAppSource = mock(CopilotAppSource.class);
    when(mockCopilotAppSource.getFile()).thenReturn(mockCopilotFile);

    // Mock file operations
    mockedFileUtils.when(() -> FileUtils.attachFile(any(), any(), any())).thenAnswer(invocation -> null);
    mockedFileUtils.when(() -> FileUtils.removeAttachment(any(), any())).thenAnswer(invocation -> null);

    // When
    File resultFile = processHqlAppSource.generate(mockCopilotAppSource);

    // Then
    String fileContent = Files.readString(resultFile.toPath());
    assertTrue(StringUtils.contains(fileContent, "bd_table_name"));
    assertTrue(StringUtils.contains(fileContent, "bd_column_name"));
    assertTrue(StringUtils.contains(fileContent, "AD_Table"));
    assertTrue(StringUtils.contains(fileContent, "AD_Column"));
  }

  @Test
  public void textSuccessfullHQLCSV() throws Exception {
    // Given
    String testUrl = EXAMPLE_FILE_URL;
    String fileName = "example.csv";
    when(mockCopilotFile.getHql()).thenReturn(testUrl);
    when(mockCopilotFile.getFilename()).thenReturn(fileName);

    mockCopilotAppSource = mock(CopilotAppSource.class);
    when(mockCopilotAppSource.getFile()).thenReturn(mockCopilotFile);

    // Mock file operations
    mockedFileUtils.when(() -> FileUtils.attachFile(any(), any(), any())).thenAnswer(invocation -> null);
    mockedFileUtils.when(() -> FileUtils.removeAttachment(any(), any())).thenAnswer(invocation -> null);

    // When
    File resultFile = processHqlAppSource.generate(mockCopilotAppSource);

    // Then
    String fileContent = Files.readString(resultFile.toPath());
    assertTrue(StringUtils.contains(fileContent, "bd_table_name"));
    assertTrue(StringUtils.contains(fileContent, "bd_column_name"));
    assertTrue(StringUtils.contains(fileContent, "AD_Table"));
    assertTrue(StringUtils.contains(fileContent, "AD_Column"));
  }
}
