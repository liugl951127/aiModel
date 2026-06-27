<template>
  <el-dialog v-model="visible" title="🪄 AI 极速生成流程" width="680px" :close-on-click-modal="false"
    @open="onOpen" @close="onClose">
    <div class="ai-gen">
      <!-- 当前画布状态 (多轮时显示) -->
      <div v-if="hasCurrent" class="ctx-bar">
        <el-icon><InfoFilled /></el-icon>
        <span>当前画布: <b>{{ currentName }}</b> · {{ currentNodes.length }} 节点 / {{ currentEdges.length }} 边</span>
        <el-tag v-if="lastAction" size="small" :type="actionType">{{ actionLabel }}</el-tag>
      </div>

      <!-- 用户输入 -->
      <div class="input-box">
        <el-input v-model="userInput" type="textarea" :rows="2"
          :placeholder="inputPlaceholder"
          @keyup.ctrl.enter="onGenerate" />
        <div class="hint">💡 支持多轮: '多 3 个评估节点' / '改成 RAG' / 'topK=5' / Ctrl+Enter 发送</div>
      </div>

      <!-- 生成按钮 -->
      <div class="gen-btn-row">
        <el-button type="primary" :loading="loading" @click="onGenerate" size="large">
          <el-icon><MagicStick /></el-icon>
          {{ hasCurrent ? 'AI 修改画布' : 'AI 一键生成' }}
        </el-button>
        <el-button v-if="hasCurrent" type="success" :disabled="!result || !result.nodes.length" plain @click="onRunDirect">
          <el-icon><VideoPlay /></el-icon> 应用并运行
        </el-button>
        <el-button @click="onReset" plain>{{ hasCurrent ? '重置对话' : '清空' }}</el-button>
      </div>

      <!-- 进度条 -->
      <div v-if="loading || progressVisible" class="progress-box">
        <div class="progress-label">
          <el-icon class="rotating"><Loading v-if="loading" /><Check v-else /></el-icon>
          <span>{{ progressLabel }}</span>
          <span class="progress-pct">{{ progressPct }}%</span>
        </div>
        <el-progress :percentage="progressPct" :stroke-width="14" :show-text="false"
          :color="progressColor" :duration="6" />
        <div class="progress-steps">
          <span :class="{ done: progressPct > 0, active: progressStep === 0 }">① 思考需求</span>
          <span :class="{ done: progressPct > 25, active: progressStep === 1 }">② 查询知识</span>
          <span :class="{ done: progressPct > 50, active: progressStep === 2 }">③ 生成节点</span>
          <span :class="{ done: progressPct > 80, active: progressStep === 3 }">④ 检查参数</span>
        </div>
      </div>

      <!-- 预设场景 (无当前画布时显示) -->
      <div v-if="!hasCurrent" class="scenarios">
        <div class="sc-label">📦 预设场景 (一键填):</div>
        <div class="sc-grid">
          <div v-for="s in scenarios" :key="s.key" class="sc-card" @click="useScenario(s)">
            <div class="sc-icon">{{ s.icon }}</div>
            <div class="sc-name">{{ s.name }}</div>
            <div class="sc-desc">{{ s.desc }}</div>
          </div>
        </div>
      </div>

      <!-- 多轮快捷命令 (有画布时显示) -->
      <div v-else class="scenarios">
        <div class="sc-label">💬 多轮快捷:</div>
        <div class="sc-grid">
          <div v-for="(q, i) in quickModifications" :key="i" class="sc-card sc-small" @click="useQuick(q)">
            <div class="sc-icon">{{ q.icon }}</div>
            <div class="sc-name">{{ q.name }}</div>
          </div>
        </div>
      </div>

      <!-- 生成结果 -->
      <div v-if="result" class="result">
        <div class="r-head">
          <div>
            <span class="r-name">
              <el-icon v-if="lastAction === 'replace'"><Refresh /></el-icon>
              <el-icon v-else-if="lastAction === 'add_node'"><Plus /></el-icon>
              <el-icon v-else-if="lastAction === 'delete_node'"><Delete /></el-icon>
              <el-icon v-else-if="lastAction === 'update_params'"><Setting /></el-icon>
              {{ result.name || '结果' }}
            </span>
            <el-tag v-if="result.action" size="small" :type="actionType" style="margin-left: 8px;">
              {{ actionLabel }}
            </el-tag>
          </div>
          <div class="r-meta">
            {{ result.nodes.length }} 节点 · {{ result.edges.length }} 边
          </div>
        </div>
        <div v-if="result.description" class="r-desc">{{ result.description }}</div>

        <!-- 节点预览 -->
        <div class="r-nodes">
          <div v-for="(n, i) in result.nodes" :key="n.id" class="r-node">
            <div class="r-num">{{ i + 1 }}</div>
            <div class="r-info">
              <div class="r-node-name">{{ n.name }}</div>
              <div class="r-node-type">{{ n.type }}</div>
            </div>
            <el-icon v-if="i < result.nodes.length - 1" class="r-arrow"><Right /></el-icon>
          </div>
        </div>

        <!-- 迷你流程图预览 (SVG): 让用户在填到画布前看到完整连接关系 -->
        <div v-if="result.edges && result.edges.length" class="r-graph">
          <div class="rg-label">🗺️ 连线预览 (点击'应用到画布'后会使用完整 SVG 连接):</div>
          <svg class="rg-svg" :viewBox="`0 0 ${graphW} ${graphH}`" preserveAspectRatio="xMidYMid meet">
            <defs>
              <marker id="rg-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#6366f1" />
              </marker>
            </defs>
            <path
              v-for="(e, i) in result.edges"
              :key="'e'+i"
              :d="miniEdgePath(e)"
              fill="none"
              stroke="#6366f1"
              stroke-width="2"
              marker-end="url(#rg-arrow)"
            />
            <g v-for="(n, i) in result.nodes" :key="'n'+n.id">
              <rect :x="n.x - 60" :y="n.y - 14" width="120" height="28" rx="6"
                :fill="i === 0 ? '#10b981' : (i === result.nodes.length - 1 ? '#f43f5e' : '#fff')"
                :stroke="i === 0 ? '#10b981' : (i === result.nodes.length - 1 ? '#f43f5e' : '#6366f1')"
                stroke-width="1.5" />
              <text :x="n.x" :y="n.y + 4" text-anchor="middle"
                :fill="i === 0 || i === result.nodes.length - 1 ? '#fff' : '#1f2937'"
                font-size="11" font-weight="600">{{ miniNodeLabel(n, i) }}</text>
            </g>
          </svg>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="result && result.nodes.length" type="primary" @click="onApply">
        <el-icon><Check /></el-icon>
        {{ hasCurrent ? '应用到画布' : '填到画布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onMounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Right, Check, VideoPlay, Plus, Delete, Refresh, Setting,
  InfoFilled, Loading
} from '@element-plus/icons-vue'
import { workflowApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 注入当前画布, 启用多轮
  current: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'apply'])

