<template>
  <BizCrudPage
    title="🛠️ 服务项目"
    search-placeholder="服务名"
    :columns="columns" :form-fields="formFields" :api="api" api-prefix="service"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'code', label: '编码', width: 130 },
  { prop: 'name', label: '服务名', minWidth: 200 },
  { prop: 'category', label: '类别', width: 100 },
  { prop: 'price', label: '价格', width: 120, money: true },
  { prop: 'slaHours', label: 'SLA(小时)', width: 100 },
  { prop: 'description', label: '描述', minWidth: 200 }
]

const formFields = [
  { prop: 'code', label: '编码' },
  { prop: 'name', label: '服务名' },
  { prop: 'category', label: '类别' },
  { prop: 'price', label: '价格', type: 'number' },
  { prop: 'slaHours', label: 'SLA(小时)', type: 'number' },
  { prop: 'description', label: '描述', type: 'textarea' }
]

const api = bizApi
</script>
