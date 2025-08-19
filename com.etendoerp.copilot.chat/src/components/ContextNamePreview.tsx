import React from 'react';
import { IconInfo, XIcon } from "etendo-ui-library";

interface ContextTitlePreviewProps {
  contextTitle: string;
  hasFile?: boolean | null;
  onClearContext?: () => void;
}

const ContextTitlePreview: React.FC<ContextTitlePreviewProps> = ({
  contextTitle,
  hasFile,
  onClearContext,
}) => {
  return (
    <div
      className={`px-2 w-full bg-gray-100 h-[56px] flex border border-stone-400 rounded-lg items-center justify-between ${hasFile ? "mb-16" : "mb-2"
        }`}
    >
      <div className="flex items-center">
        <IconInfo />
        <p className="ml-2 font-medium text-blue-950 text-sm">{contextTitle}</p>
      </div>
      {onClearContext && (
        <button
          onClick={onClearContext}
          title="Cancelar contexto"
          className="p-2"
        >
          <XIcon />
        </button>
      )}
    </div>
  );
};

export default ContextTitlePreview;