const bus = useGlobalBus()

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => visible.value = v)
watch(visible, v => emit('update:modelValue', v))

// ===== 迷你流程图预览 (SVG) =====
const GRAPH_W_DEFAULT = 600
const GRAPH_H_DEFAULT = 320
const MINI_NODE_W = 120
const MINI_NODE_H = 28
const graphW = computed(() => {
  if (!result.value || !result.value.nodes.length) return GRAPH_W_DEFAULT
  // 按节点最大 x + 边距定宽
  const maxX = Math.max(...result.value.nodes.map(n => n.x || 0))
  return Math.max(GRAPH_W_DEFAULT, maxX + 80)
})
const graphH = computed(() => {
  if (!result.value || !result.value.nodes.length) return GRAPH_H_DEFAULT
  const maxY = Math.max(...result.value.nodes.map(n => n.y || 0))
  return Math.max(GRAPH_H_DEFAULT, maxY + 60)
})
// 计算边路径 (用后端生成的真实坐标, 贝塞尔连接)
const miniEdgePath = (e) => {
  if (!result.value || !result.value.nodes) return ''
  const a = result.value.nodes.find(n => n.id === e.from)
  const b = result.value.nodes.find(n => n.id === e.to)
  if (!a || !b) return ''
  // 节点中点 (SVG viewBox 中点)
  const ax = a.x, ay = a.y
  const bx = b.x, by = b.y
  const halfW = MINI_NODE_W / 2
  // 出入点: 节点右边中点 → 节点左边中点
  const p1x = ax + halfW, p1y = ay
  const p2x = bx - halfW, p2y = by
  // 同行用直线 (后端生成的节点本来就在同 y 上), 不同行用贝塞尔
  if (Math.abs(p1y - p2y) < 5) {
    return `M ${p1x} ${p1y} L ${p2x} ${p2y}`
  }
  const dx = Math.max(40, (p2x - p1x) / 2)
  return `M ${p1x} ${p1y} C ${p1x + dx} ${p1y}, ${p2x - dx} ${p2y}, ${p2x} ${p2y}`
}
const miniNodeLabel = (n, i) => {
  // 显示缩写: 中文名字优先, 否则 type
  const name = n.name || n.type || `节点${i+1}`
  return name.length > 10 ? name.slice(0, 10) + '...' : name
}

