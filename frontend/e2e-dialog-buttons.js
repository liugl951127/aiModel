// E2E: 测试数据节点配置弹窗的取消/保存按钮 (验证视觉可见性)
import { chromium } from 'playwright'
const BASE = process.env.BASE || 'http://127.0.0.1:5173'

// 工具: 数 visible dialog
async function visibleDialogCount(page) {
  return await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.el-overlay')).filter(o => {
      const cs = window.getComputedStyle(o)
      return cs.display !== 'none' && o.querySelector('.el-dialog')
    }).length
  })
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => { console.log('PAGE ERR:', e.message.slice(0, 250)); errors.push(e.message) })

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

// 加 3 个数据节点
console.log('\n--- 加 3 个数据节点 ---')
const dataItems = page.locator('.palette .pal-group').nth(0).locator('[draggable="true"]')
for (let i = 0; i < 3; i++) {
  await dataItems.nth(i).click()
  await page.waitForTimeout(300)
}
const nodes = await page.locator('.wf-node').count()
console.log(`画布节点数: ${nodes}`)

// 双击节点 1
console.log('\n--- 双击数据节点 ---')
await page.locator('.wf-node').first().dispatchEvent('dblclick')
await page.waitForTimeout(4000)

let dialogsVisible = await visibleDialogCount(page)
const schemaCount = await page.locator('.el-dialog .el-form-item').count()
console.log(`弹窗可见: ${dialogsVisible}, schema 字段数: ${schemaCount}`)

if (schemaCount === 0) {
  console.log('✗ schema 没加载')
  await browser.close()
  process.exit(1)
}

await page.screenshot({ path: '/tmp/e2e-dialog-open.png' })

// 测试 1: 取消按钮
console.log('\n--- 测试取消按钮 ---')
await page.locator('.el-dialog__footer button:has-text("取消")').first().click()
await page.waitForTimeout(2000)
dialogsVisible = await visibleDialogCount(page)
console.log(`取消后弹窗可见: ${dialogsVisible} (期望 0)`)
const cancelOK = dialogsVisible === 0

// 再次双击
await page.locator('.wf-node').first().dispatchEvent('dblclick')
await page.waitForTimeout(4000)
dialogsVisible = await visibleDialogCount(page)
console.log(`再次双击弹窗可见: ${dialogsVisible}`)

// 修改参数
const input = page.locator('.el-dialog input[placeholder*="/data/"]').first()
if (await input.count() > 0) {
  await input.fill('/custom/path/')
  await page.waitForTimeout(300)
  console.log('已修改参数')
}

// 测试 2: 保存按钮
console.log('\n--- 测试保存按钮 ---')
await page.locator('.el-dialog__footer button:has-text("确定保存")').first().click()
await page.waitForTimeout(2000)
dialogsVisible = await visibleDialogCount(page)
console.log(`保存后弹窗可见: ${dialogsVisible} (期望 0)`)
const saveOK = dialogsVisible === 0

await page.screenshot({ path: '/tmp/e2e-dialog-final.png' })
console.log(`\npage errors: ${errors.length}`)
if (errors.length > 0) errors.slice(0, 3).forEach(e => console.log('  ERR:', e.slice(0, 100)))

const ok = cancelOK && saveOK && errors.length === 0
console.log(ok ? '\n✅ 取消/保存按钮均工作' : '\n❌ 失败')
await browser.close()
process.exit(ok ? 0 : 1)