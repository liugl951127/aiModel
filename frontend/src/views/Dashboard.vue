<template>
  <div class="dash">
    <!-- 顶部欢迎条 -->
    <header class="hello">
      <div class="hello-text">
        <h1>你好，{{ nickname }} <span class="wave">👋</span></h1>
        <p class="hello-sub">
          <el-tag size="small" effect="plain" type="primary">
            <el-icon><OfficeBuilding /></el-icon>
            {{ tenantName || '默认租户' }}
          </el-tag>
          <el-tag v-if="department" size="small" effect="plain">
            <el-icon><User /></el-icon>
            {{ department }}
          </el-tag>
          <el-tag size="small" effect="plain" type="info">
            <el-icon><Clock /></el-icon>
            {{ now }}
          </el-tag>
        </p>
      </div>
      <div class="hello-cta">
        <el-button type="primary" size="large" @click="$router.push('/workflow')">
          <el-icon><Connection /></el-icon>
          立即编排
        </el-button>
        <el-button size="large" @click="$router.push('/chat')">
          <el-icon><ChatDotRound /></el-icon>
          智能对话
        </el-button>
      </div>
    </header>

    <!-- 4 张统计卡（紧凑 + 点击跳转） -->
    <section class="stats">
      <div
        v-for="s in stats"
        :key="s.key"
        class="stat"
        :class="s.tone"
        @click="s.path && $router.push(s.path)"
      >
        <div class="stat-main">
          <div class="stat-ico">
            <el-icon :size="20"><component :is="s.icon" /></el-icon>
          </div>
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
        <svg class="stat-spark" viewBox="0 0 60 20" preserveAspectRatio="none">
          <polyline :points="s.spark" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.7" />
        </svg>
      </div>
    </section>

    <!-- 主体三列：能力图谱 / 实时活动 / 系统状态 -->
    <section class="grid-3">
      <!-- ===== 左：能力图谱 (节点化) ===== -->
      <div class="card">
        <div class="card-head">
          <h3>🚀 平台能力</h3>
          <el-link type="primary" :underline="false" @click="$router.push('/workflow')">编排工作流 →</el-link>
        </div>
        <div class="cap-graph">
          <div v-for="(c, i) in capList" :key="c.name" class="cap-node" :style="`--c1: ${c.c1}; --c2: ${c.c2}`" @click="onCapClick(c)">
            <div class="cap-ico">{{ c.icon }}</div>
            <div class="cap-name">{{ c.name }}</div>
            <div class="cap-count">{{ c.count }} 项</div>
          </div>
          <svg class="cap-wires" viewBox="0 0 100 100" preserveAspectRatio="none">
            <line x1="0" y1="50" x2="100" y2="50" stroke="rgba(99, 102, 241, 0.3)" stroke-width="0.4" stroke-dasharray="2 2" />
          </svg>
        </div>
      </div>

      <!-- ===== 中：实时活动流（内嵌） ===== -->
      <div class="card">
        <div class="card-head">
          <h3>📡 实时活动</h3>
          <div>
            <span class="live-dot" :class="{ active: liveCount > 0 }"></span>
            <span class="muted small">最近 {{ recentEvents.length }} 条</span>
          </div>
        </div>
        <div class="evt-list" v-if="recentEvents.length">
          <article v-for="e in recentEvents" :key="e.id" class="evt" :class="e.type">
            <span class="evt-tag">{{ tagOf(e.type) }}</span>
            <span class="evt-text">{{ e.text }}</span>
            <span class="evt-time">{{ e.timeAgo }}</span>
          </article>
        </div>
        <div class="evt-empty" v-else>
          <p class="muted small">暂无活动，试试去
            <el-link type="primary" :underline="false" @click="$router.push('/train')">训练任务</el-link>
            或
            <el-link type="primary" :underline="false" @click="$router.push('/chat')">智能对话</el-link>
          </p>
        </div>
      </div>

      <!-- ===== 右：系统状态 ===== -->
      <div class="card">
        <div class="card-head">
          <h3>💚 系统状态</h3>
          <span class="muted small">{{ sysCheckTime }}</span>
        </div>
        <div class="sys-list">
          <div v-for="s in sysList" :key="s.name" class="sys-row">
            <div class="sys-name">
              <span class="sys-dot" :class="s.status"></span>
              {{ s.name }}
            </div>
            <div class="sys-meta">
              <span class="muted small">{{ s.detail }}</span>
            </div>
          </div>
        </div>
        <div class="sys-tip">
          <el-link type="primary" :underline="false" @click="checkAll">重新检查</el-link>
        </div>
      </div>
    </section>

    <!-- 底部：4 个快捷入口横排 -->
    <section class="quick-row">
      <div v-for="q in quickActions" :key="q.path" class="quick" @click="$router.push(q.path)" :style="`--c1: ${q.c1}; --c2: ${q.c2}`">
        <div class="q-ico"><el-icon :size="22"><component :is="q.icon" /></el-icon></div>
        <div class="q-name">{{ q.name }}</div>
        <div class="q-desc">{{ q.desc }}</div>
        <el-icon class="q-arrow"><ArrowRight /></el-icon>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  Cpu, Files, VideoPlay, UserFilled, Tools, Reading, ChatDotRound, Setting,
  OfficeBuilding, User, Clock, Connection, ArrowRight
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'
import { modelApi, datasetApi, agentApi, knowledgeApi } from '@/api'

