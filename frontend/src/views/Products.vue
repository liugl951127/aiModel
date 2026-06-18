<template>
  <BizCrudPage
    title="📦 产品 SKU"
    search-placeholder="产品名 / 编码"
    :columns="columns" :form-fields="formFields" :api="api" api-prefix="product"
    :row2form="r => r" :form2row="f => f"
  />
</template>

<script setup>
import { bizApi } from '@/api'
import BizCrudPage from '@/components/BizCrudPage.vue'

const columns = [
  { prop: 'code', label: '编码', width: 130 },
  { prop: 'name', label: '产品名', minWidth: 180 },
  { prop: 'category', label: '类别', width: 100 },
  { prop: 'price', label: '价格', width: 120, money: true },
  { prop: 'unit', label: '单位', width: 60 },
  { prop: 'stock', label: '库存', width: 80 },
  { prop: 'description', label: '描述', minWidth: 200 }
]

const formFields = [
  { prop: 'code', label: '编码' },
  { prop: 'name', label: '产品名' },
  { prop: 'category', label: '类别' },
  { prop: 'price', label: '价格', type: 'number' },
  { prop: 'unit', label: '单位' },
  { prop: 'stock', label: '库存', type: 'number' },
  { prop: 'description', label: '描述', type: 'textarea' }
]

const api = bizApi
</script>
