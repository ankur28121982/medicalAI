#!/usr/bin/env bash
set -euo pipefail
TOKEN="$(cat "$HOME/medical_report_ai_token.txt")"
echo "WSL health:"
curl -s http://127.0.0.1:8000/health | python3 -m json.tool
echo "WSL auth history test:"
curl -s -H "x-app-token: $TOKEN" "http://127.0.0.1:8000/v1/report/history?device_id=test-device-001" | python3 -m json.tool
