from typing import Optional

from pydantic import BaseModel


class QuestionSchema(BaseModel):
    question: str
    assistant_id: Optional[str] = None
    conversation_id: Optional[str] = None
