<template>
  <div class="page-container">
    <el-card>
      <template #header><b>本地模型推理测试</b></template>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        已加载模型：<b>{{ Object.keys(models).join(', ') || '(无)' }}</b>
      </el-alert>
      <el-form>
        <el-form-item label="模型">
          <el-select v-model="form.modelCode">
            <el-option v-for="m in Object.keys(models)" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="Prompt">
          <el-input v-model="form.prompt" type="textarea" :rows="3" placeholder="输入文本" />
        </el-form-item>
        <el-form-item label="Tokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="512" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input-number v-model="form.temperature" :step="0.1" :min="0" :max="2" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="generate">生成</el-button>
      </el-form>
      <el-divider>结果</el-divider>
      <pre class="result">{{ result }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { inferenceApi } from '@/api'

const models = ref({})
const loading = ref(false)
const result = ref('')
const form = reactive({ modelCode: 'default', prompt: '你好', maxTokens: 50, temperature: 0.8 })

const load = async () => {
  try {
    const r = await inferenceApi.models()
    models.value = r.data || {}
    if (!form.modelCode && Object.keys(models.value).length) form.modelCode = Object.keys(models.value)[0]
  } catch (e) { /* offline */ }
}
const generate = async () => {
  loading.value = true
  try {
    const r = await inferenceApi.generate(form)
    result.value = JSON.stringify(r.data, null, 2)
  } catch (e) {
    ElMessage.error('推理失败: ' + (e.message || ''))
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.result { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 6px; min-height: 80px; white-space: pre-wrap; word-break: break-all; }
</style>
