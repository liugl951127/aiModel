<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="(item, i) in stats" :key="i">
        <el-card shadow="hover">
          <div class="stat">
            <div>
              <div class="stat-label">{{ item.label }}</div>
              <div class="stat-value">{{ item.value }}</div>
            </div>
            <el-icon :size="36" :color="item.color"><component :is="item.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt-16">
      <el-col :span="12">
        <el-card>
          <template #header><b>平台能力</b></template>
          <ul class="cap-list">
            <li>✅ 大模型训练 / 导出 / 本地推理（自实现 Transformer）</li>
            <li>✅ 智能体 ReAct 循环 + 工具注册中心</li>
            <li>✅ 知识库 + ES 检索（带回退）</li>
            <li>✅ 多租户 + JWT 鉴权 + 网关</li>
            <li>✅ MyBatis-Plus 多租户行级隔离</li>
            <li>✅ Nacos 注册中心 / 配置中心</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><b>快速入口</b></template>
          <el-space wrap>
            <el-button type="primary" @click="$router.push('/inference')">推理测试</el-button>
            <el-button @click="$router.push('/agents')">智能体</el-button>
            <el-button @click="$router.push('/knowledge')">知识库</el-button>
            <el-button @click="$router.push('/models')">模型</el-button>
          </el-space>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Cpu, ChatDotRound, UserFilled, Reading } from '@element-plus/icons-vue'
import { modelApi, agentApi, knowledgeApi, inferenceApi } from '@/api'

const stats = ref([
  { label: '大模型', value: 0, icon: Cpu, color: '#2c5cff' },
  { label: '智能体', value: 0, icon: UserFilled, color: '#67c23a' },
  { label: '知识库', value: 0, icon: Reading, color: '#e6a23c' },
  { label: '本地推理模型', value: 0, icon: ChatDotRound, color: '#f56c6c' }
])

onMounted(async () => {
  try {
    const [m, a, k, inf] = await Promise.all([
      modelApi.list(), agentApi.list(), knowledgeApi.bases(), inferenceApi.models()
    ])
    stats.value[0].value = m.data?.length || 0
    stats.value[1].value = a.data?.length || 0
    stats.value[2].value = k.data?.length || 0
    stats.value[3].value = inf.data ? Object.keys(inf.data).length : 0
  } catch (e) { /* offline ok */ }
})
</script>

<style scoped>
.stat { display: flex; align-items: center; justify-content: space-between; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.cap-list { padding-left: 20px; line-height: 28px; color: #303133; }
</style>
