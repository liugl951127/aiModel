<template>
  <div class="inf-page">
    <header class="inf-hero">
      <div class="hero-l">
        <h1>🔮 推理测试</h1>
        <p>直接调 ONNX 推理服务, 流式 / 非流式 / Embedding 都支持</p>
      </div>
      <div class="hero-r">
        <el-tag :type="health.ok ? 'success' : 'danger'" size="large" effect="dark">
          <el-icon><Connection /></el-icon>
          {{ health.ok ? `已连接 (${health.latency}ms)` : '未连接' }}
        </el-tag>
      </div>
    </header>

    <div class="inf-grid">
      <!-- 左: 模型选择 + 参数 -->
      <el-card shadow="never" class="inf-config">
        <h3>⚙️ 推理配置</h3>
        <el-form label-position="top" size="default">
          <el-form-item label="模型">
            <el-select v-model="form.model" placeholder="选择模型" style="width: 100%" @change="onModelChange">
              <el-option v-for="m in models" :key="m.modelCode" :label="`${m.modelName} (${m.modelCode})`" :value="m.modelCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="任务类型">
            <el-radio-group v-model="form.task">
              <el-radio-button label="generate">生成</el-radio-button>
              <el-radio-button label="chat">对话</el-radio-button>
              <el-radio-button label="embed">Embedding</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="最大长度">
            <el-input-number v-model="form.maxTokens" :min="10" :max="2048" :step="50" style="width: 100%" />
          </el-form-item>
          <el-form-item label="温度">
            <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
          </el-form-item>
          <el-form-item label="Top-p">
            <el-slider v-model="form.topP" :min="0" :max="1" :step="0.05" show-input />
          </el-form-item>
          <el-form-item v-if="form.task === 'chat'" label="系统提示">
            <el-input v-model="form.systemPrompt" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="loading" @click="run" style="width: 100%" size="large">
          <el-icon><VideoPlay /></el-icon> 运行推理
        </el-button>
      </el-card>

      <!-- 右: 输入 / 输出 -->
      <el-card shadow="never" class="inf-io">
        <el-tabs v-model="tab">
          <el-tab-pane label="📥 输入" name="input">
            <el-input v-model="form.prompt" type="textarea" :rows="8" placeholder="输入文本 / 问题" />
            <div class="quick-prompt">
              <span class="muted small">快捷测试:</span>
              <el-tag v-for="p in quickPrompts" :key="p" size="small" effect="plain" @click="form.prompt = p" style="cursor: pointer;">
                {{ p }}
              </el-tag>
            </div>
          </el-tab-pane>
          <el-tab-pane label="📤 输出" name="output">
            <div v-if="!result" class="muted" style="text-align: center; padding: 40px 0;">点击「运行推理」查看结果</div>
            <div v-else class="result-pane">
              <div class="result-meta">
                <el-tag size="small" :type="result.error ? 'danger' : 'success'">{{ result.error ? '失败' : '成功' }}</el-tag>
                <span class="muted small">耗时 {{ result.durationMs }}ms · 模型 {{ result.model }}</span>
                <el-button v-if="result.text" size="small" plain @click="copyText">复制</el-button>
              </div>
              <el-input v-if="form.task === 'embed'" :model-value="formatEmbed(result.embeddings)" type="textarea" :rows="10" readonly />
              <div v-else class="result-text">{{ result.text || result.response || JSON.stringify(result) }}</div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, VideoPlay } from '@element-plus/icons-vue'
import { modelApi, inferenceApi } from '@/api'

const models = ref([])
const health = ref({ ok: false, latency: 0 })
const loading = ref(false)
const tab = ref('input')
const form = reactive({
  model: 'minigpt',
  task: 'generate',
  maxTokens: 200,
  temperature: 0.7,
  topP: 0.9,
  systemPrompt: '你是友好的 AI 助手',
  prompt: ''
})
const result = ref(null)

