/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool;

/** Raised when a server tool cannot be executed within its context or deadline. */
public class ToolExecutionException extends Exception {

  private static final long serialVersionUID = 1L;

  public ToolExecutionException(String message) {
    super(message);
  }

  public ToolExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
