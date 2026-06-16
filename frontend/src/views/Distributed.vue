<template>
  <div class="dist-page">
    <header class="dist-head">
      <h2>🌐 分布式能力中心</h2>
      <p class="muted">基于 Redis + Redisson 实现的 7 大分布式能力 — 锁 / ID / 限流 / 幂等 / 缓存 / 事件总线 / 调度</p>
      <div class="health-bar">
        <el-tag :type="health ? 'success' : 'danger'" effect="dark">
          <el-icon><Connection /></el-icon>
          {{ health ? '服务在线' : '连接中...' }}
        </el-tag>
        <el-tag effect="plain" v-if="health">节点: {{ health.nodeId }}</el-tag>
        <el-tag effect="plain" type="info" v-for="f in (health?.features || [])" :key="f">{{ f }}</el-tag>
      </div>
    </header>

    <el-row :gutter="14" class="feature-grid">
      <!-- ============== 1. 分布式锁 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#6366f1">🔒</span>
              <div>
                <strong>分布式锁</strong>
                <small class="muted">Redisson Lock</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">防止并发场景下的资源争抢, 如创建订单/扣库存.</p>
          <el-input v-model="lockKey" placeholder="锁 key" size="small" class="mb-8">
            <template #prepend><span class="prefix-tag">order:</span></template>
          </el-input>
          <div class="btn-row">
            <el-button type="primary" size="small" :loading="lockLoading" @click="tryAcquireLock">
              抢锁
            </el-button>
            <el-button size="small" :disabled="!lockResult?.acquired" @click="releaseLock">释放</el-button>
          </div>
          <el-alert v-if="lockResult" :type="lockResult.acquired ? 'success' : 'warning'" :closable="false" show-icon class="mt-8">
            <template #title>
              {{ lockResult.acquired ? '✓ 抢到锁' : '× 抢锁失败' }}
              <small class="ml-4">耗时 {{ lockResult.elapsedMs }}ms</small>
            </template>
            <div class="alert-detail">
              <code>{{ lockResult.key }}</code>
            </div>
          </el-alert>
        </el-card>
      </el-col>

      <!-- ============== 2. 雪花 ID ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#8b5cf6">❄️</span>
              <div>
                <strong>雪花 ID</strong>
                <small class="muted">Snowflake Algorithm</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">分布式全局唯一 ID, 64 位 (41时间+10机器+12序列).</p>
          <el-input-number v-model="idCount" :min="1" :max="1000" size="small" class="mb-8" style="width:100%"/>
          <el-button type="primary" size="small" :loading="idLoading" @click="generateIds" block>
            生成 {{ idCount }} 个 ID
          </el-button>
          <div v-if="idResult" class="id-stats mt-8">
            <el-row :gutter="6">
              <el-col :span="8"><el-statistic title="数量" :value="idResult.count"/></el-col>
              <el-col :span="8"><el-statistic title="耗时" :value="idResult.elapsedMs" suffix="ms"/></el-col>
              <el-col :span="8"><el-statistic title="QPS" :value="idResult.qps" :precision="0"/></el-col>
            </el-row>
            <div class="id-time mt-8">
              <small class="muted">首个 ID 时间: <strong>{{ idResult.firstHumanTime }}</strong></small>
            </div>
            <el-scrollbar max-height="120" class="id-list">
              <code v-for="i in idResult.ids" :key="i" class="id-chip">{{ i }}</code>
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>

      <!-- ============== 3. 分布式限流 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#f59e0b">🚦</span>
              <div>
                <strong>分布式限流</strong>
                <small class="muted">Redis Token Bucket</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">滑动窗口计数, 多实例共享配额 (防刷/防爆破).</p>
          <el-row :gutter="6" class="mb-8">
            <el-col :span="12">
              <el-input v-model="rlLimit" size="small" type="number">
                <template #prepend>上限</template>
              </el-input>
            </el-col>
            <el-col :span="12">
              <el-input v-model="rlWindow" size="small" type="number">
                <template #prepend>秒</template>
              </el-input>
            </el-col>
          </el-row>
          <el-input v-model="rlKey" placeholder="限流 key" size="small" class="mb-8"/>
          <div class="btn-row">
            <el-button type="primary" size="small" :loading="rlLoading" @click="checkRateLimit">检查</el-button>
            <el-button size="small" @click="resetRateLimit">重置</el-button>
          </div>
          <div v-if="rlResult" class="rate-meter mt-8">
            <el-progress
              :percentage="Math.min(rlResult.current * 100 / rlResult.limit, 100)"
              :status="rlResult.allowed ? 'success' : 'exception'"
              :stroke-width="14"
            />
            <div class="rate-info">
              <small class="muted">当前 {{ rlResult.current }} / {{ rlResult.limit }} 次/{{ rlResult.windowSec }}s</small>
              <el-tag size="small" :type="rlResult.allowed ? 'success' : 'danger'" effect="dark">
                {{ rlResult.allowed ? '✓ 放行' : '× 拒绝' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- ============== 4. 分布式幂等 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#10b981">🔁</span>
              <div>
                <strong>分布式幂等</strong>
                <small class="muted">Redis Idempotency Token</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">防止表单重复提交 / API 重试, 60s 内同 token 只执行一次.</p>
          <el-input v-model="idemKey" placeholder="幂等 token" size="small" class="mb-8">
            <template #append>
              <el-button size="small" @click="newIdemKey">新</el-button>
            </template>
          </el-input>
          <div class="btn-row">
            <el-button type="primary" size="small" :loading="idemLoading" @click="submitIdempotency">
              提交 (模拟)
            </el-button>
            <el-button size="small" type="warning" plain @click="submitIdempotency" :disabled="!idemKey">
              再提一次 (验证幂等)
            </el-button>
          </div>
          <div v-if="idemResult" class="mt-8">
            <el-alert
              :type="idemResult.first ? 'success' : 'info'"
              :closable="false"
              show-icon
            >
              <template #title>
                {{ idemResult.first ? '✓ 首次执行' : `× 重复请求 (累计 ${idemResult.duplicateCount} 次)` }}
              </template>
              <div class="alert-detail">
                <small v-if="idemResult.first">
                  orderId: <code>{{ idemResult.orderId }}</code><br/>
                  result: <code>{{ idemResult.result }}</code>
                </small>
                <small v-else>
                  cached: <code>{{ idemResult.cachedResult || '无缓存' }}</code>
                </small>
              </div>
            </el-alert>
          </div>
        </el-card>
      </el-col>

      <!-- ============== 5. 分布式缓存 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#06b6d4">💾</span>
              <div>
                <strong>分布式缓存</strong>
                <small class="muted">Spring Cache + Redis</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">get-or-load 模式, 首次慢查 500ms, 命中 &lt;50ms.</p>
          <el-input v-model="cacheKey" placeholder="cache key" size="small" class="mb-8"/>
          <div class="btn-row">
            <el-button type="primary" size="small" :loading="cacheLoading" @click="getCache">读取</el-button>
            <el-button size="small" @click="evictCache">清除</el-button>
          </div>
          <div v-if="cacheResult" class="cache-result mt-8">
            <el-row :gutter="6">
              <el-col :span="12">
                <el-statistic title="耗时" :value="cacheResult.elapsedMs" suffix="ms"/>
              </el-col>
              <el-col :span="12">
                <el-statistic
                  title="命中"
                  :value="cacheResult.hit ? 'HIT' : 'MISS'"
                  :value-style="cacheResult.hit ? { color: '#10b981', fontWeight: 700 } : { color: '#ef4444', fontWeight: 700 }"
                />
              </el-col>
            </el-row>
            <code class="cache-value">{{ cacheResult.value }}</code>
          </div>
        </el-card>
      </el-col>

      <!-- ============== 6. 事件总线 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#ec4899">📡</span>
              <div>
                <strong>事件总线</strong>
                <small class="muted">Redis Pub/Sub</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">跨服务异步事件: <code>model.deployed</code> / <code>workflow.*</code>.</p>
          <el-input v-model="eventTopic" placeholder="topic" size="small" class="mb-8"/>
          <el-input v-model="eventPayload" placeholder='payload (json)' size="small" class="mb-8"/>
          <div class="btn-row">
            <el-button type="primary" size="small" :loading="eventLoading" @click="publishEvent">发布</el-button>
            <el-button size="small" @click="subscribeEvent">订阅</el-button>
          </div>
          <el-scrollbar max-height="160" class="event-log mt-8">
            <div v-for="(e, i) in eventLog" :key="i" class="event-item">
              <el-tag size="small" :type="i === 0 ? 'success' : 'info'" effect="dark">{{ e.topic }}</el-tag>
              <code class="ml-4">{{ JSON.stringify(e.payload) }}</code>
              <small class="muted ml-4">{{ new Date(e.ts).toLocaleTimeString() }}</small>
            </div>
            <el-empty v-if="!eventLog.length" :image-size="40" description="暂无事件"/>
          </el-scrollbar>
        </el-card>
      </el-col>

      <!-- ============== 7. 调度 / Leader 选举 ============== -->
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="feat-card">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#dc2626">👑</span>
              <div>
                <strong>Leader 选举</strong>
                <small class="muted">Redisson Lock + Scheduler</small>
              </div>
            </div>
          </template>
          <p class="feat-desc">集群中只有一个节点执行定时任务, 避免重复执行.</p>
          <el-button-group class="mb-8">
            <el-button type="primary" size="small" :loading="schedLoading" @click="runAsLeader">
              抢 leader
            </el-button>
            <el-button size="small" @click="refreshSchedInfo">刷新</el-button>
          </el-button-group>
          <el-row :gutter="6" v-if="schedInfo">
            <el-col :span="12">
              <el-statistic title="本节点 ID" :value="schedInfo.nodeId"/>
            </el-col>
            <el-col :span="12">
              <el-statistic title="执行次数" :value="schedInfo.leaderRuns"/>
            </el-col>
          </el-row>
          <el-tag class="mt-8" effect="dark" :type="leaderResult?.isLeader ? 'success' : 'info'">
            {{ leaderResult?.isLeader ? `✓ 本节点是 leader (${leaderResult?.nodeId})` : `× 让出给别的节点` }}
          </el-tag>
        </el-card>
      </el-col>

      <!-- ============== 8. 综合监控 ============== -->
      <el-col :span="24">
        <el-card shadow="hover" class="feat-card feat-monitor">
          <template #header>
            <div class="feat-hd">
              <span class="feat-icon" style="background:#1e293b">📊</span>
              <div>
                <strong>能力矩阵</strong>
                <small class="muted">一键查询所有分布式能力状态</small>
              </div>
            </div>
          </template>
          <el-row :gutter="10">
            <el-col :xs="12" :sm="6" :md="3" v-for="c in capabilityMatrix" :key="c.name">
              <div class="matrix-cell" :class="{ on: c.on }">
                <div class="mc-ico">{{ c.ico }}</div>
                <div class="mc-name">{{ c.name }}</div>
                <el-tag size="small" :type="c.on ? 'success' : 'info'" effect="dark">
                  {{ c.on ? 'ON' : 'OFF' }}
                </el-tag>
                <div class="mc-desc">{{ c.desc }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import { distributedApi } from '@/api'

const health = ref(null)
const loading = ref(false)

// ===== 1. 锁 =====
const lockKey = ref('demo-order-1')
const lockResult = ref(null)
const lockLoading = ref(false)
const tryAcquireLock = async () => {
  lockLoading.value = true
  try {
    const r = await distributedApi.lockDemo({ orderId: lockKey.value })
    lockResult.value = r.data
    if (lockResult.value.acquired) {
      ElMessage.success('抢到锁')
    } else {
      ElMessage.warning('已被占用')
    }
  } finally { lockLoading.value = false }
}
const releaseLock = async () => {
  if (!lockResult.value?.key) return
  await distributedApi.releaseLock(lockResult.value.key)
  ElMessage.success('已释放')
  lockResult.value = null
}

// ===== 2. ID =====
const idCount = ref(5)
const idResult = ref(null)
const idLoading = ref(false)
const generateIds = async () => {
  idLoading.value = true
  try {
    const r = await distributedApi.snowflake(idCount.value)
    idResult.value = r.data
  } finally { idLoading.value = false }
}

// ===== 3. 限流 =====
const rlKey = ref('demo-user1')
const rlLimit = ref(5)
const rlWindow = ref(60)
const rlResult = ref(null)
const rlLoading = ref(false)
const checkRateLimit = async () => {
  rlLoading.value = true
  try {
    const r = await distributedApi.rateLimitCheck({
      key: rlKey.value, limit: rlLimit.value, window: rlWindow.value
    })
    rlResult.value = r.data
  } finally { rlLoading.value = false }
}
const resetRateLimit = async () => {
  await distributedApi.rateLimitReset(rlKey.value)
  rlResult.value = null
  ElMessage.success('已重置')
}

// ===== 4. 幂等 =====
const idemKey = ref('')
const idemResult = ref(null)
const idemLoading = ref(false)
const newIdemKey = () => { idemKey.value = 'tok-' + Math.random().toString(36).slice(2, 10) }
const submitIdempotency = async () => {
  if (!idemKey.value) newIdemKey()
  idemLoading.value = true
  try {
    const r = await distributedApi.idempotencySubmit(idemKey.value, { amount: 100 })
    idemResult.value = r.data
  } finally { idemLoading.value = false }
}

// ===== 5. 缓存 =====
const cacheKey = ref('user:1')
const cacheResult = ref(null)
const cacheLoading = ref(false)
const getCache = async () => {
  cacheLoading.value = true
  try {
    const r = await distributedApi.cacheGet(cacheKey.value)
    cacheResult.value = r.data
  } finally { cacheLoading.value = false }
}
const evictCache = async () => {
  await distributedApi.cacheEvict(cacheKey.value)
  cacheResult.value = null
  ElMessage.success('已清除')
}

// ===== 6. 事件 =====
const eventTopic = ref('demo.event')
const eventPayload = ref('{"msg":"hello"}')
const eventLog = ref([])
const eventLoading = ref(false)
const publishEvent = async () => {
  eventLoading.value = true
  try {
    let payload = eventPayload.value
    try { payload = JSON.parse(eventPayload.value) } catch (e) {}
    await distributedApi.eventPublish({ topic: eventTopic.value, payload })
    pollEventLog()
    ElMessage.success('已发布')
  } finally { eventLoading.value = false }
}
const subscribeEvent = async () => {
  await distributedApi.eventSubscribe(eventTopic.value)
  ElMessage.success('已订阅')
}
const pollEventLog = async () => {
  try {
    const r = await distributedApi.eventLog()
    eventLog.value = r.data || []
  } catch (e) {}
}
let pollTimer = null
onMounted(() => {
  pollTimer = setInterval(pollEventLog, 2000)
  // 默认触发一些
  loadHealth()
})
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

// ===== 7. 调度 =====
const schedInfo = ref(null)
const leaderResult = ref(null)
const schedLoading = ref(false)
const runAsLeader = async () => {
  schedLoading.value = true
  try {
    const r = await distributedApi.schedulerLeader()
    leaderResult.value = r.data
    await refreshSchedInfo()
  } finally { schedLoading.value = false }
}
const refreshSchedInfo = async () => {
  const r = await distributedApi.schedulerInfo()
  schedInfo.value = r.data
}

const loadHealth = async () => {
  try {
    const r = await distributedApi.health()
    health.value = r.data
  } catch (e) {
    health.value = null
  }
}

const capabilityMatrix = [
  { name: '分布式锁', ico: '🔒', on: true, desc: 'Redisson Lock' },
  { name: '雪花 ID', ico: '❄️', on: true, desc: 'Snowflake' },
  { name: '限流', ico: '🚦', on: true, desc: 'Redis Token' },
  { name: '幂等', ico: '🔁', on: true, desc: 'Redis Token' },
  { name: '缓存', ico: '💾', on: true, desc: 'Spring Cache' },
  { name: '事件总线', ico: '📡', on: true, desc: 'Pub/Sub' },
  { name: '调度', ico: '👑', on: true, desc: 'Leader 选举' },
  { name: '链路追踪', ico: '🔗', on: true, desc: 'TraceId 透传' }
]
</script>

<style scoped>
.dist-page { padding: 0; }
.dist-head { margin-bottom: 16px; }
.dist-head h2 { margin: 0 0 4px; font-size: 18px; }
.dist-head .muted { color: #94a3b8; font-size: 12px; margin: 0 0 10px; }
.health-bar { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

.feature-grid { margin-bottom: 12px; }
.feat-card { margin-bottom: 12px; }
.feat-card :deep(.el-card__body) { padding: 14px; }
.feat-hd { display: flex; align-items: center; gap: 10px; }
.feat-icon {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.feat-hd strong { display: block; font-size: 14px; }
.feat-hd small { color: #94a3b8; font-size: 11px; }
.feat-desc { font-size: 12px; color: #64748b; line-height: 1.6; margin: 8px 0; }
.mb-8 { margin-bottom: 8px; }
.mt-8 { margin-top: 8px; }
.ml-4 { margin-left: 4px; }
.btn-row { display: flex; gap: 6px; }
.prefix-tag { font-size: 11px; color: #6366f1; }
.alert-detail { font-size: 11px; line-height: 1.7; margin-top: 4px; }
.alert-detail code { font-size: 11px; }

.id-stats .el-statistic__content { font-size: 14px; }
.id-time { font-size: 11px; }
.id-list { background: #f8fafc; border-radius: 6px; padding: 6px; margin-top: 6px; }
.id-chip {
  display: inline-block; margin: 2px; padding: 1px 5px; font-size: 10px;
  background: #ede9fe; color: #6d28d9; border-radius: 3px;
}

.rate-meter { padding: 4px 0; }
.rate-info { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }

.cache-result { padding: 4px 0; }
.cache-value {
  display: block; margin-top: 6px; padding: 4px 8px;
  background: #f1f5f9; border-radius: 4px; font-size: 11px; word-break: break-all;
}

.event-log {
  background: #1e293b; color: #e2e8f0; border-radius: 6px; padding: 8px;
  font-family: 'SF Mono', monospace; font-size: 11px;
}
.event-item { padding: 3px 0; border-bottom: 1px dashed #334155; }
.event-item:last-child { border-bottom: none; }
.event-item .muted { color: #94a3b8 !important; }

.feat-monitor { background: linear-gradient(135deg, #faf5ff 0%, #f0f9ff 100%); }
.matrix-cell {
  text-align: center; padding: 10px 6px; border-radius: 8px;
  background: #fff; border: 1px solid #e5e7eb;
  transition: all 0.18s;
}
.matrix-cell.on { border-color: #a7f3d0; box-shadow: 0 0 0 1px #a7f3d0; }
.mc-ico { font-size: 22px; }
.mc-name { font-size: 12px; font-weight: 600; color: #1e293b; margin: 2px 0; }
.mc-desc { font-size: 10px; color: #94a3b8; margin-top: 2px; }

.muted { color: #94a3b8; }
</style>
