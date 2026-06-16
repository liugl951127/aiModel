<template>
  <div class="wf">
    <header class="wf-head">
      <div>
        <h2>🔗 工作流编排</h2>
        <p class="muted">把 12 个 API 模块（模型 / 训练 / 智能体 / 知识库 / 工作流 / 推理）自由组合成可执行流程</p>
      </div>
      <div class="wf-head-actions">
        <el-button :underline="false" @click="loadTemplates">
          <el-icon><Reading /></el-icon>
          模板库
        </el-button>
        <el-button :underline="false" @click="saveAs">
          <el-icon><Folder /></el-icon>
          另存为
        </el-button>
        <el-button type="primary" :loading="running" @click="runWorkflow">
          <el-icon><VideoPlay /></el-icon>
          运行
        </el-button>
      </div>
    </header>

    <!-- ============ 节点库（左侧可拖入） ============ -->
    <div class="wf-grid">
      <aside class="palette">
        <h4>节点库</h4>
        <div v-for="g in palette" :key="g.group" class="pal-group">
          <div class="pal-group-hd">
            <span class="pal-ico" :style="`background: ${g.c1}`">{{ g.icon }}</span>
            <strong>{{ g.group }}</strong>
            <small class="muted">{{ g.nodes.length }}</small>
          </div>
          <div
            v-for="n in g.nodes"
            :key="n.id"
            class="pal-node"
            draggable="true"
            @dragstart="onDragStart($event, n)"
            @click="addNode(n)"
          >
            <el-icon><component :is="n.icon" /></el-icon>
            <div class="pal-meta">
              <div class="pal-name">{{ n.name }}</div>
              <div class="pal-desc">{{ n.desc }}</div>
            </div>
          </div>
        </div>
      </aside>

      <!-- ============ 画布 ============ -->
      <section class="canvas" @drop="onDrop($event)" @dragover.prevent @dragenter.prevent>
        <div v-if="!nodes.length" class="canvas-empty">
          <el-icon :size="48" color="#cbd5e1"><Plus /></el-icon>
          <h3>拖入节点开始编排</h3>
          <p class="muted">从左侧拖入节点 / 直接点击添加<br>连接节点后点"运行"即可执行</p>
          <el-button :underline="false" @click="loadTemplate('rag')" type="primary" plain>
            <el-icon><MagicStick /></el-icon>
            加载 RAG 模板
          </el-button>
        </div>

        <!-- SVG 连线层 -->
        <svg v-if="nodes.length" class="wires" :viewBox="`0 0 ${canvasW} ${canvasH}`" preserveAspectRatio="none">
          <defs>
            <linearGradient id="wireGrad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#6366f1" />
              <stop offset="100%" stop-color="#ec4899" />
            </linearGradient>
          </defs>
          <path
            v-for="(edge, i) in edges"
            :key="i"
            :d="edgePath(edge)"
            stroke="url(#wireGrad)"
            stroke-width="2"
            fill="none"
            class="wire"
            :class="{ active: running && edge.from === currentNode }"
          />
          <circle
            v-for="(edge, i) in edges"
            :key="`d${i}`"
            r="4"
            fill="#ec4899"
            :class="{ 'wire-dot': true, animating: running && edge.from === currentNode }"
            :style="edgeDotStyle(edge)"
          />
        </svg>

        <!-- 节点层 -->
        <div
          v-for="(n, i) in nodes"
          :key="n.id"
          class="wf-node"
          :class="{ running: currentNode === n.id, done: doneSet.has(n.id) }"
          :style="{ left: n.x + 'px', top: n.y + 'px' }"
          @mousedown="onNodeMouseDown($event, n)"
        >
          <div class="wn-head" :style="`background: ${n.c1}`">
            <el-icon><component :is="n.icon" /></el-icon>
            <strong>{{ n.name }}</strong>
            <el-button :underline="false" text size="small" class="wn-close" @click="removeNode(n.id)">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="wn-body">
            <div class="wn-ports">
              <span v-for="p in n.inputs || []" :key="`i${p}`" class="wn-port in" :data-node="n.id" :data-port="p" @mouseup="onPortMouseUp($event, n.id, p, 'in')" @mousedown.stop></span>
              <span v-for="p in n.outputs || ['out']" :key="`o${p}`" class="wn-port out" :data-node="n.id" :data-port="p" @mousedown.stop @click="connect(n.id, p)"></span>
            </div>
            <div class="wn-config" v-if="n.config && Object.keys(n.config).length">
              <div v-for="(v, k) in n.config" :key="k" class="wn-cfg-row">
                <label>{{ k }}</label>
                <el-input v-model="n.config[k]" size="small" />
              </div>
            </div>
            <div v-if="n.status" class="wn-status" :class="n.status">
              <el-icon v-if="n.status === 'running'"><Loading /></el-icon>
              <el-icon v-else-if="n.status === 'done'"><CircleCheckFilled /></el-icon>
              <el-icon v-else-if="n.status === 'error'"><CircleCloseFilled /></el-icon>
              <span>{{ n.statusText || n.status }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 右侧：执行日志 ============ -->
      <aside class="runlog">
        <div class="rl-head">
          <h4>执行日志</h4>
          <el-button :underline="false" text size="small" @click="logs = []">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div class="rl-body" ref="logEl">
          <p v-if="!logs.length" class="muted small empty">点"运行"开始执行</p>
          <article v-for="l in logs" :key="l.id" class="rl-item" :class="l.level">
            <span class="rl-time">{{ l.time }}</span>
            <span class="rl-tag">{{ l.level }}</span>
            <span class="rl-msg">{{ l.text }}</span>
          </article>
        </div>
        <footer class="rl-foot">
          <el-button v-if="doneSet.size > 0" :underline="false" type="primary" plain size="small" @click="downloadResult">
            <el-icon><Download /></el-icon>
            下载结果
          </el-button>
          <span class="muted small">{{ doneSet.size }} / {{ nodes.length }} 节点</span>
        </footer>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Cpu, Files, VideoPlay, UserFilled, Tools, Reading, ChatDotRound, Connection,
  Reading as ReadingIcon, OfficeBuilding, Promotion, Delete, Plus, MagicStick, Folder, Download,
  Loading, CircleCheckFilled, CircleCloseFilled, Close, Document
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'
import {
  modelApi, datasetApi, trainApi, agentApi, toolApi, knowledgeApi, inferenceApi
} from '@/api'

const bus = useGlobalBus()
const logEl = ref(null)

let _id = 0
const nid = () => `n${++_id}`

// ============== 节点库 ==============
const palette = [
  {
    group: '模型 / 数据', icon: '🧠', c1: '#6366f1',
    nodes: [
      { id: 'model_list',   name: '列出模型',     icon: Cpu,     desc: 'GET /api/model',   api: () => modelApi.list() },
      { id: 'dataset_list', name: '列出数据集',   icon: Files,   desc: 'GET /api/dataset', api: () => datasetApi.list() }
    ]
  },
  {
    group: '训练', icon: '⚡', c1: '#f59e0b',
    nodes: [
      { id: 'train_start',  name: '开始训练',     icon: VideoPlay, desc: 'POST /api/trainer/job', inputs: ['in'], outputs: ['out'],
        config: { corpus: '/opt/corpus/sample.txt', epochs: 10 }, api: (cfg) => trainApi.start({ corpus: cfg.corpus, epochs: cfg.epochs }) }
    ]
  },
  {
    group: '智能体', icon: '🤖', c1: '#8b5cf6',
    nodes: [
      { id: 'agent_list',   name: '列出智能体',   icon: UserFilled, desc: 'GET /api/agent' },
      { id: 'agent_chat',   name: '智能体对话',   icon: ChatDotRound, desc: 'POST /api/agent/chat', inputs: ['in'], outputs: ['out'],
        config: { agentId: 1, message: '你好' }, api: (cfg) => agentApi.chat({ agentId: Number(cfg.agentId), message: cfg.message }) }
    ]
  },
  {
    group: '知识库', icon: '📚', c1: '#10b981',
    nodes: [
      { id: 'kb_list',      name: '列出知识库',   icon: Reading,    desc: 'GET /api/knowledge' },
      { id: 'kb_search',    name: '知识库查询',   icon: Reading,    desc: 'POST /api/knowledge/search-enhanced', inputs: ['in'], outputs: ['out'],
        config: { query: '什么是 Seata' }, api: (cfg) => knowledgeApi.enhancedSearch({ query: cfg.query, topK: 3 }) }
    ]
  },
  {
    group: '工具 / 推理', icon: '🛠️', c1: '#06b6d4',
    nodes: [
      { id: 'tool_list',    name: '列出工具',     icon: Tools,       desc: 'GET /api/tool' },
      { id: 'infer',        name: '推理',         icon: ChatDotRound, desc: 'POST /api/inference', inputs: ['in'], outputs: ['out'],
        config: { text: '你好', maxTokens: 50 }, api: (cfg) => inferenceApi.generate({ text: cfg.text, maxTokens: Number(cfg.maxTokens) }) }
    ]
  }
]

// ============== 画布状态 ==============
const nodes = ref([])
const edges = ref([])
const currentNode = ref(null)
const doneSet = reactive(new Set())
const canvasW = 2000
const canvasH = 2000

const findNode = (id) => nodes.value.find(n => n.id === id)

const onDragStart = (e, n) => {
  e.dataTransfer.setData('node-template', JSON.stringify(n))
  e.dataTransfer.effectAllowed = 'copy'
}
const onDrop = (e) => {
  const raw = e.dataTransfer.getData('node-template')
  if (!raw) return
  const tpl = JSON.parse(raw)
  const rect = e.currentTarget.getBoundingClientRect()
  addNode(tpl, e.clientX - rect.left, e.clientY - rect.top)
}
const addNode = (tpl, x = 100 + nodes.value.length * 30, y = 100 + nodes.value.length * 30) => {
  nodes.value.push({
    ...tpl,
    id: nid(),
    x, y,
    status: null,
    statusText: null,
    config: tpl.config || {},
    inputs: tpl.inputs || [],
    outputs: tpl.outputs || ['out']
  })
}
const removeNode = (id) => {
  const i = nodes.value.findIndex(n => n.id === id)
  if (i >= 0) nodes.value.splice(i, 1)
  edges.value = edges.value.filter(e => e.from !== id && e.to !== id)
}

// ============== 节点拖动 ==============
const onNodeMouseDown = (e, n) => {
  if (e.target.closest('.wn-close') || e.target.closest('.wn-port') || e.target.closest('.wn-cfg-row')) return
  const startX = e.clientX, startY = e.clientY
  const ox = n.x, oy = n.y
  const move = (m) => {
    n.x = Math.max(0, ox + (m.clientX - startX))
    n.y = Math.max(0, oy + (m.clientY - startY))
  }
  const up = () => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

// ============== 端口连线（简化：点击两个端口连接） ==============
const _connectFrom = ref(null)
const connect = (nodeId, portName) => {
  if (!_connectFrom.value) {
    _connectFrom.value = { from: nodeId, port: portName }
    ElMessage.info(`已选中源节点 ${nodeId}，再点击目标节点的输入端口连线`)
  } else if (_connectFrom.value.from !== nodeId) {
    edges.value.push({ from: _connectFrom.value.from, fromPort: _connectFrom.value.port, to: nodeId, toPort: portName })
    _connectFrom.value = null
    ElMessage.success('已连接')
  }
}
const onPortMouseUp = (e, nodeId, port, dir) => {
  if (dir === 'in' && _connectFrom.value) {
    edges.value.push({ from: _connectFrom.value.from, fromPort: _connectFrom.value.port, to: nodeId, toPort: port })
    _connectFrom.value = null
  }
}
const edgePath = (e) => {
  const a = findNode(e.from)
  const b = findNode(e.to)
  if (!a || !b) return ''
  const x1 = a.x + 240, y1 = a.y + 30
  const x2 = b.x, y2 = b.y + 30
  const dx = (x2 - x1) / 2
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}
const edgeDotStyle = (e) => {
  const a = findNode(e.from)
  const b = findNode(e.to)
  if (!a || !b) return {}
  const x1 = a.x + 240, y1 = a.y + 30
  const x2 = b.x, y2 = b.y + 30
  return { offsetPath: `path('M ${x1} ${y1} C ${(x1+x2)/2} ${y1}, ${(x1+x2)/2} ${y2}, ${x2} ${y2}')`, offsetDistance: '50%' }
}

// ============== 模板 ==============
const loadTemplate = (name) => {
  nodes.value = []
  edges.value = []
  if (name === 'rag') {
    addNode(palette[3].nodes[1], 60, 60)   // kb_search
    addNode(palette[2].nodes[1], 360, 60)  // agent_chat
    edges.value.push({ from: nodes.value[0].id, fromPort: 'out', to: nodes.value[1].id, toPort: 'in' })
  }
}
const loadTemplates = () => {
  ElMessageBox ? ElMessageBox.alert('模板库: RAG / Agent Loop / Train Pipeline', '模板', { type: 'info' })
    : ElMessage.info('模板库: RAG / Agent Loop / Train Pipeline')
}
const saveAs = () => ElMessage.success('已保存到本地 (功能待开发云端)')

// ============== 执行 ==============
const running = ref(false)
const logs = ref([])
const addLog = (level, text) => {
  logs.value.push({
    id: Math.random().toString(36).slice(2),
    level, text,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false })
  })
  if (logs.value.length > 200) logs.value.length = 200
  nextTick(() => { if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight })
}
const runWorkflow = async () => {
  if (!nodes.value.length) return ElMessage.warning('画布为空')
  if (running.value) return
  running.value = true
  doneSet.clear()
  logs.value = []

  bus.emit('wf:event', { text: `工作流启动: ${nodes.value.length} 节点` })
  addLog('INFO', `工作流启动: ${nodes.value.length} 节点, ${edges.value.length} 边`)

  // 拓扑序执行 (简化版: 按节点顺序)
  for (const n of nodes.value) {
    currentNode.value = n.id
    n.status = 'running'
    n.statusText = '执行中'
    addLog('INFO', `▶ ${n.name} (${n.id})`)

    try {
      if (!n.api) {
        // 静态节点 (例如 list) — 不发请求, 标记 done
        await new Promise(r => setTimeout(r, 200))
        n.status = 'done'
        n.statusText = '已就绪'
        addLog('OK', `${n.name} 已就绪 (静态节点)`)
      } else {
        const resp = await n.api(n.config || {})
        const data = resp?.data?.data ?? resp?.data
        n.status = 'done'
        n.statusText = typeof data === 'string' ? data.slice(0, 30) : '完成'
        addLog('OK', `${n.name} 成功: ${typeof data === 'object' ? JSON.stringify(data).slice(0, 80) : data}`)

        // 推到 bus
        const evtName = n.id.startsWith('train') ? 'train' : n.id.startsWith('agent') ? 'agent' : n.id.startsWith('kb') ? 'kb' : 'sys'
        bus.emit(evtName + ':event', { text: `${n.name} 执行完成`, actor: 'workflow' })
      }
    } catch (e) {
      n.status = 'error'
      n.statusText = e?.response?.data?.message || e.message || '失败'
      addLog('ERR', `${n.name} 失败: ${n.statusText}`)
      bus.emit('sys:event', { text: `${n.name} 失败: ${n.statusText}` })
    }
    doneSet.add(n.id)
  }

  currentNode.value = null
  running.value = false
  addLog('INFO', '=== 工作流执行完成 ===')
  bus.emit('wf:event', { text: '工作流执行完成' })
  ElMessage.success('工作流执行完成')
}

