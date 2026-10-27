<template>
  <main class="public-chat">
    <header><strong>KnowFlow · 只读对话快照</strong><p>此内容由分享者主动公开；不提供原会话、账号、附件或管理权限。</p></header>
    <p v-if="loading" role="status">正在读取公开分享…</p>
    <section v-else-if="error" role="alert"><p>{{error}}</p><button type="button" @click="load">重新加载</button></section>
    <template v-else-if="snapshot">
      <h1>{{snapshot.title}}</h1><p class="meta">有效期至 {{new Date(snapshot.expiresAt).toLocaleString()}} · 创建后新增内容不会出现在此快照中</p>
      <p v-for="note in snapshot.redactions" :key="note" class="meta">{{note}}</p>
      <article v-for="(message,index) in snapshot.messages" :key="index"><h2>{{message.role==='user'?'用户':'AI'}}</h2><div class="message">{{message.content}}</div></article>
      <p v-if="!snapshot.messages.length">此快照没有可公开的文本。</p>
    </template>
  </main>
</template>
<script setup lang="ts">
import {ref,onMounted,onUnmounted,watch} from 'vue'
import {useRoute} from 'vue-router'
import {readPublicChat,type PublicChatSnapshot} from '@/api/chat'
const route=useRoute(),snapshot=ref<PublicChatSnapshot|null>(null),loading=ref(true),error=ref('');let epoch=0,disposed=false
async function load(){const generation=++epoch;loading.value=true;error.value='';snapshot.value=null;try{const value=await readPublicChat(String(route.params.token||''));if(!disposed&&generation===epoch)snapshot.value=value}catch{if(!disposed&&generation===epoch)error.value='分享不存在、已过期或已撤销。请联系分享者确认。'}finally{if(!disposed&&generation===epoch)loading.value=false}}
const policy=document.createElement('meta');policy.name='referrer';policy.content='no-referrer'
onMounted(()=>{document.head.appendChild(policy);void load()});watch(()=>route.params.token,()=>void load());onUnmounted(()=>{disposed=true;epoch++;policy.remove()})
</script>
<style scoped>
.public-chat{max-width:52rem;margin:auto;padding:var(--kb-space-6);color:var(--kb-foreground);background:var(--kb-card);min-height:100dvh}.public-chat header{border-bottom:1px solid var(--kb-border);padding-bottom:var(--kb-space-4)}.public-chat p,.meta{color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm);margin-block:var(--kb-space-3)}.public-chat h1{font-size:var(--kb-fs-title-lg);margin-block:var(--kb-space-6);overflow-wrap:anywhere}.public-chat h2{font-size:var(--kb-fs-body-md);font-weight:600;margin-bottom:var(--kb-space-2)}.public-chat article{padding-block:var(--kb-space-4);border-bottom:1px solid var(--kb-border)}.message{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8}.public-chat button{color:var(--kb-primary)}
</style>
