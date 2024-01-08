import { useState } from "react";
import { IAssistant } from "../interfaces/IAssistant";

export const useAssistants = (shouldHideInitialMessage: () => boolean) => {
    const [selectedOption, setSelectedOption] = useState<IAssistant | null>(null);
    const [assistants, setAssistants] = useState<IAssistant[]>([]);
    const [showInitialMessage, setShowInitialMessage] = useState(true);

    // Fetch assistants data
    const getAssistants = async () => {
        const requestOptions = {
            method: 'GET',
        }

        const response = await fetch("../../copilot/assistants", requestOptions);
        const data = await response.json();
        if (data.length > 0) {
            setSelectedOption(data[0]);
            setAssistants(data);
        }
    };

    // Handle option selection
    const handleOptionSelected = (value: IAssistant | null) => {
        setSelectedOption(value);
        if (shouldHideInitialMessage()) {
            setShowInitialMessage(false);
        }
    };

    // Function to hide initial introduction
    const hideInitialMessage = () => {
        setShowInitialMessage(false);
    };

    return {
        selectedOption,
        assistants,
        getAssistants,
        handleOptionSelected,
        showInitialMessage,
        hideInitialMessage
    };
};
