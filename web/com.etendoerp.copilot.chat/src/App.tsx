import { useState, useEffect, ChangeEvent, FormEvent, useRef } from "react";
import Input from "etendo-ui-library/dist-web/components/input/Input";
import TextMessage from "etendo-ui-library/dist-web/components/text-message/TextMessage";
import { useAssistants } from "./hooks/useAssistants";
import { formatTime, formatTimeNewDate } from "./utils/functions";
import enterIcon from "./assets/enter.svg";
import purpleEnterIcon from "./assets/purple_enter.svg";
import botIcon from "./assets/bot.svg";
import responseSent from "./assets/response-sent.svg";
import { LOADING_MESSAGES } from "./utils/constants";
import { ILabels } from "./interfaces";
import { IMessage } from "./interfaces/IMessage";
import "./App.css";

function App() {
  // States
  const [labels, setLabels] = useState<ILabels>({});
  const [sendIcon, setSendIcon] = useState(enterIcon);
  const [statusIcon, setStatusIcon] = useState(enterIcon);
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [isBotLoading, setIsBotLoading] = useState<boolean>(false);
  const [isInputFocused, setIsInputFocused] = useState<boolean>(false);
  const [areLabelsLoaded, setAreLabelsLoaded] = useState<boolean>(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const { selectedOption, assistants, getAssistants, handleOptionSelected } = useAssistants();

  // Constants
  const noAssistants = assistants.length === 0;

  // References
  const messagesEndRef = useRef<any>(null);
  const inputRef = useRef<any>(null);

  // Function to scroll bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  // Function to update the bot interpretation message
  const updateInterpretingMessage = () => {
    const randomIndex = Math.floor(Math.random() * LOADING_MESSAGES.length);
    const newMessage = LOADING_MESSAGES[randomIndex];

    setMessages(currentMessages => {
      const lastInterpretingIndex = currentMessages.findIndex(message => message.sender === "interpreting");
      if (lastInterpretingIndex !== -1) {
        const updatedMessages = [...currentMessages];
        updatedMessages[lastInterpretingIndex] = { ...updatedMessages[lastInterpretingIndex], text: newMessage };
        return updatedMessages;
      }
      return currentMessages;
    });
  };

  // Fetch labels data
  const getLabels = async () => {
    const requestOptions = {
      method: 'GET',
    }

    const response = await fetch("../../copilot/labels", requestOptions);
    const data = await response.json();
    if (data) {
      setLabels(data);
      setAreLabelsLoaded(true);
    }
  };

  // Function to handle sending a message
  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsBotLoading(true);

    if (!isBotLoading) {
      const question = inputValue.trim();
      setInputValue("");
      if (!question) return;

      // Delete previous error messages if they exist
      let updatedMessages = messages.filter(message => message.sender !== "error" && message.sender !== "interpreting");

      // Add user message
      const userMessage: IMessage = {
        text: question,
        sender: "user",
        timestamp: formatTimeNewDate(new Date()),
      };

      // Add interpreting message
      const interpretingMessage: IMessage = {
        text: "Interpreting request...",
        sender: "interpreting",
        timestamp: formatTimeNewDate(new Date())
      };

      updatedMessages.push(userMessage, interpretingMessage);

      setMessages(updatedMessages);
      setStatusIcon(botIcon);

      // Prepare request body
      const requestBody: any = {
        question: inputValue,
        app_id: selectedOption?.app_id
      };
      if (conversationId) {
        requestBody.conversation_id = conversationId;
      }

      const requestOptions = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      };

      try {
        const response = await fetch("../../copilot/question", requestOptions);
        const data = await response.json();
        if (!conversationId) setConversationId(data.conversation_id);

        // Update messages after response
        setMessages((currentMessages: any) => currentMessages.map((message: IMessage) =>
          message.sender === "interpreting" ?
            { ...message, text: labels.ETCOP_Generated_Response } :
            message
        ));
        setStatusIcon(responseSent);

        setTimeout(() => {
          setMessages(currentMessages => [
            ...currentMessages.filter(message => message.sender !== "interpreting"),
            { text: data.response, sender: "bot", timestamp: formatTime(data.timestamp) }
          ]);
          scrollToBottom();
          setIsBotLoading(false);
          setStatusIcon(botIcon);
        }, 2000);

      } catch (error) {
        console.error('Error fetching data:', error);
        setIsBotLoading(false);
        setMessages((currentMessages: any) => [
          ...currentMessages.filter((message: IMessage) => message.sender !== "interpreting"),
          {
            text: labels.ETCOP_ConectionError,
            sender: "error",
            timestamp: formatTimeNewDate(new Date())
          }
        ]);
        scrollToBottom();
      }
    }
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

  // Scroll bottom effect
  useEffect(() => {
    if (messages.length > 0) {
      scrollToBottom();
    }
  }, [messages]);

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

  // Effect to position focus on input
  useEffect(() => {
    inputRef.current.focus();
  }, [assistants])

  return (
    <div className="h-screen w-screen flex flex-col">
      {/* Initial message and assistants selection */}
      {assistants.length > 0 &&
        <div className="w-full assistants-shadow border-b py-1 px-2 border-gray-600">
          <Input
            value={selectedOption?.name}
            dataPicker={assistants}
            typeField="picker"
            displayKey="name"
            onOptionSelected={(option: any) => {
              handleOptionSelected(option);
              setMessages([]);
              setConversationId(null);
            }}
            height={33}
          />
        </div>
      }

      {/* Chat display area */}
      <div className="flex-1 hide-scrollbar overflow-y-auto px-[12px] pb-[12px] bg-gray-200">
        {messages.length === 0 && (
          <div className="inline-flex mt-[12px] rounded-lg text-blue-900 font-medium">
            {areLabelsLoaded && (
              noAssistants ? (
                <TextMessage
                  title={`${labels.ETCOP_NoAssistant}`}
                  type={"error"}
                  text={""}
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
                    type={message.sender === "error" ? "error" : message.sender === "user" ? "right-user" : "left-user"}
                  />
                ) : (
                  // Normal message with Copilot's response
                  message.sender === "bot" ? (
                    <TextMessage
                      key={index}
                      text={message.text}
                      time={message.timestamp}
                      type="left-user"
                    />
                  ) : (
                    <TextMessage
                      key={index}
                      text={message.text}
                      time={message.timestamp}
                      type="right-user"
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
      <div className={`bg-white-900 rounded-lg mx-[12px] border ${isInputFocused ? ' border-blue-900' : 'border-transparent'} ${noAssistants && 'opacity-50'}`}>
        <form
          onSubmit={handleSendMessage}
          className="flex w-full bg-white-900 rounded-lg px-2"
        >
          <input
            type="text"
            ref={inputRef}
            placeholder={labels.ETCOP_Message_Placeholder}
            className="flex-1 text-sm p-2 py-3 bg-transparent placeholder:text-gray-600 focus:outline-none"
            value={inputValue}
            disabled={noAssistants}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setInputValue(event.target.value)
            }
            onFocus={() => setIsInputFocused(true)}
            onBlur={() => setIsInputFocused(false)}
            required
          />

          <button
            type="submit"
            className="p-2 py-3 text-gray-600 hover:text-blue-500"
            disabled={isBotLoading || noAssistants}
            onMouseOver={() => setSendIcon(purpleEnterIcon)}
            onMouseOut={() => setSendIcon(enterIcon)}
          >
            <div className={`flex items-center gap-2 ${isBotLoading && 'opacity-50'}`}>
              <img src={sendIcon} className="w-3 h-3" alt="Enter" />
              <p className="text-xs">{labels.ETCOP_Send}</p>
            </div>
          </button>
        </form>
      </div>
    </div>
  );
}

export default App;
