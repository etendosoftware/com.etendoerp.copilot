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

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.database.SessionInfo;
import org.openbravo.dal.core.DalThreadCleaner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Language;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotUtils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * An immutable snapshot of the identity for one question, plus an explicit per-question attribute
 * map. Each {@link #call(Duration, Callable)} runs on a bounded worker with a fresh non-admin
 * {@link OBContext} and transaction.
 */
public final class ToolExecutionContext {

  private static final int WORKER_COUNT = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
  private static final int QUEUE_CAPACITY = 64;
  private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
  private static final ThreadPoolExecutor TOOL_EXECUTOR = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT,
      0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), new ToolThreadFactory(),
      new ThreadPoolExecutor.AbortPolicy());
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
  private static final ConcurrentMap<String, AtomicInteger> ACTIVE_WORKSPACES = new ConcurrentHashMap<>();

  private final String userId;
  private final String roleId;
  private final String clientId;
  private final String orgId;
  private final String warehouseId;
  private final String language;
  private final String agentId;
  private final String conversationId;
  private final boolean webServiceEnabled;
  private final JSONObject extraInfo;
  private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();
  private final Binding binding;

  ToolExecutionContext(String userId, String roleId, String clientId, String orgId,
      String warehouseId, String language, String agentId, String conversationId,
      boolean webServiceEnabled, JSONObject extraInfo, Binding binding) {
    this.userId = userId;
    this.roleId = roleId;
    this.clientId = clientId;
    this.orgId = orgId;
    this.warehouseId = warehouseId;
    this.language = language;
    this.agentId = agentId;
    this.conversationId = conversationId;
    this.webServiceEnabled = webServiceEnabled;
    this.extraInfo = copy(extraInfo);
    this.extraInfo.remove("auth");
    this.binding = binding;
  }

  /** Capture the effective SWS identity on the current DAL thread before any tool thread hop. */
  public static ToolExecutionContext capture(CopilotApp app, String conversationId, JSONObject extraInfo) {
    OBContext context = OBContext.getOBContext();
    if (context == null) {
      throw new OBException("Cannot execute a server tool without an active Etendo user context.");
    }
    CopilotUtils.EtendoSWSIdentity identity = CopilotUtils.resolveEtendoSWSIdentity(context, null);
    Language language = context.getLanguage();
    String languageCode = language == null ? null : language.getLanguage();
    return new ToolExecutionContext(identity.userId(), identity.roleId(), identity.clientId(), identity.orgId(),
        identity.warehouseId(), languageCode, app == null ? null : app.getId(), conversationId,
        identity.webServiceEnabled(), extraInfo, EtendoBinding.INSTANCE);
  }

  public String userId() {
    return userId;
  }

  public String roleId() {
    return roleId;
  }

  /** Role client's ID, matching the SWS JWT client claim. */
  public String clientId() {
    return clientId;
  }

  public String orgId() {
    return orgId;
  }

  public String warehouseId() {
    return warehouseId;
  }

  public String language() {
    return language;
  }

  public String agentId() {
    return agentId;
  }

  public String conversationId() {
    return conversationId;
  }

  public boolean webServiceEnabled() {
    return webServiceEnabled;
  }

  /** Return a defensive copy so callers cannot mutate the captured extra information. */
  public JSONObject extraInfo() {
    return copy(extraInfo);
  }

  /** Mutable scratch space scoped to this captured question. */
  public ConcurrentMap<String, Object> attributes() {
    return attributes;
  }

  /** True while any bounded server-tool call is using this managed workspace identity. */
  public static boolean isWorkspaceActive(String clientId, String userId, String conversationId) {
    return ACTIVE_WORKSPACES.containsKey(workspaceKey(clientId, userId, conversationId));
  }

  /** Custom workspace roots omit the client/user path levels; any matching active conversation is protected. */
  public static boolean isConversationActive(String conversationId) {
    String suffix = "/" + safeSegment(conversationId, "mcp");
    return ACTIVE_WORKSPACES.keySet().stream().anyMatch(key -> key.endsWith(suffix));
  }

  private static String workspaceKey(String clientId, String userId, String conversationId) {
    return safeSegment(clientId, "mcp") + "/" + safeSegment(userId, "mcp") + "/"
        + safeSegment(conversationId, "mcp");
  }

  private static String safeSegment(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    String segment = value.replaceAll("[^A-Za-z0-9._-]", "_");
    return segment.isBlank() || segment.equals(".") || segment.equals("..") ? "default" : segment;
  }

  /**
   * Run the body on a dedicated, bounded daemon worker with this identity bound to DAL state.
   * A timeout cancels the worker with interruption; success commits and every failure rolls back.
   */
  public <T> T call(Duration timeout, Callable<T> body) throws ToolExecutionException {
    if (body == null) {
      throw new IllegalArgumentException("A tool body is required.");
    }
    Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (effectiveTimeout.isZero() || effectiveTimeout.isNegative()) {
      throw new IllegalArgumentException("A tool timeout must be positive.");
    }
    AtomicBoolean cancelled = new AtomicBoolean();
    Future<T> future;
    try {
      future = TOOL_EXECUTOR.submit(() -> runBound(body, cancelled));
    } catch (RuntimeException e) {
      throw new ToolExecutionException("The server tool executor is unavailable.", e);
    }
    long timeoutNanos;
    try {
      timeoutNanos = effectiveTimeout.toNanos();
    } catch (ArithmeticException e) {
      timeoutNanos = Long.MAX_VALUE;
    }
    try {
      return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      cancelled.set(true);
      future.cancel(true);
      throw new ToolExecutionException("The server tool exceeded its execution timeout.", e);
    } catch (InterruptedException e) {
      cancelled.set(true);
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new ToolExecutionException("The server tool execution was interrupted.", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      throw new ToolExecutionException("The server tool execution failed.", cause);
    }
  }

  /** Run a narrow, balanced administrator block inside an already-bound tool call. */
  public <T> T admin(Callable<T> body) throws Exception {
    OBContext.setAdminMode();
    try {
      return body.call();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /** Mint a fresh SWS token for an HTTP tool, or return {@code null} for a non-WS role. */
  public String mintToken() {
    if (!webServiceEnabled) {
      return null;
    }
    try {
      return CopilotUtils.getEtendoSWSToken(OBContext.getOBContext(),
          OBDal.getInstance().get(Role.class, roleId));
    } catch (Exception e) {
      throw new OBException("Unable to mint the Secure Web Services token for this tool call.", e);
    }
  }

  /** @return true only for the compatibility placeholder used in tool arguments */
  public static boolean isTokenPlaceholder(String token) {
    return "ETENDO_TOKEN".equals(token);
  }

  private <T> T runBound(Callable<T> body, AtomicBoolean cancelled) throws Exception {
    String workspaceKey = workspaceKey(clientId, userId, conversationId);
    ACTIVE_WORKSPACES.compute(workspaceKey, (key, count) -> {
      if (count == null) count = new AtomicInteger();
      count.incrementAndGet();
      return count;
    });
    boolean success = false;
    try {
      binding.bind(this);
      T result = body.call();
      success = !cancelled.get() && !Thread.currentThread().isInterrupted();
      return result;
    } finally {
      try {
        try {
          binding.finish(success);
        } finally {
          Thread.interrupted();
        }
      } finally {
        ACTIVE_WORKSPACES.computeIfPresent(workspaceKey, (key, count) ->
            count.decrementAndGet() == 0 ? null : count);
      }
    }
  }

  private static JSONObject copy(JSONObject source) {
    if (source == null) {
      return new JSONObject();
    }
    try {
      return new JSONObject(source.toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException("The extra tool context is not valid JSON.", e);
    }
  }

  interface Binding {
    void bind(ToolExecutionContext context);

    void finish(boolean success);
  }

  private static final class EtendoBinding implements Binding {
    private static final EtendoBinding INSTANCE = new EtendoBinding();

    @Override
    public void bind(ToolExecutionContext context) {
      resetThreadState();
      OBContext workerContext = SecureWebServicesUtils.createContext(context.userId, context.roleId,
          context.orgId, context.warehouseId, context.clientId);
      if (workerContext == null) {
        throw new OBException("Unable to create the server tool's user context.");
      }
      OBContext.setOBContext(workerContext);
      OBContext.clearAdminModeStack();
      if (OBContext.getOBContext() != workerContext) {
        throw new OBException("Unable to bind the server tool's user context.");
      }
      SessionInfo.setUserId(context.userId);
      SessionInfo.setProcessType("COPILOT");
      SessionInfo.setProcessId(context.agentId);
    }

    @Override
    public void finish(boolean success) {
      try {
        if (SessionHandler.isSessionHandlerPresent()) {
          if (success) {
            DalThreadCleaner.getInstance().cleanWithCommit();
          } else {
            DalThreadCleaner.getInstance().cleanWithRollback();
          }
        }
      } catch (RuntimeException | Error failure) {
        if (success && SessionHandler.isSessionHandlerPresent()) {
          DalThreadCleaner.getInstance().cleanWithRollback();
        }
        throw failure;
      } finally {
        try {
          OBContext.clearAdminModeStack();
        } finally {
          try {
            SessionInfo.init();
          } finally {
            try {
              SessionHandler.deleteSessionHandler();
            } finally {
              OBContext.setOBContext((OBContext) null);
            }
          }
        }
      }
    }

    private static void resetThreadState() {
      try {
        if (SessionHandler.isSessionHandlerPresent()) {
          DalThreadCleaner.getInstance().cleanWithRollback();
        }
      } finally {
        try {
          OBContext.clearAdminModeStack();
        } finally {
          try {
            SessionInfo.init();
          } finally {
            try {
              SessionHandler.deleteSessionHandler();
            } finally {
              OBContext.setOBContext((OBContext) null);
            }
          }
        }
      }
    }
  }

  private static final class ToolThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "copilot-tool-" + THREAD_NUMBER.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    }
  }
}
