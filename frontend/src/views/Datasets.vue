<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>数据集</b>
        <el-button type="primary" @click="openCreate">+ 新建数据集</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="datasetCode" label="编码" width="160" />
        <el-table-column prop="datasetName" label="名称" />
        <el-table-column prop="format" label="格式" width="100" />
        <el-table-column prop="sampleCount" label="样本数" width="120" />
        <el-table-column prop="language" label="语言" width="80" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="useInTrain(row)">
              <el-icon><VideoPlay /></el-icon> 训练
            </el-button>
            <el-button size="small" @click="useInWorkflow(row)">用干编排</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建数据集" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="数据集名"><el-input v-model="form.datasetName" /></el-form-item>
        <el-form-item label="格式"><el-input v-model="form.format" placeholder="jsonl" /></el-form-item>
        <el-form-item label="样本数"><el-input-number v-model="form.sampleCount" :min="0" /></el-form-item>
        <el-form-item label="语言"><el-input v-model="form.language" placeholder="zh" /></el-form-item>
        <el-form-item label="存储路径"><el-input v-model="form.storagePath" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import { datasetApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const router = useRouter()
const bus = useGlobalBus()
const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ datasetName: '', format: 'jsonl', sampleCount: 0, language: 'zh', storagePath: '' })

const load = async () => {
  loading.value = true
  try {
    const resp = await datasetApi.page({ current: 1, size: 20 })
    rows.value = resp.records || []
  } finally { loading.value = false }
}

const openCreate = () => { dialog.value = true }
const submit = async () => {
  await datasetApi.create(form)
  ElMessage.success('已创建')
  dialog.value = false
  load()
}

onMounted(load)

// ★ 贯通: 数据集 → 训练页 (带 datasetName/Path 自动填)
const useInTrain = (row) => {
  router.push({
    path: '/train',
    query: {
      datasetId: row.id,
      datasetName: row.datasetName,
      datasetPath: row.storagePath || ('/opt/ai-platform/corpus/' + row.datasetCode + '.' + (row.format || 'jsonl'))
    }
  })
}

// ★ 贯通: 数据集 → 流程编排 (发 bus 事件, AI 助手跳过去)
const useInWorkflow = (row) => {
  bus.emit('workflow:ai-generate', {
    input: `使用数据集 ${row.datasetName} (格式: ${row.format}) 训练模型`
  })
  router.push('/workflow')
}
</script>
