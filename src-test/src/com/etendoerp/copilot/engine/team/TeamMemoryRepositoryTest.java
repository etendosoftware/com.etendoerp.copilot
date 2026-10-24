/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import com.etendoerp.copilot.engine.tool.ToolExecutionContext;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

public class TeamMemoryRepositoryTest {
  @Test
  public void persistsMemberHistoryWithinScopedConversationOnly() {
    TeamMemoryRepository repository = TeamMemoryRepository.DEFAULT;
    ToolExecutionContext firstUser = context("user-a", "client-a", "org-a", "team-a");
    ToolExecutionContext otherUser = context("user-b", "client-a", "org-a", "team-a");
    List<ChatMessage> history = List.of(UserMessage.from("previous conversation"));
    String conversation = "memory-test-" + System.nanoTime();

    assertEquals("previous conversation", text(repository.load(firstUser, conversation, "member-a", history)));
    repository.save(firstUser, conversation, "member-a", List.of(UserMessage.from("member answer")));

    assertEquals("member answer", text(repository.load(firstUser, conversation, "member-a", history)));
    assertNotEquals("member answer", text(repository.load(otherUser, conversation, "member-a", history)));
  }
  private String text(List<ChatMessage> messages) {
    return ((TextContent) ((UserMessage) messages.get(0)).contents().get(0)).text();
  }


  private ToolExecutionContext context(String user, String client, String org, String team) {
    ToolExecutionContext context = mock(ToolExecutionContext.class);
    when(context.userId()).thenReturn(user);
    when(context.clientId()).thenReturn(client);
    when(context.orgId()).thenReturn(org);
    when(context.agentId()).thenReturn(team);
    return context;
  }
}
