from typing import Optional

from pydantic import BaseModel


class QuestionSchema(BaseModel):
    question: str
    type: Optional[str] = None
    assistant_id: Optional[str] = None
    conversation_id: Optional[str] = None
    file_ids: Optional[list[str]] = None
    extra_info: Optional[dict] = None
