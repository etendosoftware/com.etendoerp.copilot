import  { useState, useEffect, useRef } from "react";
import TextMessage from "etendo-ui-library/dist-web/components/text-message/TextMessage";
import FileSearchInput from "etendo-ui-library/dist-web/components/inputBase/file-search-input/FileSearchInput";
import { useAssistants } from "./hooks/useAssistants";
import { formatTimeNewDate, getMessageType } from "./utils/functions";
import enterIcon from "./assets/enter.svg";
import botIcon from "./assets/bot.svg";
import responseSent from "./assets/response-sent.svg";
import { ILabels } from "./interfaces";
import { IMessage } from "./interfaces/IMessage";
import { References } from "./utils/references";
import "./App.css";
import { DropdownInput } from "etendo-ui-library/dist-web/components";
import { SparksIcon } from "etendo-ui-library/dist-web/assets/images/icons";
import { RestUtils, isDevelopment } from "./utils/environment";
import { EventSourcePolyfill } from 'event-source-polyfill';

function App() {
  // States
  const [file, setFile] = useState<any>(null);
  const [labels, setLabels] = useState<ILabels>({});
  const [statusIcon, setStatusIcon] = useState(enterIcon);
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [fileId, setFileId] = useState<string | null>(null);
  const [isBotLoading, setIsBotLoading] = useState<boolean>(false);
  const [areLabelsLoaded, setAreLabelsLoaded] = useState<boolean>(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const { selectedOption, assistants, getAssistants, handleOptionSelected } = useAssistants();

  // Constants
  const noAssistants = assistants?.length === 0 ? true : false;

  // References
  const messagesEndRef = useRef<any>(null);
  const inputRef = useRef<any>(null);

  const handleNewMessage = async (role:string, message:IMessage) => {
    console.log("handleNewMessage",message)
    let _text = message.response ? message.response : message.text
    if(role === "tool") {
      _text = "🛠️️ " + _text + ""
    }
    setMessages(prevMessages => [...prevMessages, {
      message_id: message.message_id,
      text: _text,
      sender: role,
      timestamp: formatTimeNewDate(new Date()),
    }]);
    scrollToBottom();
  };

  // Function to scroll bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  // Function to update the bot interpretation message
  const updateInterpretingMessage = () => {
  };

  // Fetch labels data
  const getLabels = async () => {
    const requestOptions = {
      method: 'GET',
    }
    const response = await RestUtils.fetch(References.url.GET_LABELS, requestOptions);
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
      setInputValue("");
      if (!question) return;

      // Add user message
      const userMessage: IMessage = {
        text: question,
        sender: "user",
        timestamp: formatTimeNewDate(new Date()),
      };
      if (file) {
        userMessage.file = file.name;
      }

      await handleNewMessage("user", userMessage)
      setStatusIcon(botIcon);
      setTimeout(() => scrollToBottom(), 100);

      // Prepare request body
      const requestBody: any = {
        question: inputValue,
        app_id: selectedOption?.app_id
      };
      if (conversationId) {
        requestBody.conversation_id = conversationId;
      }
      if (fileId) {
        requestBody.file = fileId;
      }

      try {
          const params = Object.keys(requestBody).map(key => `${key}=${requestBody[key]}`).join('&');
          const eventSourceUrl = isDevelopment() ?
          `${References.DEV}${References.url.SEND_AQUESTION}?client_id=${clientId}&${params}`:
           `${References.DEV}${References.url.SEND_AQUESTION}?client_id=${clientId}&${params}`;
           const headers = {
            'Authorization': 'Basic ' + btoa('admin:admin'),
          };
          const eventSource = new EventSourcePolyfill(eventSourceUrl, {
            headers: headers,
            heartbeatTimeout: 12000000,
          });

          eventSource.onmessage = function(event) {
            const data = JSON.parse(event.data);
            console.log("ES data",data)
            const answer = data?.answer;
            if(answer?.response) {
              handleNewMessage(answer.role ? answer.role : "bot", answer);
            }
          };

          eventSource.onerror = function(err) {
            console.error("EventSource failed:", err);
            eventSource.close();
          };

        setIsBotLoading(false);
        setStatusIcon(botIcon);
      } catch (error: any) {
        console.error('Error fetching data: ', error);
        setIsBotLoading(false);
        showErrorMessage(error?.message);
      }
    };
  };

  // Modify setFile to reset the error state when a new file is selected
  const handleSetFile = (newFile: any) => {
    if (newFile !== file) {
      setFile(newFile);
    }
  };

  // Function to show error message if bot does not respond
  const showErrorMessage = async (errorMessage: string) => {
    await handleNewMessage("bot", {
        text: errorMessage,
        sender: "error",
        timestamp: formatTimeNewDate(new Date())
    });
    scrollToBottom();
  };

  // Handles ID received from file uploaded in the server
  const handleFileId = (uploadedFile: any) => {
    setFileId(uploadedFile.file);
  };

  // Manage error 
  const handleOnError = async (errorResponse: any) => {
    let errorMessage = "";

    if (errorResponse) {
      errorMessage = errorResponse.error || errorResponse.answer.error;
    } else if (typeof errorResponse === 'string') {
      errorMessage = errorResponse;
    }

    await handleNewMessage("bot", {
      text: errorMessage,
      sender: "error",
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
      intervalId = setTimeout(updateInterpretingMessage, randomDelay);
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
  }, [assistants])

  const uploadConfig = {
    file: file,
    url: References.url.UPLOAD_FILE,
    method: References.method.POST,
  }

  return (
    <div id={'iframe-container'} className="h-screen w-screen flex flex-col">
      {/* Initial message and assistants selection */}
      {assistants.length > 0 &&
        <div id={'iframe-selector'} style={{paddingTop: 8, paddingRight: 12, paddingLeft: 12,paddingBottom: 8}} className="w-full assistants-shadow border-b py-1 px-2 border-gray-600">
          <div id={'assistant-title'}>
            <SparksIcon style={{height:12,width:12}}/>
            <div id={'assistant-title-label'}>{labels.ETCOP_Message_AssistantHeader}</div>
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
      }
      <div style={{
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          overflowX: 'hidden'
        }}>
        {/* Chat display area */}
        <div className={`${file ? 'h-[428px]' : 'h-[452px]'} flex-1 hide-scrollbar overflow-y-auto px-[12px] pb-[12px] bg-gray-200`}>
          {messages.length === 0 && (
            <div className="inline-flex mt-[12px] rounded-lg text-blue-900 font-medium">
              {areLabelsLoaded && (
                noAssistants ? (
                  <TextMessage
                    type={"error"}
                    text={`${labels.ETCOP_NoAssistant}`}
                  />
                ) : (
                  <TextMessage
                    title={`${labels.ETCOP_Welcome_Greeting}\n${labels.ETCOP_Welcome_Message}`}
                    type={"left-user"}
                    text={""}
                  />
                )
              )}
            </div>
          )}

        {/* Displaying messages */}
        {messages.map((message, index) => (
          <div
            key={index}
            className={`text-sm mt-[12px] ${message.sender === "user"
              ? "text-right user-message slide-up-fade-in"
              : message.sender === "interpreting"
                ? ""
                : message.sender === "error"
                  ? "text-red-900 rounded-lg"
                  : "text-black rounded-lg"
              }`}
          >
            {message.sender === "interpreting" && (
              <div className={`flex items-center`}>
                <img
                  src={statusIcon}
                  alt="Status Icon"
                  className={statusIcon === responseSent ? "w-5 h-5 mr-1" : "w-8 h-8 slow-bounce"}
                />
                <span className={`text-sm ml-1 font-normal text-gray-700`}>
                  {message.text}
                </span>
              </div>
            )}
            {message.sender !== "interpreting" && (
              <p
                className={`slide-up-fade-in inline-flex flex-col rounded-lg ${message.sender === "user"
                  ? "text-gray-600 rounded-tr-none"
                  : message.sender === "error" ? "rounded-tl-none" : "text-black rounded-tl-none"
                  } break-words overflow-hidden max-w-[90%]`}
              >
                {message.sender === "error" ? (
                  <TextMessage
                    key={index}
                    text={message.text}
                    time={message.timestamp}
                    type={getMessageType(message.sender)}
                  />
                ) : (
                  // Normal message with Copilot's response
                  message.sender === "bot" ? (
                    <TextMessage
                      key={index}
                      text={message.text ? message.text : "..." }
                      time={message.timestamp}
                      type="left-user"
                    />
                  ) :
                  message.sender === "tool" ? (
                    <TextMessage
                      key={index}
                      text={message.text ? message.text : "..." }
                      type="left-user"
                    />
                  ) :
                  (
                    <TextMessage
                      key={index}
                      text={message.text}
                      time={message.timestamp}
                      type="right-user"
                      file={message.file}
                    />
                  )
                )}
              </p>
            )}
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

        {/* Message input area */}
        <div id={'iframe-input-container'} style={{marginBottom:12}} className={`mx-[12px]`} ref={inputRef}>
          <FileSearchInput
            value={inputValue}
            placeholder={labels.ETCOP_Message_Placeholder!}
            onChangeText={text => setInputValue(text)}
            onSubmit={handleSendMessage}
            onSubmitEditing={handleSendMessage}
            setFile={handleSetFile}
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
