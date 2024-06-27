// Define example messages
export const EXAMPLE_CONVERSATIONS = [
  { conversation: 'Explain how the finance module works in simple terms' },
  { conversation: 'How do I create a sales order?' },
  { conversation: 'I need to create a stylesheet' },
];

// Tools Models
export const MODEL_OPTIONS = [
  'XML Translation Tool',
  'Invoice Generator',
  'Window Creation',
];

// Supported files
export const SUPPORTED_MIME_TYPES = [
  'text/x-c',
  'text/x-c++',
  'application/csv',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/html',
  'text/x-java',
  'application/json',
  'text/markdown',
  'application/pdf',
  'text/x-php',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/x-python',
  'text/x-script.python',
  'text/x-ruby',
  'text/x-tex',
  'text/plain',
  'text/css',
  'image/jpeg',
  'image/jpeg',
  'text/javascript',
  'image/gif',
  'image/png',
  'application/x-tar',
  'application/typescript',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/xml',
  'text/xml',
  'application/zip',
];

// Loading messages while Copilot searches for the correct answer
export const LOADING_MESSAGES = [
  'Processing...',
  'Analyzing request...',
  'Formulating response...',
  'Compiling data...',
  'Synthesizing information...',
  'Evaluating options...',
  'Executing algorithms...',
  'Optimizing performance...',
  'Verifying results...',
];

// Constant to show the assistants
export const IS_SHOW_ASSISTANTS = {
  yes: 'Y',
  no: 'N',
};

// Colors
export const PRIMARY_100 = '#202452';
export const DANGER_900 = '#74122E';

export const ROLE_ASSISTANT = 'assistant';
export const ROLE_USER = 'user';
export const ROLE_TOOL = 'tool';
export const ROLE_WAIT = 'wait';
export const ROLE_NODE = 'node';
export const ROLE_BOT = 'bot';
export const ROLE_ERROR = 'error';
