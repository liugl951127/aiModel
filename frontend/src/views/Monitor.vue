<template>
  <div class="monitor-page">
    <!-- 顶栏: 状态 + 倒计时 -->
    <div class="top-bar">
      <div class="left">
        <h2>📊 实时监控</h2>
        <el-tag :type="connected ? 'success' : 'danger'" effect="dark" size="small">
          <span class="dot" :class="{ pulse: connected }"></span>
          {{ connected ? '已连接' : '未连接' }} · {{ lastUpdate }}
        </el-tag>
      </div>
      <div class="right">
        <span class="stat-pill"><b>{{ counters.totalRequests || 0 }}</b> 请求</span>
        <span class="stat-pill"><b>{{ counters.totalErrors || 0 }}</b> 错误</span>
        <span class="stat-pill"><b>{{ counters.totalAiCalls || 0 }}</b> AI 调用</span>
        <span class="stat-pill"><b>{{ counters.totalWorkflowRuns || 0 }}</b> workflow</span>
        <el-button :icon="Refresh" @click="refreshNow" size="small" plain>立即刷新</el-button>
      </div>
    </div>

    <!-- 告警 -->
    <el-alert v-for="(a, i) in alerts" :key="i" :title="a.message" :type="a.level === 'critical' ? 'error' : 'warning'"
      :closable="false" show-icon style="margin-bottom: 10px;" />

    <!-- 服务网格 -->
    <h3 class="section-title">🖥️ 服务健康 (9 核心服务)</h3>
    <el-row :gutter="10" class="service-grid">
      <el-col v-for="s in services" :key="s.name" :xs="12" :sm="8" :md="6" :lg="4">
        <div class="svc-card" :class="'svc-' + s.status">
          <div class="svc-hd">
            <span class="svc-dot" :class="'dot-' + s.status"></span>
            <span class="svc-name">{{ s.name }}</span>
            <el-tag size="small" :type="statusType(s.status)" effect="plain">{{ statusLabel(s.status) }}</el-tag>
          </div>
          <div class="svc-detail">{{ s.detail || '—' }}</div>
          <div class="svc-meta">
            <span>{{ s.responseMs || 0 }}ms</span>
            <span>{{ ago(s.checkedAt) }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 时序图 (2 列) -->
    <h3 class="section-title">📈 实时指标 (60 个点 · 3s 间隔)</h3>
    <el-row :gutter="10">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="ch-title">QPS (请求/秒)</span></template>
          <v-chart :option="qpsOption" :autoresize="true" style="height: 220px;" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="ch-title">平均响应时间 (ms)</span></template>
          <v-chart :option="latencyOption" :autoresize="true" style="height: 220px;" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="ch-title">错误率 (%)</span></template>
          <v-chart :option="errOption" :autoresize="true" style="height: 220px;" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="ch-title">AI 调用 / workflow 跑次</span></template>
          <v-chart :option="aiOption" :autoresize="true" style="height: 220px;" />
        </el-card>
      </el-col>
    </el-row>

    <!-- AI + 业务 -->
    <el-row :gutter="10" class="mt-12">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="info-card">
          <template #header><span>🤖 AI 指标</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="已加载模型">{{ ai.modelsLoaded }}</el-descriptions-item>
            <el-descriptions-item label="活跃 workflow">{{ ai.activeWorkflows }}</el-descriptions-item>
            <el-descriptions-item label="今日跑次">{{ ai.runsToday }}</el-descriptions-item>
            <el-descriptions-item label="今日 token">{{ ai.tokensToday?.toLocaleString() || 0 }}</el-descriptions-item>
            <el-descriptions-item label="平均延迟">{{ ai.avgLatencyMs }} ms</el-descriptions-item>
            <el-descriptions-item label="错误率">{{ ((counters.errorRate || 0) * 100).toFixed(2) }}%</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="info-card">
          <template #header><span>📦 业务指标</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="活跃用户">{{ biz.activeUsers }}</el-descriptions-item>
            <el-descriptions-item label="在线用户">{{ biz.onlineUsers }}</el-descriptions-item>
            <el-descriptions-item label="今日订单">{{ biz.todayOrders }}</el-descriptions-item>
            <el-descriptions-item label="今日查询">{{ biz.todayQueries }}</el-descriptions-item>
            <el-descriptions-item label="总请求">{{ counters.totalRequests }}</el-descriptions-item>
            <el-descriptions-item label="总错误">{{ counters.totalErrors }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时事件流 -->
    <h3 class="section-title">📡 实时事件流 (后端 SSE)</h3>
    <el-card shadow="never" class="event-card">
      <el-scrollbar height="220px" ref="scrollbar">
        <div v-for="(e, i) in liveEvents" :key="i" class="event-item" :class="e.type">
          <span class="ev-time">{{ formatTime(e.ts) }}</span>
          <el-tag size="small" :type="eventTypeColor(e.type)">{{ e.type }}</el-tag>
          <span class="ev-msg">{{ e.message }}</span>
        </div>
        <el-empty v-if="!liveEvents.length" description="暂无事件, 等待 SSE 推送..." />
      </el-scrollbar>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { monitorApi } from '@/api'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

// ============== 状态 ==============
const services = ref([])
const counters = reactive({})
const ai = reactive({})
const biz = reactive({})
const alerts = ref([])
const liveEvents = ref([])
const connected = ref(false)
const lastUpdate = ref('—')

let es = null
let refreshTimer = null

// ============== 图表数据 ==============
const seriesData = reactive({
  qps: [], latency: [], errRate: [], aiCalls: [], wfRuns: []
})

const baseChartOption = (color, yLabel) => ({
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'time', axisLabel: { color: '#94a3b8', fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { color: '#94a3b8', fontSize: 10 }, name: yLabel, nameTextStyle: { color: '#94a3b8' } },
  series: [{ type: 'line', smooth: true, showSymbol: false, lineStyle: { color, width: 2 }, areaStyle: { color: color + '22' } }]
})

const qpsOption = ref(markRaw(baseChartOption('#6366f1', 'QPS')))
const latencyOption = ref(markRaw(baseChartOption('#10b981', 'ms')))
const errOption = ref(markRaw(baseChartOption('#ef4444', '%')))
const aiOption = ref(markRaw({
  grid: { left: 50, right: 20, top: 30, bottom: 30 },
  tooltip: { trigger: 'axis' },
  legend: { textStyle: { color: '#64748b', fontSize: 11 } },
  xAxis: { type: 'time', axisLabel: { color: '#94a3b8', fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { color: '#94a3b8', fontSize: 10 } },
  series: [
    { name: 'AI 调用', type: 'line', smooth: true, showSymbol: false, lineStyle: { color: '#8b5cf6', width: 2 } },
    { name: 'workflow 跑次', type: 'line', smooth: true, showSymbol: false, lineStyle: { color: '#f59e0b', width: 2 } }
  ]
}))

const updateChart = () => {
  qpsOption.value = markRaw({ ...baseChartOption('#6366f1', 'QPS'), series: [{ ...baseChartOption('#6366f1', 'QPS').series[0], data: seriesData.qps }] })
  latencyOption.value = markRaw({ ...baseChartOption('#10b981', 'ms'), series: [{ ...baseChartOption('#10b981', 'ms').series[0], data: seriesData.latency }] })
  errOption.value = markRaw({ ...baseChartOption('#ef4444', '%'), series: [{ ...baseChartOption('#ef4444', '%').series[0], data: seriesData.errRate }] })
  aiOption.value = markRaw({
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#64748b', fontSize: 11 } },
    xAxis: { type: 'time', axisLabel: { color: '#94a3b8', fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { color: '#94a3b8', fontSize: 10 } },
    series: [
      { name: 'AI 调用', type: 'line', smooth: true, showSymbol: false, lineStyle: { color: '#8b5cf6', width: 2 }, data: seriesData.aiCalls },
      { name: 'workflow 跑次', type: 'line', smooth: true, showSymbol: false, lineStyle: { color: '#f59e0b', width: 2 }, data: seriesData.wfRuns }
    ]
  })
}

// ============== 工具 ==============
const statusType = (s) => ({ up: 'success', down: 'danger', warn: 'warning', unknown: 'info' }[s] || 'info')
const statusLabel = (s) => ({ up: '正常', down: '离线', warn: '告警', unknown: '未知' }[s] || s)
const eventTypeColor = (t) => ({ service: 'primary', metric: 'success', alert: 'danger' }[t] || 'info')

const ago = (ts) => {
  if (!ts) return '—'
  const diff = Math.floor((Date.now() - ts) / 1000)
  if (diff < 60) return `${diff}s 前`
  if (diff < 3600) return `${Math.floor(diff / 60)}m 前`
  return `${Math.floor(diff / 3600)}h 前`
}

const formatTime = (ts) => {
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

// ============== 数据加载 ==============
const loadSnapshot = async () => {
  try {
    const r = await monitorApi.snapshot()
    if (r.data?.code === 200) {
      const d = r.data.data
      services.value = d.services || []
      Object.assign(counters, d.counters || {})
      Object.assign(ai, d.ai || {})
      Object.assign(biz, d.business || {})
      alerts.value = d.alerts || []
      lastUpdate.value = formatTime(Date.now())
    }
  } catch (e) { /* ignore */ }
}

const loadMetrics = async () => {
  try {
    const r = await monitorApi.metrics()
    if (r.data?.code === 200) {
      const d = r.data.data
      seriesData.qps = (d.qps || []).map(p => [p.ts, p.value])
      seriesData.latency = (d.latency || []).map(p => [p.ts, p.value])
      seriesData.errRate = (d.errorRate || []).map(p => [p.ts, p.value * 100])
      seriesData.aiCalls = (d.aiCalls || []).map(p => [p.ts, p.value])
      seriesData.wfRuns = (d.workflowRuns || []).map(p => [p.ts, p.value])
      updateChart()
    }
  } catch (e) { /* ignore */ }
}

const openStream = () => {
  if (es) { es.close(); es = null }
  if (typeof EventSource === 'undefined') return
  es = new EventSource(monitorApi.streamUrl())
  es.onopen = () => { connected.value = true }
  es.onerror = () => { connected.value = false }
  es.addEventListener('snapshot', (e) => {
    try {
      const d = JSON.parse(e.data)
      if (d.services) services.value = d.services
      if (d.counters) Object.assign(counters, d.counters)
      lastUpdate.value = formatTime(Date.now())
    } catch {}
  })
  es.addEventListener('update', (e) => {
    try {
      const payload = JSON.parse(e.data)
      // 追加到事件流
      liveEvents.value.unshift({
        type: payload.type,
        message: payload.type === 'metric'
          ? `QPS=${payload.data.qps?.toFixed(0)} 延迟=${payload.data.latency?.toFixed(0)}ms 错误率=${(payload.data.errorRate * 100).toFixed(2)}%`
          : JSON.stringify(payload.data),
        ts: Date.now()
      })
      if (liveEvents.value.length > 50) liveEvents.value.length = 50
      // 同步 snapshot
      loadSnapshot()
    } catch {}
  })
}

const refreshNow = () => {
  loadSnapshot()
  loadMetrics()
}

// ============== 生命周期 ==============
onMounted(() => {
  refreshNow()
  openStream()
  // 30 秒刷一次 metrics
  refreshTimer = setInterval(loadMetrics, 30000)
})

onBeforeUnmount(() => {
  if (es) { es.close(); es = null }
  if (refreshTimer) clearInterval(refreshTimer)
})

defineOptions({ name: 'Monitor' })
</script>

<style scoped>
.monitor-page { padding: 12px; }

.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  background: linear-gradient(135deg, #1e293b, #0f172a);
  color: #fff;
  padding: 12px 18px;
  border-radius: 12px;
  margin-bottom: 16px;
}
.top-bar h2 { margin: 0; font-size: 18px; }
.left { display: flex; align-items: center; gap: 12px; }
.right { display: flex; align-items: center; gap: 8px; }
.stat-pill {
  background: rgba(255,255,255,0.1);
  color: #e2e8f0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}
.stat-pill b { color: #fbbf24; }

.dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #ef4444; margin-right: 4px; }
.dot.pulse { background: #10b981; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.5); } 50% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); } }

.section-title { font-size: 14px; color: #1e293b; margin: 16px 0 8px; font-weight: 600; }

.service-grid { margin-bottom: 8px; }
.svc-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
  transition: all 0.2s;
}
.svc-card:hover { box-shadow: 0 4px 8px -2px rgba(0,0,0,0.1); transform: translateY(-1px); }
.svc-down { border-color: #fca5a5; background: #fef2f2; }
.svc-up { border-color: #bbf7d0; }
.svc-warn { border-color: #fde68a; background: #fffbeb; }
.svc-hd { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.svc-dot { width: 8px; height: 8px; border-radius: 50%; }
.dot-up { background: #10b981; }
.dot-down { background: #ef4444; }
.dot-warn { background: #f59e0b; }
.dot-unknown { background: #94a3b8; }
.svc-name { font-weight: 600; font-size: 13px; flex: 1; }
.svc-detail { font-size: 11px; color: #64748b; margin: 2px 0; }
.svc-meta { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; }

.chart-card, .info-card, .event-card { margin-bottom: 10px; }
.ch-title { font-size: 13px; font-weight: 600; color: #1e293b; }

.event-card { margin-top: 12px; }
.event-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
}
.event-item:last-child { border-bottom: none; }
.event-item.service { background: #eef2ff; }
.event-item.metric { background: #ecfdf5; }
.event-item.alert { background: #fef2f2; }
.ev-time { color: #94a3b8; font-family: monospace; font-size: 11px; min-width: 70px; }
.ev-msg { color: #1e293b; flex: 1; }
</style>
