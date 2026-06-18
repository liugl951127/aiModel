import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { workflowApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

/**
 * 工作流执行 composable — 把 Workflow.vue 里 110+ 行 run() 函数拆成可复用单文件.
 *
 * <p>用法:</p>
 * <pre>
 * const { running, run, lastRun, progress, ok } = useWorkflowRunner({
 *   getNodes: () => nodes.value,
 *   getValidation: () => validation.value,
 *   getSpecName: () => specName.value,
 *   onNodeStart, onNodeDone, onNodeFailed, onDone
 * })
 * </pre>
 *
 * <p>职责: 拓扑执行 / 进度跟踪 / 失败上报 / 计时统计</p>
 */
export function useWorkflowRunner(opts) {
  const bus = useGlobalBus()

  const running = ref(false)
  const currentNodeId = ref(null)
  const doneIds = ref(new Set())
  const failedIds = ref(new Set())
  const lastRun = ref(null)   // { status, durationMs, ts, failedNodeId, failedReason }
  const log = ref([])         // [{ tag, msg, level, ts }]

  const progress = computed(() => {
    const total = (opts.getNodes() || []).length
    if (!total) return 0
    return Math.floor((doneIds.value.size / total) * 100)
  })

  function addLog(tag, msg, level = 'info') {
    log.value.push({ tag, msg, level, ts: Date.now() })
  }

  /** AI 类节点 (需要调模型) */
  const AI_NODE_TYPES = new Set(['agent_think', 'agent_chat', 'infer_chat', 'infer', 'infer_generate'])

  /** 单个节点执行 (供 run() 循环用) */
  async function execOne(node) {
    currentNodeId.value = node.id
    addLog('节点', `→ ${node.name} (${node.id})`, 'info')
    bus.emit('live:event', { type: 'wf', text: `▶ ${node.name} (${node.type}) 执行中...`, actor: 'workflow' })
    if (AI_NODE_TYPES.has(node.type)) {
      bus.emit('live:event', { type: 'agent', text: `🧠 AI 思考中: 调模型生成...`, actor: 'ai' })
    }
    opts.onNodeStart?.(node)

    try {
      const r = await workflowApi.exec({
        workflowId: opts.getSpecName() || 'wf',
        nodeId: node.type || node.id,
        input: { ...(node.params || {}), _upstream: opts._upstream || {} }
      })
      const data = r.data || {}
      if (data.error) {
        return { ok: false, error: data.error }
      }
      doneIds.value.add(node.id)
      addLog('节点', `✓ ${node.name} 完成`, 'success')
      bus.emit('live:event', { type: 'wf', text: `✓ ${node.name} 完成`, actor: 'workflow' })
      // AI 节点把文本预览推活动流
      if (AI_NODE_TYPES.has(node.type) && data.result?.text) {
        const preview = String(data.result.text).slice(0, 50)
        bus.emit('live:event', { type: 'agent', text: `✓ AI 生成: ${preview}${preview.length >= 50 ? '...' : ''}`, actor: 'ai' })
      }
      opts.onNodeDone?.(node, data.result)
      return { ok: true, result: data.result }
    } catch (e) {
      const msg = e?.response?.data?.message || e?.message || '未知错误'
      return { ok: false, error: msg, throwable: e }
    }
  }

  /** 顺序执行全部节点 (拓扑序) */
  async function run() {
    if (running.value) return
    const v = opts.getValidation()
    const nodes = opts.getNodes()
    if (!nodes.length) return
    if (!v?.valid) {
      ElMessage.error(`流程不合法: ${v.reason}`)
      addLog('运行', `✗ ${v.reason}`, 'error')
      return
    }

    running.value = true
    doneIds.value = new Set()
    failedIds.value = new Set()
    log.value = []
    const t0 = Date.now()
    let upstream = {}

    addLog('运行', `共 ${v.order.length} 节点, 开始顺序执行...`, 'info')
    bus.emit('live:event', { type: 'wf', text: `▶ 工作流 [${opts.getSpecName() || '未命名'}] 开始执行, 共 ${v.order.length} 节点`, actor: 'workflow' })

    let failedNode = null
    let failedReason = ''

    for (const id of v.order) {
      const n = nodes.find(x => x.id === id)
      if (!n) continue
      opts._upstream = upstream
      const result = await execOne(n)
      if (result.ok) {
        upstream = result.result || upstream
      } else {
        failedNode = n
        failedReason = result.error || '未知错误'
        failedIds.value.add(id)
        // 在节点对象上挂原因, 配置弹窗顶部 Banner 用
        n._failedReason = failedReason
        // 通知上层: 高亮 + 选中 + 滚到 + 弹配置
        opts.onNodeFailed?.(n, failedReason)
        // AI 助手诊断
        bus.emit('assistant:diagnose', { node: n.name, nodeId: n.id, error: failedReason, params: n.params })
        // 终止后续
        addLog('运行', `⛔ 后续节点不再执行, 请修改 ${n.name} 参数后重试`, 'error')
        break
      }
    }
    currentNodeId.value = null

    const dur = Date.now() - t0
    const ok = doneIds.value.size === v.order.length
    lastRun.value = {
      status: ok ? 'SUCCESS' : (failedNode ? 'FAILED' : 'PARTIAL'),
      durationMs: dur,
      ts: Date.now(),
      failedNodeId: failedNode?.id,
      failedReason
    }
    addLog('运行',
      ok ? `✓ 全部 ${v.order.length} 节点完成` : `⚠ 仅 ${doneIds.value.size}/${v.order.length} 成功${failedNode ? `, 失败: ${failedNode.name}` : ''}`,
      ok ? 'success' : 'error')
    bus.emit('live:event', {
      type: 'wf',
      text: ok ? `✓ 工作流完成 (${dur}ms)` : `⚠ ${failedNode ? `${failedNode.name} 失败` : '部分失败'} (${dur}ms)`,
      actor: 'workflow'
    })
    ElMessage[ok ? 'success' : 'error'](ok ? `运行完成 (${dur}ms)` : `${failedNode?.name || ''} 失败, 后续已停止`)

    opts.onDone?.(lastRun.value)
    running.value = false
  }

  /** 重试上次失败的节点 (用户改完参数后) */
  async function retry() {
    if (!lastRun.value?.failedNodeId) {
      // 没失败过, 直接跑
      return run()
    }
    const nodes = opts.getNodes()
    const n = nodes.find(x => x.id === lastRun.value.failedNodeId)
    if (!n) return run()
    addLog('运行', `↻ 重试 ${n.name} (${n.id})`, 'info')
    failedIds.value.delete(n.id)
    delete n._failedReason
    running.value = true
    opts._upstream = {}
    const r = await execOne(n)
    running.value = false
    if (!r.ok) {
      failedIds.value.add(n.id)
      n._failedReason = r.error
      opts.onNodeFailed?.(n, r.error)
      ElMessage.error(`重试仍失败: ${r.error}`)
    } else {
      // 成功后继续后面的节点
      // (简单实现: 重新跑整条流程; 进阶可从 failedIndex 续)
      ElMessage.success(`${n.name} 重试成功, 继续跑后续`)
      run()
    }
  }

  return {
    // state
    running, currentNodeId, doneIds, failedIds, lastRun, log, progress,
    // actions
    run, retry,
    // helpers
    addLog
  }
}