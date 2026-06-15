<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="hero">
      <div class="hero-text">
        <h1 class="hero-title">你好，{{ nickname }} 👋</h1>
        <p class="hero-sub">
          <span class="pill"><el-icon><OfficeBuilding /></el-icon>{{ tenantName || '默认租户' }}</span>
          <span class="pill"><el-icon><User /></el-icon>{{ department || '未设置部门' }}</span>
          <span class="pill"><el-icon><Clock /></el-icon>{{ now }}</span>
        </p>
      </div>
      <div class="hero-deco">
        <div class="deco-ring r1"></div>
        <div class="deco-ring r2"></div>
        <div class="deco-ring r3"></div>
        <span class="hero-emoji">🚀</span>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mt-16">
      <el-col :span="6" v-for="(s, i) in stats" :key="i">
        <div class="stat-card" :style="`--c1: ${s.c1}; --c2: ${s.c2}`">
          <div class="stat-icon"><el-icon :size="22"><component :is="s.icon" /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">{{ s.label }}</div>
            <div class="stat-value">{{ s.value }}</div>
            <div class="stat-delta" :class="s.up ? 'up' : 'down'">
              {{ s.up ? '↑' : '↓' }} {{ s.delta }} <span>vs 昨日</span>
            </div>
          </div>
          <svg class="stat-spark" viewBox="0 0 100 30" preserveAspectRatio="none">
            <polyline :points="s.spark" fill="none" stroke="white" stroke-width="1.5" opacity="0.7" />
          </svg>
        </div>
      </el-col>
    </el-row>

    <!-- 主体：左 平台能力 + 快捷入口 / 中 实时活动 / 右 系统状态 -->
    <el-row :gutter="16" class="mt-16">
      <el-col :span="14">
        <el-card shadow="never" class="glass-card">
          <template #header>
            <div class="card-hd"><b>🚀 快捷入口</b><el-link type="primary" :underline="false">查看全部 →</el-link></div>
          </template>
          <div class="quick-grid">
            <div v-for="q in quickActions" :key="q.path" class="quick-tile" @click="$router.push(q.path)">
              <div class="qt-icon" :style="`background: linear-gradient(135deg, ${q.c1}, ${q.c2})`">
                <el-icon :size="24"><component :is="q.icon" /></el-icon>
              </div>
              <div class="qt-meta">
                <div class="qt-name">{{ q.name }}</div>
                <div class="qt-desc">{{ q.desc }}</div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="glass-card mt-16">
          <template #header><b>📦 平台能力</b></template>
          <el-collapse v-model="capOpen">
            <el-collapse-item title="AI 训练 / 推理全链路" name="1">
              自实现字符级 Transformer（NumPy only）→ ONNX bundle 导出 → Java 推理层加载（fastjson 解析，无需 PyTorch）。
              支持早停 / 学习率调度 / checkpoint 轮转 / 模型版本注册。
            </el-collapse-item>
            <el-collapse-item title="智能体 + 工具生态" name="2">
              ReAct 引擎 + Spring 自动发现的工具注册中心（{@code ToolRegistry}）+ 短期 / 长期记忆。
              内置工具：{@code calculator / web_search / knowledge_search / time} 等，多 Agent 案例可串行调用。
            </el-collapse-item>
            <el-collapse-item title="知识库 RAG" name="3">
              Elasticsearch 8 + Tika 文档解析 + 查询改写（中文 bigram + 同义词扩展）+ 结果重排（jaccard + MMR 去重）。
              带回退索引保证 ES 不可用时仍可工作。
            </el-collapse-item>
            <el-collapse-item title="企业级" name="4">
              多公司（租户）隔离 + JWT 鉴权 + 网关统一鉴权 + 限流 / 幂等 / 灰度 / traceId / 全局异常处理 / 启动健康探针。
              MyBatis-Plus 行级 tenant_id 过滤 + 自研 @IgnoreTenant 旁路注解。
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" class="glass-card">
          <template #header>
            <div class="card-hd">
              <b>⚡ 实时活动</b>
              <el-tag size="small" :type="sseStatus === 'open' ? 'success' : sseStatus === 'closed' ? 'danger' : 'info'">
                {{ sseStatus === 'open' ? '● 实时' : sseStatus === 'closed' ? '○ 离线' : '…' }}
              </el-tag>
            </div>
          </template>
          <div class="activity-list">
            <div v-for="(a, i) in activities" :key="i" class="activity-item">
              <div class="ai-dot" :style="`background: ${a.color}`"></div>
              <div class="ai-body">
                <div class="ai-text">{{ a.text }}</div>
                <div class="ai-meta">{{ a.actor }} · {{ a.ts }}</div>
              </div>
            </div>
            <el-empty v-if="!activities.length" description="暂无活动" :image-size="60" />
          </div>
        </el-card>

        <el-card shadow="never" class="glass-card mt-16">
          <template #header><b>🩺 系统健康</b></template>
          <div v-for="h in healths" :key="h.name" class="health-row">
            <span class="h-name">{{ h.name }}</span>
            <el-progress :percentage="h.value" :status="h.status" :stroke-width="8" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  Cpu, ChatDotRound, Reading, OfficeBuilding, DataAnalysis,
  VideoPlay, User, Clock, UserFilled, Setting, Promotion
} from '@element-plus/icons-vue'

