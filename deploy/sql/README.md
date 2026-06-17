# 数据库脚本说明

## 🌟 一键初始化 (推荐)

`00_init_all.sql` 把本项目需要的全部 ai_platform 库内容 (32 表 + 21 条种子) 集中到一个文件:

```powershell
# 一条命令, 整个项目数据库初始化完
mysql -uroot -p951127 < deploy\sql\00_init_all.sql
```

跑完后会自动显示:
- `SHOW TABLES` → 32 张表
- 各表种子数量 (10 个统计项, 看到非 0 即成功)

## 其它库 (Nacos / Seata)

不在 `00_init_all.sql` 内, 官方 schema 动态变化, 按需跑:

```powershell
# 启用 Nacos 时
mysql -uroot -p951127 -e "CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARSET utf8mb4;"
curl -O https://raw.githubusercontent.com/alibaba/nacos/develop/distribution/conf/mysql-schema.sql
mysql -uroot -p951127 nacos_config < mysql-schema.sql

# 启用分布式事务时
mysql -uroot -p951127 -e "CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARSET utf8mb4;"
curl -O https://raw.githubusercontent.com/seata/seata/2.0.0/script/server/db/mysql.sql
mysql -uroot -p951127 seata < mysql.sql
```

跳过这 2 个库不影响启动: 服务走本地配置 + 事务降级到 LOCAL.

## 文件清单

| 文件 | 大小 | 作用 | 必跑 |
|---|---|---|---|
| **`00_init_all.sql`** | **40 KB** | **本项目完整初始化 (32 表 + 种子)** | **✓** |
| 01_schema.sql | 28 KB | 32 张表 DDL (00_init_all 子集) | 备查 |
| 02_seed.sql | 9 KB | 种子数据 (00_init_all 子集) | 备查 |
| README.md | 2 KB | 本说明 | - |

## 32 张表清单

### 系统管理 (8)
`sys_tenant` `sys_user` `sys_user_tenant` `sys_role` `sys_user_role` `sys_role_menu` `sys_menu` `sys_login_audit`

### 模型 (3)
`model_registry` `model_dataset` `model_train_job`

### Agent (5)
`agent_agent` `agent_tool` `agent_conversation` `agent_message` `agent_multi_agent_case`

### 文件 (1)
`file_object`

### 知识库 (2)
`kb_base` `kb_document`

### 业务全链路 (10)
`biz_customer` `biz_chat` `biz_opportunity` `biz_quote` `biz_contract` `biz_order` `biz_payment` `biz_product` `biz_service` `biz_expense`

### Seata 演示 (3)
`agent_invoke_log` `user_credits` `usage_stats`

## 32 张表 ↔ 32 个 Java 实体, 0 漏 (2026-06-17 验证)
