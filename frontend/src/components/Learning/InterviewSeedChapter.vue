<template>
 <section v-if="auth.isLoggedIn && status !== 'empty'" class="seed-chapter" aria-label="本章面试训练">
  <StateView :status="status" :error-title="error?.title" :error-hint="error?.hint" error-scope="本章训练资源未加载，正文仍可阅读。" :rows="2" @retry="run(true)">
   <template v-if="data">
    <h3>本章面试训练</h3>
    <p>理解 → 闭卷回忆 → 讲解 → 工程练习。展开答案不会生成成绩或掌握度。</p>
    <div v-if="data.rebuildAvailable && data.revision?.version !== 'java-ai-content-rebuild-v1'" class="upgrade-content">
      <p>官方资料深度课程包：HashMap、ConcurrentHashMap、AQS、Spring 事务与 RAG。启用会新增版本资料和个人回忆卡，旧资料与学习记录保留。</p>
      <button type="button" :disabled="upgrading" @click="rebuild">{{upgrading ? '正在校验与启用…' : '校验并启用五章深度课程包'}}</button>
      <p v-if="upgradeError" role="alert">启用未完成：{{upgradeError}}</p>
    </div>
    <div v-if="data.upgradeAvailable && !data.revision" class="upgrade-content">
      <p>{{ data.upgradeKind==='generics' ? '泛型与 ConcurrentHashMap' : 'HashMap' }} 深度修订包含源码讲解、Java 实验与连续追问。启用后保留原资料、笔记与复习记录。</p>
      <button type="button" :disabled="upgrading" @click="upgrade">{{ upgrading ? '正在启用…' : `启用 ${data.upgradeKind==='generics' ? '泛型与并发集合' : 'HashMap'} 深度训练` }}</button>
      <p v-if="upgradeError" role="alert">升级未完成：{{ upgradeError }}。可再次点击重试。</p>
    </div>
    <div v-if="data.readingUpgradeAvailable" class="upgrade-content">
      <p>结构化阅读 V5 将新增版本文档，保留你的旧正文、笔记和训练记录。</p>
      <button type="button" :disabled="upgrading" @click="upgrade">{{upgrading ? '正在升级…' : '启用结构化阅读 V5'}}</button>
      <p v-if="upgradeError" role="alert">升级未完成：{{upgradeError}}</p>
    </div>
    <p v-if="data.revision">内容修订 {{ data.revision.version }} · {{ data.revision.flashcardCount }} 张修订回忆卡（旧卡保留）</p>
    <router-link v-if="data.revision?.previousReadingDocId && data.revision.previousReadingDocId !== data.revision.docId" :to="`/doc/${data.revision.previousReadingDocId}`">查看保留的旧版深度正文</router-link>
    <nav class="seed-actions" aria-label="本章学习资源">
     <router-link :to="resolveChapterResourceRoute('document',data.revision?.docId || data.docId,chapterId)">核心文档</router-link>
     <router-link :to="flashcardRoute">本章闪卡</router-link>
     <a :href="`#chapter-questions-${chapterId}`">本章追问</a>
     <a v-if="data.revision?.exercises.length || data.exercise" :href="`#chapter-exercises-${chapterId}`">本章代码实验</a>
    </nav>
    <template v-if="data.revision">
      <h4 :id="`chapter-questions-${chapterId}`">从原理走到追问</h4>
      <p>先独立回答，再展开参考。自动反馈仅沿用现有简答匹配，不代表面试评分。</p>
      <InterviewQuestionTree :questions="data.revision.questions" :parent-key="null" />
      <h4 :id="`chapter-exercises-${chapterId}`">Java 17 代码实验</h4>
      <p>可在练习编辑器查看/复制并在可信本地运行；网页尚不提供 Java 自动判题。</p>
      <details v-for="exercise in data.revision.exercises" :key="exercise.id"><summary>{{exercise.title}}</summary><div v-html="renderMarkdown(exercise.description+'\n\n```java\n'+exercise.codeTemplate+'\n```')"></div><router-link :to="resolveChapterResourceRoute('code',exercise.id,chapterId)">查看实验与练习编辑器</router-link><details><summary>检查要点</summary><div v-html="renderMarkdown(exercise.solutionHint)"></div></details></details>
      <h4>本章面试速记</h4>
      <details v-for="note in data.revision.notes" :key="note.id"><summary>{{note.title}}</summary><div v-html="renderMarkdown(note.content)"></div></details>
      <details><summary>保留的 v1 训练资料</summary>
        <router-link :to="`/doc/${data.docId}`">原版文档</router-link>
        <details v-for="q in data.questions" :key="q.id"><summary>{{q.title}}</summary><div v-html="renderMarkdown(q.answer+'\n\n'+q.explanation)"></div></details>
      </details>
    </template>
    <template v-else>
    <h4 :id="`chapter-questions-${chapterId}`">60–120秒模拟面试</h4>
    <InterviewQuestionTree :questions="practiceQuestions" :parent-key="null" />
    <details v-if="data.exercise" :id="`chapter-exercises-${chapterId}`"><summary>工程练习 · {{data.exercise.title}}</summary><div v-html="renderMarkdown(data.exercise.description)"></div><router-link :to="resolveChapterResourceRoute('code',data.exercise.id,chapterId)">打开练习编辑器</router-link><details><summary>检查要点（先独立完成）</summary><div v-html="renderMarkdown(data.exercise.solutionHint)"></div></details></details>
    <h4 v-if="data.notes.length">这条路径的考前速记</h4>
    <details v-for="note in data.notes" :key="note.id"><summary>{{note.title}}</summary><div v-html="renderMarkdown(note.content)"></div></details>
    </template>
   </template>
  </StateView>
 </section>
