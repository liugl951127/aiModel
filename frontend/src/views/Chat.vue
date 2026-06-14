<template>
  <div class="page-container chat-wrap">
    <el-card class="chat-card">
      <template #header>
        <div class="card-header">
          <b>{{ agentName }}</b>
          <el-tag>session: {{ sessionId.slice(0, 8) }}...</el-tag>
        </div>
      </template>
      <div ref="scrollEl" class="chat-body">
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="bubble" v-if="m.role !== 'tool'">
            <div class="role">{{ m.role }}</div>
            <div class="content">{{ m.content }}</div>
          </div>
          <div class="tool" v-else>
            <div class="role">tool: {{ m.toolName }} <span v-if="m.step">[step {{ m.step }}]</span></div>
            <div class="content">{{ m.content }}</div>
          </div>
        </div>
        <div v-if="loading" class="msg assistant">
          <div class="bubble"><div class="content">思考中...</div></div>
        </div>
      </div>
      <div class="chat-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { agentApi, userApi } from '@/api'

const route = useRoute()
const agentId = Number(route.params.agentId)
const agentName = ref('智能体')
const sessionId = ref(crypto.randomUUID().replace(/-/g, ''))
const messages = ref([])
const input = ref('')
const loading = ref(false)
const scrollEl = ref(null)

const send = async () => {
  if (!input.value.trim() || loading.value) return
  const text = input.value
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  try {
    const resp = await agentApi.chat({ agentId, sessionId: sessionId.value, input: text })
    const data = resp.data
    if (data?.trace) {
      for (const m of data.trace) {
        if (m.role && m.content) messages.value.push(m)
      }
    }
    messages.value.push({ role: 'assistant', content: data?.answer || '(无回复)' })
    await nextTick(); scrollToBottom()
  } catch (e) {
    ElMessage.error('对话失败')
  } finally {
    loading.value = false
  }
}

const scrollToBottom = () => {
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}

onMounted(async () => {
  try {
    const a = await agentApi.get(agentId)
    agentName.value = a.data.agentName
  } catch (e) { /* offline */ }
})
</script>

<style scoped>
.chat-wrap { height: calc(100vh - 90px); display: flex; }
.chat-card { flex: 1; display: flex; flex-direction: column; }
.chat-body { flex: 1; overflow-y: auto; padding: 12px; background: #f9fafc; border-radius: 6px; }
.msg { display: flex; margin-bottom: 10px; }
.msg.user { justify-content: flex-end; }
.msg .bubble { max-width: 70%; padding: 10px 14px; background: #fff; border-radius: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.06); }
.msg.user .bubble { background: var(--primary); color: #fff; }
.msg .role { font-size: 11px; color: #909399; margin-bottom: 4px; }
.msg.user .role { color: rgba(255,255,255,0.85); }
.msg .content { white-space: pre-wrap; line-height: 1.5; }
.msg.assistant .bubble { background: #fff; border: 1px solid #ebeef5; }
.msg.tool { padding: 6px 12px; }
.msg.tool .tool { background: #fdf6ec; border-left: 3px solid #e6a23c; padding: 6px 10px; border-radius: 4px; max-width: 80%; }
.chat-input { display: flex; gap: 8px; margin-top: 12px; }
.chat-input .el-textarea { flex: 1; }
</style>
