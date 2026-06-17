<template>
  <BizCrudPage
    title="📦 订单管理"
    search-placeholder="订单编号"
    :columns="columns" :form-fields="formFields" :api="api"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'code', label: '订单号', width: 130 },
  { prop: 'customerId', label: '客户', width: 80 },
  { prop: 'amount', label: '总额', width: 130, money: true },
  { prop: 'paid', label: '已付', width: 130, money: true },
  { prop: 'status', label: '状态', width: 100, tag: { '待付款': 'warning', '部分付款': 'primary', '已付款': 'success', '已发货': 'success', '已完成': 'success' } },
  { prop: 'deliveryDate', label: '交付日', width: 160, date: true }
]

const formFields = [
  { prop: 'code', label: '订单号' },
  { prop: 'customerId', label: '客户ID', type: 'number' },
  { prop: 'contractId', label: '合同ID', type: 'number' },
  { prop: 'amount', label: '总额', type: 'number' },
  { prop: 'paid', label: '已付', type: 'number' },
  { prop: 'status', label: '状态', select: true, options: [
    { value: '待付款', label: '待付款' },
    { value: '部分付款', label: '部分付款' },
    { value: '已付款', label: '已付款' },
    { value: '已发货', label: '已发货' },
    { value: '已完成', label: '已完成' }
  ]},
  { prop: 'deliveryDate', label: '交付日', type: 'date' },
  { prop: 'notes', label: '备注', type: 'textarea' }
]

const api = bizApi
</script>
