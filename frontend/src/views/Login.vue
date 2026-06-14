<template>
  <div class="login-bg">
    <el-card class="login-card">
      <h2 class="title">AI Agent Platform</h2>
      <p class="subtitle">大模型 · 智能体 · 多租户 一体化平台</p>
      <el-form :model="form" label-width="80px" @keyup.enter="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="admin123" show-password />
        </el-form-item>
        <el-form-item label="租户">
          <el-input v-model="form.tenantId" placeholder="1" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="onSubmit">登 录</el-button>
      </el-form>
      <p class="hint">默认账号: admin / admin123, 租户 ID: 1</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123', tenantId: '1' })

const onSubmit = async () => {
  loading.value = true
  try {
    const body = { ...form, tenantId: Number(form.tenantId) || 1 }
    const resp = await authApi.login(body)
    localStorage.setItem('access_token', resp.data.accessToken)
    localStorage.setItem('refresh_token', resp.data.refreshToken)
    localStorage.setItem('username', resp.data.username)
    localStorage.setItem('tenant_id', String(resp.data.tenantId))
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  height: 100vh;
  background: linear-gradient(135deg, #2c5cff 0%, #00c6ff 100%);
  display: flex; align-items: center; justify-content: center;
}
.login-card { width: 420px; padding: 24px 8px 8px; }
.title { text-align: center; margin: 0; color: var(--primary); }
.subtitle { text-align: center; color: #909399; margin: 4px 0 20px; }
.hint { text-align: center; color: #909399; font-size: 12px; margin-top: 12px; }
</style>
