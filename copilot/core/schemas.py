from typing import Optional

from pydantic import BaseModel


class MessageSchema(BaseModel):
    content: str
    role: str


class FunctionSchema(BaseModel):
    name: str


class ToolSchema(BaseModel):
    type: str
    function: FunctionSchema


class AssistantStage(BaseModel):
    name: str
    assistants: list[str]


class AssistantGraph(BaseModel):
    stages: list[AssistantStage]


class AssistantSchema(BaseModel):
    name: Optional[str] = None
    type: Optional[str] = None
    assistant_id: Optional[str] = None
    file_ids: Optional[list[str]] = None
    local_file_ids: Optional[list[str]] = None
    provider: Optional[str] = None
    model: Optional[str] = None
    system_prompt: Optional[str] = None
    tools: Optional[list[ToolSchema]] = None
    temperature: Optional[float] = None
    description: Optional[str] = None


class QuestionSchema(AssistantSchema):
    question: str
    conversation_id: Optional[str] = None
    history: Optional[list[MessageSchema]] = None
    extra_info: Optional[dict] = None

class QuestionResponseSchema(BaseModel):
    answer: str

class GraphQuestionSchema(BaseModel):
    question: str
    conversation_id: Optional[str] = None
    history: Optional[list[MessageSchema]] = None
    assistants: Optional[list[AssistantSchema]] = None
    graph: Optional[AssistantGraph] = None
    extra_info: Optional[dict] = None
    generate_image: Optional[bool] = False
    local_file_ids: Optional[list[str]] = None
    temperature: Optional[float] = None
