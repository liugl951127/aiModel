# 部署 & 初始化数据库

## 1. 跑 schema + seed

```bash
mysql -uroot -p < deploy/sql/01_schema.sql
mysql -uroot -p < deploy/sql/02_seed.sql
```

## 2. 默认账号 (password 用项目真实 BCrypt 编码)

| 用户名 | 密码 | 角色 | 租户 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 超级管理员 (所有租户) | 自动拥有 |
| `demo` | `demo123` | 市场部 / 用户 | 1 (默认公司) |
| `manager` | `demo123` | 运营部 / 用户 | 2 (示例科技) |

## 3. 自定义密码 (用项目真实 BCryptPasswordEncoder)

```bash
cd backend
# 安装项目到本地 maven
mvn -N install

# 跑 hash 生成器
mvn -pl ai-platform-user exec:java \
  -Dexec.mainClass="com.aiplatform.user.util.BCryptHashMain" \
  -Dexec.classpathScope=runtime \
  -Dfile.encoding=UTF-8
```

输出:
```
明文:   admin123
hash:   $2a$10$8qabkLPmOcdGOTABzszZ2O/qTOArLELuL27Vne4rU9sD/viL7IFZy
rounds: 10 (Spring BCryptPasswordEncoder 默认)
自验:   ✅ 通过
```

把 `hash` 复制到 SQL:
```bash
mysql -uroot -p -e "UPDATE ai_platform.sys_user SET password='\$2a\$10\$8qa...' WHERE username='admin';"
```

**重要**: 必须用项目的 `BCryptPasswordEncoder` 生成的 hash, 不能用 Python `bcrypt` 库。
两个库默认 salt prefix 不同 ($2a$ vs $2b$)，虽然 Spring 兼容 $2b$ 但推荐统一用 $2a$。

## 4. 排查密码错误

```bash
# 1. 看后端审计
mysql -uroot -p -e "SELECT username, login_status, fail_reason FROM ai_platform.sys_login_audit ORDER BY login_time DESC LIMIT 5;"
# 期望:
#   FAILED / 密码错误  → 密码不匹配
#   FAILED / 用户不存在 → DB 没 seed

# 2. 重置 admin 密码
curl -X POST http://127.0.0.1:9001/api/user/1/reset-password
# 返: { "code": 200, "data": "123456" }
```
