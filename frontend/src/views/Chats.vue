<template>
  <BizCrudPage
    title="🤝 洽谈记录"
    search-placeholder="洽谈主题"
    :columns="columns" :form-fields="formFields" :api="api"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'subject', label: '主题', minWidth: 200 },
  { prop: 'customerId', label: '客户', width: 80 },
  { prop: 'type', label: '方式', width: 80 },
  { prop: 'status', label: '状态', width: 100, tag: { '进行中': 'primary', '已成交': 'success', '已搁置': 'info' } },
  { prop: 'nextStep', label: '下一步', minWidth: 150 },
  { prop: 'nextDate', label: '下次日期', width: 160, date: true },
  { prop: 'createdAt', label: '创建', width: 160, date: true }
]

const formFields = [
  { prop: 'subject', label: '主题' },
  { prop: 'customerId', label: '客户ID', type: 'number' },
  { prop: 'type', label: '方式', select: true, options: [
    { value: '面谈', label: '面谈' },
    { value: '电话', label: '电话' },
    { value: '微信', label: '微信' },
    { value: '邮件', label: '邮件' }
  ]},
  { prop: 'status', label: '状态', select: true, options: [
    { value: '进行中', label: '进行中' },
    { value: '已成交', label: '已成交' },
    { value: '已搁置', label: '已搁置' }
  ]},
  { prop: 'nextStep', label: '下一步' },
  { prop: 'nextDate', label: '下次日期', type: 'date' },
  { prop: 'summary', label: '洽谈纪要', type: 'textarea' }
]

const api = bizApi
</script>
