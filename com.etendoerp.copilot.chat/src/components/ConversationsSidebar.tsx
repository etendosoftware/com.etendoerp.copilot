import React, { useState, useRef, useEffect } from 'react';
import { IConversation } from '../interfaces';
import { ILabels } from '../interfaces/ILabels';
import { Button } from 'etendo-ui-library/dist-web/components';


interface ConversationsSidebarProps {
  conversations: IConversation[];
  isLoading: boolean;
  currentConversationId: string | null;
  onConversationSelect: (conversationId: string) => void;
  onNewConversation: () => void;
  isVisible: boolean;
  generatingTitles?: Set<string>;
  conversationsWithUnreadMessages?: Set<string>;
  labels?: ILabels;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onRename: (conversationId: string, newTitle: string) => void;
  onDelete: (conversationId: string) => void;
  archivedConversations: IConversation[];
  isArchiveExpanded: boolean;
  isArchiveLoading?: boolean;
  onToggleArchive: () => void;
  onRestore: (conversationId: string) => void;
  onPermanentDelete: (conversationId: string) => void;
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
  labels,
  searchQuery,
  onSearchChange,
  onRename,
  onDelete,
  archivedConversations,
  isArchiveExpanded,
  isArchiveLoading,
  onToggleArchive,
  onRestore,
  onPermanentDelete,
}) => {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState<string>('');
  const editInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editingId && editInputRef.current) {
      editInputRef.current.focus();
      editInputRef.current.select();
    }
  }, [editingId]);

  if (!isVisible) return null;

  const startEditing = (conversation: IConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(conversation.id);
    setEditValue(conversation.title || '');
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditValue('');
  };

  const saveEditing = () => {
    if (editingId && editValue.trim()) {
      onRename(editingId, editValue.trim());
    }
    cancelEditing();
  };

  const handleEditKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      saveEditing();
    } else if (e.key === 'Escape') {
      cancelEditing();
    }
  };

  const handleDelete = (conversationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete(conversationId);
  };

  const handleRestore = (conversationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    onRestore(conversationId);
  };

  const handlePermanentDelete = (conversationId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    onPermanentDelete(conversationId);
  };

  const getConversationTitle = (conversation: IConversation) => {
    const hasUnreadMessages = conversationsWithUnreadMessages?.has(conversation.id);
    const fontWeight = hasUnreadMessages ? 'font-bold' : '';

    if (generatingTitles?.has(conversation.id)) {
      return <span className={`text-black italic ${fontWeight}`}>{labels?.ETCOP_GeneratingTitle ?? 'Generating...'}</span>;
    }

    if (conversation.title && conversation.title.trim() !== '') {
      const normalized = conversation.title.trim().toLowerCase();
      const currentConversationLabel = labels?.ETCOP_CurrentConversation ?? 'Current conversation';
      const tempTitles = [
        'current conversation',
        'conversación actual',
        currentConversationLabel.trim().toLowerCase()
      ];
      const isTemp = tempTitles.includes(normalized);

      if (isTemp) {
        return (
          <span className={`italic text-gray-600 ${fontWeight}`}>
            {conversation.title}
          </span>
        );
      }

      return <span className={fontWeight}>{conversation.title}</span>;
    }

    return <span className={`text-gray-400 italic ${fontWeight}`}>{labels?.ETCOP_Untitled ?? 'Untitled'}</span>;
  };

  const getTooltipTitle = (conversation: IConversation) => {
    if (generatingTitles?.has(conversation.id)) {
      return labels?.ETCOP_GeneratingTitle ?? 'Generating title...';
    }

    if (conversation.title && conversation.title.trim() !== '') {
      return conversation.title;
    }

    return labels?.ETCOP_Untitled ?? 'Untitled.';
  };

  return (
    <div className="flex-1 flex flex-col bg-gray-50 h-full">
      {/* Header */}
      <div className="flex-shrink-0 p-3 border-b border-gray-300">
        <Button
          fontSize={16}
          onPress={() => onNewConversation()}
          paddingHorizontal={12}
          paddingVertical={12}
          text={labels?.ETCOP_NewConversation ?? 'New conversation'}
          typeStyle="primary"
        />
      </div>

      {/* Search Bar */}
      <div className="flex-shrink-0 p-2 border-b border-gray-200">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={labels?.ETCOP_SearchConversations ?? 'Search conversations...'}
          className="w-full px-3 py-2 text-sm border border-gray-300 rounded focus:outline-none focus:border-blue-500"
        />
      </div>

      {/* Conversations List */}
      <div className="flex-1 overflow-y-auto min-h-0">
        {isLoading && (
          <div className="p-3 text-gray-500 text-sm">{labels?.ETCOP_LoadingConversations ?? 'Loading conversations...'}</div>
        )}
        {!isLoading && conversations.length === 0 && (
          <div className="p-3 text-gray-500 text-sm">{labels?.ETCOP_NoConversations ?? 'No conversations'}</div>
        )}
        {!isLoading && conversations.length > 0 && (
          conversations.map((conversation) => (
            <div
              key={conversation.id}
              className={`group relative w-full text-left p-3 border-b border-gray-200 hover:bg-gray-100 transition-colors cursor-pointer ${
                currentConversationId === conversation.id ? 'bg-blue-100 border-l-4 border-l-blue-900' : ''
              }`}
              onClick={() => {
                if (editingId !== conversation.id) {
                  onConversationSelect(conversation.id);
                }
              }}
              title={getTooltipTitle(conversation)}
            >
              <div className="flex items-center">
                <div className="flex-1 text-sm font-medium text-gray-900 truncate min-w-0">
                  {editingId === conversation.id ? (
                    <input
                      ref={editInputRef}
                      type="text"
                      value={editValue}
                      onChange={(e) => setEditValue(e.target.value)}
                      onKeyDown={handleEditKeyDown}
                      onBlur={saveEditing}
                      className="w-full px-1 py-0 text-sm border border-blue-500 rounded focus:outline-none"
                      onClick={(e) => e.stopPropagation()}
                    />
                  ) : (
                    getConversationTitle(conversation)
                  )}
                </div>
                {editingId !== conversation.id && !generatingTitles?.has(conversation.id) && (
                  <div className="hidden group-hover:flex items-center gap-1 ml-2 flex-shrink-0">
                    <button
                      onClick={(e) => startEditing(conversation, e)}
                      title={labels?.ETCOP_Rename ?? 'Rename'}
                      className="p-1 text-gray-800 bg-gray-50 border border-gray-200 hover:text-blue-600 hover:bg-blue-50 hover:border-blue-200 rounded transition-colors"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
                        <path d="m15 5 4 4"/>
                      </svg>
                    </button>
                    <button
                      onClick={(e) => handleDelete(conversation.id, e)}
                      title={labels?.ETCOP_Delete ?? 'Delete'}
                      className="p-1 text-gray-800 bg-gray-50 border border-gray-200 hover:text-red-600 hover:bg-red-50 hover:border-red-200 rounded transition-colors"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M3 6h18"/>
                        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                      </svg>
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Archived Section */}
      <div className="flex-shrink-0 border-t border-gray-300">
        <button
          onClick={onToggleArchive}
          className="w-full flex items-center justify-between p-3 text-sm text-gray-600 hover:bg-gray-100 transition-colors"
        >
          <span>
            {labels?.ETCOP_Archived ?? 'Archived'}
            {archivedConversations.length > 0 && ` (${archivedConversations.length})`}
          </span>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`transition-transform ${isArchiveExpanded ? 'rotate-180' : ''}`}
          >
            <path d="m6 9 6 6 6-6"/>
          </svg>
        </button>

        {isArchiveExpanded && (
          <div className="max-h-48 overflow-y-auto">
            {isArchiveLoading && (
              <div className="p-3 text-gray-500 text-sm">Loading...</div>
            )}
            {!isArchiveLoading && archivedConversations.length === 0 && (
              <div className="p-3 text-gray-400 text-sm italic">No archived conversations</div>
            )}
            {!isArchiveLoading && archivedConversations.map((conversation) => (
              <div
                key={conversation.id}
                className="group relative w-full text-left p-3 border-b border-gray-200 bg-gray-100"
                title={conversation.title || 'Untitled'}
              >
                <div className="flex items-center">
                  <div className="flex-1 text-sm text-gray-500 truncate min-w-0">
                    {conversation.title || <span className="italic">{labels?.ETCOP_Untitled ?? 'Untitled'}</span>}
                  </div>
                  <div className="hidden group-hover:flex items-center gap-1 ml-2 flex-shrink-0">
                    <button
                      onClick={(e) => handleRestore(conversation.id, e)}
                      title={labels?.ETCOP_Restore ?? 'Restore'}
                      className="p-1 text-gray-800 bg-gray-50 border border-gray-200 hover:text-green-600 hover:bg-green-50 hover:border-green-200 rounded transition-colors"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
                        <path d="M3 3v5h5"/>
                      </svg>
                    </button>
                    <button
                      onClick={(e) => handlePermanentDelete(conversation.id, e)}
                      title={labels?.ETCOP_PermanentDelete ?? 'Delete permanently'}
                      className="p-1 text-gray-800 bg-gray-50 border border-gray-200 hover:text-red-600 hover:bg-red-50 hover:border-red-200 rounded transition-colors"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M3 6h18"/>
                        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                        <line x1="10" y1="11" x2="10" y2="17"/>
                        <line x1="14" y1="11" x2="14" y2="17"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ConversationsSidebar;
