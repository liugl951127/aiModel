<template>
  <div class="login-bg">
    <!-- 背景动效 -->
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

      <!-- 右：登录表单 -->
      <section class="form-panel">
        <!-- 顶 tab：登录方式 -->
        <div class="login-tabs">
          <button v-for="t in tabs" :key="t.key"
                  :class="['tab', { active: activeTab === t.key }]"
                  @click="switchTab(t.key)">
            <span class="tab-ico">{{ t.icon }}</span>
            <span>{{ t.label }}</span>
          </button>
        </div>

        <!-- 公共：3 步流程（账号 → 公司 → 密码） -->
        <transition name="step" mode="out-in">
          <!-- ============ 步骤 1：账号 ============ -->
          <div v-if="activeTab === 'account' && step === 0" key="s1" class="step-panel">
            <h2 class="panel-title">欢迎回来 👋</h2>
            <p class="panel-tip">输入账号，我们来帮你找可登录的公司</p>

            <el-form @keyup.enter="onCheckUser" :model="form" label-position="top" size="large">
              <el-form-item label="用户名 / 邮箱 / 手机号">
                <el-input
                  v-model="form.username"
                  placeholder="admin / demo / manager"
                  :prefix-icon="User"
                  autofocus
                  clearable
                />
              </el-form-item>
              <el-button type="primary" :loading="checking" class="primary-btn" @click="onCheckUser">
                下一步
              </el-button>
            </el-form>

            <div class="quick-fill">
              <p class="quick-tip">快速体验账号：</p>
              <div class="quick-row">
                <button class="chip" @click="quickFill('admin','admin123','技术部','默认公司',1)">
                  <span class="chip-avatar admin">A</span>
                  <div class="chip-meta">
                    <div class="chip-name">admin / admin123</div>
                    <div class="chip-dept">技术部 · 平台管理员</div>
                  </div>
                </button>
                <button class="chip" @click="quickFill('demo','demo123','市场部','默认公司',1)">
                  <span class="chip-avatar demo">D</span>
                  <div class="chip-meta">
                    <div class="chip-name">demo / demo123</div>
                    <div class="chip-dept">市场部 · 普通用户</div>
                  </div>
                </button>
                <button class="chip" @click="quickFill('manager','demo123','运营部','示例科技公司',2)">
                  <span class="chip-avatar mgr">M</span>
                  <div class="chip-meta">
                    <div class="chip-name">manager / demo123</div>
                    <div class="chip-dept">运营部 · 子公司</div>
                  </div>
                </button>
              </div>
            </div>

            <div class="foot-tip">
              <span>没账号？</span>
              <el-link type="primary" :underline="false">联系企业管理员</el-link>
            </div>
          </div>

          <!-- ============ 步骤 2：选公司 ============ -->
          <div v-else-if="activeTab === 'account' && step === 1" key="s2" class="step-panel">
            <div class="user-card">
              <el-avatar :size="48" :src="avatar" class="user-avatar">
                {{ (nickname || username).charAt(0) }}
              </el-avatar>
              <div class="user-info">
                <div class="user-name">{{ nickname || username }}</div>
                <div class="user-dept">
                  <el-icon><OfficeBuilding /></el-icon>
                  <el-tag v-if="department" size="small" effect="light" round>{{ department }}</el-tag>
                  <span v-else class="muted">未设置部门</span>
                </div>
              </div>
              <el-link type="info" :underline="false" @click="step = 0">重选</el-link>
            </div>

            <p class="panel-tip">请选择要登录的公司（租户）：</p>
            <div class="tenant-grid">
              <div
                v-for="t in tenants"
                :key="t.id"
                class="tenant-card"
                :class="{ active: form.tenantId === t.id }"
                @click="form.tenantId = t.id; form.tenantName = t.tenantName; form.tenantCode = t.tenantCode"
              >
                <div class="tenant-avatar">
                  {{ (t.tenantName || t.tenantCode).charAt(0) }}
                </div>
                <div class="tenant-meta">
                  <div class="tenant-name">{{ t.tenantName }}</div>
                  <div class="tenant-code">{{ t.tenantCode }} · {{ t.role === 'owner' ? '主公司' : '访客' }}</div>
                </div>
                <el-icon v-if="form.tenantId === t.id" class="check"><CircleCheckFilled /></el-icon>
              </div>
            </div>

            <el-button type="primary" :disabled="!form.tenantId" class="primary-btn" @click="step = 2">
              下一步
            </el-button>
          </div>

          <!-- ============ 步骤 3：密码 ============ -->
          <div v-else-if="activeTab === 'account' && step === 2" key="s3" class="step-panel">
            <div class="confirm-card">
              <div class="confirm-row">
                <span class="label">用户</span>
                <span class="value">{{ nickname || username }}</span>
              </div>
              <div class="confirm-row">
                <span class="label">公司</span>
                <span class="value">{{ form.tenantName }} <small class="muted">({{ form.tenantCode }})</small></span>
              </div>
              <div class="confirm-row">
                <span class="label">部门</span>
                <span class="value">
                  <el-tag size="small" effect="light" round>{{ department || '未设置' }}</el-tag>
                </span>
              </div>
              <el-link type="info" :underline="false" class="change" @click="step = 1">换公司</el-link>
            </div>

            <el-form @keyup.enter="onSubmit" :model="form" label-position="top" size="large">
              <el-form-item label="密码">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                  :prefix-icon="Lock"
                  autofocus
                />
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="form.remember">7 天内自动登录</el-checkbox>
                <el-link type="primary" :underline="false" class="forgot">忘记密码？</el-link>
              </el-form-item>
              <el-button type="primary" :loading="loading" class="primary-btn" @click="onSubmit">
                登 录
              </el-button>
            </el-form>
          </div>

          <!-- ============ Tab: SSO ============ -->
          <div v-else-if="activeTab === 'sso'" key="sso" class="step-panel">
            <h2 class="panel-title">企业 SSO 登录</h2>
            <p class="panel-tip">使用企业账号（OIDC / LDAP / 飞书 / 钉钉）登录</p>

            <div class="sso-grid">
              <button class="sso-btn" @click="onSsoLogin('feishu')">
                <div class="sso-ico feishu">飞</div>
                <div class="sso-name">飞书</div>
                <div class="sso-sub">Lark OIDC</div>
              </button>
              <button class="sso-btn" @click="onSsoLogin('dingtalk')">
                <div class="sso-ico ding">钉</div>
                <div class="sso-name">钉钉</div>
                <div class="sso-sub">DingTalk OAuth</div>
              </button>
              <button class="sso-btn" @click="onSsoLogin('wecom')">
                <div class="sso-ico wec">企</div>
                <div class="sso-name">企业微信</div>
                <div class="sso-sub">WeCom QR</div>
              </button>
              <button class="sso-btn" @click="onSsoLogin('ldap')">
                <div class="sso-ico ldap">LD</div>
                <div class="sso-name">LDAP / AD</div>
                <div class="sso-sub">企业目录</div>
              </button>
              <button class="sso-btn" @click="onSsoLogin('oauth2')">
                <div class="sso-ico oa">O2</div>
                <div class="sso-name">OAuth 2.0</div>
                <div class="sso-sub">通用 OIDC</div>
              </button>
              <button class="sso-btn" @click="onSsoLogin('saml')">
                <div class="sso-ico sam">SA</div>
                <div class="sso-name">SAML 2.0</div>
                <div class="sso-sub">企业 SAML</div>
              </button>
            </div>
            <p class="sso-note">企业 SSO 接入请联系系统管理员配置 client_id / 重定向 URL</p>
          </div>

          <!-- ============ Tab: 访客 ============ -->
          <div v-else-if="activeTab === 'guest'" key="guest" class="step-panel">
            <h2 class="panel-title">访客体验</h2>
            <p class="panel-tip">无需账号，1 分钟快速浏览平台功能（只读沙箱）</p>

            <div class="guest-card">
              <div class="guest-ico">👀</div>
              <div class="guest-title">Demo Sandbox</div>
              <p class="guest-desc">预置 AI 助手、示例知识库、3 个示例多 Agent 案例。<br/>可聊天但不能保存数据，刷新即清空。</p>
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

        <!-- 步骤指示器（仅账号 tab 显示） -->
        <div v-if="activeTab === 'account'" class="step-dots">
          <div v-for="i in 3" :key="i" :class="['dot', { on: step >= i - 1 }]"></div>
          <span class="step-label">{{ stepLabels[step] }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, OfficeBuilding, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { authApi } from '@/api'

const router = useRouter()

// ============== Tab 配置 ==============
const tabs = [
  { key: 'account', label: '账号登录', icon: '🔑' },
  { key: 'sso',     label: '企业 SSO', icon: '🏢' },
  { key: 'guest',   label: '访客体验', icon: '👀' }
]
const activeTab = ref('account')
const switchTab = (k) => { activeTab.value = k; step.value = 0 }

// ============== 步骤 ==============
const step = ref(0)
const stepLabels = ['输入账号', '选择公司', '登录']

// ============== 表单 ==============
const form = reactive({
  username: '',
  password: '',
  tenantId: null,
  tenantName: '',
  tenantCode: '',
  remember: true
})

const checking = ref(false)
const loading = ref(false)

// 用户信息（步骤 1 拉取）
const username = ref('')
const nickname = ref('')
const department = ref('')
const avatar = ref('')
const tenants = ref([])

// ============== 背景粒子 ==============
const canvas = ref(null)
const currentTime = ref(new Date().toLocaleString('zh-CN'))
let rafId = null
let clockTimer = null

// ============== 步骤 1：检查用户 ==============
const onCheckUser = async () => {
  if (!form.username) return ElMessage.warning('请输入用户名')
  checking.value = true
  try {
    const resp = await authApi.preview(form.username)
    if (!resp.data) {
      ElMessage.error('用户不存在')
      return
    }
    const d = resp.data
    username.value = d.username
    nickname.value = d.nickname || d.username
    department.value = d.department || ''
    avatar.value = d.avatar || ''
    tenants.value = d.tenants || []
    if (tenants.value.length === 0) {
      ElMessage.warning('该用户暂无可登录的公司')
      return
    }
    // 默认选第一个
    form.tenantId = tenants.value[0].id
    form.tenantName = tenants.value[0].tenantName
    form.tenantCode = tenants.value[0].tenantCode
    step.value = 1
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || '查询失败')
  } finally {
    checking.value = false
  }
}

