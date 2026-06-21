#!/usr/bin/env python3
"""
前端集成测试用 Mock Backend
模拟 Spring Boot 11 个微服务的所有 API
完全 0 依赖, 单 Python 文件跑

启动: python3 mock-backend.py 9000
"""
import http.server
import json
import sys
import time
import uuid
from datetime import datetime, timedelta
from urllib.parse import urlparse, parse_qs
import random

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9000

NOW = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
TODAY = datetime.now().strftime("%Y-%m-%d")
TOKEN = "mock-jwt-token-" + uuid.uuid4().hex[:16]

# ============= 数据集 =============

USERS = [
    {"id": 1, "tenantId": 1, "username": "admin", "nickname": "管理员",
     "email": "admin@example.com", "phone": "", "avatar": None,
     "status": 1, "department": "技术部", "lastLoginIp": "127.0.0.1",
     "createTime": NOW, "updateTime": NOW, "deleted": 0,
     "roles": [{"id": 1, "code": "admin", "name": "管理员"}]},
    {"id": 2, "tenantId": 1, "username": "demo", "nickname": "演示账号",
     "email": "demo@example.com", "phone": "", "avatar": None,
     "status": 1, "department": "市场部", "lastLoginIp": None,
     "createTime": NOW, "updateTime": NOW, "deleted": 0,
     "roles": [{"id": 2, "code": "user", "name": "普通用户"}]},
    {"id": 3, "tenantId": 2, "username": "manager", "nickname": "王经理",
     "email": "manager@example.com", "phone": "", "avatar": None,
     "status": 1, "department": "运营部", "lastLoginIp": None,
     "createTime": NOW, "updateTime": NOW, "deleted": 0,
     "roles": [{"id": 3, "code": "manager", "name": "部门经理"}]},
]

TENANTS = [
    {"id": 1, "code": "default", "name": "默认公司", "status": 1, "createTime": NOW},
    {"id": 2, "code": "demo", "name": "演示公司", "status": 1, "createTime": NOW},
]

MENUS = [
    {"id": 1, "parentId": 0, "name": "系统管理", "path": "/system", "icon": "Setting",
     "sort": 1, "type": 1, "permissions": ["system:view"]},
    {"id": 2, "parentId": 0, "name": "AI 工作台", "path": "/agent", "icon": "MagicStick",
     "sort": 2, "type": 1, "permissions": ["agent:view"]},
    {"id": 3, "parentId": 0, "name": "知识库", "path": "/knowledge", "icon": "Reading",
     "sort": 3, "type": 1, "permissions": ["knowledge:view"]},
    {"id": 4, "parentId": 0, "name": "工作流", "path": "/workflow", "icon": "Share",
     "sort": 4, "type": 1, "permissions": ["workflow:view"]},
    {"id": 5, "parentId": 0, "name": "训练", "path": "/trainer", "icon": "Cpu",
     "sort": 5, "type": 1, "permissions": ["trainer:view"]},
    {"id": 6, "parentId": 0, "name": "业务全链路", "path": "/biz", "icon": "Connection",
     "sort": 6, "type": 1, "permissions": ["biz:view"]},
    {"id": 7, "parentId": 0, "name": "分布式演示", "path": "/distributed", "icon": "Cpu",
     "sort": 7, "type": 1, "permissions": ["distributed:view"]},
    {"id": 8, "parentId": 0, "name": "实时监控", "path": "/monitor", "icon": "Monitor",
     "sort": 8, "type": 1, "permissions": ["monitor:view"]},
]

ROLES = [
    {"id": 1, "code": "admin", "name": "管理员", "permissions": ["*"], "status": 1, "createTime": NOW},
    {"id": 2, "code": "user", "name": "普通用户", "permissions": ["dashboard:view"], "status": 1, "createTime": NOW},
]

DICTS = [
    {"id": 1, "type": "biz_status", "label": "进行中", "value": "1", "sort": 1},
    {"id": 2, "type": "biz_status", "label": "已完成", "value": "2", "sort": 2},
    {"id": 3, "type": "biz_status", "label": "已取消", "value": "3", "sort": 3},
    {"id": 4, "type": "biz_type", "label": "订单", "value": "order", "sort": 1},
    {"id": 5, "type": "biz_type", "label": "报价", "value": "quote", "sort": 2},
]

