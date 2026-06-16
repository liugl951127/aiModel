#!/usr/bin/env python3
"""
v3 (final): 用 ruamel.yaml 重构 spring 节, 保留所有原结构 + 注释.
- application -> main -> config -> cloud -> profiles 顺序
- nacos.discovery / nacos.config 简化 (用 block 形式, 因为 ruamel inline 设置复杂)
- 保留 openfeign / gateway / datasource / routes 等业务配置不动
"""
from ruamel.yaml import YAML
from ruamel.yaml.comments import CommentedMap
import glob

FILES = sorted(glob.glob('/workspace/ai-agent-platform/backend/ai-platform-*/src/main/resources/application.yml') +
               glob.glob('/workspace/ai-agent-platform/backend/seata-demo/src/main/resources/application.yml'))

yaml = YAML()
yaml.preserve_quotes = True
yaml.indent(mapping=2, sequence=4, offset=2)
yaml.width = 120


def refactor(data, name):
    if 'spring' not in data:
        return
    spring = data['spring']

    # 拿 openfeign / gateway (deep reference, 不复制)
    openfeign = None
    gateway = None
    if isinstance(spring, dict) and 'cloud' in spring and isinstance(spring['cloud'], dict):
        cloud = spring['cloud']
        if 'openfeign' in cloud:
            openfeign = cloud['openfeign']
        if 'gateway' in cloud:
            gateway = cloud['gateway']

    # 拿其他 spring 子项
    other_spring = {}
    for k, v in spring.items():
        if k in ('application', 'main', 'config', 'profiles', 'cloud'):
            continue
        other_spring[k] = v

    # 重建 spring
    new_spring = CommentedMap()
    new_spring['application'] = CommentedMap()
    new_spring['application']['name'] = name
    new_spring['main'] = CommentedMap()
    new_spring['main']['allow-bean-definition-overriding'] = True
    new_spring['config'] = CommentedMap()
    new_spring['config']['import'] = f'nacos:{name}.yaml'
    new_spring['cloud'] = CommentedMap()

    if openfeign is not None:
        new_spring['cloud']['openfeign'] = openfeign

    # nacos — block 形式, 简化
    nacos = CommentedMap()
    nacos['discovery'] = CommentedMap()
    nacos['discovery']['server-addr'] = '127.0.0.1:8848'
    nacos['discovery']['enabled'] = True
    nacos['config'] = CommentedMap()
    nacos['config']['server-addr'] = '127.0.0.1:8848'
    nacos['config']['file-extension'] = 'yaml'
    nacos['config']['enabled'] = True
    new_spring['cloud']['nacos'] = nacos

    if gateway is not None:
        new_spring['cloud']['gateway'] = gateway

    new_spring['profiles'] = CommentedMap()
    new_spring['profiles']['active'] = '${SPRING_PROFILES_ACTIVE:dev}'

    for k, v in other_spring.items():
        new_spring[k] = v

    data['spring'] = new_spring


def main():
    for f in FILES:
        with open(f) as fp:
            data = yaml.load(fp) or CommentedMap()

        name = data.get('spring', {}).get('application', {}).get('name', 'unknown')
        refactor(data, name)

        with open(f, 'w') as fp:
            fp.write('# =====================================================\n')
            fp.write('# Spring Cloud Alibaba 配置 v3 (yml 重构)\n')
            fp.write('# nacos 默认 enabled: true (因为 config.import 拉取).\n')
            fp.write('# 想关闭: --spring.cloud.nacos.discovery.enabled=false\n')
            fp.write('#         --spring.cloud.nacos.config.enabled=false\n')
            fp.write('# =====================================================\n\n')
            yaml.dump(data, fp)
        print(f'✅ {f} (service: {name})')


if __name__ == '__main__':
    main()
