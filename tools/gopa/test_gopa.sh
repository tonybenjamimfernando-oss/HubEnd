#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo '=== GoPA v2 tests ==='
gofmt -w main.go main_test.go
go test -race -count=1 ./...
if command -v docker >/dev/null 2>&1; then
  GOPA_API_TOKEN="${GOPA_API_TOKEN:-0123456789abcdef0123456789abcdef}" docker compose config >/dev/null
  echo 'Compose configuration: valid'
else
  echo 'Docker unavailable: deployment smoke test skipped'
fi