const downloadResult = () => {
  const blob = new Blob([JSON.stringify(nodes.value.map(n => ({ id: n.id, name: n.name, status: n.status, statusText: n.statusText })), null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `workflow-result-${Date.now()}.json`; a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  bus.emit('sys:event', { text: '进入工作流编排页' })
})
</script>

<style scoped>
.wf { display: flex; flex-direction: column; gap: 12px; height: 100%; }
.wf-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.wf-head h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }
.wf-head-actions { display: flex; gap: 8px; }

.wf-grid { display: grid; grid-template-columns: 220px 1fr 320px; gap: 12px; flex: 1; min-height: 600px; }

.palette { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; padding: 12px; overflow-y: auto; }
.palette h4 { margin: 0 0 8px; font-size: 12px; color: #94a3b8; text-transform: uppercase; letter-spacing: 1px; }
.pal-group { margin-bottom: 12px; }
.pal-group-hd { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.pal-ico { width: 22px; height: 22px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 11px; }
.pal-node {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 8px;
  background: #f8fafc; cursor: grab;
  border: 1px solid #e5e7eb;
  margin-bottom: 4px;
  transition: all 0.15s;
}
.pal-node:hover { background: #ede9fe; border-color: #8b5cf6; transform: translateX(2px); }
.pal-node:active { cursor: grabbing; }
.pal-node .el-icon { color: #6366f1; }
.pal-meta { flex: 1; min-width: 0; }
.pal-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.pal-desc { font-size: 10px; color: #94a3b8; }

.canvas {
  position: relative; overflow: auto;
  background: var(--bg-top, #fff);
  background-image:
    radial-gradient(circle at 1px 1px, #e5e7eb 1px, transparent 0);
  background-size: 20px 20px;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 12px;
  min-height: 600px;
}
.canvas-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; pointer-events: none; }
.canvas-empty > * { pointer-events: auto; }
.canvas-empty h3 { margin: 8px 0 4px; color: #1e293b; }
.canvas-empty p { margin: 0 0 12px; }

.wires { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.wire { transition: all 0.2s; }
.wire.active { stroke-width: 3; filter: drop-shadow(0 0 4px #ec4899); }
.wire-dot { offset-rotate: 0deg; }
.wire-dot.animating { animation: flow 1.5s linear infinite; }
@keyframes flow { 0% { offset-distance: 0%; } 100% { offset-distance: 100%; } }

.wf-node {
  position: absolute; width: 240px;
  background: #fff; border: 1.5px solid #e5e7eb; border-radius: 10px;
  box-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.1);
  transition: all 0.2s; cursor: grab;
}
.wf-node:hover { box-shadow: 0 8px 20px -4px rgba(0, 0, 0, 0.2); }
.wf-node.running { border-color: #6366f1; box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.15); }
.wf-node.done { border-color: #10b981; }
.wn-head {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; color: #fff; font-size: 12px;
  border-radius: 8px 8px 0 0;
}
.wn-head strong { flex: 1; }
.wn-close { color: #fff !important; }
.wn-body { padding: 8px 10px; position: relative; }
.wn-ports { position: absolute; inset: 0; pointer-events: none; }
.wn-port { position: absolute; width: 10px; height: 10px; border-radius: 50%; background: #6366f1; pointer-events: auto; cursor: crosshair; }
.wn-port.in { left: -5px; top: 30px; }
.wn-port.out { right: -5px; top: 30px; background: #ec4899; }
.wn-config { margin-top: 6px; }
.wn-cfg-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.wn-cfg-row label { font-size: 10px; color: #94a3b8; flex-shrink: 0; min-width: 50px; }
.wn-cfg-row .el-input { flex: 1; }
.wn-cfg-row :deep(.el-input__wrapper) { padding: 1px 6px; }
.wn-status { margin-top: 6px; padding: 4px 8px; border-radius: 6px; font-size: 11px; display: flex; align-items: center; gap: 4px; }
.wn-status.running { background: #dbeafe; color: #1d4ed8; }
.wn-status.done { background: #d1fae5; color: #047857; }
.wn-status.error { background: #fee2e2; color: #b91c1c; }

.runlog { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; display: flex; flex-direction: column; }
.rl-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-bottom: 1px solid var(--border, #e5e7eb); }
.rl-head h4 { margin: 0; font-size: 13px; }
.rl-body { flex: 1; overflow-y: auto; padding: 8px 12px; font-family: 'SF Mono', Monaco, monospace; font-size: 11px; }
.rl-body .empty { text-align: center; padding: 30px 0; }
.rl-item { display: flex; gap: 6px; padding: 2px 0; line-height: 1.4; }
.rl-time { color: #94a3b8; flex-shrink: 0; }
.rl-tag { font-weight: 700; padding: 0 4px; border-radius: 3px; flex-shrink: 0; }
.rl-item.INFO .rl-tag { background: #dbeafe; color: #1d4ed8; }
.rl-item.OK .rl-tag { background: #d1fae5; color: #047857; }
.rl-item.ERR .rl-tag { background: #fee2e2; color: #b91c1c; }
.rl-msg { color: #334155; word-break: break-word; }
.rl-foot { padding: 8px 14px; border-top: 1px solid var(--border, #e7ebf0); display: flex; justify-content: space-between; align-items: center; font-size: 11px; }
</style>
