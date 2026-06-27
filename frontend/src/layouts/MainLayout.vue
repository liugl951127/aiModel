<template>
  <div class="app-shell">
    <!-- ============================================ -->
    <!-- 侧栏                                       -->
    <!-- ============================================ -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="logo-wrap" @click="goHome">
        <div class="logo-mark">L</div>
        <div v-if="!collapsed" class="logo-text">
          <div class="logo-title">LIUGL</div>
          <div class="logo-sub">AI Agent Platform</div>
        </div>
      </div>

      <el-menu
        class="side-menu"
        :default-active="$route.path"
        :default-openeds="defaultOpenedMenus"
        :collapse="collapsed"
        router
        background-color="transparent"
        text-color="#cbd5e1"
        active-text-color="#fff"
        unique-opened
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>

        <!-- ============ AI 能力 (一级) - 按依赖顺序组织 ============ -->
        <el-sub-menu index="/ai">
          <template #title>
            <el-icon><MagicStick /></el-icon>
            <span>AI 能力</span>
          </template>

          <!-- 二级: 数据准备 (基础) -->
          <el-sub-menu index="/ai-data">
            <template #title>
              <el-icon><Files /></el-icon>
              <span>📦 数据准备</span>
            </template>
            <el-menu-item index="/datasets">
              <el-icon><Files /></el-icon>
              <template #title>数据集</template>
            </el-menu-item>
            <el-menu-item index="/knowledge">
              <el-icon><Reading /></el-icon>
              <template #title>知识库</template>
            </el-menu-item>
            <el-menu-item index="/files">
              <el-icon><Folder /></el-icon>
              <template #title>文件管理</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 二级: 模型管理 -->
          <el-sub-menu index="/ai-model">
            <template #title>
              <el-icon><Cpu /></el-icon>
              <span>🤖 模型管理</span>
            </template>
            <el-menu-item index="/models">
              <el-icon><Cpu /></el-icon>
              <template #title>大模型</template>
            </el-menu-item>
            <el-menu-item index="/model-versions">
              <el-icon><Box /></el-icon>
              <template #title>模型版本</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 二级: 训练 (依赖数据+模型) -->
          <el-sub-menu index="/ai-train">
            <template #title>
              <el-icon><VideoPlay /></el-icon>
              <span>⚙️ 训练任务</span>
            </template>
            <el-menu-item index="/train">
              <el-icon><VideoPlay /></el-icon>
              <template #title>训练任务</template>
            </el-menu-item>
            <el-menu-item index="/agents">
              <el-icon><UserFilled /></el-icon>
              <template #title>智能体</template>
            </el-menu-item>
            <el-menu-item index="/tools">
              <el-icon><Tools /></el-icon>
              <template #title>工具</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 二级: 推理部署 (依赖模型+训练) -->
          <el-sub-menu index="/ai-deploy">
            <template #title>
              <el-icon><Aim /></el-icon>
              <span>🚀 推理服务</span>
            </template>
            <el-menu-item index="/inference">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>推理测试</template>
            </el-menu-item>
            <el-menu-item index="/chat">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>智能对话</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 二级: 工作流编排 (依赖上面所有) -->
          <el-sub-menu index="/ai-workflow">
            <template #title>
              <el-icon><Connection /></el-icon>
              <span>🔀 流程编排</span>
            </template>
            <el-menu-item index="/workflow">
              <el-icon><MagicStick /></el-icon>
              <template #title>编排画布</template>
            </el-menu-item>
            <el-menu-item index="/workflow-list">
              <el-icon><Tickets /></el-icon>
              <template #title>工作流管理</template>
            </el-menu-item>
          </el-sub-menu>
        </el-sub-menu>

        <!-- ============ 业务 (独立, 不依赖 AI 能力) ============ -->
        <el-sub-menu index="/biz">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>业务</span>
          </template>
          <el-menu-item index="/customers">客户</el-menu-item>
          <el-menu-item index="/chats">洽谈</el-menu-item>
          <el-menu-item index="/opportunities">商机</el-menu-item>
          <el-menu-item index="/quotes">报价</el-menu-item>
          <el-menu-item index="/contracts">合同</el-menu-item>
          <el-menu-item index="/orders">订单</el-menu-item>
          <el-menu-item index="/products">产品</el-menu-item>
          <el-menu-item index="/services">服务</el-menu-item>
        </el-sub-menu>

        <!-- ============ 系统 (独立) ============ -->
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统</span>
          </template>
          <el-menu-item index="/users">用户</el-menu-item>
          <el-menu-item index="/tenants">租户</el-menu-item>
          <el-menu-item index="/roles">角色</el-menu-item>
          <el-menu-item index="/menus">菜单</el-menu-item>
          <el-menu-item index="/audit">审计</el-menu-item>
          <el-menu-item index="/monitor">实时监控</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-foot" v-if="!collapsed">
        <div class="tenant-card" @click="showTenantSwitch = true">
          <div class="t-ico">🏢</div>
          <div class="t-info">
            <div class="t-name">{{ tenantName || '默认租户' }}</div>
            <div class="t-code">{{ tenantCode || '—' }}</div>
          </div>
          <el-icon class="t-swap"><Refresh /></el-icon>
        </div>
      </div>
    </aside>

    <!-- ============================================ -->
    <!-- 主区                                       -->
    <!-- ============================================ -->
    <main class="main">
      <!-- 顶栏 (el-affix 钉屏幕顶部, 不设 target 避免路由切换时 main 元素卸载报错) -->
      <el-affix :offset="0" class="topbar-affix">
        <header class="topbar">
          <div class="topbar-left">
            <el-tooltip :content="collapsed ? '展开侧栏' : '折叠侧栏'" placement="bottom">
              <el-button underline="never" link @click="collapsed = !collapsed" class="collapse-btn">
                <el-icon :size="20"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
              </el-button>
            </el-tooltip>
            <el-breadcrumb separator="/" class="crumb">
              <el-breadcrumb-item :to="{ path: '/dashboard' }">
                <el-icon><HomeFilled /></el-icon>
                首页
              </el-breadcrumb-item>
              <el-breadcrumb-item>{{ $route.meta?.title || $route.name }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="topbar-center">
            <el-autocomplete
              v-model="searchKey"
              :fetch-suggestions="onSearchSuggest"
              placeholder="搜索菜单 / API / 智能体…"
              :prefix-icon="Search"
              clearable
              class="global-search"
              @select="onSearchSelect"
              @keyup.enter="onSearch"
              value-key="name"
            >
              <template #default="{ item }">
                <div class="si-item">
                  <el-icon><component :is="item.icon" /></el-icon>
                  <div class="si-meta">
                    <div class="si-title">{{ item.name }}</div>
                    <div class="si-desc">{{ item.desc }}</div>
                  </div>
                </div>
              </template>
            </el-autocomplete>
          </div>

          <div class="topbar-right">
            <el-tooltip content="实时事件流" placement="bottom">
              <el-badge :value="liveCount" :hidden="liveCount === 0" :max="99">
                <el-button underline="never" link @click="showTicker = !showTicker" class="live-btn">
                  <el-icon :size="20"><BellFilled /></el-icon>
                </el-button>
              </el-badge>
            </el-tooltip>

            <el-tooltip content="主题" placement="bottom">
              <el-button underline="never" link @click="cycleTheme">
                <el-icon :size="20"><component :is="themeIcon" /></el-icon>
              </el-button>
            </el-tooltip>

            <!-- ★ P1-2 修复: 主题切换按钮 -->
            <el-tooltip content="切换主题" placement="bottom">
              <el-dropdown trigger="click" @command="onThemeChange">
                <el-button text class="theme-btn" :title="'主题: ' + themeName">
                  <el-icon><component :is="themeIcon" /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="light"><el-icon><Sunny /></el-icon>浅色</el-dropdown-item>
                    <el-dropdown-item command="dark"><el-icon><Moon /></el-icon>深色</el-dropdown-item>
                    <el-dropdown-item command="auto"><el-icon><MagicStick /></el-icon>跟随系统</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-tooltip>

            <el-tooltip v-if="roles.includes('SUPER_ADMIN')" content="超级管理员" placement="bottom">
              <el-tag type="warning" size="small" effect="dark" class="role-tag">🔑 超管</el-tag>
            </el-tooltip>

            <el-dropdown trigger="click" @command="onUserMenu">
              <div class="user-chip">
                <el-avatar :size="32" class="user-avatar">{{ avatarLetter }}</el-avatar>
                <div class="user-meta">
                  <div class="user-name">{{ nickname || username }}</div>
                  <div class="user-dept">{{ department || '未设置部门' }}</div>
                </div>
                <el-icon><CaretBottom /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled>
                    <div class="ud-info">
                      <strong>{{ nickname || username }}</strong>
                      <div class="muted">{{ roles.join(' · ') }}</div>
                    </div>
                  </el-dropdown-item>
                  <el-dropdown-item command="tenants">
                    <el-icon><OfficeBuilding /></el-icon>切换租户
                  </el-dropdown-item>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="swagger" divided>
                    <el-icon><Document /></el-icon>API 文档
                  </el-dropdown-item>
                  <el-dropdown-item command="agreement">
                    <el-icon><Tickets /></el-icon>用户协议
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>
      </el-affix>

      <!-- 顶部进度条 (路由切换时显示, 无感过渡) -->
      <div class="route-progress" :class="{ active: isNavigating }"></div>

      <!-- 内容 -->
      <section class="content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-page" mode="out-in">
            <!-- ★ v3.x 去掉 keep-alive: cachedViews 用路由 name 跟 Vue 组件 name 不对齐
                 (如 'Agent' 路由对应 'Agents.vue'), 导致缓存错乱/状态丢失. 直接不缓存. -->
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </section>
    </main>

    <!-- 实时事件条 -->
    <LiveTickerBar v-model:visible="showTicker" />

    <!-- 租户切换弹窗 -->
    <el-dialog v-model="showTenantSwitch" title="切换租户" width="420px" align-center>
      <el-radio-group v-model="pendingTenantId" class="tenant-radio">
        <el-radio-button
          v-for="t in availableTenants"
          :key="t.id"
          :value="t.id"
          :disabled="t.id === Number(tenantId)"
        >
          {{ t.tenantName || t.tenantCode }}
          <small v-if="t.id === Number(tenantId)" class="cur">当前</small>
        </el-radio-button>
      </el-radio-group>
      <template #footer>
        <el-button @click="showTenantSwitch = false">取消</el-button>
        <el-button type="primary" @click="confirmSwitchTenant" :disabled="pendingTenantId === Number(tenantId)">
          切换
        </el-button>
      </template>
    </el-dialog>

    <!-- 全局 AI 助手 (跨所有页面, 跟着路由走) -->
    <WorkflowAssistant />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataLine, Cpu, Files, VideoPlay, UserFilled, Tools, Reading, ChatDotRound, Setting,
  Connection, Refresh, Search, Fold, Expand, CaretBottom, User, SwitchButton,
  OfficeBuilding, Document, Sunny, Moon, MagicStick, Tickets, Box, HomeFilled, BellFilled,
  Folder, Aim
} from '@element-plus/icons-vue'
import LiveTickerBar from '@/components/LiveTickerBar.vue'
import WorkflowAssistant from '@/components/WorkflowAssistant.vue'
import { useGlobalBus } from '@/composables/useGlobalBus'

