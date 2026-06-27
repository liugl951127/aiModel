#!/usr/bin/env bash
# ★ Trainer 启动脚本 (绕过 DJL cuda 探测)
#
# DJL 0.36 在 Windows 启动时会探测 cuda flavor, 即使只引 cpu native 也报 WARN.
# 设 PYTORCH_FLAVOR=cpu 跳过探测.
#
# 用法: bash scripts/start-trainer.sh
# 也可: PYTORCH_FLAVOR=cu121 bash scripts/start-trainer.sh  (GPU 环境)
set -e

cd "$(dirname "$0")/../backend"

# 默认 cpu flavor, 已通过 TrainerApplication.java 静态块设
# 这里再保险一次 (环境变量级别)
export PYTORCH_FLAVOR=${PYTORCH_FLAVOR:-cpu}

JAR="ai-platform-trainer/target/ai-platform-trainer.jar"
if [ ! -f "$JAR" ]; then
  echo "[X] $JAR 不存在, 先 mvn package -DskipTests"
  exit 1
fi

JAVA_OPTS=${JAVA_OPTS:-"-Xmx512m -Xms128m"}
APP_OPTS=${APP_OPTS:-"--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false"}

echo "[i] 启动 trainer: PYTORCH_FLAVOR=$PYTORCH_FLAVOR"
exec java $JAVA_OPTS -jar "$JAR" $APP_OPTS "$@"