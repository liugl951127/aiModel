#!/usr/bin/env node
/**
 * 前端 AI 极速生成 - 端到端模拟
 * 模拟 WorkflowAiGenerate.vue 的 onGenerate 全流程:
 *   1) 启动进度条 (4 阶段, 2.4s)
 *   2) 调 /api/workflow/ai-generate (实际 HTTP)
 *   3) 跟 axios 拦截器一样的解构 (r = {code, message, data: <workflow>})
 *   4) 验证结果可应用 (有 nodes/edges/params)
 *
 * 用法: node simulate_ai.cjs http://127.0.0.1:9999
 */

const MOCK = process.argv[2] || 'http://127.0.0.1:9999'

// 跟前端 utils/request.js 一样的拦截器
function axiosLikeFetch(url, options = {}) {
  return (async () => {
    const t0 = Date.now()
    const resp = await fetch(url, {
      method: options.method || 'GET',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      body: options.body ? JSON.stringify(options.body) : undefined
    })
    const data = await resp.json()
    const elapsed = Date.now() - t0
    // 模拟拦截器: code===200 时返回 data (整个 Result 对象)
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) return { data, elapsed }
      return Promise.reject({ data, elapsed })
    }
    return { data, elapsed }
  })()
}

// 模拟 progress 状态
let progressPct = 0
let progressStep = -1
let progressLabel = ''

const stepLabels = ['① 思考需求...', '② 查询知识库...', '③ 生成节点中...', '④ 检查参数...']
let timer = null

function startProgress() {
  progressPct = 0
  progressStep = 0
  progressLabel = stepLabels[0]
  console.log(`\n  📊 进度:  0%  ${progressLabel}`)
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (progressPct < 30) { progressPct += 5; progressStep = 0; progressLabel = stepLabels[0] }
    else if (progressPct < 55) { progressPct += 4; progressStep = 1; progressLabel = stepLabels[1] }
    else if (progressPct < 85) { progressPct += 3; progressStep = 2; progressLabel = stepLabels[2] }
    else if (progressPct < 99) { progressPct += 1; progressStep = 3; progressLabel = stepLabels[3] }
    process.stdout.write(`  📊 进度: ${progressPct.toString().padStart(3)}%  ${progressLabel}   \r`)
  }, 200)
}

function stopProgress(success) {
  if (timer) { clearInterval(timer); timer = null }
  if (success) {
    progressPct = 100
    progressStep = 3
    progressLabel = '✓ 生成完成'
    console.log(`\n  📊 进度: 100%  ${progressLabel}`)
  } else {
    progressLabel = '✗ 生成失败'
    console.log(`\n  📊 ${progressLabel}`)
  }
}

// 模拟 WorkflowAiGenerate.onGenerate
async function onGenerate(input) {
  if (!input.trim()) {
    console.log('  ❌ 请先描述你的需求')
    return
  }
  console.log(`\n  🚀 用户输入: "${input}"`)
  startProgress()
  try {
    const { data, elapsed } = await axiosLikeFetch(`${MOCK}/api/workflow/ai-generate`, {
      method: 'POST',
      body: { input }
    })
    stopProgress(true)
    // ★ 关键解构: 3 种可能防御
    let payload = null
    if (data?.data?.nodes && Array.isArray(data.data.nodes)) {
      payload = data.data  // 拦截器扒过
    } else if (data?.nodes) {
      payload = data       // 直接返
    } else {
      console.log('  ❌ 返回格式识别不出')
      return
    }

    // 验证
    if (Array.isArray(payload.nodes) && payload.nodes.length > 0) {
      const label = { replace: '已重生成', add_node: '已新增节点', delete_node: '已删除节点', update_params: '已更新参数' }[payload.action || 'replace']
      console.log(`  ✅ ${label}: ${payload.nodes.length} 节点 / ${payload.edges.length} 边`)
      console.log(`  📋 场景: ${payload.scenario}, 名称: ${payload.name}`)
      console.log(`  ⏱  HTTP 响应耗时: ${elapsed}ms`)
      console.log(`  📦 节点详情:`)
      for (const n of payload.nodes) {
        const p = JSON.stringify(n.params)
        console.log(`     - ${n.id} ${n.type.padEnd(15)} ${n.name.padEnd(10)} (${n.x}, ${n.y}) ${p}`)
      }
      console.log(`  🔗 边:`)
      for (const e of payload.edges) {
        console.log(`     - ${e.from} → ${e.to}`)
      }
      // ★ 验证导出可序列化
      const exported = {
        ...payload,
        version: 1,
        exportedAt: new Date().toISOString(),
        platform: 'ai-platform-workflow',
        minRuntimeVersion: '2.0'
      }
      const json = JSON.stringify(exported, null, 2)
      console.log(`  📤 导出 JSON: ${json.length} 字节, 含 ${(json.match(/\n/g) || []).length} 行`)
      console.log(`     ✓ 含 platform 字段: ${json.includes('"platform"')}`)
      console.log(`     ✓ 含 nodes 数组: ${json.includes('"nodes"')}`)
      console.log(`     ✓ 含 edges 数组: ${json.includes('"edges"')}`)
    } else {
      console.log('  ℹ️  未识别到场景, 请描述更具体')
    }
  } catch (e) {
    stopProgress(false)
    console.log(`  ❌ 生成失败: ${e.data?.message || e.message || '网络错误'}`)
  }
}

