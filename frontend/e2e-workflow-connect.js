// E2E: 工作流连线完整稳定性测试 (3 节点 2 连线 + 多 outPorts)
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
console.log('1) login done')

await page.goto(`${BASE}/#/workflow`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3000)

// 加 5 个节点
for (let i = 0; i < 5; i++) {
  await page.locator('.palette [draggable="true"]').first().click()
  await page.waitForTimeout(300)
}
await page.waitForTimeout(1000)
const nodes = await page.locator('.wf-node').count()
const ports = await page.locator('.wn-port').count()
console.log(`2) nodes=${nodes}, ports=${ports}`)
await page.screenshot({ path: '/tmp/e2e-wf-5nodes.png' })

// 连线 1: node0.out → node1.in
console.log('\n3) 连线 1: node0.out → node1.in')
await page.locator('.wf-node').nth(0).locator('.wn-port-out').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(400)
await page.locator('.wf-node').nth(1).locator('.wn-port-in').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(800)

// 连线 2: node1.out → node2.in
console.log('4) 连线 2: node1.out → node2.in')
await page.locator('.wf-node').nth(1).locator('.wn-port-out').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(400)
await page.locator('.wf-node').nth(2).locator('.wn-port-in').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(800)

// 连线 3: node2.out → node3.in (跨多个)
console.log('5) 连线 3: node2.out → node3.in')
await page.locator('.wf-node').nth(2).locator('.wn-port-out').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(400)
await page.locator('.wf-node').nth(3).locator('.wn-port-in').first().dispatchEvent('mousedown', { button: 0 })
await page.waitForTimeout(800)

const edgesFinal = await page.locator('.wires path.edge-path').count()
console.log(`\n6) 最终连线数: ${edgesFinal}`)

// 验证每条连线的渲染路径 (d 属性非空)
const edgePaths = await page.locator('.wires path.edge-path').evaluateAll(els => els.map(e => e.getAttribute('d')))
const validPaths = edgePaths.filter(d => d && d.length > 5).length
console.log(`   有效 path 数: ${validPaths}`)
edgePaths.forEach((d, i) => console.log(`   edge[${i}]: ${d?.slice(0, 80)}`))

await page.screenshot({ path: '/tmp/e2e-wf-3edges.png', fullPage: false })

// 验证
if (edgesFinal === 3 && validPaths === 3 && errors.length === 0) {
  console.log('\n✅ 全部通过: 3 节点 3 连线渲染正确, 0 page errors')
} else {
  console.log(`\n❌ 失败: edges=${edgesFinal}, valid=${validPaths}, errors=${errors.length}`)
}
await browser.close()
process.exit(edgesFinal === 3 && validPaths === 3 && errors.length === 0 ? 0 : 1)