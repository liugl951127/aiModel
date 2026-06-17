<template>
  <div class="wf">
    <header class="wf-head">
      <div>
        <h2>🔗 工作流编排</h2>
        <p class="muted">把 12 个 API 模块（模型 / 训练 / 智能体 / 知识库 / 工作流 / 推理）自由组合成可执行流程</p>
      </div>
      <div class="wf-head-actions">
        <el-button-group>
          <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
            <el-button @click="undo" :disabled="historyIdx < 0">
              <el-icon><Back /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
            <el-button @click="redo" :disabled="historyIdx >= history.length - 1">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </el-tooltip>
        </el-button-group>
        <el-button-group>
          <el-tooltip content="全选 (Ctrl+A)" placement="bottom">
            <el-button @click="selectAll"><el-icon><Check /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="删除选中 (DEL)" placement="bottom">
            <el-button @click="deleteSelected" :disabled="selectedIds.length === 0" type="danger" plain>
              <el-icon><Delete /></el-icon>
              <span v-if="selectedIds.length > 0" class="del-count">{{ selectedIds.length }}</span>
            </el-button>
          </el-tooltip>
        </el-button-group>
        <span v-if="selectedIds.length" class="sel-info">已选 {{ selectedIds.length }} 节点</span>
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
      <section
        class="canvas"
        @drop="onDrop($event)"
        @dragover.prevent
        @dragenter.prevent
        @mousedown="onCanvasMouseDown"
        @mousemove="onCanvasMouseMove"
        @mouseup="onCanvasMouseUp"
        @click="onCanvasClick"
      >
        <!-- 框选矩形 -->
        <div
          v-if="selectionRect"
          class="selection-rect"
          :style="{
            left: Math.min(selectionRect.x1, selectionRect.x2) + 'px',
            top: Math.min(selectionRect.y1, selectionRect.y2) + 'px',
            width: Math.abs(selectionRect.x2 - selectionRect.x1) + 'px',
            height: Math.abs(selectionRect.y2 - selectionRect.y1) + 'px'
          }"
        />
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
          :class="{ running: currentNode === n.id, done: doneSet.has(n.id), selected: isSelected(n.id) }"
          :style="{ left: n.x + 'px', top: n.y + 'px' }"
          @mousedown.stop="onNodeMouseDown($event, n)"
          @click.stop="selectedIds = [n.id]"
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

defineOptions({ name: 'Workflow' })

import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  QuestionFilled, DataAnalysis, Back, RefreshRight,
  Cpu, Files, VideoPlay, UserFilled, Tools, Reading, ChatDotRound, Connection,
  Reading as ReadingIcon, OfficeBuilding, Promotion, Delete, Plus, MagicStick, Folder, Download,
  FolderOpened, Brush, ScaleToOriginal, Coin, Key, Search, SetUp, Refresh, Upload, User, DataLine,
  Loading, CircleCheckFilled, CircleCloseFilled, Close, Document
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'
import {
  modelApi, datasetApi, trainApi, agentApi, toolApi, knowledgeApi, inferenceApi
} from '@/api'

const bus = useGlobalBus()
const logEl = ref(null)

