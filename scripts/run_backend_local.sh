#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../backend"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created backend/.env. Edit OPENAI_API_KEY and APP_ACCESS_TOKEN before real use."
fi
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
