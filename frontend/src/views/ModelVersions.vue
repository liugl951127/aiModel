<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>📦 模型版本管理</h2>
        <p class="muted">同一 modelCode 下的所有版本 · 激活 / 对比 / 历史</p>
      </div>
      <div class="hd-actions">
        <el-input v-model="filterModel" placeholder="按 modelCode 过滤" clearable style="width:200px" @change="load" />
        <el-button :underline="false" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </header>

    <!-- 统计 -->
    <section class="stats">
      <div class="stat s-total"><span class="s-ico">📊</span><div><div class="sv">{{ stats.total || 0 }}</div><div class="sl">总数</div></div></div>
      <div class="stat s-active"><span class="s-ico">🟢</span><div><div class="sv">{{ stats.active || 0 }}</div><div class="sl">活跃</div></div></div>
      <div class="stat s-draft"><span class="s-ico">🟡</span><div><div class="sv">{{ stats.draft || 0 }}</div><div class="sl">草稿</div></div></div>
      <div class="stat s-archived"><span class="s-ico">⚫</span><div><div class="sv">{{ stats.archived || 0 }}</div><div class="sl">归档</div></div></div>
    </section>

    <el-card shadow="never" class="box">
      <el-table :data="filtered" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="模型" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.modelName }}</strong>
            <small class="muted"> / {{ row.modelCode }}</small>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : row.status === 'archived' ? 'info' : 'warning'">
              {{ row.version }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : row.status === 'archived' ? 'info' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parameterCount" label="参数" width="100">
          <template #default="{ row }">{{ row.parameterCount ? (row.parameterCount / 1_000_000).toFixed(1) + 'M' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="contextLength" label="上下文" width="100" />
        <el-table-column prop="framework" label="框架" width="100" />
        <el-table-column label="ONNX 路径" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <small class="path">{{ row.onnxPath || '—' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }"><small class="muted">{{ row.createTime }}</small></template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button :underline="false" size="small" type="success" :disabled="row.status === 'active'" @click="onActivate(row)">激活</el-button>
            <el-button :underline="false" size="small" @click="onCompare(row)">对比</el-button>
            <el-button :underline="false" size="small" type="primary" @click="onNewVersion(row)">新版本</el-button>
            <el-button :underline="false" size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 对比弹窗 -->
    <el-dialog v-model="dlgCompare" title="版本对比" width="780px">
      <div v-if="compareResult" class="cmp">
        <div class="cmp-h">差异字段 ({{ compareResult.diff?.length || 0 }})</div>
        <el-tag v-for="d in compareResult.diff" :key="d" type="info" size="small" class="cmp-tag">{{ d }}</el-tag>
        <div v-if="!compareResult.diff?.length" class="muted">两个版本完全一致</div>

        <div class="cmp-tables">
          <table>
            <thead>
              <tr><th>字段</th><th>A: {{ compareResult.a?.version }}</th><th>B: {{ compareResult.b?.version }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="f in fields" :key="f">
                <td>{{ f }}</td>
                <td>{{ compareResult.a?.[f] || '—' }}</td>
                <td>{{ compareResult.b?.[f] || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { modelApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const all = ref([])
const stats = ref({})
const filterModel = ref('')
const filtered = computed(() => filterModel.value
  ? all.value.filter(m => m.modelCode?.includes(filterModel.value))
  : all.value)
const dlgCompare = ref(false)
const compareResult = ref(null)
const fields = ['version', 'status', 'parameterCount', 'contextLength', 'framework', 'onnxPath', 'exportFormat', 'metrics', 'description']

const load = async () => {
  loading.value = true
  try {
    const r = await modelApi.list()
    all.value = r.data || []
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}
const loadStats = async () => {
  try { stats.value = (await modelApi.stats()).data || {} } catch {}
}

const onActivate = async (row) => {
  await ElMessageBox.confirm(`激活版本 ${row.version}? 其它同模型版本会归档`, '确认', { type: 'warning' })
  await modelApi.activate(row.id)
  ElMessage.success('已激活')
  bus.emit('sys:event', { text: `模型 ${row.modelName} ${row.version} 已激活` })
  load()
}
const onCompare = async (row) => {
  // 找同 modelCode 其它版本
  const sameCode = all.value.filter(m => m.modelCode === row.modelCode)
  if (sameCode.length < 2) return ElMessage.warning('需要至少 2 个版本才能对比')
  const other = sameCode.find(m => m.id !== row.id) || sameCode[0]
  const r = await modelApi.compare(row.id, other.id)
  compareResult.value = r.data
  dlgCompare.value = true
}
const onNewVersion = async (row) => {
  const { value } = await ElMessageBox.prompt('输入新版本号 (如 v0.2.0)', '新建版本', { inputValue: 'v0.1.0' })
  await modelApi.newVersion(row.modelCode, { ...row, id: null, version: value, status: 'draft' })
  ElMessage.success('已创建')
  bus.emit('sys:event', { text: `模型 ${row.modelName} 新版本 ${value} 已创建` })
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`删除 ${row.modelName} ${row.version}?`, '确认', { type: 'warning' })
  await modelApi.remove(row.id)
  ElMessage.success('已删除')
  bus.emit('sys:event', { text: `模型 ${row.modelName} ${row.version} 已删除` })
  load()
}

onMounted(() => {
  load(); loadStats()
  bus.emit('sys:event', { text: '进入模型版本管理' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.hd-actions { display: flex; gap: 8px; }
.muted { color: #94a3b8; }
.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
.path { font-family: monospace; font-size: 11px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 10px; color: #fff; }
.stat .s-ico { font-size: 24px; }
.stat .sv { font-size: 22px; font-weight: 700; line-height: 1; }
.stat .sl { font-size: 11px; opacity: 0.9; margin-top: 2px; }
.s-total { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.s-active { background: linear-gradient(135deg, #10b981, #047857); }
.s-draft { background: linear-gradient(135deg, #f59e0b, #d97706); }
.s-archived { background: linear-gradient(135deg, #64748b, #334155); }
.cmp { font-size: 13px; }
.cmp-h { font-weight: 700; margin-bottom: 6px; }
.cmp-tag { margin-right: 4px; margin-bottom: 4px; }
.cmp-tables { margin-top: 12px; overflow-x: auto; }
.cmp-tables table { width: 100%; border-collapse: collapse; font-size: 12px; }
.cmp-tables th, .cmp-tables td { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
.cmp-tables th { background: #f3f4f6; font-weight: 700; }
.cmp-tables tbody tr:hover { background: #f8fafc; }
</style>
