"""Streamlit UI for Swift AI."""

from __future__ import annotations

import os

import streamlit as st
from dotenv import load_dotenv

from chatbot import ChatbotConfigurationError, SwiftBankChatbot
from swiftbank_api_client import SwiftBankApiClient, SwiftBankApiError
from transaction_analyzer import TransactionAnalyzer
from utils.formatting import mask_token


load_dotenv()

QUICK_QUESTIONS = [
    "Cum trimit bani in SwiftBank?",
    "Cum functioneaza OTP-ul?",
    "Pe ce am cheltuit cel mai mult?",
    "Care este cea mai mare tranzactie?",
    "Cum imi activez cardul virtual?",
    "Cum pot economisi mai mult luna aceasta?",
]


def setup_page() -> None:
    st.set_page_config(
        page_title="Swift AI",
        page_icon="🤖",
        layout="centered",
    )
    st.markdown(
        """
        <style>
            .main .block-container {
                padding-top: 2rem;
                max-width: 920px;
            }
            .swift-card {
                padding: 1rem 1.1rem;
                border-radius: 12px;
                background: rgba(22, 163, 74, 0.08);
                border: 1px solid rgba(22, 163, 74, 0.18);
                margin-bottom: 1rem;
            }
            .small-muted {
                color: #7c7c7c;
                font-size: 0.9rem;
            }
            div[data-testid="stSidebar"] button {
                width: 100%;
                border-radius: 10px;
            }
        </style>
        """,
        unsafe_allow_html=True,
    )


def init_state() -> None:
    st.session_state.setdefault("messages", [])
    st.session_state.setdefault("financial_summary", None)
    st.session_state.setdefault("financial_snapshot_loaded", False)
    st.session_state.setdefault("pending_prompt", None)


def load_financial_context(base_url: str, access_token: str) -> None:
    if not access_token.strip():
        st.warning("Adauga un Bearer access token pentru a incarca datele SwiftBank.")
        return

    with st.spinner("Incarc datele SwiftBank..."):
        client = SwiftBankApiClient(base_url=base_url, access_token=access_token)
        snapshot = client.load_financial_snapshot()
        analyzer = TransactionAnalyzer(
            transactions=snapshot["transactions"],
            accounts=snapshot["accounts"],
            statistics=snapshot.get("statistics", {}),
        )
        st.session_state.financial_summary = analyzer.build_summary_text()
        st.session_state.financial_snapshot_loaded = True


def render_sidebar() -> tuple[str, str]:
    st.sidebar.title("SwiftBank AI")
    st.sidebar.caption("Demo conectat la SwiftBank API")

    base_url = st.sidebar.text_input(
        "SwiftBank API URL",
        value=os.getenv("SWIFTBANK_API_URL", "http://localhost:8080"),
    )
    access_token = st.sidebar.text_input(
        "Bearer access token",
        value=os.getenv("SWIFTBANK_ACCESS_TOKEN", ""),
        type="password",
        help="Pentru demo local. Nu salva token-uri reale in cod sau in repository.",
    )

    st.sidebar.markdown(f"Status date: `{mask_token(access_token)}`")
    if st.sidebar.button("Conecteaza datele SwiftBank"):
        try:
            load_financial_context(base_url, access_token)
            st.sidebar.success("Datele au fost incarcate.")
        except SwiftBankApiError as exc:
            st.sidebar.error(str(exc))

    if st.sidebar.button("Clear Chat"):
        st.session_state.messages = []
        st.session_state.pending_prompt = None
        st.rerun()

    st.sidebar.divider()
    st.sidebar.subheader("Intrebari rapide")
    for question in QUICK_QUESTIONS:
        if st.sidebar.button(question):
            st.session_state.pending_prompt = question
            st.rerun()

    st.sidebar.divider()
    st.sidebar.caption(
        "AI-ul poate explica si analiza date sumarizate. Nu introduce PIN, OTP, CVV sau date complete ale cardului."
    )
    return base_url, access_token


def render_header() -> None:
    st.title("🤖 Swift AI")
    st.markdown(
        """
        <div class="swift-card">
            <b>Asistent educational pentru SwiftBank.</b><br />
            Intreaba despre transferuri, OTP, card virtual, facturi, tranzactii sau buget personal.
        </div>
        """,
        unsafe_allow_html=True,
    )
    if st.session_state.financial_snapshot_loaded:
        st.success("Datele SwiftBank sunt conectate pentru aceasta sesiune.")
    else:
        st.info("Fara token API, raspund doar din cunostinte generale despre SwiftBank.")


def render_history() -> None:
    for message in st.session_state.messages:
        with st.chat_message(message["role"]):
            st.markdown(message["content"])


def generate_reply(prompt: str) -> None:
    st.session_state.messages.append({"role": "user", "content": prompt})
    with st.chat_message("user"):
        st.markdown(prompt)

    with st.chat_message("assistant"):
        with st.spinner("AI-ul raspunde..."):
            try:
                chatbot = SwiftBankChatbot()
                # Exclude the current user message from conversation history; it is
                # passed explicitly as user_message.
                history = st.session_state.messages[:-1]
                reply = chatbot.reply(
                    user_message=prompt,
                    history=history,
                    financial_summary=st.session_state.financial_summary,
                )
            except ChatbotConfigurationError as exc:
                reply = f"Configurare lipsa: {exc}"
            except Exception as exc:  # Defensive UI boundary for demo mode.
                reply = f"Nu am putut genera raspunsul acum: {exc}"

            st.markdown(reply)
            st.session_state.messages.append({"role": "assistant", "content": reply})


def main() -> None:
    setup_page()
    init_state()
    render_sidebar()
    render_header()
    render_history()

    pending_prompt = st.session_state.pop("pending_prompt", None)
    prompt = pending_prompt or st.chat_input("Scrie o intrebare pentru SwiftBank AI...")
    if prompt:
        generate_reply(prompt)


if __name__ == "__main__":
    main()