import React, { useState, useRef, useEffect, useCallback } from 'react';
import filterIcon from '../assets/filter.svg';

interface IAssistantOption {
  name: string;
  app_id: string;
  [key: string]: any;
}

interface AssistantDropdownProps {
  selectedOption: IAssistantOption | null;
  assistants: IAssistantOption[];
  onSelect: (option: IAssistantOption) => void;
  showOnlyFeatured?: boolean;
  hasFeaturedAssistants?: boolean;
  onToggleFeaturedFilter?: () => void;
  searchPlaceholder?: string;
}

const AssistantDropdown = ({
  selectedOption,
  assistants,
  onSelect,
  showOnlyFeatured,
  hasFeaturedAssistants,
  onToggleFeaturedFilter,
  searchPlaceholder = 'Filter Profiles...',
}: AssistantDropdownProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [filterText, setFilterText] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const filteredOptions = assistants.filter(opt =>
    opt.name.toLowerCase().includes(filterText.toLowerCase())
  );

  // Close on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setFilterText('');
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Focus search input when dropdown opens
  useEffect(() => {
    if (isOpen && searchRef.current) {
      setTimeout(() => searchRef.current?.focus(), 50);
    }
  }, [isOpen]);

  const handleSelect = useCallback((option: IAssistantOption) => {
    onSelect(option);
    setIsOpen(false);
    setFilterText('');
  }, [onSelect]);

  return (
    <div ref={containerRef} style={{ display: 'flex', alignItems: 'center', gap: 4, position: 'relative' }}>
      {/* Trigger button — mimics InputBase style */}
      <button
        onClick={() => setIsOpen(prev => !prev)}
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FAFAFA',
          border: '1px solid #202452',
          borderRadius: 8,
          padding: '0 12px',
          height: 40,
          cursor: 'pointer',
          overflow: 'hidden',
          minWidth: 0,
        }}
      >
        <span style={{
          fontSize: 14,
          fontWeight: 500,
          color: '#202452',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          flex: 1,
          textAlign: 'left',
        }}>
          {selectedOption?.name ?? ''}
        </span>
        <svg
          width="16" height="16" viewBox="0 0 24 24" fill="none"
          style={{ flexShrink: 0, marginLeft: 4, color: '#202452', transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
          xmlns="http://www.w3.org/2000/svg"
        >
          <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {/* Filter toggle button */}
      {hasFeaturedAssistants && onToggleFeaturedFilter && (
        <button
          onClick={onToggleFeaturedFilter}
          title="Toggle featured filter"
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: 4,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <img
            src={filterIcon}
            alt="Toggle featured filter"
            style={{
              width: 16,
              height: 16,
              filter: showOnlyFeatured
                ? 'invert(13%) sepia(47%) saturate(1200%) hue-rotate(207deg) brightness(85%) contrast(110%)'
                : 'invert(67%) sepia(0%) saturate(0%) hue-rotate(0deg) brightness(90%) contrast(90%)',
            }}
          />
        </button>
      )}

      {/* Dropdown panel */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: hasFeaturedAssistants && onToggleFeaturedFilter ? 28 : 0,
            marginTop: 2,
            backgroundColor: '#FAFAFA',
            border: '1px solid #e5e7eb',
            borderRadius: 8,
            boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
            zIndex: 9999,
            overflow: 'hidden',
          }}
        >
          {/* Search input */}
          <div style={{ padding: '8px 8px 4px' }}>
            <input
              ref={searchRef}
              type="text"
              value={filterText}
              onChange={e => setFilterText(e.target.value)}
              placeholder={searchPlaceholder}
              style={{
                width: '100%',
                boxSizing: 'border-box',
                padding: '6px 10px',
                border: '1px solid #e5e7eb',
                borderRadius: 6,
                fontSize: 13,
                color: '#202452',
                backgroundColor: '#fff',
                outline: 'none',
              }}
            />
          </div>

          {/* Options list */}
          <ul style={{ listStyle: 'none', margin: 0, padding: '4px 0', maxHeight: 200, overflowY: 'auto' }}>
            {filteredOptions.length === 0 ? (
              <li style={{ padding: '8px 12px', fontSize: 13, color: '#9E9E9E' }}>No results</li>
            ) : (
              filteredOptions.map(opt => (
                <li
                  key={opt.app_id}
                  onClick={() => handleSelect(opt)}
                  style={{
                    padding: '8px 12px',
                    fontSize: 14,
                    fontWeight: opt.app_id === selectedOption?.app_id ? 600 : 400,
                    color: '#202452',
                    backgroundColor: opt.app_id === selectedOption?.app_id ? '#e8eaf6' : 'transparent',
                    cursor: 'pointer',
                  }}
                  onMouseEnter={e => {
                    if (opt.app_id !== selectedOption?.app_id) {
                      (e.currentTarget as HTMLElement).style.backgroundColor = '#f3f4f6';
                    }
                  }}
                  onMouseLeave={e => {
                    if (opt.app_id !== selectedOption?.app_id) {
                      (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                    }
                  }}
                >
                  {opt.name}
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
};

export default AssistantDropdown;
