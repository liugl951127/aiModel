<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>租户</b>
        <el-button type="primary" @click="dialog = true">+ 新建租户</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tenantCode" label="编码" width="160" />
        <el-table-column prop="tenantName" label="名称" />
        <el-table-column prop="contactName" label="联系人" />
        <el-table-column prop="contactEmail" label="邮箱" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建租户" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.tenantCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.contactEmail" /></el-form-item>
        <el-form-item label="套餐"><el-input v-model="form.planCode" placeholder="free" /></el-form-item>
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
import { tenantApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ tenantCode: '', tenantName: '', contactName: '', contactEmail: '', planCode: 'free' })

const load = async () => {
  loading.value = true
  try {
    const resp = await tenantApi.list()
    rows.value = resp.data || []
  } finally { loading.value = false }
}
const submit = async () => {
  await tenantApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}
onMounted(load)
</script>
