<template>
  <div class="wf">
    <header class="wf-head">
      <div>
        <h2>🔗 工作流编排</h2>
        <p class="muted">把 12 个 API 模块（模型 / 训练 / 智能体 / 知识库 / 工作流 / 推理）自由组合成可执行流程</p>
      </div>
      <div class="wf-head-actions">
        <el-button :underline="false" @click="showGuide = true">
          <el-icon><QuestionFilled /></el-icon>
          使用说明
        </el-button>
        <el-button :underline="false" @click="showCases = true" type="warning" plain>
          <el-icon><DataAnalysis /></el-icon>
          案例库
        </el-button>
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

    <!-- ============ 使用说明 drawer ============ -->
    <el-drawer v-model="showGuide" title="工作流编排使用说明" size="560px" :with-header="true" direction="rtl">
      <div class="guide">
        <h3>🎯 这是什么?</h3>
        <p>工作流编排器把 12 个后端 API (模型 / 训练 / 智能体 / 知识库 / 推理 / 工具) 自由组合成可执行流程.
        节点即 API, 连线即数据流, 一次编排可在生产环境直接运行.</p>

        <h3>🧱 核心概念</h3>
        <el-table :data="conceptRows" :show-header="false" size="small" border>
          <el-table-column prop="k" label="概念" width="120" />
          <el-table-column prop="v" label="说明" />
        </el-table>

        <h3>🖱️ 操作指南</h3>
        <ol class="guide-steps">
          <li><strong>拖拽或点击</strong>左侧节点, 添加到画布 (节点将自动布局)</li>
          <li>调整节点位置: 鼠标拖动</li>
          <li>连接节点: 拖动节点右侧输出端口到下一个节点的左侧输入端口</li>
          <li>配置参数: 鼠标悬停节点, 在弹出的"参数表单"里填写 (corpus / epochs / agentId 等)</li>
          <li><strong>查看节点说明</strong>: 鼠标悬停<em>左侧节点库</em>中的节点, 弹出该节点的参数 tips</li>
          <li>点击"运行"执行工作流, 右侧实时日志显示每步状态</li>
          <li>完成后可"另存为"模板, 或"加载 RAG 模板"快速开始</li>
        </ol>

        <h3>📝 配置占位符</h3>
        <p>节点的参数中支持 <code v-pre>{{input}}</code> 占位符, 接收上一个节点的输出. 例如:</p>
        <pre>kb_search: query: <code v-pre>{{input}}</code>  ← 接收上游节点的输出作为查询</pre>

        <h3>💡 调试技巧</h3>
        <ul>
          <li>右键节点: 删除 / 复制 / 禁用</li>
          <li>右上"另存为"导出 JSON, 可直接用于 curl 调用</li>
          <li>Feign 调用已统一 <code>10s/30s</code> 超时, 避免 Read Timed Out</li>
          <li>开发模式 (登录页) 启用明文密码, 不依赖 BCrypt 工具</li>
        </ul>

        <h3>📚 推荐入门</h3>
        <p>点击右上 <strong>案例库</strong>, 加载预置的"智能客服 RAG 训练"完整 8 步流程,
        修改参数即可用于生产环境.</p>
      </div>
    </el-drawer>

    <!-- ============ 案例库 drawer ============ -->
    <el-drawer v-model="showCases" title="📚 完整大模型编排案例" size="760px" direction="rtl">
      <div class="cases">
        <el-alert type="info" :closable="false" show-icon class="cases-tip">
          <template #title>每个案例含 8 步编排, 点击"加载到画布"即可一键复用</template>
        </el-alert>
        <div v-for="cs in caseStudies" :key="cs.key" class="case-card">
          <div class="case-head">
            <div class="case-emoji">{{ cs.emoji }}</div>
            <div class="case-meta">
              <h4>{{ cs.title }}</h4>
              <p>{{ cs.desc }}</p>
              <div class="case-tags">
                <el-tag v-for="t in cs.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
                <el-tag size="small" type="warning">{{ cs.level }}</el-tag>
                <el-tag size="small" type="success">{{ cs.duration }}</el-tag>
              </div>
            </div>
            <el-button type="primary" plain @click="loadCase(cs)">
              <el-icon><Download /></el-icon>
              加载到画布
            </el-button>
          </div>
          <el-collapse>
            <el-collapse-item :title="`📋 查看 ${cs.steps.length} 步编排详情`" name="detail">
              <el-steps :active="cs.steps.length" finish-status="success" direction="vertical" space="80px">
                <el-step v-for="(s, i) in cs.steps" :key="i" :title="`${i+1}. ${s.name}`" :description="s.desc">
                  <template #description>
                    <div class="step-desc">
                      <p>{{ s.desc }}</p>
                      <div class="step-params">
                        <strong>推荐参数:</strong>
                        <code v-for="(v, k) in s.params" :key="k">{{ k }}=<span>{{ v }}</span></code>
                      </div>
                      <el-tag size="small" type="info" effect="plain">调用: {{ s.tool }}</el-tag>
                    </div>
                  </template>
                </el-step>
              </el-steps>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-drawer>

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
          <el-popover
            v-for="n in g.nodes"
            :key="n.id"
            placement="right"
            :width="320"
            trigger="hover"
            :show-after="200"
            popper-class="node-tip-popper"
          >
            <template #reference>
              <div
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
            </template>
            <div class="tip-box">
              <div class="tip-title">
                <el-icon><component :is="n.icon" /></el-icon>
                {{ n.name }}
                <el-tag size="small" effect="plain" type="info">{{ n.id }}</el-tag>
              </div>
              <p class="tip-desc">{{ n.desc }}</p>
              <table v-if="n.tips" class="tip-table">
                <tr v-for="t in n.tips" :key="t.k">
                  <td class="tip-k">{{ t.k }}</td>
                  <td class="tip-v">{{ t.v }}</td>
                </tr>
              </table>
              <div v-if="n.config" class="tip-config">
                <strong>配置示例:</strong>
                <code v-for="(v, k) in n.config" :key="k">{{ k }}={{ v }}</code>
              </div>
            </div>
          </el-popover>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  QuestionFilled, DataAnalysis,
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
/**
 * 案例库: 完整可加载的大模型编排案例.
 * 每个案例含: 名称 / 描述 / 标签 / 8 步详情 / 节点模板.
 */
