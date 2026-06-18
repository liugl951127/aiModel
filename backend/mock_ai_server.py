#!/usr/bin/env python3
"""
AI 极速生成 Mock Server
完全模拟 Spring Boot AiWorkflowController 的 4 个接口
不依赖 nacos / Redis / MySQL, 纯 Python 起一个 HTTP 服务

启动: python3 mock_ai_server.py 9000
"""
import http.server
import json
import sys
import re
from urllib.parse import urlparse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9000

# ============================================================
# 跟 Java AiWorkflowGenerator 行为完全一致的 Python 实现
# ============================================================

SCENARIOS = [
    {"key": "rag", "name": "RAG 知识库问答", "icon": "📚",
     "input": "做一个 RAG 知识库问答, 用 BGE 中文嵌入",
     "desc": "文档入库 → 向量检索 → AI 回答"},
    {"key": "lora_train", "name": "LoRA 模型微调", "icon": "⚙️",
     "input": "训练个营销文案的 LoRA, epochs=3",
     "desc": "数据加载 → 切片 → LoRA 训练 → 评估 → 注册"},
    {"key": "marketing", "name": "营销文案生成", "icon": "✍️",
     "input": "写小红书/公众号/抖音 3 平台营销文案",
     "desc": "需求解析 → AI 创作 → 多平台改写"},
    {"key": "customer_service", "name": "客服自动回复", "icon": "🎧",
     "input": "做一个客服自动回复 + 工单流程",
     "desc": "意图识别 → 检索 → 回复 + 工单"},
    {"key": "contract_review", "name": "合同风险审查", "icon": "📜",
     "input": "审查合同风险条款, 给出修改建议",
     "desc": "解析 → 识别 → 标注 → 建议"},
    {"key": "etl", "name": "数据 ETL 流水线", "icon": "🔄",
     "input": "把 MySQL 数据 ETL 到 ClickHouse, 每天同步",
     "desc": "源数据 → 清洗 → 转换 → 入库"},
    {"key": "evaluate", "name": "模型评估流水线", "icon": "🧪",
     "input": "评估 RAG 系统的幻觉率和准确率",
     "desc": "模型 → BLEU → 幻觉 → 报告"},
    {"key": "agent", "name": "智能体 ReAct", "icon": "🤖",
     "input": "做一个能调工具的智能体",
     "desc": "思考 → 工具调用 → 反思 → 输出"},
    {"key": "deploy", "name": "模型部署上线", "icon": "🚀",
     "input": "把模型部署上线, 灰度 10%",
     "desc": "注册 → ONNX → 部署 → 灰度"}
]

SCENARIO_DEFS = {
    "rag": {
        "name": "RAG 知识库问答",
        "description": "文档入库 → 向量检索 → AI 回答 (引用溯源)",
        "nodes": [("kb_ingest", "文档入库"), ("kb_search", "向量检索"), ("agent_think", "AI 回答")]
    },
    "lora_train": {
        "name": "LoRA 模型微调",
        "description": "数据加载 → 数据切片 → LoRA 训练 → 评估 → 注册",
        "nodes": [("data_loader", "数据加载"), ("data_split", "数据切片"),
                  ("lora_train", "LoRA 训练"), ("eval_bleu", "评估指标"), ("model_register", "注册模型")]
    },
    "marketing": {
        "name": "营销文案生成",
        "description": "需求解析 → AI 创作 → 多平台改写",
        "nodes": [("agent_think", "需求解析"), ("infer_chat", "AI 文案"), ("infer_chat", "多平台改写")]
    },
    "customer_service": {
        "name": "客服自动回复",
        "description": "用户提问 → 意图识别 → 知识库检索 → 自动回复 + 工单",
        "nodes": [("agent_think", "意图识别"), ("kb_search", "知识库检索"),
                  ("infer_chat", "自动回复"), ("webhook", "建工单")]
    },
    "contract_review": {
        "name": "合同风险审查",
        "description": "合同解析 → 条款识别 → 风险标注 → 修改建议",
        "nodes": [("chunker", "合同解析"), ("infer_chat", "条款识别"),
                  ("infer_chat", "风险标注"), ("infer_chat", "修改建议")]
    },
    "etl": {
        "name": "数据 ETL 流水线",
        "description": "源数据 → 抽取清洗 → 转换 → 入库",
        "nodes": [("data_loader", "源数据"), ("data_clean", "数据清洗"),
                  ("data_split", "数据转换"), ("vector_index", "入库")]
    },
    "evaluate": {
        "name": "模型评估流水线",
        "description": "模型加载 → 测试集评估 → 多维度打分 → 报告",
        "nodes": [("model_list", "模型加载"), ("eval_bleu", "BLEU 评估"),
                  ("eval_hallucination", "幻觉检测"), ("eval_rouge", "评估报告")]
    },
    "agent": {
        "name": "智能体 ReAct",
        "description": "用户输入 → 思考 → 工具调用 → 反思 → 输出",
        "nodes": [("agent_think", "智能体思考"), ("agent_tool", "工具调用"),
                  ("agent_think", "反思推理"), ("infer_chat", "最终回答")]
    },
    "deploy": {
        "name": "模型部署上线",
        "description": "模型注册 → 转换 ONNX → 部署 → 灰度",
        "nodes": [("model_register", "模型注册"), ("model_deploy", "ONNX 导出"),
                  ("model_deploy", "部署服务"), ("infer_generate", "灰度调用")]
    }
}

