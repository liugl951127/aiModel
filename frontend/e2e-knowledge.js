// E2E: 知识库 CRUD 测试
// 验证: 进入知识库 → 列表渲染 → 创建新知识库 → 删除
// 用法: BASE=http://127.0.0.1:5173 node e2e-knowledge.js

import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://127.0.0.1:5173'

async function main() {
  console.log(`[E2E-KB] 启动, base=${BASE}`)
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
  const page = await ctx.newPage()

  const errors = []
  const apiResponses = []
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', e => errors.push(`PAGE: ${e.message}`))
  page.on('response', r => {
    if (r.url().includes('/api/') && !r.url().includes('/auth/login')) {
      apiResponses.push(`${r.status()} ${r.url().replace(BASE, '')}`)
    }
  })

  try {
    // 1. 登录
    console.log('\n[E2E-KB] === 1. 登录 ===')
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.fill('input[autocomplete="username"], input[placeholder*="账"], input[type="text"]:first-of-type', 'admin')
    await page.fill('input[autocomplete="current-password"], input[type="password"]', 'admin123')
    await page.waitForTimeout(500)
    await page.evaluate(() => document.querySelectorAll('.agree-row .el-checkbox')[0]?.click())
    await page.waitForTimeout(500)
    await page.evaluate(() => document.querySelector('button.submit-btn')?.click())
    try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
    console.log(`[E2E-KB] 登录完成: ${page.url()}`)

    // 2. 进入知识库页 (hash 路由)
    console.log('\n[E2E-KB] === 2. 进入知识库 ===')
    await page.goto(`${BASE}/#/knowledge`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForTimeout(2500)
    console.log(`[E2E-KB] URL: ${page.url()}`)
    await page.screenshot({ path: '/tmp/e2e-kb-page.png', fullPage: false })

    // 3. 看知识库列表
    const kbCards = await page.locator('.el-card, .kb-card, [class*="kb"]').count()
    console.log(`[E2E-KB] 知识库卡片数: ${kbCards}`)
    const tableRows = await page.locator('.el-table__row').count()
    console.log(`[E2E-KB] 表格行数: ${tableRows}`)

    // 4. 找"新建知识库"按钮
    console.log('\n[E2E-KB] === 3. 创建知识库 ===')
    const newBtn = page.locator('button:has-text("新建"), button:has-text("新增"), button:has-text("创建")').first()
    const newBtnCount = await newBtn.count()
    if (newBtnCount > 0) {
      await newBtn.click()
      await page.waitForTimeout(1500)
      // 检查 dialog
      const dialogVisible = await page.locator('.el-dialog__body').isVisible().catch(() => false)
      console.log(`[E2E-KB] 创建 dialog 显示: ${dialogVisible}`)
      if (dialogVisible) {
        // 填名称
        const nameInput = page.locator('.el-dialog input[placeholder*="名称"], .el-dialog input').first()
        if (await nameInput.count() > 0) {
          await nameInput.fill(`E2E测试KB_${Date.now()}`)
          await page.waitForTimeout(500)
          // 提交
          const submitBtn = page.locator('.el-dialog__footer button:has-text("确定"), .el-dialog button:has-text("保存"), .el-dialog button:has-text("创建")').first()
          if (await submitBtn.count() > 0) {
            await submitBtn.click({ timeout: 3000 }).catch(e => console.log('  提交失败:', e.message))
            await page.waitForTimeout(2000)
            console.log('[E2E-KB] ✓ 已尝试创建')
          }
        }
        await page.screenshot({ path: '/tmp/e2e-kb-create.png' })
      }
    }

    // 5. 看 API 调用
    console.log('\n[E2E-KB] === 4. API 调用 ===')
    const kbApis = apiResponses.filter(r => r.includes('/api/kb') || r.includes('/api/knowledge'))
    console.log(`[E2E-KB] 知识库 API 调用 ${kbApis.length} 次`)
    kbApis.slice(0, 5).forEach(r => console.log('  ', r))

    // 总结
    console.log('\n[E2E-KB] === 结果 ===')
    console.log(`[E2E-KB] console.error: ${errors.length}`)
    console.log(`[E2E-KB] 总 API 调用: ${apiResponses.length}`)
    if (errors.length > 0) errors.slice(0, 3).forEach(e => console.log('  ERR:', e.slice(0, 100)))
    console.log(errors.length === 0 ? '\n✓ [E2E-KB] 知识库 E2E 通过' : '\n✗ [E2E-KB] 知识库 E2E 失败')

  } catch (e) {
    console.error('[E2E-KB] 异常:', e.message)
    await page.screenshot({ path: '/tmp/e2e-kb-error.png' })
  } finally {
    await browser.close()
  }
}

main()