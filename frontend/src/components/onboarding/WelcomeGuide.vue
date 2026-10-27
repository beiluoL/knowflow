<template>
  <Teleport to="body">
    <div v-if="guide.open" class="welcome-overlay">
      <section ref="dialog" role="dialog" aria-modal="true" aria-labelledby="welcome-title" aria-describedby="welcome-description" tabindex="-1" class="welcome-dialog" @keydown="onKeydown">
        <div class="welcome-top"><span class="kb-caption">快速上手 · {{ step + 1 }} / {{ steps.length }}</span><button type="button" class="welcome-text-btn" @click="guide.dismiss()">跳过引导</button></div>
        <Icon :name="current.icon" size="2xl" aria-hidden="true" />
        <h2 id="welcome-title" class="kb-h2">{{ current.title }}</h2>
        <p id="welcome-description" class="kb-body">{{ current.description }}</p>
        <ol class="welcome-progress" aria-label="引导进度"><li v-for="(item,index) in steps" :key="item.title" :aria-current="index === step ? 'step' : undefined" :class="{ active:index===step }"><span>{{ index+1 }}</span>{{ item.label }}</li></ol>
        <p class="welcome-hint">{{ current.hint }}</p>
        <footer><button v-if="step>0" type="button" class="welcome-text-btn" @click="step--">上一步</button><span v-else /><Button @click="advance">{{ step === steps.length-1 ? '开始收集' : '下一步' }}</Button></footer>
      </section>
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useOnboardingStore } from '@/stores/onboarding'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
const emit=defineEmits<{ start:[] }>()
const guide=useOnboardingStore(), step=ref(0), dialog=ref<HTMLElement|null>(null)
const steps=[
  {label:'首页',icon:'home',title:'欢迎来到 KnowFlow',description:'把零散知识变成真正学会的能力。首页会汇总你的学习进度，并提供统一的下一步行动。',hint:'不必一次学会所有功能，先从一条灵感开始。'},
  {label:'知识',icon:'book-open',title:'知识库是你的资料底座',description:'在「知识」中浏览资料、搜索文档和管理知识库。收集与整理后的内容可以继续用于复习。',hint:'顶部搜索随时帮你找到知识、笔记与闪卡。'},
  {label:'工作台',icon:'layers',title:'沿着四步工作台沉淀知识',description:'收集箱 → 整理笔记 → 复习 → 输出。先捕获灵感，再整理为自己的理解，最后通过复习与表达巩固。',hint:'整理笔记使用康奈尔结构，输出用故事解释给外行听。'},
  {label:'学习',icon:'graduation-cap',title:'复习不是反复阅读',description:'在「学习」中使用学习路径、闪卡和练习。根据真实反馈调整学习节奏，不需要自己猜下一步。',hint:'没有学习材料时，先收集或创建内容；空页面也会告诉你如何开始。'},
  {label:'应用',icon:'grid',title:'按需添加你的小应用',description:'到「应用」探索适合自己的功能。已启用的学习工具会出现在对应导航中，不必一次安装全部工具。',hint:'随时从头像菜单选择「新手引导」重新查看。'},
] as const
const current=computed(()=>steps[step.value]!)
let previous:HTMLElement|null=null, previousOverflow='', locked=false
function restore(){if(locked){document.body.style.overflow=previousOverflow;locked=false};if(previous?.isConnected)previous.focus();else document.querySelector<HTMLElement>('[data-test="onboarding-trigger"]')?.focus()}
watch(()=>[guide.open,guide.userId] as const,async([open])=>{if(!open){restore();return};step.value=0;if(!locked){previous=document.activeElement instanceof HTMLElement && document.activeElement !== document.body ?document.activeElement:null;previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';locked=true};await nextTick();if(guide.open)dialog.value?.focus()},{flush:'post',immediate:true})
function advance(){if(step.value<steps.length-1){step.value++;dialog.value?.focus()}else{guide.dismiss();emit('start')}}
function onKeydown(event:KeyboardEvent){if(event.key==='Escape'){event.preventDefault();guide.dismiss();return};if(event.key!=='Tab')return;const buttons=dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');if(!buttons?.length)return;const first=buttons[0]!,last=buttons[buttons.length-1]!;if(event.shiftKey&&(document.activeElement===first||document.activeElement===dialog.value)){event.preventDefault();last.focus()}else if(!event.shiftKey&&(document.activeElement===last||document.activeElement===dialog.value)){event.preventDefault();first.focus()}}
onUnmounted(restore)
</script>
<style scoped>
.welcome-overlay{position:fixed;inset:0;z-index:var(--kb-z-modal,1000);display:grid;place-items:center;padding:var(--kb-space-4);background:color-mix(in srgb,var(--kb-foreground) 45%,transparent)}
.welcome-dialog{width:min(100%,calc(var(--kb-content-max-width) / 2));max-height:90dvh;overflow:auto;background:var(--kb-card);color:var(--kb-foreground);border:1px solid var(--kb-border);border-radius:var(--kb-radius-lg);padding:var(--kb-space-6);box-shadow:var(--shadow-dropdown-3);outline:none;display:grid;gap:var(--kb-space-4)}
.welcome-top,.welcome-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:var(--kb-space-4)}
.welcome-dialog :deep(svg){color:var(--kb-primary)}.welcome-text-btn{color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm);padding:var(--kb-space-2);border-radius:var(--kb-radius-sm)}
.welcome-text-btn:focus-visible{outline:2px solid var(--kb-primary)}.welcome-progress{display:flex;flex-wrap:wrap;gap:var(--kb-space-3);list-style:none;padding:0}.welcome-progress li{display:flex;gap:var(--kb-space-1);align-items:center;font-size:var(--kb-fs-caption);color:var(--kb-muted-foreground)}.welcome-progress li.active{color:var(--kb-primary);font-weight:600}.welcome-hint{background:var(--kb-primary-soft);padding:var(--kb-space-3);border-radius:var(--kb-radius-md);font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground)}
@media(prefers-reduced-motion:no-preference){.welcome-dialog{animation:welcome-in 180ms ease-out}@keyframes welcome-in{from{opacity:0;transform:translateY(var(--kb-space-2))}to{opacity:1;transform:translateY(0)}}}
</style>
