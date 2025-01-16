import { useState, useEffect, useRef } from 'react';
import ContextTitlePreview from './components/context-name-preview';
import TextMessage from 'etendo-ui-library/dist-web/components/text-message/TextMessage';
import FileSearchInput from 'etendo-ui-library/dist-web/components/inputBase/file-search-input/FileSearchInput';
import { useAssistants } from './hooks/useAssistants';
import { formatTimeNewDate, getMessageType } from './utils/functions';
import botIcon from './assets/bot.svg';
import responseSent from './assets/response-sent.svg';
import { ILabels } from './interfaces';
import { IMessage } from './interfaces/IMessage';
import { References } from './utils/references';
import './App.css';
import { DropdownInput } from 'etendo-ui-library/dist-web/components';
import { SparksIcon } from 'etendo-ui-library/dist-web/assets/images/icons';
import { RestUtils, isDevelopment } from './utils/environment';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { ROLE_BOT, ROLE_ERROR, ROLE_NODE, ROLE_TOOL, ROLE_USER, ROLE_WAIT } from './utils/constants';
import { getMessageContainerClasses } from './utils/styles';

function App() {
  const search = window.location.search;
  const params = new URLSearchParams(search);
  const contextValue = params.get("context_value");

  // States
  const [file, setFile] = useState<any>(null);
  const [labels, setLabels] = useState<ILabels>({});
  const [statusIcon, setStatusIcon] = useState(botIcon);
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [isFirstMessage, setIsFirstMessage] = useState(true);
  const [fileId, setFileId] = useState<string[] | null>(null);
  const [isBotLoading, setIsBotLoading] = useState<boolean>(false);
  const [files, setFiles] = useState<(string | File)[] | null>(null);
  const [areLabelsLoaded, setAreLabelsLoaded] = useState<boolean>(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState<string>(params.get("question") ?? '');
  const [contextTitle, setContextTitle] = useState<string | null>(params.get("context_title"));

  // Hooks
  const { selectedOption, assistants, getAssistants, handleOptionSelected } =
    useAssistants();

  // Effect to handle the assistant_id parameter
  useEffect(() => {
    const assistant_id = params.get("assistant_id");
    if (assistant_id && assistants.length > 0) {
      const assistant = assistants.find(assistant => assistant.app_id === assistant_id);
      if (assistant) {
        handleOptionSelected(assistant);
      }
    }
  }, [assistants, params]);

  // Constants
  const noAssistants = assistants?.length === 0 ? true : false;

  // References
  const messagesEndRef = useRef<any>(null);
  const inputRef = useRef<any>(null);

  const handleNewMessage = async (role: string, message: IMessage) => {
    let _text = message?.response ?? message.text;
    if (role === ROLE_WAIT) {
      _text = '⏳ ' + _text + '';
    }
    if (role === ROLE_TOOL) {
      _text = '🛠️ ' + _text + '';
    }
    if (role === ROLE_NODE) {
      _text = '🤖 ' + _text + '';
    }

    setMessages((prevMessages: any) => {
      // Get files or context title
      let fileNames: { name: string }[] = [];
      if (files && files.length > 0) {
        fileNames = files.map((file: any) => ({ name: file.name }));
      } else if (contextTitle) {
        fileNames = [{ name: contextTitle }];
      }

      // Replace the last message if the role is the same
      const lastMessage = prevMessages[prevMessages.length - 1];
      if (
        lastMessage &&
        (lastMessage.sender === ROLE_TOOL || lastMessage.sender === ROLE_NODE || lastMessage.sender === ROLE_WAIT) &&
        (role === lastMessage.sender || role === ROLE_BOT || lastMessage.sender === ROLE_WAIT)
      ) {
        // Replace the last message if the role is the same
        return [
          ...prevMessages.slice(0, -1),
          {
            message_id: message.message_id,
            text: _text,
            sender: role,
            timestamp: formatTimeNewDate(new Date()),
            files: fileNames,
          },
        ];
      } else {
        // Add a new message if the role is different
        return [
          ...prevMessages,
          {
            message_id: message.message_id,
            text: _text,
            sender: role,
            timestamp: formatTimeNewDate(new Date()),
            files: fileNames,
          },
        ];
      }
    });
    setContextTitle(null);
    if (role === ROLE_USER) {
      await handleNewMessage(ROLE_WAIT, {
        text: 'Processing...',
        sender: ROLE_WAIT,
        timestamp: formatTimeNewDate(new Date()),
      });
    }
    scrollToBottom();
  };

  // Function to scroll bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  // Fetch labels data
  const getLabels = async () => {
    const requestOptions = {
      method: 'GET',
    };
    const response = await RestUtils.fetch(
      References.url.GET_LABELS,
      requestOptions,
    );
    const data = await response.json();
    if (data) {
      setLabels(data);
      setAreLabelsLoaded(true);
    }
  };

  // Function to handle sending a message
  const handleSendMessage = async () => {
    setIsBotLoading(true);
    setFile(null);
    setFileId(null);

    if (!isBotLoading) {
      const question = inputValue.trim();
      setInputValue('');
      if (!question) return;

      // Add user message
      const userMessage: IMessage = {
        text: question,
        sender: ROLE_USER,
        timestamp: formatTimeNewDate(new Date()),
      };
      if (file) {
        userMessage.file = file.name;
      }
      await handleNewMessage(ROLE_USER, userMessage);
      setStatusIcon(botIcon);
      setTimeout(() => scrollToBottom(), 100);

      // Prepare request body
      const requestBody: any = {
        question: inputValue,
        app_id: selectedOption?.app_id,
      };

      if (isFirstMessage) {
        setIsFirstMessage(false);
        if (contextValue) {
          requestBody.context_value = contextValue;
        }
      }

      if (conversationId) {
        requestBody.conversation_id = conversationId;
      }
      if (fileId) {
        requestBody.file = fileId;
      }

      if (encodeURIComponent(inputValue).length > 7000) {
        const cacheQuestionBody = {
          question: inputValue,
        };
        const cacheQuestionRequest = {
          method: References.method.POST,
          body: JSON.stringify(cacheQuestionBody),
          headers: { 'Content-Type': 'application/json' },
        };
        const cacheQuestionResponse = await RestUtils.fetch(
          `${References.url.CACHE_QUESTION}`,
          cacheQuestionRequest,
        );
        const cacheQuestionData = await cacheQuestionResponse.json();
        if (cacheQuestionData) {
          delete requestBody.question;
        }
      }

      try {
        const params = Object.keys(requestBody)
          .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(requestBody[key])}`)
          .join('&');
        const eventSourceUrl = isDevelopment()
          ? `${References.DEV}${References.url.SEND_AQUESTION}?${params}`
          : `${References.PROD}${References.url.SEND_AQUESTION}?${params}`;
        let headers = {};
        if (isDevelopment()) {
          headers = {
            Authorization: 'Basic ' + btoa('admin:admin'),
          };
        }
        const eventSource = new EventSourcePolyfill(eventSourceUrl, {
          headers: headers,
          heartbeatTimeout: 12000000,
        });
        eventSource.onmessage = async function (event) {
          const data = JSON.parse(event.data);
          const answer = data?.answer;
          if (answer?.conversation_id) {
            setConversationId(answer.conversation_id);
          }
          if (answer?.response) {
            if (answer.role === 'debug') {
              // Don't delete
              console.log('Debug message', answer.response);
            } else {
              await handleNewMessage(answer.role ? answer.role : ROLE_BOT, answer);
            }
          }
        };

        eventSource.onerror = function (err) {
          setIsBotLoading(false);
          console.error('EventSource failed:', err);
          eventSource.close();
        };
        setStatusIcon(botIcon);
        const intervalTimeOut = setInterval(() => {
          if (eventSource.readyState === EventSourcePolyfill.CLOSED) {
            setIsBotLoading(false);
            eventSource.close();
            setTimeout(() => scrollToBottom(), 100);
            clearInterval(intervalTimeOut);
          }
        }, 1000);
      } catch (error: any) {
        console.error('Error fetching data: ', error);
        setIsBotLoading(false);
        showErrorMessage(error?.message);
      }
    }
  };

  // Modify setFile to reset the error state when a new file is selected
  const handleSetFiles = (newFiles: any) => {
    if (newFiles !== file) {
      setFiles(newFiles);
    }
  };

  // Function to show error message if bot does not respond
  const showErrorMessage = async (errorMessage: string) => {
    await handleNewMessage(ROLE_BOT, {
      text: errorMessage,
      sender: ROLE_ERROR,
      timestamp: formatTimeNewDate(new Date()),
    });
    scrollToBottom();
  };

  // Handles ID received from file uploaded in the server
  const handleFileId = (uploadedFile: any) => {
    setFileId(Object.values(uploadedFile) as string[]);
  };

  // Manage error
  const handleOnError = async (errorResponse: any) => {
    let errorMessage = '';

    if (errorResponse) {
      errorMessage = errorResponse.error || errorResponse.answer.error;
    } else if (typeof errorResponse === 'string') {
      errorMessage = errorResponse;
    }

    await handleNewMessage(ROLE_BOT, {
      text: errorMessage,
      sender: ROLE_ERROR,
      timestamp: formatTimeNewDate(new Date()),
    });
    setFile(null);
    scrollToBottom();
  };

  // Effect to update the loading message
  useEffect(() => {
    let intervalId: NodeJS.Timeout;

    if (isBotLoading && statusIcon !== responseSent) {
      const randomDelay = Math.random() * (10000 - 5000) + 5000;
      intervalId = setTimeout(() => { }, randomDelay);
    }

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [isBotLoading, statusIcon]);

  // Effect to retrieve assistants and set focus on the text input when the page first loads
  useEffect(() => {
    getLabels();
    getAssistants();
  }, []);

  // Reset the conversation when a new attendee is selected
  useEffect(() => {
    setMessages([]);
    setConversationId(null);
  }, [selectedOption]);

  // Scroll bottom when loading a new file
  useEffect(() => {
    scrollToBottom();
  }, [file]);

  // Effect to position focus on input
  useEffect(() => {
    inputRef.current.focus();
  }, [assistants]);

  let url = '';
  if (isDevelopment()) {
    url = References.DEV + References.url.UPLOAD_FILE;
  } else {
    url = References.PROD + References.url.UPLOAD_FILE;
  }
  const uploadConfig = {
    file: files,
    url: url,
    method: References.method.POST,
  };

  return (
    <div id={'iframe-container'} className="h-screen w-screen flex flex-col">
      {/* Initial message and assistants selection */}
      {assistants.length > 0 && (
        <div
          id={'iframe-selector'}
          style={{
            paddingTop: 8,
            paddingRight: 12,
            paddingLeft: 12,
            paddingBottom: 8,
          }}
          className="w-full assistants-shadow border-b py-1 px-2 border-gray-600"
        >
          <div id={'assistant-title'}>
            <SparksIcon style={{ height: 12, width: 12 }} />
            <div id={'assistant-title-label'}>
              {labels.ETCOP_Message_AssistantHeader}
            </div>
          </div>
          <DropdownInput
            value={selectedOption?.name}
            staticData={assistants}
            displayKey="name"
            onSelect={(option: any) => {
              handleOptionSelected(option);
              setMessages([]);
              setConversationId(null);
            }}
          />
        </div>
      )}
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          overflowX: 'hidden',
        }}
      >
        {/* Chat display area */}
        <div
          className={`${file ? 'h-[428px]' : 'h-[452px]'} flex-1 hide-scrollbar overflow-y-auto px-[12px] pb-[12px] bg-gray-200`}
        >
          {messages.length === 0 && (
            <div className="inline-flex mt-[12px] rounded-lg text-blue-900 font-medium">
              {areLabelsLoaded &&
                (noAssistants ? (
                  <TextMessage
                    type={ROLE_ERROR}
                    text={`${labels.ETCOP_NoAssistant}`}
                  />
                ) : (
                  <TextMessage
                    title={`${labels.ETCOP_Welcome_Greeting}\n${labels.ETCOP_Welcome_Message}`}
                    type={'left-user'}
                    text={''}
                  />
                ))}
            </div>
          )}

          {/* Displaying messages */}
          {messages.map((message, index) => (
            <div
              key={index}
              className={getMessageContainerClasses(message.sender)}
            >
              {message.sender === 'interpreting' && (
                <div className={`flex items-center`}>
                  <img
                    src={statusIcon}
                    alt="Status Icon"
                    className={
                      statusIcon === responseSent
                        ? 'w-5 h-5 mr-1'
                        : 'w-8 h-8 slow-bounce'
                    }
                  />
                  <span className={`text-sm ml-1 font-normal text-gray-700`}>
                    {message.text}
                  </span>
                </div>
              )}
              {message.sender !== 'interpreting' && (
                <p
                  className={`slide-up-fade-in inline-flex flex-col rounded-lg ${message.sender === ROLE_USER
                    ? 'text-gray-600 rounded-tr-none'
                    : message.sender === ROLE_ERROR
                      ? 'rounded-tl-none'
                      : 'text-black rounded-tl-none'
                    } break-words overflow-hidden max-w-[90%]`}
                >
                  {message.sender === ROLE_ERROR ? (
                    <TextMessage
                      key={index}
                      text={message.text}
                      time={message.timestamp}
                      type={getMessageType(message.sender)}
                    />
                  ) : // Normal message with Copilot's response
                    message.sender === ROLE_BOT ? (
                      <TextMessage
                        key={index}
                        text={message.text ? message.text : '...'}
                        time={message.timestamp}
                        type="left-user"
                      />
                    ) : message.sender === ROLE_TOOL || message.sender === ROLE_NODE || message.sender === ROLE_WAIT ? (
                      <div className={`flex items-center`}>
                        <img
                          src={statusIcon}
                          alt="Status Icon"
                          className={
                            statusIcon === responseSent
                              ? 'w-5 h-5 mr-1'
                              : 'w-8 h-8 slow-bounce'
                          }
                        />
                        <span className={`text-sm ml-1 font-normal`}>
                          {message.text ? message.text : '...'}
                        </span>
                      </div>
                    ) : (
                      <TextMessage
                        key={index}
                        text={message.text}
                        time={message.timestamp}
                        type="right-user"
                        files={message.files}
                      />
                    )}
                </p>
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        {/* Message input area */}
        <div
          id={'iframe-input-container'}
          style={{ marginBottom: 12 }}
          className={`mx-[12px]`}
          ref={inputRef}
        >
          {/* Conditionally render the context name title */}
          {contextTitle && isFirstMessage && (
            <ContextTitlePreview contextTitle={contextTitle} />
          )}

          {/* Input area */}
          <FileSearchInput
            value={inputValue}
            placeholder={labels.ETCOP_Message_Placeholder!}
            onChangeText={text => setInputValue(text)}
            onSubmit={handleSendMessage}
            onSubmitEditing={handleSendMessage}
            setFile={handleSetFiles}
            uploadConfig={uploadConfig}
            isDisabled={noAssistants}
            isSendDisable={isBotLoading}
            isAttachDisable={isBotLoading}
            onFileUploaded={handleFileId}
            onError={handleOnError}
            multiline
            numberOfLines={7}
          />
        </div>
      </div>
    </div>
  );
}

export default App;
