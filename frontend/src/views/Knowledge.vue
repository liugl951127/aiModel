<template>
  <div class="page-container">
    <el-card>
      <div class="card-header">
        <b>知识库</b>
        <el-button type="primary" @click="openCreateBase">+ 新建知识库</el-button>
      </div>
      <el-table :data="bases" v-loading="loading" border>
        <el-table-column prop="kbCode" label="编码" width="160" />
        <el-table-column prop="kbName" label="名称" />
        <el-table-column prop="indexName" label="ES 索引" width="160" />
        <el-table-column prop="embeddingModel" label="Embedding" width="160" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="openUpload(row)">上传文档</el-button>
            <el-button size="small" @click="openSearch(row)">搜索测试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialog" title="新建知识库" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.kbName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="Embedding"><el-input v-model="form.embeddingModel" placeholder="byte-hash" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="createBase">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadDialog" title="上传文档" width="500px">
      <el-upload :auto-upload="false" :on-change="onFileChange" :show-file-list="false">
        <el-button>选择文件</el-button>
      </el-upload>
      <div v-if="currentFile" class="mt-12">已选: {{ currentFile.name }}</div>
      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button type="primary" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="searchDialog" title="搜索测试" width="700px">
      <el-input v-model="searchQuery" placeholder="输入查询">
        <template #append>
          <el-button @click="doSearch">搜索</el-button>
        </template>
      </el-input>
      <el-table :data="searchResults" class="mt-12">
        <el-table-column prop="score" label="Score" width="80" />
        <el-table-column prop="content" label="内容" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api'

const bases = ref([])
const loading = ref(false)
const createDialog = ref(false)
const uploadDialog = ref(false)
const searchDialog = ref(false)
const searchQuery = ref('')
const searchResults = ref([])
const currentBase = ref(null)
const currentFile = ref(null)

const form = reactive({ kbName: '', description: '', embeddingModel: 'byte-hash' })

const load = async () => {
  loading.value = true
  try {
    const resp = await knowledgeApi.bases()
    bases.value = resp.data || []
  } finally { loading.value = false }
}

const openCreateBase = () => { createDialog.value = true }
const createBase = async () => {
  await knowledgeApi.createBase(form)
  ElMessage.success('已创建')
  createDialog.value = false
  load()
}

const openUpload = (row) => { currentBase.value = row; uploadDialog.value = true }
const onFileChange = (file) => { currentFile.value = file.raw }
const doUpload = async () => {
  if (!currentFile.value) return
  await knowledgeApi.upload(currentBase.value.id, currentFile.value)
  ElMessage.success('已上传，正在索引')
  uploadDialog.value = false
}

const openSearch = (row) => { currentBase.value = row; searchDialog.value = true; searchQuery.value = ''; searchResults.value = [] }
const doSearch = async () => {
  const resp = await knowledgeApi.search(currentBase.value.id, searchQuery.value, 5)
  searchResults.value = resp.data || []
}

onMounted(load)
</script>
