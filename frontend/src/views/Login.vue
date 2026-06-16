<template>
  <div class="login-bg">
    <canvas ref="canvas" class="particles"></canvas>
    <div class="blob blob-1"></div>
    <div class="blob blob-2"></div>
    <div class="blob blob-3"></div>

    <!-- 顶部细信息条 -->
    <div class="topbar-info">
      <div class="tb-left">
        <span class="status-dot"></span>
        <span>系统正常 · {{ currentTime }}</span>
      </div>
      <div class="tb-right">
        <el-link :underline="false" @click="showAudit = true" type="primary" class="tb-link">
          <el-icon><Document /></el-icon>
          最近登录记录
        </el-link>
        <el-link :underline="false" @click="showHelp = true" class="tb-link">
          <el-icon><QuestionFilled /></el-icon>
          帮助
        </el-link>
        <el-link :underline="false" @click="cycleTheme" class="tb-link">
          <el-icon><component :is="themeIcon" /></el-icon>
        </el-link>
      </div>
    </div>

    <div class="login-shell">
      <!-- ============================================ -->
      <!-- 左：品牌区                                  -->
      <!-- ============================================ -->
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
            <span>{{ systemInfo }}</span>
          </div>
        </div>
      </section>

      <!-- ============================================ -->
      <!-- 右：登录表单                                -->
      <!-- ============================================ -->
      <section class="form-panel">
        <!-- 顶 tab -->
        <div class="login-tabs">
          <button v-for="t in tabs" :key="t.key"
                  :class="['tab', { active: activeTab === t.key }]"
                  @click="switchTab(t.key)">
            <span class="tab-ico">{{ t.icon }}</span>
            <span>{{ t.label }}</span>
          </button>
        </div>

        <transition name="step" mode="out-in">
          <!-- ============================================ -->
          <!-- Tab: 账号登录 — 多组横排                    -->
          <!-- ============================================ -->
          <div v-if="activeTab === 'account'" key="account" class="step-panel">
            <h2 class="panel-title">登录信息</h2>
            <p class="panel-tip">
              多组横排 · 每组独立发送请求验证 · <strong>Admin</strong> 跳过公司校验 ·
              失败会记录到 <code>sys_login_audit</code>
            </p>

            <!-- 公司列表（页面加载时拉取） -->
            <div class="tenant-banner" v-if="tenants.length">
              <div class="tb-head">
                <el-icon><OfficeBuilding /></el-icon>
                <strong>已加载 {{ tenants.length }} 家公司</strong>
                <el-icon class="reload" :class="{ spinning: loadingTenants }" @click="loadTenants">
                  <Refresh />
                </el-icon>
                <el-link :underline="false" class="tb-collapse" @click="showTenantBanner = !showTenantBanner">
                  {{ showTenantBanner ? '收起' : '展开' }}
                </el-link>
              </div>
              <div v-if="showTenantBanner" class="tb-list">
                <div v-for="t in tenants" :key="t.id" class="tb-item" @click="fillTenant(t)">
                  <div class="tb-ico" :style="`background: ${colorOf(t.id)}`">
                    {{ t.tenantName?.charAt(0) || '?' }}
                  </div>
                  <div class="tb-meta">
                    <div class="tb-name">{{ t.tenantName }}</div>
                    <div class="tb-code">{{ t.tenantCode }} · id={{ t.id }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 列头 -->
            <div class="grid-head">
              <span class="col-idx">#</span>
              <span class="col-name">用户名 <small class="req">*</small></span>
              <span class="col-name">密码 <small class="req">*</small></span>
              <span class="col-tenant">
                公司
                <small class="req" v-if="!anyAdminEntered">*</small>
                <small class="req admin-tip" v-else>admin 留空</small>
              </span>
              <span class="col-act">操作</span>
            </div>

            <!-- 多组横排：每行 = 一组登录信息 -->
            <transition-group name="row" tag="div" class="grid-rows">
              <div v-for="(g, idx) in groups" :key="g.id" class="grid-row" :class="{
                admin: g.username.toLowerCase() === 'admin',
                locked: g.lockedUntil && Date.now() < g.lockedUntil
              }">
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

                <!-- 公司 -->
                <div class="col-tenant cell-input">
                  <el-select
                    v-if="g.username.toLowerCase() !== 'admin'"
                    v-model="g.tenantId"
                    placeholder="选择公司"
                    size="default"
                    clearable
                    filterable
                    :loading="loadingTenants"
                    class="tenant-sel"
                  >
                    <el-option
                      v-for="t in tenants"
                      :key="t.id"
                      :label="t.tenantName || t.tenantCode"
                      :value="t.id"
                    >
                      <span class="opt-row">
                        <span class="opt-name">{{ t.tenantName }}</span>
                        <span class="opt-code">{{ t.tenantCode }} · id={{ t.id }}</span>
                      </span>
                    </el-option>
                  </el-select>
                  <div v-else class="admin-tenant">
                    <el-icon><Avatar /></el-icon>
                    全部公司
                  </div>
                </div>

                <!-- 操作 -->
                <div class="col-act cell-act">
                  <el-button
                    type="primary"
                    :loading="g.sending"
                    :disabled="!g.username || !g.password || (g.lockedUntil && Date.now() < g.lockedUntil)"
                    size="default"
                    @click="onSend(g)"
                  >
                    <el-icon><Promotion /></el-icon>
                    {{ g.sending ? '发送中' : '发送' }}
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

                <!-- 结果行 -->
                <div v-if="g.result" class="result-line" :class="g.result.kind">
                  <span class="result-icon">
                    <el-icon v-if="g.result.kind === 'success'"><CircleCheckFilled /></el-icon>
                    <el-icon v-else-if="g.result.kind === 'error'"><CircleCloseFilled /></el-icon>
                    <el-icon v-else><Loading /></el-icon>
                  </span>
                  <span class="result-text">{{ g.result.text }}</span>
                  <span v-if="g.result.detail" class="result-detail">{{ g.result.detail }}</span>
                  <el-button
                    v-if="g.result.kind === 'error' && g.result.failCount"
                    type="warning"
                    :underline="false"
                    size="small"
                    plain
                    @click="onSend(g)"
                  >
                    重试 ({{ g.failCount }}/5)
                  </el-button>
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
              <el-button :underline="false" @click="batchSend" :loading="batchSending" :disabled="!groups.length">
                <el-icon><VideoPlay /></el-icon>
                一键发送 ({{ groups.length }} 组)
              </el-button>
              <el-button :underline="false" @click="clearAll" text>
                <el-icon><Delete /></el-icon>
                清空
              </el-button>
              <div class="spacer"></div>
              <span class="stat-pill">
                总数 <strong>{{ groups.length }}</strong>
                · 已登录 <strong class="ok">{{ successCount }}</strong>
                · 失败 <strong class="err">{{ errorCount }}</strong>
              </span>
            </div>

            <!-- 快捷账号 -->
            <div class="quick-fill">
              <div class="quick-head">
                <p class="quick-tip">3 个快速账号（点击即填对应输入框）:</p>
              </div>
              <div class="quick-row">
                <button class="chip" @click="quickFill('admin','admin123',null,'超管')">
                  <span class="chip-avatar admin">A</span>
                  <div class="chip-meta">
                    <div class="chip-name">admin / admin123</div>
                    <div class="chip-dept">超级管理员 · 公司留空</div>
                  </div>
                </button>
                <button class="chip" @click="quickFill('demo','demo123',1,'用户')">
                  <span class="chip-avatar demo">D</span>
                  <div class="chip-meta">
                    <div class="chip-name">demo / demo123</div>
                    <div class="chip-dept">市场部 · 默认公司</div>
                  </div>
                </button>
                <button class="chip" @click="quickFill('manager','demo123',2,'用户')">
                  <span class="chip-avatar mgr">M</span>
                  <div class="chip-meta">
                    <div class="chip-name">manager / demo123</div>
                    <div class="chip-dept">运营部 · 示例科技</div>
                  </div>
                </button>
              </div>
              <div class="remember-row">
                <el-checkbox v-model="rememberMe" @change="onRememberChange">7 天内自动登录</el-checkbox>
                <el-link :underline="false" type="primary" @click="showAudit = true" class="audit-link">
                  <el-icon><Document /></el-icon>
                  查看最近登录
                </el-link>
              </div>
            </div>
          </div>

          <!-- ============================================ -->
          <!-- Tab: 企业 SSO                              -->
          <!-- ============================================ -->
          <div v-else-if="activeTab === 'sso'" key="sso" class="step-panel">
            <h2 class="panel-title">企业 SSO 登录</h2>
            <p class="panel-tip">使用企业账号（OIDC / LDAP / 飞书 / 钉钉）登录</p>
            <div class="sso-grid">
              <button class="sso-btn" v-for="s in ssoList" :key="s.key" @click="onSsoLogin(s.key)">
                <div class="sso-ico" :style="`background: linear-gradient(135deg, ${s.c1}, ${s.c2})`">{{ s.ch }}</div>
                <div class="sso-name">{{ s.name }}</div>
                <div class="sso-sub">{{ s.sub }}</div>
              </button>
            </div>
            <p class="sso-note">企业 SSO 接入请联系系统管理员配置 client_id / 重定向 URL</p>
          </div>

          <!-- ============================================ -->
          <!-- Tab: 访客                                  -->
          <!-- ============================================ -->
          <div v-else-if="activeTab === 'guest'" key="guest" class="step-panel">
            <h2 class="panel-title">访客体验</h2>
            <p class="panel-tip">无需账号，1 分钟快速浏览平台功能（只读沙箱）</p>
            <div class="guest-card">
              <div class="guest-ico">👀</div>
              <div class="guest-title">Demo Sandbox</div>
              <p class="guest-desc">
                预置 AI 助手、示例知识库、3 个示例多 Agent 案例。<br/>
                可聊天但不能保存数据，刷新即清空。
              </p>
              <el-button type="primary" size="large" round @click="onGuestLogin">
                进入沙箱 →
              </el-button>
            </div>
            <div class="guest-warn">
              <el-icon><WarningFilled /></el-icon>
              访客模式仅供评估用，生产数据请用正式账号登录
            </div>
          </div>
        </transition>
      </section>
    </div>

    <!-- ============================================ -->
    <!-- 调试日志（折叠在底部）                      -->
    <!-- ============================================ -->
    <div class="debug-bar" v-if="logs.length" :class="{ expanded: debugExpanded }">
      <div class="db-head" @click="debugExpanded = !debugExpanded">
        <span class="db-ico">🐛</span>
        <strong>请求日志</strong>
        <span class="db-count">{{ logs.length }}</span>
        <el-icon :class="{ flip: !debugExpanded }"><ArrowDown /></el-icon>
      </div>
      <div v-if="debugExpanded" class="db-body">
        <p v-if="!logs.length" class="db-empty">暂无日志</p>
        <article v-for="(l, i) in logs.slice(0, 30)" :key="i" class="db-item" :class="l.kind">
          <span class="db-time">{{ l.time }}</span>
          <span class="db-tag">{{ l.tag }}</span>
          <span class="db-msg">{{ l.msg }}</span>
        </article>
      </div>
    </div>

    <!-- ============================================ -->
    <!-- 最近登录记录弹窗                            -->
    <!-- ============================================ -->
    <el-dialog v-model="showAudit" title="最近登录记录" width="780px">
      <div class="audit-stats">
        <div class="as s-total"><span>📊 总数</span><strong>{{ auditStats.total || 0 }}</strong></div>
        <div class="as s-ok"><span>✅ 今日成功</span><strong>{{ auditStats.todaySuccess || 0 }}</strong></div>
        <div class="as s-err"><span>❌ 今日失败</span><strong>{{ auditStats.todayFailed || 0 }}</strong></div>
        <div class="as s-lock"><span>🔒 今日锁定</span><strong>{{ auditStats.todayLocked || 0 }}</strong></div>
      </div>
      <el-table :data="auditRecords" v-loading="loadingAudit" stripe size="small" max-height="380">
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="IP" width="140">
          <template #default="{ row }">
            <code class="ip">{{ row.loginIp || '—' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.loginStatus)" size="small">{{ row.loginStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <small class="muted">{{ row.loginTime }}</small>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-link :underline="false" type="primary" @click="loadAudit">刷新</el-link>
        <el-button @click="showAudit = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ============================================ -->
    <!-- 帮助弹窗                                    -->
    <!-- ============================================ -->
    <el-dialog v-model="showHelp" title="登录帮助" width="560px">
      <div class="help-content">
        <h3>📋 默认账号</h3>
        <el-table :data="helpAccounts" size="small">
          <el-table-column prop="user" label="用户名" />
          <el-table-column prop="pass" label="密码" />
          <el-table-column prop="role" label="角色" />
        </el-table>
        <h3>❓ 常见问题</h3>
        <el-collapse>
          <el-collapse-item title="忘记密码怎么办？" name="1">
            联系企业管理员重置，或在 User 管理页找 admin 重置
          </el-collapse-item>
          <el-collapse-item title="Admin 登录后看不到公司？" name="2">
            Admin 是超级管理员，拥有所有租户权限。公司列表中会自动包含 [id=0, code=ALL] 虚拟公司
          </el-collapse-item>
          <el-collapse-item title="为什么登录失败后被记录？" name="3">
            平台会自动记录所有登录尝试到 <code>sys_login_audit</code>，含 IP / User-Agent / 失败原因，用于安全审计
          </el-collapse-item>
          <el-collapse-item title="如何切换公司？" name="4">
            登录后右上角"用户菜单 → 切换租户"，会注销当前会话并以新公司身份重新登录
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Lock, OfficeBuilding, Avatar, Promotion, Close, Plus, VideoPlay, Delete,
  Refresh, CircleCheckFilled, CircleCloseFilled, Loading, ArrowDown, Document,
  QuestionFilled, WarningFilled, Sunny, Moon, MagicStick
} from '@element-plus/icons-vue'
import { authApi, auditApi } from '@/api'

