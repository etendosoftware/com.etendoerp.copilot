import { useState, useEffect, ChangeEvent, FormEvent } from "react";
import { IMessage } from "./interfaces/IMessage";
import { useAssistants } from "./hooks/useAssistants";
import { formatTime } from "./utils/functions";
import { EXAMPLE_CONVERSATIONS } from "./utils/constants";
import Input from "etendo-ui-library/dist-web/components/input/Input";
import enterIcon from "./assets/enter.svg";
import botcitoIcon from "./assets/botcito.svg";
import "./App.css";

function App() {
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [conversationId, setConversationId] = useState<string | null>(null);
  const { selectedOption, assistants, getAssistants, handleOptionSelected } = useAssistants();

  // Function to handle sending a message
  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!inputValue.trim()) return;

    // Constructing the user's message
    const userMessage: IMessage = {
      text: inputValue,
      sender: "user",
      timestamp: formatTime(new Date()),
    };

    const interpretingMessage: IMessage = {
      text: "Interpreting request...",
      sender: "interpreting",
    };

    setMessages((currentMessages) => [...currentMessages, userMessage, interpretingMessage]);

    const question = {
      question: inputValue,
      assistant_id: selectedOption?.assistant_id,
      ...(conversationId && { conversation_id: conversationId }),
    };

    const requestOptions = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(question),
    };

    try {
      const response = await fetch("../../copilot/question", requestOptions);
      const data = await response.json();

      if (!conversationId) setConversationId(data.conversation_id);
      setMessages((currentMessages) => [
        ...currentMessages.filter((msg) => msg.sender !== "interpreting"),
        { text: data.answer, sender: "bot", timestamp: formatTime(new Date()) },
      ]);
    } catch (error) {
      console.error('Error fetching data:', error);
    }

    setInputValue("");
  };

  // Effect to simulate typing animation for bot messages
  useEffect(() => {
    const lastMessageIndex = messages.length - 1;
    if (
      messages.length > 0 &&
      messages[lastMessageIndex].sender === "bot" &&
      messages[lastMessageIndex].text === ""
    ) {
      typeMessage("¡Hola! Soy un chatbot.", lastMessageIndex);
    }
  }, [messages]);

  // Function to simulate typing effect for messages
  const typeMessage = (text: string, messageIndex: number) => {
    let i = 0;
    const typingEffect = setInterval(() => {
      if (i < text.length) {
        setMessages((currentMessages) =>
          currentMessages.map((msg, index) =>
            index === messageIndex ? { ...msg, text: msg.text + text[i++] } : msg
          )
        );
      } else {
        clearInterval(typingEffect);
      }
    }, 20);
  };

  // Effect to get assistants on initial component mount
  useEffect(() => {
    getAssistants();
  }, []);


  // Function to handle conversation selection
  const handleConversationClick = (conversation: string) => {
    setInputValue(conversation);
  };

  return (
    <div className="h-screen w-screen pt-2 pb-1 px-[12px] bg-gray-200 flex flex-col justify-end">
      {/* Chat display area */}
      <div className="flex-1 overflow-y-auto text-sm hide-scrollbar">
        {/* Initial message and model selection */}
        {messages.length === 0 && (
          <div>
            <div className="w-full mb-2">
              {assistants && <Input
                value={selectedOption?.name}
                dataPicker={assistants}
                typeField={"picker"}
                displayKey="name"
                onOptionSelected={(option: any) => handleOptionSelected(option)}
              />}
            </div>
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
            </div>
          </div>
        )}

        {/* Displaying messages */}
        {messages.map((message, index) => (
          <div
            key={index}
            className={`p-2 ${message.sender === "user" ? "text-right" : ""}`}
          >
            {message.sender === "interpreting" && (
              <div className="flex items-center">
                <img
                  src={botcitoIcon}
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
                {message.text}
                <span className="text-xs mt-1 text-gray-600">
                  {message.timestamp}
                </span>
              </p>
            )}
          </div>
        ))}
      </div>

      {/* Message input area */}
      <div className="bg-white-900 rounded-lg">
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
