<template>
  <transition name="bar">
    <aside v-if="visible" class="ticker-drawer">
      <header class="td-head">
        <div class="td-title">
          <span class="td-dot" :class="{ active: messages.length }"></span>
          <strong>实时活动</strong>
          <el-tag size="small" type="success" v-if="messages.length">{{ messages.length }}</el-tag>
        </div>
        <div class="td-actions">
          <el-tooltip content="清空" placement="top">
            <el-button underline="never" text size="small" @click="messages = []">
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button underline="never" text size="small" @click="visible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>

      <!-- 过滤栏 -->
      <div class="td-filter">
        <el-radio-group v-model="filterType" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="train">训练</el-radio-button>
          <el-radio-button value="agent">智能体</el-radio-button>
          <el-radio-button value="kb">知识库</el-radio-button>
          <el-radio-button value="wf">工作流</el-radio-button>
          <el-radio-button value="sys">系统</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 消息列表 -->
      <div class="td-body" v-if="filtered.length">
        <article
          v-for="m in filtered"
          :key="m.id"
          class="td-item"
          :class="m.type"
        >
          <div class="td-ico">
            <el-icon :size="14"><component :is="iconOf(m.type)" /></el-icon>
          </div>
          <div class="td-meta">
            <div class="td-text">
              <span class="td-tag" :class="m.type">{{ tagOf(m.type) }}</span>
              {{ m.text }}
            </div>
            <div class="td-sub">
              <span v-if="m.actor">@{{ m.actor }}</span>
              <span v-if="m.actor" class="dot">·</span>
              <span>{{ m.timeAgo }}</span>
              <span v-if="m.action" class="dot">·</span>
              <el-link v-if="m.action" type="primary" underline="never" size="small" @click="m.action && m.action.handler && m.action.handler()">
                {{ m.action.label }}
              </el-link>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="td-empty">
        <el-empty description="暂无活动" :image-size="80" />
      </div>

      <footer class="td-foot">
        <el-button underline="never" text size="small" @click="clearAll">
          <el-icon><Refresh /></el-icon>
          重新拉取
        </el-button>
        <span class="muted">最近 100 条</span>
      </footer>
    </aside>
  </transition>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  BellFilled, VideoPlay, UserFilled, Reading, Connection, Setting,
  Delete, Close, Refresh
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible'])

const visible = computed({
  get: () => props.visible,
  set: v => emit('update:visible', v)
})

const bus = useGlobalBus()
const messages = ref([])
const filterType = ref('all')

const tagOf = (t) => ({ train: '训练', agent: '智能体', kb: '知识库', wf: '工作流', sys: '系统' })[t] || '事件'
const iconOf = (t) => ({ train: VideoPlay, agent: UserFilled, kb: Reading, wf: Connection, sys: Setting })[t] || BellFilled

const filtered = computed(() =>
  filterType.value === 'all' ? messages.value : messages.value.filter(m => m.type === filterType.value)
)

const push = (m) => {
  messages.value.unshift({
    id: m.id || Math.random().toString(36).slice(2),
    type: m.type || 'sys',
    text: m.text || JSON.stringify(m),
    actor: m.actor,
    action: m.action,
    ts: m.ts || Date.now(),
    timeAgo: '刚刚'
  })
  if (messages.value.length > 100) messages.value.length = 100
  refresh()
  // 通知顶栏
  bus.emit('live:event', m)
}

const refresh = () => {
  const now = Date.now()
  for (const m of messages.value) {
    const diff = Math.max(0, now - m.ts)
    if (diff < 60_000) m.timeAgo = Math.floor(diff / 1000) + 's 前'
    else if (diff < 3_600_000) m.timeAgo = Math.floor(diff / 60_000) + 'm 前'
    else m.timeAgo = Math.floor(diff / 3_600_000) + 'h 前'
  }
}

let timer = null
let _off1, _off2, _off3, _off4, _off5

const clearAll = () => {
  messages.value = []
  // 重新拉取活动
  push({ type: 'sys', text: '活动流已重置' })
}

