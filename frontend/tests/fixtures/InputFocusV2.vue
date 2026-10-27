<template>
 <main class="focus-fixture">
  <h1>Focus V2 · 真实共享组件验收</h1><p>仅验证组件视觉和事件，不连接AI或保存业务数据。</p>
  <button id="focus-start">焦点起点</button>
  <section id="normal-composer"><h2>聊天输入</h2><AIComposer v-model="text" @submit="sent++;last=text;text=''"/><p id="sent-count">发送次数 {{ sent }}：{{ last }}</p></section>
  <section id="error-composer"><h2>错误</h2><AIComposer v-model="errorText" error="受控表单错误"/></section>
  <section id="disabled-composer"><h2>禁用</h2><AIComposer model-value="不可修改" disabled/></section>
  <input aria-label="原生文本" placeholder="原生文本"/><div class="fixture-fields"><Input v-model="name" label="普通文本"/><Input v-model="number" type="number" label="数字"/><Input model-value="错误值" error="请检查输入" label="错误字段"/><Input model-value="禁用值" disabled label="禁用字段"/><Textarea v-model="description" label="共享多行"/><Select v-model="selected" :options="[{value:1,label:'示例选项一'},{value:2,label:'示例选项二'}]" aria-label="共享选择" searchable/></div>
  <div class="kb-input-surface fixture-search"><Icon name="search" :size="16"/><input class="kb-input" aria-label="组合搜索" placeholder="搜索"/></div>
 </main>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import AIComposer from '@/components/ai/AIComposer.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
const text=ref(''),errorText=ref('错误内容'),name=ref(''),number=ref<string|number>(0),description=ref(''),selected=ref<number|null>(null),sent=ref(0),last=ref('')
</script>
<style scoped>
.focus-fixture{max-width:var(--kb-notes-reading-width);margin:auto;padding:var(--kb-space-5);display:grid;gap:var(--kb-space-4);color:var(--kb-foreground);background:var(--kb-background)}
h1{font-size:var(--kb-fs-h3)}h2{font-size:var(--kb-fs-body-md);margin-block:var(--kb-space-2)}
#focus-start{padding:var(--kb-space-2);border:1px solid var(--kb-border);border-radius:var(--kb-radius-md)}
.fixture-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kb-space-3)}
.fixture-search{display:flex;gap:var(--kb-space-2);align-items:center;background:var(--kb-field-surface);border:1px solid var(--kb-border);border-radius:var(--kb-field-radius);padding-inline:var(--kb-space-3)}
@media(max-width:639px){.fixture-fields{grid-template-columns:minmax(0,1fr)}}
</style>
