<template><main><h1>Checkbox V3 验收</h1><button id="keyboard-start" type="button">键盘起点</button><form @submit.prevent="submitted++"><Checkbox id="basic" v-model="basic" name="basic" value="yes" label="普通选项"/><label class="kb-checkbox-row"><Checkbox as="span" v-model="nested"/><span>嵌套标签</span></label><Checkbox v-model="half" indeterminate label="部分选中"/><Checkbox v-model="disabled" disabled label="禁用选项"/><Checkbox v-model="selected" :value="1" label="数组一"/><Checkbox v-model="selected" :value="2" label="数组二"/><Checkbox v-model="number" :true-value="1" :false-value="0" label="数字开关值"/><button type="submit">提交</button></form><output>{{JSON.stringify({basic,half,disabled,selected,number,submitted})}}</output></main></template>
<script setup lang="ts">
import {ref} from 'vue'
import Checkbox from '/src/components/ui/Checkbox.vue'
const nested=ref(false),basic=ref(false),half=ref(false),disabled=ref(true),selected=ref([1]),number=ref(0),submitted=ref(0)
</script>
<style scoped>main{padding:var(--kb-space-6);max-width:50rem;margin:auto}form{display:flex;flex-direction:column;align-items:flex-start;gap:var(--kb-space-3);margin-top:var(--kb-space-4)}output{display:block;margin-top:var(--kb-space-4);overflow-wrap:anywhere}button{padding:var(--kb-space-2);border:1px solid var(--kb-border);border-radius:var(--kb-radius-sm)}</style>
