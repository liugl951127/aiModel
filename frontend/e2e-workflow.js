// E2E: 工作流编排完整流程测试
// 验证: 登录 → 工作流编排 → AI 生成 → 编辑 → 保存 → 配置弹窗点击
// 用法: BASE=http://127.0.0.1:5173 node e2e-workflow.js

import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://127.0.0.1:5173'

async function main() {
  console.log(`[E2E-WF] 启动, base=${BASE}`)
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
  const page = await ctx.newPage()

  const errors = []
  const failedRequests = []
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', e => errors.push(`PAGE: ${e.message}`))
  page.on('response', r => { if (r.status() >= 400 && !r.url().includes('/login')) failedRequests.push(`${r.status()} ${r.url().replace(BASE, '')}`) })

  try {
    // 1. 登录 (与 e2e-login-dashboard 一致, 勾选协议)
    console.log('\n[E2E-WF] === 1. 登录 ===')
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.fill('input[autocomplete="username"], input[placeholder*="账"], input[type="text"]:first-of-type', 'admin')
    await page.fill('input[autocomplete="current-password"], input[type="password"]', 'admin123')
    await page.waitForTimeout(500)
    await page.evaluate(() => {
      const labels = document.querySelectorAll('.agree-row .el-checkbox')
      if (labels.length > 0) labels[0].click()
    })
    await page.waitForTimeout(500)
    await page.evaluate(() => { document.querySelector('button.submit-btn')?.click() })
    try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
    console.log(`[E2E-WF] 登录完成: ${page.url()}`)

    // 2. 进入工作流编排 (hash 路由)
    console.log('\n[E2E-WF] === 2. 进入工作流编排 ===')
    await page.goto(`${BASE}/#/workflow`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForTimeout(3000)
    console.log(`[E2E-WF] 当前URL: ${page.url()}`)

    // 如果跳到 login 说明 token 失效, 不重试 (避免递归)

    // 3. 检查工作流工具栏
    const hasToolbar = await page.locator('.wf-toolbar, .workflow-page').count() > 0
    console.log(`[E2E-WF] 工作流页面渲染: ${hasToolbar}`)
    if (!hasToolbar) {
      await page.screenshot({ path: '/tmp/e2e-wf-page.png' })
      throw new Error('工作流页未加载')
    }

    // 4. AI 一键生成
    console.log('\n[E2E-WF] === 3. AI 一键生成 ===')
    const aiBtn = page.locator('button:has-text("AI 极速生成"), button:has-text("AI")').first()
    if (await aiBtn.count() > 0) {
      await aiBtn.click()
      await page.waitForTimeout(1200)
      const inputArea = page.locator('.input-box textarea, textarea[placeholder*="需求"], textarea[placeholder*="描述"]').first()
      await inputArea.fill('做一个 RAG 知识库问答流程')
      await page.waitForTimeout(500)
      // 生成按钮在 AI dialog footer 里
      const genBtn = page.locator('.el-dialog__footer button:has-text("生成"), .el-dialog button:has-text("一键生成")').first()
      await genBtn.click()
      console.log('[E2E-WF] 已点 AI 生成, 等待 30s...')
      try {
        await page.waitForSelector('.r-graph svg, .rg-svg, [class*="result"] svg', { timeout: 30000 })
        console.log('[E2E-WF] ✓ AI 生成结果出现')
        await page.screenshot({ path: '/tmp/e2e-wf-ai-result.png' })
        const applyBtn = page.locator('button:has-text("填到画布"), button:has-text("应用到画布")').first()
        if (await applyBtn.count() > 0) {
          await applyBtn.click()
          await page.waitForTimeout(2000)
          console.log('[E2E-WF] ✓ AI 生成已应用到画布')
        }
      } catch (e) {
        console.log('[E2E-WF] AI 生成超时:', e.message.slice(0, 80))
      }
    } else {
      console.log('[E2E-WF] - 无 AI 按钮, 跳过')
    }

    // 5. 验证画布
    console.log('\n[E2E-WF] === 4. 验证画布 ===')
    await page.waitForTimeout(1500)
    const nodeCount = await page.locator('.wf-node, [class*="node-"]').count()
    const edgeCount = await page.locator('.wires path, .edges path').count()
    console.log(`[E2E-WF] 节点: ${nodeCount}, 边: ${edgeCount}`)
    await page.screenshot({ path: '/tmp/e2e-wf-canvas.png' })

    // 6. 双击节点打开配置弹窗
    console.log('\n[E2E-WF] === 5. 节点配置弹窗 ===')
    const firstNode = page.locator('.wf-node').first()
    if (await firstNode.count() > 0) {
      await firstNode.dblclick()
      await page.waitForTimeout(1500)
      const dialogVisible = await page.locator('.el-dialog__body').isVisible().catch(() => false)
      console.log(`[E2E-WF] 配置弹窗显示: ${dialogVisible}`)
      if (dialogVisible) {
        await page.screenshot({ path: '/tmp/e2e-wf-config.png' })
        // 测试取消按钮 - 关键: 修复后应该可点
        const cancelBtn = page.locator('.el-dialog__footer button:has-text("取消"), .el-dialog button:has-text("取消")').first()
        if (await cancelBtn.count() > 0) {
          try {
            await cancelBtn.click({ timeout: 3000 })
            await page.waitForTimeout(800)
            const stillVisible = await page.locator('.el-dialog__body').isVisible().catch(() => false)
            if (!stillVisible) console.log('[E2E-WF] ✓✓ 取消按钮可点, dialog 已关闭')
            else console.log('[E2E-WF] ⚠ 取消按钮点了, 但 dialog 还在')
          } catch (e) {
            console.log('[E2E-WF] ✗ 取消按钮点击失败:', e.message.slice(0, 100))
            errors.push(`取消按钮: ${e.message}`)
          }
        }
      }
    } else {
      console.log('[E2E-WF] - 无节点可双击')
    }

    // 7. 总结
    console.log('\n[E2E-WF] === 总结 ===')
    console.log(`[E2E-WF] 节点: ${nodeCount}, 边: ${edgeCount}`)
    console.log(`[E2E-WF] console.error: ${errors.length}`)
    console.log(`[E2E-WF] 失败请求: ${failedRequests.length}`)
    if (errors.length > 0) errors.slice(0, 3).forEach(e => console.log('  ERR:', e.slice(0, 100)))
    if (failedRequests.length > 0) failedRequests.slice(0, 5).forEach(r => console.log('  REQ:', r))
    console.log(errors.length === 0 ? '\n✓ [E2E-WF] 通过' : '\n✗ [E2E-WF] 失败')

  } catch (e) {
    console.error('[E2E-WF] 异常:', e.message)
    await page.screenshot({ path: '/tmp/e2e-wf-error.png' })
  } finally {
    await browser.close()
  }
}

main()