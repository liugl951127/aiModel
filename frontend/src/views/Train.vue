<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>训练任务</b>
        <el-button type="primary" @click="openSubmit">+ 提交训练</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="jobCode" label="任务编号" width="160" />
        <el-table-column prop="modelId" label="模型 ID" width="100" />
        <el-table-column prop="algorithm" label="算法" width="160" />
        <el-table-column prop="epochs" label="轮次" width="80" />
        <el-table-column prop="batchSize" label="Batch" width="80" />
        <el-table-column prop="learningRate" label="学习率" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="提交训练" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="模型 ID"><el-input-number v-model="form.modelId" :min="1" /></el-form-item>
        <el-form-item label="数据集 ID"><el-input-number v-model="form.datasetId" :min="1" /></el-form-item>
        <el-form-item label="算法"><el-input v-model="form.algorithm" /></el-form-item>
        <el-form-item label="轮次"><el-input-number v-model="form.epochs" :min="1" /></el-form-item>
        <el-form-item label="Batch Size"><el-input-number v-model="form.batchSize" :min="1" /></el-form-item>
        <el-form-item label="学习率"><el-input-number v-model="form.learningRate" :step="0.0001" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">启动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, h } from 'vue'
import { ElMessage } from 'element-plus'
import { trainApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ modelId: 1, datasetId: 1, algorithm: 'causal-lm-finetune', epochs: 3, batchSize: 8, learningRate: 0.00005 })

const load = async () => {
  loading.value = true
  try {
    const resp = await trainApi.page({ current: 1, size: 20 })
    rows.value = resp.records || []
  } finally { loading.value = false }
}

const openSubmit = () => { dialog.value = true }
const submit = async () => {
  await trainApi.submit(form)
  ElMessage.success('训练已启动')
  dialog.value = false
  load()
}

const tagType = (s) => ({
  queued: 'info', running: 'warning', succeeded: 'success', failed: 'danger'
}[s] || '')

onMounted(load)
</script>
