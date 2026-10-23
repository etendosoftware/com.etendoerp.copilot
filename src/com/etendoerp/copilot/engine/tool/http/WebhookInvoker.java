/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedWebhookParam;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/** Invokes configured Etendo webhook implementations in-process after enforcing role access. */
public final class WebhookInvoker {

  private WebhookInvoker() {
  }

  /** Resolve the configured webhook, enforce role access and required parameters, then invoke it. */
  public static String invoke(ToolExecutionContext ctx, String webhookName,
      Map<String, String> params) throws Exception {
    if (ctx == null) {
      throw new IllegalArgumentException("A user tool context is required to invoke a webhook.");
    }
    if (webhookName == null || webhookName.isBlank()) {
      throw new IllegalArgumentException("A webhook name is required.");
    }
    Map<String, String> provided = params == null ? Map.of() : params;
    Invocation invocation = ctx.admin(() -> resolveInvocation(webhookName, ctx.roleId(), provided));

    @SuppressWarnings("unchecked")
    Class<? extends BaseWebhookService> handlerClass = (Class<? extends BaseWebhookService>) OBClassLoader
        .getInstance().loadClass(invocation.javaClass());
    BaseWebhookService service = WeldUtils.getInstanceFromStaticBeanManager(handlerClass);
    Map<String, String> response = new HashMap<>();
    service.get(invocation.params(), response);
    return toJson(response);
  }

  /** Enforce a webhook's role grant without calling the configured implementation. */
  public static void authorize(ToolExecutionContext ctx, String webhookName) throws Exception {
    if (ctx == null) {
      throw new IllegalArgumentException("A user tool context is required to invoke a webhook.");
    }
    if (webhookName == null || webhookName.isBlank()) {
      throw new IllegalArgumentException("A webhook name is required.");
    }
    ctx.admin(() -> {
      findAuthorizedHook(webhookName, ctx.roleId());
      return null;
    });
  }


  private static Invocation resolveInvocation(String webhookName, String roleId,
      Map<String, String> provided) throws JSONException {
    DefinedWebHook hook = findAuthorizedHook(webhookName, roleId);
    List<DefinedWebhookParam> configuredParams = new ArrayList<>(hook.getSmfwheDefinedwebhookParamList());
    return new Invocation(hook.getJavaClass(), collectParameters(configuredParams, provided));
  }

  private static DefinedWebHook findAuthorizedHook(String webhookName, String roleId) {
    OBCriteria<DefinedWebHook> hooks = OBDal.getInstance().createCriteria(DefinedWebHook.class);
    hooks.add(Restrictions.eq(DefinedWebHook.PROPERTY_NAME, webhookName));
    hooks.setMaxResults(1);
    DefinedWebHook hook = (DefinedWebHook) hooks.uniqueResult();
    if (hook == null) {
      throw new IllegalArgumentException("Webhook not found: " + webhookName);
    }
    Role role = OBDal.getInstance().get(Role.class, roleId);
    if (role == null || !hasRoleAccess(hook, role)) {
      throw new SecurityException("Role is not authorized to invoke webhook: " + webhookName);
    }
    return hook;
  }

  private static boolean hasRoleAccess(DefinedWebHook hook, Role role) {
    OBCriteria<DefinedwebhookRole> access = OBDal.getInstance().createCriteria(DefinedwebhookRole.class);
    access.setFilterOnReadableClients(false);
    access.setFilterOnReadableOrganization(false);
    access.add(Restrictions.eq(DefinedwebhookRole.PROPERTY_SMFWHEDEFINEDWEBHOOK, hook));
    access.add(Restrictions.eq(DefinedwebhookRole.PROPERTY_ROLE, role));
    access.setMaxResults(1);
    return access.uniqueResult() != null;
  }

  static Map<String, String> collectParameters(Collection<DefinedWebhookParam> configuredParams,
      Map<String, String> provided) {
    Map<String, String> result = provided == null ? new HashMap<>() : new HashMap<>(provided);
    for (DefinedWebhookParam param : configuredParams) {
      String name = param.getName();
      String value = provided == null ? null : provided.get(name);
      if (Boolean.TRUE.equals(param.isRequired()) && (value == null || value.isEmpty())) {
        throw new IllegalArgumentException("Missing required webhook parameter: " + name);
      }
    }
    return result;
  }

  private static String toJson(Map<String, String> response) throws JSONException {
    JSONObject json = new JSONObject();
    for (Map.Entry<String, String> entry : response.entrySet()) {
      json.put(entry.getKey(), entry.getValue());
    }
    return json.toString();
  }

  private record Invocation(String javaClass, Map<String, String> params) {
  }
}