const router = useRouter()
const bus = useGlobalBus()

// ============== 用户信息 ==============
const username = localStorage.getItem('username') || ''
const nickname = localStorage.getItem('nickname') || username
const tenantName = localStorage.getItem('tenant_name') || ''
const department = localStorage.getItem('department') || ''

// ============== 时钟 ==============
const now = ref(new Date().toLocaleString('zh-CN'))
let clockTimer = null

// ============== 4 张统计卡（点击跳转） ==============
const stats = ref([
  { key: 'models',  label: '大模型',   value: '—', icon: Cpu,        tone: 'blue',   path: '/models',    spark: '0,15 10,12 20,14 30,8 40,11 50,6 60,4' },
  { key: 'agents',  label: '智能体',   value: '—', icon: UserFilled, tone: 'purple', path: '/agents',    spark: '0,18 10,15 20,12 30,14 40,9 50,11 60,7' },
  { key: 'kb',      label: '知识库',   value: '—', icon: Reading,    tone: 'green',  path: '/knowledge', spark: '0,16 10,14 20,10 30,12 40,8 50,5 60,3' },
  { key: 'train',   label: '训练任务', value: '—', icon: VideoPlay,  tone: 'orange', path: '/train',     spark: '0,17 10,13 20,11 30,8 40,9 50,4 60,2' }
])
const loadStats = async () => {
  // 并发拉 4 个数字
  const tries = [
    { k: 'models', fn: () => modelApi.list().then(r => r.data?.data?.length || r.data?.length || 0) },
    { k: 'agents', fn: () => agentApi.list().then(r => r.data?.data?.length || r.data?.length || 0) },
    { k: 'kb',     fn: () => knowledgeApi.list().then(r => r.data?.data?.length || r.data?.length || 0) },
    { k: 'train',  fn: () => datasetApi.list().then(r => r.data?.data?.length || r.data?.length || 0) }
  ]
  for (const t of tries) {
    try {
      const v = await t.fn()
      const s = stats.value.find(x => x.key === t.k)
      if (s) s.value = v
    } catch (e) { /* ignore, keep '—' */ }
  }
  bus.emit('sys:event', { text: `统计已刷新: 模型 ${stats.value[0].value} · 智能体 ${stats.value[1].value} · 知识库 ${stats.value[2].value}` })
}

// ============== 实时活动（订阅 bus + 本地缓存） ==============
const recentEvents = ref([])
const liveCount = ref(0)
const tagOf = (t) => ({ train: '训练', agent: '智能体', kb: '知识库', wf: '工作流', sys: '系统' })[t] || '事件'

let _off1, _off2, _off3, _off4, _off5
const onEvent = (e) => {
  recentEvents.value.unshift({
    id: e.id || Math.random().toString(36).slice(2),
    type: e.type || 'sys',
    text: e.text,
    ts: e.ts || Date.now(),
    timeAgo: '刚刚'
  })
  if (recentEvents.value.length > 8) recentEvents.value.length = 8
  liveCount.value++
  setTimeout(() => { if (liveCount.value > 0) liveCount.value-- }, 3000)
  // 时间刷新
  setTimeout(() => {
    const now = Date.now()
    for (const m of recentEvents.value) {
      const diff = Math.max(0, now - m.ts)
      if (diff < 60_000) m.timeAgo = Math.floor(diff / 1000) + 's 前'
      else if (diff < 3_600_000) m.timeAgo = Math.floor(diff / 60_000) + 'm 前'
      else m.timeAgo = Math.floor(diff / 3_600_000) + 'h 前'
    }
  }, 100)
}

