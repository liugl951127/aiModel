<template>
  <div class="files-page">
    <header class="files-hero">
      <div class="hero-l">
        <h1>📁 文件管理</h1>
        <p>上传 / 下载 / 删除 — 自动分片 (5MB/片) + Redis 持久化 (断点续传) + S3-pluggable</p>
      </div>
      <div class="hero-r">
        <el-statistic title="总文件" :value="stats.total || 0" :value-style="{ color: '#6366f1', fontWeight: 700 }" />
        <el-statistic title="总大小" :value="stats.totalSize ? formatSize(stats.totalSize) : '0 B'" :value-style="{ color: '#10b981', fontWeight: 700 }" />
        <el-statistic title="桶数" :value="stats.buckets || 0" :value-style="{ color: '#f59e0b', fontWeight: 700 }" />
      </div>
    </header>

    <div class="files-toolbar">
      <el-select v-model="bucket" placeholder="桶" clearable style="width: 160px" @change="load">
        <el-option v-for="b in buckets" :key="b" :label="b" :value="b" />
        <template #prefix><el-icon><Box /></el-icon></template>
      </el-select>
      <el-input v-model="kw" placeholder="搜索文件名" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 搜索</el-button>
      <el-button @click="load"><el-icon><Refresh /></el-icon></el-button>
      <div class="spacer" />
      <el-button type="success" @click="showUploader = true">
        <el-icon><Upload /></el-icon> 上传文件
      </el-button>
    </div>

    <!-- 列表 -->
    <el-card shadow="never" class="files-table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="文件" min-width="220">
          <template #default="{ row }">
            <div class="file-cell">
              <el-icon class="file-icon" :color="iconColor(row.contentType)"><Document /></el-icon>
              <div class="file-name">
                <div>{{ row.originalName || row.objectKey }}</div>
                <div class="file-meta muted">{{ row.contentType || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="bucket" label="桶" width="100" />
        <el-table-column label="大小" width="120" align="right">
          <template #default="{ row }">{{ formatSize(row.sizeBytes || row.size || 0) }}</template>
        </el-table-column>
        <el-table-column label="存储" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.storageProvider === 's3' ? 'warning' : 'info'" effect="plain">
              {{ row.storageProvider || 'local' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.createdAt || row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="download(row)">
              <el-icon><Download /></el-icon> 下载
            </el-button>
            <el-button size="small" @click="copyKey(row)">
              <el-icon><CopyDocument /></el-icon> 复制 key
            </el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
        style="margin-top: 12px; justify-content: flex-end;"
      />
    </el-card>

    <!-- 上传弹窗 -->
    <el-dialog v-model="showUploader" title="上传文件 (分片上传, 断点续传)" width="640px" align-center>
      <ChunkUploader
        :bucket="bucket || 'default'"
        :tip="`上传到桶 [${bucket || 'default'}], 大文件自动分片 (5MB/片), 中断后下次继续可断点续传`"
        @uploaded="onUploaded"
        @error="onError"
      />
      <template #footer>
        <el-button @click="showUploader = false">关闭</el-button>
        <el-button type="primary" @click="afterUpload">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box, Search, Refresh, Document, Upload, Download, CopyDocument } from '@element-plus/icons-vue'
import { fileApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'
import ChunkUploader from '@/components/ChunkUploader.vue'

defineOptions({ name: 'Files' })

const router = useRouter()
const bus = useGlobalBus()

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const kw = ref('')
const bucket = ref('default')
const buckets = ref(['default', 'kb', 'dataset', 'model'])
const loading = ref(false)
const stats = ref({})
const showUploader = ref(false)

function formatSize(b) {
  if (!b) return '0 B'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(1) + ' MB'
  return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatTime(t) {
  if (!t) return '—'
  try {
    const d = new Date(t)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch (e) { return String(t) }
}

function iconColor(ct) {
  if (!ct) return '#94a3b8'
  if (ct.startsWith('image/')) return '#ec4899'
  if (ct.startsWith('video/')) return '#f59e0b'
  if (ct.startsWith('audio/')) return '#10b981'
  if (ct.includes('pdf')) return '#ef4444'
  if (ct.includes('json') || ct.includes('xml')) return '#6366f1'
  return '#64748b'
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (kw.value) params.name = kw.value
    if (bucket.value) params.bucket = bucket.value
    const r = await fileApi.list(params)
    if (r.code === 200) {
      rows.value = r.data?.rows || r.data || []
      total.value = r.data?.total || rows.value.length
    }
  } catch (e) {
    console.error('[files] load failed:', e)
    ElMessage.error('加载失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  // 后端可能没 stats 接口, 模拟算
  stats.value = {
    total: rows.value.length,
    totalSize: rows.value.reduce((s, r) => s + (r.sizeBytes || r.size || 0), 0),
    buckets: new Set(rows.value.map(r => r.bucket).filter(Boolean)).size
  }
}

async function download(row) {
  try {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token') || ''
    const resp = await fetch(fileApi.downloadUrl(row.id), {
      headers: token ? { 'Authorization': 'Bearer ' + token } : {}
    })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalName || `file-${row.id}`
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

async function copyKey(row) {
  try {
    await navigator.clipboard.writeText(row.objectKey || row.id)
    ElMessage.success('已复制: ' + (row.objectKey || row.id))
  } catch (e) {
    ElMessage.warning('复制失败, key = ' + (row.objectKey || row.id))
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除文件 ${row.originalName || row.objectKey}?`, '提示', { type: 'warning' })
    const r = await fileApi.remove(row.id)
    if (r.code === 200) {
      ElMessage.success('已删除')
      load()
    } else {
      ElMessage.error('删除失败: ' + r.message)
    }
  } catch (e) { /* cancel */ }
}

function onUploaded(payload) {
  // ★ 贯通: 上传成功后发 bus, 让 Knowledge/Dataset/Train 页能感知
  bus.emit('files:uploaded', payload)
  bus.emit('live:event', { type: 'file', text: `📁 上传 ${payload.file.name} 成功`, actor: 'file' })
}

function onError(payload) {
  bus.emit('live:event', { type: 'file', text: `✗ 上传 ${payload.file.name} 失败: ${payload.error}`, actor: 'file' })
}

function afterUpload() {
  showUploader.value = false
  load()
}

onMounted(() => {
  load()
  bus.emit('sys:event', { text: '进入文件管理' })
})
</script>

<style scoped>
.files-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.files-hero { padding: 16px 24px; background: linear-gradient(135deg, #6366f1, #4f46e5); border-radius: 12px; color: #fff; display: flex; justify-content: space-between; align-items: center; }
.files-hero h1 { margin: 0 0 4px; font-size: 22px; }
.files-hero p { margin: 0; opacity: 0.9; font-size: 13px; }
.hero-r { display: flex; gap: 24px; }
.hero-r :deep(.el-statistic__head) { color: rgba(255,255,255,0.9); }
.hero-r :deep(.el-statistic__number) { color: #fff; }
.files-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; }
.files-toolbar .spacer { flex: 1; }
.file-cell { display: flex; align-items: center; gap: 10px; }
.file-icon { font-size: 22px; }
.file-name { font-weight: 600; color: #1e293b; }
.file-meta { font-size: 11px; }
.muted { color: #94a3b8; }
</style>