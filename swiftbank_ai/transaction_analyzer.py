"""Deterministic transaction analysis for SwiftBank AI Assistant.

No machine learning is used here. The analyzer computes simple, explainable
aggregations from transactions already returned by the SwiftBank API.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import pandas as pd


EXPENSE_TYPES = {"CARD", "BILL", "TRANSFER", "CARD_PENDING_APPROVAL"}


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value or default)
    except (TypeError, ValueError):
        return default


def _pick_category(row: pd.Series) -> str:
    for key in ("category_name", "biller_category", "subtitle", "transaction_type"):
        value = row.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return "Necategorizat"


def _pick_merchant(row: pd.Series) -> str:
    for key in ("merchant_name", "biller_name", "title", "description"):
        value = row.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return "Tranzactie"


@dataclass
class TransactionAnalyzer:
    transactions: list[dict[str, Any]]
    accounts: list[dict[str, Any]] | None = None
    statistics: dict[str, Any] | None = None

    def _frame(self) -> pd.DataFrame:
        if not self.transactions:
            return pd.DataFrame()

        df = pd.DataFrame(self.transactions).copy()
        if "amount" not in df.columns:
            df["amount"] = 0
        df["amount"] = df["amount"].apply(_safe_float)
        df["expense_amount"] = df["amount"].apply(lambda value: abs(value) if value < 0 else 0)
        df["income_amount"] = df["amount"].apply(lambda value: value if value > 0 else 0)
        df["category"] = df.apply(_pick_category, axis=1)
        df["merchant"] = df.apply(_pick_merchant, axis=1)
        df["currency"] = df.get("currency", df.get("account_currency", "RON"))
        if "created_at" in df.columns:
            df["created_at"] = pd.to_datetime(df["created_at"], errors="coerce")
        return df

    def total_balance_by_currency(self) -> dict[str, float]:
        totals: dict[str, float] = {}
        for account in self.accounts or []:
            currency = str(account.get("currency", "RON")).strip()
            balance = _safe_float(account.get("balance") or account.get("available_balance"))
            totals[currency] = totals.get(currency, 0.0) + balance
        return totals

    def spending_by_category(self, top_n: int = 6) -> list[dict[str, Any]]:
        df = self._frame()
        if df.empty:
            return []
        expenses = df[df["expense_amount"] > 0]
        if expenses.empty:
            return []
        grouped = (
            expenses.groupby(["category", "currency"], dropna=False)["expense_amount"]
            .sum()
            .reset_index()
            .sort_values("expense_amount", ascending=False)
            .head(top_n)
        )
        return grouped.to_dict(orient="records")

    def top_merchants(self, top_n: int = 5) -> list[dict[str, Any]]:
        df = self._frame()
        if df.empty:
            return []
        expenses = df[df["expense_amount"] > 0]
        if expenses.empty:
            return []
        grouped = (
            expenses.groupby(["merchant", "currency"], dropna=False)["expense_amount"]
            .sum()
            .reset_index()
            .sort_values("expense_amount", ascending=False)
            .head(top_n)
        )
        return grouped.to_dict(orient="records")

    def biggest_transaction(self) -> dict[str, Any] | None:
        df = self._frame()
        if df.empty:
            return None
        expenses = df[df["expense_amount"] > 0]
        if expenses.empty:
            return None
        row = expenses.sort_values("expense_amount", ascending=False).iloc[0]
        return {
            "merchant": row.get("merchant"),
            "category": row.get("category"),
            "amount": float(row.get("expense_amount", 0)),
            "currency": row.get("currency", "RON"),
            "date": str(row.get("created_at")) if "created_at" in row else "",
        }

    def recent_transactions(self, limit: int = 8) -> list[dict[str, Any]]:
        df = self._frame()
        if df.empty:
            return []
        if "created_at" in df.columns:
            df = df.sort_values("created_at", ascending=False)
        return [
            {
                "merchant": row.get("merchant"),
                "category": row.get("category"),
                "amount": float(row.get("amount", 0)),
                "currency": row.get("currency", "RON"),
                "type": row.get("transaction_type", ""),
            }
            for _, row in df.head(limit).iterrows()
        ]

    def savings_recommendations(self) -> list[str]:
        categories = self.spending_by_category(top_n=3)
        merchants = self.top_merchants(top_n=3)
        tips: list[str] = []

        if categories:
            top = categories[0]
            tips.append(
                f"Categoria principala de cheltuieli este {top['category']} "
                f"({top['expense_amount']:.2f} {top['currency']}). Verifica daca exista cheltuieli recurente aici."
            )
        if merchants:
            top = merchants[0]
            tips.append(
                f"Cel mai mare total pe comerciant este la {top['merchant']} "
                f"({top['expense_amount']:.2f} {top['currency']})."
            )
        tips.append("Seteaza o limita lunara pentru 1-2 categorii flexibile, nu pentru toate deodata.")
        tips.append("Pastreaza recomandarile ca orientare generala, nu ca sfat financiar profesional.")
        return tips

    def build_summary_text(self) -> str:
        balances = self.total_balance_by_currency()
        categories = self.spending_by_category()
        merchants = self.top_merchants()
        biggest = self.biggest_transaction()
        recent = self.recent_transactions()
        tips = self.savings_recommendations()

        lines: list[str] = []
        lines.append("SwiftBank financial snapshot (sanitized):")
        lines.append(f"- Accounts loaded: {len(self.accounts or [])}")
        lines.append(f"- Transactions loaded: {len(self.transactions)}")

        if balances:
            rendered = ", ".join(f"{currency}: {amount:.2f}" for currency, amount in balances.items())
            lines.append(f"- Available balances by currency: {rendered}")

        if categories:
            lines.append("- Top spending categories:")
            for item in categories:
                lines.append(f"  * {item['category']}: {item['expense_amount']:.2f} {item['currency']}")

        if merchants:
            lines.append("- Top merchants/billers:")
            for item in merchants:
                lines.append(f"  * {item['merchant']}: {item['expense_amount']:.2f} {item['currency']}")

        if biggest:
            lines.append(
                "- Biggest expense: "
                f"{biggest['merchant']} / {biggest['category']} / "
                f"{biggest['amount']:.2f} {biggest['currency']}"
            )

        if recent:
            lines.append("- Recent transactions sample:")
            for item in recent:
                lines.append(
                    f"  * {item['merchant']} ({item['category']}): "
                    f"{item['amount']:.2f} {item['currency']} [{item['type']}]"
                )

        if tips:
            lines.append("- Deterministic budgeting notes:")
            for tip in tips:
                lines.append(f"  * {tip}")

        return "\n".join(lines)