// Define TypeScript types for message structure
export interface IMessage {
  text: string;
  sender: string;
  timestamp?: string;
  hasFile?: boolean;
  fileName?: string;
}
