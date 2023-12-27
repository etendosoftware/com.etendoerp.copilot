import { useState, useEffect, ChangeEvent, FormEvent, useRef } from "react";
import { IMessage } from "./interfaces/IMessage";
import { useAssistants } from "./hooks/useAssistants";
import { formatTime } from "./utils/functions";
import { EXAMPLE_CONVERSATIONS } from "./utils/constants";
import Input from "etendo-ui-library/dist-web/components/input/Input";
import enterIcon from "./assets/enter.svg";
import botIcon from "./assets/botcito.svg";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "./App.css";
import { CodeComponent } from "./components/CodeComponent";

function App() {
  const messagesEndRef = useRef<any>(null);
  const hasMessagesSent = () => messages.length > 0;

  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [conversationId, setConversationId] = useState<string | null>(null);
  const { selectedOption, assistants, getAssistants, handleOptionSelected, showInitialMessage, hideInitialMessage } = useAssistants(hasMessagesSent);

  // Reference
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  // Function to handle sending a message
  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!inputValue.trim()) return;

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
        setMessages(currentMessages => [
          ...currentMessages.filter(message => message !== interpretingMessage),
          { text: data.answer, sender: "bot", timestamp: formatTime(new Date()) }
        ]);
        scrollToBottom();
      }, 2000);
    } catch (error) {
      console.error('Error fetching data:', error);
    }

    setInputValue("");
  };

  // Scroll bottom effect
  useEffect(() => {
    if (messages.length > 0) {
      scrollToBottom();
    }
  }, [messages]);

  // Effect to get assistants on initial component mount
  useEffect(() => {
    getAssistants();
  }, []);

  // Reset the conversation when a new attendee is selected
  useEffect(() => {
    setMessages([]);
    setConversationId(null);
  }, [selectedOption]);


  // Function to handle conversation selection
  const handleConversationClick = (conversation: string) => {
    setInputValue(conversation);
  };

  return (
    <div className="h-screen w-screen flex flex-col">
      {/* Initial message and model selection */}
      <div className="w-full border-b py-[0.35rem] px-2 border-gray-600">
        {assistants &&
          <Input
            value={selectedOption?.name}
            dataPicker={assistants}
            typeField={"picker"}
            displayKey="name"
            onOptionSelected={(option: any) => handleOptionSelected(option)}
            height={33}
          />}
      </div>

      {/* Chat display area */}
      <div className="flex-1 hide-scrollbar overflow-y-auto px-[12px] pt-2 pb-1 bg-gray-200">
        {showInitialMessage &&
          <div className="bg-white-900 p-5 rounded-lg text-blue-900 font-medium">
            <div className="mb-2 text-xl font-semibold">
              <p>Hi admin 👋</p>
              <span> How can we help?</span>
            </div>
            <div>
              {EXAMPLE_CONVERSATIONS.map((conversation, index) => (
                <div
                  key={index}
                  className="rounded-lg mt-4 text-gray-600 bg-gray-400 p-4 text-sm font-medium cursor-pointer hover:text-blue-900 transition duration-300 ease-in-out"
                  onClick={() =>
                    handleConversationClick(conversation.conversation)
                  }
                >
                  {conversation.conversation} →
                </div>
              ))}
            </div>
          </div>}

        {/* Displaying messages */}
        {messages.map((message, index) => (
          <div
            key={index}
            className={`p-2 text-sm ${message.sender === "user" ? "text-right" : ""}`}
          >
            {message.sender === "interpreting" && (
              <div className="flex items-center">
                <img
                  src={botIcon}
                  alt="Interpreting"
                  className="w-8 h-8 slow-bounce"
                />
                <span className="text-sm ml-1 font-normal text-gray-700">
                  {message.text}
                </span>
              </div>
            )}
            {message.sender !== "interpreting" && (
              <p
                className={`inline-flex flex-col p-2 rounded-lg ${message.sender === "user"
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
      <div className="bg-white-900 rounded-lg mx-[12px]">
        <form
          onSubmit={handleSendMessage}
          className="flex w-full bg-white-900 rounded-lg px-2"
        >
          <input
            type="text"
            placeholder="Message..."
            className="flex-1 text-sm p-2 bg-transparent placeholder:text-gray-600 focus:outline-none"
            value={inputValue}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setInputValue(event.target.value)
            }
            required
            title="Please enter a message before sending."
          />

          <button type="submit" className="p-2">
            <div className="flex items-center gap-2">
              <img src={enterIcon} className="w-3 h-3" alt="Enter" />
              <p className="text-gray-600 text-xs">Send</p>
            </div>
          </button>
        </form>
      </div>
    </div>
  );
}

export default App;
