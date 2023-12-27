import { nightOwl } from "react-syntax-highlighter/dist/cjs/styles/hljs";
import SyntaxHighlighter from "react-syntax-highlighter";
import { useState } from "react";

export const CodeComponent = ({
    node,
    inline,
    className,
    children,
    ...props
}: any) => {
    const [copiedCode, setCopiedCode] = useState(false);

    // Function to copy the code
    const handleCopyCode = (code: string) => {
        navigator.clipboard.writeText(code);
        setCopiedCode(true);
    };

    if (!inline) {
        return (
            <div className="my-2 w-full rounded">
                <div className="font-regular flex items-center justify-end rounded-t-[0.5rem] bg-[#343444] px-2 text-[0.75rem]">
                    <div
                        className="flex cursor-pointer items-center gap-2"
                        onClick={() => handleCopyCode(children)}
                    >
                        <p className="text-white-900 text-xs py-1">
                            {copiedCode ? "¡Texto copiado!" : "Copiar código"}
                        </p>
                    </div>
                </div>
                <SyntaxHighlighter
                    children={String(children).replace(/\n$/, "")}
                    style={nightOwl}
                    language="java"
                    PreTag="div"
                    wrapLongLines={true}
                    customStyle={{
                        borderBottomRightRadius: "0.5rem",
                        borderBottomLeftRadius: "0.5rem",
                        fontSize: "0.75rem",
                    }}
                    {...props}
                />
            </div>
        );
    } else {
        return (
            <p className="py rounded bg-gray-300 px-1 font-medium">
                {children}
            </p>
        );
    }
};
