<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: var(--sidebar-bg)">
      <div class="logo">AI Agent Platform</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="var(--sidebar-bg)"
        text-color="#fff"
        active-text-color="#fff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/models">
          <el-icon><Cpu /></el-icon>
          <span>大模型</span>
        </el-menu-item>
        <el-menu-item index="/datasets">
          <el-icon><Files /></el-icon>
          <span>数据集</span>
        </el-menu-item>
        <el-menu-item index="/train">
          <el-icon><VideoPlay /></el-icon>
          <span>训练任务</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><UserFilled /></el-icon>
          <span>智能体</span>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Tools /></el-icon>
          <span>工具</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Reading /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/inference">
          <el-icon><ChatDotRound /></el-icon>
          <span>推理测试</span>
        </el-menu-item>
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon><span>系统</span>
          </template>
          <el-menu-item index="/users">用户</el-menu-item>
          <el-menu-item index="/tenants">租户</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff; border-bottom: 1px solid #ebeef5; display:flex; align-items:center; justify-content:space-between">
        <div style="font-weight:600">{{ $route.meta.title || 'AI Platform' }}</div>
        <div>
          <el-tag type="info" effect="plain" style="margin-right: 12px">
            {{ tenantCode }}
          </el-tag>
          <el-dropdown>
            <span class="user-trigger">{{ username }}<el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const router = useRouter()
const username = computed(() => localStorage.getItem('username') || 'guest')
const tenantCode = computed(() => localStorage.getItem('tenant_code') || 'default')

const logout = async () => {
  try { await authApi.logout() } catch (e) { /* ignore */ }
  localStorage.clear()
  ElMessage.success('已退出')
  router.push('/login')
}
</script>

<style scoped>
.logo {
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  padding: 18px 16px;
  border-bottom: 1px solid #1f2d3d;
}
.user-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 4px;
}
.el-menu { border-right: none; }
</style>
