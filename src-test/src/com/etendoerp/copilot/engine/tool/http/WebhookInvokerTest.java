package com.etendoerp.copilot.engine.tool.http;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.etendoerp.webhookevents.data.DefinedWebhookParam;

public class WebhookInvokerTest {

  @Test
  public void requiredWebhookParametersFailWhenMissing() {
    DefinedWebhookParam required = parameter("Query", true);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> WebhookInvoker.collectParameters(List.of(required), Map.of()));
    assertEquals("Missing required webhook parameter: Query", error.getMessage());
  }

  @Test
  public void optionalMissingParametersKeepAllProvidedValues() {
    DefinedWebhookParam optional = parameter("Mode", false);
    Map<String, String> result = WebhookInvoker.collectParameters(List.of(optional),
        Map.of("unconfigured", "ignored"));
    assertEquals(Map.of("unconfigured", "ignored"), result);
  }

  @Test
  public void configuredValuesArePassedThroughWithoutMutation() {
    DefinedWebhookParam query = parameter("Query", true);
    Map<String, String> result = WebhookInvoker.collectParameters(List.of(query),
        Map.of("Query", "select 1"));
    assertEquals(Map.of("Query", "select 1"), result);
  }

  @Test
  public void invocationRequiresAUserContextBeforeAnyDalAccess() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> WebhookInvoker.invoke(null, "DBQueryExec", Map.of()));
    assertEquals("A user tool context is required to invoke a webhook.", error.getMessage());
  }

  private static DefinedWebhookParam parameter(String name, boolean required) {
    DefinedWebhookParam parameter = mock(DefinedWebhookParam.class);
    when(parameter.getName()).thenReturn(name);
    when(parameter.isRequired()).thenReturn(required);
    return parameter;
  }
}
