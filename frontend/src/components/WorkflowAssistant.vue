<template>
  <div class="wfa-root" :style="rootStyle" v-if="visible">
    <!-- 浮窗标题栏 (可拖动) -->
    <div class="wfa-header" @mousedown="onDragStart">
      <div class="wfa-title">
        <div class="wfa-avatar">{{ pageMeta.icon || '🪄' }}</div>
        <div>
          <div class="wfa-name">{{ pageMeta.name ? pageMeta.name + ' 助手' : 'AI 助手' }}</div>
          <div class="wfa-status">
            <span class="wfa-dot" :class="{ active: status === 'ready' }"></span>
            {{ statusText }}
          </div>
        </div>
      </div>
      <div class="wfa-actions">
        <el-tooltip content="清空对话 (Ctrl+L)" placement="bottom">
          <el-button link size="small" @click="clearMessages" title="清空对话">
            <el-icon><Delete /></el-icon>
          </el-button>
        </el-tooltip>
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
      <!-- 页面上下文条 (顶部) -->
      <div class="wfa-page" v-if="pageMeta.name">
        <div class="page-info">
          <span class="page-icon">{{ pageMeta.icon }}</span>
          <span class="page-name">{{ pageMeta.name }}</span>
          <span class="page-desc">{{ pageMeta.description }}</span>
        </div>
      </div>

      <!-- Workflow 画布上下文 (如有) -->
      <div class="wfa-ctx" v-if="effectiveContext && effectiveContext.nodeCount !== undefined">
        <span class="ctx-tag">节点 {{ effectiveContext.nodeCount }}</span>
        <span class="ctx-tag">边 {{ effectiveContext.edgeCount }}</span>
        <span v-if="effectiveContext.cycleCount > 0" class="ctx-tag err">环 {{ effectiveContext.cycleCount }}</span>
        <span v-else-if="effectiveContext.nodeCount > 0" class="ctx-tag ok">无环</span>
        <span v-else class="ctx-tag">空画布</span>
      </div>

      <!-- 页面内容状态 (表格/控件) -->
      <div class="wfa-pagestatus" v-if="effectivePageStatus && effectivePageStatus.length">
        <span v-for="(s, i) in effectivePageStatus" :key="i" class="status-tag" :class="s.type">
          <el-icon v-if="s.icon"><component :is="s.icon" /></el-icon>
          {{ s.label }}: {{ s.value }}
        </span>
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
        <div class="quick-label">💡 {{ pageMeta.name ? '本页' : '推荐' }}问题</div>
        <el-tag v-for="q in quickQuestions" :key="q" size="small" effect="plain" @click="ask(q)" style="cursor: pointer;">
          {{ q }}
        </el-tag>
      </div>

      <!-- 输入框 -->
      <div class="wfa-input">
        <el-input v-model="input" :placeholder="inputPlaceholder" @keyup.enter="ask()" size="default">
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
    <el-icon :size="20">{{ pageMeta.icon || '🪄' }}</el-icon>
    <span v-if="hint" class="fab-hint">!</span>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Close, Plus, Minus, Promotion, Delete } from '@element-plus/icons-vue'
import { getPageKnowledge } from '@/composables/pageKnowledge'
import { useGlobalBus } from '@/composables/useGlobalBus'

const props = defineProps({
  // 画布状态 (Workflow 页传, 其它页可不传)
  context: { type: Object, default: () => null },
  // 页面内容状态 (表格/控件实时数据, 由父组件传)
  pageStatus: { type: Array, default: () => [] },
  // 自定义页面知识 (覆盖默认)
  customKnowledge: { type: Object, default: null }
})
const emit = defineEmits(['action', 'navigate'])
const route = useRoute()
const router = useRouter()
// ★ 默认使用 'default' agentId (后端有, 走 base ReAct loop); 页面 metadata 可覆盖
const agentId = ref(pageMeta.value?.agentId || 'default')
const bus = useGlobalBus()

// 全局推送的上下文 (画布状态 / 表格状态等)
const liveContext = ref(null)
const livePageStatus = ref([])

