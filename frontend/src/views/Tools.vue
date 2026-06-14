<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>工具注册</b>
        <el-button type="primary" @click="openCreate">+ 新建工具</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="toolCode" label="编码" width="160" />
        <el-table-column prop="toolName" label="名称" />
        <el-table-column prop="toolType" label="类型" width="120" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建工具" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="工具名"><el-input v-model="form.toolName" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.toolType" placeholder="http" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="入口 URL"><el-input v-model="form.endpoint" /></el-form-item>
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
import { ElMessage } from 'element-plus'
import { toolApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ toolName: '', toolType: 'http', description: '', endpoint: '' })

const load = async () => {
  loading.value = true
  try {
    const resp = await toolApi.list()
    rows.value = resp.data || []
  } finally { loading.value = false }
}
const openCreate = () => { dialog.value = true }
const submit = async () => {
  await toolApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}
onMounted(load)
</script>
