import React from 'react';
import { IConversation } from '../interfaces';

interface ConversationsSidebarProps {
  conversations: IConversation[];
  isLoading: boolean;
  currentConversationId: string | null;
  onConversationSelect: (conversationId: string) => void;
  onNewConversation: () => void;
  isVisible: boolean;
  generatingTitles?: Set<string>;
  conversationsWithUnreadMessages?: Set<string>;
}

const ConversationsSidebar: React.FC<ConversationsSidebarProps> = ({
  conversations,
  isLoading,
  currentConversationId,
  onConversationSelect,
  onNewConversation,
  isVisible,
  generatingTitles,
  conversationsWithUnreadMessages,
}) => {
  if (!isVisible) return null;

  const getConversationTitle = (conversation: IConversation) => {
    const hasUnreadMessages = conversationsWithUnreadMessages?.has(conversation.id);
    const fontWeight = hasUnreadMessages ? 'font-bold' : '';

    if (generatingTitles?.has(conversation.id)) {
      return <span className={`text-black italic ${fontWeight}`}>Generando...</span>;
    }

    if (conversation.title && conversation.title.trim() !== '') {
      return <span className={fontWeight}>{conversation.title}</span>;
    }

    return <span className={`text-gray-400 italic ${fontWeight}`}>Sin título</span>;
  };

  const getTooltipTitle = (conversation: IConversation) => {
    if (generatingTitles?.has(conversation.id)) {
      return "Generating title...";
    }

    if (conversation.title && conversation.title.trim() !== '') {
      return conversation.title;
    }

    return "Untitled.";
  };

  return (
    <div className="flex-1 flex flex-col bg-gray-50 h-full">
      {/* Header */}
      <div className="flex-shrink-0 p-3 border-b border-gray-300">
        <button
          onClick={onNewConversation}
          className="w-full px-3 py-2 bg-blue-600 text-white rounded-md text-sm hover:bg-blue-700 transition-colors"
        >
          Nueva conversación
        </button>
      </div>

      {/* Conversations List */}
      <div className="flex-1 overflow-y-auto min-h-0">
        {isLoading && (
          <div className="p-3 text-gray-500 text-sm">Cargando conversaciones...</div>
        )}
        {!isLoading && conversations.length === 0 && (
          <div className="p-3 text-gray-500 text-sm">No hay conversaciones</div>
        )}
        {!isLoading && conversations.length > 0 && (
          conversations.map((conversation) => (
            <button
              key={conversation.id}
              onClick={() => onConversationSelect(conversation.id)}
              onKeyDown={(e) => e.key === 'Enter' && onConversationSelect(conversation.id)}
              title={getTooltipTitle(conversation)}
              className={`w-full text-left p-3 border-b border-gray-200 hover:bg-gray-100 transition-colors ${
                currentConversationId === conversation.id ? 'bg-blue-100 border-l-4 border-l-blue-600' : ''
              }`}
            >
              <div className="text-sm font-medium text-gray-900 truncate">
                {getConversationTitle(conversation)}
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  );
};

export default ConversationsSidebar;
