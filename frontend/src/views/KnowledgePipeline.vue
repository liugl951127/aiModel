<template>
  <div class="page-container pipeline-page">
    <!-- 左：节点面板 -->
    <el-card class="left-pane" shadow="hover">
      <template #header><b>节点面板</b></template>
      <div v-for="n in nodeList" :key="n.type" class="node-card"
        draggable="true" @dragstart="onDragStart($event, n)">
        <div class="node-title">{{ n.displayName }}</div>
        <div class="node-type">{{ n.type }}</div>
      </div>
      <el-divider />
      <el-button type="primary" plain @click="newPipeline">+ 新建空白流水线</el-button>
      <el-button @click="loadPipelines">刷新列表</el-button>
    </el-card>

    <!-- 中：画布 -->
    <el-card class="canvas-pane" shadow="hover">
      <template #header>
        <div class="card-header">
          <b>{{ current?.name || '未命名流水线' }}</b>
          <div>
            <el-button @click="savePipeline" :disabled="!current">保存</el-button>
            <el-button type="primary" @click="runPipeline" :disabled="!current">▶ 运行</el-button>
          </div>
        </div>
      </template>
      <div class="canvas"
        @dragover.prevent @drop="onDrop">
        <div v-for="(n, i) in current?.nodes || []" :key="n.id"
          :class="['canvas-node', selectedNodeId === n.id ? 'selected' : '']"
          :style="{ left: n._x + 'px', top: n._y + 'px' }"
          @click.stop="selectNode(n)">
          <div class="cn-title">{{ n.label || n.type }}</div>
          <div class="cn-type">{{ n.type }} · #{{ i + 1 }}</div>
          <span class="del" @click.stop="removeNode(n.id)">×</span>
        </div>
        <el-empty v-if="!current?.nodes?.length" description="拖入左侧节点开始编排" :image-size="80" />
      </div>
    </el-card>

    <!-- 右：节点配置 + 运行结果 -->
    <el-card class="right-pane" shadow="hover">
      <template #header>
        <div class="card-header">
          <b>节点配置 / 运行结果</b>
        </div>
      </template>

      <template v-if="selectedNode">
        <el-form size="small" label-width="100px">
          <el-form-item label="标签">
            <el-input v-model="selectedNode.label" />
          </el-form-item>
          <el-divider content-position="left">参数</el-divider>
          <el-form-item v-for="(v, k) in selectedNode.config || {}" :key="k" :label="k">
            <el-input v-if="typeof v === 'string'" v-model="selectedNode.config[k]" />
            <el-input-number v-else v-model="selectedNode.config[k]" :precision="Number.isInteger(v) ? 0 : 4"
              style="width:100%" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="addEmptyParam">+ 加参数</el-button>
          </el-form-item>
          <el-form-item label="连线">
            <el-select v-model="linkTarget" placeholder="连到..." clearable>
              <el-option v-for="t in linkCandidates" :key="t.id" :label="t.label" :value="t.id" />
            </el-select>
            <el-button :disabled="!linkTarget" @click="addEdge" style="margin-left:8px">连线</el-button>
          </el-form-item>
          <el-form-item label="连线列表">
            <el-tag v-for="e in edgesOf(selectedNode.id)" :key="e.to" closable
              @close="removeEdge(e)" style="margin:2px">{{ e.to }}</el-tag>
            <span v-if="!edgesOf(selectedNode.id).length" class="muted">无</span>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-empty description="点击画布上的节点进行配置" :image-size="60" />
      </template>

      <el-divider content-position="left">流水线列表</el-divider>
      <el-table :data="pipelines" size="small" @row-click="loadPipeline">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click.stop="delPipeline(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 运行结果抽屉 -->
    <el-drawer v-model="resultDrawer" title="运行结果" size="60%">
      <template v-if="lastResult">
        <el-alert v-if="lastResult.failed" type="error" :closable="false" show-icon title="流水线失败" />
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务">{{ lastResult.runId }}</el-descriptions-item>
          <el-descriptions-item label="查询">{{ lastResult.query }}</el-descriptions-item>
          <el-descriptions-item label="节点">{{ Object.keys(lastResult.nodeResults || {}).length }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">最终回答</el-divider>
        <pre class="answer-box">{{ lastResult.finalAnswer || '(无)' }}</pre>

        <el-divider content-position="left">节点执行</el-divider>
        <el-table :data="nodeRows" size="small" border>
          <el-table-column prop="id" label="节点" />
          <el-table-column prop="ok" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.ok ? 'success' : 'danger'">{{ row.ok ? 'OK' : 'FAIL' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
          <el-table-column prop="error" label="错误" />
        </el-table>

        <el-divider content-position="left">防幻觉审计</el-divider>
        <el-descriptions v-if="lastResult.audit" :column="2" border>
          <el-descriptions-item label="幻觉分数">{{ lastResult.audit.hallucinationScore }}</el-descriptions-item>
          <el-descriptions-item label="引用覆盖">{{ lastResult.audit.citationCoverage }}</el-descriptions-item>
          <el-descriptions-item label="事实支撑">{{ lastResult.audit.factualSupport }}</el-descriptions-item>
          <el-descriptions-item label="拒答">
            <el-tag :type="lastResult.audit.refused ? 'danger' : 'success'">
              {{ lastResult.audit.refused ? '已拒答' : '放行' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="lastResult.audit?.reason" type="warning" :closable="false" :title="lastResult.audit.reason" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pipelineApi } from '@/api'

const nodeList = ref([])
const pipelines = ref([])
const current = ref(null)
const selectedNodeId = ref(null)
const linkTarget = ref(null)
const resultDrawer = ref(false)
const lastResult = ref(null)
const runQuery = ref('什么是 Mini-GPT？')

const selectedNode = computed(() =>
  current.value?.nodes?.find(n => n.id === selectedNodeId.value) || null)
const linkCandidates = computed(() =>
  (current.value?.nodes || []).filter(n => n.id !== selectedNodeId.value))
const nodeRows = computed(() => {
  if (!lastResult.value?.nodeResults) return []
  return Object.entries(lastResult.value.nodeResults).map(([id, r]) => ({ id, ...r }))
})

function onDragStart (e, n) {
  e.dataTransfer.setData('application/json', JSON.stringify(n))
}
function onDrop (e) {
  const raw = e.dataTransfer.getData('application/json')
  if (!raw) return
  const meta = JSON.parse(raw)
  if (!current.value) newPipeline()
  const id = 'n-' + Math.random().toString(36).slice(2, 7)
  current.value.nodes.push({
    id, type: meta.type, label: meta.displayName,
    config: { ...(meta.defaultConfig || {}) },
    _x: e.offsetX - 60, _y: e.offsetY - 30
  })
  selectedNodeId.value = id
}
function selectNode (n) { selectedNodeId.value = n.id }
function removeNode (id) {
  current.value.nodes = current.value.nodes.filter(n => n.id !== id)
  current.value.edges = current.value.edges.filter(e => e.from !== id && e.to !== id)
  if (selectedNodeId.value === id) selectedNodeId.value = null
}
function addEdge () {
  if (!selectedNode.value || !linkTarget.value) return
  if (current.value.edges.some(e => e.from === selectedNode.value.id && e.to === linkTarget.value)) return
  current.value.edges.push({ from: selectedNode.value.id, to: linkTarget.value })
  linkTarget.value = null
}
function removeEdge (e) {
  current.value.edges = current.value.edges.filter(x => !(x.from === e.from && x.to === e.to))
}
function edgesOf (id) { return (current.value?.edges || []).filter(e => e.from === id) }
function addEmptyParam () {
  if (!selectedNode.value) return
  ElMessageBox.prompt('参数名', '新增参数', { inputPattern: /^\w+$/, inputErrorMessage: '字母数字下划线' })
    .then(({ value }) => { selectedNode.value.config[value] = '' })
    .catch(() => {})
}
function newPipeline () {
  current.value = {
    id: null, name: '未命名流水线', description: '',
    nodes: [], edges: []
  }
}
async function loadPipelines () {
  try {
    const r = await pipelineApi.list()
    pipelines.value = r.data || []
  } catch (e) { /* silent */ }
}
async function loadPipeline (row) {
  try {
    const r = await pipelineApi.get(row.id)
    current.value = r.data
    selectedNodeId.value = null
  } catch (e) { ElMessage.error('加载失败') }
}
async function savePipeline () {
  if (!current.value) return
  try {
    const r = await pipelineApi.save(current.value)
    current.value = r.data
    ElMessage.success('已保存')
    loadPipelines()
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}
async function delPipeline (row) {
  await ElMessageBox.confirm(`删除流水线 ${row.name}？`, '确认')
  await pipelineApi.remove(row.id)
  if (current.value?.id === row.id) current.value = null
  loadPipelines()
}
async function runPipeline () {
  if (!current.value?.id) {
    ElMessage.warning('请先保存流水线')
    return
  }
  try {
    const r = await pipelineApi.run(current.value.id, runQuery.value, {})
    lastResult.value = r.data
    resultDrawer.value = true
  } catch (e) { ElMessage.error('运行失败') }
}

onMounted(async () => {
  try {
    const r = await pipelineApi.nodes()
    nodeList.value = r.data || []
  } catch (e) { /* silent */ }
  loadPipelines()
})
</script>

<style scoped>
.pipeline-page {
  display: grid;
  grid-template-columns: 240px 1fr 340px;
  gap: 12px;
  align-items: start;
}
.node-card {
  border: 1px solid #ebeef5; border-radius: 4px; padding: 8px 10px; margin-bottom: 8px;
  cursor: grab; background: #fafbfc;
}
.node-card:hover { border-color: #409eff; background: #ecf5ff; }
.node-title { font-weight: 500; }
.node-type { font-size: 11px; color: #909399; font-family: ui-monospace, Menlo, monospace; }
.canvas {
  position: relative; min-height: 480px; background:
    repeating-linear-gradient(0deg, #fafafa 0 24px, transparent 24px 25px),
    repeating-linear-gradient(90deg, #fafafa 0 24px, transparent 24px 25px),
    #fff;
  border: 1px dashed #dcdfe6; border-radius: 4px;
}
.canvas-node {
  position: absolute; min-width: 140px; padding: 6px 10px;
  background: #fff; border: 1px solid #409eff; border-radius: 4px; cursor: pointer;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.canvas-node.selected { border-color: #f56c6c; box-shadow: 0 0 0 2px rgba(245,108,108,.2); }
.cn-title { font-weight: 500; }
.cn-type { font-size: 11px; color: #909399; }
.del { position: absolute; right: 4px; top: 0; color: #f56c6c; cursor: pointer; padding: 0 4px; }
.muted { color: #909399; font-size: 12px; }
.answer-box { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px; white-space: pre-wrap; }
</style>
