from typing import Optional

from pydantic import BaseModel


class AssistantSchema(BaseModel):
   name: str
   type: str
   assistant_id: Optional[str] = None
   system_prompt: Optional[str] = None

class QuestionSchema(BaseModel):
    question: str
    type: Optional[str] = None
    assistant_id: Optional[str] = None
    conversation_id: Optional[str] = None
    file_ids: Optional[list[str]] = None
    assistants: Optional[list[AssistantSchema]]
