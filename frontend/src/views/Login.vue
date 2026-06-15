<template>
  <div class="login-bg">
    <canvas ref="canvas" class="particles"></canvas>
    <div class="blob blob-1"></div>
    <div class="blob blob-2"></div>
    <div class="blob blob-3"></div>

    <div class="login-shell">
      <!-- 左：品牌区 -->
      <section class="brand-panel">
        <div class="brand-stack">
          <div class="logo-wrap">
            <div class="logo-ring"><span class="logo-emoji">🤖</span></div>
            <div class="logo-version">v 1.0</div>
          </div>
          <h1 class="brand-title">AI Agent Platform</h1>
          <p class="brand-sub">大模型 · 智能体 · 分布式事务 一体化平台</p>

          <ul class="feature-list">
            <li><span class="ico">⚡</span><span>ReAct 多智能体编排 + 联网搜索</span></li>
            <li><span class="ico">🧠</span><span>本地训练（Transformer）+ ONNX 推理</span></li>
            <li><span class="ico">📚</span><span>知识库 RAG（ES 8 + Tika + 查询改写）</span></li>
            <li><span class="ico">🔗</span><span>Seata 分布式事务 + Nacos 服务发现</span></li>
            <li><span class="ico">🏢</span><span>多公司（租户）+ 部门归属 + 网关统一鉴权</span></li>
          </ul>

          <div class="brand-foot">
            <span class="status-dot"></span>
            <span>{{ currentTime }} · 系统正常</span>
          </div>
        </div>
      </section>

      <!-- 右：登录表单（多组横排 + 模拟发送） -->
      <section class="form-panel">
        <h2 class="panel-title">登录信息</h2>
        <p class="panel-tip">
          多组横排展示，每组独立发送请求验证；
          <strong>Admin</strong> 是最高系统管理员，<span class="hl">公司信息留空</span>。
        </p>

        <!-- 列头 -->
        <div class="grid-head">
          <span class="col-idx">#</span>
          <span class="col-name">用户名 <small class="req">*</small></span>
          <span class="col-name">密码 <small class="req">*</small></span>
          <span class="col-tenant">
            公司
            <small class="req" v-if="!anyAdminEntered">*</small>
            <small class="req admin-tip" v-else>admin 留空</small>
            <el-icon class="reload" :class="{ spinning: loadingTenants }" @click="loadTenants">
              <Refresh />
            </el-icon>
          </span>
          <span class="col-act">操作</span>
        </div>

        <!-- 多组横排：每行 = 一组登录信息（用户/密码/公司 三 input + 发送按钮 + 结果） -->
        <transition-group name="row" tag="div" class="grid-rows">
          <div v-for="(g, idx) in groups" :key="g.id" class="grid-row" :class="{ admin: g.username.toLowerCase() === 'admin' }">
            <span class="col-idx row-idx">#{{ idx + 1 }}</span>

            <!-- 用户名 -->
            <div class="col-name cell-input">
              <el-input
                v-model="g.username"
                placeholder="用户名"
                :prefix-icon="User"
                size="default"
                clearable
                @blur="recheckAdmin(g)"
              />
            </div>

            <!-- 密码 -->
            <div class="col-name cell-input">
              <el-input
                v-model="g.password"
                type="password"
                placeholder="密码"
                show-password
                :prefix-icon="Lock"
                size="default"
              />
            </div>

            <!-- 公司（admin 自动隐藏） -->
            <div class="col-tenant cell-input">
              <el-select
                v-if="g.username.toLowerCase() !== 'admin'"
                v-model="g.tenantId"
                :placeholder="loadingTenants ? '加载公司…' : '选择公司'"
                size="default"
                clearable
                filterable
                class="tenant-sel"
                :loading="loadingTenants"
              >
                <el-option
                  v-for="t in tenants"
                  :key="t.id"
                  :label="t.tenantName || t.tenantCode"
                  :value="t.id"
                >
                  <span class="opt-row">
                    <span class="opt-name">{{ t.tenantName || t.tenantCode }}</span>
                    <span class="opt-code">{{ t.tenantCode }} · id={{ t.id }}</span>
                  </span>
                </el-option>
              </el-select>
              <div v-else class="admin-tenant">
                <el-icon><Avatar /></el-icon>
                全部公司（超管）
              </div>
            </div>

            <!-- 操作：发送请求 + 删除 -->
            <div class="col-act cell-act">
              <el-button
                type="primary"
                :loading="g.sending"
                :disabled="!g.username || !g.password"
                size="default"
                @click="onSend(g)"
              >
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
              <el-button
                v-if="groups.length > 1"
                text
                :underline="false"
                size="default"
                class="del-btn"
                @click="removeRow(g.id)"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>

            <!-- 结果行（横跨全行） -->
            <div v-if="g.result" class="result-line" :class="g.result.kind">
              <span class="result-icon">
                <el-icon v-if="g.result.kind === 'success'"><CircleCheckFilled /></el-icon>
                <el-icon v-else-if="g.result.kind === 'error'"><CircleCloseFilled /></el-icon>
                <el-icon v-else><Loading /></el-icon>
              </span>
              <span class="result-text">{{ g.result.text }}</span>
              <span v-if="g.result.detail" class="result-detail">{{ g.result.detail }}</span>
              <el-button
                v-if="g.result.kind === 'success' && g.result.token"
                text
                :underline="false"
                size="small"
                type="primary"
                @click="useTokenAndEnter(g)"
              >
                进入系统 →
              </el-button>
            </div>
          </div>
        </transition-group>

        <!-- 工具栏 -->
        <div class="toolbar">
          <el-button :underline="false" @click="addRow" type="primary" plain>
            <el-icon><Plus /></el-icon>
            增加一组
          </el-button>
          <el-button :underline="false" @click="batchSend" :loading="batchSending">
            <el-icon><VideoPlay /></el-icon>
            一键批量发送（{{ groups.length }} 组）
          </el-button>
          <el-button :underline="false" @click="clearAll" text>
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
          <div class="spacer"></div>
          <span class="stat-pill">
            总数 <strong>{{ groups.length }}</strong>
            ·
            已登录 <strong class="ok">{{ successCount }}</strong>
            ·
            失败 <strong class="err">{{ errorCount }}</strong>
          </span>
        </div>

        <!-- 调试信息 -->
        <div class="debug-box">
          <p class="debug-title">🐛 调试信息（每次发送请求会显示完整链路）</p>
          <div class="debug-log">
            <p v-if="!logs.length" class="empty">点击"发送"后这里会显示 HTTP 请求/响应/状态码…</p>
            <p v-for="(l, i) in logs" :key="i" class="log-line" :class="l.kind">
              <span class="log-time">{{ l.time }}</span>
              <span class="log-tag">{{ l.tag }}</span>
              <span class="log-msg">{{ l.msg }}</span>
            </p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  User, Lock, Avatar, Promotion, Close, Plus, VideoPlay, Delete,
  Refresh, CircleCheckFilled, CircleCloseFilled, Loading
} from '@element-plus/icons-vue'
import { authApi } from '@/api'

