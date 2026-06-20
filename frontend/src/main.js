import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
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
  console.error('[Vue Error]', err, info)
  ElMessage.error(`页面运行出错: ${err?.message || err}`)
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
app.use(ElementPlus)
bindRouterProgress(router)
app.mount('#app')
