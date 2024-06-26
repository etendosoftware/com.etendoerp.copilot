import { useState } from "react";
import { IAssistant } from "../interfaces/IAssistant";
import { References } from "../utils/references";
import { RestUtils } from "../utils/environment";

export const useAssistants = () => {
    const [selectedOption, setSelectedOption] = useState<IAssistant | null>(null);
    const [assistants, setAssistants] = useState<IAssistant[]>([]);
    const [showInitialMessage, setShowInitialMessage] = useState(true);

    // Fetch assistants data
    const getAssistants = async () => {
        const requestOptions = {
            method: 'GET',
        }
        const response = await RestUtils.fetch(References.url.GET_ASSISTANTS, requestOptions);
        const data = await response.json();
        if (data.length > 0) {
            setSelectedOption(data[0]);
            setAssistants(data);
        }
    };

    // Function to reset messages
    const resetMessages = () => {
        setShowInitialMessage(true);
    };

    // Handle option selection
    const handleOptionSelected = (value: IAssistant | null) => {
        setSelectedOption(value);
        resetMessages();
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
        hideInitialMessage,
        resetMessages
    };
};
