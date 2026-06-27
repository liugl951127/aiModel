// E2E: RAG 模板加载测试
import { chromium } from 'playwright'
const BASE = process.env.BASE || 'http://127.0.0.1:5173'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => { console.log('PAGE ERR:', e.message.slice(0, 200)); errors.push(e.message) })

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForSelector('input', { timeout: 15000 })
await page.fill('input[type="text"]:first-of-type', 'admin')
await page.fill('input[type="password"]', 'admin123')
await page.evaluate(() => document.querySelectorAll('.agree-row .el-checkbox')[0]?.click())
await page.waitForTimeout(500)
await page.evaluate(() => document.querySelector('button.submit-btn')?.click())
try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
console.log('login done')

await page.goto(`${BASE}/#/workflow`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3000)

// 点击 "加载 RAG 模板" 按钮
const tplBtn = page.locator('button:has-text("加载 RAG"), button:has-text("RAG 模板")').first()
const btnCount = await tplBtn.count()
console.log(`RAG template button count: ${btnCount}`)
await tplBtn.click()
await page.waitForTimeout(3000)

const nodes = await page.locator('.wf-node').count()
const edges = await page.locator('.wires path.edge-path').count()
console.log(`nodes: ${nodes}, edges: ${edges}`)

// 看节点名
const nodeNames = await page.evaluate(() => Array.from(document.querySelectorAll('.wf-node')).map(n => n.textContent?.trim().split(/\s+/).slice(0, 2).join(' ')))
console.log('node names:', nodeNames)

const ok = nodes >= 5 && edges >= 4 && errors.length === 0
console.log(ok ? '\n✅ RAG 模板加载成功' : `\n❌ 失败 nodes=${nodes} edges=${edges} errors=${errors.length}`)
await browser.close()
process.exit(ok ? 0 : 1)