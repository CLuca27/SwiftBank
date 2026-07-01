"""FastAPI service for Swift AI.

This service receives already-filtered SwiftBank context from the Node.js backend
and uses OpenAI only for the conversational response. It does not authenticate
users and it does not read the database directly.
"""

from __future__ import annotations

from typing import Literal

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from chatbot import ChatbotConfigurationError, SwiftBankChatbot


load_dotenv()

app = FastAPI(title="Swift AI", version="0.1.0")


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1000)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)
    financial_summary: str | None = Field(default=None, max_length=12000)


class ChatResponse(BaseModel):
    answer: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "swift-ai"}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        chatbot = SwiftBankChatbot()
        answer = chatbot.reply(
            user_message=request.message,
            history=[message.model_dump() for message in request.history],
            financial_summary=request.financial_summary,
        )
        return ChatResponse(answer=answer)
    except ChatbotConfigurationError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Swift AI nu a putut genera raspunsul: {exc}") from exc