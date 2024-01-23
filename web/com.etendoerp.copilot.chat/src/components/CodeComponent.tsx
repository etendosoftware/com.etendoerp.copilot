import { nightOwl } from "react-syntax-highlighter/dist/cjs/styles/hljs";
import SyntaxHighlighter from "react-syntax-highlighter";

export const CodeComponent = ({
    node,
    inline,
    className,
    children,
    ...props
}: any) => {
    if (!inline) {
        return (
            <div className="my-2 w-full rounded">
                <SyntaxHighlighter
                    children={String(children).replace(/\n$/, "")}
                    style={nightOwl}
                    language="java"
                    PreTag="div"
                    wrapLongLines={true}
                    customStyle={{
                        borderRadius: "0.25rem",
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