onMounted(() => {
  // 订阅全局 bus, 接收各页推送的实时状态
  bus.on('assistant:context', (payload) => {
    if (payload?.type === 'canvas') liveContext.value = payload.data
    else if (payload?.type === 'pageStatus') livePageStatus.value = payload.data || []
  })
  // 订阅诊断事件: 节点异常, AI 主动推诊断
  bus.on('assistant:diagnose', (payload) => {
    const node = payload?.node || '节点'
    const err = payload?.error || '未知错误'
    // 推一条助手消息
    messages.value.push({
      role: 'assistant',
      title: '🚨 AI 诊断: ' + node + ' 异常',
      content: `节点 [${node}] 执行失败: ${err}\n\n可能原因:
1. 参数缺失 (双击节点检查必填项)
2. 后端服务没起 (看 Nacos / 服务状态)
3. 模型未加载 (Train.vue 加载模型)
4. 网络问题 (重试或看后端日志)

快速修复: 双击节点, 点 [🤖 AI 补全参数], 会自动填所有字段.`,
      actions: [
        { label: '打开节点配置', event: 'navigate', payload: '/workflow' },
        { label: '问 AI 怎么修', event: 'ask', payload: '节点执行失败怎么修?' }
      ]
    })
    hint.value = true
    scrollBottom()
    // 弹窗展开
    if (minimized.value) minimized.value = false
  })
})

// 优先用 live (bus 推送), 兜底用 props (直接传)
const effectiveContext = computed(() => liveContext.value || props.context)
const effectivePageStatus = computed(() => livePageStatus.value.length ? livePageStatus.value : (props.pageStatus || []))

// 浮窗位置 + 显隐
const visible = ref(true)
const minimized = ref(false)
const pos = reactive({ x: 24, y: 24 })
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

// ====== 页面知识 (随路由变化) ======
const pageMeta = computed(() => {
  if (props.customKnowledge) return props.customKnowledge
  return getPageKnowledge(route.path)
})

const pageWelcome = computed(() => {
  const meta = pageMeta.value
  return `你好! 我是「${meta.name}」智能助手, 懂这页的每个功能. 试试问我任何问题!`
})

// 监听路由变化, 不再重置消息 — 持久到 localStorage, 跨页面/跨刷新保留
// ★ 旧代码: 每次切换路由都清空, 现在保持对话连续性
watch(() => route.path, (newPath) => {
  // 轻量提示: 不清空, 仅添加一条页面变更提示 (避免冗余, 跳过)
  // 如果用户想清, 点 [清空对话] 按钮 或 Ctrl+L
}, { immediate: false })

// ====== 消息 (localStorage 持久化, 跨页面/跨刷新保留) ======
const STORAGE_KEY = 'ai_assistant_messages_v1'
const loadMessages = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr) && arr.length) return arr
    }
  } catch (e) { /* 容错 */ }
  // 默认: 欢迎语
  return [{ role: 'assistant', title: '👋 你好!', content: '我是 AI 助手, 懂这页每个功能. 试试问我任何问题!' }]
}
const messages = ref(loadMessages())

// messages 变化时同步到 localStorage (debounce)
let saveTimer = null
watch(messages, () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    try {
      // 只保留最近 50 条 (避免 localStorage 满)
      const arr = messages.value.slice(-50)
      localStorage.setItem(STORAGE_KEY, JSON.stringify(arr))
    } catch (e) { /* quota 等异常容错 */ }
  }, 500)
}, { deep: true })

// 清空对话
const clearMessages = () => {
  messages.value = [{ role: 'assistant', title: '🗑️ 已清空对话', content: '你好! 试试问我任何问题. (Ctrl+L 也可清空)' }]
  ElMessage.success('对话已清空')
}
const input = ref('')

// 快捷键 Ctrl+L 清空
const onKey = (e) => {
  if (e.ctrlKey && e.key === 'l' && visible.value && !e.shiftKey) {
    clearMessages()
    e.preventDefault()
  }
}
const status = ref('ready')
const msgEl = ref(null)
const hint = ref(false)

const scrollBottom = () => nextTick(() => { if (msgEl.value) msgEl.value.scrollTop = 1e9 })

// ====== 状态文本 ======
const statusText = computed(() => {
  if (status.value === 'thinking') return '正在分析...'
  // 优先级: 画布状态 > 页面状态 > 默认
  if (effectiveContext.value?.cycleCount > 0) return '检测到死循环, 建议先修复'
  if (effectiveContext.value?.nodeCount > 0) return '画布编辑中, 随时可问'
  if (effectivePageStatus.value && effectivePageStatus.value.length) return `已加载 ${pageMeta.value.name}`
  return '就绪 · 随时提问'
})

