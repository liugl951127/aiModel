#!/usr/bin/env python3
"""
统一 12 个 yml 的 MySQL datasource 块 (只动有 DB 的服务).
gateway / inference / seata-demo 不动 (它们不需要 datasource).

新格式:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DB:ai_platform}?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
"""
from ruamel.yaml import YAML
from ruamel.yaml.comments import CommentedMap
import glob

# 有 datasource 的服务
TARGETS = [
    'ai-platform-agent',
    'ai-platform-auth',
    'ai-platform-files',
    'ai-platform-knowledge',
    'ai-platform-model',
    'ai-platform-system',
    'ai-platform-trainer',
    'ai-platform-user',
    'ai-platform-workflow',
]

# 不动的服务 (无需 DB 持久化)
SKIP = ['ai-platform-gateway', 'ai-platform-inference', 'seata-demo']

yaml = YAML()
yaml.preserve_quotes = True
yaml.indent(mapping=2, sequence=4, offset=2)
yaml.width = 120


def new_datasource_block():
    """标准 MySQL datasource 块."""
    ds = CommentedMap()
    ds['url'] = 'jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DB:ai_platform}?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    ds['username'] = '${MYSQL_USER:root}'
    ds['password'] = '${MYSQL_PASSWORD:root}'
    ds['driver-class-name'] = 'com.mysql.cj.jdbc.Driver'
    return ds


def main():
    for mod in TARGETS:
        f = f'/workspace/ai-agent-platform/backend/{mod}/src/main/resources/application.yml'
        with open(f) as fp:
            data = yaml.load(fp) or CommentedMap()
        if 'spring' not in data:
            print(f'⚠️  {f} no spring block, skip')
            continue
        spring = data['spring']
        # 替换 / 新增
        old_ds = spring.get('datasource', None)
        if old_ds is not None:
            print(f'  旧 url: {old_ds.get("url", "?")[:80]}...')
        spring['datasource'] = new_datasource_block()
        with open(f, 'w') as fp:
            yaml.dump(data, fp)
        print(f'✅ {mod} — 统一为标准 MySQL URL (单库 ai_platform)')


if __name__ == '__main__':
    main()
