<template>
  <div class="dt-page">
    <header class="dt-header">
      <h2>🔗 分布式事务 (Seata) 配置</h2>
      <el-tag :type="effectiveMode === 'SEATA' ? 'success' : effectiveMode === 'LOCAL_FALLBACK' ? 'warning' : 'info'" size="large">
        当前模式: {{ modeLabel }}
      </el-tag>
    </header>

    <el-alert :type="config.enabled ? 'success' : 'info'" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        {{ config.enabled ? '✅ 分布式事务已开启' : '⏸ 分布式事务已关闭 (产品功能不受影响)' }}
      </template>
      <p style="margin: 4px 0 0;">
        {{ config.enabled
          ? '所有 @GlobalTransactional 方法走 Seata AT 模式; 若 TC 不可达, 自动降级为本地 @Transactional 事务, 产品照常运行.'
          : '全部走本地 @Transactional, 不依赖 Seata TC. 可在生产环境关闭以减少运维负担.' }}
      </p>
    </el-alert>

    <el-card shadow="never" v-loading="loading">
      <el-form :model="config" label-width="180px" size="default" style="max-width: 800px">
        <el-form-item label="总开关">
          <el-switch v-model="config.enabled" active-text="开启" inactive-text="关闭"
                      @change="save" />
          <span class="muted" style="margin-left: 12px;">关闭后所有事务走本地 @Transactional</span>
        </el-form-item>
        <el-form-item label="Seata TC 地址">
          <el-input v-model="config.seataServerAddr" placeholder="127.0.0.1:8091" @blur="save" />
        </el-form-item>
        <el-form-item label="事务分组">
          <el-input v-model="config.txServiceGroup" @blur="save" />
        </el-form-item>
        <el-form-item label="超时 (ms)">
          <el-input-number v-model="config.timeoutMs" :min="1000" :max="300000" :step="1000" @change="save" />
        </el-form-item>
        <el-form-item label="自动降级">
          <el-switch v-model="config.autoFallback" active-text="是" inactive-text="否"
                      @change="save" />
          <span class="muted" style="margin-left: 12px;">TC 不可达时自动降级为本地事务</span>
        </el-form-item>
        <el-form-item label="Seata TC 探测">
          <el-tag :type="config.seataTcReachable ? 'success' : 'danger'" size="small">
            {{ config.seataTcReachable ? '✓ 可达' : '✗ 不可达' }}
          </el-tag>
          <el-button size="small" style="margin-left: 8px" @click="load">重新探测</el-button>
        </el-form-item>
        <el-form-item label="生效模式">
          <code>{{ effectiveMode }}</code>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <h3>📚 三种模式说明</h3>
      <el-table :data="modes" border>
        <el-table-column prop="mode" label="模式" width="160" />
        <el-table-column prop="desc" label="说明" min-width="200" />
        <el-table-column prop="scenario" label="适用场景" min-width="240" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { distTxApi } from '@/api'

const config = reactive({
  enabled: true,
  seataServerAddr: '127.0.0.1:8091',
  txServiceGroup: 'default_tx_group',
  timeoutMs: 60000,
  autoFallback: true,
  seataTcReachable: false
})
const loading = ref(false)
const effectiveMode = computed(() =>
  !config.enabled ? 'OFF' :
  config.seataTcReachable ? 'SEATA' : 'LOCAL_FALLBACK'
)
const modeLabel = computed(() => ({
  OFF: '关闭',
  SEATA: 'Seata 分布式事务',
  LOCAL_FALLBACK: '本地事务降级'
}[effectiveMode.value]))

const modes = [
  { mode: 'SEATA', desc: '@GlobalTransactional 走 Seata AT 模式 (undo_log)', scenario: '多库多服务强一致 (订单+库存+账务)' },
  { mode: 'LOCAL_FALLBACK', desc: '降级为 @Transactional 本地事务', scenario: 'TC 不可达, 产品不能挂, 单库原子性保证' },
  { mode: 'OFF', desc: '全部本地事务, 不引入 Seata', scenario: '运维减负, 接受弱一致' }
]

const load = async () => {
  loading.value = true
  try {
    const r = await distTxApi.get()
    if (r.code === 200) Object.assign(config, r.data)
  } catch (e) { ElMessage.error(`加载失败: ${e.message}`) } finally { loading.value = false }
}

const save = async () => {
  try {
    const r = await distTxApi.update({ ...config })
    if (r.code === 200) {
      Object.assign(config, r.data)
      ElMessage.success(`已保存, 当前模式: ${modeLabel.value}`)
    }
  } catch (e) { ElMessage.error(`保存失败: ${e.message}`) }
}

onMounted(load)
</script>

<style scoped>
.dt-page { padding: 12px; }
.dt-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.dt-header h2 { margin: 0; }
.muted { color: #94a3b8; font-size: 12px; }
code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
</style>
