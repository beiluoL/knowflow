<template><main><h1>搜索下拉样式验收（仅测试数据）</h1><div class="wide-shell"><Select v-model="value" aria-label="搜索样式验收" searchable :options="options" /></div><output data-value>{{ JSON.stringify(value) }}</output></main></template>
<script setup lang="ts">
import {ref} from 'vue'
import Select from '/src/components/ui/Select.vue'
const value=ref<number|null>(1000)
const options=Array.from({length:80},(_,i)=>({value:1000+i,label:i===1?'这是一个用于验收长名称省略和完整名称提示的知识库名称，不代表真实业务数据':'验收知识库 '+i}))
</script>
<style scoped>main{padding:var(--kb-space-6);min-height:100dvh}h1{margin-bottom:var(--kb-space-5)}.wide-shell{width:100%;overflow:hidden}output{display:block;margin-block:var(--kb-space-4)}</style>
