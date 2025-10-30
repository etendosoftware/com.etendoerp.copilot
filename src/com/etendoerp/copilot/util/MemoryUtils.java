package com.etendoerp.copilot.util;

import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.AgentMemory;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Utility class with helpers to persist agent-related memory entries.
 *
 * <p>This class provides simple convenience methods to create and persist
 * {@link com.etendoerp.copilot.data.AgentMemory} instances associated with a
 * {@link com.etendoerp.copilot.data.CopilotApp}.</p>
 */
public class MemoryUtils {
  // Private constructor to prevent instantiation of this utility class
  private MemoryUtils() {
    // Utility class
  }

  /**
   * Persists a question as an agent memory record associated with the provided CopilotApp.
   *
   * <p>The saved memory will be created with the current {@code Organization}, {@code Role}
   * and {@code User} from the {@link org.openbravo.dal.core.OBContext} and the provided
   * question will be stored in the {@code textField} property of the resulting
   * {@link com.etendoerp.copilot.data.AgentMemory} record.</p>
   *
   * @param question the question text to store as memory; must not be {@code null}
   * @param copilotApp the CopilotApp instance to which the memory will be associated; must not be {@code null}
   * @throws NullPointerException if {@code question} or {@code copilotApp} is {@code null}
   */
  public static void saveMemoryFromQuestion(String question, CopilotApp copilotApp) {
    // Implementation to save memory from question
    if (question == null) {
      throw new NullPointerException("question must not be null");
    }
    if (copilotApp == null) {
      throw new NullPointerException("copilotApp must not be null");
    }

    AgentMemory mem = OBProvider.getInstance().get(AgentMemory.class);
    // Save the question as memory associated with the CopilotApp
    mem.setAgent(copilotApp);
    OBContext obContext = OBContext.getOBContext();
    mem.setOrganization(obContext.getCurrentOrganization());
    mem.setRole(obContext.getRole());
    mem.setUserContact(obContext.getUser());
    mem.setTextField(question);
    OBDal.getInstance().save(mem);
  }
}
