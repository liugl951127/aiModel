#!/usr/bin/env python3
"""
E2E Launcher: 启动 mock-backend (9000) + 静态前端 (5173) + 反向代理
- /api/*  → mock-backend:9000
- 其他    → frontend/dist/
"""
import http.server
import urllib.request
import urllib.error
import threading
import sys
import os
import subprocess
from pathlib import Path

FRONTEND_DIST = Path("/workspace/ai-agent-platform/frontend/dist")
MOCK_PORT = 9000
WEB_PORT = 5173

def start_mock():
    return subprocess.Popen(
        ["python3", "/workspace/ai-agent-platform/frontend/mock-backend.py", str(MOCK_PORT)],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )

class ProxyHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(FRONTEND_DIST), **kwargs)
    
    def do_GET(self):
        if self.path.startswith('/api/') or self.path.startswith('/actuator/'):
            return self._proxy_api()
        if self.path == '/' or not os.path.exists(FRONTEND_DIST / self.path.lstrip('/')):
            # SPA fallback
            self.path = '/index.html'
        return super().do_GET()
    
    def do_POST(self): return self._proxy_api()
    def do_PUT(self): return self._proxy_api()
    def do_DELETE(self): return self._proxy_api()
    
    def _proxy_api(self):
        url = f"http://127.0.0.1:{MOCK_PORT}{self.path}"
        try:
            data = None
            if self.command in ('POST', 'PUT'):
                length = int(self.headers.get('Content-Length', 0))
                if length > 0:
                    data = self.rfile.read(length)
            
            req = urllib.request.Request(url, data=data, method=self.command)
            for k in ['Content-Type', 'Authorization', 'X-Tenant-Id', 'Accept']:
                v = self.headers.get(k)
                if v:
                    req.add_header(k, v)
            
            with urllib.request.urlopen(req, timeout=30) as resp:
                self.send_response(resp.status)
                # ★ 透传所有 header (含 SSE Content-Type), 但跳过 hop-by-hop
                skip = {'transfer-encoding', 'connection', 'content-length'}
                ctype = ''
                for k, v in resp.getheaders():
                    if k.lower() == 'content-type':
                        ctype = v.lower()
                    if k.lower() not in skip:
                        self.send_header(k, v)
                
                # ★ SSE 流式传输: 不要 read() 全部, 分块转发
                if 'text/event-stream' in ctype:
                    self.end_headers()
                    while True:
                        chunk = resp.read(4096)
                        if not chunk:
                            break
                        try:
                            self.wfile.write(chunk)
                            self.wfile.flush()
                        except (BrokenPipeError, ConnectionResetError):
                            break
                else:
                    body = resp.read()
                    self.send_header('Content-Length', str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.end_headers()
            self.wfile.write(e.read() if e.fp else b'')
        except Exception as ex:
            self.send_response(502)
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            self.wfile.write(f"Proxy error: {ex}".encode())
    
    def log_message(self, fmt, *args):
        # 只打错误
        if 'error' in fmt.lower() or '5' in str(args):
            super().log_message(fmt, *args)

if __name__ == '__main__':
    mock = start_mock()
    print(f"[mock] started pid={mock.pid} port={MOCK_PORT}")
    import time; time.sleep(2)
    
    class ReuseServer(http.server.HTTPServer):
        allow_reuse_address = True
    server = ReuseServer(('127.0.0.1', WEB_PORT), ProxyHandler)
    print(f"[web]  serving on http://127.0.0.1:{WEB_PORT}")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        mock.terminate()