const nickname = computed(() => localStorage.getItem('nickname') || localStorage.getItem('username') || '访客')
const tenantName = computed(() => localStorage.getItem('tenant_name') || '')
const department = computed(() => localStorage.getItem('department') || '')

const now = ref(new Date().toLocaleString('zh-CN'))
let clockTimer = null
onMounted(() => { clockTimer = setInterval(() => now.value = new Date().toLocaleString('zh-CN'), 1000) })
onBeforeUnmount(() => { if (clockTimer) clearInterval(clockTimer) })

const stats = ref([
  { label: '大模型数', value: '4', delta: '12%', up: true, icon: Cpu, c1: '#6366f1', c2: '#8b5cf6', spark: '0,25 20,18 40,22 60,12 80,15 100,5' },
  { label: '智能体数', value: '12', delta: '5%', up: true, icon: UserFilled, c1: '#ec4899', c2: '#f43f5e', spark: '0,20 20,22 40,15 60,18 80,8 100,10' },
  { label: '今日对话', value: '326', delta: '8%', up: true, icon: ChatDotRound, c1: '#06b6d4', c2: '#0ea5e9', spark: '0,18 20,12 40,20 60,8 80,15 100,3' },
  { label: '训练任务', value: '2', delta: '50%', up: false, icon: VideoPlay, c1: '#f59e0b', c2: '#ef4444', spark: '0,10 20,15 40,8 60,18 80,20 100,12' }
])

const quickActions = [
  { path: '/agents', name: '智能体', desc: 'ReAct 工具调用 + 多 Agent 案例', icon: UserFilled, c1: '#6366f1', c2: '#ec4899' },
  { path: '/chat/general', name: '对话', desc: '与智能体实时聊天', icon: ChatDotRound, c1: '#ec4899', c2: '#f43f5e' },
  { path: '/knowledge', name: '知识库', desc: 'RAG 检索 + 查询改写', icon: Reading, c1: '#10b981', c2: '#06b6d4' },
  { path: '/train', name: '训练', desc: '实时 loss / 幻觉 / 样本', icon: VideoPlay, c1: '#f59e0b', c2: '#ef4444' },
  { path: '/inference', name: '推理测试', desc: '本地 ONNX 模型推理', icon: Promotion, c1: '#8b5cf6', c2: '#3b82f6' },
  { path: '/models', name: '模型', desc: '模型注册 + 版本管理', icon: Cpu, c1: '#3b82f6', c2: '#06b6d4' }
]

const capOpen = ref(['1'])

const activities = ref([
  { text: 'admin 在 智能体 "调研助手" 触发了 web_search', actor: 'admin', ts: '刚刚', color: '#6366f1' },
  { text: '训练任务 job-001 完成，loss=2.31，幻觉=0.18', actor: 'trainer', ts: '2m 前', color: '#f59e0b' },
  { text: '知识库 KB-DEFAULT 新增 12 个文档分片', actor: 'knowledge', ts: '5m 前', color: '#10b981' },
  { text: 'demo 加入了 "市场部"', actor: 'system', ts: '1h 前', color: '#6b7280' }
])
const sseStatus = ref('init')

