<template>
  <BizCrudPage
    title="💰 报价管理"
    search-placeholder="报价标题 / 编号"
    :columns="columns" :form-fields="formFields" :api="api" api-prefix="quote"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'code', label: '编号', width: 130 },
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'customerId', label: '客户', width: 80 },
  { prop: 'totalAmount', label: '总额', width: 130, money: true },
  { prop: 'discount', label: '折扣', width: 80 },
  { prop: 'finalAmount', label: '实付', width: 130, money: true },
  { prop: 'status', label: '状态', width: 100, tag: { '草稿': 'info', '审批中': 'warning', '已发送': 'primary', '已接受': 'success', '已拒绝': 'danger' } }
]

const formFields = [
  { prop: 'code', label: '编号' },
  { prop: 'title', label: '标题' },
  { prop: 'customerId', label: '客户ID', type: 'number' },
  { prop: 'opportunityId', label: '商机ID', type: 'number' },
  { prop: 'totalAmount', label: '总额', type: 'number' },
  { prop: 'discount', label: '折扣(0-1)', type: 'number', min: 0, max: 1 },
  { prop: 'finalAmount', label: '实付', type: 'number' },
  { prop: 'status', label: '状态', select: true, options: [
    { value: '草稿', label: '草稿' },
    { value: '审批中', label: '审批中' },
    { value: '已发送', label: '已发送' },
    { value: '已接受', label: '已接受' },
    { value: '已拒绝', label: '已拒绝' }
  ]},
  { prop: 'validUntil', label: '有效期', type: 'date' },
  { prop: 'notes', label: '备注', type: 'textarea' }
]

const api = bizApi
</script>
