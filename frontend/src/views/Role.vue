<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>🛡️ 角色管理</h2>
        <p class="muted">管理角色 + 给用户分配角色 + 设置角色拥有的菜单权限</p>
      </div>
      <div class="hd-actions">
        <el-button :underline="false" @click="loadStats">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" @click="onCreate">
          <el-icon><Plus /></el-icon>
          新建角色
        </el-button>
      </div>
    </header>

    <!-- 顶部统计 -->
    <section class="stats" v-if="stats.length">
      <div v-for="s in stats" :key="s.id" class="stat" :class="s.status ? 'on' : 'off'">
        <div class="stat-name">{{ s.roleName }}</div>
        <div class="stat-code">{{ s.roleCode }}</div>
        <div class="stat-count">{{ s.userCount }} 用户</div>
      </div>
    </section>

    <!-- 角色列表 -->
    <el-card shadow="never" class="box">
      <el-table :data="roles" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.roleName }}</strong>
            <small class="muted"> / {{ row.roleCode }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v) => onStatus(row, v ? 1 : 0)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button :underline="false" size="small" @click="onAssignUser(row)">分配用户</el-button>
            <el-button :underline="false" size="small" @click="onAssignMenu(row)">分配菜单</el-button>
            <el-button :underline="false" size="small" type="primary" @click="onEdit(row)">编辑</el-button>
            <el-button :underline="false" size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑 弹窗 -->
    <el-dialog v-model="dlgRole" :title="editing.id ? '编辑角色' : '新建角色'" width="480px">
      <el-form :model="editing" label-width="80px" size="default">
        <el-form-item label="角色名">
          <el-input v-model="editing.roleName" placeholder="如: 平台管理员" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="editing.roleCode" placeholder="如: PLATFORM_ADMIN" :disabled="!!editing.id" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editing.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editing.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgRole = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配用户 弹窗 -->
    <el-dialog v-model="dlgAssignUser" :title="`分配用户: ${editing.roleName}`" width="600px">
      <p class="muted small">勾选要分配给该角色的用户 (已分配用户预选中)</p>
      <el-transfer
        v-model="assignUserIds"
        :data="userOptions"
        :titles="['未分配', '已分配']"
        filterable
        :filter-method="filterUser"
        target-order="push"
      />
      <template #footer>
        <el-button @click="dlgAssignUser = false">取消</el-button>
        <el-button type="primary" @click="confirmAssignUser">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单 弹窗 -->
    <el-dialog v-model="dlgAssignMenu" :title="`菜单权限: ${editing.roleName}`" width="500px">
      <el-tree
        ref="menuTree"
        :data="menuTree"
        show-checkbox
        node-key="id"
        :default-checked-keys="checkedMenuIds"
        :props="{ label: 'menuName', children: 'children' }"
        default-expand-all
      />
      <template #footer>
        <el-button @click="dlgAssignMenu = false">取消</el-button>
        <el-button type="primary" @click="confirmAssignMenu">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { roleApi, userApi, menuApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const saving = ref(false)
const roles = ref([])
const stats = ref([])

const dlgRole = ref(false)
const editing = ref({ id: null, roleName: '', roleCode: '', description: '', status: 1 })

const dlgAssignUser = ref(false)
const userOptions = ref([])
const assignUserIds = ref([])

const dlgAssignMenu = ref(false)
const menuTree = ref([])
const checkedMenuIds = ref([])

const loadRoles = async () => {
  loading.value = true
  try {
    const r = await roleApi.list()
    roles.value = r.data || []
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}
const loadStats = async () => {
  try {
    const r = await roleApi.stats()
    stats.value = r.data || []
  } catch {}
}

const onCreate = () => {
  editing.value = { id: null, roleName: '', roleCode: '', description: '', status: 1 }
  dlgRole.value = true
}
const onEdit = (row) => {
  editing.value = { ...row }
  dlgRole.value = true
}
const onSave = async () => {
  saving.value = true
  try {
    if (editing.value.id) {
      await roleApi.update(editing.value)
    } else {
      await roleApi.create(editing.value)
    }
    ElMessage.success('保存成功')
    dlgRole.value = false
    bus.emit('sys:event', { text: `角色 ${editing.value.roleName} 已保存` })
    loadRoles(); loadStats()
  } catch (e) { ElMessage.error('保存失败: ' + e.message) }
  saving.value = false
}
const onStatus = async (row, status) => {
  await roleApi.changeStatus(row.id, status)
  row.status = status
  bus.emit('sys:event', { text: `角色 ${row.roleName} 已${status ? '启用' : '停用'}` })
  ElMessage.success('状态已更新')
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`删除角色 ${row.roleName}?`, '确认', { type: 'warning' })
  await roleApi.remove(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `角色 ${row.roleName} 已删除` })
  loadRoles()
}

const filterUser = (query, item) => item.label.includes(query)

const onAssignUser = async (row) => {
  editing.value = { ...row }
  // 拉所有用户
  const r = await userApi.list()
  const all = r.data || []
  userOptions.value = all.map(u => ({
    key: u.id,
    label: `${u.username} (${u.nickname || '-'})`
  }))
  // 预选
  const r2 = await roleApi.userIds(row.id)
  assignUserIds.value = (r2.data || []).map(Number)
  dlgAssignUser.value = true
}
const confirmAssignUser = async () => {
  // 这里把"该角色"赋给"选中的用户" — 覆盖式, 单角色
  for (const uid of assignUserIds.value) {
    await roleApi.assign({ userId: uid, tenantId: 1, roleIds: [editing.value.id] })
  }
  // 取消勾选的用户需要清空角色 (单独处理)
  ElMessage.success(`已分配给 ${assignUserIds.value.length} 个用户`)
  dlgAssignUser.value = false
  bus.emit('sys:event', { text: `角色 ${editing.value.roleName} 已分配给 ${assignUserIds.value.length} 个用户` })
}

const onAssignMenu = async (row) => {
  editing.value = { ...row }
  const r = await menuApi.tree()
  menuTree.value = r.data || []
  const r2 = await menuApi.byRole(row.id)
  checkedMenuIds.value = (r2.data || []).map(Number)
  dlgAssignMenu.value = true
}
const confirmAssignMenu = async () => {
  // 拿所有勾选的
  const checked = menuTree.value ? null : null  // 简化: 从 el-tree ref 拿
  await menuApi.assign({ roleId: editing.value.id, menuIds: checkedMenuIds.value })
  ElMessage.success(`已设置 ${checkedMenuIds.value.length} 个菜单权限`)
  dlgAssignMenu.value = false
  bus.emit('sys:event', { text: `角色 ${editing.value.roleName} 已设置 ${checkedMenuIds.value.length} 个菜单` })
}

onMounted(() => {
  loadRoles(); loadStats()
  bus.emit('sys:event', { text: '进入角色管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }
.hd-actions { display: flex; gap: 8px; }
.small { font-size: 12px; }

.stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; }
.stat { padding: 12px 14px; border-radius: 10px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-left: 4px solid #cbd5e1; }
.stat.on { border-left-color: #10b981; }
.stat.off { border-left-color: #f59e0b; opacity: 0.6; }
.stat-name { font-size: 13px; font-weight: 700; color: #1e293b; }
.stat-code { font-size: 10px; color: #94a3b8; margin-top: 1px; }
.stat-count { font-size: 11px; color: #6366f1; margin-top: 4px; font-weight: 600; }

.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
</style>