const healths = ref([
  { name: 'CPU 使用率', value: 38, status: 'success' },
  { name: '内存', value: 64, status: 'warning' },
  { name: '磁盘', value: 28, status: 'success' },
  { name: 'Nacos 连接', value: 95, status: 'success' }
])
</script>

<style scoped>
.dashboard { padding: 0; }

/* 欢迎横幅 */
.hero {
  position: relative;
  padding: 28px 32px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  border-radius: 20px;
  color: #fff; overflow: hidden;
  box-shadow: 0 10px 30px -10px rgba(99, 102, 241, 0.4);
}
.hero-title { font-size: 26px; font-weight: 700; margin: 0; }
.hero-sub { margin: 8px 0 0; display: flex; gap: 8px; flex-wrap: wrap; }
.pill {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.2); backdrop-filter: blur(10px);
  font-size: 12px;
}
.hero-deco { position: absolute; right: 30px; top: 50%; transform: translateY(-50%); }
.hero-emoji { font-size: 100px; position: relative; z-index: 2; }
.deco-ring { position: absolute; border: 2px solid rgba(255, 255, 255, 0.3); border-radius: 50%; }
.r1 { width: 140px; height: 140px; top: -20px; right: -20px; animation: ringPulse 4s ease-in-out infinite; }
.r2 { width: 180px; height: 180px; top: -40px; right: -40px; animation: ringPulse 4s ease-in-out infinite 0.5s; }
.r3 { width: 220px; height: 220px; top: -60px; right: -60px; animation: ringPulse 4s ease-in-out infinite 1s; }
@keyframes ringPulse { 0%, 100% { opacity: 0.3; transform: scale(1); } 50% { opacity: 0.6; transform: scale(1.05); } }

/* 统计卡片 */
.stat-card {
  position: relative; padding: 20px;
  border-radius: 16px; color: #fff; overflow: hidden;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  box-shadow: 0 8px 20px -6px rgba(0, 0, 0, 0.2);
  transition: transform 0.22s;
  cursor: pointer;
}
.stat-card:hover { transform: translateY(-4px); }
.stat-card .stat-icon { position: absolute; right: 16px; top: 16px; opacity: 0.4; font-size: 24px; }
.stat-label { font-size: 13px; opacity: 0.9; }
.stat-value { font-size: 32px; font-weight: 700; margin: 4px 0; letter-spacing: -1px; }
.stat-delta { font-size: 11px; opacity: 0.9; }
.stat-delta.up { color: #d1fae5; }
.stat-delta.down { color: #fecaca; }
.stat-delta span { opacity: 0.7; margin-left: 4px; }
.stat-spark { position: absolute; bottom: 0; left: 0; right: 0; height: 30px; opacity: 0.6; }

.glass-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
}
.card-hd { display: flex; justify-content: space-between; align-items: center; }

.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.quick-tile {
  display: flex; align-items: center; gap: 12px; padding: 14px;
  border-radius: 12px; background: #f8fafc; cursor: pointer; transition: all 0.2s;
}
.quick-tile:hover { background: #fff; transform: translateY(-2px); box-shadow: 0 6px 16px -4px rgba(0, 0, 0, 0.1); }
.qt-icon {
  width: 44px; height: 44px; border-radius: 12px; color: #fff;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.qt-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.qt-desc { font-size: 11px; color: #64748b; margin-top: 2px; }

.activity-list { max-height: 320px; overflow-y: auto; }
.activity-item { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px dashed #e2e8f0; }
.activity-item:last-child { border-bottom: none; }
.ai-dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.ai-text { font-size: 13px; color: #1e293b; }
.ai-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.health-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.h-name { width: 90px; font-size: 12px; color: #64748b; }
.health-row :deep(.el-progress) { flex: 1; }

.mt-16 { margin-top: 16px; }
</style>
