#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8000}"
TOKEN="${APP_ACCESS_TOKEN:-change-this-long-random-token}"
curl -fsS "$BASE_URL/health" | python3 -m json.tool