// ============== 系统状态 ==============
const sysList = ref([
  { name: '网关',  status: 'ok',  detail: '9000 · 12 路由' },
  { name: '认证',  status: 'ok',  detail: '9010 · JWT' },
  { name: '推理',  status: 'ok',  detail: '9007 · ONNX' },
  { name: '训练',  status: 'ok',  detail: '9011 · DJL' },
  { name: '知识库', status: 'ok',  detail: '9005 · ES 8' },
  { name: 'Nacos', status: 'warn', detail: '8848 · 可选' }
])
const sysCheckTime = ref('')
const checkAll = async () => {
  sysCheckTime.value = new Date().toLocaleTimeString('zh-CN')
  for (const s of sysList.value) {
    try {
      const resp = await fetch(`/${s.name === 'Nacos' ? 'nacos' : 'api/' + (s.name === '网关' ? 'auth' : s.name === '认证' ? 'auth' : s.name === '推理' ? 'inference' : s.name === '训练' ? 'trainer' : s.name === '知识库' ? 'knowledge' : 'unknown')}/health`, { method: 'GET' })
      s.status = resp.ok ? 'ok' : 'down'
    } catch { s.status = 'down' }
  }
  bus.emit('sys:event', { text: '系统状态已重新检查' })
}

// ============== 能力图谱 ==============
const capList = [
  { name: '模型', icon: '🧠', count: 4, c1: '#6366f1', c2: '#8b5cf6', path: '/models' },
  { name: '数据集', icon: '📚', count: 6, c1: '#10b981', c2: '#06b6d4', path: '/datasets' },
  { name: '训练', icon: '⚡', count: 5, c1: '#f59e0b', c2: '#ef4444', path: '/train' },
  { name: '智能体', icon: '🤖', count: 3, c1: '#8b5cf6', c2: '#ec4899', path: '/agents' },
  { name: '工具', icon: '🛠️', count: 4, c1: '#06b6d4', c2: '#3b82f6', path: '/tools' },
  { name: '知识库', icon: '📖', count: 5, c1: '#10b981', c2: '#84cc16', path: '/knowledge' }
]
const onCapClick = (c) => router.push(c.path)

// ============== 快捷入口 ==============
const quickActions = [
  { name: '工作流编排', desc: '拖拽编排业务流', icon: Connection,     c1: '#6366f1', c2: '#8b5cf6', path: '/workflow' },
  { name: '智能体管理', desc: 'ReAct + 工具调用', icon: UserFilled,  c1: '#ec4899', c2: '#f43f5e', path: '/agents' },
  { name: '知识库',    desc: 'RAG 检索增强',     icon: Reading,     c1: '#10b981', c2: '#06b6d4', path: '/knowledge' },
  { name: '推理测试',  desc: 'ONNX 推理',        icon: ChatDotRound, c1: '#f59e0b', c2: '#ef4444', path: '/inference' }
]

onMounted(() => {
  clockTimer = setInterval(() => { now.value = new Date().toLocaleString('zh-CN') }, 1000)
  loadStats()
  checkAll()
  // 订阅全局事件
  _off1 = bus.on('train:event', onEvent)
  _off2 = bus.on('agent:event', onEvent)
  _off3 = bus.on('kb:event', onEvent)
  _off4 = bus.on('wf:event', onEvent)
  _off5 = bus.on('sys:event', onEvent)
})
onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  _off1 && _off1(); _off2 && _off2(); _off3 && _off3(); _off4 && _off4(); _off5 && _off5()
})
</script>

<style scoped>
.dash { display: flex; flex-direction: column; gap: 14px; }

/* ============== Hello ============== */
.hello {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18px 22px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  color: #fff; border-radius: 16px;
  position: relative; overflow: hidden;
}
.hello::before {
  content: ''; position: absolute; right: -40px; top: -40px;
  width: 200px; height: 200px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}
.hello::after {
  content: ''; position: absolute; right: 60px; bottom: -60px;
  width: 160px; height: 160px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}
