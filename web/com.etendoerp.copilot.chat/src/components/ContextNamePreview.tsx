import { IconInfo } from "etendo-ui-library";

interface ContextTitlePreviewProps {
  contextTitle: string;
  hasFile?: boolean | null;
}

const ContextTitlePreview = ({ contextTitle, hasFile }: ContextTitlePreviewProps) => {
  return (
    <div
      className={`px-2 w-full bg-gray-100 h-[56px] flex border border-stone-400 rounded-lg items-center ${hasFile ? "mb-16" : "mb-2"}`}
    >
      <IconInfo />
      <p className="ml-2 font-medium text-blue-950 text-sm">{contextTitle}</p>
    </div>
  );
};

export default ContextTitlePreview;
