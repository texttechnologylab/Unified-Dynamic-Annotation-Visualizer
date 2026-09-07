#!/usr/bin/env bash
# =============================================================================================
# Batch export evaluation - one command.
#
#   tools/run-evaluation.sh                  full campaign against a running UDAV (hours)
#   tools/run-evaluation.sh --smoke          one run per condition, correctness only (minutes)
#   tools/run-evaluation.sh --docker         start UDAV + Postgres with the evaluation fixtures
#                                            first (docker compose, on port 18080), then run against it
#   tools/run-evaluation.sh --url http://host:8080 --report my-report.txt
#
# Anything else on the command line is handed to Maven, e.g. the harness knobs
#   -Dudav.eval.analysisRuns=5 -Dudav.eval.steadyWindow=5 -Dudav.eval.pipelines=P1,P5
#
# What it needs: JDK 21 + Maven (the harness is a JUnit class), bash and curl (Git Bash or WSL
# on Windows), and a UDAV instance started with EXPORT_METRICS=true that has imported the bundled
# pipeline_eval-*.json pipelines (the Docker image and a source-tree start both do). The report
# is written to --report (default evaluation-report.txt); its first section says whether the
# numbers are usable.
# =============================================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
URL=""
REPORT="evaluation-report.txt"
SMOKE=0
DOCKER=0
EXTRA=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --smoke)   SMOKE=1; shift ;;
    --docker)  DOCKER=1; shift ;;
    --url)     URL="$2"; shift 2 ;;
    --report)  REPORT="$2"; shift 2 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *)         EXTRA+=("$1"); shift ;;
  esac
done

cd "$ROOT"

# --docker publishes the stack on 18080 so it can run next to a development instance on 8080
if [[ -z "$URL" ]]; then
  if [[ $DOCKER -eq 1 ]]; then URL="http://localhost:18080"; else URL="http://localhost:8080"; fi
fi

if [[ $DOCKER -eq 1 ]]; then
  echo "[evaluation] starting the Docker stack (port 18080, measurements on)"
  docker compose -f docker-compose.yml -f tools/docker-compose.evaluation.yml up -d --build
fi

echo "[evaluation] waiting for $URL"
for i in $(seq 1 90); do
  if curl -fsS -m 5 "$URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then break; fi
  if [[ $i -eq 90 ]]; then echo "[evaluation] UDAV at $URL did not become healthy" >&2; exit 1; fi
  sleep 2
done

FIRST_PIPELINE="e0000001-0000-4000-8000-000000000001"
if ! curl -fsS -m 30 -o /dev/null "$URL/api/pipelines/$FIRST_PIPELINE"; then
  echo "[evaluation] the evaluation pipelines are not imported at $URL." >&2
  echo "             They import at startup from src/main/resources/pipelines (source-tree start)" >&2
  echo "             or /app/pipelines (Docker image); check PIPELINE_IMPORTER and the folder." >&2
  exit 1
fi

MVN=(mvn -B test
     -Dtest=BatchExportEvaluationIT
     -Dsurefire.failIfNoSpecifiedTests=false
     "-DUDAV_BASE_URL=$URL"
     "-DUDAV_EVAL_REPORT=$REPORT")
if [[ $SMOKE -eq 1 ]]; then
  MVN+=(-Dudav.eval.bugHunt=true)
  echo "[evaluation] smoke mode: one run per condition, no timing"
else
  echo "[evaluation] full campaign: this takes hours; the report is only valid on a quiet machine"
fi

echo "[evaluation] ${MVN[*]} ${EXTRA[*]:-}"
# ${EXTRA[@]+...}: an empty array trips "set -u" on bash 3.2, which macOS still ships
"${MVN[@]}" ${EXTRA[@]+"${EXTRA[@]}"}
echo
echo "[evaluation] report: $ROOT/$REPORT"
sed -n '/## 1. VERDICT/,/## 2/p' "$REPORT" | head -8
