"""Small REST client for the existing SwiftBank Node.js API.

The AI service does not log in by itself. It receives a short-lived Bearer token
from the user/session and calls the same protected endpoints used by Android.
This avoids creating a separate login session that could revoke the mobile app
session in the current single-device architecture.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import requests


class SwiftBankApiError(RuntimeError):
    """Raised when the SwiftBank API returns an error or cannot be reached."""


@dataclass
class SwiftBankApiClient:
    base_url: str
    access_token: str
    timeout: int = 12

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")
        self.access_token = self.access_token.strip()

    @property
    def headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.access_token}",
            "Accept": "application/json",
        }

    def _get(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        url = f"{self.base_url}{path}"
        try:
            response = requests.get(url, headers=self.headers, params=params, timeout=self.timeout)
        except requests.RequestException as exc:
            raise SwiftBankApiError(f"Nu pot contacta SwiftBank API: {exc}") from exc

        if not response.ok:
            try:
                payload = response.json()
                message = payload.get("error", {}).get("message") or payload.get("message")
            except ValueError:
                message = response.text
            raise SwiftBankApiError(f"SwiftBank API error {response.status_code}: {message}")

        try:
            payload = response.json()
        except ValueError as exc:
            raise SwiftBankApiError("SwiftBank API a returnat un raspuns invalid JSON.") from exc

        if payload.get("success") is False:
            message = payload.get("error", {}).get("message") or payload.get("message") or "Cerere esuata"
            raise SwiftBankApiError(message)

        return payload.get("data", payload)

    def get_accounts(self) -> list[dict[str, Any]]:
        data = self._get("/api/accounts")
        return data.get("accounts", [])

    def get_transactions_for_account(self, account_id: int, limit: int = 100) -> list[dict[str, Any]]:
        data = self._get(
            "/api/transactions",
            params={"account_id": account_id, "limit": min(limit, 100), "offset": 0},
        )
        return data.get("transactions", [])

    def get_all_transactions(self, limit_per_account: int = 100) -> list[dict[str, Any]]:
        transactions: list[dict[str, Any]] = []
        for account in self.get_accounts():
            account_id = account.get("account_id")
            if account_id is None:
                continue
            account_transactions = self.get_transactions_for_account(int(account_id), limit_per_account)
            for transaction in account_transactions:
                transaction.setdefault("account_currency", account.get("currency"))
                transaction.setdefault("account_id", account_id)
            transactions.extend(account_transactions)
        return transactions

    def get_statistics(self, period: str = "this_month") -> dict[str, Any]:
        return self._get("/api/statistics", params={"period": period})

    def load_financial_snapshot(self) -> dict[str, Any]:
        """Load the minimum useful context for the assistant."""
        accounts = self.get_accounts()
        transactions = self.get_all_transactions(limit_per_account=100)
        try:
            statistics = self.get_statistics("this_month")
        except SwiftBankApiError:
            statistics = {}

        return {
            "accounts": accounts,
            "transactions": transactions,
            "statistics": statistics,
        }