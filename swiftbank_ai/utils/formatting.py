"""Formatting helpers for the Streamlit interface."""

from __future__ import annotations


def mask_token(token: str | None) -> str:
    if not token:
        return "neconectat"
    token = token.strip()
    if len(token) <= 12:
        return "token setat"
    return f"{token[:6]}...{token[-6:]}"


def money(value: float, currency: str = "RON") -> str:
    return f"{value:,.2f} {currency}".replace(",", " ")