const router = useRouter()
const isNavigating = ref(false)
const cachedViews = ['Dashboard', 'Workflow', 'WorkflowList', 'Distributed', 'KnowledgePipeline', 'Train', 'Agent', 'Chat']

// 路由切换进度条
let navTimer = null
router.beforeEach((to, from, next) => {
  if (to.path !== from.path) {
    isNavigating.value = true
    clearTimeout(navTimer)
    navTimer = setTimeout(() => { isNavigating.value = false }, 300)
  }
  next()
})
router.afterEach(() => {
  setTimeout(() => { isNavigating.value = false }, 100)
})
const route = useRoute()
const bus = useGlobalBus()

// ============== 用户/租户 ==============
const username = ref(localStorage.getItem('username') || '')
const nickname = ref(localStorage.getItem('nickname') || '')
const tenantId = ref(localStorage.getItem('tenant_id') || '1')
const tenantCode = ref(localStorage.getItem('tenant_code') || '')
const tenantName = ref(localStorage.getItem('tenant_name') || '')
const department = ref(localStorage.getItem('department') || '')
const roles = ref(JSON.parse(localStorage.getItem('roles') || '["user"]'))

const avatarLetter = computed(() =>
  (nickname.value || username.value || '?').charAt(0).toUpperCase()
)

