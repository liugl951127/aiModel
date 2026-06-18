<template>
  <!-- 通用业务 CRUD 页面 — Customer/Chat/Opportunity/Quote/Contract/Order/Product/Service 复用 -->
  <div class="biz-page">
    <header class="biz-header">
      <h2>{{ title }}</h2>
      <div class="biz-actions">
        <el-input v-model="kw" :placeholder="searchPlaceholder" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load">
          <el-icon><Search /></el-icon> 搜索
        </el-button>
        <el-button @click="load">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button type="success" @click="openCreate">
          <el-icon><Plus /></el-icon> 新增
        </el-button>
      </div>
    </header>

    <!-- 顶部统计 -->
    <div v-if="statsCards.length" class="biz-stats">
      <el-card v-for="s in statsCards" :key="s.label" shadow="never" class="stat-card">
        <el-statistic :title="s.label" :value="s.value" :precision="s.precision || 0" :prefix="s.prefix || ''" :value-style="{ color: s.color || '#6366f1' }" />
      </el-card>
    </div>

    <!-- 列表 -->
    <el-card shadow="never" class="biz-table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column type="index" label="#" width="48" />
        <el-table-column v-for="c in columns" :key="c.prop" :prop="c.prop" :label="c.label" :width="c.width" :min-width="c.minWidth">
          <template #default="{ row }">
            <template v-if="c.tag">
              <el-tag :type="tagType(row[c.prop], c.tag)" size="small">{{ displayVal(row, c) }}</el-tag>
            </template>
            <template v-else-if="c.money">
              ¥ {{ Number(row[c.prop] || 0).toLocaleString() }}
            </template>
            <template v-else-if="c.date">
              {{ formatDate(row[c.prop]) }}
            </template>
            <template v-else>
              {{ displayVal(row, c) }}
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="size"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end;" @current-change="load" @size-change="load"
      />
    </el-card>

    <!-- 新增/编辑 dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑' : '新增'" width="640px" align-center>
      <el-form :model="form" label-width="120px" label-position="right" size="default">
        <el-form-item v-for="c in formFields" :key="c.prop" :label="c.label">
          <template v-if="c.select">
            <el-select v-model="form[c.prop]" :placeholder="`选择 ${c.label}`" style="width: 100%">
              <el-option v-for="opt in (c.options || [])" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
          <template v-else-if="c.type === 'number'">
            <el-input-number v-model="form[c.prop]" :min="c.min" :max="c.max" style="width: 100%" />
          </template>
          <template v-else-if="c.type === 'textarea'">
            <el-input v-model="form[c.prop]" type="textarea" :rows="3" />
          </template>
          <template v-else-if="c.type === 'date'">
            <el-date-picker v-model="form[c.prop]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          </template>
          <template v-else>
            <el-input v-model="form[c.prop]" />
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'

// props: title, searchPlaceholder, columns, formFields, api (含 page/list/get/create/update/remove/stats),
//        row2form (row => form), form2row (form => body), stats (额外统计)
// ★ apiPrefix: 业务实体名前缀 (如 'product' / 'order' / 'customer'),
//   设了就调 api[\`\${apiPrefix}Page\`] / [\`\${apiPrefix}Create\`] 等
const props = defineProps({
  title: { type: String, required: true },
  searchPlaceholder: { type: String, default: '搜索' },
  columns: { type: Array, required: true },      // [{ prop, label, width, tag, money, date }]
  formFields: { type: Array, required: true },   // [{ prop, label, type, select, options, min, max }]
  api: { type: Object, required: true },
  apiPrefix: { type: String, default: '' },      // ★ 'product' / 'order' / 'customer' 等
  row2form: { type: Function, default: r => r },
  form2row: { type: Function, default: f => f },
  statsCards: { type: Array, default: () => [] }
})

const kw = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive({})

const tagType = (val, mapping) => {
  if (!mapping) return 'primary'
  const v = String(val || '').toLowerCase()
  for (const k in mapping) if (v === String(k).toLowerCase()) return mapping[k]
  return 'primary'
}

const displayVal = (row, c) => {
  const v = row[c.prop]
  if (v == null) return '-'
  if (c.options) {
    const opt = c.options.find(o => o.value === v)
    return opt ? opt.label : v
  }
  return v
}

const formatDate = (s) => {
  if (!s) return '-'
  try { return new Date(s).toLocaleString('zh-CN') } catch (e) { return s }
}

const load = async () => {
  loading.value = true
  try {
    const pageFn = props.apiPrefix ? props.api[`${props.apiPrefix}Page`] : props.api.page
    if (!pageFn) {
      ElMessage.error(`接口未配置: ${props.apiPrefix}Page / page`)
      rows.value = []; total.value = 0; return
    }
    const r = await pageFn({ page: page.value, size: size.value, keyword: kw.value })
    if (r.code === 200) {
      rows.value = r.data.records || []
      total.value = r.data.total || 0
    }
  } catch (e) {
    ElMessage.error(`加载失败: ${e.message}`)
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, props.form2row({}))
  dialogVisible.value = true
}

const openEdit = (row) => {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, props.row2form(row))
  dialogVisible.value = true
}

const save = async () => {
  try {
    const body = props.form2row(form)
    const createFn = props.apiPrefix ? props.api[`${props.apiPrefix}Create`] : props.api.create
    const updateFn = props.apiPrefix ? props.api[`${props.apiPrefix}Update`] : props.api.update
    let r
    if (form.id) {
      r = await updateFn(body)
    } else {
      r = await createFn(body)
    }
    if (r.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      load()
    } else {
      ElMessage.error(r.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error(`保存失败: ${e.message}`)
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除 "${row.name || row.title || row.code || row.id}"?`, '提示', { type: 'warning' })
    const removeFn = props.apiPrefix ? props.api[`${props.apiPrefix}Remove`] : props.api.remove
    if (!removeFn) {
      ElMessage.error(`接口未配置: ${props.apiPrefix}Remove / remove`)
      return
    }
    const r = await removeFn(row.id)
    if (r.code === 200) {
      ElMessage.success('已删除')
      load()
    } else {
      ElMessage.error(r.message)
    }
  } catch (e) { /* cancel */ }
}

watch(kw, () => { page.value = 1; load() })

onMounted(load)
</script>

<style scoped>
.biz-page { display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.biz-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; }
.biz-header h2 { margin: 0; font-size: 18px; }
.biz-actions { display: flex; align-items: center; gap: 8px; }
.biz-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.stat-card { padding: 8px 12px !important; }
.biz-table-card { padding: 4px 8px !important; }
</style>
