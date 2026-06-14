<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>用户</b>
        <el-button type="primary" @click="openCreate">+ 新建用户</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建用户" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" placeholder="默认 123456" /></el-form-item>
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
import { userApi } from '@/api'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ username: '', nickname: '', email: '', phone: '', password: '' })

const load = async () => {
  loading.value = true
  try {
    const resp = await userApi.page({ current: 1, size: 20 })
    rows.value = resp.records || []
  } finally { loading.value = false }
}
const openCreate = () => { dialog.value = true }
const submit = async () => {
  await userApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`删除用户 ${row.username}?`, '提示')
  await userApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