// ============== 侧栏折叠 ==============
const collapsed = ref(false)

// 默认展开的菜单 (AI 能力 + 当前路由所在的二级)
const defaultOpenedMenus = computed(() => {
  const path = route.path
  const opens = new Set(['/ai'])
  if (path.startsWith('/datasets') || path.startsWith('/knowledge') || path.startsWith('/files')) opens.add('/ai-data')
  else if (path.startsWith('/models') || path.startsWith('/model-versions')) opens.add('/ai-model')
  else if (path.startsWith('/train') || path.startsWith('/agents') || path.startsWith('/tools')) opens.add('/ai-train')
  else if (path.startsWith('/inference') || path.startsWith('/chat')) opens.add('/ai-deploy')
  else if (path.startsWith('/workflow') || path.startsWith('/workflow-list')) opens.add('/ai-workflow')
  if (path.startsWith('/customers') || path.startsWith('/opportunities') || path.startsWith('/chats') ||
      path.startsWith('/quotes') || path.startsWith('/contracts') || path.startsWith('/orders') ||
      path.startsWith('/products') || path.startsWith('/services')) opens.add('/biz')
  if (['/users','/tenants','/roles','/menus','/audit','/monitor'].some(p => path.startsWith(p))) opens.add('/system')
  return [...opens]
})

