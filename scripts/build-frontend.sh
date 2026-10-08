#!/usr/bin/env bash
# 프론트를 빌드해 src/main/resources/static으로 복사한다. 이후 ./mvnw package로 jar 하나에 담긴다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATIC="$ROOT/src/main/resources/static"

cd "$ROOT/frontend"
npm ci
npm run build

rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R dist/. "$STATIC/"

echo "복사 완료: $STATIC"
