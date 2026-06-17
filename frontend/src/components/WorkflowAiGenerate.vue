<template>
  <el-dialog v-model="visible" title="🪄 AI 极速生成流程" width="680px" :close-on-click-modal="false"
    @open="onOpen" @close="onClose">
    <div class="ai-gen">
      <!-- 当前画布状态 (多轮时显示) -->
      <div v-if="hasCurrent" class="ctx-bar">
        <el-icon><InfoFilled /></el-icon>
        <span>当前画布: <b>{{ currentName }}</b> · {{ currentNodes.length }} 节点 / {{ currentEdges.length }} 边</span>
        <el-tag v-if="lastAction" size="small" :type="actionType">{{ actionLabel }}</el-tag>
      </div>

      <!-- 用户输入 -->
      <div class="input-box">
        <el-input v-model="userInput" type="textarea" :rows="2"
          :placeholder="inputPlaceholder"
          @keyup.ctrl.enter="onGenerate" />
        <div class="hint">💡 支持多轮: '多 3 个评估节点' / '改成 RAG' / 'topK=5' / Ctrl+Enter 发送</div>
      </div>

      <!-- 生成按钮 -->
      <div class="gen-btn-row">
        <el-button type="primary" :loading="loading" @click="onGenerate" size="large">
          <el-icon><MagicStick /></el-icon>
          {{ hasCurrent ? 'AI 修改画布' : 'AI 一键生成' }}
        </el-button>
        <el-button v-if="hasCurrent" type="success" :disabled="!result || !result.nodes.length" plain @click="onRunDirect">
          <el-icon><VideoPlay /></el-icon> 应用并运行
        </el-button>
        <el-button @click="onReset" plain>{{ hasCurrent ? '重置对话' : '清空' }}</el-button>
      </div>

      <!-- 预设场景 (无当前画布时显示) -->
      <div v-if="!hasCurrent" class="scenarios">
        <div class="sc-label">📦 预设场景 (一键填):</div>
        <div class="sc-grid">
          <div v-for="s in scenarios" :key="s.key" class="sc-card" @click="useScenario(s)">
            <div class="sc-icon">{{ s.icon }}</div>
            <div class="sc-name">{{ s.name }}</div>
            <div class="sc-desc">{{ s.desc }}</div>
          </div>
        </div>
      </div>

      <!-- 多轮快捷命令 (有画布时显示) -->
      <div v-else class="scenarios">
        <div class="sc-label">💬 多轮快捷:</div>
        <div class="sc-grid">
          <div v-for="(q, i) in quickModifications" :key="i" class="sc-card sc-small" @click="useQuick(q)">
            <div class="sc-icon">{{ q.icon }}</div>
            <div class="sc-name">{{ q.name }}</div>
          </div>
        </div>
      </div>

      <!-- 生成结果 -->
      <div v-if="result" class="result">
        <div class="r-head">
          <div>
            <span class="r-name">
              <el-icon v-if="lastAction === 'replace'"><Refresh /></el-icon>
              <el-icon v-else-if="lastAction === 'add_node'"><Plus /></el-icon>
              <el-icon v-else-if="lastAction === 'delete_node'"><Delete /></el-icon>
              <el-icon v-else-if="lastAction === 'update_params'"><Setting /></el-icon>
              {{ result.name || '结果' }}
            </span>
            <el-tag v-if="result.action" size="small" :type="actionType" style="margin-left: 8px;">
              {{ actionLabel }}
            </el-tag>
          </div>
          <div class="r-meta">
            {{ result.nodes.length }} 节点 · {{ result.edges.length }} 边
          </div>
        </div>
        <div v-if="result.description" class="r-desc">{{ result.description }}</div>

        <!-- 节点预览 -->
        <div class="r-nodes">
          <div v-for="(n, i) in result.nodes" :key="n.id" class="r-node">
            <div class="r-num">{{ i + 1 }}</div>
            <div class="r-info">
              <div class="r-node-name">{{ n.name }}</div>
              <div class="r-node-type">{{ n.type }}</div>
            </div>
            <el-icon v-if="i < result.nodes.length - 1" class="r-arrow"><Right /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="result && result.nodes.length" type="primary" @click="onApply">
        <el-icon><Check /></el-icon>
        {{ hasCurrent ? '应用到画布' : '填到画布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onMounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Right, Check, VideoPlay, Plus, Delete, Refresh, Setting,
  InfoFilled
} from '@element-plus/icons-vue'
import { workflowApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 注入当前画布, 启用多轮
  current: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'apply'])

const bus = useGlobalBus()

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => visible.value = v)
watch(visible, v => emit('update:modelValue', v))

const userInput = ref('')
const loading = ref(false)
const result = ref(null)
const scenarios = ref([])

// 当前画布状态
const hasCurrent = computed(() => props.current && (props.current.nodes?.length || 0) > 0)
const currentName = computed(() => props.current?.name || '未命名')
const currentNodes = computed(() => props.current?.nodes || [])
const currentEdges = computed(() => props.current?.edges || [])
const lastAction = computed(() => result.value?.action)

const actionLabel = computed(() => ({
  replace: '🔄 重生成',
  add_node: '➕ 新增节点',
  delete_node: '➖ 删除节点',
  update_params: '⚙️ 更新参数'
}[lastAction.value] || '已生成'))