</template>
<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import { interviewSeedApi,type CollectionsRevision,type InterviewQuestion } from '@/api/interviewSeed'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { renderMarkdown } from '@/utils/markdown'
import { stampActionContext,resolveContextualRoute,resolveChapterResourceRoute } from '@/learning/action-contract'
import StateView from '@/components/ui/StateView.vue'
import { useAuthStore } from '@/stores/auth'
import InterviewQuestionTree from './InterviewQuestionTree.vue'
import { getApiError } from '@/utils/toast'
const props=defineProps<{chapterId:number}>()
const auth=useAuthStore()
const emit=defineEmits<{revision:[value:CollectionsRevision|null];reading:[value:string|null];resources:[value:{questions:number;exercises:number}|null];questions:[value:InterviewQuestion[]]}>()
const upgrading=ref(false),upgradeError=ref('')
const {status,data,error,run}=useAsyncResource(()=>interviewSeedApi.chapter(props.chapterId),v=>v===null,'本章面试训练','section')
watch(()=>[props.chapterId,auth.user?.id],()=>{data.value=null;upgradeError.value='';emit('revision',null);if(auth.isLoggedIn)void run()},{immediate:true})
watch(()=>data.value?.revision,value=>emit('revision',value??null))
watch(()=>data.value?.readingContent,value=>emit('reading',value??null))
watch(data,value=>emit('resources',value ? {questions:value.revision?.questions.length ?? value.questions.length,exercises:value.revision?.exercises.length ?? (value.exercise?1:0)} : null))
async function upgrade(){if(upgrading.value)return;upgrading.value=true;upgradeError.value='';const id=props.chapterId;try{if(data.value?.upgradeKind==='generics')await interviewSeedApi.upgradeGenerics();else await interviewSeedApi.upgradeCollections();if(id===props.chapterId)await run(true)}catch(e){if(id===props.chapterId)upgradeError.value=getApiError(e)}finally{upgrading.value=false}}
async function rebuild(){if(upgrading.value)return;upgrading.value=true;upgradeError.value='';const id=props.chapterId;try{await interviewSeedApi.rebuildDryRun();await interviewSeedApi.rebuildImport();if(id===props.chapterId)await run(true)}catch(e){if(id===props.chapterId)upgradeError.value=getApiError(e)}finally{upgrading.value=false}}
const flashcardRoute=computed(()=>resolveContextualRoute(stampActionContext({type:'FLASHCARD',title:'本章闪卡',reason:'用户选择主动回忆',route:''},{learningNodeId:props.chapterId})))
const practiceQuestions=computed<InterviewQuestion[]>(()=>data.value?.revision?.questions ?? data.value?.questions.map(q=>({...q,canonicalKey:`question/${q.id}`,parentKey:null})) ?? [])
watch(practiceQuestions,value=>emit('questions',value),{immediate:true})
</script>
<style scoped>
.seed-chapter{margin-top:var(--kf-space-6);padding-top:var(--kf-space-4);border-top:var(--kf-border-width,1px) solid var(--kf-border)}
.seed-chapter p{color:var(--kf-muted-foreground)}
.seed-actions{display:flex;flex-wrap:wrap;gap:var(--kf-space-4);margin-block:var(--kf-space-4)}
.seed-chapter a{color:var(--kf-primary)}
.seed-chapter details{margin-block:var(--kf-space-3);padding:var(--kf-space-3);border-radius:var(--kf-radius-sm);background:var(--kf-background)}
.seed-chapter summary{cursor:pointer;color:var(--kf-foreground)}
.upgrade-content{padding:var(--kf-space-4);border:1px solid var(--kf-border);border-radius:var(--kf-radius-md);margin-block:var(--kf-space-4)}
.upgrade-content button{background:var(--kf-primary);color:var(--kf-primary-foreground);padding:var(--kf-space-2) var(--kf-space-4);border-radius:var(--kf-radius-sm)}
</style>
