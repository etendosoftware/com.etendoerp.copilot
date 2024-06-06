from langchain_core.messages import HumanMessage, AIMessage


class MemoryHandler:
    def get_memory(self, history, question) -> list[HumanMessage | AIMessage]:
        messages = []
        for message in history:
            if message.role == "USER":
                messages.append(HumanMessage(content=message.content))
            elif message.role == "ASSISTANT":
                messages.append(AIMessage(content=message.content))
        if question:
            messages.append(HumanMessage(content=question))
        return messages
