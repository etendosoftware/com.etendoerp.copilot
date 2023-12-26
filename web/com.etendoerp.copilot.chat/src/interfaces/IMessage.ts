export interface IMessage {
    text: string;
    sender: string;
    timestamp?: string;
    hasFile?: boolean;
    fileName?: string;
  }
  