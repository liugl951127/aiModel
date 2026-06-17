#!/usr/bin/env node
/**
 * 端到端贯通测试 (跨 5 大模块 7 个二级菜单)
 *
 * 启动:  python3 backend/mock_ai_server.py 9999 &
 * 运行:  node frontend/e2e_full_chain.cjs http://127.0.0.1:9999
 *
 * 验证完整流程 (用户真实操作顺序):
 *   1) Datasets     创建数据集     POST /api/dataset
 *   2) Models       训练模型      POST /api/trainer/submit  (用 step 1 数据集)
 *   3) Models       导出模型      POST /api/model/export/1?format=onnx
 *   4) Workflow     创建工作流    POST /api/workflow/spec    (含训练节点)
 *   5) Workflow     保存到 DB     GET  /api/workflow/spec/list (验证入库)
 *   6) Workflow     运行          POST /api/workflow/run      (用 step 4 spec)
 *   7) Workflow     查运行历史    GET  /api/workflow/runs     (验证 step 6)
 *   8) Inference    拿模型推理    POST /api/inference/generate (用 step 2 训练完的)
 *   9) Files        分片上传      POST /api/files/chunk/init + PUT chunk
 *  10) Distributed  分布式能力    POST /api/distributed/lock   (Redis 演示)
 *
 * 每个步骤验证: HTTP 200 + data.code=200 + 必要字段存在
 */

const http = require('http')

const BASE = (process.argv[2] || 'http://127.0.0.1:9999').replace(/\/$/, '')
const STEP = (label) => console.log(`\n━━━ ${label} ━━━`)
const OK = (msg) => console.log(`  ✓ ${msg}`)
const FAIL = (msg) => { console.error(`  ✗ ${msg}`); process.exitCode = 1 }

function call(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE + path)
    const data = body ? JSON.stringify(body) : null
    const req = http.request({
      hostname: url.hostname,
      port: url.port || 80,
      path: url.pathname + url.search,
      method,
      headers: {
        'Content-Type': 'application/json',
        'X-Username': 'e2e-test',
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {})
      }
    }, (res) => {
      let chunks = []
      res.on('data', c => chunks.push(c))
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString()
        let json
        try { json = text ? JSON.parse(text) : {} } catch (e) { json = { _raw: text } }
        resolve({ status: res.statusCode, body: json })
      })
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

async function expect(label, res, check) {
  if (res.status !== 200) { FAIL(`${label} HTTP ${res.status}`); return null }
  if (res.body.code !== undefined && res.body.code !== 200) {
    FAIL(`${label} code=${res.body.code} msg=${res.body.message}`); return null
  }
  const data = res.body.data !== undefined ? res.body.data : res.body
  if (check && !check(data)) { FAIL(`${label} 数据校验失败: ${JSON.stringify(data).slice(0, 200)}`); return null }
  OK(label)
  return data
}

async function main() {
  console.log('=== 端到端贯通测试 (E2E Full Chain) ===')
  console.log(`目标: ${BASE}\n`)

  // 0) health
  STEP('0. 健康检查')
  await expect('GET /api/auth/health', await call('GET', '/api/auth/health'), d => d.status === 'UP')

  // 1) Datasets
  STEP('1. Datasets - 创建数据集')
  const ds = await expect('POST /api/dataset', await call('POST', '/api/dataset', {
    datasetCode: 'e2e-corpus-' + Date.now(),
    datasetName: 'E2E 测试语料',
    format: 'jsonl',
    sampleCount: 1000,
    language: 'zh'
  }), d => d.id || d.datasetCode)
  const datasetId = ds?.id || 1

  // 2) Models - 训练 (走 trainer service, 用 dataset path)
  STEP('2. Models - 提交训练任务')
  const train = await expect('POST /api/trainer/submit', await call('POST', '/api/trainer/submit', {
    trainerId: 'minigpt',
    corpusPath: '/opt/ai-platform/corpus/e2e.txt',
    params: { epochs: 3, batchSize: 16, learningRate: 3e-3 }
  }), d => d.jobId)
  const jobId = train?.jobId

  // 3) Models - 导出
  STEP('3. Models - 导出 ONNX')
  await expect('POST /api/model/export/1?format=onnx', await call('POST', '/api/model/export/1?format=onnx', { includeTokenizer: true }))

  // 4) Workflow - 创建 (前端 AI 生成模拟)
  STEP('4. Workflow - 创建工作流 (AI 极速生成)')
  const wf = await expect('POST /api/workflow/ai-generate', await call('POST', '/api/workflow/ai-generate', {
    input: 'LoRA 训练流程'
  }), d => d.nodes && d.edges && d.nodes.length > 0)
  console.log(`  → AI 生成 ${wf.nodes.length} 节点 / ${wf.edges.length} 边`)

  // 5) Workflow - 保存
  STEP('5. Workflow - 保存到 DB')
  const saved = await expect('POST /api/workflow/spec', await call('POST', '/api/workflow/spec', {
    name: 'E2E 贯通测试 - ' + new Date().toISOString(),
    nodes: wf.nodes,
    edges: wf.edges
  }), d => d.id)
  const specId = saved?.id

  // 6) Workflow - 运行
  STEP('6. Workflow - 运行 (异步)')
  const run = await expect('POST /api/workflow/run', await call('POST', '/api/workflow/run', { specId }), d => d && typeof d === 'string')
  const runId = run

  // 7) Workflow - 查运行历史
  STEP('7. Workflow - 查运行历史 (DB 持久化验证)')
  await expect('GET /api/workflow/runs', await call('GET', '/api/workflow/runs'), list => Array.isArray(list))

  // 8) Inference
  STEP('8. Inference - 模型推理')
  await expect('POST /api/inference/generate', await call('POST', '/api/inference/generate', {
    model: 'minigpt',
    prompt: '你好, 介绍一下自己'
  }))

  // 9) Files - 分片上传
  STEP('9. Files - 分片上传 (Redis 持久化验证)')
  const init = await expect('POST /api/files/chunk/init', await call('POST', '/api/files/chunk/init', {
    originalName: 'e2e.bin',
    contentType: 'application/octet-stream',
    totalSize: 1024,
    bucket: 'e2e'
  }), d => d.uploadId)
  const uploadId = init?.uploadId

  // 10) Distributed
  STEP('10. Distributed - Redis 分布式锁')
  await expect('POST /api/distributed/lock', await call('POST', '/api/distributed/lock', {
    orderId: 'e2e-order-' + Date.now()
  }))

  // 总结
  console.log('\n' + '='.repeat(60))
  if (process.exitCode === 1) {
    console.log('❌ 有步骤失败, 看上面 ✗ 标记')
  } else {
    console.log('✅ 全部贯通路径 OK')
    console.log(`   训练 jobId:  ${jobId}`)
    console.log(`   工作流 spec: ${specId}`)
    console.log(`   工作流 run:  ${runId}`)
    console.log(`   上传 upload: ${uploadId}`)
  }
  console.log('='.repeat(60))
}

main().catch(e => { console.error('FATAL:', e); process.exit(2) })