onMounted(() => {
  // 订阅所有 bus 事件
  _off1 = bus.on('train:event', (e) => push({ type: 'train', text: e.text, actor: e.actor, action: e.action }))
  _off2 = bus.on('agent:event', (e) => push({ type: 'agent', text: e.text, actor: e.actor, action: e.action }))
  _off3 = bus.on('kb:event', (e) => push({ type: 'kb', text: e.text, actor: e.actor, action: e.action }))
  _off4 = bus.on('wf:event', (e) => push({ type: 'wf', text: e.text, actor: e.actor, action: e.action }))
  _off5 = bus.on('sys:event', (e) => push({ type: 'sys', text: e.text, actor: e.actor, action: e.action }))

  // 加载后端历史事件 (填充初始列表)
  try {
    import('@/api').then(({ activityApi }) => {
      activityApi.recent().then(r => {
        if (r.data?.code === 200 && Array.isArray(r.data.data)) {
          r.data.data.forEach(e => push(e))
        }
      }).catch(() => { /* 后端不可达时不报错 */ })
    })
  } catch { /* ignore */ }

  // 试连后端 SSE (预留)
  try {
    if (typeof EventSource !== 'undefined') {
      const es = new EventSource('/api/activity/stream', { withCredentials: true })
      es.addEventListener('message', (e) => {
        try { push(JSON.parse(e.data)) } catch { push({ type: 'sys', text: e.data }) }
      })
      es.onerror = () => es.close()
    }
  } catch { /* ignore */ }

  timer = setInterval(refresh, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  _off1 && _off1(); _off2 && _off2(); _off3 && _off3(); _off4 && _off4(); _off5 && _off5()
})
</script>

<style scoped>
.ticker-drawer {
  position: fixed; right: 0; top: 56px; bottom: 0; width: 360px;
  background: var(--bg-top, #fff);
  border-left: 1px solid var(--border, #e5e7eb);
  box-shadow: -10px 0 30px -8px rgba(0, 0, 0, 0.15);
  z-index: 50;
  display: flex; flex-direction: column;
}

.td-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border, #e5e7eb);
  flex-shrink: 0;
}
.td-title { display: flex; align-items: center; gap: 8px; }
.td-title strong { font-size: 15px; color: #1e293b; }
.td-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #cbd5e1;
}
.td-dot.active { background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); } 50% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.1); } }
.td-actions { display: flex; gap: 2px; }

.td-filter { padding: 10px 16px; border-bottom: 1px solid var(--border, #e5e7eb); flex-shrink: 0; }
.td-filter :deep(.el-radio-button__inner) { padding: 5px 8px; font-size: 11px; }

.td-body { flex: 1; overflow-y: auto; padding: 8px 0; }
.td-empty { flex: 1; display: flex; align-items: center; justify-content: center; }

.td-item {
  display: flex; gap: 10px; padding: 10px 16px;
  border-left: 3px solid transparent;
  transition: background 0.12s;
  cursor: default;
}
.td-item:hover { background: rgba(99, 102, 241, 0.04); }
.td-item.train { border-left-color: #3b82f6; }
.td-item.agent { border-left-color: #8b5cf6; }
.td-item.kb { border-left-color: #10b981; }
.td-item.wf { border-left-color: #f97316; }
.td-item.sys { border-left-color: #64748b; }

.td-ico {
  width: 26px; height: 26px; border-radius: 7px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
}
.td-item.train .td-ico { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
.td-item.agent .td-ico { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.td-item.kb .td-ico { background: linear-gradient(135deg, #10b981, #047857); }
.td-item.wf .td-ico { background: linear-gradient(135deg, #f97316, #c2410c); }
.td-item.sys .td-ico { background: linear-gradient(135deg, #64748b, #334155); }

.td-meta { flex: 1; min-width: 0; }
.td-text { font-size: 12px; color: #1e293b; line-height: 1.5; word-break: break-word; }
.td-tag {
  font-size: 10px; padding: 0 5px; border-radius: 3px; font-weight: 600;
  margin-right: 4px; vertical-align: 1px;
}
.td-tag.train { background: #dbeafe; color: #1d4ed8; }
.td-tag.agent { background: #ede9fe; color: #6d28d9; }
.td-tag.kb { background: #d1fae5; color: #047857; }
.td-tag.wf { background: #fed7aa; color: #c2410c; }
.td-tag.sys { background: #e5e7eb; color: #4b5563; }

.td-sub { font-size: 10px; color: #94a3b8; margin-top: 2px; display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
.td-sub .dot { color: #cbd5e1; }

.td-foot {
  padding: 8px 16px;
  border-top: 1px solid var(--border, #e5e7eb);
  display: flex; justify-content: space-between; align-items: center;
  font-size: 11px; color: #94a3b8;
  flex-shrink: 0;
}
.muted { color: #94a3b8; }

.bar-enter-active, .bar-leave-active { transition: all 0.25s ease; }
.bar-enter-from, .bar-leave-to { opacity: 0; transform: translateX(40px); }
</style>
