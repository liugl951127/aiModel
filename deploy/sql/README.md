# 数据库脚本说明

## 文件顺序 (按文件名升序执行)

| 文件 | 库 | 行数 | 作用 |
|---|---|---|---|
| `01_schema.sql` | ai_platform | 646 | **32 张业务表** (主库, 必跑) |
| `02_seed.sql` | ai_platform | 140 | **系统 + 业务种子** (admin/角色/菜单/客户/订单) |
| nacos 自带 | nacos_config | - | 跑 [Nacos 官方 schema-mysql.sql](https://github.com/alibaba/nacos/blob/develop/distribution/conf/mysql-schema.sql) (用 nacos 必跑) |
| seata 自带 | seata | - | 跑 [Seata 官方 db_store.sql](https://github.com/seata/seata/tree/develop/script/server/db) (用分布式事务必跑) |

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

## 跑法 (PowerShell)

```powershell
# 1. 业务主库 (必)
mysql -uroot -p951127 < deploy\sql\01_schema.sql
mysql -uroot -p951127 ai_platform < deploy\sql\02_seed.sql

# 2. Nacos 配置库 (启用 Nacos 时必)
# 下载 https://raw.githubusercontent.com/alibaba/nacos/develop/distribution/conf/mysql-schema.sql
mysql -uroot -p951127 < nacos-mysql.sql

# 3. Seata 库 (启用分布式事务时必)
# 下载 https://raw.githubusercontent.com/seata/seata/2.0.0/script/server/db/mysql.sql
mysql -uroot -p951127 < seata-mysql.sql
```
