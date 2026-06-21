// E2E: 模拟前端完整登录 + Dashboard 加载
// 跑: node e2e-login-dashboard.js
import { chromium } from 'playwright'

const BASE = process.env.BASE || 'http://127.0.0.1:5173'

async function main() {
  console.log(`[E2E] 启动浏览器, base=${BASE}`)
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  const page = await ctx.newPage()

  // 收集 console + 错误
  const errors = []
  const consoleMsgs = []
  const failedRequests = []

  page.on('console', (msg) => {
    const text = msg.text()
    consoleMsgs.push(`[${msg.type()}] ${text}`)
    if (msg.type() === 'error') errors.push(`console.error: ${text}`)
  })
  page.on('pageerror', (err) => {
    errors.push(`pageerror: ${err.message}`)
  })
  page.on('requestfailed', (req) => {
    failedRequests.push(`${req.method()} ${req.url()} - ${req.failure()?.errorText}`)
  })
  page.on('response', (resp) => {
    const url = resp.url()
    // 重要 API 响应都打
    if (url.includes('/api/')) {
      consoleMsgs.push(`[api] ${resp.status()} ${resp.request().method()} ${url.replace(BASE, '')}`)
    }
    if (resp.status() >= 400) {
      failedRequests.push(`HTTP ${resp.status()} ${url}`)
    }
  })

  try {
    // 1) 访问登录页
    console.log('\n[E2E] === 步骤 1: 打开登录页 ===')
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForLoadState('load', { timeout: 10000 })
    // 等关键元素出现
    await page.waitForSelector('input[autocomplete], input[placeholder*="账"], input[type="text"]', { timeout: 10000 })
    console.log(`[E2E] 登录页 URL: ${page.url()}`)
    const loginTitle = await page.title()
    console.log(`[E2E] 页面 title: ${loginTitle}`)

    // 2) 截图登录页
    await page.screenshot({ path: '/tmp/e2e-1-login.png', fullPage: true })
    console.log('[E2E] 截图: /tmp/e2e-1-login.png')

    // 3) 填表 + 点登录
    console.log('\n[E2E] === 步骤 2: 输入 admin/admin123 + 登录 ===')
    // Login.vue 表单 input 用 .el-input__inner
    await page.fill('input[autocomplete="username"], input[placeholder*="账"], input[type="text"]:first-of-type', 'admin')
    await page.fill('input[autocomplete="current-password"], input[type="password"]', 'admin123')
    await page.screenshot({ path: '/tmp/e2e-2-filled.png', fullPage: true })
    console.log('[E2E] 截图: /tmp/e2e-2-filled.png')

    // 勾选 "我已阅读并同意" checkbox (Element Plus 协议必须)
    await page.waitForTimeout(300)
    const agreeClicked = await page.evaluate(() => {
      // 找 .agree-row 里的 .el-checkbox 整个 label
      const labels = document.querySelectorAll('.agree-row .el-checkbox')
      if (labels.length > 0) {
        labels[0].click()
        return true
      }
      return false
    })
    console.log(`[E2E] 协议 click: ${agreeClicked ? 'OK' : '未找到'}`)
    await page.waitForTimeout(500)

    // 点登录按钮 — 绕过 DOM 重新渲染, 直接 eval DOM click
    await page.waitForTimeout(500)  // 等表单 reactive 稳定
    const clicked = await page.evaluate(() => {
      const btn = document.querySelector('button.submit-btn')
      if (btn) { btn.click(); return true }
      return false
    })
    console.log(`[E2E] 按钮 click: ${clicked ? 'OK' : '未找到'}`)
    console.log('[E2E] 已点登录')

    // 4) 等跳转
    console.log('\n[E2E] === 步骤 3: 等待跳转 ===')
    // 不等 waitForURL (可能缓慢, 或由于 mock 返值不是 100% 路由), 等 dashboard 关键元素
    try {
      await page.waitForURL((url) => !url.toString().includes('/login'), { timeout: 8000 })
    } catch (e) {
      console.log(`[E2E] 跳转超时, 当前 URL: ${page.url()}, 继续检测 dashboard 元素`)
    }
    console.log(`[E2E] 当前 URL: ${page.url()}`)
    await page.waitForLoadState('load', { timeout: 10000 })
    // 等 dashboard 关键元素 (无论 URL 是不是 /dashboard)
    await page.waitForTimeout(5000)
    // 滚动到底部, 触发懒加载
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await page.waitForTimeout(2000)
    await page.screenshot({ path: '/tmp/e2e-3-dashboard.png', fullPage: true })
    console.log('[E2E] 截图: /tmp/e2e-3-dashboard.png')

    // 5) 抓 dashboard 关键元素
    console.log('\n[E2E] === 步骤 4: 检查 Dashboard 渲染 ===')
    const stats = await page.locator('.stat-card, .el-card').count()
    console.log(`[E2E] stat-card 数量: ${stats}`)

    // 抓所有可见 text
    const bodyText = (await page.locator('body').innerText()).slice(0, 500)
    console.log(`[E2E] 页面文字前 500 字: ${bodyText.replace(/\n/g, ' | ')}`)

    // 6) 测 API 调用
    console.log('\n[E2E] === 步骤 5: 测 API 调用 ===')
    const apiResult = await page.evaluate(async () => {
      const token = localStorage.getItem('access_token')
      const res = await fetch('/api/admin/dashboard', { headers: { Authorization: 'Bearer ' + token } })
      const json = await res.json()
      return { status: res.status, code: json.code, data: json.data }
    })
    console.log(`[E2E] /api/admin/dashboard: status=${apiResult.status} code=${apiResult.code}`)
    console.log(`[E2E]   users=${apiResult.data?.users}, todayActive=${apiResult.data?.todayActiveUsers}`)

    // 7) 测 /api/monitor/snapshot
    const monitor = await page.evaluate(async () => {
      const token = localStorage.getItem('access_token')
      const res = await fetch('/api/monitor/snapshot', { headers: { Authorization: 'Bearer ' + token } })
      const json = await res.json()
      return { code: json.code, services: json.data?.services?.length, alerts: json.data?.alerts?.length }
    })
    console.log(`[E2E] /api/monitor/snapshot: code=${monitor.code} services=${monitor.services} alerts=${monitor.alerts}`)

  } catch (err) {
    console.error('[E2E] ✗ 失败:', err.message)
    await page.screenshot({ path: '/tmp/e2e-error.png', fullPage: true }).catch(() => {})
    errors.push(`exception: ${err.message}`)
  } finally {
    // 输出汇总
    console.log('\n[E2E] === 错误汇总 ===')
    if (errors.length === 0) {
      console.log('[E2E] ✓ 无 console.error / pageerror')
    } else {
      errors.forEach((e) => console.log(`[E2E]   ✗ ${e}`))
    }
    console.log('\n[E2E] === 失败请求 (>= 400) ===')
    if (failedRequests.length === 0) {
      console.log('[E2E] ✓ 无失败请求')
    } else {
      failedRequests.forEach((r) => console.log(`[E2E]   ✗ ${r}`))
    }
    console.log('\n[E2E] === 全部 console (前 30) ===')
    consoleMsgs.slice(0, 30).forEach((m) => console.log(`[E2E]   ${m}`))

    await browser.close()
  }
}

main().catch((e) => { console.error(e); process.exit(1) })
