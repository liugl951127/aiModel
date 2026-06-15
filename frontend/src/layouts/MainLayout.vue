<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="logo-wrap">
        <div class="logo-mark">🤖</div>
        <div class="logo-text">
          <div class="logo-title">AI Agent</div>
          <div class="logo-sub">Platform</div>
        </div>
      </div>
      <el-menu
        class="side-menu"
        :default-active="$route.path"
        router
        background-color="transparent"
        text-color="#cbd5e1"
        active-text-color="#fff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon><span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/models">
          <el-icon><Cpu /></el-icon><span>大模型</span>
        </el-menu-item>
        <el-menu-item index="/datasets">
          <el-icon><Files /></el-icon><span>数据集</span>
        </el-menu-item>
        <el-menu-item index="/train">
          <el-icon><VideoPlay /></el-icon><span>训练任务</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><UserFilled /></el-icon><span>智能体</span>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Tools /></el-icon><span>工具</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Reading /></el-icon><span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/inference">
          <el-icon><ChatDotRound /></el-icon><span>推理测试</span>
        </el-menu-item>
        <el-sub-menu index="/system">
          <template #title><el-icon><Setting /></el-icon><span>系统</span></template>
          <el-menu-item index="/users">用户</el-menu-item>
          <el-menu-item index="/tenants">租户</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="sidebar-foot">
        <div class="tenant-card">
          <div class="t-ico">🏢</div>
          <div class="t-info">
            <div class="t-name">{{ tenantName || '默认租户' }}</div>
            <div class="t-code">{{ tenantCode }}</div>
          </div>
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="page-title">{{ $route.meta.title || 'AI Platform' }}</div>
        <div class="topbar-right">
          <el-tooltip :content="`部门：${department || '未设置'}`" placement="bottom">
            <div class="dept-badge">
              <el-icon><OfficeBuilding /></el-icon>
              <span>{{ department || '未设置部门' }}</span>
            </div>
          </el-tooltip>
          <div class="user-chip" @click="openProfile = true">
            <el-avatar :size="32" :src="avatar" class="chip-avatar">
              {{ (nickname || username).charAt(0) }}
            </el-avatar>
            <span class="chip-name">{{ nickname || username }}</span>
          </div>
          <el-button text :icon="SwitchButton" @click="logout" circle />
        </div>
      </header>
      <section class="content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </main>

    <!-- 浮动实时活动条 -->
    <LiveTickerBar />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, SwitchButton } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import LiveTickerBar from '@/components/LiveTickerBar.vue'

const router = useRouter()
const username = computed(() => localStorage.getItem('username') || 'guest')
const nickname = computed(() => localStorage.getItem('nickname') || '')
const avatar = computed(() => localStorage.getItem('avatar') || '')
const department = computed(() => localStorage.getItem('department') || '')
const tenantName = computed(() => localStorage.getItem('tenant_name') || '')
const tenantCode = computed(() => localStorage.getItem('tenant_code') || 'default')

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定退出当前会话？', '提示', { type: 'warning' })
  } catch { return }
  try { await authApi.logout() } catch (e) { /* ignore */ }
  localStorage.clear()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.app-shell { display: flex; height: 100vh; overflow: hidden; background: #f1f5f9; }

.sidebar {
  width: 230px; flex-shrink: 0;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  display: flex; flex-direction: column;
  position: relative;
}
.sidebar::after {
  content: ''; position: absolute; right: 0; top: 0; bottom: 0; width: 1px;
  background: linear-gradient(180deg, transparent, rgba(99, 102, 241, 0.4), transparent);
}
.logo-wrap {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px; border-bottom: 1px solid rgba(255,255,255,0.06);
}
.logo-mark {
  width: 40px; height: 40px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; box-shadow: 0 4px 12px -2px rgba(99, 102, 241, 0.5);
}
.logo-title { color: #fff; font-weight: 700; font-size: 15px; letter-spacing: -0.3px; }
.logo-sub { color: #94a3b8; font-size: 11px; margin-top: 1px; }
.side-menu { flex: 1; border-right: none; padding: 8px 0; }
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) { height: 42px; line-height: 42px; margin: 2px 8px; border-radius: 8px; }
.side-menu :deep(.el-menu-item:hover) { background: rgba(99, 102, 241, 0.15) !important; }
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #6366f1, #8b5cf6) !important;
  box-shadow: 0 4px 12px -2px rgba(99, 102, 241, 0.5);
}
.sidebar-foot { padding: 12px; border-top: 1px solid rgba(255,255,255,0.06); }
.tenant-card {
  display: flex; align-items: center; gap: 10px; padding: 8px 10px;
  background: rgba(99, 102, 241, 0.1); border-radius: 10px;
}
.t-ico {
  width: 32px; height: 32px; border-radius: 8px;
  background: linear-gradient(135deg, #06b6d4, #6366f1);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
}
.t-name { color: #fff; font-size: 12px; font-weight: 600; }
.t-code { color: #94a3b8; font-size: 10px; margin-top: 1px; font-family: monospace; }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.topbar {
  height: 60px; padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #e2e8f0;
  display: flex; align-items: center; justify-content: space-between;
  position: sticky; top: 0; z-index: 10;
}
.page-title { font-size: 16px; font-weight: 700; color: #1e293b; }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.dept-badge {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: 999px;
  background: linear-gradient(135deg, #ede9fe, #dbeafe);
  color: #6d28d9; font-size: 12px; font-weight: 500;
}
.user-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 10px 4px 4px; border-radius: 999px;
  background: #f1f5f9; cursor: pointer; transition: background 0.18s;
}
.user-chip:hover { background: #e2e8f0; }
.chip-avatar { background: linear-gradient(135deg, #6366f1, #ec4899); color: #fff; font-weight: 600; }
.chip-name { font-size: 13px; color: #1e293b; font-weight: 500; }

.content { flex: 1; overflow: auto; padding: 20px 24px; }

.page-fade-enter-active, .page-fade-leave-active { transition: all 0.22s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(8px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
