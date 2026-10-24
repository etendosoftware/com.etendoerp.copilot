package com.etendoerp.copilot.kb;

import org.openbravo.base.exception.OBException;

/** Raised when a knowledge base contains vectors from a different embedding model or dimension. */
public final class KnowledgeBaseIncompatibleException extends OBException {
  public KnowledgeBaseIncompatibleException(String message) { super(message); }
}
