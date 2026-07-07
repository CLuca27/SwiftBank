# Swift AI

Swift AI este modulul AI pentru SwiftBank. Are doua moduri de rulare:

- FastAPI service (`api.py`) pentru integrarea reala cu backend-ul Node.js;
- Streamlit demo (`app.py`) pentru prezentare locala si testare rapida.

In integrarea recomandata, aplicatia Android nu vorbeste direct cu OpenAI si nu trimite cheia API. Android trimite mesajul catre backend-ul SwiftBank, backend-ul valideaza access token-ul existent, construieste un context financiar filtrat, apoi cheama serviciul Python Swift AI.

## Flow recomandat

```text
Android app
  -> POST /api/ai/chat
  -> Node.js auth middleware valideaza Bearer token-ul
  -> Node.js construieste sumar financiar sigur
  -> Python FastAPI /chat
  -> OpenAI
  -> raspuns inapoi in Android
```

## Ce primeste AI-ul

AI-ul nu primeste date brute din baza de date. Backend-ul Node trimite doar un sumar filtrat, de exemplu:

- solduri disponibile pe valuta;
- venituri si cheltuieli agregate;
- top categorii de cheltuieli;
- top comercianti;
- cea mai mare cheltuiala recenta;
- cateva tranzactii recente sumarizate.

Nu se trimit PIN, OTP, CVV, parole, access token, refresh token, device_id, IBAN complet sau numar complet de card.

## Structura proiectului

```text
swiftbank_ai/
|-- api.py
|-- app.py
|-- chatbot.py
|-- knowledge.py
|-- prompts.py
|-- swiftbank_api_client.py
|-- transaction_analyzer.py
|-- requirements.txt
|-- README.md
|-- .env.example
`-- utils/
    |-- __init__.py
    `-- formatting.py
```

## Instalare

Din directorul `swiftbank_ai`:

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

Pe macOS/Linux:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Configurare Python `.env`

Copiaza `.env.example` in `.env`:

```bash
copy .env .env
```

Completeaza:

```env
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-5.5
OPENAI_FALLBACK_MODEL=gpt-4o
```

Nu urca `.env` in Git.

## Rulare ca serviciu pentru Node.js

Porneste serviciul Python:

```bash
uvicorn api:app --host 0.0.0.0 --port 8000
```

Verificare:

```text
GET http://localhost:8000/health
```

In `.env` pentru backend-ul Node.js adauga:

```env
SWIFT_AI_SERVICE_URL=http://127.0.0.1:8000
SWIFT_AI_TIMEOUT_MS=30000
```

Endpoint-ul expus de SwiftBank backend este:

```text
POST /api/ai/chat
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "message": "Pe ce am cheltuit cel mai mult luna asta?",
  "history": []
}
```

Raspuns:

```json
{
  "success": true,
  "data": {
    "answer": "...",
    "contextConnected": true
  }
}
```

## Rulare demo Streamlit

Pentru demo local:

```bash
streamlit run app.py
```

Streamlit poate folosi un Bearer access token introdus manual pentru testare, dar integrarea reala trebuie sa treaca prin backend-ul Node.js ca sa nu fie expuse token-uri sau cheia OpenAI.

## Intrebari sugerate

- Cum trimit bani in SwiftBank?
- Cum functioneaza OTP-ul?
- Pe ce am cheltuit cel mai mult?
- Care este cea mai mare tranzactie?
- Cum imi activez cardul virtual?
- Cum pot economisi mai mult luna aceasta?


## Comportament Swift AI

Swift AI foloseste reguli explicite pentru doua zone sensibile:

- date sensibile: refuza sa afiseze CVV, PIN, OTP, token-uri, parole sau numere complete de card, apoi ghideaza utilizatorul catre fluxul securizat din aplicatie;
- recomandari financiare: ofera doar observatii si sugestii generale de bugetare, bazate pe sumarul filtrat, fara investitii, credite, produse financiare sau consultanta profesionala.

Pentru raspunsuri financiare, modelul recomandat este:

```text
observa -> compara -> sugereaza
```

Exemplu: „Ai cheltuit mai mult pe restaurante luna aceasta. Fata de luna trecuta, categoria pare mai ridicata. Poti incerca o limita saptamanala pentru iesiri in oras. Aceasta este o recomandare generala de bugetare, nu consultanta financiara profesionala.”

## Note de securitate

Swift AI este educational si ofera explicatii si recomandari generale de bugetare. Nu executa operatiuni bancare, nu muta bani, nu blocheaza carduri si nu schimba PIN-uri. Pentru tranzactii necunoscute, suspiciuni de frauda, acces blocat sau probleme reale de cont, utilizatorul trebuie directionat catre banca sau suport.