// ============== 画布状态 ==============
const nodes = ref([])        // 节点列表 [{id, type, name, x, y, params}]
const edges = ref([])        // 连接列表 [{from, fromPort, to, toPort}]
const doneSet = ref(new Set())  // 已执行完成的节点 id (运行中状态)
const canvasW = ref(2000)    // 画布逻辑宽度
const canvasH = ref(1200)    // 画布逻辑高度
const currentNode = ref(null)  // 正在执行的节点 id (高亮用)

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
    group: '数据准备', icon: '📥', c1: '#0ea5e9',
    nodes: [
      { id: 'dataset_list', name: '数据集列表',   icon: Files,   desc: 'GET /api/dataset', 
        config: { format: 'jsonl' },
        tips: [
          { k: '接口', v: 'GET /api/dataset' },
          { k: 'format', v: '过滤 jsonl/json/csv/parquet' },
          { k: '返回', v: 'Dataset[]: id/name/size/format' }
        ]
      },
      { id: 'data_loader', name: '数据加载',     icon: FolderOpened, desc: '读 jsonl/json/csv', inputs: ['in'],
        config: { path: '/data/corpus.jsonl', limit: 1000 },
        tips: [
          { k: 'path', v: '文件绝对路径或 http(s) URL' },
          { k: 'limit', v: '最多读 N 行' },
          { k: '输出', v: '{rows: Array, total}' }
        ]
      },
      { id: 'data_clean', name: '文本清洗',     icon: Brush,  desc: '去噪/去HTML/正则',
        config: { rules: '去除HTML/URL/邮箱', minLen: 10 },
        tips: [
          { k: 'rules', v: '自定义正则规则集' },
          { k: 'minLen', v: '过滤短于 N 字符的样本' }
        ]
      },
      { id: 'data_split', name: '数据划分',     icon: ScaleToOriginal, desc: 'train/val/test 切分',
        config: { train: 0.8, val: 0.1, test: 0.1, seed: 42 },
        tips: [
          { k: 'train/val/test', v: '比例, 和=1' },
          { k: 'seed', v: '随机种子, 保证可复现' }
        ]
      }
    ]
  },
  {
    group: '预处理', icon: '✂️', c1: '#f97316',
    nodes: [
      { id: 'chunker', name: '文档切片',     icon: Files,  desc: 'sliding window', inputs: ['in'],
        config: { chunkSize: 256, overlap: 32, by: 'sentence' },
        tips: [
          { k: 'chunkSize', v: '每片 token, 128-512' },
          { k: 'overlap', v: '重叠, 10-20%' },
          { k: 'by', v: 'sentence/paragraph/token' }
        ]
      },
      { id: 'tokenize', name: '分词编码',     icon: Key,   desc: 'tokenize + 编码',
        config: { tokenizer: 'bpe', maxLen: 2048 },
        tips: [
          { k: 'tokenizer', v: 'bpe/unigram/wordpiece' },
          { k: 'maxLen', v: '最大长度, 超长截断' }
        ]
      }
    ]
  },
  {
    group: 'Embedding / 索引', icon: '🧬', c1: '#10b981',
    nodes: [
      { id: 'embed', name: '向量化',     icon: Connection, desc: 'BGE/OpenAI/text2vec', inputs: ['in'], outputs: ['out'],
        config: { model: 'BAAI/bge-small-zh-v1.5', dim: 512, batchSize: 32 },
        tips: [
          { k: 'model', v: 'BAAI/bge-*, text2vec, OpenAI' },
          { k: 'dim', v: '维度: bge 512/768, OpenAI 1536/3072' },
          { k: 'batchSize', v: '16-64' }
        ]
      },
      { id: 'vector_index', name: '向量索引',   icon: Coin, desc: 'Milvus/ES/Chroma',
        config: { backend: 'milvus', collection: 'vec_v1', metric: 'cosine' },
        tips: [
          { k: 'backend', v: 'milvus/elasticsearch/chroma' },
          { k: 'metric', v: 'cosine/l2/ip' }
        ]
      },
      { id: 'kb_search',    name: '知识库查询',   icon: Reading,    desc: 'POST /api/knowledge/search-enhanced', inputs: ['in'], outputs: ['out'],
        config: { query: '什么是 Seata', topK: 3, rerank: true }, 
        tips: [
          { k: 'query', v: '查询文本; 支持 {{input}}' },
          { k: 'topK', v: '返回条数, 3-10' },
          { k: 'rerank', v: 'true 启用精排' },
          { k: '特性', v: '自动 query rewrite + rerank' }
        ]
      },
      { id: 'kb_index', name: '入库索引',   icon: Coin, desc: '添加文档到 KB',
        config: { kbId: 1, source: '/data/docs/' },
        tips: [
          { k: 'kbId', v: '知识库 ID' },
          { k: 'source', v: '文档路径/URL' }
        ]
      }
    ]
  },
  {
    group: '训练', icon: '⚡', c1: '#f59e0b',
    nodes: [
      { id: 'train_start',  name: '开始训练',     icon: VideoPlay, desc: 'POST /api/trainer/job', inputs: ['in'], outputs: ['out'],
        config: { corpus: '/opt/corpus/sample.txt', epochs: 10, batchSize: 32, lr: '2e-4' },
        tips: [
          { k: 'corpus', v: '训练语料路径' },
          { k: 'epochs', v: '5-50' },
          { k: 'batchSize', v: '32 默认, 大模型 8-16' },
          { k: 'lr', v: 'LoRA 2e-4, 全参 1e-5' },
          { k: '后端', v: 'ai-platform-trainer DJL + SSE' }
        ]
      },
      { id: 'lora_train', name: 'LoRA 微调',   icon: DataLine, desc: 'PEFT LoRA 训练',
        config: { baseModel: 'llama-3-8b', rank: 16, alpha: 32, dropout: 0.05, targetModules: 'q,k,v,o' },
        tips: [
          { k: 'rank', v: '4-64, 默认 16' },
          { k: 'alpha', v: '通常 = 2*rank' },
          { k: 'targetModules', v: 'q/k/v/o/gate/up/down' }
        ]
      },
      { id: 'dpo_train', name: 'DPO 训练',     icon: DataAnalysis, desc: '直接偏好优化',
        config: { beta: 0.1, refModel: 'base' },
        tips: [
          { k: 'beta', v: 'KL 系数, 0.1-0.5' }
        ]
      }
    ]
  },
  {
    group: '智能体', icon: '🤖', c1: '#8b5cf6',
    nodes: [
      { id: 'agent_list',   name: '列出智能体',   icon: UserFilled, desc: 'GET /api/agent',
        config: {},
        tips: [
          { k: '接口', v: 'GET /api/agent' },
          { k: '返回', v: 'Agent[]' }
        ]
      },
      { id: 'agent_chat',   name: '智能体对话',   icon: ChatDotRound, desc: 'POST /api/agent/chat', inputs: ['in'], outputs: ['out'],
        config: { agentId: 1, message: '你好', temperature: 0.7 },
        
        tips: [
          { k: 'agentId', v: '智能体 ID' },
          { k: 'message', v: '用户消息; {{input}} 占位' },
          { k: 'temperature', v: '0=稳定, 1=创意, 默认 0.7' }
        ]
      },
      { id: 'agent_think', name: 'Agent 思考',   icon: MagicStick, desc: 'ReAct 思考步骤',
        config: { prompt: '分析问题并决定下一步', maxSteps: 5 },
        tips: [
          { k: 'maxSteps', v: '最大思考步数, 防死循环' }
        ]
      },
      { id: 'agent_tool', name: '工具调用',     icon: Tools,  desc: 'function call',
        config: { tool: 'web_search', params: '{}' },
        tips: [
          { k: 'tool', v: 'web_search/calculator/code_exec' }
        ]
      }
    ]
  },
  {
    group: '工具 / 推理', icon: '🛠️', c1: '#06b6d4',
    nodes: [
      { id: 'tool_list',    name: '列出工具',     icon: Tools, desc: 'GET /api/tool',
        config: {},
        tips: [
          { k: '接口', v: 'GET /api/tool' }
        ]
      },
      { id: 'web_search',   name: '联网搜索',     icon: Search, desc: 'DuckDuckGo',
        config: { query: 'Spring Cloud Alibaba', maxResults: 5 },
        tips: [
          { k: 'query', v: '搜索词' },
          { k: 'maxResults', v: '1-20' }
        ]
      },
      { id: 'infer',        name: '推理',         icon: ChatDotRound, desc: 'POST /api/inference', inputs: ['in'], outputs: ['out'],
        config: { text: '你好', maxTokens: 50, temperature: 0.7 },
        
        tips: [
          { k: 'text', v: '输入文本; {{input}} 占位' },
          { k: 'maxTokens', v: '50-500' },
          { k: 'temperature', v: '0=稳定, 1=创意' }
        ]
      },
      { id: 'code_exec', name: '代码执行',     icon: Promotion, desc: '沙箱跑 Python/JS',
        config: { language: 'python', code: 'print(1+1)', timeout: 30 },
        tips: [
          { k: 'language', v: 'python/javascript/bash' },
          { k: 'timeout', v: '秒, 防死循环' }
        ]
      }
    ]
  },
  {
    group: '评估', icon: '📊', c1: '#ec4899',
    nodes: [
      { id: 'eval_bleu', name: 'BLEU 评估',   icon: DataLine, desc: '机器翻译指标',
        config: { references: '/data/ref.txt', predictions: '/data/pred.txt' },
        tips: [
          { k: '输出', v: '{bleu1, bleu2, bleu3, bleu4}' }
        ]
      },
      { id: 'eval_rouge', name: 'ROUGE 评估',  icon: DataLine, desc: '摘要指标',
        config: { type: 'rouge-l' },
        tips: [
          { k: 'type', v: 'rouge-1/2/l' },
          { k: '输出', v: '{precision, recall, f1}' }
        ]
      },
      { id: 'eval_human', name: '人工抽检',    icon: User,  desc: '采样 + 评分',
        config: { sampleRate: 0.1, dimensions: '准确/流畅/相关' },
        tips: [
          { k: 'sampleRate', v: '0-1' }
        ]
      }
    ]
  },
  {
    group: '输出 / 部署', icon: '🚀', c1: '#dc2626',
    nodes: [
      { id: 'model_register', name: '注册模型',   icon: Coin, desc: 'ModelRegistry 写入',
        config: { name: 'my-llm-v1', stage: 'staging' },
        tips: [
          { k: 'stage', v: 'dev/staging/prod' }
        ]
      },
      { id: 'model_deploy', name: '部署上线',     icon: Upload, desc: 'K8s/灰度发布',
        config: { modelId: 1, replicas: 2, canary: 10 },
        tips: [
          { k: 'replicas', v: '1-10' },
          { k: 'canary', v: '灰度 0-100' }
        ]
      },
      { id: 'webhook', name: 'Webhook 通知',   icon: Promotion, desc: 'POST 外部 URL',
        config: { url: 'https://example.com/hook', method: 'POST' },
        tips: [
          { k: 'method', v: 'POST/PUT/GET' }
        ]
      },
      { id: 'log', name: '日志输出',         icon: Document, desc: '写日志',
        config: { level: 'INFO', message: '{{output}}' },
        tips: [
          { k: 'level', v: 'DEBUG/INFO/WARN/ERROR' }
        ]
      }
    ]
  },
  {
    group: '控制流', icon: '🔀', c1: '#64748b',
    nodes: [
      { id: 'if_branch', name: '条件分支',     icon: SetUp, desc: 'if/else 路由',
        config: { condition: 'score > 0.8', trueTo: '', falseTo: '' },
        tips: [
          { k: 'condition', v: 'JS 表达式' }
        ]
      },
      { id: 'loop', name: '循环',             icon: Refresh, desc: 'forEach 迭代',
        config: { over: '{{rows}}', maxIter: 100 },
        tips: [
          { k: 'over', v: '要迭代的数组' },
          { k: 'maxIter', v: '防死循环' }
        ]
      },
      { id: 'parallel', name: '并行分支',     icon: Connection, desc: 'fan-out 同步执行',
        config: { branches: '[]' },
        tips: [
          { k: 'branches', v: 'JSON 数组' }
        ]
      },
      { id: 'merge', name: '合并',             icon: Coin, desc: 'fan-in 同步等待',
        config: { strategy: 'all' },
        tips: [
          { k: 'strategy', v: 'all/any/first' }
        ]
      }
    ]
  },
  {
    group: '原基础', icon: '🧠', c1: '#6366f1',
    nodes: [
      { id: 'model_list',   name: '列出模型',     icon: Cpu,     desc: 'GET /api/model',
        config: { stage: 'PROD' },
        tips: [
          { k: '接口', v: 'GET /api/model' },
          { k: 'stage', v: '过滤 stage' }
        ]
      }
    ]
  }
]
// ============ 画布框选 ============
let canvasEl = null
const onCanvasMouseDown = (e) => {
  // 仅在点击空白时启动框选 (target 是 canvas 本身或 svg 背景)
  if (e.target.classList && (e.target.classList.contains('canvas') || e.target.classList.contains('canvas-bg') || e.target.tagName === 'svg')) {
    canvasEl = e.currentTarget
    const rect = canvasEl.getBoundingClientRect()
    selectionRect.value = { x1: e.clientX - rect.left, y1: e.clientY - rect.top, x2: e.clientX - rect.left, y2: e.clientY - rect.top }
    clearSelection()
  }
}
const onCanvasMouseMove = (e) => {
  if (!selectionRect.value || !canvasEl) return
  const rect = canvasEl.getBoundingClientRect()
  selectionRect.value.x2 = e.clientX - rect.left
  selectionRect.value.y2 = e.clientY - rect.top
}
const onCanvasMouseUp = () => {
  if (selectionRect.value && canvasEl) {
    const r = selectionRect.value
    const x1 = Math.min(r.x1, r.x2), y1 = Math.min(r.y1, r.y2)
    const x2 = Math.max(r.x1, r.x2), y2 = Math.max(r.y1, r.y2)
    // 选区内的节点 id
    selectedIds.value = nodes.value.filter(n => n.x >= x1 && n.x <= x2 + 200 && n.y >= y1 && n.y <= y2 + 80).map(n => n.id)
  }
  selectionRect.value = null
  canvasEl = null
}
const onCanvasClick = (e) => {
  // 点击空白 (非节点) 清空选择
  if (e.target.classList && (e.target.classList.contains('canvas') || e.target.classList.contains('canvas-bg'))) {
    clearSelection()
  }
}

