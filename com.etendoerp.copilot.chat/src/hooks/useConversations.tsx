import { useState, useEffect, useMemo } from 'react';
import { IConversation, IMessage } from '../interfaces';
import { ILabels } from '../interfaces/ILabels';
import { RestUtils } from '../utils/environment';
import { References } from '../utils/references';
import { ROLE_USER, ROLE_BOT } from '../utils/constants';

export const useConversations = (selectedAppId: string | null, labels?: ILabels) => {
  const [conversations, setConversations] = useState<IConversation[]>([]);
  const [isLoadingConversations, setIsLoadingConversations] = useState<boolean>(false);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [generatingTitles, setGeneratingTitles] = useState<Set<string>>(new Set());
  const [conversationsWithUnreadMessages, setConversationsWithUnreadMessages] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState<string>('');

  const filteredConversations = useMemo(() => {
    if (!searchQuery.trim()) return conversations;
    const query = searchQuery.toLowerCase();
    return conversations.filter(conv =>
      (conv.title || '').toLowerCase().includes(query)
    );
  }, [conversations, searchQuery]);

  const loadConversations = async () => {
    if (!selectedAppId) return;

    setIsLoadingConversations(true);
    try {
      const response = await RestUtils.fetch(
        `${References.url.GET_CONVERSATIONS}?app_id=${selectedAppId}`,
        { method: References.method.GET }
      );

      if (response.ok) {
        const conversationsData: IConversation[] = await response.json();
        setConversations(conversationsData);

        const currentConversationLabel = labels?.ETCOP_CurrentConversation ?? 'Current conversation';
        const conversationsWithoutTitle = conversationsData.filter(conversation =>
          !conversation.title || conversation.title.trim() === '' || conversation.title === currentConversationLabel || conversation.title === 'Current conversation' || conversation.title === 'Conversación actual'
        );

        console.log(`🎯 Found ${conversationsWithoutTitle.length} conversations without title`);

        // Process in batches to avoid flooding the backend. Adjust batchSize and delay between batches as needed.
        const batchSize = 3;
        const batchDelayMs = 2000;

        // Process batches sequentially, generating titles concurrently within each batch
        for (let i = 0; i < conversationsWithoutTitle.length; i += batchSize) {
          const batch = conversationsWithoutTitle.slice(i, i + batchSize);
          console.log(`🎯 Processing batch ${Math.floor(i / batchSize) + 1} with ${batch.length} conversations`);

          // Immediately update the UI to show 'Generating...' for batch items
          setConversations(prev =>
            prev.map(conv =>
              batch.find(b => b.id === conv.id)
                ? { ...conv, title: 'Generating...' }
                : conv
            )
          );

          // Start generating titles in background without awaiting so UI is not blocked
          batch.forEach((conversation, index) => {
            console.log(`🎯 Scheduling background generation for conversation ${i + index + 1}/${conversationsWithoutTitle.length}:`, conversation.id);
            // fire and forget; generateTitleInBackground handles duplicate prevention
            generateTitleInBackground(conversation.id).catch(err => {
              console.error('❌ Error generating title for', conversation.id, err);
            });
          });

          // Wait before starting the next batch to avoid overloading the backend
          if (i + batchSize < conversationsWithoutTitle.length) {
            console.log(`⏳ Waiting ${batchDelayMs}ms before next batch`);
            await new Promise(resolve => setTimeout(resolve, batchDelayMs));
          }
        }
      } else {
        console.error('Failed to load conversations');
        setConversations([]);
      }
    } catch (error) {
      console.error('Error loading conversations:', error);
      setConversations([]);
    } finally {
      setIsLoadingConversations(false);
    }
  };

  const generateTitleInBackground = async (conversationId: string) => {
    console.log('🔥 Starting title generation for:', conversationId);
    console.log('🔥 Current generating titles:', Array.from(generatingTitles));
    console.log('🔥 Selected app ID:', selectedAppId);

    if (generatingTitles.has(conversationId)) {
      console.log('⚠️ Already generating for:', conversationId);
      return;
    }

    if (!selectedAppId) {
      console.log('❌ No selectedAppId available');
      return;
    }

    setGeneratingTitles(prev => new Set(prev).add(conversationId));

    setConversations(prev =>
      prev.map(conv =>
        conv.id === conversationId
          ? { ...conv, title: 'Generating...' }
          : conv
      )
    );

    try {
      const requestBody = {
        conversation_id: conversationId
      };

      const requestOptions = {
        method: References.method.POST,
        body: JSON.stringify(requestBody),
      };

      console.log('🌐 Making request to:', References.url.GENERATE_TITLE);
      console.log('📝 Request body:', requestBody);
      console.log('⚙️ Request options:', requestOptions);

      // Log the full URL that will be constructed
      const isDev = process.env.NODE_ENV !== "production";
      const baseUrl = isDev ? 'http://localhost:8080/etendo/copilot/' : '../../copilot/';
      const fullUrl = baseUrl + References.url.GENERATE_TITLE;
      console.log('🔗 Full URL:', fullUrl);

      const response = await RestUtils.fetch(
        References.url.GENERATE_TITLE,
        requestOptions
      );

      console.log('🔥 Response status:', response.status);
      console.log('🔥 Response ok:', response.ok);

      if (response.ok) {
        const result = await response.json();
        console.log('🔥 Generated title result:', result);

        const generatedTitle = result.title || 'Untitled Conversation';

        setConversations(prev =>
          prev.map(conv =>
            conv.id === conversationId
              ? { ...conv, title: generatedTitle }
              : conv
          )
        );
      } else {
        const errorText = await response.text();
        console.error('❌ Failed response:', errorText);
        // Fallback title
        setConversations(prev =>
          prev.map(conv =>
            conv.id === conversationId
              ? { ...conv, title: 'Untitled Conversation' }
              : conv
          )
        );
      }
    } catch (error) {
      console.error('❌ Network error:', error);
      // Fallback title
      setConversations(prev =>
        prev.map(conv =>
          conv.id === conversationId
            ? { ...conv, title: 'Untitled Conversation' }
            : conv
        )
      );
    } finally {
      setGeneratingTitles(prev => {
        const newSet = new Set(prev);
        newSet.delete(conversationId);
        return newSet;
      });
    }
  };

  const loadConversationMessages = async (conversationId: string): Promise<IMessage[]> => {
    try {
      const response = await RestUtils.fetch(
        `${References.url.GET_CONVERSATION_MESSAGES}?conversation_id=${conversationId}`,
        { method: References.method.GET }
      );

      if (response.ok) {
        const messagesData = await response.json();
        // Transform the messages from backend format to frontend format
        return messagesData.map((msg: any) => ({
          text: msg.content,
          sender: msg.role?.toLowerCase() === 'user' ? ROLE_USER : ROLE_BOT,
          timestamp: msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString(),
        }));
      } else {
        console.error('Failed to load conversation messages');
        return [];
      }
    } catch (error) {
      console.error('Error loading conversation messages:', error);
      return [];
    }
  };

  const addNewConversationToList = (conversationId: string) => {
    console.log('➕ Adding new conversation to list:', conversationId);
    setConversations(prev => {
      // Check if conversation already exists
      const exists = prev.find(conv => conv.id === conversationId);
      if (!exists) {
        console.log('✅ Conversation added to list, total conversations:', prev.length + 1);
        return [{
          id: conversationId,
          title: labels?.ETCOP_CurrentConversation ?? 'Current conversation',
          created_at: new Date().toISOString()
        }, ...prev];
      }
      console.log('⚠️ Conversation already exists in list');
      return prev;
    });
  };

  const selectConversation = (conversationId: string) => {
    console.log('🎯 Selecting conversation:', conversationId);

    if (currentConversationId && currentConversationId !== conversationId) {
      const previousConversation = conversations.find(conv => conv.id === currentConversationId);
      const currentConversationLabel = labels?.ETCOP_CurrentConversation ?? 'Current conversation';
      if (previousConversation && (!previousConversation.title || previousConversation.title === 'Conversación actual' || previousConversation.title === 'Current conversation' || previousConversation.title === currentConversationLabel)) {
        console.log('🎯 Generating title for previous conversation:', currentConversationId);
        generateTitleInBackground(currentConversationId);
      }
    }

    setCurrentConversationId(conversationId);
  };

  const clearCurrentConversation = () => {
    setCurrentConversationId(null);
  };

  const markConversationAsUnread = (conversationId: string) => {
    setConversationsWithUnreadMessages(prev => new Set(prev).add(conversationId));
  };

  const markConversationAsRead = (conversationId: string) => {
    setConversationsWithUnreadMessages(prev => {
      const newSet = new Set(prev);
      newSet.delete(conversationId);
      return newSet;
    });
  };

  const renameConversation = async (conversationId: string, newTitle: string) => {
    const previousConversations = [...conversations];
    setConversations(prev =>
      prev.map(conv =>
        conv.id === conversationId ? { ...conv, title: newTitle } : conv
      )
    );

    try {
      const response = await RestUtils.fetch(
        References.url.RENAME_CONVERSATION,
        {
          method: References.method.POST,
          body: JSON.stringify({ conversation_id: conversationId, title: newTitle }),
        }
      );

      if (!response.ok) {
        console.error('Failed to rename conversation');
        setConversations(previousConversations);
      }
    } catch (error) {
      console.error('Error renaming conversation:', error);
      setConversations(previousConversations);
    }
  };

  const deleteConversation = async (conversationId: string): Promise<IConversation | null> => {
    const conversationToDelete = conversations.find(conv => conv.id === conversationId);
    const previousConversations = [...conversations];

    setConversations(prev => prev.filter(conv => conv.id !== conversationId));

    if (currentConversationId === conversationId) {
      setCurrentConversationId(null);
    }

    try {
      const response = await RestUtils.fetch(
        References.url.DELETE_CONVERSATION,
        {
          method: References.method.POST,
          body: JSON.stringify({ conversation_id: conversationId }),
        }
      );

      if (!response.ok) {
        console.error('Failed to delete conversation');
        setConversations(previousConversations);
        return null;
      }

      return conversationToDelete || null;
    } catch (error) {
      console.error('Error deleting conversation:', error);
      setConversations(previousConversations);
      return null;
    }
  };

  const addConversationToList = (conversation: IConversation) => {
    setConversations(prev => {
      const exists = prev.find(conv => conv.id === conversation.id);
      if (!exists) {
        return [conversation, ...prev];
      }
      return prev;
    });
  };

  // When selecting a conversation, mark it as read
  const selectConversationAndMarkAsRead = (conversationId: string) => {
    selectConversation(conversationId);
    markConversationAsRead(conversationId);
  };

  useEffect(() => {
    loadConversations();
  }, [selectedAppId]);

  return {
    conversations,
    filteredConversations,
    isLoadingConversations,
    currentConversationId,
    loadConversations,
    loadConversationMessages,
    selectConversation,
    clearCurrentConversation,
    generateTitleInBackground,
    generatingTitles,
    addNewConversationToList,
    conversationsWithUnreadMessages,
    markConversationAsUnread,
    markConversationAsRead,
    selectConversationAndMarkAsRead,
    renameConversation,
    deleteConversation,
    addConversationToList,
    searchQuery,
    setSearchQuery,
  };
};
