<template>
  <transition name="bar">
    <div v-if="messages.length" class="ticker-bar" @click="expanded = !expanded">
      <div class="ticker-icon" :class="{ pulse: !!latest }">
        <el-icon><BellFilled /></el-icon>
        <span v-if="unread" class="badge">{{ unread }}</span>
      </div>
      <div class="ticker-main" :class="{ expanded }">
        <div class="ticker-row" v-for="m in shown" :key="m.id">
          <span class="t-tag" :class="m.type">{{ tagOf(m.type) }}</span>
          <span class="t-text">{{ m.text }}</span>
          <span class="t-time">{{ m.timeAgo }}</span>
        </div>
        <div v-if="messages.length > 1" class="ticker-more">
          {{ expanded ? '收起' : `还有 ${messages.length - 1} 条…` }}
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { BellFilled } from '@element-plus/icons-vue'

/**
 * 实时活动条：右下角浮动小条，订阅全局 SSE 活动流，展示最新的训练任务 / 智能体
 * 调用 / 知识库更新 / 工作流执行等事件。点击展开/收起。
 *
 * <p>事件源 {@code GET /api/activity/stream} 是后端预留的统一 SSE 端点（暂未实现，
 * 现在 fallback 到本地 EventBus 推送 — 各页面 emit 事件到这里）。</p>
 */
const messages = ref([])
const expanded = ref(false)
let timer = null

const tagOf = (t) => ({ train: '训练', agent: '智能体', kb: '知识库', wf: '工作流', sys: '系统' })[t] || '事件'
const shown = computed(() => expanded.value ? messages.value : messages.value.slice(0, 1))
const unread = computed(() => Math.min(messages.value.length, 9))
const latest = computed(() => messages.value[0])

// 时间相对显示
const refresh = () => {
  const now = Date.now()
  for (const m of messages.value) {
    const diff = Math.max(0, now - m.ts)
    if (diff < 60_000) m.timeAgo = Math.floor(diff / 1000) + 's 前'
    else if (diff < 3_600_000) m.timeAgo = Math.floor(diff / 60_000) + 'm 前'
    else m.timeAgo = Math.floor(diff / 3_600_000) + 'h 前'
  }
}

const push = (m) => {
  messages.value.unshift({
    id: m.id || Math.random().toString(36).slice(2),
    type: m.type || 'sys',
    text: m.text || JSON.stringify(m),
    ts: m.ts || Date.now(),
    timeAgo: '刚刚'
  })
  if (messages.value.length > 30) messages.value.length = 30
  refresh()
}

// 暴露给外部手动 push
window.__ticker = window.__ticker || push

onMounted(() => {
  // 试连 SSE（如果后端实现了）
  try {
    if (typeof EventSource !== 'undefined') {
      const es = new EventSource('/api/activity/stream')
      es.addEventListener('message', (e) => {
        try { push(JSON.parse(e.data)) } catch { push({ text: e.data }) }
      })
      es.onerror = () => es.close()
    }
  } catch { /* ignore */ }
  timer = setInterval(refresh, 5000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.ticker-bar {
  position: fixed; right: 18px; bottom: 18px; z-index: 100;
  display: flex; align-items: flex-start; gap: 10px;
  max-width: 380px; padding: 10px 12px;
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(12px);
  border-radius: 14px;
  box-shadow: 0 10px 30px -8px rgba(0, 0, 0, 0.2), 0 0 0 1px rgba(99, 102, 241, 0.1);
  cursor: pointer; user-select: none;
  transition: all 0.22s;
}
.ticker-bar:hover { transform: translateY(-2px); box-shadow: 0 14px 40px -8px rgba(99, 102, 241, 0.3); }
.ticker-icon {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  font-size: 18px; position: relative;
}
.ticker-icon.pulse::before {
  content: ''; position: absolute; inset: -2px; border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  z-index: -1; opacity: 0.5; animation: ringPulse 1.5s ease-out infinite;
}
@keyframes ringPulse { 0% { transform: scale(1); opacity: 0.5; } 100% { transform: scale(1.4); opacity: 0; } }
.badge {
  position: absolute; top: -4px; right: -4px;
  background: #ef4444; color: #fff; font-size: 10px; font-weight: 700;
  width: 18px; height: 18px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #fff;
}
.ticker-main { flex: 1; min-width: 0; }
.ticker-main.expanded { max-height: 320px; overflow-y: auto; }
.ticker-row {
  display: flex; align-items: center; gap: 6px; padding: 3px 0;
  font-size: 12px; color: #334155;
}
.t-tag {
  font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; flex-shrink: 0;
}
.t-tag.train { background: #dbeafe; color: #1d4ed8; }
.t-tag.agent { background: #ede9fe; color: #6d28d9; }
.t-tag.kb { background: #d1fae5; color: #047857; }
.t-tag.wf { background: #fed7aa; color: #c2410c; }
.t-tag.sys { background: #e5e7eb; color: #4b5563; }
.t-text { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.t-time { font-size: 10px; color: #94a3b8; flex-shrink: 0; }
.ticker-more { font-size: 10px; color: #6366f1; text-align: right; padding-top: 2px; }

.bar-enter-active, .bar-leave-active { transition: all 0.3s ease; }
.bar-enter-from, .bar-leave-to { opacity: 0; transform: translateX(40px); }
</style>
