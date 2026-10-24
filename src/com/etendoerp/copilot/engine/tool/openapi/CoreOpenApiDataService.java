/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import java.util.Map;
import org.openbravo.service.json.DefaultJsonDataService;

/** Calls Etendo's core JSON data service without HTTP or context/admin-mode changes. */
final class CoreOpenApiDataService implements OpenApiDataService {
  private DefaultJsonDataService service() {
    DefaultJsonDataService service = DefaultJsonDataService.getInstance();
    if (service == null) throw new IllegalStateException("Core JSON data service is unavailable");
    return service;
  }
  @Override public String fetch(Map<String, String> parameters) { return service().fetch(parameters); }
}