const userInput = ref('')
const loading = ref(false)
const result = ref(null)
const scenarios = ref([])

// 进度条状态
const progressPct = ref(0)
const progressStep = ref(-1)  // -1 = 未开始
const progressLabel = ref('')
const progressVisible = ref(false)

let progressTimer = null

const progressColor = computed(() => {
  if (progressPct.value >= 100) return '#10b981'
  if (progressPct.value >= 50) return '#6366f1'
  return '#94a3b8'
})

const startProgress = () => {
  progressPct.value = 0
  progressStep.value = 0
  progressVisible.value = true
  progressLabel.value = '正在理解需求...'
  if (progressTimer) clearInterval(progressTimer)
  // 模拟 4 个阶段, 每个 0.6s, 总 2.4s 后 100%
  progressTimer = setInterval(() => {
    if (progressPct.value < 30) {
      progressPct.value += 5
      progressStep.value = 0
      progressLabel.value = '① 思考需求...'
    } else if (progressPct.value < 55) {
      progressPct.value += 4
      progressStep.value = 1
      progressLabel.value = '② 查询知识库...'
    } else if (progressPct.value < 85) {
      progressPct.value += 3
      progressStep.value = 2
      progressLabel.value = '③ 生成节点中...'
    } else if (progressPct.value < 99) {
      progressPct.value += 1
      progressStep.value = 3
      progressLabel.value = '④ 检查参数...'
    }
  }, 200)
}

const stopProgress = (success) => {
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  if (success) {
    progressPct.value = 100
    progressStep.value = 3
    progressLabel.value = '✓ 生成完成'
    // 3s 后隐藏
    setTimeout(() => { progressVisible.value = false }, 3000)
  } else {
    progressLabel.value = '✗ 生成失败'
  }
}

// 当前画布状态
const hasCurrent = computed(() => props.current && (props.current.nodes?.length || 0) > 0)
const currentName = computed(() => props.current?.name || '未命名')
const currentNodes = computed(() => props.current?.nodes || [])
const currentEdges = computed(() => props.current?.edges || [])
const lastAction = computed(() => result.value?.action)

const actionLabel = computed(() => ({
  replace: '🔄 重生成',
  add_node: '➕ 新增节点',
  delete_node: '➖ 删除节点',
  update_params: '⚙️ 更新参数'
}[lastAction.value] || '已生成'))

