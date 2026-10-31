#!/usr/bin/env bash
# =============================================================================================
# Checks a running UDAV in about a minute, with nothing but bash and curl:
#
#   tools/smoke-test.sh                      against http://localhost:8080 (the Docker stack)
#   tools/smoke-test.sh http://host:8081     against another instance
#
# It waits for the application, for the bundled pipelines and their generator data, then reads
# a widget's data through the data API, exports a whole pipeline through the batch API and
# converts an SVG to TikZ. Exit code 0 means every step passed.
# =============================================================================================
set -u

URL="${1:-http://localhost:8080}"
PIPELINE="e0000001-0000-4000-8000-000000000001"   # evaluation pipeline P1, imported at startup
GENERATOR="MapCoordinates-eval-points"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILED=0

step() { printf '%-52s' "$1"; }
ok()   { echo "ok${1:+ ($1)}"; }
fail() { echo "FAILED${1:+: $1}"; FAILED=1; }

step "application answers at $URL"
for i in $(seq 1 60); do
  curl -fsS -m 5 "$URL/actuator/health" 2>/dev/null | grep -q '"UP"' && break
  sleep 3
done
if curl -fsS -m 5 "$URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then ok; else fail "not healthy after 3 minutes"; exit 1; fi

step "bundled pipelines are imported"
for i in $(seq 1 60); do
  curl -fsS -m 10 -o /dev/null "$URL/api/pipelines/$PIPELINE" 2>/dev/null && break
  sleep 3
done
count=$(curl -fsS -m 10 "$URL/api/pipelines?size=100" 2>/dev/null | grep -o '"id"' | wc -l | tr -d ' ')
if [[ "${count:-0}" -ge 1 ]]; then ok "$count pipelines"; else fail "pipeline list is empty"; fi

step "generator data of pipeline P1 is built"
for i in $(seq 1 60); do
  body=$(curl -fsS -m 20 -X POST "$URL/api/data?pipelineId=$PIPELINE&generatorId=$GENERATOR&chartType=VoronoiDiagram&page=1&size=1" \
           -H 'Content-Type: application/json' -d '{"corpus":{},"chart":{}}' 2>/dev/null)
  echo "$body" | grep -q '"x"' && break
  sleep 3
done
if echo "${body:-}" | grep -q '"x"'; then ok; else fail "no data after 3 minutes: ${body:0:120}"; fi

step "a corpus filter that matches nothing is handled"
code=$(curl -sS -m 20 -o "$TMP/filtered.json" -w '%{http_code}' -X POST "$URL/api/data?pipelineId=$PIPELINE&generatorId=CategoryNumber-eval-annotations&chartType=BarChart&page=1&size=1" \
         -H 'Content-Type: application/json' -d '{"corpus":{"files":[],"tags":[],"dateFrom":"2030-01-01","dateTo":""},"chart":{}}')
if [[ "$code" == "200" ]] && grep -q '"data"' "$TMP/filtered.json"; then ok; else fail "HTTP $code"; fi

step "batch API exports the whole pipeline as SVG"
code=$(curl -sS -m 120 -o "$TMP/export.zip" -w '%{http_code}' "$URL/api/batch/export/pipeline/$PIPELINE/svg")
if [[ "$code" == "200" ]] && [[ "$(head -c 2 "$TMP/export.zip")" == "PK" ]]; then
  if command -v unzip >/dev/null 2>&1; then
    entries=$(unzip -l "$TMP/export.zip" | grep -c '\.svg$')
    ok "ZIP with $entries SVG files"
  else
    ok "ZIP of $(wc -c < "$TMP/export.zip" | tr -d ' ') bytes"
  fi
else
  fail "HTTP $code"
fi

step "SVG to TikZ conversion"
tex=$(curl -fsS -m 30 -X POST "$URL/api/conversions/tikz" -H 'Content-Type: application/json' \
        -d '{"type":"BarChart","svg":"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\"><rect x=\"10\" y=\"10\" width=\"30\" height=\"20\" fill=\"red\"/></svg>","data":[],"meta":{}}' 2>/dev/null)
if echo "$tex" | grep -q 'tikzpicture'; then ok; else fail "no TikZ in the answer"; fi

echo
if [[ $FAILED -eq 0 ]]; then echo "UDAV works. Open $URL in a browser."; else echo "Some steps failed; see the container log: docker compose logs udav"; exit 1; fi
