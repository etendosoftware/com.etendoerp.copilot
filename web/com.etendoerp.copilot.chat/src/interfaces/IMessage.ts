export interface IMessage {
  message_id?: string;
  text: string;
  response?: string;
  sender: string;
  timestamp?: string;
  file?: any;
}
