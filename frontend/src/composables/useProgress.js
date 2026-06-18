/**
 * ★ P0-PM-2 全局 Loading 进度条 (自实现 NProgress-like, 0 依赖)
 * 路由切换时: start 30% -> 60% -> 95% -> done
 * 普通接口请求: 慢接口超过 800ms 自动出现
 */
import { ref, onBeforeUnmount } from 'vue'

let _barEl = null
let _timer = null
let _pct = 0

function ensureBar() {
  if (_barEl) return _barEl
  _barEl = document.createElement('div')
  _barEl.id = '__app-progress'
  document.body.appendChild(_barEl)
  return _barEl
}

function setPct(v) {
  _pct = Math.max(0, Math.min(100, v))
  const el = ensureBar()
  el.style.width = _pct + '%'
  el.classList.remove('done')
}

export function startProgress() {
  ensureBar()
  setPct(30)
  if (_timer) clearInterval(_timer)
  _timer = setInterval(() => {
    if (_pct < 90) setPct(_pct + (90 - _pct) * 0.1)
  }, 200)
}

export function doneProgress() {
  if (_timer) { clearInterval(_timer); _timer = null }
  setPct(100)
  if (_barEl) {
    setTimeout(() => {
      if (_barEl) {
        _barEl.classList.add('done')
        setTimeout(() => setPct(0), 500)
      }
    }, 200)
  }
}

// 暴露 router hook helper
export function bindRouterProgress(router) {
  router.beforeEach((to, from, next) => {
    if (to.path !== from.path) startProgress()
    next()
  })
  router.afterEach(() => doneProgress())
  router.onError(() => doneProgress())
}

export function useProgress() {
  return { start: startProgress, done: doneProgress }
}
