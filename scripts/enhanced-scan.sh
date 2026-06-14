#!/bin/bash
# Enhanced bean + health scan for trainer/files/knowledge/workflow.
# Detects: UnsatisfiedDependency, BeanDefinitionOverride, FAILED startup.
# Also probes /health endpoint + checks for BOOT-OVERVIEW log line.
set +e
MODULES=(
  "ai-platform-trainer|9008|/api/trainer/health"
  "ai-platform-files|9010|/api/files/health"
  "ai-platform-knowledge|9005|/api/knowledge/health"
  "ai-platform-workflow|9011|/api/workflow/runs"
)
PASS=0
FAIL=0
ISSUES=()
for ENTRY in "${MODULES[@]}"; do
  MOD="${ENTRY%%|*}"
  REST="${ENTRY#*|}"
  PORT="${REST%%|*}"
  HEALTH="${REST##*|}"
  JAR="/workspace/ai-agent-platform/backend/$MOD/target/$MOD.jar"
  if [ ! -f "$JAR" ]; then
    echo "[SKIP] $MOD (no jar)"
    continue
  fi
  pkill -9 -f "$MOD" 2>/dev/null
  sleep 1
  LOG="/tmp/scan-$MOD.log"
  nohup java -Xmx768m -jar "$JAR" > "$LOG" 2>&1 &
  PID=$!
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    sleep 2
    grep -q "Started.*Application" "$LOG" 2>/dev/null && break
    grep -qE "APPLICATION FAILED|UnsatisfiedDependencyException|BeanDefinitionOverride" "$LOG" 2>/dev/null && break
  done
  if grep -qE "APPLICATION FAILED|UnsatisfiedDependencyException|BeanDefinitionOverride" "$LOG" 2>/dev/null; then
    echo "[FAIL] $MOD"
    grep -E "APPLICATION FAILED|UnsatisfiedDependency|BeanDefinitionOverride" "$LOG" | head -1 | sed 's/^/    /'
    ISSUES+=("$MOD")
    FAIL=$((FAIL+1))
  elif grep -q "Started.*Application" "$LOG"; then
    T=$(grep "Started.*Application" "$LOG" | head -1 | sed 's/.*in \([0-9.]*\) seconds.*/\1/')
    HCODE=$(curl -s --max-time 4 -o /dev/null -w "%{http_code}" "http://127.0.0.1:$PORT$HEALTH" 2>/dev/null)
    OVERVIEW=$(grep -c "BOOT-OVERVIEW" "$LOG")
    GUARD=$(grep -c "GRAY" "$LOG")
    echo "[OK]   $MOD (${T}s, health=$HCODE, boot-overview=$OVERVIEW)"
    PASS=$((PASS+1))
  else
    echo "[TIMEOUT] $MOD"
    tail -3 "$LOG" | sed 's/^/    /'
    FAIL=$((FAIL+1))
    ISSUES+=("$MOD")
  fi
  pkill -9 -f "$MOD" 2>/dev/null
  sleep 1
done
echo ""
echo "==== summary ===="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
[ ${#ISSUES[@]} -gt 0 ] && echo "FAILED: ${ISSUES[*]}"
