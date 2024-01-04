import { useState, useEffect, ChangeEvent, FormEvent, useRef } from "react";
import Input from "etendo-ui-library/dist-web/components/input/Input";
import { IMessage } from "./interfaces/IMessage";
import { useAssistants } from "./hooks/useAssistants";
import { formatTime } from "./utils/functions";
import enterIcon from "./assets/enter.svg";
import purpleEnterIcon from "./assets/purple_enter.svg";
import botIcon from "./assets/botcito.svg";
import responseSent from "./assets/response-sent.svg";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "./App.css";
import { CodeComponent } from "./components/CodeComponent";
import { LOADING_MESSAGES } from "./utils/constants";

function App() {
  // Constants
  const hasMessagesSent = () => messages.length > 0;

  // States
  const [statusIcon, setStatusIcon] = useState(enterIcon);
  const [sendIcon, setSendIcon] = useState(enterIcon);
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [isBotLoading, setIsBotLoading] = useState<boolean>(false);
  const [isInputFocused, setIsInputFocused] = useState<boolean>(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const { selectedOption, assistants, getAssistants, handleOptionSelected, showInitialMessage, hideInitialMessage } = useAssistants(hasMessagesSent);

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
        return [
          ...currentMessages.slice(0, lastInterpretingIndex),
          { ...currentMessages[lastInterpretingIndex], text: newMessage }
        ];
      }
      return currentMessages;
    });
  };

  // Function to handle sending a message
  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsBotLoading(true);

    if (!isBotLoading) {
      const question = inputValue.trim();
      setInputValue("");
      if (!question) return;
      hideInitialMessage();

      const userMessage: IMessage = {
        text: inputValue,
        sender: "user",
        timestamp: formatTime(new Date()),
      };
      setMessages(currentMessages => [...currentMessages, userMessage]);

      const interpretingMessage: IMessage = {
        text: "Interpreting request...",
        sender: "interpreting",
        timestamp: formatTime(new Date())
      };

      setMessages(currentMessages => [...currentMessages, interpretingMessage]);
      setStatusIcon(botIcon);

      const requestOptions = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: inputValue, assistant_id: selectedOption?.assistant_id })
      };

      try {
        const response = await fetch("../../copilot/question", requestOptions);
        const data = await response.json();
        if (!conversationId) setConversationId(data.conversation_id);

        setTimeout(() => {
          setMessages(currentMessages => currentMessages.map(message =>
            message.sender === "interpreting" ?
              { ...message, text: "Ready! Generating response..." } :
              message
          ));
          setStatusIcon(responseSent);

          setTimeout(() => {
            setMessages(currentMessages => [
              ...currentMessages.filter(message => message.sender !== "interpreting"),
              { text: data.answer, sender: "bot", timestamp: formatTime(new Date()) }
            ]);
            scrollToBottom();
            setIsBotLoading(false);
            setStatusIcon(botIcon);
          }, 2000);
        }, 7000);

      } catch (error) {
        console.error('Error fetching data:', error);
        setIsBotLoading(false);
      }
    }
  };

  // Effect to update the loading message
  useEffect(() => {
    let intervalId: NodeJS.Timeout | null = null;

    const updateInterpretingMessageRandomly = () => {
      // Every 5 and 10 second intervals the bot's response upload message is updated
      const randomDelay = Math.random() * (10000 - 5000) + 5000;
      updateInterpretingMessage();
      intervalId = setTimeout(updateInterpretingMessageRandomly, randomDelay);
    };

    if (isBotLoading && statusIcon !== responseSent) {
      updateInterpretingMessageRandomly();
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
    inputRef.current.focus();
    getAssistants();
  }, []);

  // Reset the conversation when a new attendee is selected
  useEffect(() => {
    setMessages([]);
    setConversationId(null);
  }, [selectedOption]);

  return (
    <div className="h-screen w-screen flex flex-col">
      {/* Initial message and assistants selection */}
      {assistants &&
        <div className="w-full assistants-shadow border-b py-[0.35rem] px-2 border-gray-600">
          <Input
            value={selectedOption?.name}
            dataPicker={assistants}
            typeField="picker"
            displayKey="name"
            onOptionSelected={(option: any) => handleOptionSelected(option)}
            height={33}
          />
        </div>
      }

      {/* Chat display area */}
      <div className="flex-1 hide-scrollbar overflow-y-auto px-[12px] pt-2 pb-1 bg-gray-200">
        {showInitialMessage &&
          <div className="bg-white-900 inline-flex p-5 py-3 rounded-lg text-blue-900 font-medium">
            <div className="text-xl font-semibold">
              <p>Hi 👋</p>
              <span>How can we help?</span>
            </div>
          </div>}

        {/* Displaying messages */}
        {messages.map((message, index) => (
          <div
            key={index}
            className={`p-2 text-sm ${message.sender === "user"
              ? "text-right user-message slide-up-fade-in"
              : message.sender === "interpreting"
                ? ""
                : "message-animation"
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
                className={`slide-up-fade-in inline-flex flex-col p-2 rounded-lg ${message.sender === "user"
                  ? "bg-gray-400 text-gray-600 rounded-tr-none"
                  : "bg-white-900 text-black rounded-tl-none"
                  } break-words overflow-hidden max-w-[90%]`}
              >
                {message.sender === "bot" ? (
                  <ReactMarkdown
                    children={message.text}
                    remarkPlugins={[remarkGfm]}
                    components={{
                      code: CodeComponent,
                    }}
                  />
                ) : (
                  <p>{message.text}</p>
                )}
                <span className="text-xs mt-1 text-gray-600">
                  {message.timestamp}
                </span>
              </p>
            )}
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* Message input area */}
      <div className={`bg-white-900 rounded-lg mx-[12px] border ${isInputFocused ? ' border-blue-900' : 'border-transparent'}`}>
        <form
          onSubmit={handleSendMessage}
          className="flex w-full bg-white-900 rounded-lg px-2"
        >
          <input
            type="text"
            ref={inputRef}
            placeholder="Message..."
            className="flex-1 text-sm p-2 py-3 bg-transparent placeholder:text-gray-600 focus:outline-none"
            value={inputValue}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setInputValue(event.target.value)
            }
            onFocus={() => setIsInputFocused(true)}
            onBlur={() => setIsInputFocused(false)}
            required
            title="Please enter a message before sending."
          />

          <button
            type="submit"
            className="p-2 py-3 text-gray-600 hover:text-blue-500"
            disabled={isBotLoading}
            onMouseOver={() => setSendIcon(purpleEnterIcon)}
            onMouseOut={() => setSendIcon(enterIcon)}
          >
            <div className={`flex items-center gap-2 ${isBotLoading && 'opacity-50'}`}>
              <img src={sendIcon} className="w-3 h-3" alt="Enter" />
              <p className="text-xs">Send</p>
            </div>
          </button>
        </form>
      </div>
    </div>
  );
}

export default App;
