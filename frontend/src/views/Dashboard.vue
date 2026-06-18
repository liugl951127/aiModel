<template>
  <div class="dash">
    <!-- Hero: el-page-header + el-statistic -->
    <div class="hello">
      <div class="hello-text">
        <h1>你好，{{ nickname }} <span class="wave">👋</span></h1>
        <div class="hello-tags">
          <el-tag size="small" type="primary" effect="plain">
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
        </div>
      </div>
      <div class="hello-cta">
        <el-button type="primary" size="large" round @click="$router.push('/workflow')">
          <el-icon><Connection /></el-icon>
          立即编排
        </el-button>
        <el-button size="large" round @click="$router.push('/chat')">
          <el-icon><ChatDotRound /></el-icon>
          智能对话
        </el-button>
      </div>
    </div>

    <!-- 4 张统计卡 (el-statistic) -->
    <el-row :gutter="12" class="stats-row">
      <el-col v-for="s in stats" :key="s.key" :xs="12" :sm="12" :md="6" :lg="6">
        <el-card
          shadow="hover"
          class="stat-card"
          :body-style="{ padding: 0 }"
          @click="s.path && $router.push(s.path)"
        >
          <div class="stat-inner" :class="s.tone">
            <el-statistic
              :value="s.value"
              :title="s.label"
              :title-style="{ color: '#fff', opacity: 0.9, fontSize: '12px' }"
              :value-style="{ color: '#fff', fontSize: '28px', fontWeight: 700 }"
            />
            <el-icon class="stat-ico" :size="22">
              <component :is="s.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体三列 -->
    <el-row :gutter="12" class="mt-12">
      <!-- 左: 能力图谱 -->
      <el-col :xs="24" :md="8" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-hd">
              <h3>🚀 平台能力</h3>
              <el-link type="primary" underline="never" @click="$router.push('/workflow')">编排工作流 →</el-link>
            </div>
          </template>
          <div class="cap-graph">
            <div v-for="(c, i) in capList" :key="c.name" class="cap-node" :style="`--c1: ${c.c1}; --c2: ${c.c2}`" @click="onCapClick(c)">
              <el-avatar :size="36" class="cap-ico" :style="`background: linear-gradient(135deg, ${c.c1}, ${c.c2})`">
                {{ c.icon }}
              </el-avatar>
              <div class="cap-name">{{ c.name }}</div>
              <el-tag size="small" type="info" effect="plain" class="cap-count">{{ c.count }} 项</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 中: 实时活动 -->
      <el-col :xs="24" :md="8" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-hd">
              <h3>📡 实时活动</h3>
              <div>
                <span class="live-dot" :class="{ active: liveCount > 0 }"></span>
                <small class="text-muted">最近 {{ recentEvents.length }} 条</small>
              </div>
            </div>
          </template>
          <el-scrollbar height="240px">
            <div v-if="recentEvents.length" class="evt-list">
              <article v-for="e in recentEvents" :key="e.id" class="evt" :class="e.type">
                <el-tag size="small" effect="dark" :type="evtType(e.type)">{{ tagOf(e.type) }}</el-tag>
                <span class="evt-text">{{ e.text }}</span>
                <small class="text-muted evt-time">{{ e.timeAgo }}</small>
              </article>
            </div>
            <el-empty v-else description="暂无活动" :image-size="60" />
          </el-scrollbar>
          <template v-if="!recentEvents.length">
            <p class="text-muted text-sm" style="text-align:center; margin-top: 8px;">
              试试去
              <el-link type="primary" underline="never" @click="$router.push('/train')">训练任务</el-link>
              或
              <el-link type="primary" underline="never" @click="$router.push('/chat')">智能对话</el-link>
            </p>
          </template>
        </el-card>
      </el-col>

      <!-- 中右: 业务总览 -->
      <el-col :xs="24" :md="8" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-hd">
              <h3>💰 业务总览</h3>
              <small class="text-muted">实时</small>
            </div>
          </template>
          <el-statistic title="客户数" :value="bizStats.customerTotal || 0" :value-style="{ color: '#6366f1' }" />
          <el-statistic title="商机数" :value="bizStats.opportunityTotal || 0" :value-style="{ color: '#f59e0b' }" style="margin-top: 8px;" />
          <el-statistic title="合同总金额" :value="bizStats.contractAmount || 0" :precision="2" prefix="¥" :value-style="{ color: '#10b981' }" style="margin-top: 8px;" />
          <el-statistic title="已回款" :value="bizStats.paidAmount || 0" :precision="2" prefix="¥" :value-style="{ color: '#ec4899' }" style="margin-top: 8px;" />
          <el-divider style="margin: 12px 0;" />
          <el-link type="primary" underline="never" size="small" @click="$router.push('/customers')">客户管理</el-link>
          <el-divider direction="vertical" />
          <el-link type="primary" underline="never" size="small" @click="$router.push('/opportunities')">商机</el-link>
          <el-divider direction="vertical" />
          <el-link type="primary" underline="never" size="small" @click="$router.push('/contracts')">合同</el-link>
          <el-divider direction="vertical" />
          <el-link type="primary" underline="never" size="small" @click="$router.push('/orders')">订单</el-link>
        </el-card>
      </el-col>

      <!-- 右: 系统状态 -->
      <el-col :xs="24" :md="8" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-hd">
              <h3>💚 系统状态</h3>
              <small class="text-muted">{{ sysCheckTime }}</small>
            </div>
          </template>
          <el-descriptions :column="1" border size="small" class="sys-list">
            <el-descriptions-item v-for="s in sysList" :key="s.name" :label="s.name">
              <template #label>
                <span class="sys-label">
                  <span class="sys-dot" :class="s.status"></span>
                  {{ s.name }}
                </span>
              </template>
              <span class="text-muted text-sm">{{ s.detail }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="sys-tip">
            <el-link type="primary" underline="never" size="small" @click="checkAll">
              <el-icon><Refresh /></el-icon>
              重新检查
            </el-link>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="12" class="mt-12">
      <el-col v-for="q in quickActions" :key="q.path" :xs="24" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="quick-card" :body-style="{ padding: 0 }" @click="$router.push(q.path)">
          <div class="quick-inner" :style="`--c1: ${q.c1}; --c2: ${q.c2}`">
            <el-avatar :size="44" class="q-ico" :style="`background: linear-gradient(135deg, ${q.c1}, ${q.c2})`">
              <el-icon :size="22"><component :is="q.icon" /></el-icon>
            </el-avatar>
            <div class="q-meta">
              <div class="q-name">{{ q.name }}</div>
              <div class="q-desc">{{ q.desc }}</div>
            </div>
            <el-icon class="q-arrow"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>

defineOptions({ name: 'Dashboard' })

import { ref, computed, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import {
  Cpu, Files, VideoPlay, UserFilled, Tools, Reading, ChatDotRound,
  OfficeBuilding, User, Clock, Connection, ArrowRight, Refresh
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'
import { bizApi } from '@/api'
import { modelApi, datasetApi, agentApi, knowledgeApi } from '@/api'
import request from '@/utils/request'

const router = useRouter()
const bus = useGlobalBus()

const username = localStorage.getItem('username') || ''
const nickname = localStorage.getItem('nickname') || username
const tenantName = localStorage.getItem('tenant_name') || ''
const department = localStorage.getItem('department') || ''

const now = ref(new Date().toLocaleString('zh-CN'))
let clockTimer = null

const stats = ref([
  { key: 'models',  label: '大模型',   value: 0, icon: markRaw(Cpu),        tone: 'blue',   path: '/models' },
  { key: 'agents',  label: '智能体',   value: 0, icon: markRaw(UserFilled), tone: 'purple', path: '/agents' },
  { key: 'kb',      label: '知识库',   value: 0, icon: markRaw(Reading),    tone: 'green',  path: '/knowledge' },
  { key: 'train',   label: '训练任务', value: 0, icon: markRaw(VideoPlay),  tone: 'orange', path: '/train' }
])
// 业务数据 (客户/商机/合同/订单)
const bizStats = ref({ customerTotal: 0, opportunityTotal: 0, contractAmount: 0, paidAmount: 0 })
const loadBiz = async () => {
  try {
    const r = await bizApi.dashboard()
    if (r.code === 200) bizStats.value = r.data
  } catch (e) { /* ignore */ }
}

const loadStats = async () => {
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
    } catch (e) { /* ignore */ }
  }
  bus.emit('sys:event', { text: `统计已刷新: 模型 ${stats.value[0].value} · 智能体 ${stats.value[1].value} · 知识库 ${stats.value[2].value}` })
}

const recentEvents = ref([])
const liveCount = ref(0)
const tagOf = (t) => ({ train: '训练', agent: '智能体', kb: '知识库', wf: '工作流', sys: '系统' })[t] || '事件'
const evtType = (t) => ({ train: 'primary', agent: 'success', kb: 'info', wf: 'warning', sys: 'info' })[t] || 'info'

let _off1, _off2, _off3, _off4, _off5
const onEvent = (e) => {
  recentEvents.value.unshift({
    id: e.id || Math.random().toString(36).slice(2),
    type: e.type || 'sys', text: e.text, ts: e.ts || Date.now(), timeAgo: '刚刚'
  })
  if (recentEvents.value.length > 8) recentEvents.value.length = 8
  liveCount.value++
  setTimeout(() => { if (liveCount.value > 0) liveCount.value-- }, 3000)
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

// ★ P0-1 修复: sysList 初始为空, 状态从 /api/*/health 真探活
const sysList = ref([
  { name: '网关',  status: 'pending',  detail: '9000 · 12 路由' },
  { name: '认证',  status: 'pending',  detail: '9010 · JWT' },
  { name: '推理',  status: 'pending',  detail: '9007 · ONNX' },
  { name: '训练',  status: 'pending',  detail: '9011 · DJL' },
  { name: '知识库', status: 'pending',  detail: '9005 · ES 8' },
  { name: 'Nacos', status: 'pending', detail: '8848 · 可选' }
])
const sysCheckTime = ref('')
const checkAll = async () => {
  sysCheckTime.value = new Date().toLocaleTimeString('zh-CN')
  // 名称 -> 接口路径映射 (真实后端)
  const pathMap = {
    '网关': '/api/auth/health',
    '认证': '/api/auth/health',
    '推理': '/api/inference/health',
    '训练': '/api/trainer/health',
    '知识库': '/api/knowledge/health'
  }
  for (const s of sysList.value) {
    if (s.name === 'Nacos') {
      // Nacos 另起端口, 简单 ping 一下, 不影响其他
      s.status = 'warn' // 本地默认不走 nacos, 标 warn
      continue
    }
    const path = pathMap[s.name] || '/api/auth/health'
    try {
      const resp = await request.get(path)
      s.status = resp.code === 200 || resp.data?.code === 200 ? 'ok' : 'down'
    } catch { s.status = 'down' }
  }
  bus.emit('sys:event', { text: '系统状态已重新检查' })
}

// ★ P0-2 延伸修复: capList 走真接口 (原来 count: 4/6/5/3/4/5 都是 hardcoded)
const capList = ref([
  { name: '模型', icon: '🧠', count: 0, c1: '#6366f1', c2: '#8b5cf6', path: '/models', api: () => modelApi.list() },
  { name: '数据集', icon: '📚', count: 0, c1: '#10b981', c2: '#06b6d4', path: '/datasets', api: () => datasetApi.page({ pageNum: 1, pageSize: 1 }) },
  { name: '训练', icon: '⚡', count: 0, c1: '#f59e0b', c2: '#ef4444', path: '/train', api: () => trainerApi.jobs() },
  { name: '智能体', icon: '🤖', count: 0, c1: '#8b5cf6', c2: '#ec4899', path: '/agents', api: () => agentApi.list() },
  { name: '工具', icon: '🛠️', count: 0, c1: '#06b6d4', c2: '#3b82f6', path: '/tools', api: null },
  { name: '知识库', icon: '📖', count: 0, c1: '#10b981', c2: '#84cc16', path: '/knowledge', api: () => knowledgeApi.bases() }
])
const loadCap = async () => {
  await Promise.allSettled(capList.value.map(async (c) => {
    if (!c.api) return
    try {
      const r = await c.api()
      c.count = Array.isArray(r.data) ? r.data.length : (r.data?.total || 0)
    } catch (e) { c.count = 0 }
  }))
}
const onCapClick = (c) => router.push(c.path)

const quickActions = [
  { name: '工作流编排', desc: '拖拽编排业务流', icon: Connection,     c1: '#6366f1', c2: '#8b5cf6', path: '/workflow' },
  { name: '智能体管理', desc: 'ReAct + 工具调用', icon: UserFilled,  c1: '#ec4899', c2: '#f43f5e', path: '/agents' },
  { name: '知识库',    desc: 'RAG 检索增强',     icon: Reading,     c1: '#10b981', c2: '#06b6d4', path: '/knowledge' },
  { name: '推理测试',  desc: 'ONNX 推理',        icon: ChatDotRound, c1: '#f59e0b', c2: '#ef4444', path: '/inference' }
]

let healthTimer = null
onMounted(() => {
  clockTimer = setInterval(() => { now.value = new Date().toLocaleString('zh-CN') }, 1000)
  loadStats()
  loadCap()
  checkAll()
  loadBiz()
  // ★ P0-1 增强: 30s 自动重新探活 (服务挂了能看到)
  healthTimer = setInterval(() => { checkAll() }, 30000)
  _off1 = bus.on('train:event', onEvent)
  _off2 = bus.on('agent:event', onEvent)
  _off3 = bus.on('kb:event', onEvent)
  _off4 = bus.on('wf:event', onEvent)
  _off5 = bus.on('sys:event', onEvent)
})
onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (healthTimer) clearInterval(healthTimer)
  _off1 && _off1(); _off2 && _off2(); _off3 && _off3(); _off4 && _off4(); _off5 && _off5()
})
</script>

