from pydantic import BaseModel, Field


class ScoringRequest(BaseModel):
    pan_hash: str
    amount_inr: float
    mcc: str
    network: str
    method: str
    hour_of_day: int = Field(ge=0, le=23)


class ScoringResponse(BaseModel):
    score: float = Field(ge=0.0, le=1.0)
    reasoning: str
