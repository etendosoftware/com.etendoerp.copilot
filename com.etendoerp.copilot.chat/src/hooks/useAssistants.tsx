import { useState, useCallback, useMemo } from "react";
import { IAssistant } from "../interfaces/IAssistant";
import { References } from "../utils/references";
import { RestUtils } from "../utils/environment";

export const useAssistants = () => {
    const [selectedOption, setSelectedOption] = useState<IAssistant | null>(null);
    const [assistants, setAssistants] = useState<IAssistant[]>([]);
    const [showInitialMessage, setShowInitialMessage] = useState(true);
    const [showOnlyFeatured, setShowOnlyFeatured] = useState(true);

    // Fetch assistants data
    const getAssistants = async () => {
        const requestOptions = {
            method: 'GET',
        }
        const response = await RestUtils.fetch(References.url.GET_ASSISTANTS, requestOptions);
        const data: IAssistant[] = await response.json();
        if (data.length > 0) {
            // Sort: featured "Y" first, then the rest
            const sorted = [...data].sort((a, b) => {
                if (a.featured === "Y" && b.featured !== "Y") return -1;
                if (a.featured !== "Y" && b.featured === "Y") return 1;
                return 0;
            });
            setSelectedOption(sorted[0]);
            setAssistants(sorted);
        }
    };

    // Filtered list: when showOnlyFeatured is true, only show assistants with featured "Y".
    // Falls back to all if none have featured "Y".
    const filteredAssistants = useMemo(() => {
        if (!showOnlyFeatured) return assistants;
        const featured = assistants.filter(a => a.featured === "Y");
        return featured.length > 0 ? featured : assistants;
    }, [assistants, showOnlyFeatured]);

    const hasFeaturedAssistants = useMemo(
        () => assistants.some(a => a.featured === "Y"),
        [assistants]
    );

    const toggleFeaturedFilter = useCallback(() => {
        setShowOnlyFeatured(prev => !prev);
    }, []);

    const resetFeaturedFilter = useCallback(() => {
        setShowOnlyFeatured(true);
    }, []);

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
        filteredAssistants,
        hasFeaturedAssistants,
        showOnlyFeatured,
        toggleFeaturedFilter,
        resetFeaturedFilter,
        getAssistants,
        handleOptionSelected,
        showInitialMessage,
        hideInitialMessage,
        resetMessages
    };
};
