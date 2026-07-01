"""Static SwiftBank product knowledge used by the AI assistant.

This module intentionally contains only public/product information. Sensitive
user data is loaded separately through the SwiftBank API and summarized before
it is sent to the language model.
"""

SWIFTBANK_KNOWLEDGE = {
    "otp": (
        "SwiftBank uses OTP codes during registration and account recovery. "
        "OTP codes are short-lived and should never be shared with anyone."
    ),
    "accounts": (
        "Users can manage current accounts in multiple currencies, view balances, "
        "and create supported additional currency accounts."
    ),
    "transfers": (
        "Users can send money to beneficiaries, validate IBAN details, and review "
        "transfer history from the transactions screen."
    ),
    "bill_payments": (
        "SwiftBank supports bill payments through saved billers and bill categories. "
        "Payments are visible in the transaction history after completion."
    ),
    "virtual_card": (
        "Users can create and manage a virtual card, view protected card details, "
        "temporarily block the card, and delete it when needed."
    ),
    "notifications": (
        "SwiftBank sends notifications for important account events such as transfers, "
        "card payment approvals, bill payments, and session revocation."
    ),
    "transactions": (
        "The transaction history shows card payments, transfers, bill payments, "
        "amounts, dates, statuses, merchants, categories, and account currency."
    ),
    "spending_analysis": (
        "SwiftBank can analyze spending by category, merchant, period, and account. "
        "The assistant can provide simple budgeting suggestions, but not professional "
        "financial or investment advice."
    ),
    "security": (
        "Sensitive actions are protected with PIN, OTP, biometrics, access tokens, "
        "refresh tokens, and single-device session checks. Users should contact the "
        "bank if they notice unknown transactions or unexpected account activity."
    ),
}


def format_knowledge() -> str:
    """Return a compact text block that can be injected into the system prompt."""
    lines = ["SwiftBank product knowledge:"]
    for key, value in SWIFTBANK_KNOWLEDGE.items():
        lines.append(f"- {key}: {value}")
    return "\n".join(lines)