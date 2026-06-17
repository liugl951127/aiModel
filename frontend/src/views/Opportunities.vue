<template>
  <BizCrudPage
    title="💼 商机管理"
    search-placeholder="商机名"
    :columns="columns" :form-fields="formFields" :api="api" :stats-cards="statsCards"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'name', label: '商机名', minWidth: 200 },
  { prop: 'customerId', label: '客户ID', width: 80 },
  { prop: 'amount', label: '金额', width: 130, money: true },
  { prop: 'stage', label: '阶段', width: 100, tag: { '线索': 'info', '接触': 'primary', '方案': 'warning', '谈判': 'warning', '成交': 'success', '输单': 'danger' } },
  { prop: 'probability', label: '概率(%)', width: 80 },
  { prop: 'expectedDate', label: '预计成交', width: 160, date: true }
]

const formFields = [
  { prop: 'name', label: '商机名' },
  { prop: 'customerId', label: '客户ID', type: 'number' },
  { prop: 'amount', label: '金额', type: 'number' },
  { prop: 'stage', label: '阶段', select: true, options: [
    { value: '线索', label: '线索' },
    { value: '接触', label: '接触' },
    { value: '方案', label: '方案' },
    { value: '谈判', label: '谈判' },
    { value: '成交', label: '成交' },
    { value: '输单', label: '输单' }
  ]},
  { prop: 'probability', label: '概率(0-100)', type: 'number', min: 0, max: 100 },
  { prop: 'expectedDate', label: '预计成交', type: 'date' },
  { prop: 'source', label: '来源' },
  { prop: 'notes', label: '备注', type: 'textarea' }
]

const api = bizApi
const statsCards = ref([])
const loadStats = async () => {
  try {
    const r = await bizApi.opportunityStats()
    if (r.code === 200) {
      const d = r.data.byStage
      statsCards.value = Object.entries(d).map(([k, v]) => ({
        label: k, value: `${v.count}单 / ¥${Number(v.amount).toLocaleString()}`,
        color: k === '成交' ? '#10b981' : k === '输单' ? '#ef4444' : '#6366f1'
      }))
    }
  } catch (e) {}
}
onMounted(loadStats)
</script>
