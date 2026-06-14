#!/bin/bash
set +e
MODULES=(
  "ai-platform-trainer|9008"
  "ai-platform-files|9010"
  "ai-platform-workflow|9011"
  "ai-platform-knowledge|9005"
  "ai-platform-inference|9007"
  "ai-platform-user|9002"
  "ai-platform-system|9003"
  "ai-platform-agent|9004"
  "ai-platform-model|9006"
  "ai-platform-auth|9001"
  "ai-platform-gateway|9000"
)
PASS=0
FAIL=0
FAIL_LIST=""
for ENTRY in "${MODULES[@]}"; do
  MOD="${ENTRY%%|*}"
  PORT="${ENTRY##*|}"
  JAR="/workspace/ai-agent-platform/backend/$MOD/target/$MOD.jar"
  if [ ! -f "$JAR" ]; then
    echo "[SKIP] $MOD (no jar at $JAR)"
    continue
  fi
  pkill -9 -f "$MOD" 2>/dev/null
  sleep 1
  nohup java -Xmx768m -jar "$JAR" > /tmp/scan-$MOD.log 2>&1 &
  PID=$!
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    sleep 2
    grep -q "Started.*Application" /tmp/scan-$MOD.log 2>/dev/null && break
    grep -qE "APPLICATION FAILED|UnsatisfiedDependencyException|BeanDefinitionOverride" /tmp/scan-$MOD.log 2>/dev/null && break
  done
  if grep -qE "APPLICATION FAILED|UnsatisfiedDependencyException|BeanDefinitionOverride" /tmp/scan-$MOD.log 2>/dev/null; then
    echo "[FAIL] $MOD (port $PORT)"
    grep -E "APPLICATION FAILED|UnsatisfiedDependencyException|BeanDefinitionOverrideException|Description:|Action:" /tmp/scan-$MOD.log | head -6 | sed 's/^/    /'
    FAIL=$((FAIL+1))
    FAIL_LIST="$FAIL_LIST $MOD"
  elif grep -q "Started.*Application" /tmp/scan-$MOD.log; then
    T=$(grep "Started.*Application" /tmp/scan-$MOD.log | head -1 | sed 's/.*in \([0-9.]*\) seconds.*/\1/')
    echo "[OK]   $MOD (port $PORT, ${T}s)"
    PASS=$((PASS+1))
  else
    echo "[TIMEOUT] $MOD"
    tail -3 /tmp/scan-$MOD.log | sed 's/^/    /'
    FAIL=$((FAIL+1))
    FAIL_LIST="$FAIL_LIST $MOD"
  fi
  pkill -9 -f "$MOD" 2>/dev/null
  sleep 1
done
echo ""
echo "==== summary ===="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
[ -n "$FAIL_LIST" ] && echo "FAILED modules:$FAIL_LIST"
