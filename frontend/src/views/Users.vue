<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>👥 用户管理</h2>
        <p class="muted">用户 CRUD · 重置密码 · 启停账号 · 改密码</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建用户
      </el-button>
    </header>

    <!-- 统计 -->
    <section class="stats">
      <div class="stat s-total"><span class="s-ico">📊</span><div><div class="sv">{{ stats.total || 0 }}</div><div class="sl">总数</div></div></div>
      <div class="stat s-active"><span class="s-ico">🟢</span><div><div class="sv">{{ stats.active || 0 }}</div><div class="sl">启用</div></div></div>
      <div class="stat s-inactive"><span class="s-ico">⚫</span><div><div class="sv">{{ stats.inactive || 0 }}</div><div class="sl">停用</div></div></div>
      <div class="stat s-month"><span class="s-ico">🆕</span><div><div class="sv">{{ stats.thisMonth || 0 }}</div><div class="sl">本月新增</div></div></div>
    </section>

    <el-card shadow="never" class="box">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <strong>{{ row.username }}</strong>
            <small v-if="row.nickname" class="muted"> ({{ row.nickname }})</small>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="手机" width="120" />
        <el-table-column prop="department" label="部门" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v) => onStatus(row, v ? 1 : 0)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button :underline="false" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button :underline="false" size="small" @click="onChangePwd(row)">改密</el-button>
            <el-button :underline="false" size="small" type="warning" @click="onResetPwd(row)">重置</el-button>
            <el-button :underline="false" size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑用户' : '新建用户'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="form.department" /></el-form-item>
        <el-form-item v-if="!form.id" label="密码"><el-input v-model="form.password" placeholder="默认 123456" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 改密 -->
    <el-dialog v-model="dlgPwd" :title="`修改密码: ${pwdRow?.username}`" width="420px">
      <el-form :model="pwdForm" label-width="100px">
        <el-form-item label="原密码"><el-input v-model="pwdForm.oldPwd" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPwd" type="password" show-password /></el-form-item>
        <el-form-item label="确认"><el-input v-model="pwdForm.cfm" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgPwd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPwd">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { userApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const stats = ref({})

const dlg = ref(false)
const form = reactive({ id: null, username: '', nickname: '', email: '', phone: '', department: '', password: '' })

const dlgPwd = ref(false)
const pwdRow = ref(null)
const pwdForm = reactive({ oldPwd: '', newPwd: '', cfm: '' })

const load = async () => {
  loading.value = true
  try {
    const resp = await userApi.page({ current: 1, size: 20 })
    rows.value = resp.records || resp.data?.records || []
  } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}
const loadStats = async () => {
  try { stats.value = (await userApi.stats()).data || {} } catch {}
}

const openCreate = () => {
  Object.assign(form, { id: null, username: '', nickname: '', email: '', phone: '', department: '', password: '' })
  dlg.value = true
}
const openEdit = (row) => {
  Object.assign(form, row)
  dlg.value = true
}
const submit = async () => {
  saving.value = true
  try {
    if (form.id) await userApi.update(form)
    else await userApi.create(form)
    ElMessage.success('已保存')
    dlg.value = false
    bus.emit('sys:event', { text: `用户 ${form.username} 已保存` })
    load(); loadStats()
  } catch (e) { ElMessage.error('保存失败') }
  saving.value = false
}

const onStatus = async (row, status) => {
  await userApi.changeStatus(row.id, status)
  row.status = status
  ElMessage.success('已更新')
  bus.emit('sys:event', { text: `用户 ${row.username} 已${status ? '启用' : '停用'}` })
  loadStats()
}

const onChangePwd = (row) => {
  pwdRow.value = row
  Object.assign(pwdForm, { oldPwd: '', newPwd: '', cfm: '' })
  dlgPwd.value = true
}
const submitPwd = async () => {
  if (pwdForm.newPwd !== pwdForm.cfm) return ElMessage.error('两次密码不一致')
  await userApi.changePassword(pwdRow.value.id, { oldPwd: pwdForm.oldPwd, newPwd: pwdForm.newPwd })
  ElMessage.success('密码已修改')
  dlgPwd.value = false
  bus.emit('sys:event', { text: `用户 ${pwdRow.value.username} 密码已修改` })
}

const onResetPwd = async (row) => {
  await ElMessageBox.confirm(`重置 ${row.username} 的密码为 123456?`, '确认', { type: 'warning' })
  await userApi.resetPassword(row.id)
  ElMessage.success('已重置为 123456')
  bus.emit('sys:event', { text: `用户 ${row.username} 密码已重置` })
}

const remove = async (row) => {
  await ElMessageBox.confirm(`删除用户 ${row.username}?`, '确认', { type: 'warning' })
  await userApi.remove(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `用户 ${row.username} 已删除` })
  load(); loadStats()
}

onMounted(() => {
  load(); loadStats()
  bus.emit('sys:event', { text: '进入用户管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }
.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 10px; color: #fff; }
.stat .s-ico { font-size: 24px; }
.stat .sv { font-size: 22px; font-weight: 700; line-height: 1; }
.stat .sl { font-size: 11px; opacity: 0.9; margin-top: 2px; }
.s-total { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.s-active { background: linear-gradient(135deg, #10b981, #047857); }
.s-inactive { background: linear-gradient(135deg, #64748b, #334155); }
.s-month { background: linear-gradient(135deg, #f59e0b, #d97706); }
</style>