# 业务
def gen_biz(kind, n=10):
    items = []
    for i in range(1, n + 1):
        items.append({
            "id": i, "tenantId": 1, "createBy": 1, "updateBy": 1,
            "createTime": NOW, "updateTime": NOW, "deleted": 0,
            "name": f"{kind}-{i:04d}", "code": f"{kind[:3].upper()}-{i:04d}",
            "status": random.choice([1, 2, 3]),
            "amount": round(random.uniform(1000, 100000), 2),
            "customerName": f"客户-{i}", "ownerName": "admin",
        })
    return items

BIZ_DATA = {
    "customer": gen_biz("客户"),
    "opportunity": gen_biz("商机"),
    "order": gen_biz("订单"),
    "quote": gen_biz("报价"),
    "contract": gen_biz("合同"),
    "product": gen_biz("产品"),
    "service": gen_biz("服务"),
    "payment": gen_biz("付款"),
    "expense": gen_biz("费用"),
    "chat": gen_biz("对话"),
}

AGENTS = [
    {"id": 1, "code": "default", "name": "默认助手", "description": "通用对话智能体",
     "modelCode": "mock", "tools": "[]", "status": 1, "createTime": NOW},
    {"id": 2, "code": "rag", "name": "RAG 知识库问答", "description": "基于知识库的问答",
     "modelCode": "mock", "tools": '[{"name":"kb_search"}]', "status": 1, "createTime": NOW},
    {"id": 3, "code": "marketing", "name": "营销文案生成", "description": "多平台文案",
     "modelCode": "mock", "tools": "[]", "status": 1, "createTime": NOW},
]

MODELS = [
    {"id": 1, "code": "mock", "name": "Mock 演示模型", "type": "chat",
     "provider": "internal", "status": 1, "createTime": NOW},
    {"id": 2, "code": "bge-small-zh", "name": "BGE Small 中文嵌入", "type": "embedding",
     "provider": "onnx", "status": 1, "createTime": NOW},
    {"id": 3, "code": "qwen2-1.5b", "name": "Qwen2 1.5B 指令", "type": "chat",
     "provider": "onnx", "status": 0, "createTime": NOW},
]

KNOWLEDGE_BASES = [
    {"id": 1, "name": "产品手册", "docCount": 23, "sizeBytes": 1048576, "status": 1, "createTime": NOW},
    {"id": 2, "name": "客户FAQ", "docCount": 156, "sizeBytes": 524288, "status": 1, "createTime": NOW},
]

WORKFLOWS = [
    {"id": 1, "code": "rag-qa", "name": "RAG 问答流程", "status": 1, "createTime": NOW},
    {"id": 2, "code": "lora-train", "name": "LoRA 微调", "status": 0, "createTime": NOW},
]

SERVICES = [
    {"id": 9000, "name": "网关", "port": 9000, "status": 1, "checkedAt": NOW},
    {"id": 9001, "name": "认证", "port": 9001, "status": 1, "checkedAt": NOW},
    {"id": 9002, "name": "用户", "port": 9002, "status": 1, "checkedAt": NOW},
    {"id": 9003, "name": "系统", "port": 9003, "status": 1, "checkedAt": NOW},
    {"id": 9004, "name": "模型", "port": 9004, "status": 1, "checkedAt": NOW},
    {"id": 9005, "name": "智能体", "port": 9005, "status": 1, "checkedAt": NOW},
    {"id": 9006, "name": "知识库", "port": 9006, "status": 1, "checkedAt": NOW},
    {"id": 9007, "name": "推理", "port": 9007, "status": 1, "checkedAt": NOW},
    {"id": 9008, "name": "训练", "port": 9008, "status": 1, "checkedAt": NOW},
    {"id": 9010, "name": "文件", "port": 9010, "status": 1, "checkedAt": NOW},
    {"id": 9011, "name": "工作流", "port": 9011, "status": 1, "checkedAt": NOW},
]

ACTIVITY = []
for i in range(20):
    ACTIVITY.append({
        "id": i + 1, "userId": random.choice([1, 2, 3]),
        "username": random.choice(["admin", "demo", "manager"]),
        "action": random.choice(["CREATE", "UPDATE", "DELETE", "LOGIN", "QUERY"]),
        "module": random.choice(["user", "agent", "knowledge", "workflow", "biz"]),
        "description": "用户操作记录",
        "ip": "127.0.0.1", "createTime": (datetime.now() - timedelta(minutes=i*5)).strftime("%Y-%m-%dT%H:%M:%S")
    })

