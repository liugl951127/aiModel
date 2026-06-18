import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layouts/MainLayout.vue'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  // ★ P1-1 修复: 404 顶层独立页 (不被 Layout 包)
  { path: '/404', name: 'NotFound', component: () => import('@/views/NotFound.vue'), meta: { title: '页面不存在' } },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'workflow', name: 'Workflow', component: () => import('@/views/Workflow.vue'), meta: { title: '工作流编排' } },
      { path: 'distributed', name: 'Distributed', component: () => import('@/views/Distributed.vue'), meta: { title: '分布式能力' } },
      { path: 'workflow-list', name: 'WorkflowList', component: () => import('@/views/WorkflowList.vue'), meta: { title: '工作流管理' } },
      { path: 'model-versions', name: 'ModelVersions', component: () => import('@/views/ModelVersions.vue'), meta: { title: '模型版本' } },
      { path: 'models', name: 'Models', component: () => import('@/views/Models.vue'), meta: { title: '大模型管理' } },
      { path: 'datasets', name: 'Datasets', component: () => import('@/views/Datasets.vue'), meta: { title: '数据集' } },
      { path: 'train', name: 'Train', component: () => import('@/views/Train.vue'), meta: { title: '训练任务' } },
      { path: 'agents', name: 'Agents', component: () => import('@/views/Agents.vue'), meta: { title: '智能体' } },
      { path: 'tools', name: 'Tools', component: () => import('@/views/Tools.vue'), meta: { title: '工具' } },
      { path: 'chat', name: 'Chat', component: () => import('@/views/Chat.vue'), meta: { title: '对话' } },
      { path: 'chat/:agentId', name: 'ChatWithAgent', component: () => import('@/views/Chat.vue'), meta: { title: '对话' } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/Knowledge.vue'), meta: { title: '知识库' } },
      { path: 'knowledge/pipeline', name: 'KnowledgePipeline', component: () => import('@/views/KnowledgePipeline.vue'), meta: { title: '知识库流程编排' } },
      { path: 'inference', name: 'Inference', component: () => import('@/views/Inference.vue'), meta: { title: '推理测试' } },
      { path: 'users', name: 'Users', component: () => import('@/views/Users.vue'), meta: { title: '用户' } },
      { path: 'tenants', name: 'Tenants', component: () => import('@/views/Tenants.vue'), meta: { title: '租户' } },
      { path: 'roles', name: 'Roles', component: () => import('@/views/Role.vue'), meta: { title: '角色' } },
      { path: 'menus', name: 'Menus', component: () => import('@/views/Menu.vue'), meta: { title: '菜单' } },
      { path: 'audit', name: 'Audit', component: () => import('@/views/AuditLog.vue'), meta: { title: '审计' } },
      { path: 'files', name: 'Files', component: () => import('@/views/Files.vue'), meta: { title: '文件管理' } },

      // 业务模块
      { path: 'customers', name: 'Customers', component: () => import('@/views/Customers.vue'), meta: { title: '客户' } },
      { path: 'chats', name: 'Chats', component: () => import('@/views/Chats.vue'), meta: { title: '洽谈' } },
      { path: 'opportunities', name: 'Opportunities', component: () => import('@/views/Opportunities.vue'), meta: { title: '商机' } },
      { path: 'quotes', name: 'Quotes', component: () => import('@/views/Quotes.vue'), meta: { title: '报价' } },
      { path: 'contracts', name: 'Contracts', component: () => import('@/views/Contracts.vue'), meta: { title: '合同' } },
      { path: 'orders', name: 'Orders', component: () => import('@/views/Orders.vue'), meta: { title: '订单' } },
      { path: 'products', name: 'Products', component: () => import('@/views/Products.vue'), meta: { title: '产品' } },
      { path: 'services', name: 'Services', component: () => import('@/views/Services.vue'), meta: { title: '服务' } },
      { path: 'dist-tx', name: 'DistributedTx', component: () => import('@/views/DistributedTx.vue'), meta: { title: '分布式事务' } },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/Monitor.vue'), meta: { title: '实时监控' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path === '/login') return next()
  if (to.path === '/404') return next()
  const token = localStorage.getItem('access_token')
  if (!token) return next('/login')
  next()
})

// ★ P1-1 增强: 路由表最后才加 catch-all — 任意未匹配路径跳 /404
// (放在 routes 数组里保证顺序: 已知路由在前面, 404 兜底)
routes.push({ path: '/:pathMatch(.*)*', redirect: '/404' })

export default router
