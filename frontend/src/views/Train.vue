<template>
  <div class="page-container train-page">
    <!-- 左：模型选择 + 超参数 -->
    <el-card class="left-pane" shadow="hover">
      <template #header>
        <div class="card-header">
          <b>训练控制台</b>
          <el-tag size="small" :type="connected ? 'success' : 'info'">
            {{ connected ? '● SSE 已连接' : '○ 未连接' }}
          </el-tag>
        </div>
      </template>

      <el-form label-width="100px" size="default">
        <el-form-item label="模型">
          <el-select v-model="form.trainerId" placeholder="选择模型" style="width:100%" @change="onModelChange">
            <el-option v-for="m in models" :key="m.id" :label="m.displayName" :value="m.id">
              <span style="float:left">{{ m.displayName }}</span>
              <span style="float:right; color:#999; font-size:12px">{{ m.id }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="语料路径">
          <el-input v-model="form.corpusPath" placeholder="/opt/corpus/sample.txt" />
        </el-form-item>

        <el-divider content-position="left">超参数</el-divider>

        <el-form-item v-for="p in currentHyperParams" :key="p.key" :label="p.label">
          <el-tooltip v-if="p.hint" :content="p.hint" placement="top">
            <div style="width:100%">
              <el-input-number
                v-if="p.type === 'int' || p.type === 'float'"
                v-model="form.params[p.key]"
                :min="p.min" :max="p.max" :step="p.step || 1"
                :precision="p.type === 'float' ? 4 : 0"
                style="width:100%" />
              <el-switch v-else-if="p.type === 'bool'"
                v-model="form.params[p.key]" />
              <el-select v-else-if="p.type === 'choice'"
                v-model="form.params[p.key]" style="width:100%">
                <el-option v-for="o in p.opts || (p.min === 0 ? p._opts : [])"
                  :key="o" :label="o" :value="o" />
              </el-select>
            </div>
          </el-tooltip>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="running" @click="onSubmit" :disabled="running">
            {{ running ? '训练中…' : '开始训练' }}
          </el-button>
          <el-button v-if="running" @click="onSample" :disabled="!jobId">生成样本</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 中：实时图表 + 步骤日志 -->
    <el-card class="center-pane" shadow="hover">
      <template #header>
        <div class="card-header">
          <b>训练指标</b>
          <span class="muted">Step {{ step }} · 任务 {{ jobId || '—' }}</span>
        </div>
      </template>

      <div class="charts">
        <div class="chart-box">
          <div class="chart-label">Loss</div>
          <v-chart class="chart" :option="lossOption" autoresize />
        </div>
        <div class="chart-box">
          <div class="chart-label">幻觉分数 (越低越好)</div>
          <v-chart class="chart" :option="hallOption" autoresize />
        </div>
        <div class="chart-box">
          <div class="chart-label">事实支撑 / 引用覆盖</div>
          <v-chart class="chart" :option="supportOption" autoresize />
        </div>
      </div>

      <el-divider content-position="left">实时事件</el-divider>
      <div ref="logBox" class="log-box">
        <div v-for="(e, i) in events" :key="i" :class="['log-line', e.type]">
          <span class="ts">[{{ e.ts }}]</span>
          <el-tag size="small" :type="tagType(e.type)">{{ e.type }}</el-tag>
          <span v-if="e.loss !== undefined">loss={{ e.loss.toFixed(4) }} </span>
          <span v-if="e.sample" class="sample">"{{ e.sample }}"</span>
          <span v-if="e.hall !== undefined" :class="hallClass(e.hall)">
            hallucination={{ e.hall.toFixed(2) }}
          </span>
        </div>
      </div>
    </el-card>

    <!-- 右：样本预览 + 审计 -->
    <el-card class="right-pane" shadow="hover">
      <template #header>
        <div class="card-header">
          <b>实时样本 & 审计</b>
        </div>
      </template>

      <el-form size="small">
        <el-form-item label="Prompt">
          <el-input v-model="samplePrompt" type="textarea" :rows="2"
            placeholder="输入 prompt，按【生成样本】即可触发实时生成" />
        </el-form-item>
        <el-form-item>
          <el-button @click="onSample" :disabled="!jobId || !running">生成样本</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">最近样本</el-divider>
      <div class="sample-box">
        <div v-for="(s, i) in samples" :key="i" class="sample-card">
          <div class="sample-meta">
            <el-tag size="small">step {{ s.step }}</el-tag>
            <span class="muted">{{ s.ts }}</span>
          </div>
          <div class="sample-text">{{ s.text }}</div>
        </div>
        <el-empty v-if="samples.length === 0" description="暂无样本" :image-size="60" />
      </div>

      <el-divider content-position="left">防幻觉审计</el-divider>
      <div class="audit-box">
        <div class="kv"><span>幻觉阈值</span><b>{{ (form.params.hallucinationThreshold ?? 0.7).toFixed(2) }}</b></div>
        <div class="kv"><span>当前幻觉分数</span>
          <b :class="hallClass(latestHall)">{{ latestHall.toFixed(2) }}</b></div>
        <div class="kv"><span>事实支撑</span><b>{{ (audit.factualSupport ?? 0).toFixed(2) }}</b></div>
        <div class="kv"><span>引用覆盖</span><b>{{ (audit.citationCoverage ?? 0).toFixed(2) }}</b></div>
        <div class="kv"><span>重复度</span><b>{{ (audit.repetition ?? 0).toFixed(2) }}</b></div>
        <div class="kv"><span>熵</span><b>{{ (audit.entropy ?? 0).toFixed(2) }}</b></div>
        <el-alert v-if="latestHall >= (form.params.hallucinationThreshold ?? 0.7)"
          type="warning" :closable="false" show-icon
          title="模型当前置信度不足，生成的内容可能存在幻觉" />
        <el-alert v-else type="success" :closable="false" show-icon
          title="当前置信度安全" />
      </div>
    </el-card>
  </div>
</template>

<script setup>

defineOptions({ name: 'Train' })

import { ref, reactive, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { trainerApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const models = ref([])
const form = reactive({
  trainerId: 'minigpt',
  corpusPath: '/opt/ai-platform/corpus/sample.txt',
  params: {}
})
const currentHyperParams = computed(() => {
  const m = models.value.find(x => x.id === form.trainerId)
  return m ? m.hyperParams : []
})

const jobId = ref('')
const running = ref(false)
const connected = ref(false)
const step = ref(0)
const events = ref([])
const samples = ref([])
const samplePrompt = ref('介绍一下人工智能')
const lossSeries = ref([])
const hallSeries = ref([])
const supportSeries = ref([])
const stepSeries = ref([])
const audit = reactive({ factualSupport: 0, citationCoverage: 0, repetition: 0, entropy: 0 })
const latestHall = ref(0)
const logBox = ref(null)
let es = null
const bus = useGlobalBus()

async function loadModels () {
  try {
    const r = await trainerApi.models()
    models.value = r.data || []
    if (models.value.length && !form.trainerId) form.trainerId = models.value[0].id
    onModelChange()
  } catch (e) { /* gateway may not be running; fall back to empty */ }
}

function onModelChange () {
  const m = models.value.find(x => x.id === form.trainerId)
  if (!m) return
  form.params = { ...(m.defaultParams || {}) }
}

async function onSubmit () {
  if (!form.corpusPath) { ElMessage.warning('请填写语料路径'); return }
  try {
    const r = await trainerApi.submit({
      trainerId: form.trainerId,
      corpusPath: form.corpusPath,
      params: form.params
    })
    jobId.value = r.data.jobId
    running.value = true
    lossSeries.value = []; hallSeries.value = []; supportSeries.value = []; stepSeries.value = []
    events.value = []; samples.value = []
    openStream()
  } catch (e) { ElMessage.error('提交失败') }
}

async function onSample () {
  if (!jobId.value) return
  try { await trainerApi.sample(jobId.value, samplePrompt.value, 60) }
  catch (e) { /* will be shown on stream */ }
}

function openStream () {
  if (es) { es.close(); es = null }
  if (!jobId.value) return
  es = new EventSource(trainerApi.streamUrl(jobId.value))
  es.onopen = () => { connected.value = true }
  es.onerror = () => { connected.value = false }
  const handler = (kind) => (ev) => {
    const data = JSON.parse(ev.data)
    step.value = data.step || step.value
    const hall = data.antiHallucination
      ? 0.5 * (data.antiHallucination.entropy || 0.5)
        + 0.2 * (data.antiHallucination.repetition || 0)
        + 0.3 * (1 - (data.antiHallucination.factualSupport || 0))
      : 0
    pushEvent({
      type: kind, ts: new Date().toLocaleTimeString(),
      loss: data.loss, sample: data.sample, hall
    })
    if (kind === 'step' || kind === 'warn' || kind === 'metric') {
      stepSeries.value.push(data.step)
      lossSeries.value.push([data.step, +data.loss.toFixed(4)])
      hallSeries.value.push([data.step, +hall.toFixed(3)])
      supportSeries.value.push([data.step, +(data.antiHallucination?.factualSupport ?? 0).toFixed(3)])
      latestHall.value = hall
      Object.assign(audit, data.antiHallucination || {})
    }
    if (kind === 'sample' && data.sample) {
      samples.value.unshift({ step: data.step, text: data.sample, ts: new Date().toLocaleTimeString() })
      if (samples.value.length > 20) samples.value.length = 20
      bus.emit('train:event', { text: `Step ${data.step} 生成样本: ${data.sample.slice(0, 30)}…`, actor: 'trainer' })
    }
    if (kind === 'done') {
      running.value = false
      connected.value = false
      bus.emit('train:event', { text: '训练任务完成', actor: 'trainer' })
    }
  }
  es.addEventListener('step', handler('step'))
  es.addEventListener('metric', handler('metric'))
  es.addEventListener('sample', handler('sample'))
  es.addEventListener('warn', handler('warn'))
  es.addEventListener('done', () => {
    running.value = false
    connected.value = false
  })
}

function pushEvent (e) {
  events.value.push(e)
  if (events.value.length > 200) events.value.shift()
  nextTick(() => {
    if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight
  })
}

function tagType (k) { return { step: '', metric: 'info', sample: 'success', warn: 'warning', done: 'primary' }[k] || '' }
function hallClass (h) { return h >= (form.params.hallucinationThreshold ?? 0.7) ? 'danger' : 'safe' }

const baseChart = (name) => ({
  grid: { left: 36, right: 12, top: 28, bottom: 24 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: stepSeries },
  yAxis: { type: 'value' },
  series: [{ name, type: 'line', smooth: true, data: [], showSymbol: false, areaStyle: { opacity: 0.15 } }]
})
const lossOption = computed(() => ({ ...baseChart('loss'), series: [{ ...baseChart('loss').series[0], data: lossSeries.value }] }))
const hallOption = computed(() => ({ ...baseChart('hallucination'), series: [{ ...baseChart('hallucination').series[0], data: hallSeries.value }] }))
const supportOption = computed(() => ({ ...baseChart('support'), series: [{ ...baseChart('support').series[0], data: supportSeries.value }] }))

// 当超参数变更时热推到后端
import { watch } from 'vue'
watch(() => ({ ...form.params }), (newP, oldP) => {
  if (!jobId.value || !running.value) return
  const delta = {}
  for (const k of Object.keys(newP)) {
    if (newP[k] !== (oldP?.[k])) delta[k] = newP[k]
  }
  if (Object.keys(delta).length) trainerApi.updateParams(jobId.value, delta).catch(() => {})
}, { deep: true })

onMounted(loadModels)
onBeforeUnmount(() => { if (es) es.close() })
</script>

<style scoped>
.train-page {
  display: grid;
  grid-template-columns: 360px 1fr 360px;
  gap: 12px;
  align-items: start;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.charts { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
.chart-box { border: 1px solid #ebeef5; border-radius: 4px; padding: 6px; }
.chart-label { font-size: 12px; color: #606266; margin-bottom: 4px; }
.chart { height: 140px; }
.log-box {
  max-height: 220px; overflow: auto; background: #1e1e1e; color: #d4d4d4;
  border-radius: 4px; padding: 8px; font-family: ui-monospace, Menlo, monospace; font-size: 12px;
}
.log-line { padding: 2px 0; }
.log-line.warn { color: #f56c6c; }
.log-line.sample { color: #67c23a; }
.log-line.done { color: #409eff; }
.ts { color: #888; margin-right: 4px; }
.sample { margin-left: 4px; }
.danger { color: #f56c6c; }
.safe { color: #67c23a; }
.sample-box { max-height: 260px; overflow: auto; }
.sample-card { border-left: 2px solid #409eff; padding: 4px 8px; margin-bottom: 8px; background: #f5f7fa; border-radius: 0 4px 4px 0; }
.sample-meta { display: flex; justify-content: space-between; margin-bottom: 4px; }
.sample-text { font-size: 13px; }
.audit-box .kv { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px dashed #eee; }
.audit-box { display: flex; flex-direction: column; gap: 4px; }
</style>
