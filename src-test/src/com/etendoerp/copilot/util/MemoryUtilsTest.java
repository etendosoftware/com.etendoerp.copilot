package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.AgentMemory;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Test class for MemoryUtils utility methods.
 *
 * <p>This test suite validates the functionality of saving agent memory records,
 * including proper handling of null parameters and correct population of contextual
 * information from the OBContext.</p>
 */
public class MemoryUtilsTest extends WeldBaseTest {

  /**
   * Expected exception rule for testing exception scenarios.
   */
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBDal> mockedOBDal;
  private AutoCloseable mocks;

  @Mock
  private OBProvider mockOBProviderInstance;

  @Mock
  private OBContext mockOBContext;

  @Mock
  private OBDal mockOBDal;

  @Mock
  private AgentMemory mockAgentMemory;

  @Mock
  private CopilotApp mockCopilotApp;

  @Mock
  private Organization mockOrganization;

  @Mock
  private Role mockRole;

  @Mock
  private User mockUser;

  private static final String TEST_QUESTION = "What is the weather today?";

  /**
   * Set up test environment before each test.
   * Initializes mocks and configures static mock behavior.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // Setup static mocks
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockOBProviderInstance);

    mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(mockOBContext);

    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);

    // Setup mock behavior
    when(mockOBProviderInstance.get(AgentMemory.class)).thenReturn(mockAgentMemory);
    when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockOBContext.getRole()).thenReturn(mockRole);
    when(mockOBContext.getUser()).thenReturn(mockUser);
  }

  /**
   * Tear down test environment after each test.
   * Closes all mocks properly.
   *
   * @throws Exception if teardown fails
   */
  @After
  public void tearDown() throws Exception {
    if (mockedOBProvider != null) {
      mockedOBProvider.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  /**
   * Test successful save of memory from question.
   * Validates that all fields are properly set and save is called.
   */
  @Test
  public void testSaveMemoryFromQuestion_Success() {
    // Execute
    MemoryUtils.saveMemoryFromQuestion(TEST_QUESTION, mockCopilotApp);

    // Verify
    verify(mockOBProviderInstance).get(AgentMemory.class);
    verify(mockAgentMemory).setAgent(mockCopilotApp);
    verify(mockAgentMemory).setOrganization(mockOrganization);
    verify(mockAgentMemory).setRole(mockRole);
    verify(mockAgentMemory).setUserContact(mockUser);
    verify(mockAgentMemory).setTextField(TEST_QUESTION);
    verify(mockOBDal).save(mockAgentMemory);
  }

  /**
   * Test that null question throws NullPointerException.
   */
  @Test
  public void testSaveMemoryFromQuestion_NullQuestion() {
    // Expect
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("question must not be null");

    // Execute
    MemoryUtils.saveMemoryFromQuestion(null, mockCopilotApp);
  }

  /**
   * Test that null copilotApp throws NullPointerException.
   */
  @Test
  public void testSaveMemoryFromQuestion_NullCopilotApp() {
    // Expect
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("copilotApp must not be null");

    // Execute
    MemoryUtils.saveMemoryFromQuestion(TEST_QUESTION, null);
  }

  /**
   * Test that empty question is handled properly.
   */
  @Test
  public void testSaveMemoryFromQuestion_EmptyQuestion() {
    // Execute
    MemoryUtils.saveMemoryFromQuestion("", mockCopilotApp);

    // Verify - empty string is valid, should still save
    verify(mockAgentMemory).setTextField("");
    verify(mockOBDal).save(mockAgentMemory);
  }

  /**
   * Test that long question text is handled properly.
   */
  @Test
  public void testSaveMemoryFromQuestion_LongQuestion() {
    // Setup
    String longQuestion = "This is a very long question ".repeat(100);

    // Execute
    MemoryUtils.saveMemoryFromQuestion(longQuestion, mockCopilotApp);

    // Verify
    verify(mockAgentMemory).setTextField(longQuestion);
    verify(mockOBDal).save(mockAgentMemory);
  }

  /**
   * Test that special characters in question are handled properly.
   */
  @Test
  public void testSaveMemoryFromQuestion_SpecialCharacters() {
    // Setup
    String questionWithSpecialChars = "¿Qué es esto? <script>alert('test')</script> & \"quotes\"";

    // Execute
    MemoryUtils.saveMemoryFromQuestion(questionWithSpecialChars, mockCopilotApp);

    // Verify
    verify(mockAgentMemory).setTextField(questionWithSpecialChars);
    verify(mockOBDal).save(mockAgentMemory);
  }

  /**
   * Test that the context information is properly retrieved.
   * Validates that the method calls the correct context getters.
   */
  @Test
  public void testSaveMemoryFromQuestion_ContextInformation() {
    // Execute
    MemoryUtils.saveMemoryFromQuestion(TEST_QUESTION, mockCopilotApp);

    // Verify context calls
    mockedOBContext.verify(OBContext::getOBContext);
    verify(mockOBContext).getCurrentOrganization();
    verify(mockOBContext).getRole();
    verify(mockOBContext).getUser();
  }

  /**
   * Test that both parameters null throws NullPointerException for question first.
   * The method should check question first according to the implementation order.
   */
  @Test
  public void testSaveMemoryFromQuestion_BothParametersNull() {
    // Expect
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage("question must not be null");

    // Execute
    MemoryUtils.saveMemoryFromQuestion(null, null);
  }

  /**
   * Test that multiline question is handled properly.
   */
  @Test
  public void testSaveMemoryFromQuestion_MultilineQuestion() {
    // Setup
    String multilineQuestion = "Line 1\nLine 2\nLine 3\nWhat is the answer?";

    // Execute
    MemoryUtils.saveMemoryFromQuestion(multilineQuestion, mockCopilotApp);

    // Verify
    verify(mockAgentMemory).setTextField(multilineQuestion);
    verify(mockOBDal).save(mockAgentMemory);
  }
}
