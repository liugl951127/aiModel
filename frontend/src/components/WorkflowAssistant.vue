<template>
  <div class="wfa-root" :style="rootStyle" v-if="visible">
    <!-- 浮窗标题栏 (可拖动) -->
    <div class="wfa-header" @mousedown="onDragStart">
      <div class="wfa-title">
        <div class="wfa-avatar"><el-icon><MagicStick /></el-icon></div>
        <div>
          <div class="wfa-name">AI 编排助手</div>
          <div class="wfa-status">
            <span class="wfa-dot" :class="{ active: status === 'ready' }"></span>
            {{ status === 'thinking' ? '正在分析... · ' + contextHint : '就绪 · ' + contextHint }}
          </div>
        </div>
      </div>
      <div class="wfa-actions">
        <el-button link size="small" @click="minimized = !minimized" :title="minimized ? '展开' : '收起'">
          <el-icon><component :is="minimized ? 'Plus' : 'Minus'" /></el-icon>
        </el-button>
        <el-button link size="small" @click="visible = false" title="关闭">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 主区 (可收起) -->
    <div v-show="!minimized" class="wfa-body">
      <!-- 上下文摘要 (画布状态) -->
      <div class="wfa-ctx">
        <span class="ctx-tag">节点 {{ ctx.nodeCount }}</span>
        <span class="ctx-tag">边 {{ ctx.edgeCount }}</span>
        <span v-if="ctx.cycleCount > 0" class="ctx-tag err">环 {{ ctx.cycleCount }}</span>
        <span v-else-if="ctx.nodeCount > 0" class="ctx-tag ok">无环</span>
        <span v-else class="ctx-tag">空画布</span>
      </div>

      <!-- 消息区 -->
      <div ref="msgEl" class="wfa-msgs">
        <div v-for="(m, i) in messages" :key="i" class="wfa-msg" :class="m.role">
          <div class="msg-bubble">
            <div v-if="m.title" class="msg-title">{{ m.title }}</div>
            <div class="msg-content">{{ m.content }}</div>
            <!-- 可操作建议 -->
            <div v-if="m.actions && m.actions.length" class="msg-actions">
              <el-button v-for="(a, ai) in m.actions" :key="ai" size="small" :type="a.type || 'primary'" plain @click="onAction(a)">
                <el-icon v-if="a.icon"><component :is="a.icon" /></el-icon>
                {{ a.label }}
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="status === 'thinking'" class="wfa-msg assistant">
          <div class="msg-bubble">
            <span class="thinking-dots"><span></span><span></span><span></span></span>
          </div>
        </div>
      </div>

      <!-- 快捷问题 -->
      <div v-if="quickQuestions.length" class="wfa-quick">
        <div class="quick-label">💡 推荐问题</div>
        <el-tag v-for="q in quickQuestions" :key="q.q" size="small" effect="plain" @click="ask(q.q)" style="cursor: pointer;">
          {{ q.q }}
        </el-tag>
      </div>

      <!-- 输入框 -->
      <div class="wfa-input">
        <el-input v-model="input" placeholder="问点什么... (例如: 怎么加 RAG 节点?)" @keyup.enter="ask()" size="default">
          <template #append>
            <el-button type="primary" @click="ask()" :loading="status === 'thinking'">
              <el-icon><Promotion /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>

  <!-- 关闭后小图标 -->
  <div v-else class="wfa-fab" @click="visible = true; minimized = false" title="打开 AI 助手">
    <el-icon :size="20"><MagicStick /></el-icon>
    <span v-if="hint" class="fab-hint">!</span>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Close, Plus, Minus, Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  // 画布状态 (父组件传入, 变化时自动重算推荐问题)
  context: { type: Object, required: true }
})
const emit = defineEmits(['action'])

// 浮窗位置 + 显隐
const visible = ref(true)
const minimized = ref(false)
const pos = reactive({ x: 0, y: 0 })
const dragStart = ref(null)
const rootStyle = computed(() => ({ right: pos.x + 'px', bottom: pos.y + 'px' }))

