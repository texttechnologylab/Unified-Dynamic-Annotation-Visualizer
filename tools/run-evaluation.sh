#!/usr/bin/env bash
# =============================================================================================
# Batch export evaluation - one command.
#
#   tools/run-evaluation.sh                  full campaign against a running UDAV (hours)
#   tools/run-evaluation.sh --smoke          one run per condition, correctness only (minutes)
#   tools/run-evaluation.sh --docker         start UDAV + Postgres with the evaluation fixtures
#                                            first (docker compose, on port 18080), then run against it
#   tools/run-evaluation.sh --docker --jvm-runs 3
#                                            the full campaign three times, restarting UDAV in
#                                            between (several JVM invocations, Georges et al.);
#                                            one report per run, pooled by tools/cost_model.py
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
JVM_RUNS=1
EXTRA=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --smoke)    SMOKE=1; shift ;;
    --docker)   DOCKER=1; shift ;;
    --jvm-runs) JVM_RUNS="$2"; shift 2 ;;
    --url)      URL="$2"; shift 2 ;;
    --report)   REPORT="$2"; shift 2 ;;
    -h|--help)  sed -n '2,23p' "$0"; exit 0 ;;
    *)          EXTRA+=("$1"); shift ;;
  esac
done

cd "$ROOT"

# --docker publishes the stack on 18080 so it can run next to a development instance on 8080
if [[ -z "$URL" ]]; then
  if [[ $DOCKER -eq 1 ]]; then URL="http://localhost:18080"; else URL="http://localhost:8080"; fi
fi

if [[ $JVM_RUNS -gt 1 && $DOCKER -eq 0 ]]; then
  echo "[evaluation] --jvm-runs needs --docker: the script restarts the stack between runs" >&2
  exit 1
fi

COMPOSE=(docker compose -f docker-compose.yml -f tools/docker-compose.evaluation.yml)

if [[ $DOCKER -eq 1 ]]; then
  echo "[evaluation] starting the Docker stack (port 18080, measurements on)"
  "${COMPOSE[@]}" up -d --build
fi

wait_healthy() {
  echo "[evaluation] waiting for $URL"
  for i in $(seq 1 90); do
    if curl -fsS -m 5 "$URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then return 0; fi
    if [[ $i -eq 90 ]]; then echo "[evaluation] UDAV at $URL did not become healthy" >&2; exit 1; fi
    sleep 2
  done
}

wait_healthy

FIRST_PIPELINE="e0000001-0000-4000-8000-000000000001"
if ! curl -fsS -m 30 -o /dev/null "$URL/api/pipelines/$FIRST_PIPELINE"; then
  echo "[evaluation] the evaluation pipelines are not imported at $URL." >&2
  echo "             They import at startup from src/main/resources/pipelines (source-tree start)" >&2
  echo "             or /app/pipelines (Docker image); check PIPELINE_IMPORTER and the folder." >&2
  exit 1
fi

run_harness() {
  local report="$1"
  local run="$2"
  # the run numbers let the harness print the progress over all JVM runs
  local mvn=(mvn -B test
             -Dtest=BatchExportEvaluationIT
             -Dsurefire.failIfNoSpecifiedTests=false
             "-DUDAV_BASE_URL=$URL"
             "-DUDAV_EVAL_REPORT=$report"
             "-Dudav.eval.jvmRun=$run"
             "-Dudav.eval.jvmRuns=$JVM_RUNS")
  if [[ $SMOKE -eq 1 ]]; then
    mvn+=(-Dudav.eval.bugHunt=true)
    echo "[evaluation] smoke mode: one run per condition, no timing"
  else
    echo "[evaluation] full campaign: this takes hours; the report is only valid on a quiet machine"
  fi

  echo "[evaluation] ${mvn[*]} ${EXTRA[*]:-}"
  # ${EXTRA[@]+...}: an empty array trips "set -u" on bash 3.2, which macOS still ships
  "${mvn[@]}" ${EXTRA[@]+"${EXTRA[@]}"}
  echo
  if [[ "$report" = /* ]]; then echo "[evaluation] report: $report"; else echo "[evaluation] report: $ROOT/$report"; fi
  sed -n '/## 1. VERDICT/,/## 2/p' "$report" | head -8
}

REPORTS=()
for ((run = 1; run <= JVM_RUNS; run++)); do
  if [[ $run -gt 1 ]]; then
    echo "[evaluation] restarting UDAV for JVM run $run of $JVM_RUNS"
    "${COMPOSE[@]}" restart udav
    wait_healthy
  fi
  if [[ $JVM_RUNS -gt 1 ]]; then report="${REPORT%.txt}-run$run.txt"; else report="$REPORT"; fi
  run_harness "$report" "$run"
  REPORTS+=("$report")
done

if [[ $JVM_RUNS -gt 1 ]]; then
  echo
  echo "[evaluation] pool the runs with: tools/cost_model.py ${REPORTS[*]}"
fi