const actionType = computed(() => ({
  replace: 'warning',
  add_node: 'success',
  delete_node: 'danger',
  update_params: 'info'
}[lastAction.value] || 'success'))

const inputPlaceholder = computed(() => {
  if (hasCurrent.value) {
    return '多轮修改, 例: 多加 2 个评估节点 / 改成营销文案 / 删掉第 1 个 / topK=5'
  }
  return '一句话描述, 例: 做一个 RAG 知识库问答, 用 BGE 中文嵌入, topK=5'
})

const quickModifications = computed(() => [
  { icon: '➕', name: '加 1 个评估节点' },
  { icon: '➕', name: '加 1 个 LoRA 训练' },
  { icon: '➕', name: '加 1 个数据加载' },
  { icon: '🔄', name: '改成营销文案' },
  { icon: '🔄', name: '改成客服自动回复' },
  { icon: '➖', name: '删掉最后一个节点' },
  { icon: '⚙️', name: 'topK=10' },
  { icon: '⚙️', name: 'epochs=5' }
])

// 接收来自 AI 助手的 generate 事件
onMounted(() => {
  bus.on('workflow:ai-generate', ({ input }) => {
    visible.value = true
    nextTick(() => {
      userInput.value = input || ''
      setTimeout(() => onGenerate(), 200)
    })
  })
})

const onOpen = async () => {
  result.value = null
  userInput.value = ''
  if (!hasCurrent.value) {
    try {
      const r = await workflowApi.aiScenarios()
      // 兼容两种格式: {code,message,data:Array} 或 {data:Array}
      const list = r?.data?.data || r?.data
      if (Array.isArray(list)) scenarios.value = list
    } catch (e) { scenarios.value = [] }
  }
}

const onClose = () => { result.value = null; userInput.value = '' }

const useScenario = (s) => {
  userInput.value = s.input
  onGenerate()
}

const useQuick = (q) => {
  userInput.value = q.name
  onGenerate()
}

const onGenerate = async () => {
  if (!userInput.value.trim()) {
    ElMessage.warning('请先描述你的需求')
    return
  }
  loading.value = true
  startProgress()
  try {
    let r
    if (hasCurrent.value) {
      // 多轮修改
      r = await workflowApi.aiModify(userInput.value.trim(), {
        name: currentName.value,
        nodes: currentNodes.value,
        edges: currentEdges.value
      })
    } else {
      r = await workflowApi.aiGenerate(userInput.value.trim())
    }
    // axios 拦截器: code===200 已经剥了一层, r = {code, message, data: <workflow>}
    // 防御性: 3 种可能
    //   1. r = {code, message, data: <workflow>}     - 拦截器扒了
    //   2. r = {data: <Result>}                       - 未拦截
    //   3. r = <workflow>                             - 直接返
    let payload = null
    if (r?.data?.nodes && Array.isArray(r.data.nodes)) {
      // case 2 or 3
      payload = r.data
    } else if (r?.data?.data?.nodes && Array.isArray(r.data.data.nodes)) {
      // case 1 (interceptor 抓过)
      payload = r.data.data
    } else {
      console.warn('[AI] 返回格式识别不出, 完整 r:', JSON.stringify(r).slice(0, 500))
      ElMessage.error('返回格式不对, 请看 console')
      stopProgress(false)
      return
    }
    stopProgress(true)
    result.value = payload
    if (result.value.nodes.length === 0) {
      ElMessage.info('未识别到场景, 请描述更具体')
    } else {
      const a = result.value.action || 'replace'
      const label = { replace: '已重生成', add_node: '已新增节点', delete_node: '已删除节点', update_params: '已更新参数' }[a]
      ElMessage.success(`${label}: ${result.value.nodes.length} 节点 / ${result.value.edges.length} 边`)
    }
  } catch (e) {
    console.error('AI 生成失败:', e)
    stopProgress(false)
    ElMessage.error('生成失败: ' + (e?.message || '网络错误'))
  } finally {
    loading.value = false
  }
}

