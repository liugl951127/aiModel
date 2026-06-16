<template>
  <div class="login-page">
    <!-- 顶部工具条 (桌面端) -->
    <header class="topbar" v-if="!isMobile">
      <div class="tb-left"></div>
      <div class="tb-right">
        <el-dropdown trigger="click" @command="onLang">
          <el-link :underline="false" class="tb-link">
            <el-icon><Position /></el-icon>
            {{ currentLang.label }}
            <el-icon><ArrowDown /></el-icon>
          </el-link>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="l in langs" :key="l.code" :command="l.code" :disabled="l.code === currentLangCode">
                <el-icon v-if="l.code === currentLangCode"><Check /></el-icon>
                {{ l.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-divider direction="vertical" />
        <el-tooltip content="主题" placement="bottom">
          <el-link :underline="false" class="tb-link" @click="cycleTheme">
            <el-icon :size="14"><component :is="themeIcon" /></el-icon>
          </el-link>
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-tooltip content="登录审计" placement="bottom">
          <el-link :underline="false" class="tb-link" @click="showAudit = true">
            <el-icon :size="14"><Document /></el-icon>
          </el-link>
        </el-tooltip>
      </div>
    </header>

    <div class="main" :class="{ mobile: isMobile, tablet: isTablet }">
      <!-- ============== 左品牌区 ============== -->
      <section class="brand-panel" v-if="!isMobile">
        <div class="brand-content">
          <!-- Logo + 品牌名 -->
          <div class="brand-head">
            <div class="logo">
              <span class="logo-mark">L</span>
              <div class="logo-text">
                <strong>LAIYE</strong>
                <small>智能自动化平台</small>
              </div>
            </div>
          </div>

          <!-- 插画 (SVG inline) -->
          <div class="illustration">
            <svg viewBox="0 0 500 400" xmlns="http://www.w3.org/2000/svg" class="illu-svg">
              <!-- 背景圆 + 弧线 -->
              <circle cx="120" cy="200" r="130" fill="rgba(99,102,241,0.08)" />
              <path d="M 0 350 Q 250 280 500 350" stroke="rgba(99,102,241,0.15)" stroke-width="2" fill="none" />
              <path d="M 50 380 Q 280 320 480 380" stroke="rgba(99,102,241,0.1)" stroke-width="1.5" fill="none" stroke-dasharray="4 4" />

              <!-- 仪表盘背景 -->
              <rect x="60" y="120" width="180" height="130" rx="8" fill="white" stroke="rgba(99,102,241,0.2)" stroke-width="1.5" />
              <rect x="70" y="135" width="60" height="8" rx="2" fill="rgba(99,102,241,0.4)" />
              <rect x="70" y="150" width="100" height="6" rx="2" fill="rgba(99,102,241,0.2)" />
              <line x1="70" y1="180" x2="225" y2="180" stroke="rgba(99,102,241,0.1)" />
              <!-- 柱状图 -->
              <rect x="80" y="210" width="18" height="30" rx="2" fill="#6366f1" />
              <rect x="105" y="195" width="18" height="45" rx="2" fill="#8b5cf6" />
              <rect x="130" y="180" width="18" height="60" rx="2" fill="#a78bfa" />
              <rect x="155" y="200" width="18" height="40" rx="2" fill="#c4b5fd" />
              <rect x="180" y="220" width="18" height="20" rx="2" fill="#ddd6fe" />
              <circle cx="89" cy="170" r="8" fill="#fbbf24" />
              <circle cx="89" cy="170" r="3" fill="white" />

              <!-- 圆盘 -->
              <circle cx="350" cy="180" r="50" fill="white" stroke="rgba(99,102,241,0.2)" stroke-width="1.5" />
              <circle cx="350" cy="180" r="32" fill="none" stroke="rgba(99,102,241,0.3)" stroke-width="2" stroke-dasharray="60 20" />
              <circle cx="350" cy="180" r="16" fill="rgba(99,102,241,0.15)" />
              <circle cx="350" cy="180" r="6" fill="#6366f1" />

              <!-- 人 (简化) -->
              <g transform="translate(220 180)">
                <!-- 头 -->
                <circle cx="0" cy="0" r="20" fill="#fde68a" />
                <!-- 身体 -->
                <path d="M -25 30 Q -25 18 0 18 Q 25 18 25 30 L 30 95 L -30 95 Z" fill="#3b82f6" />
                <!-- 胳膊 -->
                <path d="M -25 30 Q -40 50 -38 70" stroke="#3b82f6" stroke-width="10" fill="none" stroke-linecap="round" />
                <path d="M 25 30 Q 45 40 50 25" stroke="#3b82f6" stroke-width="10" fill="none" stroke-linecap="round" />
                <!-- 腿 -->
                <path d="M -10 95 L -12 130" stroke="#1e40af" stroke-width="14" fill="none" stroke-linecap="round" />
                <path d="M 10 95 L 12 130" stroke="#1e40af" stroke-width="14" fill="none" stroke-linecap="round" />
                <!-- 头发 -->
                <path d="M -18 -10 Q -20 -25 0 -25 Q 20 -25 18 -10 L 15 -5 L -15 -5 Z" fill="#1e293b" />
              </g>

              <!-- 椅子 -->
              <rect x="190" y="285" width="6" height="50" fill="rgba(99,102,241,0.4)" />
              <rect x="240" y="285" width="6" height="50" fill="rgba(99,102,241,0.4)" />
              <rect x="190" y="280" width="60" height="8" rx="3" fill="rgba(99,102,241,0.5)" />
              <!-- 椅子底座 -->
              <ellipse cx="220" cy="335" rx="35" ry="5" fill="rgba(99,102,241,0.2)" />
              <line x1="220" y1="335" x2="220" y2="305" stroke="rgba(99,102,241,0.4)" stroke-width="3" />
            </svg>
          </div>

          <!-- 副标题 -->
          <p class="brand-tag">大模型 · 智能体 · 分布式事务 一体化</p>
        </div>
      </section>

      <!-- ============== 右表单区 ============== -->
      <section class="form-panel">
        <div class="form-card">
          <!-- 移动端 brand 头 -->
          <div class="mobile-brand" v-if="isMobile">
            <div class="logo">
              <span class="logo-mark">L</span>
              <div class="logo-text">
                <strong>LAIYE</strong>
                <small>智能自动化平台</small>
              </div>
            </div>
            <el-dropdown trigger="click" @command="onLang">
              <el-link :underline="false" class="tb-link">
                <el-icon><Position /></el-icon>
                {{ currentLang.label }}
              </el-link>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="l in langs" :key="l.code" :command="l.code" :disabled="l.code === currentLangCode">
                    {{ l.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <!-- Tab 切换 -->
          <div class="login-tabs">
            <button v-for="t in tabs" :key="t.key" :class="['tab', { active: activeTab === t.key }]" @click="switchTab(t.key)">
              {{ t.label }}
            </button>
          </div>

          <transition name="step" mode="out-in">
            <!-- ===== 登录 ===== -->
            <div v-if="activeTab === 'login'" key="login" class="tab-panel">
              <h2 class="panel-title">登录</h2>
              <p class="panel-tip">使用账号密码登录到 AI Agent Platform</p>

              <el-form :model="loginForm" ref="loginFormRef" :rules="loginRules" size="large" class="form" label-position="top">
                <el-form-item prop="username">
                  <el-input
                    v-model="loginForm.username"
                    placeholder="用户名 / 手机号 / 邮箱"
                    :prefix-icon="User"
                    clearable
                  />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="loginForm.password"
                    type="password"
                    placeholder="密码"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <el-form-item prop="tenantId" v-if="!isSuperAdmin">
                  <el-select
                    v-model="loginForm.tenantId"
                    placeholder="选择公司 (租户)"
                    :loading="loadingTenants"
                    class="tenant-sel"
                    filterable
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
                </el-form-item>
                <el-alert v-if="isSuperAdmin" type="info" :closable="false" show-icon class="admin-hint">
                  <template #title>检测到 <strong>admin</strong>，将自动拥有所有租户权限</template>
                </el-alert>

                <div class="form-extras">
                  <el-checkbox v-model="loginForm.remember">7 天自动登录</el-checkbox>
                  <el-link type="primary" :underline="false" @click="onForgot">忘记密码？</el-link>
                </div>

                <el-button type="primary" :loading="loading" @click="onLogin" class="submit-btn" size="large" round>
                  登 录
                </el-button>

                <!-- 快捷账号 -->
                <div class="quick-row">
                  <button class="chip" @click="quickFill('admin', 'admin123', null)">
                    <span class="chip-avatar admin">A</span>
                    <div>
                      <div class="chip-name">admin</div>
                      <div class="chip-dept">超级管理员</div>
                    </div>
                  </button>
                  <button class="chip" @click="quickFill('demo', 'demo123', 1)">
                    <span class="chip-avatar demo">D</span>
                    <div>
                      <div class="chip-name">demo</div>
                      <div class="chip-dept">市场部</div>
                    </div>
                  </button>
                  <button class="chip" @click="quickFill('manager', 'demo123', 2)">
                    <span class="chip-avatar mgr">M</span>
                    <div>
                      <div class="chip-name">manager</div>
                      <div class="chip-dept">运营部</div>
                    </div>
                  </button>
                </div>
              </el-form>

              <p class="bottom-tip">
                没有账号？<el-link type="primary" :underline="false" @click="switchTab('register')">立即注册</el-link>
              </p>
            </div>

            <!-- ===== 注册 ===== -->
            <div v-else-if="activeTab === 'register'" key="register" class="tab-panel">
              <h2 class="panel-title">注册</h2>
              <p class="panel-tip">使用手机号注册, 通过验证后即可登录</p>

              <el-form :model="regForm" ref="regFormRef" :rules="regRules" size="large" class="form" label-position="top">
                <el-form-item prop="phone">
                  <el-input v-model="regForm.phone" placeholder="请输入手机号" :prefix-icon="Cellphone" clearable>
                    <template #prepend>
                      <el-select v-model="regForm.countryCode" style="width: 70px">
                        <el-option label="+86" value="+86" />
                        <el-option label="+1" value="+1" />
                        <el-option label="+852" value="+852" />
                      </el-select>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item prop="captcha">
                  <el-input v-model="regForm.captcha" placeholder="请输入验证码" :prefix-icon="Key" clearable>
                    <template #append>
                      <el-button
                        :disabled="captchaCountdown > 0"
                        :loading="sendingCaptcha"
                        @click="onSendCaptcha"
                        class="captcha-btn"
                      >
                        {{ captchaCountdown > 0 ? `${captchaCountdown}s 后重试` : '获取验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item prop="password">
                  <el-input v-model="regForm.password" type="password" placeholder="请输入密码 (至少 6 位)" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-form-item prop="confirmPassword">
                  <el-input v-model="regForm.confirmPassword" type="password" placeholder="请再次输入密码" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-form-item prop="agree" class="agree-item">
                  <el-checkbox v-model="regForm.agree">
                    我已阅读并同意
                    <el-link type="primary" :underline="false">《来也用户协议》</el-link>
                    与
                    <el-link type="primary" :underline="false">《隐私协议》</el-link>
                  </el-checkbox>
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="onRegister" class="submit-btn" size="large" round>
                  注 册
                </el-button>
              </el-form>

              <p class="bottom-tip">
                已有账号？<el-link type="primary" :underline="false" @click="switchTab('login')">立即登录</el-link>
              </p>
            </div>

            <!-- ===== 忘记密码 ===== -->
            <div v-else-if="activeTab === 'forgot'" key="forgot" class="tab-panel">
              <h2 class="panel-title">忘记密码</h2>
              <p class="panel-tip">通过手机验证码重置密码</p>
              <el-form :model="forgotForm" :rules="forgotRules" ref="forgotFormRef" size="large" class="form" label-position="top">
                <el-form-item prop="phone">
                  <el-input v-model="forgotForm.phone" placeholder="请输入注册时的手机号" :prefix-icon="Cellphone" />
                </el-form-item>
                <el-form-item prop="captcha">
                  <el-input v-model="forgotForm.captcha" placeholder="验证码" :prefix-icon="Key">
                    <template #append>
                      <el-button :disabled="forgotCountdown > 0" @click="onSendForgotCaptcha">
                        {{ forgotCountdown > 0 ? `${forgotCountdown}s` : '获取验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item prop="newPassword">
                  <el-input v-model="forgotForm.newPassword" type="password" placeholder="新密码 (至少 6 位)" show-password :prefix-icon="Lock" />
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="onResetPwd" class="submit-btn" size="large" round>
                  重置密码
                </el-button>
              </el-form>
              <p class="bottom-tip">
                想起密码了？<el-link type="primary" :underline="false" @click="switchTab('login')">返回登录</el-link>
              </p>
            </div>
          </transition>

          <!-- 结果行 -->
          <transition name="alert">
            <el-alert
              v-if="resultAlert"
              :type="resultAlert.type"
              :title="resultAlert.title"
              :description="resultAlert.desc"
              :closable="true"
              show-icon
              class="result-alert"
              @close="resultAlert = null"
            />
          </transition>
        </div>

        <!-- 版权 (PC 端显示) -->
        <footer class="page-footer" v-if="!isMobile">
          <span>Copyright © 2015-{{ new Date().getFullYear() }} AI Agent Platform. All Rights Reserved.</span>
        </footer>
      </section>
    </div>

    <!-- 移动端底部版权 -->
    <footer class="mobile-footer" v-if="isMobile">
      <span>Copyright © 2015-{{ new Date().getFullYear() }} AI Agent Platform</span>
    </footer>

    <!-- 审计弹窗 (PC 端) -->
    <el-dialog v-model="showAudit" title="最近登录记录" width="780px" :align-center="true" v-if="!isMobile">
      <el-row :gutter="12" class="audit-stats">
        <el-col :span="6" v-for="s in auditStatList" :key="s.key">
          <el-card shadow="never" class="as-card" :style="`background: linear-gradient(135deg, ${s.c1}, ${s.c2})`">
            <el-statistic :value="s.value" :title="s.label" :title-style="{ color: '#fff', opacity: 0.9, fontSize: '12px' }" :value-style="{ color: '#fff', fontSize: '22px', fontWeight: 700 }" />
          </el-card>
        </el-col>
      </el-row>
      <el-table :data="auditRecords" v-loading="loadingAudit" stripe size="small" max-height="320">
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column label="IP" width="120">
          <template #default="{ row }"><code class="ip">{{ row.loginIp || '—' }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="tagType(row.loginStatus)" size="small" effect="dark">{{ row.loginStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="时间" width="140">
          <template #default="{ row }"><small class="text-muted">{{ row.loginTime }}</small></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-link :underline="false" type="primary" @click="loadAudit"><el-icon><Refresh /></el-icon>刷新</el-link>
        <el-button @click="showAudit = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Lock, Cellphone, Key, Position, ArrowDown, Check, Document, Refresh,
  Sunny, Moon, MagicStick
} from '@element-plus/icons-vue'
import { authApi, auditApi } from '@/api'

const router = useRouter()

// ============== 响应式 (3 端) ==============
const isMobile = ref(false)
const isTablet = ref(false)
const checkViewport = () => {
  const w = window.innerWidth
  isMobile.value = w < 768
  isTablet.value = w >= 768 && w < 1024
}
let resizeTimer = null
onMounted(() => {
  checkViewport()
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(checkViewport, 100)
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', checkViewport)
  clearTimeout(resizeTimer)
  if (captchaTimer) clearInterval(captchaTimer)
  if (forgotTimer) clearInterval(forgotTimer)
})

// ============== 语言 ==============
const langs = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'zh-TW', label: '繁體中文' },
  { code: 'en-US', label: 'English' }
]
const currentLangCode = ref(localStorage.getItem('lang') || 'zh-CN')
const currentLang = computed(() => langs.find(l => l.code === currentLangCode.value) || langs[0])
const onLang = (code) => {
  currentLangCode.value = code
  localStorage.setItem('lang', code)
  ElMessage.success(`已切换到 ${langs.find(l => l.code === code)?.label}`)
}

// ============== 主题 ==============
const themes = ['light', 'dark', 'auto']
const themeIdx = ref(0)
const themeIcon = computed(() => [Sunny, Moon, MagicStick][themeIdx.value])
const cycleTheme = () => {
  themeIdx.value = (themeIdx.value + 1) % themes.length
  document.documentElement.dataset.theme = themes[themeIdx.value]
  ElMessage.success(`主题: ${themes[themeIdx.value]}`)
}

// ============== Tab ==============
const tabs = [
  { key: 'login', label: '登录' },
  { key: 'register', label: '注册' }
]
const activeTab = ref('login')
const switchTab = (k) => {
  activeTab.value = k
  resultAlert.value = null
}
const onForgot = () => { activeTab.value = 'forgot' }

// ============== 登录表单 ==============
const loginForm = reactive({ username: '', password: '', tenantId: null, remember: true })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请选择公司', trigger: 'change' }]
}
const loginFormRef = ref()

const isSuperAdmin = computed(() => loginForm.username.toLowerCase() === 'admin')

// ============== 注册表单 ==============
const regForm = reactive({ countryCode: '+86', phone: '', captcha: '', password: '', confirmPassword: '', agree: false })
const regRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不对', trigger: 'blur' }
  ],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入', trigger: 'blur' },
    {
      validator: (rule, value, cb) => value === regForm.password ? cb() : cb(new Error('两次密码不一致')),
      trigger: 'blur'
    }
  ],
  agree: [{ required: true, validator: (r, v, cb) => v ? cb() : cb(new Error('需同意协议')), trigger: 'change' }]
}
const regFormRef = ref()

// ============== 忘记密码 ==============
const forgotForm = reactive({ phone: '', captcha: '', newPassword: '' })
const forgotRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '至少 6 位', trigger: 'blur' }]
}
const forgotFormRef = ref()