CN_NUM = {"一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5,
          "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}

def parse_cn(s):
    if not s:
        return -1
    try:
        return int(s)
    except Exception:
        return CN_NUM.get(s, -1)

def detect_scenario(text):
    """关键词权重匹配, 跟 Java 端完全一致"""
    text = text.lower()
    scores = {}
    for key, sc in SCENARIO_DEFS.items():
        score = 0
        if key in text:
            score += 2
        for kw in get_keywords(key):
            if kw in text:
                score += 1
        scores[key] = score
    best = max(scores, key=scores.get)
    if scores[best] == 0:
        return "custom"
    return best

def get_keywords(key):
    return {
        "rag": ["知识库", "rag", "文档问答", "检索", "问答", "私域知识", "内部知识"],
        "lora_train": ["训练", "lora", "微调", "finetune", "sft", "指令微调", "继续训练", "增量训练"],
        "marketing": ["营销", "文案", "小红书", "公众号", "抖音", "短视频", "推广", "种草", "带货", "爆款"],
        "customer_service": ["客服", "售后", "自动回复", "工单", "客户支持", "用户提问"],
        "contract_review": ["合同", "法务", "风险", "条款", "审查", "审核", "合规"],
        "etl": ["etl", "同步", "抽取", "数仓", "数据集成", "数据迁移", "数据流水线"],
        "evaluate": ["评估", "评测", "benchmark", "幻觉", "准确率", "测试", "打榜", "ragas"],
        "agent": ["智能体", "agent", "工具调用", "react", "function call", "代理", "助手"],
        "deploy": ["部署", "上线", "onnx", "发布", "灰度", "canary", "导出模型"],
    }.get(key, [])

def extract_node_type(text):
    """多轮 add_node 时, 从中文识别节点 type"""
    s = (text or "").strip()
    if "评估" in s: return "eval_bleu"
    if "训练" in s or "lora" in s.lower(): return "lora_train"
    if "检索" in s or "搜索" in s or "rag" in s.lower() or "知识库" in s: return "kb_search"
    if "入库" in s or "ingest" in s.lower(): return "kb_ingest"
    if "切片" in s or "分片" in s: return "kb_chunk"
    if "向量化" in s or "embed" in s.lower(): return "kb_embed"
    if "思考" in s or "推理" in s: return "agent_think"
    if "回答" in s or "chat" in s.lower(): return "agent_chat"
    if "部署" in s: return "model_deploy"
    if "注册" in s: return "model_register"
    if "数据" in s: return "data_loader"
    if "清洗" in s: return "data_clean"
    return None

def apply_keyword_overrides(params, text):
    m = re.search(r"top[_\-]?k[=\s]+(\d+)", text, re.IGNORECASE)
    if m: params["topK"] = int(m.group(1))
    m = re.search(r"chunk[_\-]?size[=\s]+(\d+)", text, re.IGNORECASE)
    if m: params["chunkSize"] = int(m.group(1))
    m = re.search(r"(?:lr|learning[_\-]?rate)[=\s]+([\d.]+)", text, re.IGNORECASE)
    if m: params["learningRate"] = float(m.group(1))
    m = re.search(r"(?:epoch|epochs|轮次)[=\s]+(\d+)", text, re.IGNORECASE)
    if m: params["epochs"] = int(m.group(1))
    return params

def parse_params_text(text):
    """topK=N / epochs=N / lr=N 这种语法, 返回 Map"""
    p = {}
    m = re.search(r"(top[_\-]?k|chunk[_\-]?size|epochs?|lr|learning[_\-]?rate|max[_\-]?tokens?)\s*[=:]\s*([\d.]+)", text, re.IGNORECASE)
    while m:
        key = m.group(1).lower().replace("_", "")
        val = m.group(2)
        if key.startswith("topk"): key = "topK"
        elif key.startswith("chunksize"): key = "chunkSize"
        elif key.startswith("learningrate"): key = "learningRate"
        elif key.startswith("maxtokens"): key = "maxTokens"
        p[key] = float(val) if "." in val else int(val)
        m = re.search(r"(top[_\-]?k|chunk[_\-]?size|epochs?|lr|learning[_\-]?rate|max[_\-]?tokens?)\s*[=:]\s*([\d.]+)", text[m.end():], re.IGNORECASE)
    return p

def build_new(input_text, user_input, action="replace"):
    key = detect_scenario(input_text)
    if key == "custom":
        return {
            "name": "自定义流程",
            "description": "数据加载 + AI 思考 — 通用模板",
            "scenario": "custom",
            "confidence": 0,
            "action": action,
            "userInput": user_input,
            "nodes": [
                {"id": "n1", "type": "data_loader", "name": "数据加载", "x": 100, "y": 100,
                 "params": {"format": "jsonl"}},
                {"id": "n2", "type": "agent_think", "name": "AI 思考", "x": 360, "y": 100,
                 "params": {"temperature": 0.7}}
            ],
            "edges": [{"from": "n1", "to": "n2"}]
        }
    sc = SCENARIO_DEFS[key]
    nodes = []
    edges = []
    x = 100
    y = 100
    prev_id = None
    for i, (ntype, nname) in enumerate(sc["nodes"]):
        nid = f"n{i+1}"
        params = {}
        # 默认参数
        if ntype == "kb_search":
            params = {"topK": 5, "threshold": 0.7}
        elif ntype == "kb_ingest":
            params = {"chunkSize": 256, "overlap": 32}
        elif ntype == "lora_train":
            params = {"epochs": 3, "learningRate": 0.001, "batchSize": 12}
        elif ntype == "agent_think":
            params = {"temperature": 0.7, "maxSteps": 5}
        elif ntype == "data_loader":
            params = {"format": "jsonl", "source": "/data/"}
        elif ntype == "data_split":
            params = {"trainRatio": 0.8, "valRatio": 0.2}
        elif ntype == "eval_bleu":
            params = {"maxNgram": 4}
        # 应用用户关键词覆盖
        apply_keyword_overrides(params, user_input)
        nodes.append({
            "id": nid, "type": ntype, "name": nname,
            "x": x, "y": y, "params": params
        })
        if prev_id:
            edges.append({"from": prev_id, "to": nid})
        prev_id = nid
        x += 260
    return {
        "name": sc["name"],
        "description": sc["description"],
        "scenario": key,
        "confidence": 3,
        "action": action,
        "userInput": user_input,
        "nodes": nodes,
        "edges": edges
    }

def modify_existing(input_text, current, user_input):
    # 1) replace
    if re.search(r"(换成|改成|变成|重做|不要了|重新|从新|重起)", input_text):
        return build_new(input_text, user_input, action="replace")

    # 2) delete
    m = re.search(r"(删掉|删除|去掉|移除)\s*[第]?(\d+|[一二三四五六七八九十]+|最后[一二三四五六七八九十\d]*)?\s*个?\s*节点?", input_text)
    if m:
        target = m.group(2)
        nodes = [dict(n) for n in current.get("nodes", [])]
        edges = [dict(e) for e in current.get("edges", [])]
        idx = -1
        if target and target.startswith("最"):
            idx = len(nodes)
        else:
            idx = parse_cn(target) if target else -1
        if idx > 0 and idx <= len(nodes):
            removed_id = nodes[idx-1]["id"]
            nodes.pop(idx-1)
            edges = [e for e in edges if e["from"] != removed_id and e["to"] != removed_id]
        elif not target and nodes:
            removed_id = nodes[-1]["id"]
            nodes.pop()
            edges = [e for e in edges if e["from"] != removed_id and e["to"] != removed_id]
        r = dict(current)
        r["nodes"] = nodes
        r["edges"] = edges
        r["userInput"] = user_input
        r["action"] = "delete_node"
        return r

    # 3) add
    m = re.search(r"(多加|多|加|补|插入|添)\s*(\d+|[一二三四五六七八九十]+)?\s*个?\s*(.*)", input_text)
    if m:
        num = m.group(2)
        what = m.group(3)
        count = parse_cn(num)
        if count <= 0: count = 1
        ntype = extract_node_type(what)
        if ntype:
            nodes = [dict(n) for n in current.get("nodes", [])]
            edges = [dict(e) for e in current.get("edges", [])]
            prev_id = nodes[-1]["id"] if nodes else None
            x = nodes[-1]["x"] + 260 if nodes else 100
            y = nodes[-1]["y"] if nodes else 100
            for i in range(count):
                nid = f"n{len(nodes)+1}"
                nodes.append({
                    "id": nid, "type": ntype, "name": ntype,
                    "x": x, "y": y, "params": {}
                })
                if prev_id:
                    edges.append({"from": prev_id, "to": nid})
                prev_id = nid
                x += 260
            r = dict(current)
            r["nodes"] = nodes
            r["edges"] = edges
            r["userInput"] = user_input
            r["action"] = "add_node"
            return r

    # 4) update params
    if "topk" in input_text or "chunk" in input_text or "epoch" in input_text or "lr=" in input_text:
        nodes = [dict(n) for n in current.get("nodes", [])]
        for n in nodes:
            params = dict(n.get("params", {}))
            params.update(parse_params_text(input_text))
            n["params"] = params
        r = dict(current)
        r["nodes"] = nodes
        r["userInput"] = user_input
        r["action"] = "update_params"
        return r

    # 5) fallback replace
    return build_new(input_text, user_input, action="replace")

# ============================================================
# HTTP Handler
# ============================================================

class MockHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # 安静一点
        pass

    def send_json(self, code, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/workflow/ai-scenarios":
            # 跟 Result<List<Map>> 一致
            self.send_json(200, {
                "code": 200, "message": "ok",
                "data": SCENARIOS,
                "timestamp": 1234567890
            })
        elif path == "/api/workflow/component-schemas/lora_train":
            # 模拟 schema
            self.send_json(200, {
                "code": 200, "message": "ok",
                "data": {
                    "id": "lora_train",
                    "name": "LoRA 训练",
                    "fields": [
                        {"key": "epochs", "label": "训练轮次", "type": "number", "min": 1, "max": 100, "defaultValue": 3},
                        {"key": "learningRate", "label": "学习率", "type": "number", "min": 0.0001, "max": 0.1, "step": 0.0001, "defaultValue": 0.001},
                        {"key": "batchSize", "label": "批大小", "type": "number", "min": 1, "max": 128, "defaultValue": 12}
                    ]
                }
            })
        elif path == "/api/auth/health":
            self.send_json(200, {"code": 200, "message": "ok", "data": {"status": "UP"}})

        # === E2E 贯通路径 (其他模块, 业务页面调用) ===
        elif path == "/api/workflow/spec/list":
            self.send_json(200, {"code": 200, "message": "ok", "data": [
                {"id": 1, "name": "E2E LoRA", "author": "e2e-test", "nodeCount": 5, "edgeCount": 4, "runCount": 0, "createTime": "2026-06-17", "updateTime": "2026-06-17"}
            ]})
        elif path == "/api/workflow/runs":
            # ★ WorkflowList.loadRuns 期望 dict (Object.entries), 返 dict
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "run-1": {"specName": "E2E LoRA", "status": "SUCCEEDED", "progress": 100, "currentStep": "infer", "startedAt": "2026-06-17T10:00:00", "finishedAt": "2026-06-17T10:01:00", "durationMs": 60000, "source": "db"}
            }})
        else:
            # ★ catch-all: 所有未明确定义的 GET 路径返 OK + 空数据 (供前端走通)
            if "/page" in path or "/search" in path:
                # 分页接口: 返 {records, total, ...}
                self.send_json(200, {"code": 200, "message": "ok", "data": {"records": [], "total": 0, "current": 1, "size": 10}})
            elif path.endswith("/list") or path.endswith("/all") or path.endswith("/tree") or path.endswith("/recent") or path.endswith("/stats") or path.endswith("/health"):
                # 列表/单字段接口: 返空 dict (前端不报错但可能空数据)
                self.send_json(200, {"code": 200, "message": "ok", "data": {}})
            else:
                # 详情/对象: 返空 dict
                self.send_json(200, {"code": 200, "message": "ok (mock)", "data": {}})

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", 0))
        body_raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try:
            body = json.loads(body_raw) if body_raw else {}
        except Exception:
            body = {}

        # 模拟 AI 处理耗时 (1.2-2s)
        import time
        time.sleep(1.2)

        if path == "/api/workflow/ai-generate":
            user_input = body.get("input", "")
            if not user_input.strip():
                self.send_json(200, {
                    "code": 200, "message": "ok",
                    "data": {
                        "name": "空流程", "description": "请描述你的需求",
                        "scenario": "empty", "confidence": 0, "action": "replace",
                        "userInput": user_input, "nodes": [], "edges": []
                    }
                })
                return
            wf = build_new(user_input.lower(), user_input, action="replace")
            self.send_json(200, {"code": 200, "message": "ok", "data": wf, "timestamp": 1234567890})

        elif path == "/api/workflow/ai-modify":
            user_input = body.get("input", "")
            current = body.get("current", {})
            wf = modify_existing(user_input.lower(), current, user_input)
            self.send_json(200, {"code": 200, "message": "ok", "data": wf, "timestamp": 1234567890})

        # === E2E 贯通路径 ===
        elif path == "/api/dataset":
            # 创建数据集
            new_id = 1000 + len(body.get("datasetCode", "")) % 100
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "id": new_id, "tenantId": 1, **body, "createTime": "2026-06-17T10:00:00"
            }})
        elif path == "/api/trainer/submit":
            # 提交训练
            import uuid
            job_id = uuid.uuid4().hex[:8]
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "jobId": job_id, "status": "queued", "progress": 0, "message": "训练任务已启动"
            }})
        elif path.startswith("/api/model/export/") and path.endswith("/formats"):
            self.send_json(200, {"code": 200, "message": "ok", "data": ["onnx", "gguf", "pytorch"]})
        elif path == "/api/model/export/1" or path.startswith("/api/model/export/"):
            # ONNX/GGUF/PyTorch 导出
            fmt = "onnx"
            if "format=gguf" in self.path: fmt = "gguf"
            elif "format=pytorch" in self.path: fmt = "pytorch"
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "modelId": 1, "format": fmt, "bundlePath": f"/opt/ai-platform/exports/model-{fmt}.zip",
                "fileName": f"model-{fmt}.zip", "sizeBytes": 1024 * 1024,
                "downloadUrl": f"/api/model/export/1/download?format={fmt}"
            }})
        elif path == "/api/workflow/spec":
            new_id = 999
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "id": new_id, "name": body.get("name", "未命名"), "author": "e2e-test",
                "nodeCount": len(body.get("nodes", [])), "edgeCount": len(body.get("edges", [])),
                "runCount": 0, "createTime": "2026-06-17T10:00:00", "updateTime": "2026-06-17T10:00:00"
            }})
        elif path == "/api/workflow/run":
            import uuid
            run_id = uuid.uuid4().hex[:8]
            self.send_json(200, {"code": 200, "message": "ok", "data": run_id})
        elif path == "/api/inference/generate":
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "text": "你好! 我是 E2E 测试的 AI 助手, 很高兴认识你。", "tokens": 18, "durationMs": 234
            }})
        elif path == "/api/files/chunk/init":
            import uuid
            upload_id = uuid.uuid4().hex
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "uploadId": upload_id, "totalChunks": 1, "chunkSize": 5242880
            }})
        elif path == "/api/distributed/lock":
            self.send_json(200, {"code": 200, "message": "ok", "data": {
                "lockKey": body.get("orderId", "unknown"),
                "acquired": True, "token": "e2e-token-" + str(int(time.time())),
                "ttlMs": 30000
            }})

        else:
            # ★ catch-all: 未明确定义的 POST 返 OK (供前端走通, 实际环境会返 404)
            self.send_json(200, {"code": 200, "message": "ok (mock catch-all)", "data": {"id": 1}})

    def do_PUT(self):
        # ★ catch-all PUT
        from urllib.parse import urlparse
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", 0))
        body_raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try: body = json.loads(body_raw) if body_raw else {}
        except: body = {}
        self.send_json(200, {"code": 200, "message": "ok (mock catch-all PUT)", "data": body or {"id": 1}})

    def do_DELETE(self):
        # ★ catch-all DELETE
        from urllib.parse import urlparse
        path = urlparse(self.path).path
        self.send_json(200, {"code": 200, "message": "ok (mock catch-all DELETE)", "data": None})

    def do_OPTIONS(self):
        # CORS preflight
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type,Authorization")
        self.end_headers()


if __name__ == "__main__":
    server = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), MockHandler)
    print(f"[MOCK] AI 极速生成 mock server 启动: http://127.0.0.1:{PORT}")
    print(f"[MOCK]  端点:")
    print(f"[MOCK]    GET  /api/workflow/ai-scenarios")
    print(f"[MOCK]    POST /api/workflow/ai-generate  body: {{input: '...'}}")
    print(f"[MOCK]    POST /api/workflow/ai-modify    body: {{input, current}}")
    print(f"[MOCK]    GET  /api/workflow/component-schemas/lora_train")
    print(f"[MOCK]  Ctrl+C 退出")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[MOCK] 退出")
