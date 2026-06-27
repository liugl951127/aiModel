import { createApp } from 'vue'
import { createPinia } from 'pinia'
// ★ v3.x Element Plus 按需引入: 仅导入本项目实际使用的组件, 体积从 ~1MB 降到 ~250KB gzip
// unplugin-auto-import + unplugin-vue-components 已处理 <el-button> 等按需 import, 这里仅注册需要的指令/服务
import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus'
import App from './App.vue'
import router from './router'
import { bindRouterProgress } from './composables/useProgress'
import './assets/styles/main.scss'
import './assets/styles/progressbar.css'

// 暴露 ElMessage 给 router 免循环 import
window.__EP_MESSAGE__ = ElMessage

const app = createApp(App)

// ★ P0-PM-3 全局错误边界 — 未捕异常不会导致整页白
// 必须在 createApp 之后设置 (TDZ: const 在声明前不可用)
app.config.errorHandler = (err, instance, info) => {
  // ★ v3.x 修复: 只 console.error + fetch 上报, 不用 ElMessage.error()
  //   ElMessage 触发 Vue 渲染, 若错误发生在渲染中会导致递归 → Maximum call stack
  console.error('[Vue Error]', err, info)
  // 上报接口 (留 hook, 后面可接 Sentry)
  try {
    fetch('/api/log/frontend-error', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ err: String(err), info, ts: Date.now() })
    }).catch(() => {})
  } catch (e) {}
}

app.use(createPinia())
app.use(router)
// ★ v3.x 按需: 使用 unplugin-auto-import + unplugin-vue-components 处理 <el-xxx> 组件自动按需 import
// 这里仅注册需要的指令 + 服务 (ElMessage/ElMessageBox/ElNotification/ElLoading)
app.use(ElLoading)        // 服务指令 v-loading
app.use(ElMessage)         // 全局提示 (某些场景不用组件实例也能用)
app.use(ElMessageBox)      // 弹窗确认
app.use(ElNotification)    // 通知
bindRouterProgress(router)
app.mount('#app')
