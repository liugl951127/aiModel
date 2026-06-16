<template>
  <div class="page">
    <header class="hd">
      <div>
        <h2>📜 登录审计</h2>
        <p class="muted">谁/什么时间/IP/结果 — 全量登录审计日志, 来自 sys_login_audit 表</p>
      </div>
      <el-button :underline="false" @click="loadAll">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </header>

    <!-- 顶部 4 个统计卡 -->
    <section class="stats">
      <div class="stat s-total">
        <div class="s-ico">📊</div>
        <div class="s-meta">
          <div class="s-value">{{ stats.total || 0 }}</div>
          <div class="s-label">总记录</div>
        </div>
      </div>
      <div class="stat s-ok">
        <div class="s-ico">✅</div>
        <div class="s-meta">
          <div class="s-value">{{ stats.todaySuccess || 0 }}</div>
          <div class="s-label">今日成功</div>
        </div>
      </div>
      <div class="stat s-err">
        <div class="s-ico">❌</div>
        <div class="s-meta">
          <div class="s-value">{{ stats.todayFailed || 0 }}</div>
          <div class="s-label">今日失败</div>
        </div>
      </div>
      <div class="stat s-lock">
        <div class="s-ico">🔒</div>
        <div class="s-meta">
          <div class="s-value">{{ stats.todayLocked || 0 }}</div>
          <div class="s-label">今日锁定</div>
        </div>
      </div>
    </section>

    <!-- 过滤 -->
    <el-card shadow="never" class="box">
      <div class="filter-bar">
        <el-input v-model="filter.username" placeholder="按用户名" clearable style="width:160px" @change="load" />
        <el-select v-model="filter.status" placeholder="状态" clearable style="width:120px" @change="load">
          <el-option value="SUCCESS" label="成功" />
          <el-option value="FAILED" label="失败" />
          <el-option value="LOCKED" label="锁定" />
        </el-select>
      </div>

      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <strong>{{ row.username }}</strong>
            <small v-if="row.userId" class="muted"> (id={{ row.userId }})</small>
          </template>
        </el-table-column>
        <el-table-column label="租户" width="120">
          <template #default="{ row }">
            <span v-if="row.tenantId">#{{ row.tenantId }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }">
            <code class="ip">{{ row.loginIp || '—' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.loginStatus)" size="small">{{ row.loginStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="User-Agent" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <small class="muted ua">{{ row.userAgent || '—' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <small class="muted">{{ row.loginTime }}</small>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[20, 50, 100]"
        @current-change="load"
        @size-change="load"
        style="margin-top: 12px; text-align: right"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditApi } from '@/api'
import { useGlobalBus } from '@/composables/useGlobalBus'

const bus = useGlobalBus()
const loading = ref(false)
const records = ref([])
const stats = ref({})
const filter = reactive({ username: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const tagType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', LOCKED: 'warning' })[s] || 'info'

const load = async () => {
  loading.value = true
  try {
    const r = await auditApi.page({
      current: page.current,
      size: page.size,
      username: filter.username || undefined,
      status: filter.status || undefined
    })
    records.value = r.data?.records || []
    page.total = r.data?.total || 0
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}
const loadStats = async () => {
  try {
    const r = await auditApi.stats()
    stats.value = r.data || {}
  } catch {}
}
const loadAll = () => { load(); loadStats() }

onMounted(() => {
  loadAll()
  bus.emit('sys:event', { text: '进入登录审计' })
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.hd { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 12px; }
.hd h2 { margin: 0 0 2px; font-size: 18px; color: #1e293b; }
.muted { color: #94a3b8; }

.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat { display: flex; align-items: center; gap: 14px; padding: 16px; border-radius: 12px; color: #fff; }
.stat .s-ico { font-size: 32px; }
.stat .s-meta { flex: 1; }
.stat .s-value { font-size: 28px; font-weight: 700; line-height: 1; }
.stat .s-label { font-size: 12px; opacity: 0.9; margin-top: 4px; }
.s-total { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.s-ok    { background: linear-gradient(135deg, #10b981, #047857); }
.s-err   { background: linear-gradient(135deg, #ef4444, #b91c1c); }
.s-lock  { background: linear-gradient(135deg, #f59e0b, #d97706); }

.box { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.ip { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-family: monospace; font-size: 12px; }
.ua { font-size: 11px; word-break: break-all; }
</style>