const caseStudies = [
  {
    key: 'rag-customer-service',
    title: '智能客服 RAG 训练流程',
    emoji: '🤖',
    desc: '从原始 FAQ 文档到可上线的智能客服: 数据清洗 → 切片 → Embedding → 向量索引 → RAG 检索 → 答案生成 → 评估 → 部署',
    duration: '约 45 分钟',
    level: '中等',
    tags: ['RAG', '客服', 'Embedding', 'BGE', 'Milvus'],
    steps: [
      { name: '数据源接入', type: 'load_doc', tool: 'dataset_list', params: { source: '/data/faq/2024.jsonl', format: 'jsonl' }, desc: '读取 FAQ 原始语料 (jsonl/json/csv)' },
      { name: '文本清洗', type: 'clean', tool: 'tool_clean', params: { rules: '去除HTML/URL/邮箱; 保留中英文' }, desc: '正则清洗, 去除噪声' },
      { name: '文档切片', type: 'chunk', tool: 'tool_chunk', params: { chunkSize: 256, overlap: 32, by: 'sentence' }, desc: '滑窗切片 256 token, 重叠 32, 按句子边界' },
      { name: 'Embedding', type: 'embed', tool: 'infer_embed', params: { model: 'BAAI/bge-small-zh-v1.5', dim: 512, batchSize: 32 }, desc: 'BGE 中文小模型, 512 维, 批 32' },
      { name: '向量索引', type: 'index', tool: 'kb_index', params: { backend: 'milvus', collection: 'faq_v1', metric: 'cosine' }, desc: 'Milvus 集合 faq_v1, cosine 相似度' },
      { name: 'RAG 检索', type: 'retrieve', tool: 'kb_search', params: { query: '{{input}}', topK: 3, rerank: true }, desc: 'topK=3, 启用 reranker 精排' },
      { name: '答案生成', type: 'generate', tool: 'agent_chat', params: { agentId: 1, message: '{{input}} + 上下文' }, desc: 'Agent 融合 prompt + 检索结果' },
      { name: '评估与部署', type: 'eval_deploy', tool: 'infer_eval', params: { metrics: 'BLEU/ROUGE/Hit@3', deploy: 'kubernetes' }, desc: '评估集测试, K8s 灰度上线' }
    ]
  },
  {
    key: 'finetune-llama',
    title: 'Llama 中文指令微调',
    emoji: '🦙',
    desc: '基于 Llama-3-8B 的中文 LoRA 微调: 数据准备 → 分词 → LoRA 训练 → 合并权重 → 推理评估',
    duration: '约 6 小时 (单 A100)',
    level: '高级',
    tags: ['Llama', 'LoRA', 'PEFT', '微调'],
    steps: [
      { name: '指令数据准备', type: 'data', tool: 'dataset_list', params: { source: 'alpaca_zh_10k.json' }, desc: 'alpaca 格式 1 万条中文指令' },
      { name: '分词与编码', type: 'tokenize', tool: 'tool_tokenize', params: { tokenizer: 'llama-3', maxLen: 2048 }, desc: 'Llama-3 tokenizer, max_len 2048' },
      { name: 'LoRA 训练', type: 'train', tool: 'train_start', params: { base: 'llama-3-8b', rank: 16, alpha: 32, lr: '2e-4', epochs: 3 }, desc: 'rank=16, alpha=32, lr=2e-4, 3 epoch' },
      { name: '权重合并', type: 'merge', tool: 'tool_merge', params: { adapter: 'lora-3ep', target: 'merged.safetensors' }, desc: 'LoRA 权重合并到 base' },
      { name: '推理测试', type: 'infer', tool: 'infer', params: { text: '你好', maxTokens: 200, temp: 0.7 }, desc: '20 条 sample 推理验证' },
      { name: '评估指标', type: 'eval', tool: 'tool_eval', params: { metrics: 'BLEU/PP/HumanEval' }, desc: 'BLEU + 困惑度 + 人工抽检' },
      { name: '注册模型', type: 'register', tool: 'model_register', params: { name: 'llama-3-8b-zh-v1', stage: 'staging' }, desc: '写入 ModelRegistry, stage=staging' },
      { name: '灰度发布', type: 'deploy', tool: 'tool_deploy', params: { canary: 10, duration: '24h' }, desc: '10% 流量 24h, 监控后全量' }
    ]
  },
  {
    key: 'agent-loop',
    title: 'Agent 工具调用循环',
    emoji: '🔄',
    desc: 'ReAct 风格 Agent: 思考 → 工具选择 → 执行 → 观察 → 循环直到完成',
    duration: '约 5 分钟',
    level: '入门',
    tags: ['Agent', 'ReAct', 'Tool Use'],
    steps: [
      { name: '用户提问', type: 'input', tool: 'user_input', params: { prompt: '查询天气' }, desc: '用户输入: 查询天气' },
      { name: 'LLM 思考', type: 'think', tool: 'agent_chat', params: { agentId: 2, message: '{{input}}' }, desc: 'Agent 决定是否调用工具' },
      { name: '工具选择', type: 'select_tool', tool: 'tool_select', params: { tools: ['web_search', 'calculator', 'knowledge'] }, desc: '从工具列表选 1 个' },
      { name: '执行工具', type: 'execute', tool: 'tool_exec', params: { tool: '{{selected}}' }, desc: '执行所选工具' },
      { name: '结果观察', type: 'observe', tool: 'tool_observe', params: { format: 'json' }, desc: '获取工具返回' },
      { name: '循环判断', type: 'loop_check', tool: 'agent_chat', params: { condition: '任务完成?' }, desc: '未完成则回到思考' },
      { name: '最终答案', type: 'finalize', tool: 'agent_chat', params: { agentId: 2, message: '总结' }, desc: '输出最终答复' }
    ]
  }
]

