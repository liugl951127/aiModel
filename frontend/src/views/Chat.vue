<template>
  <div class="chat-page">
    <!-- 左: 会话列表 -->
    <aside class="chat-side">
      <div class="side-head">
        <h3>💬 对话</h3>
        <el-button size="small" type="primary" @click="newChat"><el-icon><Plus /></el-icon></el-button>
      </div>
      <el-scrollbar style="flex: 1;">
        <div v-for="s in sessions" :key="s.id" class="session-item" :class="{ active: s.id === currentId }" @click="selectSession(s)">
          <el-icon><ChatDotRound /></el-icon>
          <div class="s-info">
            <div class="s-title">{{ s.title || '新对话' }}</div>
            <div class="s-time">{{ formatTime(s.updatedAt) }}</div>
          </div>
        </div>
        <el-empty v-if="!sessions.length" description="还没有对话" :image-size="60" />
      </el-scrollbar>
    </aside>

    <!-- 中: 对话区 -->
    <main class="chat-main">
      <header class="chat-head">
        <h3>{{ currentSession?.title || '智能对话' }}</h3>
        <div class="head-actions">
          <el-select v-model="model" size="default" style="width: 200px">
            <el-option v-for="m in models" :key="m.modelCode" :label="`${m.modelName} (${m.modelCode})`" :value="m.modelCode" />
          </el-select>
          <el-button @click="clearChat" type="danger" plain size="small">
            <el-icon><Delete /></el-icon> 清空
          </el-button>
        </div>
      </header>

      <div ref="scrollEl" class="chat-messages">
        <div v-if="!messages.length" class="chat-empty">
          <el-icon :size="48" color="#cbd5e1"><ChatLineRound /></el-icon>
          <h3>开始一次对话</h3>
          <p>试试问我: "介绍一下 RAG 检索增强的原理" 或者 "用 Python 写个快速排序"</p>
          <div class="quick-chips">
            <el-tag v-for="q in quickQuestions" :key="q" size="large" effect="plain" @click="messages.push({ role: 'user', content: q }); send()" style="cursor: pointer;">{{ q }}</el-tag>
          </div>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="msg-avatar">
            <el-avatar :size="32" :style="{ background: m.role === 'user' ? '#6366f1' : '#10b981' }">
              {{ m.role === 'user' ? '我' : 'AI' }}
            </el-avatar>
          </div>
          <div class="msg-body">
            <div class="msg-meta">{{ m.role === 'user' ? '你' : 'AI 助手' }} · {{ formatTime(m.ts) }}</div>
            <div class="msg-content">{{ m.content }}</div>
          </div>
        </div>
        <div v-if="loading" class="msg assistant">
          <div class="msg-avatar">
            <el-avatar :size="32" style="background: #10b981">AI</el-avatar>
          </div>
          <div class="msg-body">
            <div class="msg-meta">AI 助手 · 正在思考...</div>
            <div class="msg-content"><el-icon class="rotating"><Loading /></el-icon></div>
          </div>
        </div>
      </div>

      <footer class="chat-input">
        <el-input v-model="input" type="textarea" :rows="3" placeholder="输入消息, Ctrl+Enter 发送" @keydown.ctrl.enter="send" />
        <el-button type="primary" :loading="loading" @click="send" size="large" style="margin-top: 8px; width: 100%">
          <el-icon><Promotion /></el-icon> 发送
        </el-button>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, ChatDotRound, ChatLineRound, Delete, Promotion, Loading } from '@element-plus/icons-vue'
import { agentApi, modelApi, inferenceApi } from '@/api'

const props = defineProps({
  agentId: { type: String, default: null }
})

const sessions = ref([])
const currentId = ref(null)
const currentSession = ref(null)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const model = ref('minigpt')
const models = ref([])
const scrollEl = ref(null)

// ★ 持久化: sessions 存 localStorage, 跨刷新保留
const SESSIONS_KEY = computed(() => `chat_sessions_${props.agentId || 'default'}`)
const loadSessions = () => {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY.value)
    if (raw) {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) return arr
    }
  } catch (e) { /* 容错 */ }
  return []
}
sessions.value = loadSessions()
// 同步持久化 (debounce)
let sessTimer = null
watch(sessions, () => {
  clearTimeout(sessTimer)
  sessTimer = setTimeout(() => {
    try { localStorage.setItem(SESSIONS_KEY.value, JSON.stringify(sessions.value.slice(0, 50))) } catch (e) {}
  }, 500)
}, { deep: true })

