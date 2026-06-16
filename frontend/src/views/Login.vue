<template>
  <div class="login-bg">
    <canvas ref="canvas" class="particles"></canvas>
    <div class="blob blob-1"></div>
    <div class="blob blob-2"></div>
    <div class="blob blob-3"></div>

    <!-- 顶栏: 用 el-page-header -->
    <el-affix :offset="0" class="topbar-affix">
      <div class="topbar-info">
        <div class="tb-left">
          <el-tag size="small" type="success" effect="plain">
            <el-icon class="dot-icon"><CircleCheckFilled /></el-icon>
            系统正常 · {{ currentTime }}
          </el-tag>
        </div>
        <div class="tb-right">
          <el-link :underline="false" @click="showAudit = true" type="primary">
            <el-icon><Document /></el-icon>
            最近登录记录
          </el-link>
          <el-divider direction="vertical" />
          <el-link :underline="false" @click="showHelp = true">
            <el-icon><QuestionFilled /></el-icon>
            帮助
          </el-link>
          <el-divider direction="vertical" />
          <el-tooltip :content="`主题: ${themes[themeIdx]}`" placement="bottom">
            <el-button :underline="false" link @click="cycleTheme" class="tb-btn">
              <el-icon :size="16"><component :is="themeIcon" /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </el-affix>

    <div class="login-shell">
      <!-- ============== 左: 品牌区 ============== -->
      <section class="brand-panel">
        <div class="brand-stack">
          <div class="logo-wrap">
            <div class="logo-ring"><span class="logo-emoji">🤖</span></div>
            <el-tag size="small" effect="dark" round class="logo-version">v 1.0</el-tag>
          </div>
          <h1 class="brand-title">AI Agent Platform</h1>
          <p class="brand-sub">大模型 · 智能体 · 分布式事务 一体化平台</p>

          <ul class="feature-list">
            <li v-for="(f, i) in features" :key="i">
              <el-avatar :size="28" class="feat-ico">
                {{ f.icon }}
              </el-avatar>
              <span>{{ f.text }}</span>
            </li>
          </ul>

          <div class="brand-foot">
            <el-tag size="small" effect="dark" round>
              <el-icon><Loading v-if="loadingHealth" /><CircleCheckFilled v-else /></el-icon>
              {{ systemInfo }}
            </el-tag>
          </div>
        </div>
      </section>

      <!-- ============== 右: 登录表单 ============== -->
      <section class="form-panel">
        <el-segmented
          v-model="activeTab"
          :options="tabOptions"
          block
          class="login-segmented"
        />

        <transition name="step" mode="out-in">
          <!-- ===== 账号登录 ===== -->
          <div v-if="activeTab === 'account'" key="account" class="step-panel">
            <el-page-header :icon="null" class="panel-hd">
              <template #content>
                <h2 class="panel-title">登录信息</h2>
              </template>
              <template #extra>
                <el-tooltip content="点此查看上次登录" placement="top">
                  <el-button :underline="false" link size="small" @click="showAudit = true">
                    <el-icon><Clock /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
            </el-page-header>
            <p class="panel-tip">
              多组横排 · 每组独立发送请求 · <strong>Admin</strong> 跳过公司校验 ·
              失败自动记录 <code>sys_login_audit</code>
            </p>

            <!-- 公司 banner: el-descriptions 卡片 -->
            <el-card v-if="tenants.length" shadow="never" class="tenant-card">
              <template #header>
                <div class="tb-head">
                  <div class="tb-title">
                    <el-icon class="tb-ico-main"><OfficeBuilding /></el-icon>
                    <strong>已加载 {{ tenants.length }} 家公司</strong>
                    <el-tooltip content="刷新" placement="top">
                      <el-button :underline="false" link size="small" :loading="loadingTenants" @click="loadTenants">
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <el-link :underline="false" type="primary" size="small" @click="showTenantBanner = !showTenantBanner">
                    {{ showTenantBanner ? '收起' : '展开' }}
                  </el-link>
                </div>
              </template>
              <div v-if="showTenantBanner" class="tb-list">
                <div v-for="t in tenants" :key="t.id" class="tb-item" @click="fillTenant(t)">
                  <el-avatar :size="32" :style="`background: ${colorOf(t.id)}`" class="tb-ico">
                    {{ t.tenantName?.charAt(0) || '?' }}
                  </el-avatar>
                  <div class="tb-meta">
                    <div class="tb-name">{{ t.tenantName }}</div>
                    <div class="tb-code">{{ t.tenantCode }} · id={{ t.id }}</div>
                  </div>
                  <el-icon class="tb-arrow"><ArrowRight /></el-icon>
                </div>
              </div>
            </el-card>

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

            <!-- 多组横排 -->
            <transition-group name="row" tag="div" class="grid-rows">
              <el-card
                v-for="(g, idx) in groups"
                :key="g.id"
                shadow="never"
                class="grid-row"
                :class="{
                  'row-admin': g.username.toLowerCase() === 'admin',
                  'row-locked': g.lockedUntil && Date.now() < g.lockedUntil
                }"
              >
                <div class="row-grid">
                  <span class="col-idx row-idx">#{{ idx + 1 }}</span>

                  <div class="col-name">
                    <el-input
                      v-model="g.username"
                      placeholder="用户名"
                      :prefix-icon="User"
                      size="default"
                      clearable
                      @blur="recheckAdmin(g)"
                    />
                  </div>

                  <div class="col-name">
                    <el-input
                      v-model="g.password"
                      type="password"
                      placeholder="密码"
                      show-password
                      :prefix-icon="Lock"
                      size="default"
                    />
                  </div>

                  <div class="col-tenant">
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
                        <span style="float:left">{{ t.tenantName }}</span>
                        <small class="text-muted" style="float:right">{{ t.tenantCode }}</small>
                      </el-option>
                    </el-select>
                    <el-tag v-else type="warning" effect="plain" class="admin-tag">
                      <el-icon><Avatar /></el-icon>
                      全部公司
                    </el-tag>
                  </div>

                  <div class="col-act">
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
                      :underline="false"
                      link
                      size="default"
                      class="del-btn"
                      @click="removeRow(g.id)"
                    >
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </div>
                </div>

                <!-- 结果行: 用 el-alert -->
                <el-alert
                  v-if="g.result"
                  :type="alertType(g.result.kind)"
                  :title="g.result.text"
                  :description="g.result.detail"
                  :closable="false"
                  show-icon
                  class="result-alert"
                >
                  <template #default>
                    <div class="alert-content">
                      <div class="alert-meta">
                        <strong>{{ g.result.text }}</strong>
                        <small v-if="g.result.detail" class="alert-detail">{{ g.result.detail }}</small>
                      </div>
                      <div class="alert-actions">
                        <el-tag v-if="g.result.kind === 'error' && g.failCount" type="warning" size="small">
                          失败 {{ g.failCount }}/5
                        </el-tag>
                        <el-button
                          v-if="g.result.kind === 'error' && g.failCount < 5"
                          type="warning"
                          size="small"
                          plain
                          @click="onSend(g)"
                        >
                          重试
                        </el-button>
                        <el-button
                          v-if="g.result.kind === 'success' && g.result.token"
                          type="primary"
                          size="small"
                          @click="useTokenAndEnter(g)"
                        >
                          进入系统 →
                        </el-button>
                      </div>
                    </div>
                  </template>
                </el-alert>
              </el-card>
            </transition-group>

            <!-- 工具栏 -->
            <div class="toolbar">
              <el-button :underline="false" @click="addRow" type="primary" plain>
                <el-icon><Plus /></el-icon>
                增加一组
              </el-button>
              <el-button :underline="false" @click="batchSend" :loading="batchSending" :disabled="!groups.length">
                <el-icon><VideoPlay /></el-icon>
                一键发送
              </el-button>
              <el-button :underline="false" @click="clearAll" link>
                <el-icon><Delete /></el-icon>
                清空
              </el-button>
              <div class="spacer"></div>
              <el-tag size="small" effect="plain" type="info">
                总数 <strong>{{ groups.length }}</strong>
                · 已登录 <strong class="color-ok">{{ successCount }}</strong>
                · 失败 <strong class="color-err">{{ errorCount }}</strong>
              </el-tag>
            </div>

            <!-- 快捷账号 -->
            <el-card shadow="never" class="quick-card">
              <template #header>
                <div class="quick-head">
                  <span class="quick-tip">3 个快速账号 (点击填入):</span>
                </div>
              </template>
              <div class="quick-row">
                <el-button
                  v-for="q in quickAccounts"
                  :key="q.user"
                  :underline="false"
                  class="chip"
                  @click="quickFill(q.user, q.pass, q.tenant)"
                >
                  <el-avatar :size="32" :class="['chip-avatar', q.cls]">{{ q.user.charAt(0).toUpperCase() }}</el-avatar>
                  <div class="chip-meta">
                    <div class="chip-name">{{ q.user }} / {{ q.pass }}</div>
                    <div class="chip-dept">{{ q.dept }}</div>
                  </div>
                </el-button>
              </div>
              <div class="remember-row">
                <el-checkbox v-model="rememberMe" @change="onRememberChange">7 天内自动登录</el-checkbox>
                <el-link :underline="false" type="primary" size="small" @click="showAudit = true">
                  <el-icon><Document /></el-icon>
                  查看最近登录
                </el-link>
              </div>
            </el-card>
          </div>

          <!-- ===== SSO ===== -->
          <div v-else-if="activeTab === 'sso'" key="sso" class="step-panel">
            <el-page-header :icon="null" class="panel-hd">
              <template #content>
                <h2 class="panel-title">企业 SSO</h2>
              </template>
            </el-page-header>
            <p class="panel-tip">使用企业账号 (OIDC / LDAP / 飞书 / 钉钉) 登录</p>
            <div class="sso-grid">
              <el-button
                v-for="s in ssoList"
                :key="s.key"
                :underline="false"
                class="sso-btn"
                @click="onSsoLogin(s.key)"
              >
                <el-avatar :size="40" :style="`background: linear-gradient(135deg, ${s.c1}, ${s.c2})`" class="sso-ico">
                  {{ s.ch }}
                </el-avatar>
                <div class="sso-name">{{ s.name }}</div>
                <div class="sso-sub">{{ s.sub }}</div>
              </el-button>
            </div>
            <el-alert type="info" :closable="false" show-icon class="sso-note">
              <template #title>企业 SSO 接入请联系系统管理员配置 client_id / redirect_uri</template>
            </el-alert>
          </div>

          <!-- ===== 访客 ===== -->
          <div v-else-if="activeTab === 'guest'" key="guest" class="step-panel">
            <el-page-header :icon="null" class="panel-hd">
              <template #content>
                <h2 class="panel-title">访客体验</h2>
              </template>
            </el-page-header>
            <p class="panel-tip">无需账号，1 分钟快速浏览平台功能（只读沙箱）</p>
            <el-card shadow="never" class="guest-card">
              <div class="guest-ico">👀</div>
              <div class="guest-title">Demo Sandbox</div>
              <p class="guest-desc">
                预置 AI 助手、示例知识库、3 个示例多 Agent 案例。<br/>
                可聊天但不能保存数据，刷新即清空。
              </p>
              <el-button type="primary" size="large" round @click="onGuestLogin">
                进入沙箱 →
              </el-button>
            </el-card>
            <el-alert type="warning" :closable="false" show-icon class="guest-warn">
              <template #title>访客模式仅供评估用，生产数据请用正式账号登录</template>
            </el-alert>
          </div>
        </transition>
      </section>
    </div>

    <!-- 调试日志条 (用 el-affix 钉底部) -->
    <el-affix position="bottom" :offset="20" class="debug-affix">
      <el-card
        v-if="logs.length"
        shadow="hover"
        class="debug-bar"
        :class="{ expanded: debugExpanded }"
        @click="debugExpanded = !debugExpanded"
      >
        <template #header>
          <div class="db-head">
            <el-icon class="db-ico"><Tools /></el-icon>
            <strong>请求日志</strong>
            <el-badge :value="logs.length" :max="99" class="db-badge" />
            <el-icon :class="{ flip: !debugExpanded }" class="db-arrow"><ArrowDown /></el-icon>
          </div>
        </template>
        <div v-if="debugExpanded" class="db-body" @click.stop>
          <el-empty v-if="!logs.length" :image-size="40" description="暂无日志" />
          <article v-for="(l, i) in logs.slice(0, 30)" :key="i" class="db-item" :class="l.kind">
            <span class="db-time">{{ l.time }}</span>
            <el-tag size="small" :type="logTag(l.kind)" effect="dark" class="db-tag">{{ l.tag }}</el-tag>
            <span class="db-msg">{{ l.msg }}</span>
          </article>
        </div>
      </el-card>
    </el-affix>

    <!-- 最近登录弹窗: 用 el-descriptions + el-table + el-statistic -->
    <el-dialog v-model="showAudit" title="最近登录记录" width="820px" :align-center="true">
      <el-row :gutter="12" class="audit-stats">
        <el-col :span="6" v-for="s in auditStatList" :key="s.key">
          <el-card shadow="never" class="as-card" :style="`background: linear-gradient(135deg, ${s.c1}, ${s.c2})`">
            <el-statistic :value="s.value" :title="s.label" :title-style="{ color: '#fff', opacity: 0.9, fontSize: '12px' }" :value-style="{ color: '#fff', fontSize: '24px', fontWeight: 700 }" />
          </el-card>
        </el-col>
      </el-row>
      <el-table :data="auditRecords" v-loading="loadingAudit" stripe size="small" max-height="340">
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="IP" width="140">
          <template #default="{ row }">
            <code class="ip">{{ row.loginIp || '—' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.loginStatus)" size="small" effect="dark">{{ row.loginStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <small class="text-muted">{{ row.loginTime }}</small>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-link :underline="false" type="primary" @click="loadAudit">
          <el-icon><Refresh /></el-icon>刷新
        </el-link>
        <el-button @click="showAudit = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 帮助弹窗: 用 el-descriptions + el-collapse -->
    <el-dialog v-model="showHelp" title="登录帮助" width="600px" :align-center="true">
      <el-descriptions title="📋 默认账号" :column="3" border size="small">
        <el-descriptions-item label="用户名">admin</el-descriptions-item>
        <el-descriptions-item label="密码">admin123</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag type="danger" size="small">超级管理员</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户名">demo</el-descriptions-item>
        <el-descriptions-item label="密码">demo123</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag type="success" size="small">用户 / 默认公司</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户名">manager</el-descriptions-item>
        <el-descriptions-item label="密码">demo123</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag type="success" size="small">用户 / 示例科技</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="help-h3">❓ 常见问题</h3>
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
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Lock, OfficeBuilding, Avatar, Promotion, Close, Plus, VideoPlay, Delete,
  Refresh, CircleCheckFilled, CircleCloseFilled, Loading, ArrowDown, ArrowRight, Document,
  QuestionFilled, WarningFilled, Sunny, Moon, MagicStick, Clock, Tools
} from '@element-plus/icons-vue'
import { authApi, auditApi } from '@/api'