// ============== 全局搜索 (el-autocomplete) ==============
const searchKey = ref('')
const onSearchSuggest = (queryString, cb) => {
  const k = (queryString || '').trim().toLowerCase()
  if (!k) { cb([]); return }
  const results = allMenus.filter(m => m.name.toLowerCase().includes(k) || m.desc.toLowerCase().includes(k)).slice(0, 8)
  cb(results)
}
const onSearchSelect = (item) => {
  if (item?.path) goTo(item.path)
}
const onSearch = () => {
  const k = searchKey.value.trim().toLowerCase()
  if (!k) return
  const r = allMenus.find(m => m.name.toLowerCase().includes(k) || m.desc.toLowerCase().includes(k))
  if (r) goTo(r.path)
}
const allMenus = [
  { path: '/dashboard', name: '工作台', icon: 'DataLine', desc: '平台概览 + 实时活动' },
  { path: '/workflow', name: '工作流编排', icon: 'Connection', desc: '拖拽编排业务流' },
  { path: '/workflow-list', name: '工作流管理', icon: 'Tickets', desc: '已保存工作流 + 复制' },
  { path: '/model-versions', name: '模型版本', icon: 'Box', desc: '多版本 + 激活 + 对比' },
  { path: '/models', name: '大模型', icon: 'Cpu', desc: '模型注册 + 版本管理' },
  { path: '/datasets', name: '数据集', icon: 'Files', desc: '语料上传 + 切片' },
  { path: '/train', name: '训练任务', icon: 'VideoPlay', desc: 'Transformer 训练 + SSE 实时' },
  { path: '/agents', name: '智能体', icon: 'UserFilled', desc: 'ReAct 智能体' },
  { path: '/tools', name: '工具', icon: 'Tools', desc: '工具注册中心' },
  { path: '/knowledge', name: '知识库', icon: 'Reading', desc: 'RAG 检索增强' },
  { path: '/inference', name: '推理测试', icon: 'ChatDotRound', desc: 'ONNX 推理' },
  { path: '/chat', name: '智能对话', icon: 'ChatDotRound', desc: '多智能体会话' },
  { path: '/users', name: '用户管理', icon: 'UserFilled', desc: '用户 + 重置密码 + 启停' },
  { path: '/tenants', name: '租户管理', icon: 'OfficeBuilding', desc: '多公司隔离' },
  { path: '/roles', name: '角色管理', icon: 'UserFilled', desc: '角色 + 用户绑定 + 菜单权限' },
  { path: '/menus', name: '菜单管理', icon: 'Setting', desc: '菜单树维护' },
  { path: '/audit', name: '登录审计', icon: 'Document', desc: 'sys_login_audit' }
]
const goTo = (p) => {
  searchKey.value = ''
  router.push(p)
}
const goHome = () => router.push('/dashboard')