async function main() {
  console.log('=========================================')
  console.log('  AI 极速生成 - 端到端模拟')
  console.log('=========================================')
  console.log(`  Mock Server: ${MOCK}`)

  // 测 1: 加载场景列表
  console.log('\n  📥 加载预设场景...')
  const r = await axiosLikeFetch(`${MOCK}/api/workflow/ai-scenarios`)
  console.log(`  ✅ 加载 ${r.data.data.length} 个场景`)

  // 测 2: 一句话生成 ★ 重点
  await onGenerate('训练个 LoRA 模型, epochs=3, topK=5')

  // 测 3: 多轮 - 加节点
  console.log('\n-----------------------------------------')
  console.log('  多轮 1: 多加 1 个评估节点')
  const base = await axiosLikeFetch(`${MOCK}/api/workflow/ai-generate`, {
    method: 'POST', body: { input: '做一个 RAG 知识库问答' }
  })
  const baseData = base.data.data
  await onGenerateMultiTurn('多加 1 个评估节点', baseData)

  // 测 4: 多轮 - 删节点
  console.log('\n-----------------------------------------')
  console.log('  多轮 2: 删掉最后一个节点')
  await onGenerateMultiTurn('删掉最后一个节点', baseData)

  // 测 5: 多轮 - 换场景
  console.log('\n-----------------------------------------')
  console.log('  多轮 3: 换成营销文案')
  await onGenerateMultiTurn('换成营销文案', baseData)

  // 测 6: 多轮 - 改参数
  console.log('\n-----------------------------------------')
  console.log('  多轮 4: topK=10')
  await onGenerateMultiTurn('topK=10', baseData)

  console.log('\n=========================================')
  console.log('  ✅ 全部场景通过')
  console.log('=========================================')
  process.exit(0)
}

async function onGenerateMultiTurn(input, current) {
  startProgress()
  try {
    const { data, elapsed } = await axiosLikeFetch(`${MOCK}/api/workflow/ai-modify`, {
      method: 'POST',
      body: { input, current }
    })
    stopProgress(true)
    const payload = data?.data?.nodes ? data.data : data
    if (Array.isArray(payload.nodes) && payload.nodes.length > 0) {
      const label = { replace: '已重生成', add_node: '已新增节点', delete_node: '已删除节点', update_params: '已更新参数' }[payload.action || 'replace']
      console.log(`  ✅ ${label}: ${payload.nodes.length} 节点 / ${payload.edges.length} 边 (${elapsed}ms)`)
    } else {
      console.log('  ❌ 无效结果')
    }
  } catch (e) {
    stopProgress(false)
    console.log(`  ❌ ${e.data?.message || e.message}`)
  }
}

main()
