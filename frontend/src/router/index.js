import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layouts/MainLayout.vue'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'models', name: 'Models', component: () => import('@/views/Models.vue'), meta: { title: '大模型管理' } },
      { path: 'datasets', name: 'Datasets', component: () => import('@/views/Datasets.vue'), meta: { title: '数据集' } },
      { path: 'train', name: 'Train', component: () => import('@/views/Train.vue'), meta: { title: '训练任务' } },
      { path: 'agents', name: 'Agents', component: () => import('@/views/Agents.vue'), meta: { title: '智能体' } },
      { path: 'tools', name: 'Tools', component: () => import('@/views/Tools.vue'), meta: { title: '工具' } },
      { path: 'chat/:agentId', name: 'Chat', component: () => import('@/views/Chat.vue'), meta: { title: '对话' } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/Knowledge.vue'), meta: { title: '知识库' } },
      { path: 'knowledge/pipeline', name: 'KnowledgePipeline', component: () => import('@/views/KnowledgePipeline.vue'), meta: { title: '知识库流程编排' } },
      { path: 'inference', name: 'Inference', component: () => import('@/views/Inference.vue'), meta: { title: '推理测试' } },
      { path: 'users', name: 'Users', component: () => import('@/views/Users.vue'), meta: { title: '用户' } },
      { path: 'tenants', name: 'Tenants', component: () => import('@/views/Tenants.vue'), meta: { title: '租户' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path === '/login') return next()
  const token = localStorage.getItem('access_token')
  if (!token) return next('/login')
  next()
})

export default router
