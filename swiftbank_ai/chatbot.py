"""OpenAI-powered chat layer for SwiftBank AI Assistant."""

from __future__ import annotations

import os
import re
import unicodedata
from typing import Any

from openai import OpenAI, OpenAIError

from prompts import SYSTEM_PROMPT, build_user_context


STRICT_CONTEXT_MONTH_WORDS = (
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
    "luna asta", "luna aceasta", "aceasta luna", "in aceasta luna", "in luna aceasta", "luna curenta", "luna in curs", "luna trecuta", "luna anterioara",
)


def normalize_intent_text(value: str) -> str:
    normalized = unicodedata.normalize("NFD", value or "")
    normalized = "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")
    return re.sub(r"\s+", " ", normalized.lower()).strip()


def should_ignore_history_for_strict_financial_question(user_message: str, financial_summary: str | None) -> bool:
    """Avoid letting older chat turns override fresh period-specific facts."""
    if not financial_summary:
        return False

    text = normalize_intent_text(user_message)
    has_period = any(word in text for word in STRICT_CONTEXT_MONTH_WORDS)
    asks_largest = bool(re.search(r"(cea mai mare|cele mai mari|maxim|maxima|top)", text))
    asks_financial_data = bool(re.search(r"(tranzact|cheltu|transfer|plata|plati|card|factur|comerciant|valuta|ron|eur|usd)", text))
    return asks_financial_data and (has_period or asks_largest)


INTERNAL_LABEL_REPLACEMENTS: tuple[tuple[str, str], ...] = (
    (r'["`]*\bSELF_IN\b["`]*', "intrarea dintr-un schimb valutar"),
    (r'["`]*\bSELF_OUT\b["`]*', "ie\u0219irea dintr-un schimb valutar"),
    (r'["`]*\bPENDING\b["`]*', "\u00een a\u0219teptare"),
    (r'["`]*\bCOMPLETED\b["`]*', "finalizat\u0103"),
    (r'["`]*\bFAILED\b["`]*', "e\u0219uat\u0103"),
    (r'["`]*\bCANCELLED\b["`]*', "anulat\u0103"),
    (r'["`]*\bCANCELED\b["`]*', "anulat\u0103"),
    (r'["`]*\bTRANSFER_IN\b["`]*', "transfer primit"),
    (r'["`]*\bTRANSFER_OUT\b["`]*', "transfer trimis"),
    (r'["`]*\bCARD_PENDING_APPROVAL\b["`]*', "plat\u0103 cu cardul \u00een a\u0219teptare"),
    (r'["`]*\bBILL\b["`]*', "plat\u0103 factur\u0103"),
    (r'["`]*\bCARD\b["`]*', "plat\u0103 cu cardul"),
)


def tidy_assistant_formatting(text: str) -> str:
    """Make compact model answers easier to read inside a narrow chat bubble."""
    cleaned = text.strip()

    # GPT sometimes returns "intro: - item - item" on one line. The Android
    # TextView can render newlines nicely, so normalize those compact lists.
    cleaned = re.sub(r":\s+-\s+", ":\n- ", cleaned)
    cleaned = re.sub(
        r"(?<!\n)\s+-\s+(?=(?:\d{4}-\d{2}-\d{2}|[A-Z]))",
        "\n- ",
        cleaned,
    )
    cleaned = re.sub(r"(?<!\n)\s+(\d+\.\s+)", r"\n\1", cleaned)

    # Keep the common closing/help sentence away from the last bullet.
    cleaned = re.sub(
        r"\s+(Dac\u0103 ai nevoie|Dac\u0103 vrei|Spune-mi|Pot s\u0103)",
        r"\n\n\1",
        cleaned,
    )

    # Avoid excessive vertical whitespace if the model already formatted well.
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def sanitize_assistant_output(text: str) -> str:
    """Hide internal backend labels and normalize chat-bubble formatting."""
    cleaned = text
    for pattern, replacement in INTERNAL_LABEL_REPLACEMENTS:
        cleaned = re.sub(pattern, replacement, cleaned)
    return tidy_assistant_formatting(cleaned)


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
        self.model = model or os.getenv("OPENAI_MODEL", "gpt-5.5")
        self.fallback_model = os.getenv("OPENAI_FALLBACK_MODEL", "gpt-4o")

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
        # For explicit factual questions, the fresh backend context is safer
        # than older chat turns that may mention another period or merchant.
        if should_ignore_history_for_strict_financial_question(user_message, financial_summary):
            relevant_history: list[dict[str, str]] = []
        else:
            relevant_history = history[-10:]

        messages.extend(relevant_history)
        messages.append({"role": "user", "content": user_message})
        return messages

    def _create_completion(self, messages: list[dict[str, str]]):
        params: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
        }
        if not self.model.lower().startswith("gpt-5"):
            params["temperature"] = 0
        return self.client.chat.completions.create(**params)

    def reply(
        self,
        user_message: str,
        history: list[dict[str, str]],
        financial_summary: str | None = None,
    ) -> str:
        messages = self.build_messages(user_message, history, financial_summary)
        try:
            response = self._create_completion(messages)
        except OpenAIError:
            if not self.fallback_model or self.fallback_model == self.model:
                raise
            self.model = self.fallback_model
            response = self._create_completion(messages)

        content = response.choices[0].message.content
        return sanitize_assistant_output(content or "Nu am putut genera un raspuns acum.")