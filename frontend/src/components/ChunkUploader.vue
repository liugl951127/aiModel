<template>
  <div class="chunk-uploader">
    <!-- 拖拽区 + 选择文件按钮 -->
    <div
      class="drop-zone"
      :class="{ 'is-dragover': dragover, 'is-disabled': disabled || uploading }"
      @dragover.prevent="dragover = true"
      @dragleave="dragover = false"
      @drop.prevent="onDrop"
      @click="triggerInput"
    >
      <el-icon class="dz-icon"><UploadFilled /></el-icon>
      <div class="dz-title">{{ uploading ? `上传中... ${progress}%` : '点击或拖拽文件到此处' }}</div>
      <div class="dz-sub">{{ tip || '支持任意大小, 自动分片 (5MB/片), 断点续传' }}</div>
      <input
        ref="fileInput"
        type="file"
        :multiple="multiple"
        :accept="accept"
        :disabled="disabled || uploading"
        style="display:none"
        @change="onPick"
      />
    </div>

    <!-- 上传进度 (单个文件一行) -->
    <div v-for="(f, i) in tasks" :key="i" class="task-row">
      <el-icon class="task-icon"><Document /></el-icon>
      <div class="task-info">
        <div class="task-name">{{ f.file.name }} <span class="muted">({{ formatSize(f.file.size) }})</span></div>
        <el-progress :percentage="f.progress" :status="f.status" :stroke-width="6" />
        <div class="task-log">
          <span v-if="f.status === 'success'">✓ 已上传 ({{ formatSize(f.file.size) }})</span>
          <span v-else-if="f.status === 'exception'">✗ {{ f.error || '失败' }}</span>
          <span v-else-if="f.status === 'paused'">⏸ 暂停 (已传 {{ f.received }}/{{ f.total }})</span>
          <span v-else-if="f.uploadId">已传 {{ f.received }}/{{ f.total }} 片</span>
        </div>
      </div>
      <div class="task-actions">
        <el-button v-if="f.status === 'paused' && f.uploadId" size="small" type="primary" plain @click="resume(f)">继续</el-button>
        <el-button v-if="(f.status === 'paused' || f.status === 'exception') && f.uploadId" size="small" plain @click="cancel(f)">取消</el-button>
        <el-button v-if="f.status === 'uploading'" size="small" type="warning" plain @click="pause(f)">暂停</el-button>
        <el-button v-if="f.status === 'success'" size="small" type="success" plain disabled>完成</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fileApi } from '@/api'

const props = defineProps({
  bucket: { type: String, default: 'default' },
  accept: { type: String, default: '' },
  multiple: { type: Boolean, default: true },
  chunkSize: { type: Number, default: 5 * 1024 * 1024 },  // 5MB
  disabled: { type: Boolean, default: false },
  tip: { type: String, default: '' }
})
const emit = defineEmits(['uploaded', 'error'])

const fileInput = ref(null)
const dragover = ref(false)
const tasks = ref([])   // [{ file, uploadId, total, received, progress, status, error, paused }]

const uploading = computed(() => tasks.value.some(t => t.status === 'uploading'))

function triggerInput() {
  if (props.disabled || uploading.value) return
  fileInput.value?.click()
}

function onPick(e) {
  const files = Array.from(e.target.files || [])
  files.forEach(uploadFile)
  e.target.value = ''
}

function onDrop(e) {
  dragover.value = false
  const files = Array.from(e.dataTransfer.files || [])
  files.forEach(uploadFile)
}

function formatSize(b) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(1) + ' MB'
  return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function uploadFile(file) {
  const task = {
    file,
    uploadId: '',
    total: 0,
    received: 0,
    progress: 0,
    status: 'uploading',
    error: '',
    paused: false,
    abort: null
  }
  tasks.value.push(task)

  try {
    // 1. init
    const initRes = await fileApi.chunkInit({
      originalName: file.name,
      contentType: file.type || 'application/octet-stream',
      totalSize: file.size,
      bucket: props.bucket,
      chunkSize: props.chunkSize
    })
    if (initRes.code !== 200) {
      task.status = 'exception'
      task.error = initRes.message || 'init 失败'
      return
    }
    const sess = initRes.data
    task.uploadId = sess.uploadId
    task.total = sess.totalChunks

    // 2. 检查已有 (断点续传)
    try {
      const st = await fileApi.chunkStatus(task.uploadId)
      if (st.code === 200 && st.data?.received) {
        task.received = st.data.received.length
        task.progress = Math.floor((task.received / task.total) * 100)
      }
    } catch (e) { /* 容错, 继续传 */ }

    // 3. 分片上传
    await uploadChunks(task)
  } catch (e) {
    task.status = 'exception'
    task.error = e?.response?.data?.message || e?.message || '未知错误'
    emit('error', { file, error: task.error })
  }
}

async function uploadChunks(task) {
  const cs = props.chunkSize
  for (let i = task.received; i < task.total; i++) {
    if (task.paused) {
      task.status = 'paused'
      return
    }
    const start = i * cs
    const end = Math.min(start + cs, task.file.size)
    const blob = task.file.slice(start, end)

    let retries = 0
    while (retries < 3) {
      try {
        const r = await fileApi.chunkPut(task.uploadId, i, blob)
        if (r.code !== 200) throw new Error(r.message || `分片 ${i} 失败`)
        const rd = r.data || {}
        task.received = rd.received ?? (task.received + 1)
        task.progress = Math.floor((task.received / task.total) * 100)
        break
      } catch (e) {
        retries++
        if (retries >= 3) throw e
        await new Promise(r => setTimeout(r, 500 * retries))
      }
    }
  }

  // 4. complete
  const c = await fileApi.chunkComplete(task.uploadId)
  if (c.code !== 200) {
    task.status = 'exception'
    task.error = c.message || '合并失败'
    return
  }
  task.status = 'success'
  task.progress = 100
  emit('uploaded', { file, key: c.data, task })
}

function pause(task) {
  task.paused = true
}
function resume(task) {
  task.paused = false
  task.status = 'uploading'
  uploadChunks(task).catch(e => {
    task.status = 'exception'
    task.error = e?.message || '恢复失败'
  })
}
function cancel(task) {
  task.paused = true
  task.status = 'exception'
  task.error = '已取消'
}
</script>

<style scoped>
.chunk-uploader { display: flex; flex-direction: column; gap: 12px; }
.drop-zone {
  border: 2px dashed #cbd5e1; border-radius: 12px;
  padding: 32px 16px; text-align: center; cursor: pointer;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  transition: all 0.18s;
}
.drop-zone:hover, .drop-zone.is-dragover {
  border-color: #6366f1; background: linear-gradient(180deg, #eef2ff, #e0e7ff);
}
.drop-zone.is-disabled { opacity: 0.5; cursor: not-allowed; }
.dz-icon { font-size: 40px; color: #6366f1; margin-bottom: 8px; }
.dz-title { font-size: 16px; font-weight: 600; color: #1e293b; }
.dz-sub { font-size: 12px; color: #64748b; margin-top: 4px; }

.task-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px; background: #fff; border-radius: 8px;
  border: 1px solid #e5e7eb;
}
.task-icon { font-size: 24px; color: #6366f1; flex-shrink: 0; }
.task-info { flex: 1; min-width: 0; }
.task-name { font-size: 13px; font-weight: 600; color: #1e293b; margin-bottom: 4px;
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-log { font-size: 11px; color: #64748b; margin-top: 2px; }
.muted { color: #94a3b8; font-weight: normal; }
.task-actions { display: flex; gap: 4px; flex-shrink: 0; }
</style>