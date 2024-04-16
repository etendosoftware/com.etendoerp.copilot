package com.etendoerp.copilot.rest;

import org.openbravo.base.exception.OBException;

public class CopilotRestServiceException extends OBException {
  private int code = -1;

  public CopilotRestServiceException(String message) {
    super(message);
  }

  public CopilotRestServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  public CopilotRestServiceException(String message, int code) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
