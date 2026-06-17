#!/usr/bin/env node
// 前端一键静态检查
// 1) SFC 编译 (用了 @vue/compiler-sfc)
// 2) el-link API 兼容性 + markRaw + 缺失 import
// 3) 图标未 import 检查
//
// 用法: node check-all.cjs
// 退出码: 0 全部通过, 1 有问题
const { execSync } = require('child_process')

const checks = [
  { name: 'SFC 编译 (34 .vue 文件)', cmd: 'node check-vue.cjs' },
  { name: 'el-link / markRaw / 缺 import', cmd: 'node check-warn.cjs' },
  { name: '图标 import', cmd: 'node check-icons.cjs' }
]

let allPass = true
for (const c of checks) {
  console.log(`\n===== ${c.name} =====`)
  try {
    execSync(c.cmd, { stdio: 'inherit' })
  } catch (e) {
    allPass = false
  }
}

console.log('\n===== 总结 =====')
if (allPass) {
  console.log('✅ 全部通过, 没有发现运行时警告风险')
  process.exit(0)
} else {
  console.log('❌ 有问题, 见上面输出')
  process.exit(1)
}