const router = useRouter()

// ============== Tab (用 el-segmented) ==============
const tabOptions = [
  { label: '🔑 账号登录', value: 'account' },
  { label: '🏢 企业 SSO', value: 'sso' },
  { label: '👀 访客体验', value: 'guest' }
]
const activeTab = ref('account')

// ============== 多组登录 ==============
let _id = 0
const makeRow = (u = '', p = '', t = null) => ({
  id: ++_id, username: u, password: p, tenantId: t,
  sending: false, result: null, failCount: 0, lockedUntil: 0
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
const recheckAdmin = (g) => { if (g.username.toLowerCase() === 'admin') g.tenantId = null }
const clearAll = () => { groups.splice(0, groups.length, makeRow()); logs.splice(0, logs.length) }

const quickAccounts = [
  { user: 'admin',   pass: 'admin123', tenant: null, dept: '超级管理员 · 公司留空', cls: 'admin' },
  { user: 'demo',    pass: 'demo123',  tenant: 1,    dept: '市场部 · 默认公司',     cls: 'demo' },
  { user: 'manager', pass: 'demo123',  tenant: 2,    dept: '运营部 · 示例科技',     cls: 'mgr' }
]
const quickFill = (u, p, t) => {
  groups[0].username = u
  groups[0].password = p
  groups[0].tenantId = t
  ElMessage.success(`已填入 ${u}`)
}

// ============== 公司 ==============
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
const logTag = (k) => ({ success: 'success', error: 'danger', warn: 'warning' })[k] || 'info'

// ============== 发送请求 ==============
const alertType = (k) => ({ success: 'success', error: 'error', pending: 'warning' })[k] || 'info'
const onSend = async (g) => {
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
    if (g.failCount >= 5) g.lockedUntil = Date.now() + 30000
    g.result = {
      kind: 'error',
      text: `❌ 登录失败 (${dt}ms)`,
      detail: `HTTP ${status || 'ERR'} · ${body?.message || e.message} · 已记录到 sys_login_audit`
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

// ============== 记住 ==============
const rememberMe = ref(false)
const onRememberChange = (v) => {
  if (v && groups[0].username) localStorage.setItem('remember_username', groups[0].username)
  else localStorage.removeItem('remember_username')
}

// ============== 审计 ==============
const showAudit = ref(false)
const auditStats = ref({})
const auditRecords = ref([])
const loadingAudit = ref(false)
const tagType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', LOCKED: 'warning' })[s] || 'info'
const auditStatList = computed(() => [
  { key: 'total', value: auditStats.value.total || 0, label: '总记录', c1: '#6366f1', c2: '#4f46e5' },
  { key: 'ok', value: auditStats.value.todaySuccess || 0, label: '今日成功', c1: '#10b981', c2: '#047857' },
  { key: 'err', value: auditStats.value.todayFailed || 0, label: '今日失败', c1: '#ef4444', c2: '#b91c1c' },
  { key: 'lock', value: auditStats.value.todayLocked || 0, label: '今日锁定', c1: '#f59e0b', c2: '#d97706' }
])
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

// ============== 时钟 / 系统 ==============
const currentTime = ref(new Date().toLocaleString('zh-CN'))
const systemInfo = ref('检测中…')
const loadingHealth = ref(false)
let clockTimer = null
let sysTimer = null
const checkSystem = async () => {
  loadingHealth.value = true
  try {
    const r = await fetch('/api/auth/health', { method: 'GET' })
    systemInfo.value = r.ok ? '服务正常' : '部分服务异常'
  } catch {
    systemInfo.value = '离线模式'
  }
  loadingHealth.value = false
}

// ============== 品牌 ==============
const features = [
  { icon: '⚡', text: 'ReAct 多智能体编排 + 联网搜索' },
  { icon: '🧠', text: '本地训练（Transformer）+ ONNX 推理' },
  { icon: '📚', text: '知识库 RAG（ES 8 + Tika + 查询改写）' },
  { icon: '🔗', text: 'Seata 分布式事务 + Nacos 服务发现' },
  { icon: '🏢', text: '多公司（租户）+ 部门归属 + 网关统一鉴权' }
]

// ============== 背景 ==============
const canvas = ref(null)
let rafId = null
const showHelp = ref(false)

onMounted(() => {
  if (localStorage.getItem('remember_username')) {
    groups[0].username = localStorage.getItem('remember_username')
    rememberMe.value = true
  }
  loadTenants()
  checkSystem()
  sysTimer = setInterval(checkSystem, 30000)
  clockTimer = setInterval(() => { currentTime.value = new Date().toLocaleString('zh-CN') }, 1000)
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

/* ===== 顶栏 (el-affix) ===== */
.topbar-affix { z-index: 50; }
.topbar-info {
  display: flex; justify-content: space-between; align-items: center;
  height: 36px; padding: 0 24px;
  background: rgba(15, 23, 42, 0.4); backdrop-filter: blur(12px);
  color: #cbd5e1; font-size: 12px;
}
.tb-left, .tb-right { display: flex; align-items: center; gap: 10px; }
.tb-left .el-tag { background: rgba(16, 185, 129, 0.15) !important; border-color: rgba(16, 185, 129, 0.3) !important; color: #6ee7b7 !important; }
.dot-icon { margin-right: 4px; }
.tb-btn { color: #cbd5e1 !important; padding: 4px; }

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
.logo-version { font-size: 11px; padding: 4px 10px; }
.brand-title { font-size: 28px; font-weight: 800; margin: 0 0 8px; letter-spacing: -0.5px; }
.brand-sub { font-size: 13px; opacity: 0.85; margin: 0 0 32px; }
.feature-list { list-style: none; padding: 0; margin: 0; }
.feature-list li { display: flex; align-items: center; gap: 12px; padding: 9px 0; font-size: 13px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); }
.feature-list li:last-child { border-bottom: none; }
.feat-ico { background: rgba(255, 255, 255, 0.18) !important; font-size: 14px; }
.brand-foot { margin-top: 28px; display: flex; align-items: center; gap: 8px; font-size: 12px; opacity: 0.85; }

/* 右 form */
.form-panel { flex: 1; padding: 24px 28px; display: flex; flex-direction: column; position: relative; min-height: 0; overflow-y: auto; }

.login-segmented { margin-bottom: 12px; }
.login-segmented :deep(.el-segmented__group) { background: #f3f4f6; }
.login-segmented :deep(.el-segmented__item) { font-weight: 600; }

.step-panel { flex: 1; }
.panel-hd { margin-bottom: 4px; }
.panel-title { font-size: 20px; font-weight: 700; color: #1e1b4b; margin: 0; }
.panel-tip { font-size: 11px; color: #6b7280; margin: 4px 0 12px; }
.panel-tip code { background: #ede9fe; padding: 1px 6px; border-radius: 3px; color: #5b21b6; font-size: 10px; }
.panel-tip strong { color: #7c3aed; }

/* ===== 公司 banner (el-card) ===== */
.tenant-card { margin-bottom: 10px; }
.tb-head { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.tb-title { display: flex; align-items: center; gap: 6px; }
.tb-ico-main { color: #6366f1; font-size: 14px; }
.tb-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 6px; }
.tb-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 8px;
  background: #fff; border: 1px solid #e5e7eb;
  cursor: pointer; transition: all 0.15s;
}
.tb-item:hover { border-color: #8b5cf6; transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(139, 92, 246, 0.2); }
.tb-ico { color: #fff; font-weight: 700; font-size: 12px; flex-shrink: 0; }
.tb-meta { flex: 1; min-width: 0; }
.tb-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.tb-code { font-size: 10px; color: #94a3b8; }
.tb-arrow { color: #cbd5e1; transition: all 0.15s; }
.tb-item:hover .tb-arrow { color: #8b5cf6; transform: translateX(2px); }

/* ===== 网格 ===== */
.grid-head, .row-grid {
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

.grid-rows { display: flex; flex-direction: column; gap: 6px; }
.grid-row { border: 1.5px solid #e5e7eb; }
.grid-row:hover { border-color: #a5b4fc; box-shadow: 0 2px 8px -2px rgba(99, 102, 241, 0.15); }
.grid-row.row-admin { background: linear-gradient(90deg, #faf5ff, #fff); border-color: #c4b5fd; }
.grid-row.row-locked { background: linear-gradient(90deg, #fef2f2, #fff); border-color: #fca5a5; opacity: 0.6; }
.row-grid { padding: 4px 0; }
.row-idx { font-weight: 700; color: #6366f1; }

.admin-tag { font-weight: 600; }
.tenant-sel { width: 100%; }

.cell-input :deep(.el-input__wrapper) {
  border: 1.5px solid #e5e7eb !important;
  border-radius: 8px !important;
  background: #fff !important;
  box-shadow: none !important;
  padding: 1px 10px;
}
.cell-input :deep(.el-input__wrapper.is-focus) {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12) !important;
}

.del-btn { color: #9ca3af; }
.del-btn:hover { color: #ef4444; }

/* 结果行 (el-alert) */
.result-alert { margin-top: 6px; }
.result-alert :deep(.el-alert__content) { padding: 4px 0; }
.result-alert :deep(.el-alert__title) { font-size: 12px; }
.result-alert :deep(.el-alert__description) { font-size: 11px; }
.alert-content { display: flex; justify-content: space-between; align-items: center; gap: 8px; width: 100%; }
.alert-meta { display: flex; flex-direction: column; gap: 2px; }
.alert-detail { color: inherit; opacity: 0.85; }
.alert-actions { display: flex; gap: 6px; align-items: center; flex-shrink: 0; }

/* ===== 工具栏 ===== */
.toolbar { display: flex; gap: 6px; align-items: center; margin-top: 10px; padding: 0 2px; }
.toolbar .spacer { flex: 1; }

/* ===== 快捷账号 (el-card) ===== */
.quick-card { margin-top: 12px; }
.quick-head { width: 100%; }
.quick-tip { font-size: 11px; color: #9ca3af; }
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.chip {
  display: flex !important; align-items: center; gap: 8px;
  padding: 7px 9px !important; border-radius: 9px !important;
  border: 1.5px solid #e5e7eb !important; background: #fff !important;
  height: auto !important; justify-content: flex-start !important;
}
.chip:hover { border-color: #a5b4fc !important; transform: translateY(-1px); box-shadow: 0 4px 10px -4px rgba(99, 102, 241, 0.2); }
.chip-avatar { color: #fff !important; font-weight: 700 !important; font-size: 12px !important; }
.chip-avatar.admin { background: linear-gradient(135deg, #6366f1, #8b5cf6) !important; }
.chip-avatar.demo { background: linear-gradient(135deg, #ec4899, #f43f5e) !important; }
.chip-avatar.mgr  { background: linear-gradient(135deg, #10b981, #06b6d4) !important; }
.chip-meta { flex: 1; min-width: 0; text-align: left; }
.chip-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.chip-dept { font-size: 10px; color: #6b7280; margin-top: 1px; }

.remember-row { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }

/* SSO / Guest */
.sso-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.sso-btn {
  display: flex !important; flex-direction: column; align-items: center; gap: 4px;
  padding: 14px 10px !important; border-radius: 12px !important;
  border: 1.5px solid #e5e7eb !important; background: #fff !important;
  height: auto !important;
}
.sso-btn:hover { border-color: #a5b4fc !important; transform: translateY(-2px); box-shadow: 0 6px 16px -4px rgba(99, 102, 241, 0.2); }
.sso-ico { color: #fff; font-size: 16px; font-weight: 700; }
.sso-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.sso-sub { font-size: 10px; color: #6b7280; }
.sso-note { margin-top: 14px; }
.sso-note :deep(.el-alert__title) { font-size: 11px; }

.guest-card { margin-bottom: 14px; text-align: center; background: linear-gradient(135deg, #fef3c7, #fce7f3); }
.guest-ico { font-size: 44px; }
.guest-title { font-size: 17px; font-weight: 700; color: #1e1b4b; margin: 6px 0; }
.guest-desc { font-size: 12px; color: #4b5563; line-height: 1.6; margin: 0 0 16px; }
.guest-warn :deep(.el-alert__title) { font-size: 11px; }

/* ================================================== */
/* 调试日志 (el-affix + el-card)                    */
/* ================================================== */
.debug-affix { z-index: 40; max-width: 460px; }
.debug-bar { transition: max-height 0.3s; cursor: pointer; }
.debug-bar.expanded :deep(.el-card__body) { max-height: 280px; overflow-y: auto; }
.db-head { display: flex; align-items: center; gap: 8px; width: 100%; }
.db-head .db-ico { color: #6366f1; }
.db-badge { margin-left: 8px; }
.db-arrow { margin-left: auto; transition: transform 0.3s; }
.db-arrow.flip { transform: rotate(180deg); }
.db-body { font-family: 'SF Mono', monospace; padding: 0 4px; }
.db-item { display: flex; gap: 6px; padding: 2px 0; line-height: 1.5; }
.db-time { color: #94a3b8; font-size: 10px; flex-shrink: 0; }
.db-tag { flex-shrink: 0; font-size: 10px !important; min-width: 50px; text-align: center; }
.db-msg { color: #334155; font-size: 11px; word-break: break-all; }

/* ================================================== */
/* 审计弹窗 (el-statistic)                          */
/* ================================================== */
.audit-stats { margin-bottom: 12px; }
.as-card { border: none !important; }
.as-card :deep(.el-card__body) { padding: 12px 16px; }
.ip { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-family: monospace; font-size: 11px; }

/* 帮助 */
.help-h3 { font-size: 14px; margin: 16px 0 8px; color: #1e1b4b; }

/* 步骤切换 */
.step-enter-active, .step-leave-active { transition: all 0.25s ease; }
.step-enter-from { opacity: 0; transform: translateX(20px); }
.step-leave-to { opacity: 0; transform: translateX(-20px); }

.row-enter-active, .row-leave-active { transition: all 0.25s ease; }
.row-enter-from, .row-leave-to { opacity: 0; transform: translateX(-20px); }

@media (max-width: 900px) {
  .login-shell { flex-direction: column; min-height: auto; }
  .brand-panel { flex: 0 0 auto; padding: 24px; }
  .feature-list { display: none; }
  .form-panel { padding: 20px; }
  .grid-head, .row-grid { grid-template-columns: 24px 1fr 1fr; }
  .col-tenant, .col-act { grid-column: 1 / -1; }
  .topbar-info { font-size: 11px; padding: 0 12px; }
}
</style>
