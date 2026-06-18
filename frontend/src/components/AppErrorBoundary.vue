<template>
  <div class="aeb">
    <div class="aeb-card">
      <div class="aeb-icon">💥</div>
      <h1>页面出错了</h1>
      <p class="muted">程序遇到了一个意外错误, 已为您隔离, 不会影响其他功能.</p>
      <pre v-if="error" class="err-detail">{{ String(error).slice(0, 500) }}</pre>
      <div class="aeb-actions">
        <el-button type="primary" size="large" @click="$emit('reset')">🔄 重试</el-button>
        <el-button size="large" @click="$router.push('/dashboard')">回工作台</el-button>
        <el-button size="large" @click="copyErr">📋 复制错误</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
defineProps({ error: { type: [Error, String], default: null } })
defineEmits(['reset'])
defineOptions({ name: 'AppErrorBoundary' })
const copyErr = () => {
  try { navigator.clipboard.writeText(String(props?.error || '')) } catch (e) {}
  ElMessage.success('已复制到剪贴板')
}
</script>

<style scoped>
.aeb { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #fef2f2, #fee2e2); }
.aeb-card { background: #fff; padding: 48px 60px; border-radius: 20px; box-shadow: 0 20px 60px rgba(239,68,68,0.15); text-align: center; max-width: 600px; }
.aeb-icon { font-size: 80px; margin-bottom: 12px; }
.aeb h1 { font-size: 24px; color: #b91c1c; margin: 0 0 8px; }
.muted { color: #64748b; margin: 0 0 16px; }
.err-detail { text-align: left; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; font-size: 12px; color: #475569; max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.aeb-actions { display: flex; gap: 12px; justify-content: center; margin-top: 20px; flex-wrap: wrap; }
</style>
