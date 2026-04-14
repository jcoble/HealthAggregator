#!/bin/bash
set -e

cd "$(dirname "$0")/.."

pkill -f "dotnet.*HealthAggregator.Api" 2>/dev/null || true
pkill -f "HealthAggregator/healthaggregator-web.*vite" 2>/dev/null || true
API_PORT="${HEALTHAGGREGATOR_API_PORT:-5310}"
WEB_PORT="${HEALTHAGGREGATOR_WEB_PORT:-5373}"
EPIC_CALLBACK_PORT="${HEALTHAGGREGATOR_EPIC_CALLBACK_PORT:-5010}"

for PORT in "$API_PORT" "$WEB_PORT" "$EPIC_CALLBACK_PORT" 5010 5173; do
  lsof -ti:"$PORT" | xargs kill -9 2>/dev/null || true
done

export ASPNETCORE_ENVIRONMENT=Development
export DOTNET_ENVIRONMENT=Development
export Epic__CallbackBaseUrl="https://localhost:$EPIC_CALLBACK_PORT"

API_URLS="https://localhost:$API_PORT"
if [ "$EPIC_CALLBACK_PORT" != "$API_PORT" ]; then
  API_URLS="$API_URLS;https://localhost:$EPIC_CALLBACK_PORT"
fi

dotnet run --project HealthAggregator.Api --urls "$API_URLS" > /tmp/healthaggregator-api.log 2>&1 &
API_PID=$!

trap "kill $API_PID 2>/dev/null || true; exit 0" INT TERM

cd healthaggregator-web
VITE_API_BASE_URL=https://localhost:$API_PORT pnpm dev --host 127.0.0.1 --port "$WEB_PORT"