const router = useRouter()

// ============== Tab ==============
const tabs = [
  { key: 'account', label: '账号登录', icon: '🔑' },
  { key: 'sso',     label: '企业 SSO', icon: '🏢' },
  { key: 'guest',   label: '访客体验', icon: '👀' }
]
const activeTab = ref('account')
const switchTab = (k) => { activeTab.value = k }

// ============== 多组登录 ==============
let _id = 0
const makeRow = (u = '', p = '', t = null) => ({
  id: ++_id,
  username: u,
  password: p,
  tenantId: t,
  sending: false,
  result: null,
  failCount: 0,
  lockedUntil: 0
})

const groups = reactive([makeRow('admin', 'admin123', null), makeRow('demo', 'demo123', 1), makeRow('manager', 'demo123', 2)])

const anyAdminEntered = computed(() => groups.some(g => g.username.toLowerCase() === 'admin'))
const successCount = computed(() => groups.filter(g => g.result?.kind === 'success').length)
const errorCount = computed(() => groups.filter(g => g.result?.kind === 'error').length)

const addRow = () => groups.push(makeRow())
const removeRow = (id) => {
  const idx = groups.findIndex(g => g.id === id)
  if (idx >= 0 && groups.length > 1) groups.splice(idx, 1)
}
const recheckAdmin = (g) => {
  if (g.username.toLowerCase() === 'admin') g.tenantId = null
}
const clearAll = () => {
  groups.splice(0, groups.length, makeRow())
  logs.splice(0, logs.length)
}

