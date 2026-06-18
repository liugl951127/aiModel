<template>
  <BizCrudPage
    title="📜 合同管理"
    search-placeholder="合同标题 / 编号"
    :columns="columns" :form-fields="formFields" :api="api" api-prefix="contract"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'code', label: '编号', width: 130 },
  { prop: 'title', label: '合同标题', minWidth: 200 },
  { prop: 'customerId', label: '客户', width: 80 },
  { prop: 'amount', label: '金额', width: 130, money: true },
  { prop: 'signDate', label: '签署日', width: 160, date: true },
  { prop: 'endDate', label: '截止日', width: 160, date: true },
  { prop: 'status', label: '状态', width: 100, tag: { '草签': 'info', '执行中': 'success', '已完结': 'primary', '已终止': 'danger' } }
]

const formFields = [
  { prop: 'code', label: '编号' },
  { prop: 'title', label: '标题' },
  { prop: 'customerId', label: '客户ID', type: 'number' },
  { prop: 'opportunityId', label: '商机ID', type: 'number' },
  { prop: 'quoteId', label: '报价ID', type: 'number' },
  { prop: 'amount', label: '金额', type: 'number' },
  { prop: 'signDate', label: '签署日', type: 'date' },
  { prop: 'startDate', label: '开始日', type: 'date' },
  { prop: 'endDate', label: '截止日', type: 'date' },
  { prop: 'status', label: '状态', select: true, options: [
    { value: '草签', label: '草签' },
    { value: '执行中', label: '执行中' },
    { value: '已完结', label: '已完结' },
    { value: '已终止', label: '已终止' }
  ]},
  { prop: 'paymentTerms', label: '付款条款', type: 'textarea' },
  { prop: 'notes', label: '备注', type: 'textarea' }
]

const api = bizApi
</script>