const quickPrompts = [
  '你好',
  '介绍一下你自己',
  '用一句话总结 RAG 的原理',
  'Python 里 list 和 tuple 的区别'
]

const onModelChange = () => { /* TODO 加载模型参数 */ }

const loadModels = async () => {
  try {
    const r = await modelApi.list()
    if (r.code === 200) models.value = r.data || []
  } catch (e) {}
}

const ping = async () => {
  const t0 = Date.now()
  try {
    await inferenceApi.models()
    health.value = { ok: true, latency: Date.now() - t0 }
  } catch (e) {
    health.value = { ok: false, latency: 0 }
  }
}

const run = async () => {
  if (!form.prompt) { ElMessage.warning('请输入 prompt'); tab.value = 'input'; return }
  loading.value = true
  const t0 = Date.now()
  try {
    let r
    if (form.task === 'embed') {
      r = await inferenceApi.generate({ model: form.model, input: form.prompt, task: 'embed' })
    } else if (form.task === 'chat') {
      r = await inferenceApi.chat({
        model: form.model,
        messages: [
          { role: 'system', content: form.systemPrompt },
          { role: 'user', content: form.prompt }
        ],
        max_tokens: form.maxTokens,
        temperature: form.temperature,
        top_p: form.topP
      })
    } else {
      r = await inferenceApi.generate({
        model: form.model,
        prompt: form.prompt,
        max_tokens: form.maxTokens,
        temperature: form.temperature,
        top_p: form.topP
      })
    }
    result.value = { ...r.data, durationMs: Date.now() - t0, model: form.model, error: r.code !== 200 }
    tab.value = 'output'
  } catch (e) {
    result.value = { error: true, text: e.message, durationMs: Date.now() - t0 }
    tab.value = 'output'
  } finally {
    loading.value = false
  }
}

const copyText = () => {
  const text = result.value?.text || result.value?.response || ''
  navigator.clipboard?.writeText(text)
  ElMessage.success('已复制')
}

const formatEmbed = (emb) => {
  if (!emb || !emb.length) return '[]'
  if (Array.isArray(emb[0])) {
    return emb.map((v, i) => `[${i}] ${v.slice(0, 8).map(x => x.toFixed(4)).join(', ')}...`).join('\n')
  }
  return `[${emb.slice(0, 16).map(x => x.toFixed(4)).join(', ')}...]`
}

watch(() => form.model, ping)

import { useRoute } from 'vue-router'
const route = useRoute()
onMounted(() => {
  loadModels().then(() => {
    const modelCode = route.query.modelCode
    const modelId = route.query.modelId
    if (modelCode) {
      const m = models.value.find(x => x.id === modelCode || x.code === modelCode || x.modelCode === modelCode)
      if (m) { form.model = m.id; ElMessage.success('已自动选中模型: ' + (m.modelName || m.name || m.id)) }
    } else if (modelId) {
      const m = models.value.find(x => String(x.id) === String(modelId))
      if (m) { form.model = m.id; ElMessage.success('已自动选中模型: ' + (m.modelName || m.name || m.id)) }
    }
  })
  ping()
})
</script>

<style scoped>
.inf-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.inf-hero { display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; background: linear-gradient(135deg, #4facfe, #00f2fe); border-radius: 12px; color: #fff; }
.inf-hero h1 { margin: 0 0 4px; font-size: 22px; }
.inf-hero p { margin: 0; opacity: 0.9; font-size: 13px; }
.inf-grid { display: grid; grid-template-columns: 380px 1fr; gap: 12px; }
.inf-config h3, .inf-io h3 { margin: 0 0 8px; color: #6366f1; font-size: 14px; }
.quick-prompt { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; margin-top: 8px; }
.quick-prompt .muted { margin-right: 4px; }
.result-pane { padding: 4px; }
.result-meta { display: flex; align-items: center; gap: 8px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.result-text { padding: 12px; background: #f8fafc; border-radius: 8px; font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.muted { color: #94a3b8; }
.small { font-size: 11px; }
</style>
