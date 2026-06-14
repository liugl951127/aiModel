<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>大模型管理</b>
        <el-button type="primary" @click="openCreate">+ 新建模型</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="modelCode" label="编码" width="160" />
        <el-table-column prop="modelName" label="名称" />
        <el-table-column prop="modelType" label="类型" width="120" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="framework" label="框架" width="120" />
        <el-table-column prop="language" label="语言" width="80" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="exportModel(row)">导出 ONNX</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建模型" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="模型名称"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.modelType" placeholder="llm" /></el-form-item>
        <el-form-item label="基础模型"><el-input v-model="form.baseModel" placeholder="minigpt" /></el-form-item>
        <el-form-item label="框架"><el-input v-model="form.framework" placeholder="numpy" /></el-form-item>
        <el-form-item label="语言"><el-input v-model="form.language" placeholder="zh" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ modelName: '', modelType: 'llm', baseModel: 'minigpt', framework: 'numpy', language: 'zh', description: '' })

const load = async () => {
  loading.value = true
  try {
    const resp = await modelApi.list()
    rows.value = resp.data || []
  } finally { loading.value = false }
}

const openCreate = () => { dialog.value = true }
const submit = async () => {
  await modelApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除模型 ${row.modelName}?`, '提示')
  await modelApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}
const exportModel = async (row) => {
  const resp = await modelApi.export(row.id)
  ElMessage.success('已导出: ' + (resp.data || ''))
}
const statusType = (s) => ({ draft: 'info', training: 'warning', ready: 'success', failed: 'danger' }[s] || 'info')

onMounted(load)
</script>
