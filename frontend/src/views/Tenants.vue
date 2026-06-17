<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>🏢 租户 (公司) 管理</h2>
        <p class="muted">公司 CRUD · 启停 · 套餐 · 用户绑定</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建公司
      </el-button>
    </header>

    <section class="stats">
      <div class="stat s-total"><span class="s-ico">📊</span><div><div class="sv">{{ stats.total || 0 }}</div><div class="sl">总数</div></div></div>
      <div class="stat s-active"><span class="s-ico">🟢</span><div><div class="sv">{{ stats.active || 0 }}</div><div class="sl">启用</div></div></div>
      <div class="stat s-bind"><span class="s-ico">🔗</span><div><div class="sv">{{ stats.userBindings || 0 }}</div><div class="sl">用户绑定</div></div></div>
    </section>

    <el-card shadow="never" class="box">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="公司" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.tenantName }}</strong>
            <small class="muted"> / {{ row.tenantCode }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="180" />
        <el-table-column prop="planCode" label="套餐" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.planCode === 'enterprise' ? 'success' : 'info'">{{ row.planCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxUsers" label="用户上限" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v) => onStatus(row, v ? 1 : 0)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button underline="never" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button underline="never" size="small" @click="onShowUsers(row)">用户 ({{ row.userCount || 0 }})</el-button>
            <el-button underline="never" size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑公司' : '新建公司'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.tenantCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.contactEmail" /></el-form-item>
        <el-form-item label="套餐">
          <el-radio-group v-model="form.planCode">
            <el-radio value="free">免费</el-radio>
            <el-radio value="pro">专业</el-radio>
            <el-radio value="enterprise">企业</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户上限"><el-input-number v-model="form.maxUsers" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlgUsers" :title="`${usersTenant?.tenantName} 的用户`" width="600px">
      <el-table :data="users" size="small">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="department" label="部门" />
        <el-table-column prop="roleInTenant" label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.roleInTenant }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { tenantApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const stats = ref({})

const dlg = ref(false)
const form = reactive({ id: null, tenantCode: '', tenantName: '', contactName: '', contactPhone: '', contactEmail: '', planCode: 'free', maxUsers: 5 })

const dlgUsers = ref(false)
const users = ref([])
const usersTenant = ref(null)

const load = async () => {
  loading.value = true
  try { rows.value = (await tenantApi.list()).data || [] } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}
const loadStats = async () => {
  try { stats.value = (await tenantApi.stats()).data || {} } catch {}
}

const openCreate = () => {
  Object.assign(form, { id: null, tenantCode: '', tenantName: '', contactName: '', contactPhone: '', contactEmail: '', planCode: 'free', maxUsers: 5 })
  dlg.value = true
}
const openEdit = (row) => { Object.assign(form, row); dlg.value = true }
const submit = async () => {
  saving.value = true
  try {
    if (form.id) await tenantApi.update(form)
    else await tenantApi.create(form)
    ElMessage.success('已保存')
    dlg.value = false
    bus.emit('sys:event', { text: `公司 ${form.tenantName} 已保存` })
    load(); loadStats()
  } catch (e) { ElMessage.error('保存失败') }
  saving.value = false
}
const onStatus = async (row, status) => {
  await tenantApi.changeStatus(row.id, status)
  row.status = status
  bus.emit('sys:event', { text: `公司 ${row.tenantName} 已${status ? '启用' : '停用'}` })
  ElMessage.success('已更新')
  loadStats()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`删除公司 ${row.tenantName}? (会解除该公司的所有用户绑定)`, '确认', { type: 'warning' })
  await tenantApi.remove(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `公司 ${row.tenantName} 已删除` })
  load(); loadStats()
}
const onShowUsers = async (row) => {
  usersTenant.value = row
  users.value = (await tenantApi.listUsers(row.id)).data || []
  dlgUsers.value = true
}

onMounted(() => {
  load(); loadStats()
  bus.emit('sys:event', { text: '进入租户管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }
.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.stat { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 10px; color: #fff; }
.stat .s-ico { font-size: 24px; }
.stat .sv { font-size: 22px; font-weight: 700; line-height: 1; }
.stat .sl { font-size: 11px; opacity: 0.9; margin-top: 2px; }
.s-total { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.s-active { background: linear-gradient(135deg, #10b981, #047857); }
.s-bind { background: linear-gradient(135deg, #f59e0b, #d97706); }
</style>