const quickFill = (u, p, t) => {
  groups[0].username = u
  groups[0].password = p
  groups[0].tenantId = t
  ElMessage.success(`已填入 ${u}`)
}

// ============== 公司列表（初始化加载） ==============
const tenants = ref([])
const loadingTenants = ref(false)
const showTenantBanner = ref(true)
const colorOf = (id) => {
  const colors = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#3b82f6']
  return colors[(Number(id) || 0) % colors.length]
}
const loadTenants = async () => {
  loadingTenants.value = true
  log('GET', '/api/auth/tenants 拉取公司列表…')
  try {
    const resp = await authApi.tenants()
    tenants.value = resp.data || []
    log('OK', `拿到 ${tenants.value.length} 家公司: ${tenants.value.map(t => t.tenantName).join(' / ')}`, 'success')
  } catch (e) {
    log('ERR', `拉取公司失败: ${e?.response?.status || ''} ${e?.response?.data?.message || e.message}`, 'error')
    tenants.value = [
      { id: 1, tenantCode: 'default',   tenantName: '默认公司' },
      { id: 2, tenantCode: 'demo-corp', tenantName: '示例科技公司' },
      { id: 3, tenantCode: 'startup-co', tenantName: '创业小公司' }
    ]
    log('FALLBACK', '使用内置 fallback 3 家公司', 'warn')
  } finally {
    loadingTenants.value = false
  }
}
const fillTenant = (t) => {
  groups.forEach(g => { g.tenantId = t.id })
  ElMessage.success(`已填入公司 ${t.tenantName}`)
}

