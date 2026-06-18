#!/usr/bin/env bash
# ★ OP-10 生成安全的 JWT_SECRET (32+ 字符)
set -e
SECRET=$(openssl rand -base64 48 | tr -d '\n')
echo ""
echo "你的 JWT_SECRET (妥善保存, 不要 commit):"
echo ""
echo "  ${SECRET}"
echo ""
echo "用法:"
echo "  export JWT_SECRET='${SECRET}'"
echo ""
echo "或写入 .env:"
echo "  echo 'JWT_SECRET=${SECRET}' >> .env"
echo ""
echo "或写入 Nacos (生产):"
echo "  curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \\"
echo "    -d 'dataId=ai-platform-auth.yaml&group=DEFAULT_GROUP&content=...'"
