#!/usr/bin/env bash
# ★ OP-8 告警 webhook (飞书/钉钉)
# 用法: Alertmanager 配 webhook_config → 调用本脚本
# 配: ./scripts/alert-webhook.sh
#
# 环境变量:
#   FEISHU_WEBHOOK_URL   - 飞书机器人 (https://open.feishu.cn/...)
#   DINGTALK_WEBHOOK_URL - 钉钉机器人
#   WECOM_WEBHOOK_URL    - 企业微信机器人

ALERT_PAYLOAD="${1:-}"
if [ -z "${ALERT_PAYLOAD}" ]; then
  # 从 stdin 读
  ALERT_PAYLOAD=$(cat)
fi

STATUS=$(echo "${ALERT_PAYLOAD}" | jq -r '.status // "firing"')
TITLE=$(echo "${ALERT_PAYLOAD}" | jq -r '.alerts[0].annotations.summary // "AI Platform 告警"')
INSTANCE=$(echo "${ALERT_PAYLOAD}" | jq -r '.alerts[0].labels.instance // "unknown"')
SEVERITY=$(echo "${ALERT_PAYLOAD}" | jq -r '.alerts[0].labels.severity // "warning"')

# 飞书
if [ -n "${FEISHU_WEBHOOK_URL}" ]; then
  curl -fsS -X POST "${FEISHU_WEBHOOK_URL}" \
    -H "Content-Type: application/json" \
    -d "{\"msg_type\":\"interactive\",\"card\":{\"header\":{\"title\":{\"tag\":\"plain\",\"content\":\"[${SEVERITY}] ${TITLE}\"},\"template\":\"${SEVERITY}\"},\"elements\":[{\"tag\":\"div\",\"text\":{\"tag\":\"lark_md\",\"content\":\"**实例**: ${INSTANCE}\n**状态**: ${STATUS}\n**时间**: $(date '+%F %T')\"}}]}}" \
    || echo "飞书告警发送失败"
fi

# 钉钉
if [ -n "${DINGTALK_WEBHOOK_URL}" ]; then
  curl -fsS -X POST "${DINGTALK_WEBHOOK_URL}" \
    -H "Content-Type: application/json" \
    -d "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"[${SEVERITY}] ${TITLE}\",\"text\":\"## [${SEVERITY}] ${TITLE}\n\n**实例**: ${INSTANCE}\n\n**状态**: ${STATUS}\n\n**时间**: $(date '+%F %T')\"}}" \
    || echo "钉钉告警发送失败"
fi

# 企业微信
if [ -n "${WECOM_WEBHOOK_URL}" ]; then
  curl -fsS -X POST "${WECOM_WEBHOOK_URL}" \
    -H "Content-Type: application/json" \
    -d "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"## [${SEVERITY}] ${TITLE}\n> 实例: ${INSTANCE}\n> 状态: ${STATUS}\n> 时间: $(date '+%F %T')\"}}" \
    || echo "企业微信告警发送失败"
fi