// ====== 快捷问题 (页面优先) ======
const quickQuestions = computed(() => {
  // Workflow 页面特殊处理 (画布状态)
  if (effectiveContext.value) {
    const c = effectiveContext.value
    if (c.cycleCount > 0) return ['为什么有死循环?', '怎么修复?', '怎么避免再出现?']
    if (c.nodeCount === 0) return ['新手怎么开始?', '推荐一个简单流程', 'RAG 流程怎么搭?']
    if (c.nodeCount < 3) return ['还应该加什么?', '怎么提高效果?', 'LLaMA 训练流程?']
    if (!c.valid) return ['流程哪里不合法?', '怎么才能运行?']
    return ['还能优化吗?', '每个参数怎么调?', 'AI 给我建议']
  }
  // 普通页面: 用页面配置的 quickQuestions
  return pageMeta.value.quickQuestions || []
})

const inputPlaceholder = computed(() => {
  return `问点什么... (例如: ${(quickQuestions.value[0] || '怎么用这个页?').replace('?', '').replace('?', '')}?)`
})

// ====== 答案匹配 ======
// 优先级: 页面知识 > 通用知识
const findAnswer = (q) => {
  const lower = q.toLowerCase()
  const pageQA = pageMeta.value.qa || []

  // 1. 页面专属 Q&A
  for (const item of pageQA) {
    const kws = item.keywords.split('|')
    if (kws.some(k => lower.includes(k.toLowerCase()))) {
      return {
        title: pageMeta.value.name + ' 助手',
        content: item.answer,
        actions: item.actions,
        confidence: 0.9
      }
    }
  }

  // 2. 兜底: 通用
  return {
    title: '通用助手',
    content: `抱歉, 我对「${pageMeta.value.name}」这个具体问题不太清楚. \n\n试试这些:\n• 用「${pageMeta.value.name}」相关关键词\n• 或者点上面的 💡 推荐问题\n• 反馈给我们: GitHub Issues`,
    actions: [
      { label: '看推荐问题', event: 'reset' },
      { label: '看帮助', event: 'navigate', payload: '/help' }
    ],
    confidence: 0
  }
}

const ask = async (q) => {
  const text = (q || input.value).trim()
  if (!text) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollBottom()
  status.value = 'thinking'

  // ★ 优先从本页 qa 列表找答案 (本地快, 无需网络)
  const localAns = findAnswer(text)
  if (localAns && localAns.confidence >= 0.7) {
    setTimeout(() => {
      messages.value.push({ role: 'assistant', title: localAns.title, content: localAns.content, actions: localAns.actions })
      status.value = 'ready'
      scrollBottom()
    }, 300 + Math.random() * 400)
    return
  }
  // ★ 本地没匹配, 真调后端 agent (如果有 agentId)
  if (!agentId.value) {
    setTimeout(() => {
      messages.value.push({
        role: 'assistant',
        title: '💡 本地知识库没有匹配',
        content: `没能从本页知识库找到答案。你可以：
1. 检查问题描述是否清楚
2. 尝试不同的关键词
3. 点 [🔗 去 Agent 页] 选个智能体发起多轮对话
4. 用 bus 全局搜索: bus.emit('assistant:context', { type: 'pageStatus' })`,
        actions: [
          { label: '🔗 去 Agent 页', event: 'navigate', payload: '/agents' },
          { label: '🔍 重新提问', event: 'ask', payload: text }
        ]
      })
      status.value = 'ready'
      scrollBottom()
    }, 400)
    return
  }
  // ★ 调后端 /api/conversation/chat
  try {
    const { agentApi } = await import('@/api')
    const sessionId = `wf-${route.path.replace(/\//g, '_')}-${Date.now()}`
    const r = await agentApi.chat({
      agentId: agentId.value,
      sessionId,
      input: text,
      // 上下文: 让 agent 知道是哪个页面的问题
      context: { page: route.path, pageMeta: pageMeta.value.name }
    })
    if (r.code === 200) {
      const ans = r.data?.answer || r.data?.text || '收到'
      messages.value.push({
        role: 'assistant',
        title: `🪄 ${pageMeta.value.name || 'AI'} 助手`,
        content: ans,
        actions: []
      })
    } else {
      messages.value.push({ role: 'assistant', title: '⚠️ 后端错误', content: r.message || '请求失败', actions: [] })
    }
  } catch (e) {
    console.warn('[assistant] 后端 chat 失败:', e?.message)
    // 失败降级到本地答案
    messages.value.push({
      role: 'assistant',
      title: localAns.title || '💡 提示',
      content: (localAns.content || '暂时连不上后端智能体, 以下是本地建议:') + `\n\n(后端错误: ${e?.response?.data?.message || e?.message || '网络错误'})`,
      actions: localAns.actions || []
    })
  } finally {
    status.value = 'ready'
    scrollBottom()
  }
}