.hello-text h1 { font-size: 22px; margin: 0 0 6px; font-weight: 700; }
.wave { display: inline-block; animation: wave 1.5s ease-in-out infinite; }
@keyframes wave { 0%, 100% { transform: rotate(0); } 50% { transform: rotate(-20deg); } }
.hello-sub { margin: 0; display: flex; gap: 8px; flex-wrap: wrap; }
.hello-sub .el-tag { background: rgba(255, 255, 255, 0.15) !important; border-color: rgba(255, 255, 255, 0.2) !important; color: #fff !important; }
.hello-sub .el-icon { margin-right: 3px; }
.hello-cta { display: flex; gap: 8px; z-index: 2; }
.hello-cta .el-button { font-weight: 600; }
.hello-cta .el-button:first-child { background: #fff; color: #6366f1; border: none; }
.hello-cta .el-button:first-child:hover { background: #f3f4f6; }
.hello-cta .el-button:not(:first-child) { background: rgba(255, 255, 255, 0.2); color: #fff; border: 1px solid rgba(255, 255, 255, 0.3); }

/* ============== 4 张统计卡 ============== */
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat {
  position: relative; padding: 14px 16px;
  border-radius: 14px; color: #fff;
  cursor: pointer; transition: all 0.2s;
  overflow: hidden;
  display: flex; align-items: center; gap: 12px;
}
.stat:hover { transform: translateY(-2px); box-shadow: 0 12px 30px -8px rgba(0, 0, 0, 0.3); }
.stat.blue   { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
.stat.purple { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.stat.green  { background: linear-gradient(135deg, #10b981, #047857); }
.stat.orange { background: linear-gradient(135deg, #f59e0b, #ea580c); }
.stat-main { flex: 1; min-width: 0; z-index: 2; }
.stat-ico { width: 36px; height: 36px; border-radius: 10px; background: rgba(255, 255, 255, 0.2); display: flex; align-items: center; justify-content: center; margin-bottom: 6px; }
.stat-num { font-size: 24px; font-weight: 700; line-height: 1; }
.stat-label { font-size: 12px; opacity: 0.9; margin-top: 2px; }
.stat-spark { position: absolute; right: 0; bottom: 0; width: 60%; height: 30px; opacity: 0.6; z-index: 1; }

/* ============== 3 列 ============== */
.grid-3 { display: grid; grid-template-columns: 1fr 1.2fr 1fr; gap: 12px; }
.card { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 14px; padding: 14px 16px; min-height: 280px; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.card-head h3 { font-size: 14px; margin: 0; color: #1e293b; font-weight: 700; }
.live-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; margin-right: 4px; }
.live-dot.active { background: #10b981; animation: pulse 1.5s infinite; }
.muted { color: #94a3b8; }
.small { font-size: 11px; }

/* ===== 能力图谱 ===== */
.cap-graph {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
  position: relative;
}
.cap-node {
  padding: 12px 10px; border-radius: 12px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  color: #fff; text-align: center;
  cursor: pointer; transition: all 0.2s;
  position: relative; z-index: 2;
}
.cap-node:hover { transform: translateY(-3px) scale(1.04); box-shadow: 0 10px 24px -6px rgba(0, 0, 0, 0.3); }
.cap-ico { font-size: 24px; margin-bottom: 4px; }
.cap-name { font-size: 12px; font-weight: 700; }
.cap-count { font-size: 10px; opacity: 0.85; }
.cap-wires { position: absolute; inset: 0; pointer-events: none; z-index: 1; opacity: 0.3; }

/* ===== 实时活动 ===== */
.evt-list { display: flex; flex-direction: column; gap: 6px; max-height: 240px; overflow-y: auto; }
.evt { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px; font-size: 12px; background: #f8fafc; }
.evt-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; flex-shrink: 0; background: #e0e7ff; color: #4f46e5; }
.evt-text { flex: 1; min-width: 0; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.evt-time { font-size: 10px; color: #94a3b8; flex-shrink: 0; }
.evt-empty { padding: 20px; text-align: center; }

/* ===== 系统状态 ===== */
.sys-list { display: flex; flex-direction: column; gap: 4px; }
.sys-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-radius: 8px; background: #f8fafc; }
.sys-name { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #1e293b; }
.sys-dot { width: 8px; height: 8px; border-radius: 50%; }
.sys-dot.ok   { background: #10b981; }
.sys-dot.warn { background: #f59e0b; }
.sys-dot.down { background: #ef4444; }
.sys-meta { font-size: 10px; }
.sys-tip { margin-top: 10px; text-align: right; }

/* ============== 快捷入口 ============== */
.quick-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.quick {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-radius: 14px;
  background: var(--bg-top, #fff);
  border: 1px solid var(--border, #e5e7eb);
  cursor: pointer; transition: all 0.2s;
  position: relative; overflow: hidden;
}
.quick::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0;
  width: 4px; background: linear-gradient(180deg, var(--c1), var(--c2));
}
.quick:hover { border-color: var(--c1); transform: translateY(-2px); box-shadow: 0 8px 20px -6px rgba(0, 0, 0, 0.15); }
.q-ico {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  color: #fff; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.q-meta { flex: 1; min-width: 0; }
.q-name { font-size: 14px; font-weight: 700; color: #1e293b; }
.q-desc { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.q-arrow { color: #94a3b8; transition: all 0.2s; }
.quick:hover .q-arrow { color: var(--c1); transform: translateX(4px); }

@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); } 50% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.1); } }

@media (max-width: 1200px) {
  .stats, .quick-row { grid-template-columns: repeat(2, 1fr); }
  .grid-3 { grid-template-columns: 1fr; }
}
</style>
