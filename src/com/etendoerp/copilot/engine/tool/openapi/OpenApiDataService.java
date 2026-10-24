/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import java.util.Map;

/** Minimal JSON data-service boundary, injectable for deterministic operation tests. */
public interface OpenApiDataService {
  String fetch(Map<String, String> parameters) throws Exception;
}
