/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.etendoerp.copilot.engine.tool.ToolExecutionContext;

import dev.langchain4j.data.message.ChatMessage;

/** Bounded process-local member checkpoints scoped to user, tenant, team, conversation, and member. */
public final class TeamMemoryRepository {
  public static final TeamMemoryRepository DEFAULT = new TeamMemoryRepository();
  private static final int MAX_CONVERSATIONS = 64;
  private static final int MAX_MEMBERS_PER_CONVERSATION = 16;
  private static final int MAX_MESSAGES_PER_MEMBER = 40;
  private static final int MAX_CHARS_PER_MEMBER = 16 * 1024;

  private final LinkedHashMap<ConversationKey, LinkedHashMap<String, List<ChatMessage>>> memories =
      new LinkedHashMap<>(16, 0.75f, true);

  private TeamMemoryRepository() {
  }

  public synchronized List<ChatMessage> load(ToolExecutionContext context, String conversationId,
      String memberId, List<ChatMessage> history) {
    ConversationKey key = key(context, conversationId);
    if (key == null) return new ArrayList<>(history);
    Map<String, List<ChatMessage>> members = memories.get(key);
    List<ChatMessage> saved = members == null ? null : members.get(memberId);
    return new ArrayList<>(saved == null ? history : saved);
  }

  public synchronized void save(ToolExecutionContext context, String conversationId, String memberId,
      List<ChatMessage> messages) {
    ConversationKey key = key(context, conversationId);
    if (key == null || StringUtils.isBlank(memberId)) return;
    LinkedHashMap<String, List<ChatMessage>> members = memories.get(key);
    if (members == null) {
      members = new LinkedHashMap<>(16, 0.75f, true);
      memories.put(key, members);
      while (memories.size() > MAX_CONVERSATIONS) {
        memories.remove(memories.keySet().iterator().next());
      }
    }
    List<ChatMessage> snapshot = boundedSnapshot(messages);
    members.put(memberId, snapshot);
    while (members.size() > MAX_MEMBERS_PER_CONVERSATION) {
      members.remove(members.keySet().iterator().next());
    }
  }

  private List<ChatMessage> boundedSnapshot(List<ChatMessage> source) {
    ArrayList<ChatMessage> result = new ArrayList<>();
    int chars = 0;
    for (int i = source.size() - 1; i >= 0 && result.size() < MAX_MESSAGES_PER_MEMBER; i--) {
      ChatMessage message = source.get(i);
      int messageChars = String.valueOf(message).length();
      if (messageChars > MAX_CHARS_PER_MEMBER || chars + messageChars > MAX_CHARS_PER_MEMBER) continue;
      result.add(message);
      chars += messageChars;
    }
    Collections.reverse(result);
    return result;
  }

  private ConversationKey key(ToolExecutionContext context, String conversationId) {
    if (context == null || StringUtils.isAnyBlank(context.userId(), context.clientId(), context.orgId(),
        context.agentId(), conversationId)) return null;
    return new ConversationKey(context.userId(), context.clientId(), context.orgId(), context.agentId(),
        conversationId);
  }

  private static final class ConversationKey {
    private final String user;
    private final String client;
    private final String org;
    private final String team;
    private final String conversation;

    private ConversationKey(String user, String client, String org, String team, String conversation) {
      this.user = user;
      this.client = client;
      this.org = org;
      this.team = team;
      this.conversation = conversation;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(user, client, org, team, conversation);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof ConversationKey)) return false;
      ConversationKey key = (ConversationKey) other;
      return user.equals(key.user) && client.equals(key.client) && org.equals(key.org)
          && team.equals(key.team) && conversation.equals(key.conversation);
    }
  }
}
