<template>
  <div class="mv-page">
    <header class="mv-header">
      <h1>📜 模型版本管理</h1>
      <p>对比不同版本的 loss / 参数量 / 评估指标, 一键激活 / 回滚</p>
    </header>

    <div class="mv-toolbar">
      <el-select v-model="modelCode" placeholder="选择模型" style="width: 280px" @change="load">
        <el-option v-for="m in modelList" :key="m.modelCode" :label="m.modelName" :value="m.modelCode" />
      </el-select>
      <el-button type="primary" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      <el-button :disabled="!compareA || !compareB" @click="doCompare">
        <el-icon><DataLine /></el-icon> 对比
      </el-button>
      <div class="spacer" />
      <el-button type="success" @click="openCreate">
        <el-icon><Plus /></el-icon> 创建新版本
      </el-button>
    </div>

    <div v-if="modelCode">
      <!-- 版本时间线 (紧凑) -->
      <el-card shadow="never" class="mv-timeline">
        <h3>📅 版本时间线</h3>
        <el-timeline>
          <el-timeline-item v-for="(v, i) in versions" :key="v.id" :timestamp="v.createdAt" placement="top" :type="i === 0 ? 'primary' : 'info'">
            <div class="vt-row">
              <el-tag size="small" :type="i === 0 ? 'success' : 'info'" effect="dark">v{{ v.version }}</el-tag>
              <span class="vt-name">{{ v.notes || '—' }}</span>
              <span class="vt-stats">loss <b>{{ v.finalLoss?.toFixed(4) || '—' }}</b> · iters <b>{{ v.iters }}</b> · bundle <b>{{ v.bundlePath || '—' }}</b></span>
              <div class="vt-actions">
                <el-button size="small" type="primary" plain @click="setA(v)">设为 A</el-button>
                <el-button size="small" plain @click="setB(v)">设为 B</el-button>
                <el-button v-if="i !== 0" size="small" type="warning" @click="activate(v)">
                  <el-icon><Top /></el-icon> 激活
                </el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 对比面板 -->
      <el-card v-if="compareA && compareB" shadow="never" class="mv-compare">
        <h3>⚖️ 版本对比 (A vs B)</h3>
        <el-table :data="compareRows" border>
          <el-table-column prop="key" label="指标" width="180" />
          <el-table-column label="A" align="center">
            <template #default="{ row }">
              <span :class="compareCls(row, 'a')">{{ row.a }}</span>
            </template>
          </el-table-column>
          <el-table-column label="B" align="center">
            <template #default="{ row }">
              <span :class="compareCls(row, 'b')">{{ row.b }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.diff > 0" type="success">↑ {{ row.diff }}</el-tag>
              <el-tag v-else-if="row.diff < 0" type="danger">↓ {{ Math.abs(row.diff) }}</el-tag>
              <el-tag v-else type="info">相同</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-empty v-else description="选择模型开始查看版本" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, DataLine, Plus, Top } from '@element-plus/icons-vue'
import { modelApi } from '@/api'

const modelList = ref([])
const modelCode = ref('')
const versions = ref([])
const compareA = ref(null)
const compareB = ref(null)

const compareRows = computed(() => {
  if (!compareA.value || !compareB.value) return []
  const a = compareA.value, b = compareB.value
  return [
    { key: '版本号', a: a.version, b: b.version, diff: 0, noCompare: true },
    { key: '最终 loss', a: a.finalLoss, b: b.finalLoss, diff: (b.finalLoss || 0) - (a.finalLoss || 0), lowerBetter: true },
    { key: '迭代数', a: a.iters, b: b.iters, diff: (b.iters || 0) - (a.iters || 0) },
    { key: '层数 nLayer', a: a.nLayer, b: b.nLayer, diff: (b.nLayer || 0) - (a.nLayer || 0) },
    { key: '头数 nHead', a: a.nHead, b: b.nHead, diff: (b.nHead || 0) - (a.nHead || 0) },
    { key: '嵌入 nEmbd', a: a.nEmbd, b: b.nEmbd, diff: (b.nEmbd || 0) - (a.nEmbd || 0) },
    { key: '块大小 blockSize', a: a.blockSize, b: b.blockSize, diff: (b.blockSize || 0) - (a.blockSize || 0) },
    { key: '词表 vocabSize', a: a.vocabSize, b: b.vocabSize, diff: (b.vocabSize || 0) - (a.vocabSize || 0) },
    { key: 'Bundle 路径', a: a.bundlePath || '—', b: b.bundlePath || '—', diff: 0, noCompare: true }
  ]
})

const compareCls = (row, side) => {
  if (row.noCompare) return ''
  const v = side === 'a' ? row.a : row.b
  return v ? '' : 'muted'
}

const setA = (v) => { compareA.value = v; ElMessage.success(`A = v${v.version}`) }
const setB = (v) => { compareB.value = v; ElMessage.success(`B = v${v.version}`) }
const doCompare = () => { /* 表格里已展示 */ }

const activate = async (v) => {
  try {
    await ElMessageBox.confirm(`激活 v${v.version} 为当前生产版本?`, '提示', { type: 'warning' })
    // TODO: 后端 activate 端点
    ElMessage.success('已激活')
  } catch (e) { /* cancel */ }
}

const openCreate = async () => {
  try {
    const { value } = await ElMessageBox.prompt('新版本号', '提示', { inputValue: `v${Date.now()}` })
    if (!modelCode.value) return ElMessage.warning('请先选择模型')
    await modelApi.newVersion(modelCode.value, { version: value, notes: '新版本' })
    ElMessage.success('已创建')
    load()
  } catch (e) { /* cancel */ }
}

const load = async () => {
  if (!modelCode.value) return
  try {
    const r = await modelApi.versions(modelCode.value)
    if (r.code === 200) versions.value = r.data || []
  } catch (e) { /* ignore */ }
}

const loadModels = async () => {
  try {
    const r = await modelApi.list()
    if (r.code === 200) modelList.value = r.data || []
  } catch (e) {}
}

onMounted(loadModels)
</script>

<style scoped>
.mv-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.mv-header { padding: 16px 24px; background: linear-gradient(135deg, #f093fb, #f5576c); border-radius: 12px; color: #fff; }
.mv-header h1 { margin: 0 0 4px; font-size: 22px; }
.mv-header p { margin: 0; opacity: 0.9; font-size: 13px; }
.mv-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; }
.mv-toolbar .spacer { flex: 1; }
.mv-timeline h3, .mv-compare h3 { margin: 0 0 8px; color: #6366f1; font-size: 14px; }
.vt-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.vt-name { font-size: 12px; color: #475569; }
.vt-stats { font-size: 11px; color: #94a3b8; }
.vt-stats b { color: #1e293b; }
.vt-actions { display: flex; gap: 4px; margin-left: auto; }
.mv-compare { margin-top: 12px; }
.muted { color: #94a3b8; }
</style>
