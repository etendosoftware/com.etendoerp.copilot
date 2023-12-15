import { useEffect, useState } from "react";
import "./App.css";
import enterIcon from "./assets/enter.svg";
import botcitoIcon from "./assets/botcito.svg";
import readyIcon from "./assets/ready.svg";
import closeIcon from "./assets/close.svg";
import uploadPDFIcon from "./assets/upload-pdf.svg";
import { formatAMPM } from "./utils/functions";
import { Input } from "etendo-ui-library/dist-web/components/input/";

const EXAMPLE_CONVERSATIONS = [
  { conversation: "Explain how the finance module works in simple terms" },
  { conversation: "How do I create a sales order?" },
  { conversation: "I need to create a stylesheet" },
];

function App() {
  const [messages, setMessages] = useState<any>([]);
  const [inputValue, setInputValue] = useState("");
  const [fileName, setFileName] = useState("");
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadedFile, setUploadedFile] = useState<any>(null);

  const options = [
    { key: "XML Translation Tool" },
    { key: "Invoice Generator" },
    { key: "Window Creation" },
  ];
  const [selectedOption, setSelectedOption] = useState(options[0].key);

  const removeFile = () => {
    setFileName("");
    setUploadProgress(0);
    setUploadedFile(null);
  };

  const uploadFileMock = (file: any) => {
    console.log("file", file);
    let progress = 0;
    const interval = setInterval(() => {
      progress += 5;
      setUploadProgress(progress);
      if (progress >= 100) {
        clearInterval(interval);
        setTimeout(() => {
          setUploadProgress(0);
          setUploadedFile(true);
        }, 500);
      }
    }, 100);
  };

  const handleSendMessage = (event: any) => {
    event.preventDefault();
    if (!inputValue.trim()) return;

    let userMessage: any = {
      text: inputValue,
      sender: "user",
      timestamp: formatAMPM(new Date()),
    };

    if (uploadedFile) {
      userMessage = {
        text: inputValue,
        sender: "user",
        hasFile: true,
        fileName: fileName,
        timestamp: formatAMPM(new Date()),
      };
      setUploadedFile(null);
      setFileName("");
    }

    const interpretingMessage: any = {
      text: "Interpreting request...",
      sender: "interpreting",
    };

    setMessages((currentMessages: any) => [
      ...currentMessages,
      userMessage,
      interpretingMessage,
    ]);

    // Simulate bot response
    setTimeout(() => {
      setMessages((currentMessages: any) => [
        ...currentMessages.filter((msg: any) => msg.sender !== "interpreting"),
        { text: "", sender: "bot", timestamp: formatAMPM(new Date()) },
      ]);
    }, 2500);

    setInputValue("");
  };

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

  const typeMessage = (text: string, messageIndex: number) => {
    let i = 0;
    let typingEffect = setInterval(() => {
      if (i < text.length - 1) {
        setMessages((currentMessages: any) =>
          currentMessages.map((msg: any, index: number) =>
            index === messageIndex ? { ...msg, text: msg.text + text[i] } : msg
          )
        );
        i++;
      } else {
        clearInterval(typingEffect);
      }
    }, 20);
  };

  const handleConversationClick = (conversation: string) => {
    setInputValue(conversation);
  };

  const handleDragOver = (event: any) => {
    event.preventDefault();
  };

  const handleDrop = (event: any) => {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (file) {
      setFileName(file.name);
      uploadFileMock(file);
    }
  };

  const handleDragLeave = (event: any) => {
    event.preventDefault();
  };

  return (
    <div className="h-screen w-screen pt-2 pb-1 px-[12px] bg-gray-200 flex flex-col justify-end">
      <div className="flex-1 overflow-y-auto text-sm hide-scrollbar">
        {messages.length === 0 && (
          <>
            <div className="w-full mb-2">
              <Input
                value={selectedOption}
                dataPicker={options}
                typeField={"picker"}
                titleLabel="Select a Model"
                displayKey="key"
                onChangeText={(value: any) => setSelectedOption(value.key)}
              />
            </div>
            <div className="bg-white-900 p-5 rounded-lg text-blue-900 font-medium">
              <div className="mb-2 text-xl font-semibold">
                <p>Hi admin 👋</p>
                <span> How can we help?</span>
              </div>
              <div>
                {EXAMPLE_CONVERSATIONS.map((conversation: any, index) => (
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
          </>
        )}

        {messages.map((message: any, index: number) => (
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
              <>
                <div className="inline-flex flex-wrap gap-2">
                  {message.sender === "user" && message.hasFile ? (
                    <div className="bg-gray-400 p-2 rounded-lg rounded-tr-none">
                      <div className="inline-flex gap-2 items-center p-2 mb-3 justify-start bg-gray-500 rounded-lg">
                        <img
                          src={uploadPDFIcon}
                          className="w-12 h-12"
                          alt="Uploaded file"
                        />
                        <div className="flex flex-col text-[0.85rem]">
                          <p className="inline-flex text-black-900">
                            {message.fileName}
                          </p>
                          <span className="text-gray-600">
                            1 page - 225 KB - pdf
                          </span>
                        </div>
                      </div>
                      <div className="flex justify-start text-gray-600 text-sm">
                        {message.text}
                      </div>
                    </div>
                  ) : (
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
              </>
            )}
          </div>
        ))}
      </div>
      <div
        className="bg-white-900 rounded-lg"
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        onDragLeave={handleDragLeave}
      >
        <form
          onSubmit={handleSendMessage}
          className="flex w-full bg-white-900 rounded-lg px-2"
        >
          <input
            type="text"
            placeholder="Message..."
            className="flex-1 text-sm p-2 bg-transparent placeholder:text-gray-600 focus:outline-none"
            value={inputValue}
            onChange={(event: any) => setInputValue(event.target.value)}
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
        {fileName && (
          <div className="text-sm flex border-t border-gray-400 flex-col gap-2 text-gray-600 px-4 py-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 w-full">
                <img
                  src={uploadPDFIcon}
                  className="w-10 h-10"
                  alt="Uploaded file"
                />
                <div className="flex flex-col w-full h-8">
                  <p className="text-sm text-black-900 w-full">{fileName}</p>
                  {uploadProgress > 0 && (
                    <div className="w-full bg-gray-300 rounded-full h-2 progress-bar-container">
                      <div
                        className="bg-black-900 h-2 rounded-full progress-bar"
                        style={{ width: `${uploadProgress}%` }}
                      />
                      <p className="text-xs text-gray-600">
                        97 of 225 KB - About 5 seconds
                      </p>
                    </div>
                  )}
                  {!uploadProgress && (
                    <p className="text-xs text-gray-600">225 KB</p>
                  )}
                </div>
              </div>
              {uploadedFile && (
                <div className="flex gap-2 items-center">
                  <img src={readyIcon} className="w-5 h-5" alt="Ready" />
                  <img
                    src={closeIcon}
                    className="w-3 h-3 cursor-pointer"
                    alt="Close"
                    onClick={removeFile}
                  />
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default App;
