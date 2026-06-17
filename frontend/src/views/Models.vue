<template>
  <div class="models-page">
    <!-- 顶部统计 + 搜索 -->
    <div class="models-hero">
      <div class="hero-l">
        <h1>🧠 大模型管理</h1>
        <p>管理 LLM 模型注册、版本、导出 — 全部通过 ONNX 推理服务运行</p>
      </div>
      <div class="hero-r">
        <el-statistic title="模型总数" :value="stats.total || 0" :value-style="{ color: '#6366f1', fontWeight: 700 }" />
        <el-statistic title="在线推理" :value="stats.ready || 0" :value-style="{ color: '#10b981', fontWeight: 700 }" />
        <el-statistic title="训练中" :value="stats.training || 0" :value-style="{ color: '#f59e0b', fontWeight: 700 }" />
        <el-statistic title="参数量" :value="stats.params || '—'" :value-style="{ color: '#ec4899', fontWeight: 700 }" />
      </div>
    </div>

    <!-- 工具条 -->
    <div class="models-toolbar">
      <el-input v-model="kw" placeholder="搜索模型名 / 编码 / 标签" clearable style="width: 280px" @keyup.enter="load" />
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px">
        <el-option label="草稿" value="draft" />
        <el-option label="训练中" value="training" />
        <el-option label="就绪" value="ready" />
        <el-option label="失败" value="failed" />
      </el-select>
      <el-button type="primary" @click="load">搜索</el-button>
      <el-button @click="load"><el-icon><Refresh /></el-icon></el-button>
      <div class="spacer" />
      <el-button type="success" @click="openRegister">
        <el-icon><Plus /></el-icon> 注册新模型
      </el-button>
      <el-button @click="loadVersions">
        <el-icon><Files /></el-icon> 版本管理
      </el-button>
    </div>

    <!-- 卡片网格 (紧凑 + 美观) -->
    <div class="models-grid">
      <div v-for="m in rows" :key="m.id" class="model-card" @click="openDetail(m)">
        <div class="mc-head">
          <div class="mc-ico" :style="{ background: gradientFor(m.modelType) }">
            <span>{{ emojiFor(m.modelType) }}</span>
          </div>
          <div class="mc-title">
            <div class="mc-name">{{ m.modelName }}</div>
            <div class="mc-code">{{ m.modelCode }} <span class="version-tag">v{{ m.version }}</span></div>
          </div>
          <el-tag :type="statusType(m.status)" size="small" effect="dark" round>{{ statusLabel(m.status) }}</el-tag>
        </div>
        <div class="mc-body">
          <div class="mc-row">
            <span class="lbl">类型</span>
            <span class="val">{{ m.modelType }} · {{ m.framework }}</span>
          </div>
          <div class="mc-row">
            <span class="lbl">参数量</span>
            <span class="val">{{ m.parameterCount || '—' }} M</span>
          </div>
          <div class="mc-row">
            <span class="lbl">上下文</span>
            <span class="val">{{ m.contextLength || '—' }}</span>
          </div>
          <div class="mc-row">
            <span class="lbl">语言</span>
            <span class="val">{{ m.language || '—' }}</span>
          </div>
          <div v-if="m.tags" class="mc-tags">
            <el-tag v-for="t in m.tags.split(',')" :key="t" size="small" effect="plain" type="info">{{ t.trim() }}</el-tag>
          </div>
        </div>
        <div class="mc-foot">
          <el-button size="small" type="primary" plain @click.stop="exportOnnx(m)">
            <el-icon><Download /></el-icon> ONNX
          </el-button>
          <el-button size="small" @click.stop="openDetail(m)">详情</el-button>
          <el-button size="small" @click.stop="newVersion(m)">
            <el-icon><Promotion /></el-icon> 新版本
          </el-button>
          <el-button size="small" type="danger" @click.stop="remove(m)">删</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && !rows.length" description="还没有模型, 点上方 [注册新模型] 开始" />
    </div>

    <!-- 注册 / 编辑 dialog -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑模型' : '注册新模型'" width="640px" align-center>
      <el-form :model="form" label-width="120px" size="default">
        <el-form-item label="模型编码" required>
          <el-input v-model="form.modelCode" placeholder="如 minigpt-zh-v1" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="MiniGPT 中文版" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.modelType" style="width: 100%">
            <el-option label="LLM 大语言模型" value="llm" />
            <el-option label="Embedding 向量" value="embed" />
            <el-option label="Classifier 分类" value="classifier" />
          </el-select>
        </el-form-item>
        <el-form-item label="基础模型">
          <el-input v-model="form.baseModel" placeholder="minigpt / llama / bge" />
        </el-form-item>
        <el-form-item label="训练框架">
          <el-select v-model="form.framework" style="width: 100%">
            <el-option label="DJL + PyTorch" value="djl-pytorch" />
            <el-option label="ONNX Runtime" value="onnx" />
            <el-option label="TensorFlow" value="tf" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数量 (M)">
          <el-input-number v-model="form.parameterCount" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="上下文长度">
          <el-input v-model="form.contextLength" placeholder="2048" />
        </el-form-item>
        <el-form-item label="语言">
          <el-input v-model="form.language" placeholder="zh / en" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔, 如: 文本生成,中文,小模型" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情侧栏 -->
    <el-drawer v-model="detailVisible" :title="detail?.modelName || '模型详情'" size="560px" direction="rtl">
      <div v-if="detail" class="detail-pane">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="编码">{{ detail.modelCode }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.modelName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.modelType }}</el-descriptions-item>
          <el-descriptions-item label="基础模型">{{ detail.baseModel }}</el-descriptions-item>
          <el-descriptions-item label="框架">{{ detail.framework }}</el-descriptions-item>
          <el-descriptions-item label="参数量">{{ detail.parameterCount }} M</el-descriptions-item>
          <el-descriptions-item label="上下文">{{ detail.contextLength }}</el-descriptions-item>
          <el-descriptions-item label="语言">{{ detail.language }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="ONNX 路径"><code>{{ detail.onnxPath || '—' }}</code></el-descriptions-item>
          <el-descriptions-item label="存储路径"><code>{{ detail.storagePath || '—' }}</code></el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px;">📜 历史版本</h4>
        <el-timeline v-if="versions.length">
          <el-timeline-item v-for="v in versions" :key="v.id" :timestamp="v.createdAt" placement="top">
            <el-tag size="small">v{{ v.version }}</el-tag>
            {{ v.notes || '—' }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无版本" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Files, Download, Promotion } from '@element-plus/icons-vue'
import { modelApi } from '@/api'

const rows = ref([])
const stats = ref({})
const kw = ref('')
const statusFilter = ref('')
const loading = ref(false)
const dialog = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const versions = ref([])
const form = reactive({
  id: null, modelCode: '', modelName: '', modelType: 'llm', baseModel: 'minigpt',
  framework: 'djl-pytorch', parameterCount: 0, contextLength: '2048', language: 'zh',
  tags: '', description: '', version: '1.0.0', status: 'draft'
})

const statusType = (s) => ({ draft: 'info', training: 'warning', ready: 'success', failed: 'danger' }[s] || 'info')
const statusLabel = (s) => ({ draft: '草稿', training: '训练中', ready: '就绪', failed: '失败' }[s] || s)

const emojiFor = (type) => ({ llm: '🧠', embed: '🧬', classifier: '🏷️' }[type] || '🤖')
const gradientFor = (type) => {
  const m = { llm: 'linear-gradient(135deg, #667eea, #764ba2)', embed: 'linear-gradient(135deg, #f093fb, #f5576c)', classifier: 'linear-gradient(135deg, #4facfe, #00f2fe)' }
  return m[type] || 'linear-gradient(135deg, #6366f1, #8b5cf6)'
}

const load = async () => {
  loading.value = true
  try {
    const r = await modelApi.list()
    if (r.code === 200) {
      let arr = r.data || []
      if (kw.value) arr = arr.filter(x => (x.modelName + x.modelCode + (x.tags || '')).toLowerCase().includes(kw.value.toLowerCase()))
      if (statusFilter.value) arr = arr.filter(x => x.status === statusFilter.value)
      rows.value = arr
    }
  } finally { loading.value = false }
}

const loadStats = async () => {
  try { const r = await modelApi.stats(); if (r.code === 200) stats.value = r.data || {} } catch (e) {}
}

const openRegister = () => {
  Object.keys(form).forEach(k => form[k] = { id: null, modelCode: '', modelName: '', modelType: 'llm', baseModel: 'minigpt', framework: 'djl-pytorch', parameterCount: 0, contextLength: '2048', language: 'zh', tags: '', description: '', version: '1.0.0', status: 'draft' }[k])
  dialog.value = true
}

const openDetail = async (m) => {
  detail.value = m
  detailVisible.value = true
  try {
    const r = await modelApi.versions(m.modelCode)
    if (r.code === 200) versions.value = r.data || []
  } catch (e) { versions.value = [] }
}

const save = async () => {
  try {
    const r = form.id ? await modelApi.update(form) : await modelApi.create(form)
    if (r.code === 200) {
      ElMessage.success('已保存')
      dialog.value = false
      load(); loadStats()
    } else { ElMessage.error(r.message) }
  } catch (e) { ElMessage.error(e.message) }
}

const remove = async (m) => {
  try {
    await ElMessageBox.confirm(`确认删除 "${m.modelName}"?`, '提示', { type: 'warning' })
    const r = await modelApi.remove(m.id)
    if (r.code === 200) { ElMessage.success('已删除'); load(); loadStats() }
  } catch (e) { /* cancel */ }
}

const exportOnnx = async (m) => {
  try {
    const r = await modelApi.export(m.id)
    if (r.code === 200) ElMessage.success('已导出 ONNX: ' + (r.data || ''))
    else ElMessage.error(r.message)
  } catch (e) { ElMessage.error(e.message) }
}

const newVersion = async (m) => {
  try {
    const { value } = await ElMessageBox.prompt('新版本号 (如 v1.1)', '提示', { inputValue: `v${Date.now()}`, inputPattern: /^v\d/, inputErrorMessage: '格式: v 开头' })
    const r = await modelApi.newVersion(m.modelCode, { version: value, notes: '新版本' })
    if (r.code === 200) { ElMessage.success('已创建新版本'); openDetail(m) }
  } catch (e) { /* cancel */ }
}

const loadVersions = () => { ElMessage.info('请点卡片查看历史版本') }

onMounted(() => { load(); loadStats() })
</script>

<style scoped>
.models-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.models-hero { display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; background: linear-gradient(135deg, #667eea, #764ba2); border-radius: 12px; color: #fff; }
.models-hero h1 { margin: 0 0 4px; font-size: 22px; }
.models-hero p { margin: 0; opacity: 0.9; font-size: 13px; }
.hero-r { display: flex; gap: 28px; }
.hero-r :deep(.el-statistic__content) { color: #fff; }
.hero-r :deep(.el-statistic__title) { color: rgba(255,255,255,0.85); }

.models-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; }
.models-toolbar .spacer { flex: 1; }

.models-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.model-card { padding: 14px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 10px; cursor: pointer; transition: all 0.2s; }
.model-card:hover { border-color: #6366f1; box-shadow: 0 4px 12px -2px rgba(99,102,241,0.15); transform: translateY(-2px); }
.mc-head { display: flex; align-items: center; gap: 10px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.mc-ico { width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.mc-title { flex: 1; min-width: 0; }
.mc-name { font-weight: 600; font-size: 14px; }
.mc-code { font-size: 11px; color: #94a3b8; }
.mc-code .version-tag { background: #eef2ff; color: #6366f1; padding: 1px 6px; border-radius: 3px; margin-left: 4px; }
.mc-body { padding: 8px 0; }
.mc-row { display: flex; align-items: center; gap: 6px; font-size: 12px; padding: 2px 0; }
.mc-row .lbl { color: #94a3b8; min-width: 56px; }
.mc-row .val { color: #1e293b; }
.mc-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.mc-foot { display: flex; gap: 4px; padding-top: 8px; border-top: 1px solid #f0f0f0; }
.mc-foot .el-button { flex: 1; padding: 4px 8px; font-size: 11px; }
.detail-pane h4 { margin: 0 0 8px; color: #6366f1; }
</style>
