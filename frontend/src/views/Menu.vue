<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>📋 菜单管理</h2>
        <p class="muted">维护系统菜单树 — 给角色分配可见菜单, 决定前端侧边栏结构</p>
      </div>
      <el-button type="primary" @click="onCreate(null)">
        <el-icon><Plus /></el-icon>
        新建顶级菜单
      </el-button>
    </header>

    <el-card shadow="never" class="box">
      <el-table :data="tree" v-loading="loading" row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
        <el-table-column label="菜单名" min-width="220">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
            <strong>{{ row.menuName }}</strong>
            <small class="muted"> / {{ row.path }}</small>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 1 ? '' : 'info'" size="small">
              {{ row.menuType === 1 ? '菜单' : row.menuType === 2 ? '按钮' : '目录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="权限标识" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="可见" width="70">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">{{ row.visible === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button :underline="false" size="small" @click="onCreate(row)">添加子菜单</el-button>
            <el-button :underline="false" size="small" type="primary" @click="onEdit(row)">编辑</el-button>
            <el-button :underline="false" size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="editing.id ? '编辑菜单' : '新建菜单'" width="540px">
      <el-form :model="editing" label-width="100px" size="default">
        <el-form-item label="父菜单">
          <el-tree-select
            v-model="editing.parentId"
            :data="parentOpts"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="顶级菜单"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="菜单名">
          <el-input v-model="editing.menuName" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="editing.path" placeholder="/xxx" />
        </el-form-item>
        <el-form-item label="组件">
          <el-input v-model="editing.component" placeholder="views/Xxx.vue" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="editing.icon" placeholder="Element Plus icon name" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="editing.permission" placeholder="user:list" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="editing.menuType">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editing.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="可见">
          <el-switch v-model="editing.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editing.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { menuApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const saving = ref(false)
const tree = ref([])
const parentOpts = ref([])

const dlg = ref(false)
const editing = ref({ id: null, parentId: 0, menuName: '', path: '', component: '', icon: '', permission: '', menuType: 1, sortOrder: 0, visible: 1, status: 1 })

const load = async () => {
  loading.value = true
  try {
    const r = await menuApi.tree()
    tree.value = r.data || []
    // parent 选项 = 顶级菜单 (parentId=0)
    parentOpts.value = [{ id: 0, menuName: '顶级菜单', children: tree.value }]
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}

const onCreate = (parent) => {
  editing.value = { id: null, parentId: parent ? parent.id : 0, menuName: '', path: '', component: '', icon: '', permission: '', menuType: parent ? 1 : 0, sortOrder: 0, visible: 1, status: 1 }
  dlg.value = true
}
const onEdit = (row) => {
  editing.value = { ...row }
  dlg.value = true
}
const onSave = async () => {
  saving.value = true
  try {
    if (editing.value.id) {
      await menuApi.update(editing.value)
    } else {
      await menuApi.create(editing.value)
    }
    ElMessage.success('保存成功')
    dlg.value = false
    bus.emit('sys:event', { text: `菜单 ${editing.value.menuName} 已保存` })
    load()
  } catch (e) { ElMessage.error('保存失败: ' + e.message) }
  saving.value = false
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`删除菜单 ${row.menuName}? (子菜单也会被删除)`, '确认', { type: 'warning' })
  await menuApi.remove(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `菜单 ${row.menuName} 已删除` })
  load()
}

onMounted(() => {
  load()
  bus.emit('sys:event', { text: '进入菜单管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }
.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
</style>