# ============= HTTP Handler =============

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # 安静模式
        pass

    def _send_json(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "application/json;charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        body_bytes = json.dumps(body, ensure_ascii=False, default=str).encode("utf-8")
        self.send_header("Content-Length", str(len(body_bytes)))
        self.end_headers()
        self.wfile.write(body_bytes)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:
            return {}

    def _ok(self, data=None, msg="操作成功"):
        return self._send_json(200, {"code": 200, "message": msg, "data": data, "timestamp": int(time.time() * 1000)})

    def _err(self, code, msg, status=500):
        return self._send_json(status, {"code": code, "message": msg, "data": None, "timestamp": int(time.time() * 1000)})

    def do_OPTIONS(self):
        self._send_json(204, {})

    def do_GET(self):
        # SSE 特殊处理
        if self.path == "/api/activity/stream":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream;charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            # 发 1 条初始事件就返, 避免 E2E / 浏览器卡住
            self.wfile.write(b"event: ping\n")
            self.wfile.write(b"data: {\"msg\":\"mock sse ready\"}\n\n".replace(b"\\\\", b"\\"))
            self.wfile.flush()
            return
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def do_PUT(self):
        self._handle("PUT")

    def do_DELETE(self):
        self._handle("DELETE")

    def _handle(self, method):
        url = urlparse(self.path)
        path = url.path
        qs = parse_qs(url.query)
        body = self._read_body() if method in ("POST", "PUT") else {}

        # === 认证 ===
        if path == "/api/auth/login" and method == "POST":
            u = body.get("username", "")
            p = body.get("password", "")
            if u in ("admin", "demo", "manager") and p:
                user = next((x for x in USERS if x["username"] == u), USERS[0])
                # Login.vue 期望的字段: accessToken / username / nickname / tenantId / tenantCode / tenantName / department / roles
                tenant = TENANTS[0]
                return self._ok({
                    "accessToken": TOKEN,
                    "refreshToken": TOKEN,
                    "tokenType": "Bearer",
                    "expiresIn": 86400,
                    "username": user["username"],
                    "nickname": user["nickname"],
                    "userId": user["id"],
                    "tenantId": tenant["id"],
                    "tenantCode": tenant["code"],
                    "tenantName": tenant["name"],
                    "department": user["department"],
                    "roles": user["roles"],
                    "avatar": user.get("avatar"),
                })
            return self._err(1001, "用户名或密码错误", 401)

        if path == "/api/auth/logout" and method == "POST":
            return self._ok({"ok": True})

        if path == "/api/auth/preview" and method == "GET":
            u = qs.get("username", [""])[0]
            user = next((x for x in USERS if x["username"] == u), None)
            if user:
                return self._ok({"user": user, "tenant": TENANTS[0]})
            return self._err(1004, "用户不存在", 404)

        if path == "/api/auth/tenants" and method == "GET":
            return self._ok(TENANTS)

        if path == "/api/auth/me" and method == "GET":
            return self._ok(USERS[0])

        # === 用户 ===
        if path == "/api/user/page" and method == "GET":
            current = int(qs.get("current", ["1"])[0])
            size = int(qs.get("size", ["10"])[0])
            total = len(USERS)
            return self._ok({"total": total, "current": current, "size": size, "records": USERS})

        if path == "/api/user/list" and method == "GET":
            return self._ok(USERS)

        if path.startswith("/api/user/feign/by-username"):
            u = qs.get("username", ["admin"])[0]
            user = next((x for x in USERS if x["username"] == u), USERS[0])
            return self._ok(user)

        if path.startswith("/api/user/") and method == "GET":
            uid = int(path.split("/")[-1])
            user = next((x for x in USERS if x["id"] == uid), None)
            return self._ok(user) if user else self._err(1004, "用户不存在", 404)

        # === 角色 / 菜单 / 字典 ===
        if path == "/api/role/page":
            return self._ok({"total": len(ROLES), "current": 1, "size": 10, "records": ROLES})
        if path == "/api/role/list":
            return self._ok(ROLES)

        if path == "/api/menu/tree":
            return self._ok(MENUS)
        if path == "/api/menu/page":
            return self._ok({"total": len(MENUS), "current": 1, "size": 20, "records": MENUS})

        if path == "/api/dict/list":
            t = qs.get("type", [""])[0]
            if t:
                items = [d for d in DICTS if d["type"] == t]
                return self._ok(items)
            return self._ok(DICTS)

        # === 系统 / Dashboard / Monitor ===
        if path == "/api/system/dashboard":
            return self._ok({
                "stats": {
                    "users": len(USERS), "agents": len(AGENTS),
                    "models": len(MODELS), "workflows": len(WORKFLOWS),
                    "knowledgeBases": len(KNOWLEDGE_BASES),
                },
                "todayQueries": 145, "todayOrders": 23,
                "activeUsers": 12, "onlineUsers": 5,
            })

        if path == "/api/admin/dashboard" or path == "/api/biz/dashboard":
            return self._ok({
                "users": 156, "todayActiveUsers": 12, "onlineUsers": 5,
                "todayQueries": 145, "todayOrders": 23, "todayRevenue": 89456.78,
                "totalModels": 4, "activeWorkflows": 8, "runsToday": 56,
                "tokensToday": 234567, "avgLatencyMs": 320, "errorRate": 0.005,
                "byDay": [
                    {"date": (datetime.now() - timedelta(days=i)).strftime("%Y-%m-%d"),
                     "all": 100 + i*5, "user": 60 + i*3, "tool": 30 + i*2}
                    for i in range(7)
                ],
                "modelDistribution": [
                    {"label": "mock", "value": 45}, {"label": "onnx", "value": 30},
                    {"label": "ollama", "value": 15}, {"label": "http", "value": 10},
                ],
            })

        if path == "/api/monitor/snapshot":
            services = []
            for s in SERVICES:
                services.append({
                    **s, "status": "up" if random.random() > 0.1 else "down",
                    "responseMs": random.randint(10, 100) if random.random() > 0.1 else 0,
                    "detail": "HTTP 200 · {}ms".format(random.randint(10, 100)) if random.random() > 0.1 else "连接失败: ConnectException",
                })
            return self._ok({
                "ts": int(time.time() * 1000),
                "services": services,
                "counters": {
                    "totalRequests": 12345, "totalErrors": 23,
                    "totalAiCalls": 567, "totalWorkflowRuns": 89, "totalTokens": 234567,
                    "errorRate": 0.018,
                },
                "business": {"activeUsers": 12, "onlineUsers": 5, "todayOrders": 23, "todayQueries": 145},
                "ai": {"modelsLoaded": 4, "activeWorkflows": 8, "runsToday": 56, "tokensToday": 234567, "avgLatencyMs": 320},
                "alerts": [],
            })

        if path == "/api/activity/recent":
            return self._ok(ACTIVITY[:10])

        if path == "/api/audit/login/page":
            return self._ok({"total": 100, "current": 1, "size": 10,
                             "records": [{
                                 "id": i+1, "username": random.choice(["admin","demo","manager"]),
                                 "ip": "127.0.0.1", "status": random.choice([1, 0]),
                                 "message": "登录成功" if random.random() > 0.1 else "密码错误",
                                 "createTime": (datetime.now() - timedelta(hours=i)).strftime("%Y-%m-%dT%H:%M:%S")
                             } for i in range(10)]})

        if path == "/api/audit/login/stats":
            return self._ok({"total": 100, "success": 92, "fail": 8})
        if path == "/api/audit/login/trend":
            return self._ok([{"date": (datetime.now() - timedelta(days=i)).strftime("%Y-%m-%d"),
                              "total": 20 + i*2, "success": 18 + i*2, "fail": 2} for i in range(7)])

        if path == "/api/audit/operation/page":
            return self._ok({"total": 50, "current": 1, "size": 10,
                             "records": [{
                                 "id": i+1, "username": "admin", "module": random.choice(["user","agent","knowledge"]),
                                 "operation": random.choice(["CREATE","UPDATE","DELETE"]),
                                 "description": "操作描述", "ip": "127.0.0.1",
                                 "costMs": random.randint(10, 500), "status": "SUCCESS",
                                 "createTime": (datetime.now() - timedelta(minutes=i*30)).strftime("%Y-%m-%dT%H:%M:%S")
                             } for i in range(10)]})

        if path == "/api/audit/operation/stats":
            return self._ok({"total": 50, "byDay": [{"date": TODAY, "count": 50}]})

        # === 业务全链路 ===
        if path.startswith("/api/biz/") and ("page" in path or "list" in path):
            kind = path.split("/")[3]  # customer / order / etc
            data = BIZ_DATA.get(kind, BIZ_DATA["customer"])
            return self._ok({"total": len(data), "current": 1, "size": 10, "records": data})

        if path.startswith("/api/biz/") and path.endswith("/stats"):
            return self._ok({"total": 100, "active": 80, "inactive": 20})

        # === Agent / Model / Knowledge / Workflow ===
        if path == "/api/agent/page" or path == "/api/agent/list":
            return self._ok({"total": len(AGENTS), "current": 1, "size": 10, "records": AGENTS})
        if path.startswith("/api/agent/") and method == "GET":
            try:
                aid = int(path.split("/")[-1])
                a = next((x for x in AGENTS if x["id"] == aid), AGENTS[0])
                return self._ok(a)
            except ValueError:
                pass
        if path == "/api/agent/cases":
            return self._ok([
                {"id": 1, "caseKey": "rag-qa-demo", "title": "企业知识库问答", "summary": "RAG 智能问答",
                 "domain": "knowledge", "featured": 1},
                {"id": 2, "caseKey": "marketing-copy", "title": "营销文案生成", "summary": "多平台文案",
                 "domain": "marketing", "featured": 0},
                {"id": 3, "caseKey": "legal-review", "title": "合同风险审查", "summary": "AI 合同审查",
                 "domain": "legal", "featured": 0},
            ])

        if path == "/api/model/list" or path == "/api/model/page":
            return self._ok({"total": len(MODELS), "current": 1, "size": 10, "records": MODELS})

        if path == "/api/knowledge/page" or path == "/api/knowledge/base/list":
            return self._ok({"total": len(KNOWLEDGE_BASES), "current": 1, "size": 10, "records": KNOWLEDGE_BASES})

        if path == "/api/workflow/page" or path == "/api/workflow/list" or path == "/api/workflow/spec/page":
            return self._ok({"total": len(WORKFLOWS), "current": 1, "size": 10, "records": WORKFLOWS})

        # === AI 后端管理 ===
        if path == "/api/ai/backend":
            return self._ok({
                "chat": {"active": "mock", "available": ["mock", "internal", "onnx", "ollama", "http"]},
                "search": {"mode": "internal", "available": ["internal", "external"]},
                "healthy": True,
            })
        if path == "/api/ai/chat":
            prompt = body.get("prompt", "") or (qs.get("prompt", [""])[0] if "prompt" in qs else "")
            return self._ok({
                "response": f"[Mock AI · 离线演示] 已收到 prompt ({len(prompt)} 字). 配置 aiplatform.ai.backend=onnx 切换到真本地 ONNX 模型.",
                "tokens": 42, "latencyMs": 156,
            })
        if path == "/api/ai/embed":
            text = body.get("text", "")
            v = [0.0] * 512
            for i, c in enumerate(text[:512]):
                v[i] = (ord(c) % 128) / 128.0
            return self._ok({"vector": v, "dim": 512})
        if path == "/api/ai/web-search":
            q = qs.get("q", [""])[0]
            return self._ok([{
                "title": f"[Mock] 关于 '{q}' 的搜索结果",
                "url": f"local://mock/{q}",
                "snippet": f"这是 mock 后端针对 '{q}' 的内置返回结果. 真实环境下应走内部 RAG 或外部 DuckDuckGo.",
                "score": 0.85,
            }])

        # === 分布式演示 ===
        if path == "/api/distributed/snapshot":
            return self._ok({
                "lock": {"available": True, "held": 0, "waiters": 0},
                "id": {"node": "mock-node-01", "snowflakeEpoch": 1288834974657},
                "rateLimit": {"qps": 100, "current": 5, "available": 95},
                "idempotent": {"keys": 0, "hits": 0},
                "cache": {"size": 0, "hits": 0, "misses": 0},
                "event": {"subscribers": 0, "published": 0},
                "schedule": {"tasks": 0, "running": 0},
            })
        if path == "/api/distributed/info":
            return self._ok({
                "name": "7大分布式能力", "redisAvailable": True,
                "redissonVersion": "3.27.2",
                "redisAddress": "127.0.0.1:6379 (mock)",
            })

        # === 工具/角色/系统配置 catch-all ===
        if path.startswith("/api/role/") and method == "GET":
            return self._ok(ROLES[0])
        if path.startswith("/api/menu/") and method == "GET":
            return self._ok(MENUS[0])

        # === 通配 catch-all (避免 404) ===
        if method == "GET":
            return self._ok([])  # 列表空
        if method in ("POST", "PUT", "DELETE"):
            return self._ok({"id": 1, "updated": True})

        return self._err(404, f"Mock backend: 路径未实现 {method} {path}", 404)


if __name__ == "__main__":
    server = http.server.HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[mock-backend] listening on http://0.0.0.0:{PORT}")
    print(f"[mock-backend] 前端 vite 配置 proxy '/api' -> 'http://127.0.0.1:{PORT}'")
    print(f"[mock-backend] 测试账号: admin/admin123, demo/demo123, manager/manager123")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock-backend] shutdown")
