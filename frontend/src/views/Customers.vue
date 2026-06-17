<template>
  <BizCrudPage
    title="👥 客户管理"
    search-placeholder="客户名 / 联系人"
    :columns="columns" :form-fields="formFields" :api="api" :stats-cards="statsCards"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'name', label: '客户名', minWidth: 160 },
  { prop: 'industry', label: '行业', width: 100 },
  { prop: 'scale', label: '规模', width: 80 },
  { prop: 'level', label: '等级', width: 80, tag: { 'S': 'danger', 'A': 'warning', 'B': 'primary', 'C': 'info' } },
  { prop: 'contactName', label: '联系人', width: 100 },
  { prop: 'contactPhone', label: '电话', width: 130 },
  { prop: 'source', label: '来源', width: 100 },
  { prop: 'createdAt', label: '创建', width: 160, date: true }
]

const formFields = [
  { prop: 'name', label: '客户名' },
  { prop: 'industry', label: '行业' },
  { prop: 'scale', label: '规模', select: true, options: [
    { value: '小', label: '小型' },
    { value: '中', label: '中型' },
    { value: '大', label: '大型' },
    { value: '超', label: '超大型' }
  ]},
  { prop: 'level', label: '等级', select: true, options: [
    { value: 'S', label: 'S - 战略' },
    { value: 'A', label: 'A - 重点' },
    { value: 'B', label: 'B - 常规' },
    { value: 'C', label: 'C - 潜在' }
  ]},
  { prop: 'contactName', label: '联系人' },
  { prop: 'contactPhone', label: '电话' },
  { prop: 'contactEmail', label: '邮箱' },
  { prop: 'source', label: '来源', select: true, options: [
    { value: '官网', label: '官网' },
    { value: '活动', label: '活动' },
    { value: '推荐', label: '推荐' },
    { value: '广告', label: '广告' }
  ]},
  { prop: 'address', label: '地址' },
  { prop: 'notes', label: '备注', type: 'textarea' }
]

const api = bizApi

const statsCards = ref([])
const loadStats = async () => {
  try {
    const r = await bizApi.customerStats()
    if (r.code === 200) {
      const d = r.data
      statsCards.value = [
        { label: '客户总数', value: d.total, color: '#6366f1' },
        { label: 'S 级战略', value: d.byLevel?.S || 0, color: '#ef4444' },
        { label: 'A 级重点', value: d.byLevel?.A || 0, color: '#f59e0b' },
        { label: 'B 级常规', value: d.byLevel?.B || 0, color: '#10b981' },
        { label: 'C 级潜在', value: d.byLevel?.C || 0, color: '#94a3b8' }
      ]
    }
  } catch (e) { /* ignore */ }
}
onMounted(loadStats)
</script>
