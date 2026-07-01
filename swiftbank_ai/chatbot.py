"""OpenAI-powered chat layer for SwiftBank AI Assistant."""

from __future__ import annotations

import os
from typing import Any

from openai import OpenAI

from prompts import SYSTEM_PROMPT, build_user_context


class ChatbotConfigurationError(RuntimeError):
    """Raised when the assistant is not configured correctly."""


class SwiftBankChatbot:
    """Thin wrapper around the OpenAI client.

    The class receives already-sanitized context. It does not call the database
    or the SwiftBank API directly; that keeps data loading separate from text
    generation and makes the module easier to integrate later.
    """

    def __init__(self, model: str | None = None) -> None:
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise ChatbotConfigurationError("OPENAI_API_KEY lipseste din .env sau din variabilele de mediu.")
        self.client = OpenAI(api_key=api_key)
        self.model = model or os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    def build_messages(
        self,
        user_message: str,
        history: list[dict[str, str]],
        financial_summary: str | None,
    ) -> list[dict[str, str]]:
        messages: list[dict[str, str]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "system", "content": build_user_context(financial_summary)},
        ]
        # Keep the last messages only. Streamlit stores the full session history,
        # but the model does not need the entire conversation for a small demo.
        messages.extend(history[-10:])
        messages.append({"role": "user", "content": user_message})
        return messages

    def reply(
        self,
        user_message: str,
        history: list[dict[str, str]],
        financial_summary: str | None = None,
    ) -> str:
        messages = self.build_messages(user_message, history, financial_summary)
        response = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=0.35,
        )
        content = response.choices[0].message.content
        return content or "Nu am putut genera un raspuns acum."