const addNode = (tpl, x = 100 + nodes.value.length * 30, y = 100 + nodes.value.length * 30) => {
  const node = {
    ...tpl,
    id: nid(),
    x, y,
    status: null,
    statusText: null,
    config: tpl.config || {},
    inputs: tpl.inputs || [],
    outputs: tpl.outputs || ['out']
  }
  nodes.value.push(node)
  pushHistory({ type: 'add', payload: { ...node } })
  return node
}
const removeNode = (id) => {
  const removed = nodes.value.filter(n => n.id === id)
  if (!removed.length) return
  const removedEdges = edges.value.filter(e => e.from === id || e.to === id)
  pushHistory({
    type: 'remove',
    payload: {
      ids: [id],
      nodes: removed,
      edges: removedEdges
    }
  })
  nodes.value = nodes.value.filter(n => n.id !== id)
  edges.value = edges.value.filter(e => e.from !== id && e.to !== id)
  selectedIds.value = selectedIds.value.filter(sid => sid !== id)
}

/**
 * 复制节点 (Ctrl+D / 右键菜单)
 */
const duplicateSelected = () => {
  if (!selectedIds.value.length) return
  const newIds = []
  selectedIds.value.forEach(sid => {
    const src = nodes.value.find(n => n.id === sid)
    if (src) {
      const copy = addNode({ ...src, name: src.name + ' (副本)' }, src.x + 40, src.y + 40)
      newIds.push(copy.id)
    }
  })
  selectedIds.value = newIds
  ElMessage.success(`已复制 ${newIds.length} 个节点`)
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
const findNode = (id) => nodes.value.find(n => n.id === id)

const edgePath = (e) => {
  const a = findNode(e.from)
  const b = findNode(e.to)
  if (!a || !b) return ''
  const x1 = a.x + 200, y1 = a.y + 30
  const x2 = b.x, y2 = b.y + 30
  const dx = (x2 - x1) / 2
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}
const edgeDotStyle = (e) => {
  const a = findNode(e.from)
  const b = findNode(e.to)
  if (!a || !b) return {}
  const x1 = a.x + 200, y1 = a.y + 30
  const x2 = b.x, y2 = b.y + 30
  return { offsetPath: `path('M ${x1} ${y1} C ${(x1+x2)/2} ${y1}, ${(x1+x2)/2} ${y2}, ${x2} ${y2}')`, offsetDistance: '50%' }
}

// ============== 模板 ==============
const onDragStart = (e, n) => {
  // 必须用 JSON.stringify, drop 里 JSON.parse 反序列化
  // 同时设 effectAllowed 允许 move/copy
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', JSON.stringify(n))
}

const onDrop = (e) => {
  const raw = e.dataTransfer.getData('text/plain')
  if (!raw) return
  const tpl = JSON.parse(raw)
  const rect = e.currentTarget.getBoundingClientRect()
  addNode(tpl, e.clientX - rect.left, e.clientY - rect.top)
}

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
// ============ 撤销/重做 (History Stack) ============
const history = ref([])     // { type: 'add'|'remove'|'update'|'move'|'connect', payload }
const historyIdx = ref(-1)
const MAX_HISTORY = 50
const pushHistory = (action) => {
  // 截断 redo 分支
  history.value = history.value.slice(0, historyIdx.value + 1)
  history.value.push({ ts: Date.now(), ...action })
  if (history.value.length > MAX_HISTORY) history.value.shift()
  historyIdx.value = history.value.length - 1
}
const undo = () => {
  if (historyIdx.value < 0) return
  const h = history.value[historyIdx.value]
  if (h.type === 'add') {
    nodes.value = nodes.value.filter(n => n.id !== h.payload.id)
  } else if (h.type === 'remove') {
    nodes.value.push(...h.payload.nodes)
    edges.value.push(...h.payload.edges)
  } else if (h.type === 'update') {
    const target = nodes.value.find(n => n.id === h.payload.id)
    if (target) Object.assign(target, h.payload.before)
  } else if (h.type === 'move') {
    const target = nodes.value.find(n => n.id === h.payload.id)
    if (target) { target.x = h.payload.x; target.y = h.payload.y }
  } else if (h.type === 'connect') {
    edges.value = edges.value.filter(e => e.from !== h.payload.from || e.to !== h.payload.to)
  } else if (h.type === 'batch') {
    h.payload.revert()
  }
  historyIdx.value--
  ElMessage.success('已撤销')
}
const redo = () => {
  if (historyIdx.value >= history.value.length - 1) return
  historyIdx.value++
  const h = history.value[historyIdx.value]
  if (h.type === 'add') {
    nodes.value.push(h.payload)
  } else if (h.type === 'remove') {
    nodes.value = nodes.value.filter(n => !h.payload.ids.includes(n.id))
    edges.value = edges.value.filter(e => !h.payload.ids.includes(e.from) && !h.payload.ids.includes(e.to))
  } else if (h.type === 'update') {
    const target = nodes.value.find(n => n.id === h.payload.id)
    if (target) Object.assign(target, h.payload.after)
  } else if (h.type === 'move') {
    const target = nodes.value.find(n => n.id === h.payload.id)
    if (target) { target.x = h.payload.x2; target.y = h.payload.y2 }
  } else if (h.type === 'connect') {
    edges.value.push({ from: h.payload.from, to: h.payload.to })
  } else if (h.type === 'batch') {
    h.payload.apply()
  }
  ElMessage.success('已重做')
}

// ============ 多选 ============
const selectedIds = ref([])  // 多选节点 id
const selectionRect = ref(null)  // 框选矩形 {x1,y1,x2,y2}
let dragStart = null

const selectAll = () => {
  selectedIds.value = nodes.value.map(n => n.id)
  ElMessage.success(`已选中 ${selectedIds.value.length} 个节点`)
}
const clearSelection = () => { selectedIds.value = [] }
const isSelected = (id) => selectedIds.value.includes(id)
const deleteSelected = () => {
  if (selectedIds.value.length === 0) return
  const ids = [...selectedIds.value]
  const removedNodes = nodes.value.filter(n => ids.includes(n.id))
  const removedEdges = edges.value.filter(e => ids.includes(e.from) || ids.includes(e.to))
  pushHistory({
    type: 'remove',
    payload: { ids, nodes: removedNodes, edges: removedEdges }
  })
  nodes.value = nodes.value.filter(n => !ids.includes(n.id))
  edges.value = edges.value.filter(e => !ids.includes(e.from) && !ids.includes(e.to))
  selectedIds.value = []
  ElMessage.success(`已删除 ${ids.length} 个节点`)
}

// ============ 键盘快捷键 (window 监听) ============
const handleKey = (e) => {
  // 输入框/文本域内不触发
  const tag = (e.target?.tagName || '').toLowerCase()
  if (tag === 'input' || tag === 'textarea' || e.target?.isContentEditable) return
  if ((e.ctrlKey || e.metaKey) && e.key === 'a') {
    e.preventDefault(); selectAll()
  } else if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
    e.preventDefault(); duplicateSelected()
  } else if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    e.preventDefault(); undo()
  } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && e.key === 'z'))) {
    e.preventDefault(); redo()
  } else if (e.key === 'Delete' || e.key === 'Backspace') {
    e.preventDefault(); deleteSelected()
  } else if (e.key === 'Escape') {
    clearSelection()
  }
}
onMounted(() => { window.addEventListener('keydown', handleKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', handleKey) })

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
  doneSet.value.clear()
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
    doneSet.value.add(n.id)
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
.wf-head-actions { display: flex; gap: 8px; align-items: center; }
.del-count {
  display: inline-block; margin-left: 4px; padding: 0 5px; min-width: 18px;
  background: #ef4444; color: #fff; border-radius: 9px;
  font-size: 11px; font-weight: 600; line-height: 18px; text-align: center;
}
.sel-info {
  font-size: 12px; color: #6366f1; font-weight: 600;
  padding: 0 6px; background: rgba(99,102,241,0.08); border-radius: 4px;
}

.wf-grid { display: grid; grid-template-columns: 180px 1fr 280px; gap: 8px; flex: 1; min-height: 600px; }

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
  position: absolute; width: 200px;
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

/* 框选矩形 */
.selection-rect {
  position: absolute;
  background: rgba(99, 102, 241, 0.08);
  border: 1px dashed #6366f1;
  pointer-events: none;
  z-index: 5;
}

/* 节点选中态 */
.wf-node.selected { box-shadow: 0 0 0 2px #6366f1, 0 8px 24px -6px rgba(99, 102, 241, 0.4); }
.wf-node.selected .wn-head { filter: brightness(1.05); }

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
