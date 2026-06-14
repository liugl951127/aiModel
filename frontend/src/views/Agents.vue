<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>智能体</b>
        <el-button type="primary" @click="openCreate">+ 新建智能体</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="agentCode" label="编码" width="160" />
        <el-table-column prop="agentName" label="名称" />
        <el-table-column prop="modelCode" label="模型" width="120" />
        <el-table-column prop="tools" label="工具" />
        <el-table-column prop="maxSteps" label="最大步数" width="100" />
        <el-table-column prop="temperature" label="温度" width="80" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push(`/chat/${row.id}`)">对话</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建智能体" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.agentName" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.agentType" placeholder="react" /></el-form-item>
        <el-form-item label="模型编码"><el-input v-model="form.modelCode" placeholder="default" /></el-form-item>
        <el-form-item label="系统提示"><el-input v-model="form.systemPrompt" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="工具(逗号分隔)"><el-input v-model="form.tools" placeholder="calculator,current_time,knowledge_search" /></el-form-item>
        <el-form-item label="最大步数"><el-input-number v-model="form.maxSteps" :min="1" :max="20" /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="form.temperature" :step="0.1" :min="0" :max="2" /></el-form-item>
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
import { agentApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({
  agentName: '助手', agentType: 'react', modelCode: 'default',
  systemPrompt: '你是一个乐于助人的智能助手。',
  tools: 'calculator,current_time,knowledge_search',
  maxSteps: 5, temperature: 0.7
})

const load = async () => {
  loading.value = true
  try {
    const resp = await agentApi.list()
    rows.value = resp.data || []
  } finally { loading.value = false }
}
const openCreate = () => { dialog.value = true }
const submit = async () => {
  await agentApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`删除 ${row.agentName}?`, '提示')
  await agentApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
