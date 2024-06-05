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

class AssistantSchema(BaseModel):
   name: str
   type: str
   assistant_id: Optional[str] = None
   system_prompt: Optional[str] = None

class AssistantStage(BaseModel):
    name: str
    assistants: list[str]

class AssistantGraph(BaseModel):
    stages: list[AssistantStage]

class QuestionSchema(BaseModel):
    question: str
    type: Optional[str] = None
    assistant_id: Optional[str] = None
    conversation_id: Optional[str] = None
    file_ids: Optional[list[str]] = None
    local_file_ids: Optional[list[str]] = None
    extra_info: Optional[dict] = None
    provider: Optional[str] = None
    model: Optional[str] = None
    system_prompt: Optional[str] = None
    history: Optional[list[MessageSchema]] = None
    tools: Optional[list[ToolSchema]] = None

class GraphQuestionSchema(QuestionSchema):
    assistants: Optional[list[AssistantSchema]] = None
    graph: Optional[AssistantGraph] = None
