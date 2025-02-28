import { IconInfo } from "etendo-ui-library";

interface ContextTitlePreviewProps {
  contextTitle: string;
}

const ContextTitlePreview = ({ contextTitle }: ContextTitlePreviewProps) => {
  return (
    <div
      className="mb-2 px-2 w-full bg-gray-100 h-[56px] flex border border-stone-400 rounded-lg items-center"
    >
      <IconInfo />
      <p className="ml-2 font-medium text-blue-950 text-sm">{contextTitle}</p>
    </div>
  )
};

export default ContextTitlePreview;
