from typing import Optional, Union

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

class AssistantSpecs(BaseModel):
    name: str
    type: str
    spec: str

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
    temperature: Optional[float] = 1
    description: Optional[str] = None
    kb_vectordb_id: Optional[str] = None
    specs: Optional[list[AssistantSpecs]] = None


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
    system_prompt: Optional[str] = None


class VectorDBInputSchema(BaseModel):
    kb_vectordb_id: str


class TextToVectorDBSchema(VectorDBInputSchema):
    text: Union[str, bytes]
    overwrite: bool = False
    extension: str
