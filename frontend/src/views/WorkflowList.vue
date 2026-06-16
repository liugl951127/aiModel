<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>🔁 工作流管理</h2>
        <p class="muted">管理已保存的工作流定义 (复用 / 编辑 / 复制 / 查运行历史)</p>
      </div>
      <div class="hd-actions">
        <el-button :underline="false" @click="$router.push('/workflow')">
          <el-icon><Plus /></el-icon>
          编排新工作流
        </el-button>
        <el-button :underline="false" @click="loadSpecs">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </header>

    <el-card shadow="never" class="box">
      <el-table :data="specs" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="120" />
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="节点数" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.stepCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运行次数" width="100">
          <template #default="{ row }">
            <span :class="row.runCount > 0 ? 'hot' : 'cold'">{{ row.runCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="作者" width="120">
          <template #default="{ row }">
            <span v-if="row.author">@{{ row.author }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最后更新" width="170">
          <template #default="{ row }">
            <small class="muted">{{ row.updateTime }}</small>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button :underline="false" size="small" type="success" @click="onRun(row)">运行</el-button>
            <el-button :underline="false" size="small" @click="onDuplicate(row)">复制</el-button>
            <el-button :underline="false" size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="box mt-12">
      <h3 class="rh">📊 最近运行实例</h3>
      <el-table :data="runs" stripe size="small">
        <el-table-column prop="id" label="Run ID" width="140" />
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            {{ row.name || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">
            <small class="muted">{{ row.startedAt || '—' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">
            <small class="muted">{{ row.finishedAt || '—' }}</small>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { workflowApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const specs = ref([])
const runs = ref([])

const loadSpecs = async () => {
  loading.value = true
  try {
    const r = await workflowApi.listSpecs()
    specs.value = r.data || []
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}
const loadRuns = async () => {
  try {
    const r = await workflowApi.listRuns()
    const all = r.data || {}
    runs.value = Object.entries(all).map(([id, v]) => ({ id, ...v })).slice(0, 20)
  } catch {}
}

const onRun = async (row) => {
  const r = await workflowApi.getSpec(row.id)
  if (r.data) {
    const r2 = await workflowApi.run(r.data)
    ElMessage.success(`已提交, runId=${r2.data}`)
    bus.emit('wf:event', { text: `工作流 ${row.name} 已运行`, action: { label: '查看', handler: () => loadRuns() } })
    loadRuns()
  }
}
const onDuplicate = async (row) => {
  await workflowApi.duplicate(row.id)
  ElMessage.success('已复制')
  bus.emit('sys:event', { text: `工作流 ${row.name} 已复制` })
  loadSpecs()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`删除工作流 ${row.name}?`, '确认', { type: 'warning' })
  await workflowApi.removeSpec(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `工作流 ${row.name} 已删除` })
  loadSpecs()
}

onMounted(() => {
  loadSpecs(); loadRuns()
  bus.emit('sys:event', { text: '进入工作流管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.hd-actions { display: flex; gap: 8px; }
.muted { color: #94a3b8; }
.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
.mt-12 { margin-top: 12px; }
.rh { font-size: 14px; margin: 0 0 10px; color: #1e293b; }
.hot { color: #ef4444; font-weight: 700; }
.cold { color: #94a3b8; }
</style>