const quickQuestions = [
  '你好, 介绍一下你自己',
  '什么是 RAG 检索增强?',
  'Python 快速排序怎么写?',
  'Spring Boot 启动流程'
]

const formatTime = (s) => {
  if (!s) return ''
  try { return new Date(s).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) } catch (e) { return '' }
}

const scrollToBottom = () => nextTick(() => { if (scrollEl.value) scrollEl.value.scrollTo({ top: 1e9, behavior: 'smooth' }) })

const newChat = () => {
  const id = 's' + Date.now()
  const s = { id, title: '新对话', updatedAt: Date.now() }
  sessions.value.unshift(s)
  currentId.value = id
  currentSession.value = s
  messages.value = []
}

const selectSession = (s) => {
  currentId.value = s.id
  currentSession.value = s
  messages.value = s.messages || []
  scrollToBottom()
}

const clearChat = () => {
  if (currentSession.value) {
    currentSession.value.messages = []
  }
  messages.value = []
  ElMessage.success('已清空')
}

// 旧的 localStorage 同步函数 — 已改用 watch 自动持久化, 保留空 stub 兼容 onMounted 调用
const saveSessions = () => { /* 已被 watch 替代 */ }

const loadModels = async () => {
  try {
    const r = await modelApi.list()
    if (r.code === 200) models.value = r.data || []
  } catch (e) {}
}

const send = async () => {
  const text = input.value.trim()
  if (!text) return
  if (!currentId.value) newChat()
  messages.value.push({ role: 'user', content: text, ts: Date.now() })
  input.value = ''
  if (currentSession.value) {
    currentSession.value.title = text.slice(0, 30) || currentSession.value.title
    currentSession.value.updatedAt = Date.now()
    currentSession.value.messages = messages.value
  }
  scrollToBottom()
  loading.value = true
  try {
    const r = await inferenceApi.chat({
      model: model.value,
      messages: messages.value.filter(m => m.role === 'user' || m.role === 'assistant').map(m => ({ role: m.role, content: m.content })),
      max_tokens: 1024,
      temperature: 0.7
    })
    const reply = r.data?.response || r.data?.text || r.data?.message || JSON.stringify(r.data)
    messages.value.push({ role: 'assistant', content: reply, ts: Date.now() })
    if (currentSession.value) {
      currentSession.value.messages = messages.value
      currentSession.value.updatedAt = Date.now()
    }
  } catch (e) {
    messages.value.push({ role: 'assistant', content: `❌ 错误: ${e.message}`, ts: Date.now() })
  } finally {
    loading.value = false
    scrollToBottom()
    saveSessions()
  }
}

onMounted(() => {
  loadSessions()
  loadModels()
  if (!sessions.value.length) newChat()
  else selectSession(sessions.value[0])
})
</script>

<style scoped>
.chat-page { display: flex; height: calc(100vh - 110px); margin: 8px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 10px; overflow: hidden; }
.chat-side { width: 240px; display: flex; flex-direction: column; border-right: 1px solid #f0f0f0; background: #fafafa; }
.side-head { display: flex; align-items: center; justify-content: space-between; padding: 12px; border-bottom: 1px solid #f0f0f0; }
.side-head h3 { margin: 0; font-size: 14px; }
.session-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; border-bottom: 1px solid #f5f5f5; }
.session-item:hover { background: #f0f0f0; }
.session-item.active { background: #eef2ff; border-left: 3px solid #6366f1; }
.s-info { flex: 1; min-width: 0; }
.s-title { font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.s-time { font-size: 10px; color: #94a3b8; }

.chat-main { flex: 1; display: flex; flex-direction: column; }
.chat-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid #f0f0f0; }
.chat-head h3 { margin: 0; font-size: 16px; }
.head-actions { display: flex; align-items: center; gap: 8px; }

.chat-messages { flex: 1; padding: 16px; overflow-y: auto; }
.chat-empty { text-align: center; padding: 40px 20px; color: #94a3b8; }
.chat-empty h3 { margin: 12px 0 4px; color: #475569; }
.chat-empty p { font-size: 12px; }
.quick-chips { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-top: 12px; }

.msg { display: flex; gap: 10px; padding: 8px 0; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg-meta { font-size: 10px; color: #94a3b8; margin-bottom: 2px; }
.msg-content { background: #f1f5f9; padding: 8px 12px; border-radius: 8px; max-width: 70%; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.msg.user .msg-content { background: #6366f1; color: #fff; }
.msg.assistant .msg-content { background: #ecfdf5; color: #064e3b; }

.chat-input { padding: 12px 16px; border-top: 1px solid #f0f0f0; }

.rotating { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>