const router = useRouter()

// ============== 多组登录信息（每组 = 一行） ==============
let _id = 0
const makeRow = (u = '', p = '', t = null) => ({
  id: ++_id,
  username: u,
  password: p,
  tenantId: t,
  sending: false,
  result: null
})

const groups = reactive([makeRow('admin', 'admin123', null), makeRow('demo', 'demo123', 1), makeRow('manager', 'demo123', 2)])

const anyAdminEntered = computed(() => groups.some(g => g.username.toLowerCase() === 'admin'))

const addRow = () => groups.push(makeRow())
const removeRow = (id) => {
  const idx = groups.findIndex(g => g.id === id)
  if (idx >= 0 && groups.length > 1) groups.splice(idx, 1)
}
const recheckAdmin = (g) => {
  if (g.username.toLowerCase() === 'admin') {
    g.tenantId = null
  }
}

const successCount = computed(() => groups.filter(g => g.result?.kind === 'success').length)
const errorCount = computed(() => groups.filter(g => g.result?.kind === 'error').length)

const clearAll = () => {
  groups.splice(0, groups.length, makeRow())
  logs.splice(0, logs.length)
}

// ============== 公司列表（页面加载时初始化） ==============
const tenants = ref([])
const loadingTenants = ref(false)
const loadTenants = async () => {
  loadingTenants.value = true
  log('GET', '/api/auth/tenants 拉取公司列表…')
  try {
    const resp = await authApi.tenants()
    tenants.value = resp.data || []
    log('OK', `拿到 ${tenants.value.length} 家公司: ${tenants.value.map(t => t.tenantName).join(' / ')}`, 'success')
  } catch (e) {
    log('ERR', `拉取公司失败: ${e?.response?.status} ${e?.response?.data?.message || e.message}`, 'error')
    // 用 fallback
    tenants.value = [
      { id: 1, tenantCode: 'default', tenantName: '默认公司' },
      { id: 2, tenantCode: 'demo-corp', tenantName: '示例科技公司' },
      { id: 3, tenantCode: 'startup-co', tenantName: '创业小公司' }
    ]
    log('FALLBACK', `使用内置 fallback 3 家公司`, 'warn')
  } finally {
    loadingTenants.value = false
  }
}

