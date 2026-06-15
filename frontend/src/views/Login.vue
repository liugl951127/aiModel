<template>
  <div class="login-bg">
    <!-- 背景动效：粒子 + 渐变光斑 -->
    <canvas ref="canvas" class="particles"></canvas>
    <div class="blob blob-1"></div>
    <div class="blob blob-2"></div>
    <div class="blob blob-3"></div>

    <div class="login-card">
      <div class="brand">
        <div class="logo-ring">
          <span class="logo-emoji">🤖</span>
        </div>
        <h1 class="brand-title">AI Agent Platform</h1>
        <p class="brand-sub">大模型 · 智能体 · 多租户 一体化平台</p>
      </div>

      <el-steps :active="step" finish-status="success" align-center class="steps">
        <el-step title="账号" icon="User" />
        <el-step title="公司" icon="OfficeBuilding" />
        <el-step title="登录" icon="Key" />
      </el-steps>

      <!-- 步骤 1：用户名 -->
      <transition name="fade-slide" mode="out-in">
        <div v-if="step === 0" key="s1" class="step-panel">
          <el-form @keyup.enter="onCheckUser" :model="form" label-position="top">
            <el-form-item label="用户名">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名 (admin / demo / manager)"
                size="large"
                :prefix-icon="User"
                autofocus
              />
            </el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="checking"
              class="primary-btn"
              @click="onCheckUser"
            >下一步</el-button>
            <p class="hint">
              默认账号：
              <el-link type="primary" :underline="false" @click="quickFill('admin','admin123','技术部','默认租户',1)">admin/admin123</el-link>
              ·
              <el-link type="primary" :underline="false" @click="quickFill('demo','demo123','市场部','默认租户',1)">demo/demo123</el-link>
            </p>
          </el-form>
        </div>

        <!-- 步骤 2：选公司 -->
        <div v-else-if="step === 1" key="s2" class="step-panel">
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

          <el-button
            type="primary"
            size="large"
            :disabled="!form.tenantId"
            :loading="checking"
            class="primary-btn"
            @click="step = 2"
          >下一步</el-button>
        </div>

        <!-- 步骤 3：密码 -->
        <div v-else key="s3" class="step-panel">
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

          <el-form @keyup.enter="onSubmit" :model="form" label-position="top">
            <el-form-item label="密码">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                show-password
                size="large"
                :prefix-icon="Lock"
                autofocus
              />
            </el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="primary-btn"
              @click="onSubmit"
            >登 录</el-button>
            <p class="hint">忘记密码请联系系统管理员重置。</p>
          </el-form>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, OfficeBuilding, CircleCheckFilled } from '@element-plus/icons-vue'
import { authApi } from '@/api'

const router = useRouter()

const step = ref(0)
const checking = ref(false)
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123',
  tenantId: null,
  tenantName: '',
  tenantCode: ''
})

const username = ref('')
const nickname = ref('')
const department = ref('')
const avatar = ref('')
const tenants = ref([])

const canvas = ref(null)
let particles = []
let rafId = null

const quickFill = (u, p, dept, tname, tid) => {
  form.username = u
  form.password = p
  form.tenantId = tid
  form.tenantName = tname
  department.value = dept
}

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
    ElMessage.success(`欢迎回来，${data.nickname || data.username}！`)
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// ---- 粒子背景 ----
onMounted(() => {
  const c = canvas.value
  if (!c) return
  c.width = c.offsetWidth
  c.height = c.offsetHeight
  const ctx = c.getContext('2d')
  const N = 50
  particles = Array.from({ length: N }, () => ({
    x: Math.random() * c.width,
    y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
    r: Math.random() * 1.6 + 0.6
  }))
  const draw = () => {
    ctx.clearRect(0, 0, c.width, c.height)
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy
      if (p.x < 0 || p.x > c.width) p.vx *= -1
      if (p.y < 0 || p.y > c.height) p.vy *= -1
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(99, 102, 241, 0.4)'
      ctx.fill()
    }
    // 连线
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dx = a.x - b.x, dy = a.y - b.y
        const d = Math.sqrt(dx * dx + dy * dy)
        if (d < 110) {
          ctx.beginPath()
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(99, 102, 241, ${(1 - d / 110) * 0.18})`
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
})
</script>

<style scoped>
.login-bg {
  position: fixed; inset: 0; overflow: hidden;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 30%, #4338ca 70%, #6366f1 100%);
  display: flex; align-items: center; justify-content: center;
}
.particles { position: absolute; inset: 0; width: 100%; height: 100%; }
.blob {
  position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.55;
  animation: float 12s ease-in-out infinite;
}
.blob-1 { width: 380px; height: 380px; background: #ec4899; top: -100px; left: -120px; }
.blob-2 { width: 460px; height: 460px; background: #06b6d4; bottom: -150px; right: -150px; animation-delay: -4s; }
.blob-3 { width: 300px; height: 300px; background: #f59e0b; top: 40%; right: 25%; animation-delay: -8s; }
@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(40px, -30px) scale(1.1); }
}
.login-card {
  position: relative; z-index: 2;
  width: 460px; max-width: calc(100vw - 32px);
  padding: 36px 40px 32px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  box-shadow: 0 20px 60px -10px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.6);
  animation: cardIn 0.5s ease-out;
}
@keyframes cardIn { from { opacity: 0; transform: translateY(20px) scale(0.96); } to { opacity: 1; transform: none; } }

.brand { text-align: center; margin-bottom: 24px; }
.logo-ring {
  width: 72px; height: 72px; margin: 0 auto 12px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 20px -4px rgba(99, 102, 241, 0.5);
  animation: pulse 3s ease-in-out infinite;
}
@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }
.logo-emoji { font-size: 38px; }
.brand-title { font-size: 24px; font-weight: 700; margin: 0; color: #1e1b4b; letter-spacing: -0.5px; }
.brand-sub { font-size: 13px; color: #6b7280; margin: 4px 0 0; }

.steps { margin-bottom: 24px; }

.step-panel { min-height: 200px; }
.primary-btn { width: 100%; height: 44px; font-size: 15px; font-weight: 600; letter-spacing: 1px; }
.hint { font-size: 12px; color: #9ca3af; text-align: center; margin: 12px 0 0; }

.user-card {
  display: flex; align-items: center; gap: 12px; padding: 12px 14px;
  background: linear-gradient(135deg, #f3f4f6, #e0e7ff); border-radius: 12px; margin-bottom: 16px;
}
.user-avatar { background: linear-gradient(135deg, #6366f1, #ec4899); color: #fff; font-weight: 600; }
.user-info { flex: 1; }
.user-name { font-size: 15px; font-weight: 600; color: #1e1b4b; }
.user-dept { font-size: 12px; color: #6b7280; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.muted { color: #9ca3af; }

.panel-tip { font-size: 13px; color: #6b7280; margin: 4px 0 12px; }
.tenant-grid { display: grid; grid-template-columns: 1fr; gap: 10px; margin-bottom: 16px; max-height: 220px; overflow-y: auto; }
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

.confirm-card {
  position: relative; padding: 14px 16px; margin-bottom: 16px;
  background: linear-gradient(135deg, #eef2ff, #fdf4ff); border-radius: 12px;
}
.confirm-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; font-size: 13px; }
.confirm-row .label { color: #6b7280; }
.confirm-row .value { color: #1e1b4b; font-weight: 500; }
.change { position: absolute; top: 8px; right: 12px; font-size: 12px; }

.fade-slide-enter-active, .fade-slide-leave-active { transition: all 0.25s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateX(20px); }
.fade-slide-leave-to { opacity: 0; transform: translateX(-20px); }
</style>
