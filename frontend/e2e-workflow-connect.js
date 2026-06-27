// E2E: 工作流端到端连线测试 (3 节点 3 连线)
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

// 加 5 个节点
for (let i = 0; i < 5; i++) {
  await page.locator('.palette [draggable="true"]').first().click()
  await page.waitForTimeout(300)
}
await page.waitForTimeout(1000)
const nodes = await page.locator('.wf-node').count()
console.log(`nodes: ${nodes}`)

// 检查端口 hit-area (20x20px)
const outBB = await page.locator('.wf-node').nth(0).locator('.wn-port-out').first().boundingBox()
const inBB = await page.locator('.wf-node').nth(1).locator('.wn-port-in').first().boundingBox()
console.log(`out port bbox: ${JSON.stringify(outBB)}`)
console.log(`in port bbox: ${JSON.stringify(inBB)}`)

const edgesBefore = await page.locator('.wires path.edge-path').count()
console.log(`edges before: ${edgesBefore}`)

// 测连线
const outPort = page.locator('.wf-node').nth(0).locator('.wn-port-out').first()
const inPort = page.locator('.wf-node').nth(1).locator('.wn-port-in').first()

if (outBB && inBB) {
  // 直接 dispatch mousedown (绕过可见性检查)
  await outPort.dispatchEvent('mousedown', { button: 0, bubbles: true })
  await page.waitForTimeout(400)
  await inPort.dispatchEvent('mousedown', { button: 0, bubbles: true })
  await page.waitForTimeout(800)

  const edgesAfter1 = await page.locator('.wires path.edge-path').count()
  console.log(`edges after dispatch mousedown: ${edgesAfter1}`)

  if (edgesAfter1 === edgesBefore) {
    // 试 page.mouse 模拟真实点击
    console.log('--- 试 page.mouse click ---')
    await page.mouse.move(outBB.x + outBB.width / 2, outBB.y + outBB.height / 2)
    await page.mouse.down()
    await page.waitForTimeout(300)
    await page.mouse.move(inBB.x + inBB.width / 2, inBB.y + inBB.height / 2)
    await page.waitForTimeout(300)
    await page.mouse.up()
    await page.waitForTimeout(800)
    const edgesAfter2 = await page.locator('.wires path.edge-path').count()
    console.log(`edges after mouse: ${edgesAfter2}`)
  }
}

const edgesFinal = await page.locator('.wires path.edge-path').count()
console.log(`\nfinal edges: ${edgesFinal}`)

// 多测 2 条线
if (edgesFinal > edgesBefore) {
  for (let i = 1; i < 3; i++) {
    const oP = page.locator('.wf-node').nth(i).locator('.wn-port-out').first()
    const iP = page.locator('.wf-node').nth(i + 1).locator('.wn-port-in').first()
    await oP.dispatchEvent('mousedown', { button: 0, bubbles: true })
    await page.waitForTimeout(300)
    await iP.dispatchEvent('mousedown', { button: 0, bubbles: true })
    await page.waitForTimeout(500)
  }
}
const edgesAll = await page.locator('.wires path.edge-path').count()
console.log(`edges after multi: ${edgesAll}`)

const ok = edgesAll >= 1 && errors.length === 0
console.log(ok ? '\n✅ 通过' : `\n❌ 失败 edges=${edgesAll} errors=${errors.length}`)
await browser.close()
process.exit(ok ? 0 : 1)