const palette = [
  {
    group: '模型 / 数据', icon: '🧠', c1: '#6366f1',
    nodes: [
      {
        id: 'model_list',   name: '列出模型',     icon: Cpu,     desc: 'GET /api/model',   api: () => modelApi.list(),
        tips: [
          { k: '接口', v: 'GET /api/model' },
          { k: '返回', v: 'SysModel[]: id/name/stage/version' },
          { k: '场景', v: '工作流起点; 选可用模型' },
          { k: '注意', v: '需 token; stage 过滤 PRETRAINED/STAGING/PROD' }
        ]
      },
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
const showGuide = ref(false)  // 使用说明 drawer
const showCases = ref(false)  // 案例库 drawer

const conceptRows = [
  { k: '节点 Node', v: '封装单个后端 API 调用 (如 kb_search)' },
  { k: '连线 Edge', v: '节点间的数据流, 上游输出喂给下游' },
  { k: '输入端口 in', v: '节点左侧, 接收上游输出 (可为空)' },
  { k: '输出端口 out', v: '节点右侧, 产出本节点结果' },
  { k: 'config', v: '节点的运行参数 (corpus / epochs / message 等)' },
  { k: '占位符', v: '{{input}} 自动被上游输出替换' },
  { k: '运行 Run', v: '按连线顺序逐个执行, 失败时停下' }
]

/**
 * 加载案例到画布: 把 8 步模板的步骤展开成节点串.
 */
const loadCase = (cs) => {
  ElMessageBox.confirm(
    `确认加载「${cs.title}」到当前画布? 当前节点会被替换.`,
    '加载案例',
    { type: 'info', confirmButtonText: '加载', cancelButtonText: '取消' }
  ).then(() => {
    nodes.value = []
    edges.value = []
    const startX = 80, startY = 60
    const dx = 320, dy = 140
    cs.steps.forEach((s, i) => {
      const col = i % 2
      const row = Math.floor(i / 2)
      const matchedNode = findNodeByTool(s.tool)
      if (matchedNode) {
        addNode(matchedNode, startX + col * dx, startY + row * dy)
      }
    })
    showCases.value = false
    ElMessage.success(`已加载 ${cs.steps.length} 步 / 案例: ${cs.title}`)
  }).catch(() => {})
}

const findNodeByTool = (tool) => {
  for (const g of palette) {
    for (const n of g.nodes) {
      if (n.id === tool) return n
    }
  }
  // fallback
  return palette[3].nodes[1]  // kb_search
}
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

/* ====== 使用说明 drawer ====== */
.guide { padding: 8px 0; }
.guide h3 { font-size: 14px; margin: 18px 0 8px; color: #1e1b4b; padding-left: 8px; border-left: 3px solid #6366f1; }
.guide p { font-size: 13px; line-height: 1.8; color: #475569; margin: 6px 0; }
.guide ol, .guide ul { padding-left: 20px; }
.guide li { font-size: 13px; line-height: 1.9; color: #475569; }
.guide code { background: #f1f5f9; padding: 1px 6px; border-radius: 3px; font-size: 12px; color: #ec4899; }
.guide pre { background: #1e293b; color: #e2e8f0; padding: 10px 14px; border-radius: 6px; font-size: 12px; line-height: 1.6; }
.guide-steps li::marker { color: #6366f1; font-weight: 700; }

/* ====== 节点 tip popover ====== */
.tip-box { font-size: 12px; }
.tip-title { display: flex; align-items: center; gap: 6px; font-weight: 700; color: #1e1b4b; margin-bottom: 6px; font-size: 13px; }
.tip-desc { color: #64748b; line-height: 1.6; margin: 0 0 8px; }
.tip-table { width: 100%; border-collapse: collapse; }
.tip-table tr { border-top: 1px dashed #e5e7eb; }
.tip-table td { padding: 4px 6px; font-size: 11px; vertical-align: top; }
.tip-k { color: #6366f1; font-weight: 600; width: 60px; }
.tip-v { color: #475569; }
.tip-config { margin-top: 8px; padding-top: 8px; border-top: 1px solid #e5e7eb; font-size: 11px; }
.tip-config strong { color: #1e1b4b; display: block; margin-bottom: 4px; }
.tip-config code { display: inline-block; background: #f1f5f9; padding: 1px 5px; border-radius: 3px; margin: 2px; color: #ec4899; }
:deep(.node-tip-popper) { padding: 12px 14px !important; max-width: 360px !important; }

/* ====== 案例库 drawer ====== */
.cases { padding: 8px 0; }
.cases-tip { margin-bottom: 12px; }
.case-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; margin-bottom: 16px; }
.case-head { display: flex; gap: 12px; align-items: flex-start; }
.case-emoji { font-size: 36px; line-height: 1; }
.case-meta { flex: 1; }
.case-meta h4 { font-size: 16px; font-weight: 700; color: #1e1b4b; margin: 0 0 4px; }
.case-meta p { font-size: 12px; color: #64748b; line-height: 1.6; margin: 0 0 8px; }
.case-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.case-card :deep(.el-collapse-item__header) { padding-left: 0; font-weight: 600; color: #6366f1; }

.step-desc p { font-size: 12px; color: #64748b; margin: 0 0 6px; }
.step-params { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; margin: 4px 0; }
.step-params strong { font-size: 11px; color: #1e1b4b; width: 100%; margin-bottom: 2px; }
.step-params code { background: #f1f5f9; padding: 2px 6px; border-radius: 3px; font-size: 11px; color: #475569; }
.step-params code span { color: #ec4899; font-weight: 600; }
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
