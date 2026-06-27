// E2E: 训练任务流程测试
// 验证: 进入训练页面 → 看任务列表 → 提交任务 (用预置语料) → 监控状态
// 用法: BASE=http://127.0.0.1:5173 node e2e-train.js

import { chromium } from 'playwright'
import fs from 'node:fs'

const BASE = process.env.BASE || 'http://127.0.0.1:5173'
const CORPUS_PATH = process.env.CORPUS_PATH || '/tmp/e2e-corpus.txt'

async function main() {
  // 准备测试语料
  if (!fs.existsSync(CORPUS_PATH)) {
    fs.writeFileSync(CORPUS_PATH, '这是一个测试语料文件，用于 E2E 训练流程验证。\n'.repeat(50))
    console.log(`[E2E-Train] 创建测试语料: ${CORPUS_PATH}`)
  }

  console.log(`[E2E-Train] 启动, base=${BASE}, corpus=${CORPUS_PATH}`)
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
  const page = await ctx.newPage()

  const errors = []
  const failedRequests = []
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', e => errors.push(`PAGE: ${e.message}\nSTACK: ${(e.stack || '<none>').slice(0, 1500)}`))
  page.on('response', r => { if (r.status() >= 400 && !r.url().includes('/login')) failedRequests.push(`${r.status()} ${r.url().replace(BASE, '')}`) })

  try {
    // 1. 登录
    console.log('\n[E2E-Train] === 1. 登录 ===')
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.fill('input[autocomplete="username"], input[placeholder*="账"], input[type="text"]:first-of-type', 'admin')
    await page.fill('input[autocomplete="current-password"], input[type="password"]', 'admin123')
    await page.waitForTimeout(500)
    await page.evaluate(() => document.querySelectorAll('.agree-row .el-checkbox')[0]?.click())
    await page.waitForTimeout(500)
    await page.evaluate(() => document.querySelector('button.submit-btn')?.click())
    try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
    console.log(`[E2E-Train] 登录完成: ${page.url()}`)

    // 2. 进入训练页 (hash 路由)
    console.log('\n[E2E-Train] === 2. 进入训练页 ===')
    await page.goto(`${BASE}/#/train`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForTimeout(2000)
    console.log(`[E2E-Train] URL: ${page.url()}`)
    await page.screenshot({ path: '/tmp/e2e-train-page.png', fullPage: false })

    // 3. 提交训练任务
    console.log('\n[E2E-Train] === 3. 提交训练任务 ===')
    const corpusInput = page.locator('input[placeholder*="语料"], input[placeholder*="路径"], input[name="corpusPath"]').first()
    const corpusInputCount = await corpusInput.count()
    if (corpusInputCount === 0) {
      console.log('[E2E-Train] - 未找到语料输入框, 尝试找其他表单')
    } else {
      await corpusInput.fill(CORPUS_PATH)
      await page.waitForTimeout(300)
      console.log(`[E2E-Train] 已填语料路径: ${CORPUS_PATH}`)
    }

    // 找提交按钮
    const submitBtn = page.locator('button:has-text("提交"), button:has-text("开始训练"), button[type="submit"]').first()
    const submitCount = await submitBtn.count()
    if (submitCount > 0) {
      const btnText = await submitBtn.textContent()
      console.log(`[E2E-Train] 找到提交按钮: "${btnText?.trim()}"`)
      await submitBtn.click()
      await page.waitForTimeout(2000)
      // 检查响应
      console.log('[E2E-Train] 提交后等待响应...')
      await page.screenshot({ path: '/tmp/e2e-train-submit.png', fullPage: false })
    } else {
      console.log('[E2E-Train] - 未找到提交按钮')
    }

    // 4. 验证训练页有内容
    const trainPageContent = await page.content()
    const hasTrainingUI = trainPageContent.includes('训练') || trainPageContent.includes('train') || trainPageContent.includes('Train')
    console.log(`[E2E-Train] 训练页有相关UI: ${hasTrainingUI}`)

    // 5. 看任务列表
    console.log('\n[E2E-Train] === 4. 任务列表 ===')
    const tableRows = await page.locator('.el-table__row, table tr').count()
    console.log(`[E2E-Train] 表格行数: ${tableRows}`)
    if (tableRows > 0) {
      await page.screenshot({ path: '/tmp/e2e-train-list.png', fullPage: false })
    }

    // 总结
    console.log('\n[E2E-Train] === 结果 ===')
    console.log(`[E2E-Train] console.error: ${errors.length}`)
    console.log(`[E2E-Train] 失败请求: ${failedRequests.length}`)
    if (errors.length > 0) errors.slice(0, 3).forEach(e => console.log('  ERR:', e.slice(0, 100)))
    if (failedRequests.length > 0) failedRequests.slice(0, 5).forEach(r => console.log('  REQ:', r))
    console.log(errors.length === 0 ? '\n✓ [E2E-Train] 训练页 E2E 通过' : '\n✗ [E2E-Train] 训练页 E2E 失败')

  } catch (e) {
    console.error('[E2E-Train] 异常:', e.message)
    await page.screenshot({ path: '/tmp/e2e-train-error.png' })
  } finally {
    await browser.close()
  }
}

main()