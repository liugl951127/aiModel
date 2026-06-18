#!/usr/bin/env python3
"""
★ Mermaid → PNG 渲染器 (用 playwright + CDN mermaid.js)
- 读一个 .md 文件, 提取所有 ```mermaid 块
- 每个块单独渲染成 PNG
- 命名: <md-stem>__<index>__<slug>.png
"""
import asyncio
import re
import sys
from pathlib import Path
from playwright.async_api import async_playwright

MERMAID_HTML = """<!DOCTYPE html>
<html><head>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>
<style>
  body { margin: 0; padding: 20px; background: white; font-family: -apple-system, "Segoe UI", sans-serif; }
  .mermaid { background: white; }
  pre.mermaid-source { display: none; }
</style>
</head><body>
<div class="mermaid">
{code}
</div>
<script>
  mermaid.initialize({ startOnLoad: true, theme: 'default', securityLevel: 'loose', flowchart: { htmlLabels: true, curve: 'basis' }, themeVariables: { fontSize: '14px' } });
</script>
</body></html>"""


def slugify(s, n=30):
    s = re.sub(r'[^a-zA-Z0-9_\u4e00-\u9fa5]', '_', s)
    return s[:n] if len(s) > n else s


async def render_one(page, code, out_path):
    html = MERMAID_HTML.replace('{code}', code)
    await page.set_content(html, wait_until='networkidle')
    # 等 mermaid 渲染完成
    try:
        await page.wait_for_function(
            "() => document.querySelector('.mermaid svg') !== null",
            timeout=15000
        )
    except Exception:
        pass
    await page.wait_for_timeout(500)  # 让动画结束
    elem = await page.query_selector('.mermaid')
    if elem is None:
        print(f"  ✗ render failed: {out_path.name}")
        return False
    await elem.screenshot(path=str(out_path), omit_background=False)
    print(f"  ✓ {out_path.name}")
    return True


async def main(md_path, out_dir):
    md_path = Path(md_path).resolve()
    out_dir = Path(out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    text = md_path.read_text(encoding='utf-8')
    # 找所有 mermaid 块
    blocks = re.findall(r'```mermaid\s*\n(.*?)\n```', text, re.DOTALL)
    if not blocks:
        print("no mermaid blocks found")
        return 0

    # 抽每个图后面紧跟的标题 (作为 filename slug)
    # 用 ```mermaid 前面最近一个 # heading
    headings = re.split(r'^(#{1,3}\s+.+)$', text, flags=re.MULTILINE)
    # headings: ['pre', 'h1', 'body', 'h2', 'body', ...]
    # 找每个 mermaid 块在哪个 body 段
    body_parts = re.split(r'```mermaid\s*\n.*?\n```', text, flags=re.DOTALL)
    # 找 body 段内最近的 h2/h3
    title_for = []
    for body in body_parts:
        lines = body.strip().splitlines()
        title = ''
        for line in reversed(lines):
            m = re.match(r'^#{1,3}\s+(.+)$', line)
            if m:
                title = m.group(1).strip()
                break
        title_for.append(title)
    title_for = title_for[1:]  # 跳过第一个 (mermaid 块在它之前)

    async with async_playwright() as p:
        # 优先用系统 chromium, 没有再下载
        import os
        chromium_dir = '/root/.cache/ms-playwright/chromium-1223/chrome-linux/chrome'
        launch_kwargs = {'headless': True}
        if os.path.exists(chromium_dir):
            launch_kwargs['executable_path'] = chromium_dir
        browser = await p.chromium.launch(**launch_kwargs)
        ctx = await browser.new_context(viewport={'width': 1600, 'height': 1200}, device_scale_factor=2)
        page = await ctx.new_page()
        ok = 0
        stem = md_path.stem
        for i, code in enumerate(blocks):
            title = title_for[i] if i < len(title_for) else ''
            slug = slugify(title or f'diagram_{i+1}')
            out = out_dir / f"{stem}__{i+1:02d}__{slug}.png"
            if await render_one(page, code, out):
                ok += 1
        await browser.close()
    return ok


if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: render_mermaid.py <input.md> <out_dir>")
        sys.exit(1)
    md = sys.argv[1]
    out = sys.argv[2]
    n = asyncio.run(main(md, out))
    print(f"rendered {n} diagram(s)")
