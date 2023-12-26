import { useState } from "react";
import { IAssistant } from "../interfaces/IAssistant";

export const useAssistants = () => {
    const [selectedOption, setSelectedOption] = useState<IAssistant | null>(null);
    const [assistants, setAssistants] = useState<IAssistant[]>([]);

    // Fetch assistants data
    const getAssistants = async () => {
        const requestOptions: any = {
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
    const handleOptionSelected = (value: any) => {
        setSelectedOption(value);
    };

    return {
        selectedOption,
        assistants,
        getAssistants,
        handleOptionSelected,
    };
};