const onAction = (a) => {
  if (a.event === 'ask') ask(a.payload)
  else if (a.event === 'navigate') {
    router.push(a.payload)
    ElMessage.success('正在跳转: ' + a.payload)
  }
  else if (a.event === 'generate') {
    // 跳到 workflow 页 + 触发 AI 生成
    router.push('/workflow').then(() => {
      setTimeout(() => {
        bus.emit('workflow:ai-generate', { input: a.payload })
      }, 500)
    })
  }
  else if (a.event === 'diagnose') {
    // 跳到 workflow 页 + 触发诊断
    router.push('/workflow').then(() => {
      setTimeout(() => {
        bus.emit('workflow:diagnose', {})
      }, 500)
    })
  }
  else if (a.event === 'suggest-params') {
    // 双击某个节点, 跳过去取 AI 建议
    router.push('/workflow').then(() => {
      setTimeout(() => {
        bus.emit('workflow:suggest-params', a.payload || {})
      }, 500)
    })
  }
  else if (a.event === 'reset') {
    // 滚到顶部让用户看推荐问题
    if (msgEl.value) msgEl.value.scrollTop = 0
  }
  else emit('action', a)
}

// 监听: 画布异常时, 主动插入一条提示
watch(() => [effectiveContext.value?.cycleCount, effectiveContext.value?.valid, effectiveContext.value?.nodeCount], ([c, v, n], [oc, ov, on]) => {
  if (c > 0 && oc !== c) {
    messages.value.push({
      role: 'assistant',
      title: '⚠️ 画布有问题',
      content: `检测到 ${c} 个死循环, 流程无法运行. 修复: 找反向箭头删除, 保持 DAG 结构.`,
      actions: [
        { label: '怎么修复', event: 'ask', payload: '怎么修复循环?' },
        { label: '隐藏助手', event: 'minimize' }
      ]
    })
    hint.value = true
    scrollBottom()
  }
  // 流程从不合法变成合法
  if (ov === false && v === true && on > 0) {
    messages.value.push({
      role: 'assistant',
      title: '✅ 流程合法',
      content: '画布已通过校验, 可以运行了. 记得点 [▶ 运行] 测试一下.',
      actions: [{ label: '看运行步骤', event: 'ask', payload: '怎么运行流程?' }]
    })
    scrollBottom()
  }
})

onMounted(() => {
  // ★ 不再覆盖消息: localStorage 已经有对话历史, 保留
  // 只在 localStorage 为空 (首次访问) 才推欢迎语
  if (messages.value.length <= 1) {
    messages.value = [
      { role: 'assistant', title: `👋 欢迎来到「${pageMeta.value.name}」`, content: pageWelcome.value, actions: pageMeta.value.qa?.[0]?.actions || [] }
    ]
  }
  // 快捷键 (Ctrl+L 清空)
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
})
</script>

<style scoped>
.wfa-root {
  position: fixed;
  width: 360px;
  max-height: 600px;
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
  font-size: 20px;
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

.wfa-body { display: flex; flex-direction: column; max-height: 540px; }

.wfa-page {
  padding: 6px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}
.page-info { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.page-icon { font-size: 14px; }
.page-name { font-weight: 600; color: #1e293b; }
.page-desc { color: #64748b; }

.wfa-ctx {
  display: flex;
  gap: 4px;
  padding: 6px 12px;
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

.wfa-pagestatus {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 12px;
  background: #fefefe;
  border-bottom: 1px solid #f0f0f0;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  padding: 2px 6px;
  background: #e0e7ff;
  color: #4338ca;
  border-radius: 4px;
}
.status-tag.warn { background: #fef3c7; color: #b45309; }
.status-tag.danger { background: #fee2e2; color: #b91c1c; }
.status-tag.ok { background: #d1fae5; color: #047857; }

.wfa-msgs {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  min-height: 160px;
}
.wfa-msg { display: flex; }
.wfa-msg.user { justify-content: flex-end; }
.msg-bubble {
  max-width: 88%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.wfa-msg.user .msg-bubble { background: #6366f1; color: #fff; }
.wfa-msg.assistant .msg-bubble { background: #f1f5f9; color: #1e293b; }
.msg-title { font-weight: 600; margin-bottom: 4px; color: #6366f1; font-size: 12px; }
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
