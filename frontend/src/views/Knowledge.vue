<template>
  <div class="kb-page">
    <!-- 顶部统计 -->
    <header class="kb-hero">
      <div class="hero-l">
        <h1>📚 知识库</h1>
        <p>管理 ES 知识库 + 文档上传 + RAG 检索测试 (Tika 解析 + 向量索引)</p>
      </div>
      <div class="hero-r">
        <el-statistic title="知识库数" :value="stats.bases || 0" :value-style="{ color: '#6366f1', fontWeight: 700 }" />
        <el-statistic title="文档数" :value="stats.docs || 0" :value-style="{ color: '#10b981', fontWeight: 700 }" />
        <el-statistic title="已检索" :value="stats.searches || 0" :value-style="{ color: '#f59e0b', fontWeight: 700 }" />
      </div>
    </header>

    <!-- Tab 切换: 知识库 / 文档 / 检索 -->
    <el-tabs v-model="tab" type="card" class="kb-tabs">
      <!-- ── Tab 1: 知识库列表 ── -->
      <el-tab-pane label="📂 知识库" name="bases">
        <div class="toolbar">
          <el-input v-model="kw" placeholder="搜索知识库名 / 编码" clearable style="width: 280px" @keyup.enter="loadBases" @clear="loadBases" />
          <el-button type="primary" @click="loadBases"><el-icon><Search /></el-icon> 搜索</el-button>
          <el-button @click="loadBases"><el-icon><Refresh /></el-icon></el-button>
          <div class="spacer" />
          <el-button type="success" @click="createDialog = true">
            <el-icon><Plus /></el-icon> 新建知识库
          </el-button>
        </div>

        <el-table :data="filteredBases" v-loading="loadingBases" stripe>
          <el-table-column label="知识库" min-width="200">
            <template #default="{ row }">
              <div class="kb-cell">
                <el-icon class="kb-icon" color="#6366f1"><Reading /></el-icon>
                <div>
                  <div class="kb-name">{{ row.kbName }}</div>
                  <div class="kb-code muted">{{ row.kbCode }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="indexName" label="ES 索引" width="160" />
          <el-table-column prop="embeddingModel" label="Embedding" width="160" />
          <el-table-column prop="documentCount" label="文档" width="80" align="center" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ready' ? 'success' : 'warning'" effect="plain">
                {{ row.status || 'unknown' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="selectBase(row)">
                <el-icon><Document /></el-icon> 文档
              </el-button>
              <el-button size="small" @click="openSearch(row)">
                <el-icon><Search /></el-icon> 检索
              </el-button>
              <el-button size="small" type="danger" plain @click="removeBase(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ── Tab 2: 文档管理 ── -->
      <el-tab-pane :label="`📄 文档 (${currentBase?.kbName || '未选'})`" name="docs">
        <div v-if="!currentBase" class="empty-tip">
          请先在 [📂 知识库] Tab 选一个, 再来这里管理文档.
        </div>
        <template v-else>
          <div class="toolbar">
            <el-input v-model="docKw" placeholder="搜索文档名" clearable style="width: 240px" @keyup.enter="loadDocs" @clear="loadDocs" />
            <el-button type="primary" @click="loadDocs"><el-icon><Search /></el-icon> 搜索</el-button>
            <el-button @click="loadDocs"><el-icon><Refresh /></el-icon></el-button>
            <div class="spacer" />
            <el-button type="success" @click="uploadDialog = true">
              <el-icon><Upload /></el-icon> 上传文档 (分片)
            </el-button>
          </div>

          <el-table :data="docs" v-loading="loadingDocs" stripe>
            <el-table-column label="文档" min-width="280">
              <template #default="{ row }">
                <div class="doc-cell">
                  <el-icon class="doc-icon" :color="iconColor(row.contentType)"><Document /></el-icon>
                  <div>
                    <div class="doc-name">{{ row.docName || row.originalName }}</div>
                    <div class="muted">{{ row.contentType || '—' }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="right">
              <template #default="{ row }">{{ formatSize(row.sizeBytes || row.size) }}</template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="切片" width="80" align="center" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'indexed' ? 'success' : 'warning'" effect="plain">
                  {{ row.status || 'pending' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" plain @click="viewDoc(row)">查看</el-button>
                <el-button size="small" type="danger" plain @click="removeDoc(row)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- ── Tab 3: 检索测试 ── -->
      <el-tab-pane :label="`🔍 检索 (${currentBase?.kbName || '未选'})`" name="search">
        <div v-if="!currentBase" class="empty-tip">
          请先在 [📂 知识库] Tab 选一个, 再来这里测检索.
        </div>
        <template v-else>
          <div class="toolbar">
            <el-input v-model="searchQuery" placeholder="输入查询 (e.g. 如何训练 LoRA 模型)" clearable
                      style="width: 480px" @keyup.enter="doSearch" @clear="clearSearch" />
            <el-button type="primary" @click="doSearch" :loading="searching"><el-icon><Search /></el-icon> 检索</el-button>
            <el-button @click="clearSearch">清空</el-button>
            <div class="spacer" />
            <el-tag size="small">Top-K: {{ topK }}</el-tag>
          </div>

          <div v-if="searchResults.length" class="search-meta">
            命中 <b>{{ searchResults.length }}</b> 条, 耗时 <b>{{ searchDuration }}ms</b>
          </div>

          <div v-for="(r, i) in searchResults" :key="i" class="search-hit">
            <div class="hit-head">
              <el-tag size="small" type="success" effect="dark">#{{ i + 1 }} · {{ (r.score * 100).toFixed(1) }}%</el-tag>
              <span class="hit-source">{{ r.docName || r.documentName || r.source || 'doc' }}</span>
            </div>
            <div class="hit-content" v-html="highlight(r.content || r.text, searchQuery)" />
          </div>

          <el-empty v-if="searchResults.length === 0 && searched" description="没命中, 试试别的关键词" />
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建知识库 -->
    <el-dialog v-model="createDialog" title="新建知识库" width="500px" align-center>
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.kbCode" placeholder="kb-zh-tech-v1" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.kbName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="Embedding">
          <el-select v-model="form.embeddingModel" style="width: 100%">
            <el-option label="byte-hash (默认, 无外部依赖)" value="byte-hash" />
            <el-option label="bge-small-zh (本地加载, 待装)" value="bge-small-zh" />
            <el-option label="text-embedding-3-small (OpenAI)" value="text-embedding-3-small" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="createBase">提交</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档 -->
    <el-dialog v-model="uploadDialog" :title="`上传到 [${currentBase?.kbName || ''}]`" width="640px" align-center>
      <ChunkUploader
        :bucket="'kb'"
        :multiple="true"
        :tip="'上传文档到当前知识库, 支持 PDF/DOCX/TXT/MD, 自动 Tika 解析 + 切片 + 索引'"
        @uploaded="onUploaded"
        @error="onUploadError"
      />
      <template #footer>
        <el-button @click="uploadDialog = false">关闭</el-button>
        <el-button type="primary" @click="afterUpload">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Reading, Search, Refresh, Plus, Document, Upload } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'
import ChunkUploader from '@/components/ChunkUploader.vue'

defineOptions({ name: 'Knowledge' })

const bus = useGlobalBus()
const tab = ref('bases')

const bases = ref([])
const docs = ref([])
const kw = ref('')
const docKw = ref('')
const loadingBases = ref(false)
const loadingDocs = ref(false)
const currentBase = ref(null)
const createDialog = ref(false)
const uploadDialog = ref(false)
const searchQuery = ref('')
const searchResults = ref([])
const searching = ref(false)
const searched = ref(false)
const searchDuration = ref(0)
const topK = 5
const stats = ref({})

const form = reactive({
  kbCode: '', kbName: '', description: '', embeddingModel: 'byte-hash'
})

const filteredBases = computed(() => {
  if (!kw.value) return bases.value
  const k = kw.value.toLowerCase()
  return bases.value.filter(b =>
    (b.kbName || '').toLowerCase().includes(k) ||
    (b.kbCode || '').toLowerCase().includes(k)
  )
})

function formatSize(b) {
  if (!b) return '—'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(1) + ' MB'
  return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function iconColor(ct) {
  if (!ct) return '#94a3b8'
  if (ct.startsWith('image/')) return '#ec4899'
  if (ct.includes('pdf')) return '#ef4444'
  if (ct.includes('json') || ct.includes('xml')) return '#6366f1'
  return '#10b981'
}

function highlight(text, query) {
  if (!text) return ''
  if (!query || !query.trim()) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const safe = escapeHtml(query).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return escaped.replace(new RegExp(safe, 'gi'),
    m => `<mark class="hl">${m}</mark>`)
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c])
}

async function loadBases() {
  loadingBases.value = true
  try {
    const r = await knowledgeApi.bases()
    if (r.code === 200) bases.value = r.data || []
    stats.value.bases = bases.value.length
  } catch (e) {
    ElMessage.error('加载知识库失败: ' + e.message)
  } finally {
    loadingBases.value = false
  }
}

async function createBase() {
  if (!form.kbCode || !form.kbName) {
    ElMessage.warning('编码和名称必填')
    return
  }
  try {
    const r = await knowledgeApi.createBase(form)
    if (r.code === 200) {
      ElMessage.success('已创建')
      createDialog.value = false
      Object.assign(form, { kbCode: '', kbName: '', description: '', embeddingModel: 'byte-hash' })
      loadBases()
    } else {
      ElMessage.error('创建失败: ' + r.message)
    }
  } catch (e) {
    ElMessage.error('创建异常: ' + e.message)
  }
}

async function removeBase(row) {
  try {
    await ElMessageBox.confirm(`确认删除知识库 [${row.kbName}]?`, '提示', { type: 'warning' })
    // 后端可能没 delete 接口, 这里先 toast
    ElMessage.warning('后端待加删除接口 (临时)')
    loadBases()
  } catch (e) { /* cancel */ }
}

function selectBase(row) {
  currentBase.value = row
  tab.value = 'docs'
  loadDocs()
}

async function loadDocs() {
  if (!currentBase.value) return
  loadingDocs.value = true
  try {
    const r = await knowledgeApi.documents(currentBase.value.id, { name: docKw.value })
    if (r.code === 200) {
      docs.value = r.data?.rows || r.data || []
      stats.value.docs = docs.value.length
    }
  } catch (e) {
    ElMessage.error('加载文档失败: ' + e.message)
  } finally {
    loadingDocs.value = false
  }
}

function viewDoc(row) {
  ElMessage.info(`查看文档: ${row.docName || row.originalName} (后端待加详情页)`)
}

async function removeDoc(row) {
  try {
    await ElMessageBox.confirm(`确认删除 [${row.docName || row.originalName}]?`, '提示', { type: 'warning' })
    ElMessage.warning('后端待加删除接口')
    loadDocs()
  } catch (e) { /* cancel */ }
}

function onUploaded(payload) {
  // ★ 用真实 knowledgeApi.upload (kbId 路径) 调后端
  // ChunkUploader 先把文件落 file_object, 再调 kb API 入索引
  ElMessage.success(`${payload.file.name} 上传完成, 索引中...`)
  bus.emit('live:event', { type: 'kb', text: `📄 上传 ${payload.file.name} 到 [${currentBase.value?.kbName}]`, actor: 'kb' })
  setTimeout(() => loadDocs(), 1000)
}

function onUploadError(payload) {
  ElMessage.error(`上传失败: ${payload.file.name} - ${payload.error}`)
}

function afterUpload() {
  uploadDialog.value = false
  loadDocs()
}

function openSearch(row) {
  currentBase.value = row
  tab.value = 'search'
  searchResults.value = []
  searchQuery.value = ''
  searched.value = false
}

async function doSearch() {
  if (!currentBase.value || !searchQuery.value.trim()) return
  searching.value = true
  searched.value = true
  const t0 = Date.now()
  try {
    const r = await knowledgeApi.search(currentBase.value.id, searchQuery.value, topK)
    if (r.code === 200) {
      searchResults.value = r.data || []
      searchDuration.value = Date.now() - t0
      stats.value.searches = (stats.value.searches || 0) + 1
      ElMessage.success(`命中 ${searchResults.value.length} 条 (${searchDuration.value}ms)`)
    } else {
      ElMessage.error('检索失败: ' + r.message)
    }
  } catch (e) {
    ElMessage.error('检索异常: ' + e.message)
  } finally {
    searching.value = false
  }
}

function clearSearch() {
  searchResults.value = []
  searchQuery.value = ''
  searched.value = false
}

onMounted(() => {
  loadBases()
  bus.emit('sys:event', { text: '进入知识库' })
})
</script>

<style scoped>
.kb-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.kb-hero { padding: 16px 24px; background: linear-gradient(135deg, #10b981, #059669); border-radius: 12px; color: #fff; display: flex; justify-content: space-between; align-items: center; }
.kb-hero h1 { margin: 0 0 4px; font-size: 22px; }
.kb-hero p { margin: 0; opacity: 0.9; font-size: 13px; }
.hero-r { display: flex; gap: 24px; }
.hero-r :deep(.el-statistic__head) { color: rgba(255,255,255,0.9); }
.hero-r :deep(.el-statistic__number) { color: #fff; }
.toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 0; }
.toolbar .spacer { flex: 1; }
.kb-cell, .doc-cell { display: flex; align-items: center; gap: 10px; }
.kb-icon, .doc-icon { font-size: 24px; }
.kb-name, .doc-name { font-weight: 600; color: #1e293b; }
.kb-code, .doc-meta { font-size: 11px; }
.muted { color: #94a3b8; }
.empty-tip { padding: 60px 16px; text-align: center; color: #64748b; background: #f8fafc; border-radius: 8px; }
.search-meta { padding: 8px 0; color: #475569; font-size: 13px; }
.search-hit {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
  padding: 12px 14px; margin-bottom: 8px;
}
.hit-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.hit-source { font-size: 12px; color: #64748b; }
.hit-content { font-size: 13px; line-height: 1.6; color: #1e293b; }
:deep(.hl) { background: #fef08a; padding: 0 2px; border-radius: 2px; font-weight: 600; }
</style>