// ============== 步骤 3：登录 ==============
const onSubmit = async () => {
  if (!form.password) return ElMessage.warning('请输入密码')
  loading.value = true
  try {
    const body = {
      username: form.username,
      password: form.password,
      tenantId: form.tenantId
    }
    const resp = await authApi.login(body)
    const data = resp.data
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('avatar', data.avatar || '')
    localStorage.setItem('department', data.department || '')
    localStorage.setItem('tenant_id', String(data.tenantId))
    localStorage.setItem('tenant_code', data.tenantCode || '')
    localStorage.setItem('tenant_name', data.tenantName || '')
    localStorage.setItem('roles', JSON.stringify(data.roles || ['user']))
    // 7 天自动登录
    if (form.remember) {
      localStorage.setItem('remember_login', 'true')
      localStorage.setItem('remember_username', form.username)
    } else {
      localStorage.removeItem('remember_login')
    }
    ElMessage.success(`欢迎回来，${data.nickname || data.username}！`)
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// ============== 快捷填充 ==============
const quickFill = (u, p, dept, tname, tid) => {
  form.username = u
  form.password = p
  form.tenantId = tid
  form.tenantName = tname
  department.value = dept
  // 自动提交
  onCheckUser()
}

// ============== SSO 登录 ==============
const onSsoLogin = (provider) => {
  // 真实场景：window.location.href = `/api/auth/sso/${provider}/login?redirect=...`
  ElMessage.info(`${provider} SSO 接入待配置（联系管理员获取 client_id / redirect_uri）`)
}

// ============== 访客登录 ==============
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

// ============== 自动填（记住登录） ==============
onMounted(() => {
  if (localStorage.getItem('remember_login') === 'true') {
    const saved = localStorage.getItem('remember_username')
    if (saved) form.username = saved
  }
  // 时钟
  clockTimer = setInterval(() => {
    currentTime.value = new Date().toLocaleString('zh-CN')
  }, 1000)
  // 粒子背景
  const c = canvas.value
  if (!c) return
  c.width = c.offsetWidth
  c.height = c.offsetHeight
  const ctx = c.getContext('2d')
  const N = 60
  const particles = Array.from({ length: N }, () => ({
    x: Math.random() * c.width,
    y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
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
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(167, 139, 250, ${(1 - d / 110) * 0.2})`
          ctx.stroke()
        }
      }
    }
    rafId = requestAnimationFrame(draw)
  }
  draw()
  window.addEventListener('resize', () => {
    c.width = c.offsetWidth
    c.height = c.offsetHeight
  })
})

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
/* =================================================== */
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
@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(40px, -30px) scale(1.1); }
}

/* =================================================== */
.login-shell {
  position: relative; z-index: 2;
  display: flex;
  width: 1100px; max-width: calc(100vw - 40px);
  min-height: 660px; max-height: calc(100vh - 40px);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  box-shadow: 0 24px 60px -12px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.6);
  animation: shellIn 0.5s ease-out;
  overflow: hidden;
}
@keyframes shellIn { from { opacity: 0; transform: translateY(20px) scale(0.96); } to { opacity: 1; transform: none; } }

/* 左 brand panel */
.brand-panel {
  flex: 0 0 420px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  color: #fff;
  padding: 48px 40px;
  position: relative;
  overflow: hidden;
  display: flex; flex-direction: column; justify-content: center;
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
  backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  font-size: 36px;
  box-shadow: 0 8px 20px -4px rgba(0, 0, 0, 0.3);
  animation: pulse 3s ease-in-out infinite;
}
@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }
.logo-version {
  font-size: 11px; padding: 4px 10px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.2); color: #fff; letter-spacing: 1px;
}
.brand-title { font-size: 30px; font-weight: 800; margin: 0 0 8px; letter-spacing: -0.5px; }
.brand-sub { font-size: 13px; opacity: 0.85; margin: 0 0 36px; }

.feature-list { list-style: none; padding: 0; margin: 0; }
.feature-list li {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 0; font-size: 13px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.feature-list li:last-child { border-bottom: none; }
.feature-list .ico {
  width: 28px; height: 28px; border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
}

.brand-foot {
  margin-top: 36px; display: flex; align-items: center; gap: 8px;
  font-size: 12px; opacity: 0.7;
}
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.3);
  animation: blink 2s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* 右 form panel */
.form-panel { flex: 1; padding: 36px 44px; display: flex; flex-direction: column; position: relative; }

.login-tabs { display: flex; gap: 6px; padding: 4px; background: #f3f4f6; border-radius: 12px; margin-bottom: 28px; }
.tab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 12px; border: none; background: transparent; cursor: pointer;
  border-radius: 9px; font-size: 13px; color: #64748b; font-weight: 500;
  transition: all 0.2s;
}
.tab:hover { color: #1e293b; }
.tab.active { background: #fff; color: #6366f1; box-shadow: 0 2px 8px -2px rgba(99, 102, 241, 0.3); font-weight: 600; }
.tab-ico { font-size: 14px; }

/* step 容器 */
.step-panel { flex: 1; min-height: 380px; }
.panel-title { font-size: 24px; font-weight: 700; color: #1e1b4b; margin: 0 0 8px; }
.panel-tip { font-size: 13px; color: #6b7280; margin: 0 0 24px; }
.primary-btn { width: 100%; height: 44px; font-size: 15px; font-weight: 600; letter-spacing: 1px;
               background: linear-gradient(135deg, #6366f1, #8b5cf6); border: none; }
.primary-btn:hover { background: linear-gradient(135deg, #4f46e5, #7c3aed); }

/* 步骤 1：账号 */
.quick-fill { margin-top: 28px; }
.quick-tip { font-size: 12px; color: #9ca3af; margin: 0 0 10px; }
.quick-row { display: flex; flex-direction: column; gap: 8px; }
.chip {
  display: flex; align-items: center; gap: 12px; padding: 10px 12px;
  border: 1.5px solid #e5e7eb; background: #fff; border-radius: 12px;
  cursor: pointer; text-align: left; transition: all 0.18s;
}
.chip:hover { border-color: #a5b4fc; transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.2); }
.chip-avatar {
  width: 36px; height: 36px; border-radius: 10px;
  color: #fff; font-weight: 700; font-size: 14px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.chip-avatar.admin { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.chip-avatar.demo { background: linear-gradient(135deg, #ec4899, #f43f5e); }
.chip-avatar.mgr  { background: linear-gradient(135deg, #10b981, #06b6d4); }
.chip-meta { flex: 1; min-width: 0; }
.chip-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.chip-dept { font-size: 11px; color: #6b7280; margin-top: 1px; }
.foot-tip { text-align: center; margin-top: 16px; font-size: 12px; color: #9ca3af; }
.foot-tip .el-link { margin-left: 4px; }

/* 步骤 2：选公司 */
.user-card {
  display: flex; align-items: center; gap: 12px; padding: 12px 14px;
  background: linear-gradient(135deg, #f3f4f6, #e0e7ff); border-radius: 12px; margin-bottom: 16px;
}
.user-avatar { background: linear-gradient(135deg, #6366f1, #ec4899); color: #fff; font-weight: 600; }
.user-info { flex: 1; }
.user-name { font-size: 15px; font-weight: 600; color: #1e1b4b; }
.user-dept { font-size: 12px; color: #6b7280; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.muted { color: #9ca3af; }

.tenant-grid { display: grid; grid-template-columns: 1fr; gap: 10px; margin-bottom: 16px; max-height: 240px; overflow-y: auto; }
.tenant-card {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border-radius: 12px; cursor: pointer;
  border: 1.5px solid #e5e7eb; background: #fff; transition: all 0.18s;
}
.tenant-card:hover { border-color: #a5b4fc; transform: translateY(-1px); box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.2); }
.tenant-card.active { border-color: #6366f1; background: linear-gradient(135deg, #eef2ff, #fdf4ff); box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.4); }
.tenant-avatar {
  width: 40px; height: 40px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 16px;
}
.tenant-meta { flex: 1; }
.tenant-name { font-size: 14px; font-weight: 600; color: #1e1b4b; }
.tenant-code { font-size: 11px; color: #6b7280; margin-top: 1px; }
.check { color: #6366f1; font-size: 20px; }

/* 步骤 3：密码 */
.confirm-card {
  position: relative; padding: 14px 16px; margin-bottom: 18px;
  background: linear-gradient(135deg, #eef2ff, #fdf4ff); border-radius: 12px;
}
.confirm-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; font-size: 13px; }
.confirm-row .label { color: #6b7280; }
.confirm-row .value { color: #1e1b4b; font-weight: 500; }
.change { position: absolute; top: 8px; right: 12px; font-size: 12px; }
.forgot { float: right; font-size: 12px; }

/* SSO 卡片 */
.sso-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.sso-btn {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 16px 12px; border: 1.5px solid #e5e7eb; background: #fff; border-radius: 12px;
  cursor: pointer; transition: all 0.2s;
}
.sso-btn:hover { border-color: #a5b4fc; transform: translateY(-2px); box-shadow: 0 6px 16px -4px rgba(99, 102, 241, 0.2); }
.sso-ico {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 18px; font-weight: 700;
}
.sso-ico.feishu { background: linear-gradient(135deg, #00d6b9, #3370ff); }
.sso-ico.ding { background: linear-gradient(135deg, #1296db, #1677ff); }
.sso-ico.wec { background: linear-gradient(135deg, #10aeff, #2675ec); }
.sso-ico.ldap { background: linear-gradient(135deg, #6b7280, #1f2937); }
.sso-ico.oa { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.sso-ico.sam { background: linear-gradient(135deg, #8b5cf6, #6366f1); }
.sso-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.sso-sub { font-size: 10px; color: #6b7280; }
.sso-note { font-size: 11px; color: #9ca3af; text-align: center; margin-top: 16px; }

/* 访客卡片 */
.guest-card {
  text-align: center; padding: 32px 24px; background: linear-gradient(135deg, #fef3c7, #fce7f3); border-radius: 16px;
  margin-bottom: 16px;
}
.guest-ico { font-size: 48px; }
.guest-title { font-size: 18px; font-weight: 700; color: #1e1b4b; margin: 8px 0; }
.guest-desc { font-size: 13px; color: #4b5563; line-height: 1.6; margin: 0 0 20px; }
.guest-warn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px; background: #fff7ed; border: 1px solid #fed7aa; border-radius: 8px;
  color: #c2410c; font-size: 12px;
}

/* 步骤指示器 */
.step-dots {
  position: absolute; bottom: 16px; right: 44px;
  display: flex; align-items: center; gap: 6px;
}
.dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #e5e7eb; transition: all 0.3s;
}
.dot.on { background: #6366f1; width: 24px; border-radius: 4px; }
.step-label { font-size: 11px; color: #9ca3af; margin-left: 6px; }

/* 步骤切换动画 */
.step-enter-active, .step-leave-active { transition: all 0.25s ease; }
.step-enter-from { opacity: 0; transform: translateX(20px); }
.step-leave-to { opacity: 0; transform: translateX(-20px); }

/* 响应式 */
@media (max-width: 900px) {
  .login-shell { flex-direction: column; min-height: auto; }
  .brand-panel { flex: 0 0 auto; padding: 32px 24px; }
  .feature-list { display: none; }
  .form-panel { padding: 24px; }
}
</style>
