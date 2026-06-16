# 部署 & 初始化数据库

## 1. 跑 schema + seed

```bash
# MySQL
mysql -uroot -p < deploy/sql/01_schema.sql
mysql -uroot -p < deploy/sql/02_seed.sql

# 或一次性
mysql -uroot -p < <(cat deploy/sql/01_schema.sql deploy/sql/02_seed.sql)
```

## 2. 默认账号 (password 已 BCrypt 编码)

| 用户名 | 密码 | 角色 | 租户 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 超级管理员 (所有租户) | 自动拥有 |
| `demo` | `demo123` | 市场部 / 用户 | 1 (默认公司) |
| `manager` | `demo123` | 运营部 / 用户 | 2 (示例科技) |

## 3. 密码不对时怎么排查

### Step 1: 验算 hash
```bash
python3 scripts/verify_password.py check admin123 '$2a$10$4tMHnM6bsrADgZJyK3vI5.z99DvtP6xhQoPAjuayBmGvtdj4Z8zeO'
# 期望: ✅ 密码 'admin123' 匹配 hash
```

### Step 2: 确认 DB 已 seed
```bash
mysql -uroot -p -e "SELECT id, username, LEFT(password, 20) AS hash_prefix, status FROM ai_platform.sys_user;"
# 期望: 看到 3 行 (admin/demo/manager)
```

如果只有 0 行, 跑 seed:
```bash
mysql -uroot -p < deploy/sql/02_seed.sql
```

### Step 3: 自定义密码
```bash
# 生成新 hash
python3 scripts/verify_password.py gen MyNewPassword
# 复制输出, 在 DB 里直接 UPDATE
mysql -uroot -p -e "UPDATE ai_platform.sys_user SET password='<新hash>' WHERE username='admin';"
```

### Step 4: 看后端日志
- `AuthService` 登录时如果 `passwordEncoder.matches(plain, stored) == false`, 会记录审计 `FAILED/密码错误`
- 看 `sys_login_audit` 表确认是否真的 BCrypt 不匹配

```bash
mysql -uroot -p -e "SELECT username, login_status, fail_reason, login_time FROM ai_platform.sys_login_audit ORDER BY login_time DESC LIMIT 5;"
```

## 4. 用现有 auth-service 重置密码

直接调 user-service API:
```bash
curl -X POST http://127.0.0.1:9001/api/user/1/reset-password
# 返: { "code":200, "data":"123456" }
# 把 admin 密码重置为 123456
```

## 5. Spring BCrypt 兼容性

`BCryptPasswordEncoder` 同时支持 `$2a$` / `$2b$` / `$2y$` 三种前缀。
Python `bcrypt` 库默认生成 `$2b$` — 写 SQL 时建议统一用 `$2a$` (更兼容)。
`verify_password.py gen` 默认就用 `$2a$`。
