<template>
  <div class="nf-page">
    <div class="nf-card">
      <div class="nf-code">404</div>
      <div class="nf-icon">🚧</div>
      <h1>页面不存在</h1>
      <p class="muted">您访问的路径 <code>{{ $route.path }}</code> 不存在或已被移除.</p>
      <div class="nf-actions">
        <el-button type="primary" size="large" @click="$router.push('/dashboard')">
          <el-icon><HomeFilled /></el-icon> 回工作台
        </el-button>
        <el-button size="large" @click="$router.back()">返回上页</el-button>
      </div>
      <div class="nf-suggest muted">企业级 AI Agent Platform</div>
    </div>
  </div>
</template>

<script setup>
import { HomeFilled } from '@element-plus/icons-vue'
defineOptions({ name: 'NotFound' })
</script>

<style scoped>
.nf-page { min-height: calc(100vh - 120px); display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f8fafc, #eef2ff); }
.nf-card { background: #fff; padding: 60px 80px; border-radius: 20px; box-shadow: 0 20px 60px rgba(99,102,241,0.15); text-align: center; max-width: 560px; }
.nf-code { font-size: 96px; font-weight: 800; color: transparent; background: linear-gradient(135deg, #6366f1, #ec4899); -webkit-background-clip: text; line-height: 1; margin-bottom: 12px; }
.nf-icon { font-size: 64px; margin-bottom: 12px; }
.nf-card h1 { font-size: 24px; color: #1e293b; margin: 0 0 12px; }
.nf-card p code { background: #f1f5f9; padding: 2px 8px; border-radius: 4px; color: #6366f1; font-size: 13px; }
.nf-actions { display: flex; gap: 12px; justify-content: center; margin-top: 24px; }
.nf-suggest { margin-top: 32px; font-size: 12px; }
.muted { color: #64748b; }
</style>