// ============== 主题 ==============
const themes = ['light', 'dark', 'auto']
const themeIdx = ref(1)  // 默认 dark
const themeName = computed(() => themes[themeIdx.value])
const themeIcon = computed(() => {
  return markRaw([Sunny, Moon, MagicStick][themeIdx.value])
})
const cycleTheme = () => {
  themeIdx.value = (themeIdx.value + 1) % themes.length
  document.documentElement.dataset.theme = themes[themeIdx.value]
  bus.emit('theme:change', themes[themeIdx.value])
  ElMessage.success(`主题切换: ${themes[themeIdx.value]}`)
}
// ★ P1-2 下拉菜单切换 (可指定主题)
const onThemeChange = (cmd) => {
  const idx = themes.indexOf(cmd)
  if (idx >= 0) {
    themeIdx.value = idx
    document.documentElement.dataset.theme = cmd
    localStorage.setItem('theme', cmd)
    bus.emit('theme:change', cmd)
    ElMessage.success(`主题已切换: ${cmd === 'light' ? '浅色' : cmd === 'dark' ? '深色' : '跟随系统'}`)
  }
}

// ============== 实时事件 ==============
const showTicker = ref(false)
const liveCount = ref(0)
const onLive = (e) => {
  liveCount.value++
  setTimeout(() => { if (liveCount.value > 0) liveCount.value-- }, 3000)
}

// ============== 租户切换 ==============
const showTenantSwitch = ref(false)
const availableTenants = ref([])
const pendingTenantId = ref(null)
const loadTenants = () => {
  try {
    const raw = localStorage.getItem('tenants')
    if (raw) {
      availableTenants.value = JSON.parse(raw)
      return
    }
  } catch (e) { /* ignore */ }
  // fallback: 3 家示例
  availableTenants.value = [
    { id: 1, tenantCode: 'default', tenantName: '默认公司' },
    { id: 2, tenantCode: 'demo-corp', tenantName: '示例科技公司' },
    { id: 3, tenantCode: 'startup-co', tenantName: '创业小公司' }
  ]
}
const confirmSwitchTenant = async () => {
  // 切换租户需要重新登录 — 直接跳到 login 携带目标
  await ElMessageBox.confirm(
    `切换租户会注销当前会话并以新租户身份重新登录。确定切换？`,
    '切换租户',
    { type: 'warning' }
  )
  localStorage.setItem('pending_tenant_id', String(pendingTenantId.value))
  localStorage.removeItem('access_token')
  router.push('/login')
}

// ============== 用户菜单 ==============
const onUserMenu = (cmd) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' }).then(() => {
      localStorage.clear()
      router.push('/login')
    }).catch(() => {})
  } else if (cmd === 'tenants') {
    showTenantSwitch.value = true
  } else if (cmd === 'profile') {
    ElMessage.info('个人中心待开发')
  } else if (cmd === 'swagger') {
    window.open('/doc.html', '_blank')
  } else if (cmd === 'agreement') {
    router.push('/agreement')
  }
}

// ============== Bus 监听 ==============
let _off1, _off2
onMounted(() => {
  loadTenants()
  document.documentElement.dataset.theme = themes[themeIdx.value]
  // ★ P1-2 恢复用户上次选择的主题
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme && themes.includes(savedTheme)) {
    themeIdx.value = themes.indexOf(savedTheme)
    document.documentElement.dataset.theme = savedTheme
  }
  _off1 = bus.on('live:event', onLive)
  _off2 = bus.on('user:update', () => {
    nickname.value = localStorage.getItem('nickname') || ''
    department.value = localStorage.getItem('department') || ''
  })
})
</script>

<style scoped>
/* ============================================ */
/* 全局壳                                       */
/* ============================================ */
.app-shell {
  display: flex;
  width: 100vw; height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f0f9ff 50%, #fef3c7 100%);
  overflow: hidden;
}