const onDragStart = (e) => {
  dragStart.value = { sx: e.clientX, sy: e.clientY, ox: pos.x, oy: pos.y }
  const onMove = (m) => {
    pos.x = Math.max(0, dragStart.value.ox - (m.clientX - dragStart.value.sx))
    pos.y = Math.max(0, dragStart.value.oy - (m.clientY - dragStart.value.sy))
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

// 上下文
const ctx = computed(() => props.context || {})
const contextHint = computed(() => {
  const c = ctx.value
  if (c.cycleCount > 0) return '检测到死循环, 建议先修复'
  if (c.nodeCount === 0) return '画布为空, 拖节点开始'
  if (c.nodeCount < 3) return '节点较少, 可以加更多'
  if (c.edgeCount < c.nodeCount - 1) return '部分节点未连接'
  return '流程已就绪, 可以运行'
})

// 消息
const messages = ref([
  {
    role: 'assistant',
    title: '👋 你好!',
    content: '我是 AI 编排助手, 可以帮你诊断流程问题、推荐参数、添加缺失的节点. 试试问我任何问题!',
    actions: [
      { label: '检查我的流程', event: 'diagnose' },
      { label: '如何加 RAG 节点', event: 'ask', payload: '怎么加 RAG 节点?' }
    ]
  }
])
const input = ref('')
const status = ref('ready') // ready | thinking
const msgEl = ref(null)
const hint = ref(false)

const scrollBottom = () => nextTick(() => { if (msgEl.value) msgEl.value.scrollTop = 1e9 })

// 快捷问题: 根据画布状态动态变
const quickQuestions = computed(() => {
  const c = ctx.value
  if (c.cycleCount > 0) return [
    { q: '为什么流程有死循环?' }, { q: '怎么修复这个循环?' }, { q: '怎么避免以后再出现?' }
  ]
  if (c.nodeCount === 0) return [
    { q: '新手怎么开始?' }, { q: '推荐一个简单流程' }, { q: 'RAG 流程怎么搭?' }
  ]
  if (c.nodeCount < 3) return [
    { q: '还应该加什么节点?' }, { q: '怎么提高效果?' }, { q: 'LLaMA 训练流程?' }
  ]
  if (!c.valid) return [
    { q: '流程哪里不合法?' }, { q: '怎么才能运行?' }
  ]
  return [
    { q: '还能优化吗?' }, { q: '每个参数怎么调?' }, { q: 'AI 给我建议' }
  ]
})

// 知识库 (内置, 不调后端 LLM, 节省 token + 实时响应)
const knowledge = {
  // 新手入门
  '新手|怎么开始|开始|上手|入门': {
    title: '🚀 新手 30 秒上手',
    content: '三步走: ① 从左侧拖一个 "数据加载" 节点到画布 ② 加一个 "知识检索" 节点 (从 kb_ingest 或 kb_search 拖) ③ 加 "Agent 思考" 节点, 拖线连起来. 然后点 [运行] 即可执行.\n\n最简单: 点右上角 [加载 RAG 模板], 一键填好 3 节点流水线.',
    actions: [{ label: '加载 RAG 模板', event: 'loadRag' }]
  },
  // RAG 相关
  'RAG|检索增强|知识库': {
    title: '📚 RAG 流程 (3 步)',
    content: 'RAG 完整流程: kb_ingest (文档入库) → kb_search (向量检索 TopK) → agent_think (LLM 基于检索结果回答).\n\n关键参数: topK 3-5 够用, chunkSize 256 token, 切片重叠 32.',
    actions: [{ label: '加载 RAG 模板', event: 'loadRag' }]
  },
  // 死循环
  '死循环|闭环|循环|自连|检测|fix': {
    title: '🛠️ 死循环修复',
    content: '死循环原因: A→B→A 这种环路. 修复方法:\n① 检查所有连线, 找反向箭头 ② 删掉造成循环的那根边 ③ 确认流程是有向无环图 (DAG) ④ 重新运行\n\n提示: 流程不合法时, 画布会变红, 节点会脉冲.',
  },
  // 训练
  '训练|lora|llama|微调|finetune': {
    title: '⚙️ 训练流程',
    content: '训练节点: train_lora (轻量微调) / train_dpo (偏好对齐) / train_full (全量).\n\n推荐: train_lora + eval_hallucination (幻觉检测) + model_register (注册版本).\n\n关键参数: lr=0.001, maxIters=200, batchSize=12.',
  },
  // 评估
  '评估|测试|指标|评分': {
    title: '🧪 模型评估',
    content: '评估节点: eval_bleu (文本相似度) / eval_hallucination (RAGAS 幻觉检测) / eval_rouge (摘要).\n\n推荐: 把 eval_bleu 加到训练后, 自动评估模型质量.',
  },
  // 部署
  '部署|上线|onnx|export': {
    title: '🚀 部署上线',
    content: '部署流程: model_register (注册版本) → model_deploy (ONNX 部署).\n\n建议: 先部署到 staging 测试, 再部署 prod 生产.',
  },
  // Agent
  'agent|智能体|react|工具调用': {
    title: '🤖 Agent 智能体',
    content: 'Agent 节点: agent_think (ReAct 自动工具调用) / agent_tool (直接调单个工具) / agent_chat (多轮对话).\n\n推荐: agent_think, maxSteps=5 够用, 多了 LLM 容易跑偏.',
  },
  // 知识库
  '知识库|kb|embedding|chunk|ingest': {
    title: '📚 知识库',
    content: 'kb 节点: kb_ingest (文档入库) / kb_search (检索) / kb_chunk (切片) / kb_embed (向量化).\n\n推荐: chunkSize=256, overlap=32, topK=3-5, 检索用 BGE 中文 512 维.',
  },
  // AI 建议
  'ai建议|参数建议|调优|建议|怎么调|优化': {
    title: '🤖 AI 调优建议',
    content: '每个节点都有 "AI 智能建议" 按钮, 双击节点后点 [🤖 根据当前输入给建议], 会针对每个参数推荐值 + 理由. 还可以 [全部应用] 一键设置.',
    actions: [{ label: '怎么用 AI 建议', event: 'ask', payload: 'AI 建议怎么用?' }]
  },
  // 默认
  'default': {
    title: '🤖 我可以帮你',
    content: '常见问题: ① 怎么搭 RAG 流程 ② 训练参数怎么调 ③ 死循环怎么修复 ④ 怎么部署 ⑤ 怎么用 AI 建议\n\n直接问, 例如: "怎么加 RAG 节点?" 或 "怎么调训练参数?"',
    actions: [
      { label: '检查我的流程', event: 'diagnose' },
      { label: '加载 RAG 模板', event: 'loadRag' }
    ]
  }
}

const findAnswer = (q) => {
  const lower = q.toLowerCase()
  for (const key in knowledge) {
    if (key === 'default') continue
    const keywords = key.split('|')
    if (keywords.some(k => lower.includes(k.toLowerCase()))) return knowledge[key]
  }
  return knowledge.default
}

const ask = (q) => {
  const text = (q || input.value).trim()
  if (!text) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollBottom()
  status.value = 'thinking'

  // 模拟思考 (300-800ms 真实感)
  setTimeout(() => {
    const ans = findAnswer(text)
    messages.value.push({ role: 'assistant', title: ans.title, content: ans.content, actions: ans.actions })
    status.value = 'ready'
    scrollBottom()
  }, 400 + Math.random() * 500)
}

const onAction = (a) => {
  if (a.event === 'ask') ask(a.payload)
  else emit('action', a)
}

// 监听画布状态, 异常时自动提示
watch(() => [ctx.value.cycleCount, ctx.value.valid, ctx.value.nodeCount], ([c, v, n]) => {
  if (c > 0) {
    hint.value = true
  }
})

onMounted(() => {})
</script>

<style scoped>
.wfa-root {
  position: fixed;
  width: 340px;
  max-height: 540px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 8px 24px -6px rgba(0,0,0,0.15);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-size: 13px;
}

.wfa-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 99;
  box-shadow: 0 6px 16px -4px rgba(99,102,241,0.4);
  transition: transform 0.2s;
}
.wfa-fab:hover { transform: scale(1.1); }
.fab-hint {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
  background: #ef4444;
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.wfa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  cursor: move;
  user-select: none;
}
.wfa-title { display: flex; align-items: center; gap: 8px; }
.wfa-avatar {
  width: 32px;
  height: 32px;
  background: rgba(255,255,255,0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.wfa-name { font-size: 13px; font-weight: 600; }
.wfa-status { font-size: 10px; opacity: 0.9; }
.wfa-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; margin-right: 4px; }
.wfa-dot.active { background: #10b981; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
.wfa-actions .el-button { color: #fff; }

.wfa-body { display: flex; flex-direction: column; height: 460px; }

.wfa-ctx {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #f0f0f0;
}
.ctx-tag {
  font-size: 10px;
  padding: 2px 6px;
  background: #e2e8f0;
  color: #475569;
  border-radius: 4px;
}
.ctx-tag.ok { background: #d1fae5; color: #047857; }
.ctx-tag.err { background: #fee2e2; color: #b91c1c; }

.wfa-msgs {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wfa-msg { display: flex; }
.wfa-msg.user { justify-content: flex-end; }
.msg-bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.wfa-msg.user .msg-bubble { background: #6366f1; color: #fff; }
.wfa-msg.assistant .msg-bubble { background: #f1f5f9; color: #1e293b; }
.msg-title { font-weight: 600; margin-bottom: 4px; color: #6366f1; }
.wfa-msg.user .msg-title { color: #fff; }
.msg-content { white-space: pre-wrap; }
.msg-actions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }

.thinking-dots { display: inline-flex; gap: 3px; }
.thinking-dots span { width: 6px; height: 6px; background: #94a3b8; border-radius: 50%; animation: bounce 1.2s infinite; }
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; } 40% { transform: scale(1); opacity: 1; } }

.wfa-quick { padding: 8px 12px; border-top: 1px solid #f0f0f0; }
.quick-label { font-size: 10px; color: #94a3b8; margin-bottom: 4px; }
.wfa-quick .el-tag { margin: 0 4px 4px 0; }

.wfa-input { padding: 8px 12px; border-top: 1px solid #f0f0f0; }
</style>
