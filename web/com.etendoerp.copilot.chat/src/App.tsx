import "./App.css";
import { IMessage } from "./interfaces";
import enterIcon from "./assets/enter.svg";
import botcitoIcon from "./assets/botcito.svg";
import { formatTime } from "./utils/functions";
import { useEffect, useState, FormEvent, ChangeEvent } from "react";
import { Input } from "etendo-ui-library/dist-web/components/input/";
import { EXAMPLE_CONVERSATIONS, MODEL_OPTIONS } from "./utils/constants";

function App() {
  const [messages, setMessages] = useState<IMessage[]>([]);
  const [inputValue, setInputValue] = useState<string>("");
  const [selectedOption, setSelectedOption] = useState<string>(
    MODEL_OPTIONS[0].key
  );

  // Handles the sending of a message
  const handleSendMessage = (event: FormEvent) => {
    event.preventDefault();
    if (!inputValue.trim()) return;

    const userMessage: IMessage = {
      text: inputValue,
      sender: "user",
      timestamp: formatTime(new Date()),
    };

    const interpretingMessage: IMessage = {
      text: "Interpreting request...",
      sender: "interpreting",
    };

    setMessages((currentMessages) => [
      ...currentMessages,
      userMessage,
      interpretingMessage,
    ]);

    setTimeout(() => {
      setMessages((currentMessages) => [
        ...currentMessages.filter((msg) => msg.sender !== "interpreting"),
        { text: "", sender: "bot", timestamp: formatTime(new Date()) },
      ]);
    }, 2500);

    setInputValue("");
  };

  // Effect to handle typing animation for bot messages
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

  // Simulates typing effect for messages
  const typeMessage = (text: string, messageIndex: number) => {
    let i = 0;
    let typingEffect = setInterval(() => {
      if (i < text.length - 1) {
        setMessages((currentMessages) =>
          currentMessages.map((msg, index: number) =>
            index === messageIndex ? { ...msg, text: msg.text + text[i] } : msg
          )
        );
        i++;
      } else {
        clearInterval(typingEffect);
      }
    }, 20);
  };

  // Handles clicking on predefined conversations
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
              <Input
                value={selectedOption}
                dataPicker={MODEL_OPTIONS}
                typeField={"picker"}
                titleLabel="Select a Model"
                displayKey="key"
                onChangeText={(value) => setSelectedOption(value)}
              />
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
                className={`inline-flex p-2 rounded-lg ${
                  message.sender === "user"
                    ? "bg-gray-400 text-gray-600 rounded-tr-none"
                    : "bg-white-900 text-black-900 rounded-tl-none"
                }`}
              >
                {message.text}
                <span className="text-[0.7rem] mt-3 ml-2 text-gray-600">
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
