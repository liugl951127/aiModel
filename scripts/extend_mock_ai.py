#!/usr/bin/env python3
"""
★ 自动扩展 mock_ai_server.py — 扫描前端所有 /api/... 调用, 自动生成 mock 处理器.

读:
- frontend/src/api/index.js (主要 API 定义)
- frontend/src/views/*.vue (view 里直接调的 request.X)

输出:
- 追加到 backend/mock_ai_server.py (在 do_GET / do_POST 末尾前)
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRONTEND = os.path.join(ROOT, 'frontend')
MOCK = os.path.join(ROOT, 'backend', 'mock_ai_server.py')

# 1) 收集前端所有 API
apis = defaultdict(set)  # method -> set of path
for root, _, files in os.walk(FRONTEND):
    if 'node_modules' in root or 'dist' in root: continue
    for f in files:
        if not f.endswith(('.js', '.vue')): continue
        path = os.path.join(root, f)
        try:
            content = open(path, encoding='utf-8', errors='ignore').read()
        except: continue
        # 找 request.method('path') 或 obj.method('path')
        for m in ['get', 'post', 'put', 'delete']:
            for p in re.findall(rf"(?:request|api|\w+Api)\.{m}\(\s*['\"`]([^'\"`]+)['\"`]", content):
                if '${' in p or '`' in p: continue
                apis[m.upper()].add(p.split('?')[0])

# 2) 看现有 mock 已支持什么
mock_content = open(MOCK, encoding='utf-8').read()
mock_paths = set()
for m in re.findall(r'path\s*==\s*["\']([^"\']+)["\']', mock_content):
    mock_paths.add(m)
for m in re.findall(r'path\.startswith\(["\']([^"\']+)["\']', mock_content):
    mock_paths.add(m + '*')

# 3) 生成新 handler
new_get = []
new_post = []
new_put = []
new_delete = []

# 简单的 default 响应模板
def gen_handler(method, path, idx):
    if '/page' in path or path.endswith('/list') or path.endswith('/all'):
        return f'            elif path == "{path}":\n                self._send_json(200, {{"code":200,"message":"ok","data":{{"records":[],"total":0,"current":1,"size":10}}}})\n'
    if method == 'GET' and ('/stats' in path or '/health' in path):
        return f'            elif path == "{path}":\n                self._send_json(200, {{"code":200,"message":"ok","data":{{"status":"UP","total":0}}}})\n'
    if method == 'POST' and '/create' not in path and path.count('/') < 5 and not re.search(r'/\d+', path):
        # 简单 POST → 返 id
        return f'            elif path == "{path}":\n                self._send_json(200, {{"code":200,"message":"ok","data":{{"id":1}}}})\n'
    if method == 'PUT' and not re.search(r'/\d+', path):
        return f'            elif path == "{path}":\n                self._send_json(200, {{"code":200,"message":"ok","data":{{}}}}) \n'
    if method == 'DELETE' and re.search(r'/\d+', path):
        return f'            elif path.startswith("{re.sub(r"/\\d+.*$", "/", path)}") and re.match(r".*\\\\d+", path.split("/")[-1]):\n                self._send_json(200, {{"code":200,"message":"ok","data":None}})\n'
    # 默认: 返 ok + 空 data
    return f'            elif path == "{path}":\n                self._send_json(200, {{"code":200,"message":"ok","data":{{}}}})\n'

# 简化: 所有未在 mock 里的, 都加一个 fallback handler
# 4) 在 mock_ai_server.py 末尾加一个万能 catch-all
# (我们已经知道现有 17 个, 差 120+, 用 catch-all 一行搞定)

print(f'前端调用 API 总数: {sum(len(v) for v in apis.values())}')
print(f'现有 mock handler: {len(mock_paths)}')

# 写一个"补全"补丁
patch = '''
# ==== 扩展: 通用 fallback (返 ok + 空 data) ====
# 上面已定义的 path 用具体 handler, 其它用这个
def _mock_send_default(self, code=200, data=None):
    if data is None: data = {"records": [], "total": 0}
    self._send_json(code, {"code": code, "message": "ok", "data": data})

# do_GET / do_POST 里所有 elif 末尾加:
#            else:
#                self._mock_send_default()
'''
print(patch)
