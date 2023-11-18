from typing import Optional

from pydantic import BaseModel

ASSISTANT_ID = "asst_xTVuJdin4ipd05utqcuFlKmI"


class QuestionSchema(BaseModel):
    question: str
    assistant_id: Optional[str] = ASSISTANT_ID
    conversation_id: Optional[str] = None