const actionType = computed(() => ({
  replace: 'warning',
  add_node: 'success',
  delete_node: 'danger',
  update_params: 'info'
}[lastAction.value] || 'success'))

const inputPlaceholder = computed(() => {
  if (hasCurrent.value) {
    return '多轮修改, 例: 多加 2 个评估节点 / 改成营销文案 / 删掉第 1 个 / topK=5'
  }
  return '一句话描述, 例: 做一个 RAG 知识库问答, 用 BGE 中文嵌入, topK=5'
})

const quickModifications = computed(() => [
  { icon: '➕', name: '加 1 个评估节点' },
  { icon: '➕', name: '加 1 个 LoRA 训练' },
  { icon: '➕', name: '加 1 个数据加载' },
  { icon: '🔄', name: '改成营销文案' },
  { icon: '🔄', name: '改成客服自动回复' },
  { icon: '➖', name: '删掉最后一个节点' },
  { icon: '⚙️', name: 'topK=10' },
  { icon: '⚙️', name: 'epochs=5' }
])

// 接收来自 AI 助手的 generate 事件
onMounted(() => {
  bus.on('workflow:ai-generate', ({ input }) => {
    visible.value = true
    nextTick(() => {
      userInput.value = input || ''
      setTimeout(() => onGenerate(), 200)
    })
  })
})

const onOpen = async () => {
  result.value = null
  userInput.value = ''
  if (!hasCurrent.value) {
    try {
      const r = await workflowApi.aiScenarios()
      if (r.data?.code === 200) scenarios.value = r.data.data
    } catch (e) { scenarios.value = [] }
  }
}

const onClose = () => { result.value = null; userInput.value = '' }

const useScenario = (s) => {
  userInput.value = s.input
  onGenerate()
}

const useQuick = (q) => {
  userInput.value = q.name
  onGenerate()
}

const onGenerate = async () => {
  if (!userInput.value.trim()) {
    ElMessage.warning('请先描述你的需求')
    return
  }
  loading.value = true
  try {
    let r
    if (hasCurrent.value) {
      // 多轮修改
      r = await workflowApi.aiModify(userInput.value.trim(), {
        name: currentName.value,
        nodes: currentNodes.value,
        edges: currentEdges.value
      })
    } else {
      r = await workflowApi.aiGenerate(userInput.value.trim())
    }
    if (r.data?.code === 200) {
      result.value = r.data.data
      if (result.value.nodes.length === 0) {
        ElMessage.info('未识别到场景, 请描述更具体')
      } else {
        const a = result.value.action || 'replace'
        const label = { replace: '已重生成', add_node: '已新增节点', delete_node: '已删除节点', update_params: '已更新参数' }[a]
        ElMessage.success(`${label}: ${result.value.nodes.length} 节点 / ${result.value.edges.length} 边`)
      }
    }
  } catch (e) {
    ElMessage.error('生成失败: ' + (e?.message || '网络错误'))
  } finally {
    loading.value = false
  }
}

const onApply = () => {
  if (!result.value || !result.value.nodes.length) {
    ElMessage.warning('没有可应用的节点')
    return
  }
  emit('apply', result.value)
  visible.value = false
}

const onRunDirect = () => {
  if (!result.value || !result.value.nodes.length) {
    ElMessage.warning('没有可运行的节点')
    return
  }
  emit('apply', result.value)
  visible.value = false
  // 通知 workflow 跑
  setTimeout(() => bus.emit('workflow:run-direct', {}), 300)
}

const onReset = () => { userInput.value = ''; result.value = null }
</script>

<style scoped>
.ai-gen { padding: 0 4px; }

.ctx-bar {
  display: flex; align-items: center; gap: 8px;
  background: linear-gradient(135deg, #eef2ff, #f0f9ff);
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  padding: 6px 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #4338ca;
}

.input-box { margin-bottom: 10px; }
.hint { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.gen-btn-row { display: flex; gap: 8px; margin-bottom: 16px; }

.scenarios { margin-bottom: 16px; }
.sc-label { font-size: 13px; color: #475569; margin-bottom: 8px; font-weight: 600; }
.sc-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.sc-card {
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.sc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px -2px rgba(99, 102, 241, 0.2);
  border-color: #6366f1;
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
}
.sc-small { padding: 6px 8px; text-align: center; }
.sc-icon { font-size: 18px; margin-bottom: 2px; }
.sc-name { font-size: 11px; font-weight: 600; color: #1e293b; }

.result {
  background: linear-gradient(135deg, #f0fdf4, #ecfdf5);
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  padding: 12px;
}
.r-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.r-name { font-size: 14px; font-weight: 700; color: #15803d; display: inline-flex; align-items: center; gap: 4px; }
.r-meta { font-size: 11px; color: #16a34a; }
.r-desc { font-size: 11px; color: #475569; margin-bottom: 8px; }

.r-nodes { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.r-node {
  display: flex; align-items: center;
  background: #fff;
  border: 1px solid #86efac;
  border-radius: 6px;
  padding: 4px 8px;
  gap: 6px;
}
.r-num {
  width: 18px; height: 18px;
  background: #22c55e;
  color: #fff;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700;
}
.r-info { display: flex; flex-direction: column; }
.r-node-name { font-size: 11px; font-weight: 600; color: #1e293b; }
.r-node-type { font-size: 9px; color: #94a3b8; }
.r-arrow { color: #22c55e; font-size: 14px; }
</style>
