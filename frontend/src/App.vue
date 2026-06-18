<template>
  <!-- ★ P0-PM-3 全局错误兜底: 子组件崩了不会白屏 -->
  <router-view v-if="!crashed" />
  <AppErrorBoundary v-else @reset="reset" :error="lastError" />
</template>

<script setup>
import { onErrorCaptured, ref } from 'vue'
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'

const crashed = ref(false)
const lastError = ref(null)

onErrorCaptured((err, instance, info) => {
  console.error('[App errorCaptured]', err, info)
  crashed.value = true
  lastError.value = err
  // 上报
  try { fetch('/api/log/frontend-error', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ err: String(err), info, ts: Date.now() }) }).catch(() => {}) } catch (e) {}
  return false  // 不冒泡
})

const reset = () => {
  crashed.value = false
  lastError.value = null
}
</script>