// ============== 验证码倒计时 ==============
const captchaCountdown = ref(0)
const forgotCountdown = ref(0)
const sendingCaptcha = ref(false)
let captchaTimer = null
let forgotTimer = null
const startCountdown = (which) => {
  const target = which === 'captcha' ? captchaCountdown : forgotCountdown
  const timer = which === 'captcha' ? captchaTimer : forgotTimer
  target.value = 60
  if (timer) clearInterval(timer)
  const t = setInterval(() => {
    if (target.value <= 0) {
      clearInterval(t)
      if (which === 'captcha') captchaTimer = null
      else forgotTimer = null
    } else {
      target.value--
    }
  }, 1000)
  if (which === 'captcha') captchaTimer = t
  else forgotTimer = t
}

const onSendCaptcha = async () => {
  if (!/^1[3-9]\d{9}$/.test(regForm.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  sendingCaptcha.value = true
  try {
    // 实际项目应调 POST /api/auth/captcha/send
    await new Promise(r => setTimeout(r, 600))
    ElMessage.success(`验证码已发送 (演示: 123456)`)
    startCountdown('captcha')
  } finally {
    sendingCaptcha.value = false
  }
}
const onSendForgotCaptcha = async () => {
  if (!forgotForm.phone) {
    ElMessage.warning('请先输入手机号')
    return
  }
  await new Promise(r => setTimeout(r, 500))
  ElMessage.success(`验证码已发送 (演示: 123456)`)
  startCountdown('forgot')
}

// ============== 公司列表 ==============
const tenants = ref([])
const loadingTenants = ref(false)
const loadTenants = async () => {
  loadingTenants.value = true
  try {
    const resp = await authApi.tenants()
    tenants.value = resp.data || []
  } catch (e) {
    tenants.value = [
      { id: 1, tenantCode: 'default', tenantName: '默认公司' },
      { id: 2, tenantCode: 'demo-corp', tenantName: '示例科技公司' }
    ]
  } finally {
    loadingTenants.value = false
  }
}

// ============== 提交 ==============
const loading = ref(false)
const resultAlert = ref(null)

const onLogin = async () => {
  const form = loginFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch (e) {
    return
  }
  loading.value = true
  resultAlert.value = null

  const isAdmin = loginForm.username.toLowerCase() === 'admin'
  const tenantId = isAdmin ? null : (loginForm.tenantId || null)
  const t0 = performance.now()
  try {
    const resp = await authApi.login({
      username: loginForm.username,
      password: loginForm.password,
      tenantId
    })
    const dt = (performance.now() - t0).toFixed(0)
    const data = resp.data
    // 存 token
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('tenant_id', String(data.tenantId))
    localStorage.setItem('tenant_code', data.tenantCode || '')
    localStorage.setItem('tenant_name', data.tenantName || '')
    localStorage.setItem('department', data.department || '')
    localStorage.setItem('roles', JSON.stringify(data.roles || ['user']))
    if (loginForm.remember) {
      localStorage.setItem('remember_login', 'true')
      localStorage.setItem('remember_username', loginForm.username)
    }
    resultAlert.value = {
      type: 'success',
      title: '登录成功',
      desc: `${data.username} · ${data.tenantName} · ${dt}ms`
    }
    setTimeout(() => router.push('/dashboard'), 600)
  } catch (e) {
    const status = e?.response?.status
    const body = e?.response?.data
    resultAlert.value = {
      type: 'error',
      title: '登录失败',
      desc: `HTTP ${status || 'ERR'} · ${body?.message || e.message}`
    }
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  const form = regFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch (e) { return }
  loading.value = true
  // 实际应调 POST /api/auth/register
  await new Promise(r => setTimeout(r, 800))
  loading.value = false
  resultAlert.value = {
    type: 'success',
    title: '注册成功',
    desc: '请使用新账号登录'
  }
  setTimeout(() => { activeTab.value = 'login'; resultAlert.value = null }, 1000)
}

const onResetPwd = async () => {
  const form = forgotFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch (e) { return }
  loading.value = true
  await new Promise(r => setTimeout(r, 800))
  loading.value = false
  resultAlert.value = {
    type: 'success',
    title: '密码已重置',
    desc: '请用新密码登录'
  }
  setTimeout(() => { activeTab.value = 'login'; resultAlert.value = null }, 1000)
}

const quickFill = (u, p, t) => {
  loginForm.username = u
  loginForm.password = p
  loginForm.tenantId = t
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
    const [stats, page] = await Promise.all([auditApi.stats(), auditApi.page({ current: 1, size: 20 })])
    auditStats.value = stats.data || {}
    auditRecords.value = page.data?.records || []
  } catch (e) { /* ignore */ }
  loadingAudit.value = false
}
watch(showAudit, (v) => { if (v) loadAudit() })

onMounted(() => {
  loadTenants()
  if (localStorage.getItem('remember_login') === 'true' && localStorage.getItem('remember_username')) {
    loginForm.username = localStorage.getItem('remember_username')
  }
})
</script>

<style scoped>
/* ============== 页面骨架 ============== */
.login-page {
  position: fixed; inset: 0;
  display: flex; flex-direction: column;
  background: linear-gradient(135deg, #eef2ff 0%, #f0f9ff 50%, #fef3c7 100%);
  overflow: hidden;
}

/* ============== 顶部 (PC) ============== */
.topbar {
  display: flex; justify-content: space-between; align-items: center;
  height: 48px; padding: 0 32px; flex-shrink: 0;
  position: relative; z-index: 5;
}
.tb-left, .tb-right { display: flex; align-items: center; gap: 4px; }
.tb-link {
  display: flex; align-items: center; gap: 4px;
  font-size: 13px !important; color: #475569 !important;
  padding: 4px 8px;
}
.tb-link:hover { color: #6366f1 !important; }
:deep(.el-divider--vertical) { height: 14px; margin: 0 4px; }

/* ============== 主区 ============== */
.main {
  flex: 1; display: flex; min-height: 0;
  position: relative; z-index: 2;
}
.main.mobile { flex-direction: column; padding: 16px; overflow-y: auto; }
.main.tablet { flex-direction: column; padding: 24px; overflow-y: auto; }

/* ============== 左品牌 ============== */
.brand-panel {
  flex: 0 0 50%;
  display: flex; align-items: center; justify-content: center;
  padding: 32px;
  position: relative;
}
.brand-content {
  max-width: 520px; width: 100%;
  display: flex; flex-direction: column; gap: 32px;
}
.brand-head { display: flex; align-items: center; }
.logo {
  display: flex; align-items: center; gap: 12px;
}
.logo-mark {
  width: 56px; height: 56px; border-radius: 14px;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff; font-size: 32px; font-weight: 900;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
  letter-spacing: -1px;
}
.logo-text strong {
  font-size: 32px; font-weight: 900; color: #1e1b4b; letter-spacing: -1px;
}
.logo-text small {
  display: block; font-size: 14px; color: #475569; margin-top: 2px;
  letter-spacing: 1px;
}
.illustration {
  width: 100%; max-width: 500px; margin: 0 auto;
}
.illu-svg { width: 100%; height: auto; display: block; }
.brand-tag {
  text-align: center; font-size: 14px; color: #475569;
  margin: 0; letter-spacing: 0.5px;
}

/* ============== 右表单 ============== */
.form-panel {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 32px;
  position: relative;
}
.main.mobile .form-panel { padding: 0; }
.main.tablet .form-panel { padding: 0; }
.form-card {
  width: 100%; max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px 24px;
  box-shadow: 0 8px 40px -8px rgba(99, 102, 241, 0.12), 0 0 0 1px rgba(99, 102, 241, 0.05);
}
.main.mobile .form-card, .main.tablet .form-card {
  box-shadow: 0 4px 20px -4px rgba(0, 0, 0, 0.08);
}

/* 移动端 brand 头 (在卡片顶部) */
.mobile-brand {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid #e5e7eb;
}
.mobile-brand .logo-mark { width: 36px; height: 36px; font-size: 20px; border-radius: 10px; }
.mobile-brand .logo-text strong { font-size: 20px; }
.mobile-brand .logo-text small { font-size: 11px; }

/* Tab */
.login-tabs {
  display: flex; gap: 24px; margin-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
}
.tab {
  background: none; border: none; padding: 10px 4px;
  font-size: 16px; color: #64748b; cursor: pointer;
  position: relative; font-weight: 500;
  transition: all 0.18s;
}
.tab.active { color: #6366f1; font-weight: 700; }
.tab.active::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: -1px;
  height: 2px; background: #6366f1; border-radius: 2px;
}

.tab-panel { padding-top: 16px; }
.panel-title { font-size: 20px; font-weight: 700; color: #1e1b4b; margin: 0 0 4px; }
.panel-tip { font-size: 12px; color: #94a3b8; margin: 0 0 20px; }

/* Form */
.form { margin-top: 4px; }
.form :deep(.el-form-item) { margin-bottom: 16px; }
.form :deep(.el-form-item__label) { padding: 0 0 4px; font-size: 12px; color: #64748b; font-weight: 500; }
.form :deep(.el-input__wrapper) {
  background: #f8fafc; border-radius: 8px; padding: 4px 12px;
  box-shadow: 0 0 0 1px #e5e7eb inset !important;
  transition: all 0.18s;
}
.form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c7d2fe inset !important; }
.form :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px #6366f1 inset !important;
}
.form :deep(.el-input__inner) { font-size: 14px; }
.form :deep(.el-input__inner::placeholder) { color: #cbd5e1; }
.tenant-sel { width: 100%; }
.tenant-sel :deep(.el-select__wrapper) {
  background: #f8fafc; border-radius: 8px; padding: 4px 12px;
  box-shadow: 0 0 0 1px #e5e7eb inset !important;
}
.tenant-sel :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 2px #6366f1 inset !important;
}

.captcha-btn { width: 110px; font-size: 12px; }

.form-extras {
  display: flex; justify-content: space-between; align-items: center;
  margin: 8px 0 16px; font-size: 12px;
}
.form-extras :deep(.el-checkbox__label) { font-size: 12px; color: #64748b; }

.admin-hint { margin-bottom: 12px; }
.admin-hint :deep(.el-alert__title) { font-size: 12px; }

.submit-btn {
  width: 100%; height: 44px;
  font-size: 15px; font-weight: 600; letter-spacing: 4px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  border: none;
}
.submit-btn:hover { background: linear-gradient(135deg, #4f46e5, #4338ca); }

/* 快捷账号 */
.quick-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 16px; }
.chip {
  display: flex !important; align-items: center; gap: 8px;
  padding: 8px 10px !important; border-radius: 10px !important;
  border: 1.5px solid #e5e7eb !important; background: #fff !important;
  height: auto !important; justify-content: flex-start !important;
  cursor: pointer;
}
.chip:hover { border-color: #a5b4fc !important; transform: translateY(-1px); }
.chip-avatar {
  width: 28px; height: 28px; border-radius: 7px;
  color: #fff !important; font-weight: 700 !important; font-size: 12px !important;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.chip-avatar.admin { background: linear-gradient(135deg, #6366f1, #8b5cf6) !important; }
.chip-avatar.demo  { background: linear-gradient(135deg, #ec4899, #f43f5e) !important; }
.chip-avatar.mgr   { background: linear-gradient(135deg, #10b981, #06b6d4) !important; }
.chip-name { font-size: 12px; font-weight: 600; color: #1e293b; line-height: 1.2; }
.chip-dept { font-size: 10px; color: #94a3b8; margin-top: 1px; }

.bottom-tip {
  text-align: center; font-size: 13px; color: #64748b; margin: 16px 0 0;
}

/* 结果 alert */
.result-alert { margin-top: 12px; }
.result-alert :deep(.el-alert__title) { font-size: 13px; font-weight: 600; }
.result-alert :deep(.el-alert__description) { font-size: 12px; }

/* 协议 */
.agree-item :deep(.el-form-item__content) { margin-bottom: 0; }
.agree-item :deep(.el-checkbox__label) { font-size: 12px; color: #64748b; }

/* 版权 */
.page-footer {
  position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
  font-size: 12px; color: #94a3b8;
}
.mobile-footer {
  text-align: center; padding: 16px;
  font-size: 11px; color: #94a3b8; flex-shrink: 0;
}

/* 审计弹窗 */
.audit-stats { margin-bottom: 12px; }
.as-card { border: none !important; }
.as-card :deep(.el-card__body) { padding: 10px 14px; }
.ip { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-family: monospace; font-size: 11px; }
.text-muted { color: #94a3b8; }

/* 步骤切换 */
.step-enter-active, .step-leave-active { transition: all 0.25s ease; }
.step-enter-from { opacity: 0; transform: translateX(20px); }
.step-leave-to { opacity: 0; transform: translateX(-20px); }
.alert-enter-active, .alert-leave-active { transition: all 0.25s ease; }
.alert-enter-from, .alert-leave-to { opacity: 0; transform: translateY(-4px); }

@media (max-width: 1024px) {
  .topbar { padding: 0 16px; }
}
</style>
