import { useEffect, useRef, useState } from 'react';
import { IConversation } from '../interfaces';
import { RestUtils } from '../utils/environment';
import { References } from '../utils/references';

export const useArchivedConversations = (selectedAppId: string | null) => {
  const [archivedConversations, setArchivedConversations] = useState<IConversation[]>([]);
  const [isExpanded, setIsExpanded] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [hasLoaded, setHasLoaded] = useState<boolean>(false);
  const activeAppIdRef = useRef<string | null>(selectedAppId);

  useEffect(() => {
    activeAppIdRef.current = selectedAppId;
    setArchivedConversations([]);
    setIsExpanded(false);
    setIsLoading(false);
    setHasLoaded(false);
  }, [selectedAppId]);

  const loadArchivedConversations = async (appId: string) => {
    if (!appId) return;
    setIsLoading(true);
    try {
      const response = await RestUtils.fetch(
        `${References.url.GET_ARCHIVED_CONVERSATIONS}?app_id=${appId}`,
        { method: References.method.GET }
      );

      if (response.ok) {
        const data: IConversation[] = await response.json();
        if (activeAppIdRef.current === appId) {
          setArchivedConversations(data);
        }
      } else {
        console.error('Failed to load archived conversations');
        if (activeAppIdRef.current === appId) {
          setArchivedConversations([]);
        }
      }
    } catch (error) {
      console.error('Error loading archived conversations:', error);
      if (activeAppIdRef.current === appId) {
        setArchivedConversations([]);
      }
    } finally {
      if (activeAppIdRef.current === appId) {
        setIsLoading(false);
        setHasLoaded(true);
      }
    }
  };

  const toggleExpanded = (appId: string) => {
    const newExpanded = !isExpanded;
    setIsExpanded(newExpanded);
    if (newExpanded && !hasLoaded) {
      loadArchivedConversations(appId);
    }
  };

  const restoreConversation = async (conversationId: string): Promise<IConversation | null> => {
    const conversationToRestore = archivedConversations.find(conv => conv.id === conversationId);

    setArchivedConversations(prev => prev.filter(conv => conv.id !== conversationId));

    try {
      const response = await RestUtils.fetch(
        References.url.RESTORE_CONVERSATION,
        {
          method: References.method.POST,
          body: JSON.stringify({ conversation_id: conversationId }),
        }
      );

      if (!response.ok) {
        console.error('Failed to restore conversation');
        if (conversationToRestore) {
          setArchivedConversations(prev => [...prev, conversationToRestore]);
        }
        return null;
      }

      return conversationToRestore || null;
    } catch (error) {
      console.error('Error restoring conversation:', error);
      if (conversationToRestore) {
        setArchivedConversations(prev => [...prev, conversationToRestore]);
      }
      return null;
    }
  };

  const permanentDeleteConversation = async (conversationId: string) => {
    const previousArchived = [...archivedConversations];

    setArchivedConversations(prev => prev.filter(conv => conv.id !== conversationId));

    try {
      const response = await RestUtils.fetch(
        References.url.PERMANENT_DELETE_CONVERSATION,
        {
          method: References.method.POST,
          body: JSON.stringify({ conversation_id: conversationId }),
        }
      );

      if (!response.ok) {
        console.error('Failed to permanently delete conversation');
        setArchivedConversations(previousArchived);
      }
    } catch (error) {
      console.error('Error permanently deleting conversation:', error);
      setArchivedConversations(previousArchived);
    }
  };

  const addToArchive = (conversation: IConversation) => {
    if (isExpanded) {
      setArchivedConversations(prev => [conversation, ...prev]);
    }
  };

  return {
    archivedConversations,
    isExpanded,
    isLoading,
    toggleExpanded,
    restoreConversation,
    permanentDeleteConversation,
    addToArchive,
    loadArchivedConversations,
  };
};