/* ============================================ */
/* 侧栏                                         */
/* ============================================ */
.sidebar {
  width: 220px;
  background: #fff;
  color: #475569;
  display: flex; flex-direction: column;
  flex-shrink: 0;
  transition: width 0.25s;
  position: relative;
  z-index: 10;
  border-right: 1px solid var(--border, #e5e7eb);
}
.sidebar.collapsed { width: 64px; }

.logo-wrap {
  display: flex; align-items: center; gap: 12px;
  padding: 18px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border, #e5e7eb);
}
.logo-mark {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff; font-size: 22px; font-weight: 900;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; letter-spacing: -1px;
}
.logo-text { display: flex; flex-direction: column; }
.logo-title { font-size: 15px; font-weight: 800; color: #1e1b4b; letter-spacing: -0.5px; }
.logo-sub { font-size: 10px; color: #94a3b8; }

.side-menu { flex: 1; border-right: none; padding: 8px; overflow-y: auto; background: transparent !important; }
.side-menu :deep(.el-menu-item), .side-menu :deep(.el-sub-menu__title) {
  border-radius: 8px; margin: 2px 0; height: 40px; line-height: 40px;
  color: #475569 !important;
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) { background: #f1f5f9 !important; }
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #6366f1, #4f46e5) !important;
  color: #fff !important;
  box-shadow: 0 4px 12px -2px rgba(99, 102, 241, 0.4);
}
/* 二级菜单背景区分 (依赖关系层级) */
.side-menu :deep(.el-menu .el-menu) {
  background: rgba(99, 102, 241, 0.04) !important;
  border-radius: 8px;
  margin: 2px 6px;
  padding: 4px 0;
}
.side-menu :deep(.el-menu .el-menu .el-menu-item) {
  padding-left: 36px !important;
  font-size: 12px;
  height: 34px; line-height: 34px;
  color: #64748b !important;
}
.side-menu :deep(.el-menu .el-menu .el-menu-item.is-active) {
  background: linear-gradient(135deg, #8b5cf6, #6366f1) !important;
  color: #fff !important;
  font-weight: 600;
}
.side-menu :deep(.el-menu .el-menu .el-sub-menu__title) {
  padding-left: 28px !important;
  font-size: 12px;
  height: 36px; line-height: 36px;
  color: #475569 !important;
}
/* 一级菜单标题加重点颜色 (AI 能力 / 业务 / 系统) */
.side-menu :deep(.el-sub-menu > .el-sub-menu__title) {
  font-weight: 600;
}

.sidebar-foot { padding: 12px; border-top: 1px solid rgba(255, 255, 255, 0.08); }
.tenant-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  cursor: pointer; transition: all 0.18s;
}
.tenant-card:hover { background: rgba(99, 102, 241, 0.15); border-color: #6366f1; }
.t-ico { font-size: 22px; }
.t-info { flex: 1; min-width: 0; }
.t-name { font-size: 12px; font-weight: 600; color: #fff; }
.t-code { font-size: 10px; color: #94a3b8; }
.t-swap { color: #94a3b8; font-size: 14px; }

/* ============================================ */
/* 主区                                         */
/* ============================================ */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* ===== 顶栏 ===== */
.topbar {
  height: 56px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border, #e5e7eb);
  display: flex; align-items: center;
  padding: 0 20px;
  gap: 16px;
  flex-shrink: 0;
  position: relative;
  z-index: 5;
}
.topbar-left { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.collapse-btn { padding: 6px; }
.crumb { font-size: 13px; }

.topbar-center {
  flex: 1; max-width: 480px; position: relative; margin: 0 auto;
}
.global-search :deep(.el-input__wrapper) {
  background: var(--bg-search, #f3f4f6);
  border-radius: 10px;
  box-shadow: none !important;
  padding: 2px 12px;
}
.search-popover {
  position: absolute; top: 100%; left: 0; right: 0; margin-top: 4px;
  background: #fff; border-radius: 10px; box-shadow: 0 10px 30px -6px rgba(0, 0, 0, 0.15);
  padding: 6px; max-height: 400px; overflow-y: auto; z-index: 10;
  border: 1px solid #e5e7eb;
}
.search-item {
  display: flex; gap: 10px; align-items: center;
  padding: 8px 10px; border-radius: 6px; cursor: pointer;
  transition: background 0.15s;
}
.search-item:hover { background: #f3f4f6; }
.search-item .el-icon { color: #6366f1; }
.si-meta { flex: 1; min-width: 0; }
.si-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.si-desc { font-size: 11px; color: #94a3b8; }

.topbar-right { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
.live-dot-wrap {
  position: relative; width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px; cursor: pointer; transition: background 0.15s;
}
.live-dot-wrap:hover { background: #f3f4f6; }
.live-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #cbd5e1; transition: all 0.3s;
}
.live-dot.active { background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.2); } 50% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.1); } }
.live-badge {
  position: absolute; top: 2px; right: 2px;
  min-width: 16px; height: 16px; padding: 0 4px;
  background: #ef4444; color: #fff; font-size: 10px; font-weight: 700;
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
}
.role-tag { font-weight: 600; }

.user-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 10px 4px 4px; border-radius: 24px;
  cursor: pointer; transition: background 0.15s;
}
.user-chip:hover { background: #f3f4f6; }
.user-avatar {
  background: linear-gradient(135deg, #6366f1, #ec4899);
  color: #fff; font-weight: 700; font-size: 13px;
}
.user-meta { display: flex; flex-direction: column; line-height: 1.2; }
.user-name { font-size: 12px; font-weight: 600; color: #1e293b; }
.user-dept { font-size: 10px; color: #94a3b8; }

.ud-info { padding: 4px 0; line-height: 1.4; }
.ud-info .muted { font-size: 11px; color: #94a3b8; }

.tenant-radio { display: flex; flex-direction: column; gap: 8px; }
.tenant-radio :deep(.el-radio-button) { width: 100%; }
.tenant-radio :deep(.el-radio-button__inner) { width: 100%; }
.cur { margin-left: 4px; color: #6366f1; font-size: 10px; }

/* ===== 内容 ===== */
/* 路由切换顶部进度条 */
.route-progress {
  position: fixed; top: 0; left: 0; right: 0;
  height: 2px; z-index: 9999;
  background: linear-gradient(90deg, #6366f1, #8b5cf6, #ec4899);
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  pointer-events: none;
}
.route-progress.active { transform: translateX(0); }

/* 页面切换 fade (无白屏) */
.fade-page-enter-active, .fade-page-leave-active {
  transition: opacity 0.2s ease;
}
.fade-page-enter-from { opacity: 0; }
.fade-page-leave-to { opacity: 0; }

.content { flex: 1; overflow: auto; padding: 20px 24px; }
.fade-page-enter-active, .fade-page-leave-active { transition: all 0.18s ease; }
.fade-page-enter-from, .fade-page-leave-to { opacity: 0; transform: translateY(4px); }

/* ============================================ */
/* 主题                                         */
/* ============================================ */
:root[data-theme='dark'] {
  --bg-app: #0b1020;
  --bg-top: #111827;
  --border: #1f2937;
  --bg-search: #1f2937;
}
:root[data-theme='light'] {
  --bg-app: #f5f7fa;
  --bg-top: #ffffff;
  --border: #e5e7eb;
  --bg-search: #f3f4f6;
}
:root[data-theme='auto'] {
  --bg-app: #f5f7fa;
  --bg-top: #ffffff;
  --border: #e5e7eb;
  --bg-search: #f3f4f6;
}

/* 暗色下内容区也要变 */
:root[data-theme='dark'] .content { color: #e2e8f0; }
:root[data-theme='dark'] .content :deep(.el-card) {
  background: #111827;
  border-color: #1f2937;
  color: #e2e8f0;
}
:root[data-theme='dark'] .content :deep(.el-card__header) {
  border-bottom-color: #1f2937;
  color: #e2e8f0;
}
:root[data-theme='dark'] .content :deep(.el-table) {
  background: #111827; color: #e2e8f0;
}
:root[data-theme='dark'] .content :deep(.el-table tr) {
  background: #111827; color: #e2e8f0;
}
:root[data-theme='dark'] .content :deep(.el-table--enable-row-hover .el-table__body tr:hover > td) {
  background: #1f2937 !important;
}
:root[data-theme='dark'] .content :deep(.el-input__wrapper),
:root[data-theme='dark'] .content :deep(.el-textarea__inner) {
  background: #1f2937; color: #e2e8f0;
}
:root[data-theme='dark'] .content :deep(.el-empty__description p) { color: #94a3b8; }
</style>
