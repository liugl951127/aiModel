// E2E: Chat 对话页测试
import { chromium } from 'playwright'
const BASE = process.env.BASE || 'http://127.0.0.1:5173'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => {
  console.log('PAGE ERR:', e.message.slice(0, 200))
  errors.push(e.message)
})
page.on('console', m => { if (m.type() === 'error') console.log('CONSOLE:', m.text().slice(0, 200)) })

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForSelector('input', { timeout: 15000 })
await page.fill('input[type="text"]:first-of-type', 'admin')
await page.fill('input[type="password"]', 'admin123')
await page.evaluate(() => document.querySelectorAll('.agree-row .el-checkbox')[0]?.click())
await page.waitForTimeout(500)
await page.evaluate(() => document.querySelector('button.submit-btn')?.click())
try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
console.log('URL after login:', page.url())

// 进入 Chat 页
await page.goto(`${BASE}/#/chat`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(4000)
const html = await page.content()
console.log('Has chat page:', html.includes('chat-page') || html.includes('对话'))
console.log('Page errors:', errors.length)
errors.slice(0, 3).forEach(e => console.log('  ERR:', e.slice(0, 100)))
await page.screenshot({ path: '/tmp/e2e-chat.png', fullPage: false })

await browser.close()
process.exit(errors.length === 0 ? 0 : 1)