const onApply = () => {
  if (!result.value || !result.value.nodes.length) {
    ElMessage.warning('没有可应用的节点')
    return
  }
  emit('apply', result.value)
  visible.value = false
}

const onRunDirect = () => {
  if (!result.value || !result.value.nodes.length) {
    ElMessage.warning('没有可运行的节点')
    return
  }
  emit('apply', result.value)
  visible.value = false
  // 通知 workflow 跑
  setTimeout(() => bus.emit('workflow:run-direct', {}), 300)
}

const onReset = () => { userInput.value = ''; result.value = null }
</script>

<style scoped>
.ai-gen { padding: 0 4px; }

.ctx-bar {
  display: flex; align-items: center; gap: 8px;
  background: linear-gradient(135deg, #eef2ff, #f0f9ff);
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  padding: 6px 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #4338ca;
}

.input-box { margin-bottom: 10px; }
.hint { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.gen-btn-row { display: flex; gap: 8px; margin-bottom: 12px; }

.progress-box {
  background: linear-gradient(135deg, #f0f9ff, #eef2ff);
  border: 1px solid #c7d2fe;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 14px;
  animation: progress-in 0.3s ease-out;
}
@keyframes progress-in {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}
.progress-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #4338ca; font-weight: 500;
  margin-bottom: 6px;
}
.progress-pct { margin-left: auto; font-weight: 700; color: #6366f1; font-size: 13px; }
.rotating { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.progress-steps {
  display: flex; gap: 6px; margin-top: 6px;
  font-size: 10px; color: #94a3b8;
  flex-wrap: wrap;
}
.progress-steps span {
  padding: 2px 6px; border-radius: 4px; background: #fff;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
}
.progress-steps span.done { background: #d1fae5; color: #047857; border-color: #a7f3d0; }
.progress-steps span.active { background: #6366f1; color: #fff; border-color: #4f46e5; font-weight: 600; }

.scenarios { margin-bottom: 16px; }
.sc-label { font-size: 13px; color: #475569; margin-bottom: 8px; font-weight: 600; }
.sc-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.sc-card {
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.sc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px -2px rgba(99, 102, 241, 0.2);
  border-color: #6366f1;
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
}
.sc-small { padding: 6px 8px; text-align: center; }
.sc-icon { font-size: 18px; margin-bottom: 2px; }
.sc-name { font-size: 11px; font-weight: 600; color: #1e293b; }

.result {
  background: linear-gradient(135deg, #f0fdf4, #ecfdf5);
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  padding: 12px;
}
.r-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.r-name { font-size: 14px; font-weight: 700; color: #15803d; display: inline-flex; align-items: center; gap: 4px; }
.r-meta { font-size: 11px; color: #16a34a; }
.r-desc { font-size: 11px; color: #475569; margin-bottom: 8px; }

.r-nodes { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.r-node {
  display: flex; align-items: center;
  background: #fff;
  border: 1px solid #86efac;
  border-radius: 6px;
  padding: 4px 8px;
  gap: 6px;
}
.r-num {
  width: 18px; height: 18px;
  background: #22c55e;
  color: #fff;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700;
}
.r-info { display: flex; flex-direction: column; }
.r-node-name { font-size: 11px; font-weight: 600; color: #1e293b; }
.r-node-type { font-size: 9px; color: #94a3b8; }
.r-arrow { color: #22c55e; font-size: 14px; }

/* ★ v3.x 迷你流程图预览 (SVG) */
.r-graph {
  margin-top: 12px;
  padding: 10px;
  background: linear-gradient(135deg, #fafbff, #f5f7ff);
  border: 1px dashed #c7d2fe;
  border-radius: 8px;
}
.rg-label { font-size: 11px; color: #6366f1; font-weight: 500; margin-bottom: 6px; }
.rg-svg { width: 100%; height: auto; max-height: 320px; display: block; background: #fff; border-radius: 4px; }
</style>
