// E2E 总入口: 依次跑所有 E2E 脚本, 输出汇总
// 用法: BASE=http://127.0.0.1:5173 node e2e-all.js

import { spawn } from 'node:child_process'

const BASE = process.env.BASE || 'http://127.0.0.1:5173'

const scripts = [
  { name: 'Login+Dashboard', file: 'e2e-login-dashboard.js' },
  { name: '工作流编排', file: 'e2e-workflow.js' },
  { name: '知识库', file: 'e2e-knowledge.js' },
  { name: '训练任务', file: 'e2e-train.js' }
]

async function runScript(script) {
  return new Promise((resolve) => {
    console.log(`\n${'='.repeat(60)}\n▶ ${script.name}\n${'='.repeat(60)}`)
    const start = Date.now()
    const proc = spawn('node', [script.file], {
      env: { ...process.env, BASE },
      stdio: 'pipe'
    })
    let output = ''
    proc.stdout.on('data', d => { output += d.toString(); process.stdout.write(d) })
    proc.stderr.on('data', d => { output += d.toString(); process.stderr.write(d) })
    proc.on('close', (code) => {
      const duration = ((Date.now() - start) / 1000).toFixed(1)
      console.log(`\n[${script.name}] 退出码 ${code}, 耗时 ${duration}s`)
      resolve({ name: script.name, code, duration })
    })
  })
}

async function main() {
  console.log(`[E2E-ALL] 总入口, base=${BASE}`)
  console.log(`[E2E-ALL] 准备: 前端 dev server 已启 + 后端 mock 或真实服务可用`)
  console.log(`[E2E-ALL] 跑 ${scripts.length} 个 E2E 脚本`)

  const results = []
  for (const s of scripts) {
    const r = await runScript(s)
    results.push(r)
  }

  console.log('\n' + '='.repeat(60))
  console.log('【E2E 总览】')
  console.log('='.repeat(60))
  let passed = 0
  results.forEach(r => {
    const status = r.code === 0 ? '✓ PASS' : '✗ FAIL'
    console.log(`  ${status}  ${r.name.padEnd(20)} ${r.duration}s`)
    if (r.code === 0) passed++
  })
  console.log(`\n  共 ${results.length} 个, 通过 ${passed}`)
  process.exit(passed === results.length ? 0 : 1)
}

main().catch(e => { console.error(e); process.exit(1) })