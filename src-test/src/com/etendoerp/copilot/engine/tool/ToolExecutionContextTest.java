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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/** Exercises worker binding and cleanup through the context's lifecycle seam without a DAL fixture. */
public class ToolExecutionContextTest {

  @Test
  public void callBindsIdentityOnWorkerAndCleansTheThreadAfterSuccessAndFailure() throws Exception {
    ThreadLocal<String> workerIdentity = new ThreadLocal<>();
    AtomicInteger commits = new AtomicInteger();
    AtomicInteger rollbacks = new AtomicInteger();
    AtomicInteger cleanupCount = new AtomicInteger();
    AtomicInteger leakedWorkerState = new AtomicInteger();
    AtomicReference<String> workerName = new AtomicReference<>();
    ToolExecutionContext.Binding binding = new ToolExecutionContext.Binding() {
      @Override
      public void bind(ToolExecutionContext context) {
        assertNull("previous worker state must be gone", workerIdentity.get());
        workerIdentity.set(context.userId() + ":" + context.roleId());
      }

      @Override
      public void finish(boolean success) {
        if (success) {
          commits.incrementAndGet();
        } else {
          rollbacks.incrementAndGet();
        }
        workerIdentity.remove();
        if (workerIdentity.get() != null) {
          leakedWorkerState.incrementAndGet();
        }
        cleanupCount.incrementAndGet();
      }
    };
    JSONObject extraInfo = new JSONObject().put("auth",
        new JSONObject().put("ETENDO_TOKEN", "never-visible-token")).put("tool_config", "enabled");
    ToolExecutionContext context = new ToolExecutionContext("user-1", "role-1", "client-1",
        "org-1", null, "en_US", "agent-1", "conversation-1", false, extraInfo, binding);
    JSONObject exposedExtraInfo = context.extraInfo();
    assertFalse(exposedExtraInfo.has("auth"));
    exposedExtraInfo.put("tool_config", "changed");
    assertEquals("enabled", context.extraInfo().getString("tool_config"));

    String identity = context.call(Duration.ofSeconds(5), () -> {
      workerName.set(Thread.currentThread().getName());
      assertTrue(Thread.currentThread().isDaemon());
      assertEquals("user-1:role-1", workerIdentity.get());
      return workerIdentity.get();
    });
    assertEquals("user-1:role-1", identity);
    assertTrue(workerName.get().startsWith("copilot-tool-"));

    // Reuse workers repeatedly after their previous identity binding has been cleared.
    for (int i = 0; i < 9; i++) {
      assertEquals("user-1:role-1", context.call(Duration.ofSeconds(5), workerIdentity::get));
    }

    try {
      context.call(Duration.ofSeconds(5), () -> {
        assertEquals("user-1:role-1", workerIdentity.get());
        throw new IllegalStateException("tool failure");
      });
      fail("Expected the tool failure to be wrapped");
    } catch (ToolExecutionException expected) {
      assertEquals("The server tool execution failed.", expected.getMessage());
    }

    assertEquals(10, commits.get());
    assertEquals(1, rollbacks.get());
    assertEquals(11, cleanupCount.get());
    assertEquals(0, leakedWorkerState.get());
  }

  @Test
  public void timeoutInterruptsTheWorkerAndRollsBackEvenIfTheBodyReturns() throws Exception {
    AtomicInteger rollbacks = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    CountDownLatch cleaned = new CountDownLatch(2);
    ToolExecutionContext.Binding binding = new ToolExecutionContext.Binding() {
      @Override
      public void bind(ToolExecutionContext context) {
      }

      @Override
      public void finish(boolean success) {
        if (!success) {
          rollbacks.incrementAndGet();
        }
        cleaned.countDown();
      }
    };
    ToolExecutionContext context = new ToolExecutionContext("user-1", "role-1", "client-1",
        "org-1", null, "en_US", "agent-1", "conversation-1", false, new JSONObject(), binding);
    context.call(Duration.ofSeconds(5), () -> null);

    try {
      context.call(Duration.ofMillis(300), () -> {
        started.countDown();
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          interrupted.countDown();
          return "returned after cancellation";
        }
        return "completed";
      });
      fail("Expected a timeout");
    } catch (ToolExecutionException expected) {
      assertEquals("The server tool exceeded its execution timeout.", expected.getMessage());
    }

    assertTrue(started.await(2, TimeUnit.SECONDS));
    assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    assertTrue(cleaned.await(2, TimeUnit.SECONDS));
    assertEquals(1, rollbacks.get());
  }
}
