// ============================================================
// 全局事件总线 — 跨页面/组件实时联动
// 用法:
//   const bus = useGlobalBus()
//   bus.on('train:progress', (data) => { ... })
//   bus.emit('train:progress', { step: 100, loss: 1.2 })
//   bus.off('train:progress', handler)
// ============================================================

import { reactive, onBeforeUnmount } from 'vue'

const _listeners = reactive({})  // { 'eventName': [handler1, handler2, ...] }

function on(event, handler) {
  if (!_listeners[event]) _listeners[event] = []
  _listeners[event].push(handler)
  // 返回 off 闭包
  return () => off(event, handler)
}

function off(event, handler) {
  if (!_listeners[event]) return
  const idx = _listeners[event].indexOf(handler)
  if (idx >= 0) _listeners[event].splice(idx, 1)
}

function emit(event, data) {
  if (!_listeners[event]) return
  // 复制一份再遍历，避免中途 off 导致索引错乱
  const handlers = _listeners[event].slice()
  for (const h of handlers) {
    try { h(data) } catch (e) { console.error('[bus] handler error:', e) }
  }
}

function once(event, handler) {
  const offFn = on(event, (data) => {
    handler(data)
    offFn()
  })
  return offFn
}

export function useGlobalBus() {
  // 组件卸载时自动清理本组件注册的 handler
  const _myHandlers = []
  const _on = (event, handler) => {
    _myHandlers.push({ event, handler })
    return on(event, handler)
  }
  onBeforeUnmount(() => {
    for (const { event, handler } of _myHandlers) off(event, handler)
  })
  return { on: _on, off, emit, once }
}

// 调试用：列出所有事件
export function _listBusEvents() {
  return Object.entries(_listeners).map(([k, v]) => `${k}: ${v.length}`).join('\n')
}