// ============== 调试日志 ==============
const logs = reactive([])
const log = (tag, msg, kind = 'info') => {
  const t = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  logs.unshift({ time: t, tag, msg, kind })
  if (logs.length > 30) logs.length = 30
}

// ============== 发送请求 ==============
const onSend = async (g) => {
  g.sending = true
  g.result = { kind: 'pending', text: '正在请求…', detail: `${g.username} @ 租户 ${g.tenantId || 'ALL(admin)'}` }
  log('POST', `→ /api/auth/login { username: ${g.username}, tenantId: ${g.tenantId} }`)

  const isAdmin = g.username.toLowerCase() === 'admin'
  const tenantId = isAdmin ? null : (g.tenantId || null)

  const t0 = performance.now()
  try {
    const resp = await authApi.login({ username: g.username, password: g.password, tenantId })
    const dt = (performance.now() - t0).toFixed(0)
    const data = resp.data
    g.result = {
      kind: 'success',
      text: `✅ 登录成功 (${dt}ms)`,
      detail: `用户 ${data.username} · 租户 ${data.tenantName}(id=${data.tenantId}) · 角色 ${data.roles?.join('/')}`,
      token: data.accessToken,
      _data: data
    }
    log('200', `← login OK, 耗时 ${dt}ms, token ${data.accessToken?.slice(0, 20)}…`, 'success')
  } catch (e) {
    const dt = (performance.now() - t0).toFixed(0)
    const status = e?.response?.status
    const body = e?.response?.data
    g.result = {
      kind: 'error',
      text: `❌ 登录失败 (${dt}ms)`,
      detail: `HTTP ${status} · ${body?.message || e.message}`
    }
    log(`${status}`, `← login FAIL: ${body?.message || e.message}`, 'error')
  } finally {
    g.sending = false
  }
}

const batchSending = ref(false)
const batchSend = async () => {
  batchSending.value = true
  log('BATCH', `批量发送 ${groups.length} 组请求…`, 'info')
  for (const g of groups) {
    if (g.username && g.password) await onSend(g)
  }
  batchSending.value = false
  log('BATCH', '批量完成', 'success')
}

const useTokenAndEnter = (g) => {
  if (!g.result?.token) return
  const data = g.result._data
  localStorage.setItem('access_token', g.result.token)
  if (data) {
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('tenant_id', String(data.tenantId))
    localStorage.setItem('tenant_code', data.tenantCode || '')
    localStorage.setItem('tenant_name', data.tenantName || '')
    localStorage.setItem('department', data.department || '')
    localStorage.setItem('roles', JSON.stringify(data.roles || ['user']))
  }
  ElMessage.success(`使用 ${g.username} 身份进入系统`)
  router.push('/dashboard')
}

// ============== 背景 / 时钟 ==============
const canvas = ref(null)
const currentTime = ref(new Date().toLocaleString('zh-CN'))
let rafId = null
let clockTimer = null

