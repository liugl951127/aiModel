// E2E: 工作流连线测试 - 验证未放大画布端到端能连接
import { chromium } from 'playwright'
const BASE = process.env.BASE || 'http://127.0.0.1:5173'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => { console.log('PAGE ERR:', e.message.slice(0, 150)); errors.push(e.message) })

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForSelector('input', { timeout: 15000 })
await page.fill('input[type="text"]:first-of-type', 'admin')
await page.fill('input[type="password"]', 'admin123')
await page.evaluate(() => document.querySelectorAll('.agree-row .el-checkbox')[0]?.click())
await page.waitForTimeout(500)
await page.evaluate(() => document.querySelector('button.submit-btn')?.click())
try { await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 10000 }) } catch {}
console.log('login done')

// 进入工作流
await page.goto(`${BASE}/#/workflow`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3000)
console.log('workflow loaded')

// 加载 RAG 模板 (有 5+ 节点)
const loadTplBtn = page.locator('button:has-text("加载 RAG 模板"), button:has-text("RAG 模板")').first()
if (await loadTplBtn.count() > 0) {
  await loadTplBtn.click()
  await page.waitForTimeout(2000)
  console.log('RAG template loaded')
}

// 看节点 + 端口
const nodes = await page.locator('.wf-node').count()
const ports = await page.locator('.wn-port').count()
console.log(`nodes=${nodes} ports=${ports}`)
await page.screenshot({ path: '/tmp/e2e-wf-before-connect.png' })

// 测连线: 节点 1 out 端口 → 节点 2 in 端口
const outPort1 = page.locator('.wf-node').nth(0).locator('.wn-port-out').first()
const inPort2 = page.locator('.wf-node').nth(1).locator('.wn-port-in').first()
const edgesBefore = await page.locator('.wires path.edge-path').count()
console.log(`edges before: ${edgesBefore}`)

if (await outPort1.count() > 0 && await inPort2.count() > 0) {
  await outPort1.click()
  await page.waitForTimeout(500)
  await inPort2.click()
  await page.waitForTimeout(1000)
  const edgesAfter = await page.locator('.wires path.edge-path').count()
  console.log(`edges after click: ${edgesAfter}`)
  if (edgesAfter > edgesBefore) {
    console.log('✓ 端到端连接成功')
  } else {
    console.log('✗ 连接失败')
  }
}

await page.screenshot({ path: '/tmp/e2e-wf-after-connect.png' })
await browser.close()
console.log('errors:', errors.length)
process.exit(errors.length === 0 ? 0 : 1)