// ============== 调试日志 ==============
const logs = reactive([])
const debugExpanded = ref(false)
const log = (tag, msg, kind = 'info') => {
  const t = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  logs.unshift({ time: t, tag, msg, kind })
  if (logs.length > 50) logs.length = 50
}

// ============== 发送请求 ==============
const onSend = async (g) => {
  // 检查锁定
  if (g.lockedUntil && Date.now() < g.lockedUntil) {
    const sec = Math.ceil((g.lockedUntil - Date.now()) / 1000)
    return ElMessage.warning(`本组已锁定，请等 ${sec}s 后再试`)
  }
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
    g.failCount = 0
    log('200', `← login OK, 耗时 ${dt}ms, token ${data.accessToken?.slice(0, 20)}…`, 'success')
  } catch (e) {
    const dt = (performance.now() - t0).toFixed(0)
    const status = e?.response?.status
    const body = e?.response?.data
    g.failCount++
    // 5 次失败锁定 30s
    if (g.failCount >= 5) {
      g.lockedUntil = Date.now() + 30000
    }
    g.result = {
      kind: 'error',
      text: `❌ 登录失败 (${dt}ms)`,
      detail: `HTTP ${status || 'ERR'} · ${body?.message || e.message} · 已记录到 sys_login_audit`,
      failCount: g.failCount
    }
    log(`${status || 'ERR'}`, `← login FAIL (${g.failCount}/5): ${body?.message || e.message}`, 'error')
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

// ============== SSO / Guest ==============
const ssoList = [
  { key: 'feishu',  ch: '飞', name: '飞书',     sub: 'Lark OIDC',     c1: '#00d6b9', c2: '#3370ff' },
  { key: 'dingtalk',ch: '钉', name: '钉钉',     sub: 'DingTalk OAuth', c1: '#1296db', c2: '#1677ff' },
  { key: 'wecom',   ch: '企', name: '企业微信', sub: 'WeCom QR',      c1: '#10aeff', c2: '#2675ec' },
  { key: 'ldap',    ch: 'LD', name: 'LDAP / AD',sub: '企业目录',     c1: '#6b7280', c2: '#1f2937' },
  { key: 'oauth2',  ch: 'O2', name: 'OAuth 2.0',sub: '通用 OIDC',    c1: '#f59e0b', c2: '#ef4444' },
  { key: 'saml',    ch: 'SA', name: 'SAML 2.0', sub: '企业 SAML',    c1: '#8b5cf6', c2: '#6366f1' }
]
const onSsoLogin = (provider) => ElMessage.info(`${provider} SSO 接入待配置`)
const onGuestLogin = () => {
  localStorage.setItem('access_token', 'guest_token')
  localStorage.setItem('username', 'guest')
  localStorage.setItem('nickname', '访客体验')
  localStorage.setItem('tenant_id', '0')
  localStorage.setItem('tenant_name', 'Demo Sandbox')
  localStorage.setItem('tenant_code', 'guest')
  localStorage.setItem('department', '体验组')
  ElMessage.success('已进入访客沙箱')
  router.push('/dashboard')
}

// ============== 主题 ==============
const themes = ['dark', 'light', 'auto']
const themeIdx = ref(0)
const themeIcon = computed(() => [Moon, Sunny, MagicStick][themeIdx.value])
const cycleTheme = () => {
  themeIdx.value = (themeIdx.value + 1) % themes.length
  document.documentElement.dataset.theme = themes[themeIdx.value]
  ElMessage.success(`主题: ${themes[themeIdx.value]}`)
}

// ============== 记住密码 ==============
const rememberMe = ref(false)
const onRememberChange = (v) => {
  if (v && groups[0].username) {
    localStorage.setItem('remember_username', groups[0].username)
  } else {
    localStorage.removeItem('remember_username')
  }
}

// ============== 登录审计 ==============
const showAudit = ref(false)
const auditStats = ref({})
const auditRecords = ref([])
const loadingAudit = ref(false)
const tagType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', LOCKED: 'warning' })[s] || 'info'
const loadAudit = async () => {
  loadingAudit.value = true
  try {
    const [stats, page] = await Promise.all([
      auditApi.stats(),
      auditApi.page({ current: 1, size: 20 })
    ])
    auditStats.value = stats.data || {}
    auditRecords.value = page.data?.records || []
    log('GET', `/api/audit/login (${auditRecords.value.length} 条)`, 'success')
  } catch (e) {
    log('ERR', `审计加载失败: ${e.message}`, 'error')
  }
  loadingAudit.value = false
}
watch(showAudit, (v) => { if (v) loadAudit() })

// ============== 时钟 / 系统信息 ==============
const currentTime = ref(new Date().toLocaleString('zh-CN'))
const systemInfo = ref('加载中…')
let clockTimer = null
let sysTimer = null

// ============== 背景 / 时钟 / 系统监控 ==============
const canvas = ref(null)
let rafId = null
const checkSystem = async () => {
  try {
    const r = await fetch('/api/auth/health', { method: 'GET' })
    systemInfo.value = r.ok ? '服务正常' : '部分服务异常'
  } catch {
    systemInfo.value = '部分服务异常 (离线模式)'
  }
}

onMounted(() => {
  // 记住用户名
  if (localStorage.getItem('remember_username')) {
    groups[0].username = localStorage.getItem('remember_username')
    rememberMe.value = true
  }
  loadTenants()
  checkSystem()
  sysTimer = setInterval(checkSystem, 30000)
  clockTimer = setInterval(() => { currentTime.value = new Date().toLocaleString('zh-CN') }, 1000)
  // 粒子背景
  const c = canvas.value
  if (!c) return
  c.width = c.offsetWidth; c.height = c.offsetHeight
  const ctx = c.getContext('2d')
  const N = 50
  const particles = Array.from({ length: N }, () => ({
    x: Math.random() * c.width, y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 0.3, vy: (Math.random() - 0.5) * 0.3,
    r: Math.random() * 1.5 + 0.5
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
        if (d < 100) {
          ctx.beginPath()
          ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(167, 139, 250, ${(1 - d / 100) * 0.18})`
          ctx.stroke()
        }
      }
    }
    rafId = requestAnimationFrame(draw)
  }
  draw()
  window.addEventListener('resize', () => { c.width = c.offsetWidth; c.height = c.offsetHeight })
})

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  if (clockTimer) clearInterval(clockTimer)
  if (sysTimer) clearInterval(sysTimer)
})

// ============== 帮助 ==============
const showHelp = ref(false)
const helpAccounts = [
  { user: 'admin',   pass: 'admin123', role: '超级管理员 (所有租户)' },
  { user: 'demo',    pass: 'demo123',  role: '市场部 / 默认公司' },
  { user: 'manager', pass: 'demo123',  role: '运营部 / 示例科技' }
]

// watch import
import { watch } from 'vue'
</script>

<style scoped>
/* ================================================== */
.login-bg {
  position: fixed; inset: 0; overflow: hidden;
  background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
  display: flex; align-items: center; justify-content: center;
}
.particles { position: absolute; inset: 0; width: 100%; height: 100%; }
.blob { position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.4; animation: float 12s ease-in-out infinite; }
.blob-1 { width: 380px; height: 380px; background: #6366f1; top: -100px; left: -120px; }
.blob-2 { width: 460px; height: 460px; background: #ec4899; bottom: -150px; right: -150px; animation-delay: -4s; }
.blob-3 { width: 300px; height: 300px; background: #06b6d4; top: 40%; right: 25%; animation-delay: -8s; }
@keyframes float { 0%, 100% { transform: translate(0, 0) scale(1); } 50% { transform: translate(40px, -30px) scale(1.1); } }

/* ===== 顶部信息条 ===== */
.topbar-info {
  position: fixed; top: 0; left: 0; right: 0; height: 32px;
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px; z-index: 50;
  background: rgba(0, 0, 0, 0.2); backdrop-filter: blur(8px);
  color: #cbd5e1; font-size: 12px;
}
.tb-left, .tb-right { display: flex; align-items: center; gap: 16px; }
.tb-left .status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #10b981; margin-right: 6px; animation: pulse 2s infinite; }
.tb-link { color: #cbd5e1 !important; font-size: 12px; display: flex; align-items: center; gap: 4px; }
.tb-link .el-icon { font-size: 13px; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

/* ================================================== */
.login-shell {
  position: relative; z-index: 2;
  display: flex;
  width: 1180px; max-width: calc(100vw - 40px);
  min-height: 720px; max-height: calc(100vh - 80px);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(20px);
  box-shadow: 0 24px 60px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1);
  animation: shellIn 0.5s ease-out;
  overflow: hidden;
}
@keyframes shellIn { from { opacity: 0; transform: translateY(20px) scale(0.96); } to { opacity: 1; transform: none; } }

/* 左 brand */
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
.logo-version { font-size: 11px; padding: 4px 10px; border-radius: 999px; background: rgba(255, 255, 255, 0.2); letter-spacing: 1px; }
.brand-title { font-size: 28px; font-weight: 800; margin: 0 0 8px; letter-spacing: -0.5px; }
.brand-sub { font-size: 13px; opacity: 0.85; margin: 0 0 32px; }
.feature-list { list-style: none; padding: 0; margin: 0; }
.feature-list li { display: flex; align-items: center; gap: 12px; padding: 9px 0; font-size: 13px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); }
.feature-list li:last-child { border-bottom: none; }
.feature-list .ico { width: 28px; height: 28px; border-radius: 8px; background: rgba(255, 255, 255, 0.18); display: flex; align-items: center; justify-content: center; font-size: 14px; }
.brand-foot { margin-top: 28px; display: flex; align-items: center; gap: 8px; font-size: 12px; opacity: 0.7; }
.brand-foot .status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #34d399; box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.3); }

/* 右 form */
.form-panel { flex: 1; padding: 24px 28px; display: flex; flex-direction: column; position: relative; min-height: 0; overflow-y: auto; }

.login-tabs { display: flex; gap: 4px; padding: 4px; background: #f3f4f6; border-radius: 10px; margin-bottom: 12px; }
.tab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 4px;
  padding: 8px 10px; border: none; background: transparent; cursor: pointer;
  border-radius: 7px; font-size: 12px; color: #64748b; font-weight: 500;
  transition: all 0.2s;
}
.tab.active { background: #fff; color: #6366f1; box-shadow: 0 2px 8px -2px rgba(99, 102, 241, 0.3); font-weight: 700; }
.tab-ico { font-size: 14px; }

.step-panel { flex: 1; }
.panel-title { font-size: 20px; font-weight: 700; color: #1e1b4b; margin: 0 0 4px; }
.panel-tip { font-size: 11px; color: #6b7280; margin: 0 0 12px; }
.panel-tip code { background: #ede9fe; padding: 1px 6px; border-radius: 3px; color: #5b21b6; font-size: 10px; }
.panel-tip strong { color: #7c3aed; }

/* ===== 公司 banner ===== */
.tenant-banner {
  background: linear-gradient(90deg, #f0f9ff, #faf5ff);
  border: 1px solid #c4b5fd; border-radius: 10px;
  padding: 8px 12px; margin-bottom: 10px;
}
.tb-head { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #1e293b; }
.tb-head strong { flex: 0 0 auto; }
.tb-head .el-icon { color: #6366f1; }
.tb-head .reload { cursor: pointer; }
.tb-head .reload.spinning { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.tb-collapse { font-size: 11px; }
.tb-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 6px; margin-top: 8px; }
.tb-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; border-radius: 8px;
  background: #fff; border: 1px solid #e5e7eb;
  cursor: pointer; transition: all 0.15s;
}
.tb-item:hover { border-color: #8b5cf6; transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(139, 92, 246, 0.2); }
.tb-ico {
  width: 28px; height: 28px; border-radius: 7px;
  color: #fff; font-weight: 700; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.tb-meta { flex: 1; min-width: 0; }
.tb-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.tb-code { font-size: 10px; color: #94a3b8; }

/* ===== 网格列头 + 行 ===== */
.grid-head, .grid-row {
  display: grid;
  grid-template-columns: 28px 1fr 1fr 1.2fr 130px;
  gap: 8px; align-items: center;
}
.grid-head {
  padding: 6px 10px;
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  border-radius: 8px;
  font-size: 11px; font-weight: 600; color: #4b5563;
  margin-bottom: 6px;
}
.col-idx { color: #9ca3af; font-weight: 500; }
.col-tenant .req { color: #ef4444; font-weight: 700; margin-left: 2px; }
.admin-tip { color: #7c3aed; font-size: 10px; }
.col-act { text-align: right; }

.grid-rows { display: flex; flex-direction: column; gap: 5px; }
.grid-row {
  padding: 8px 10px;
  background: #fff;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  transition: all 0.15s;
  position: relative;
}
.grid-row:hover { border-color: #a5b4fc; box-shadow: 0 2px 8px -2px rgba(99, 102, 241, 0.15); }
.grid-row.admin {
  background: linear-gradient(90deg, #faf5ff, #fff);
  border-color: #c4b5fd;
}
.grid-row.locked {
  background: linear-gradient(90deg, #fef2f2, #fff);
  border-color: #fca5a5;
  opacity: 0.6;
  pointer-events: none;
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
  min-height: 30px;
}
.cell-input :deep(.el-select__wrapper.is-focused) {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12) !important;
}

.admin-tenant {
  display: flex; align-items: center; gap: 6px; padding: 2px 12px;
  background: linear-gradient(135deg, #ede9fe, #fae8ff);
  color: #7c3aed; font-weight: 600; font-size: 12px;
  border-radius: 8px; height: 30px;
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
  display: flex; align-items: center; gap: 8px;
  margin-top: 5px; padding: 6px 10px;
  border-radius: 6px; font-size: 11px;
  border-left: 3px solid;
}
.result-line.pending { background: #fffbeb; border-color: #f59e0b; color: #92400e; }
.result-line.success { background: #ecfdf5; border-color: #10b981; color: #065f46; }
.result-line.error   { background: #fef2f2; border-color: #ef4444; color: #991b1b; }
.result-icon { font-size: 13px; display: flex; }
.result-line.success .result-icon { color: #10b981; }
.result-line.error .result-icon { color: #ef4444; }
.result-text { font-weight: 600; }
.result-detail { color: inherit; opacity: 0.85; font-size: 10px; }

/* ===== 工具栏 ===== */
.toolbar { display: flex; gap: 6px; align-items: center; margin-top: 10px; padding: 0 2px; }
.toolbar .spacer { flex: 1; }
.stat-pill { font-size: 11px; color: #6b7280; background: #f3f4f6; padding: 3px 10px; border-radius: 999px; }
.stat-pill .ok { color: #10b981; }
.stat-pill .err { color: #ef4444; }

/* ===== 快捷账号 ===== */
.quick-fill { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #e5e7eb; }
.quick-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.quick-tip { font-size: 11px; color: #9ca3af; margin: 0; }
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.chip {
  display: flex; align-items: center; gap: 8px; padding: 7px 9px;
  border: 1.5px solid #e5e7eb; background: #fff; border-radius: 9px;
  cursor: pointer; text-align: left; transition: all 0.15s;
}
.chip:hover { border-color: #a5b4fc; transform: translateY(-1px); box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.2); }
.chip-avatar {
  width: 28px; height: 28px; border-radius: 8px;
  color: #fff; font-weight: 700; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.chip-avatar.admin { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.chip-avatar.demo { background: linear-gradient(135deg, #ec4899, #f43f5e); }
.chip-avatar.mgr { background: linear-gradient(135deg, #10b981, #06b6d4); }
.chip-meta { flex: 1; min-width: 0; }
.chip-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.chip-dept { font-size: 10px; color: #6b7280; margin-top: 1px; }

.remember-row { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.audit-link { font-size: 11px; display: flex; align-items: center; gap: 3px; }

/* SSO / Guest */
.sso-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.sso-btn {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 14px 10px; border: 1.5px solid #e5e7eb; background: #fff; border-radius: 12px;
  cursor: pointer; transition: all 0.2s;
}
.sso-btn:hover { border-color: #a5b4fc; transform: translateY(-2px); box-shadow: 0 6px 16px -4px rgba(99, 102, 241, 0.2); }
.sso-ico {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 16px; font-weight: 700;
}
.sso-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.sso-sub { font-size: 10px; color: #6b7280; }
.sso-note { font-size: 11px; color: #9ca3af; text-align: center; margin-top: 14px; }

.guest-card { text-align: center; padding: 28px 20px; background: linear-gradient(135deg, #fef3c7, #fce7f3); border-radius: 16px; margin-bottom: 14px; }
.guest-ico { font-size: 44px; }
.guest-title { font-size: 17px; font-weight: 700; color: #1e1b4b; margin: 6px 0; }
.guest-desc { font-size: 12px; color: #4b5563; line-height: 1.6; margin: 0 0 16px; }
.guest-warn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px; background: #fff7ed; border: 1px solid #fed7aa; border-radius: 8px;
  color: #c2410c; font-size: 11px;
}

/* ================================================== */
/* 调试日志条（底部）                                */
/* ================================================== */
.debug-bar {
  position: fixed; left: 18px; bottom: 18px;
  max-width: 460px; z-index: 40;
  background: rgba(15, 23, 42, 0.97);
  border-radius: 12px;
  box-shadow: 0 10px 30px -8px rgba(0, 0, 0, 0.4);
  color: #cbd5e1; font-size: 11px;
  overflow: hidden;
  max-height: 38px;
  transition: max-height 0.3s;
}
.debug-bar.expanded { max-height: 340px; }
.db-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; cursor: pointer;
  background: rgba(99, 102, 241, 0.15);
  border-bottom: 1px solid rgba(99, 102, 241, 0.2);
}
.db-head .db-ico { font-size: 12px; }
.db-head strong { font-size: 12px; }
.db-count { background: #6366f1; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 999px; }
.db-head .el-icon { margin-left: auto; transition: transform 0.3s; }
.db-head .el-icon.flip { transform: rotate(180deg); }
.db-body { padding: 8px 12px; max-height: 280px; overflow-y: auto; font-family: 'SF Mono', monospace; }
.db-empty { color: #64748b; font-style: italic; margin: 0; }
.db-item { display: flex; gap: 6px; padding: 2px 0; line-height: 1.5; }
.db-time { color: #64748b; flex-shrink: 0; }
.db-tag {
  flex-shrink: 0; font-weight: 700; padding: 0 4px; border-radius: 3px;
  background: rgba(99, 102, 241, 0.2); color: #a5b4fc; min-width: 50px; text-align: center;
}
.db-item.success .db-tag { background: rgba(16, 185, 129, 0.2); color: #6ee7b7; }
.db-item.error   .db-tag { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
.db-item.warn    .db-tag { background: rgba(245, 158, 11, 0.2); color: #fcd34d; }
.db-msg { color: #c7d2fe; word-break: break-all; }

/* ================================================== */
/* 审计弹窗                                          */
/* ================================================== */
.audit-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
.as { padding: 10px; border-radius: 8px; color: #fff; display: flex; flex-direction: column; gap: 4px; }
.as strong { font-size: 18px; font-weight: 700; }
.as span { font-size: 11px; opacity: 0.9; }
.s-total { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.s-ok    { background: linear-gradient(135deg, #10b981, #047857); }
.s-err   { background: linear-gradient(135deg, #ef4444, #b91c1c); }
.s-lock  { background: linear-gradient(135deg, #f59e0b, #d97706); }
.ip { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-family: monospace; font-size: 11px; }
.muted { color: #94a3b8; }

/* 帮助 */
.help-content { font-size: 13px; line-height: 1.7; }
.help-content h3 { margin: 12px 0 8px; color: #1e1b4b; }
.help-content code { background: #ede9fe; padding: 1px 6px; border-radius: 3px; color: #5b21b6; }

/* 步骤切换 */
.step-enter-active, .step-leave-active { transition: all 0.25s ease; }
.step-enter-from { opacity: 0; transform: translateX(20px); }
.step-leave-to { opacity: 0; transform: translateX(-20px); }

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
  .topbar-info { font-size: 11px; }
}
</style>