onMounted(() => {
  loadTenants()
  clockTimer = setInterval(() => {
    currentTime.value = new Date().toLocaleString('zh-CN')
  }, 1000)
  const c = canvas.value
  if (!c) return
  c.width = c.offsetWidth; c.height = c.offsetHeight
  const ctx = c.getContext('2d')
  const N = 60
  const particles = Array.from({ length: N }, () => ({
    x: Math.random() * c.width, y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 0.4, vy: (Math.random() - 0.5) * 0.4,
    r: Math.random() * 1.6 + 0.6
  }))
  const draw = () => {
    ctx.clearRect(0, 0, c.width, c.height)
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > c.width) p.vx *= -1
      if (p.y < 0 || p.y > c.height) p.vy *= -1
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(167, 139, 250, 0.5)'
      ctx.fill()
    }
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const d = Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2)
        if (d < 110) {
          ctx.beginPath()
          ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(167, 139, 250, ${(1 - d / 110) * 0.2})`
          ctx.stroke()
        }
      }
    }
    rafId = requestAnimationFrame(draw)
  }
  draw()
  window.addEventListener('resize', () => { c.width = c.offsetWidth; c.height = c.offsetHeight })
})
onBeforeUnmount(() => { if (rafId) cancelAnimationFrame(rafId); if (clockTimer) clearInterval(clockTimer) })
</script>

<style scoped>
.login-bg {
  position: fixed; inset: 0; overflow: hidden;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 30%, #4338ca 70%, #6366f1 100%);
  display: flex; align-items: center; justify-content: center;
}
.particles { position: absolute; inset: 0; width: 100%; height: 100%; }
.blob { position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.55; animation: float 12s ease-in-out infinite; }
.blob-1 { width: 380px; height: 380px; background: #ec4899; top: -100px; left: -120px; }
.blob-2 { width: 460px; height: 460px; background: #06b6d4; bottom: -150px; right: -150px; animation-delay: -4s; }
.blob-3 { width: 300px; height: 300px; background: #f59e0b; top: 40%; right: 25%; animation-delay: -8s; }
@keyframes float { 0%, 100% { transform: translate(0, 0) scale(1); } 50% { transform: translate(40px, -30px) scale(1.1); } }

.login-shell {
  position: relative; z-index: 2;
  display: flex;
  width: 1180px; max-width: calc(100vw - 40px);
  min-height: 720px; max-height: calc(100vh - 40px);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  box-shadow: 0 24px 60px -12px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.6);
  animation: shellIn 0.5s ease-out;
  overflow: hidden;
}
@keyframes shellIn { from { opacity: 0; transform: translateY(20px) scale(0.96); } to { opacity: 1; transform: none; } }

.brand-panel {
  flex: 0 0 380px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  color: #fff; padding: 40px 32px;
  display: flex; flex-direction: column; justify-content: center;
  position: relative; overflow: hidden;
}
.brand-panel::before {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.15), transparent 50%);
}
.brand-stack { position: relative; z-index: 2; }
.logo-wrap { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.logo-ring {
  width: 64px; height: 64px; border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.25), rgba(255, 255, 255, 0.1));
  display: flex; align-items: center; justify-content: center; font-size: 36px;
  box-shadow: 0 8px 20px -4px rgba(0, 0, 0, 0.3);
  animation: pulse 3s ease-in-out infinite;
}
@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }
.logo-version { font-size: 11px; padding: 4px 10px; border-radius: 999px; background: rgba(255, 255, 255, 0.2); color: #fff; letter-spacing: 1px; }
.brand-title { font-size: 28px; font-weight: 800; margin: 0 0 8px; letter-spacing: -0.5px; }
.brand-sub { font-size: 13px; opacity: 0.85; margin: 0 0 32px; }
.feature-list { list-style: none; padding: 0; margin: 0; }
.feature-list li { display: flex; align-items: center; gap: 12px; padding: 9px 0; font-size: 13px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); }
.feature-list li:last-child { border-bottom: none; }
.feature-list .ico { width: 28px; height: 28px; border-radius: 8px; background: rgba(255, 255, 255, 0.18); display: flex; align-items: center; justify-content: center; font-size: 14px; }
.brand-foot { margin-top: 28px; display: flex; align-items: center; gap: 8px; font-size: 12px; opacity: 0.7; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #34d399; box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.3); animation: blink 2s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

.form-panel {
  flex: 1; padding: 28px 32px; display: flex; flex-direction: column;
  position: relative; min-height: 0; overflow-y: auto;
}
.panel-title { font-size: 22px; font-weight: 700; color: #1e1b4b; margin: 0 0 4px; }
.panel-tip { font-size: 12px; color: #6b7280; margin: 0 0 16px; }
.panel-tip .hl { color: #7c3aed; font-weight: 600; }

/* ============ 网格：列头 + 多行横排 ============ */
.grid-head, .grid-row {
  display: grid;
  grid-template-columns: 32px 1fr 1fr 1.2fr 130px;
  gap: 10px; align-items: center;
}
.grid-head {
  padding: 8px 12px;
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  border-radius: 10px;
  font-size: 12px; font-weight: 600; color: #4b5563;
  margin-bottom: 8px;
}
.col-idx { color: #9ca3af; font-weight: 500; }
.col-tenant .reload { margin-left: 4px; cursor: pointer; transition: transform 0.4s; }
.col-tenant .reload.spinning { animation: spin 0.8s linear infinite; }
.col-tenant .reload:hover { color: #6366f1; }
@keyframes spin { to { transform: rotate(360deg); } }
.req { color: #ef4444; font-weight: 700; margin-left: 2px; }
.admin-tip { color: #7c3aed; font-size: 10px; }
.col-act { text-align: right; }

.grid-rows { display: flex; flex-direction: column; gap: 6px; }
.grid-row {
  padding: 10px 12px;
  background: #fff;
  border: 1.5px solid #e5e7eb;
  border-radius: 12px;
  transition: all 0.18s;
}
.grid-row:hover { border-color: #a5b4fc; box-shadow: 0 2px 8px -2px rgba(99, 102, 241, 0.15); }
.grid-row.admin {
  background: linear-gradient(90deg, #faf5ff, #fff);
  border-color: #c4b5fd;
}
.row-idx { font-weight: 700; color: #6366f1; }

.cell-input :deep(.el-input__wrapper) {
  border: 1.5px solid #e5e7eb !important;
  border-radius: 8px !important;
  background: #fff !important;
  box-shadow: none !important;
  padding: 1px 10px;
  transition: all 0.15s;
}
.cell-input :deep(.el-input__wrapper.is-focus) {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12) !important;
}
.cell-input :deep(.el-select__wrapper) {
  border: 1.5px solid #e5e7eb !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  background: #fff !important;
  padding: 1px 10px;
  min-height: 32px;
}
.cell-input :deep(.el-select__wrapper.is-focused) {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12) !important;
}

.tenant-sel { width: 100%; }
.admin-tenant {
  display: flex; align-items: center; gap: 6px; padding: 4px 12px;
  background: linear-gradient(135deg, #ede9fe, #fae8ff);
  color: #7c3aed; font-weight: 600; font-size: 13px;
  border-radius: 8px; height: 32px;
}

.opt-row { display: flex; justify-content: space-between; width: 100%; align-items: center; }
.opt-name { font-size: 13px; font-weight: 500; }
.opt-code { font-size: 11px; color: #9ca3af; }

.cell-act { display: flex; gap: 4px; justify-content: flex-end; align-items: center; }
.del-btn { color: #9ca3af; padding: 4px; }
.del-btn:hover { color: #ef4444; }

/* 结果行 */
.result-line {
  grid-column: 1 / -1;
  display: flex; align-items: center; gap: 10px;
  margin-top: 6px; padding: 8px 12px;
  border-radius: 8px; font-size: 12px;
  border-left: 3px solid;
}
.result-line.pending { background: #fffbeb; border-color: #f59e0b; color: #92400e; }
.result-line.success { background: #ecfdf5; border-color: #10b981; color: #065f46; }
.result-line.error   { background: #fef2f2; border-color: #ef4444; color: #991b1b; }
.result-icon { font-size: 14px; display: flex; }
.result-line.success .result-icon { color: #10b981; }
.result-line.error .result-icon { color: #ef4444; }
.result-text { font-weight: 600; }
.result-detail { color: inherit; opacity: 0.85; font-size: 11px; }

/* ============ 工具栏 ============ */
.toolbar { display: flex; gap: 8px; align-items: center; margin-top: 12px; padding: 0 4px; }
.toolbar .spacer { flex: 1; }
.stat-pill {
  font-size: 12px; color: #6b7280;
  background: #f3f4f6; padding: 4px 12px; border-radius: 999px;
}
.stat-pill .ok { color: #10b981; }
.stat-pill .err { color: #ef4444; }

/* ============ 调试 ============ */
.debug-box { margin-top: 12px; padding: 10px 12px; background: #1e1b4b; border-radius: 10px; max-height: 200px; overflow-y: auto; }
.debug-title { font-size: 11px; color: #a5b4fc; margin: 0 0 6px; font-weight: 600; }
.debug-log .empty { font-size: 11px; color: #6b7280; font-style: italic; margin: 0; }
.log-line {
  display: flex; gap: 8px; font-family: 'SF Mono', Monaco, monospace; font-size: 11px;
  padding: 2px 0; line-height: 1.5;
}
.log-time { color: #6b7280; flex-shrink: 0; }
.log-tag {
  flex-shrink: 0; font-weight: 700; padding: 0 6px; border-radius: 3px;
  background: #312e81; color: #a5b4fc; min-width: 50px; text-align: center;
}
.log-line.success .log-tag { background: #064e3b; color: #6ee7b7; }
.log-line.error   .log-tag { background: #7f1d1d; color: #fca5a5; }
.log-line.warn    .log-tag { background: #78350f; color: #fcd34d; }
.log-msg { color: #c7d2fe; word-break: break-all; }

/* ============ 动画 ============ */
.row-enter-active, .row-leave-active { transition: all 0.25s ease; }
.row-enter-from, .row-leave-to { opacity: 0; transform: translateX(-20px); }
.row-enter-to, .row-leave-from { opacity: 1; transform: none; }

@media (max-width: 900px) {
  .login-shell { flex-direction: column; min-height: auto; }
  .brand-panel { flex: 0 0 auto; padding: 24px; }
  .feature-list { display: none; }
  .form-panel { padding: 20px; }
  .grid-head, .grid-row { grid-template-columns: 24px 1fr 1fr; }
  .grid-head .col-tenant, .grid-row .col-tenant,
  .grid-head .col-act, .grid-row .col-act { grid-column: 1 / -1; }
}
</style>