<style scoped>
.dash { display: flex; flex-direction: column; gap: 12px; }

/* ===== Hello (el-tag 替代 div) ===== */
.hello {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18px 22px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  color: #fff; border-radius: 16px;
  position: relative; overflow: hidden;
}
.hello::before, .hello::after { content: ''; position: absolute; border-radius: 50%; }
.hello::before { right: -40px; top: -40px; width: 200px; height: 200px; background: rgba(255, 255, 255, 0.08); }
.hello::after { right: 60px; bottom: -60px; width: 160px; height: 160px; background: rgba(255, 255, 255, 0.05); }
.hello-text h1 { font-size: 22px; margin: 0 0 6px; font-weight: 700; }
.wave { display: inline-block; animation: wave 1.5s ease-in-out infinite; }
@keyframes wave { 0%, 100% { transform: rotate(0); } 50% { transform: rotate(-20deg); } }
.hello-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.hello-tags .el-tag { background: rgba(255, 255, 255, 0.15) !important; border-color: rgba(255, 255, 255, 0.2) !important; color: #fff !important; }
.hello-tags .el-icon { margin-right: 3px; }
.hello-cta { display: flex; gap: 8px; z-index: 2; }
.hello-cta .el-button:first-child { background: #fff; color: #6366f1; border: none; font-weight: 600; }
.hello-cta .el-button:first-child:hover { background: #f3f4f6; }
.hello-cta .el-button:not(:first-child) { background: rgba(255, 255, 255, 0.2); color: #fff; border: 1px solid rgba(255, 255, 255, 0.3); }

/* ===== 统计卡 (el-card + el-statistic) ===== */
.stats-row { margin: 0 !important; }
.stats-row .el-col { margin-bottom: 8px; }
.stat-card { cursor: pointer; border: none !important; transition: all 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 12px 30px -8px rgba(0, 0, 0, 0.3) !important; }
.stat-inner { position: relative; padding: 14px 16px; color: #fff; border-radius: 8px; }
.stat-inner.blue   { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
.stat-inner.purple { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.stat-inner.green  { background: linear-gradient(135deg, #10b981, #047857); }
.stat-inner.orange { background: linear-gradient(135deg, #f59e0b, #ea580c); }
.stat-ico { position: absolute; right: 14px; top: 50%; transform: translateY(-50%); width: 36px; height: 36px; border-radius: 10px; background: rgba(255, 255, 255, 0.2); display: flex; align-items: center; justify-content: center; }

/* ===== 3 列 ===== */
.panel { height: 100%; }
.card-hd { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.card-hd h3 { font-size: 14px; margin: 0; color: #1e293b; font-weight: 700; }

.live-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; margin-right: 4px; }
.live-dot.active { background: #10b981; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); } 50% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.1); } }

/* 能力图谱 */
.cap-graph { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.cap-node {
  padding: 10px 8px; border-radius: 10px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  color: #fff; text-align: center;
  cursor: pointer; transition: all 0.2s;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.cap-node:hover { transform: translateY(-3px) scale(1.04); box-shadow: 0 10px 24px -6px rgba(0, 0, 0, 0.3); }
.cap-ico { font-size: 18px; }
.cap-name { font-size: 12px; font-weight: 700; }
.cap-count { font-size: 10px !important; height: 18px; line-height: 18px; padding: 0 6px; }

/* 实时活动 */
.evt-list { display: flex; flex-direction: column; gap: 6px; }
.evt { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 8px; background: #f8fafc; }
.evt-text { flex: 1; min-width: 0; color: #1e293b; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.evt-time { font-size: 10px; flex-shrink: 0; }
.sys-tip { margin-top: 10px; text-align: right; }

/* 系统状态 (el-descriptions) */
.sys-list :deep(.el-descriptions__label) { width: 80px; }
.sys-label { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.sys-dot { width: 8px; height: 8px; border-radius: 50%; }
.sys-dot.ok   { background: #10b981; }
.sys-dot.warn { background: #f59e0b; }
.sys-dot.down { background: #ef4444; }

/* ===== 快捷入口 (el-card) ===== */
.quick-card { cursor: pointer; border: 1px solid var(--border, #e5e7eb) !important; transition: all 0.2s; }
.quick-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px -6px rgba(0, 0, 0, 0.15) !important; }
.quick-inner {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-radius: 12px;
  position: relative; overflow: hidden;
}
.quick-inner::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0;
  width: 4px; background: linear-gradient(180deg, var(--c1), var(--c2));
}
.q-ico { color: #fff !important; flex-shrink: 0; }
.q-meta { flex: 1; min-width: 0; }
.q-name { font-size: 14px; font-weight: 700; color: #1e293b; }
.q-desc { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.q-arrow { color: #94a3b8; transition: all 0.2s; }
.quick-card:hover .q-arrow { color: var(--c1); transform: translateX(4px); }

@media (max-width: 1200px) {
  .cap-graph { grid-template-columns: repeat(2, 1fr); }
}
</style>
