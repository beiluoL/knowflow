<template>
  <section class="learning-review" aria-label="作答与复习对照" data-learning-review>
    <h3>作答与复习对照</h3>
    <p v-if="loading" role="status">正在读取已保存的作答与复习记录…</p>
    <div v-else-if="error" role="alert"><p>{{ error }}</p><Button variant="secondary" @click="load">重试读取学习记录</Button></div>
    <template v-else-if="report">
      <p v-if="!report.attempts.length">尚无本题作答记录。提交后可回读结果并关联复习。</p>
      <template v-else>
        <p v-if="!report.practiceAvailable" role="status">来源已撤权、撤下或未通过审核。私人历史保留，当前不能开始新练习。</p>
        <ol class="attempt-list"><li v-for="attempt in report.attempts" :key="attempt.answerRecordId">
          <strong>作答 #{{ attempt.answerRecordId }} · {{ attempt.correct ? '正确' : '错误' }}</strong>
          <p>{{ attempt.snapshot?.content || attempt.snapshot?.title || '旧记录没有原题快照，不能还原当时题目条件。' }}</p>
          <ol v-if="attempt.snapshot?.options"><li v-for="(option,index) in options(attempt.snapshot.options)" :key="index">{{ index }}：{{ option }}</li></ol>
          <p v-if="attempt.snapshot">当时参考答案：{{ attempt.snapshot.answer }} · {{ attempt.snapshot.explanation || '当时没有解析' }}</p>
          <p>原始答案：{{ attempt.userAnswer }}<span v-if="attempt.snapshot?.version != null"> · 题目版本 {{ attempt.snapshot.version }}</span></p>
        </li></ol>
        <p data-comparability>{{ report.comparison.note }}</p>
        <p v-if="report.comparison.paired" data-comparison-outcome>首次：{{ report.comparison.firstCorrect ? '正确' : '错误' }} → 第二次：{{ report.comparison.secondCorrect ? '正确' : '错误' }}</p>
        <h4>已保存的相关复习</h4>
        <p v-if="!report.reviews.length">尚无可确认发生在两次作答之间的关联 FSRS 复习。</p>
        <ul v-else><li v-for="review in report.reviews" :key="review.eventId">卡片 #{{ review.cardId }} · 评分 {{ review.rating }} · 事件 #{{ review.eventId }} · 下次到期 {{ review.dueAt }}</li></ul>
        <h4>已确认关联的本人闪卡</h4>
        <p v-if="!report.cards.length">暂无已确认关联的本人闪卡。内容确认与知识映射审核分别处理。</p>
        <ul v-else><li v-for="card in report.cards" :key="card.cardId"><router-link :to="cardRoute(card.cardId,card.knowledgeId)" data-related-card>{{ card.front }} · 进入闪卡复习</router-link></li></ul>
        <details v-if="report.practiceAvailable && report.baselineAnswerRecordId && !report.cards.length">
          <summary>从真实错题准备本人卡片</summary>
          <label>正面<input v-model="front" class="form-input kf-input kf-form-control" aria-label="错题闪卡正面" :disabled="saving" /></label>
          <label>背面<textarea v-model="back" class="form-input kf-textarea kf-form-control" aria-label="错题闪卡背面" :disabled="saving"></textarea></label>
          <p>保存内容后仅创建 PENDING 知识映射候选，不自动成为已确认关联卡。</p>
          <Button :disabled="saving || saved || !front.trim() || !back.trim()" @click="saveCard">{{ saving ? '保存中…' : '确认保存本人卡片' }}</Button>
          <p v-if="saveError" role="alert">{{ saveError }}。输入已保留，可重试。</p><p v-if="saved" role="status">本人卡片已保存，知识映射状态：{{ savedMappingStatus }}。</p><router-link v-if="savedCardId" :to="cardRoute(savedCardId)" data-saved-private-card>进入本人卡片（未确认知识映射不计关联证据）</router-link>
        </details>
        <div class="review-actions">
          <Button v-if="report.practiceAvailable && report.baselineAnswerRecordId" :disabled="busy" @click="emit('retest', report.baselineAnswerRecordId)">再次验证本题</Button>
          <Button variant="secondary" @click="load">刷新复习记录</Button>
          <router-link v-if="report.chapterId" :to="chapterRoute(report.chapterId)">返回专题章节</router-link>
        </div>
        <p>掌握程度与下一步建议仍由学习中心依据原有证据规则计算；样本不足时不足以判断掌握程度。</p>
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { quizApi } from '@/api'
import type { QuizLearningReview } from '@/api/quiz'
import Button from '@/components/ui/Button.vue'
import { confirmDialog } from '@/utils/toast'
import { describePageError } from '@/utils/error-message'
import { resolvePlanResourceSelector, resolveContextualRoute, stampActionContext } from '@/learning/action-contract'
const props = defineProps<{ questionId:number; refresh:number; busy?:boolean }>()
const emit = defineEmits<{ retest:[answerId:number] }>()
const loading=ref(false), error=ref(''), report=ref<QuizLearningReview|null>(null)
const front=ref(''), back=ref(''), saving=ref(false), saveError=ref(''), saved=ref(false), savedCardId=ref<number|null>(null), savedMappingStatus=ref('')
let generation=0
async function load(){const run=++generation;loading.value=true;error.value='';try{const value=await quizApi.learningReview(props.questionId);if(run!==generation)return;report.value=value;const original=value.attempts.find(a=>a.answerRecordId===value.baselineAnswerRecordId)?.snapshot;if(!front.value && original){front.value=original.content ?? original.title ?? '';back.value=original.explanation ?? original.answer ?? ''}}catch(e:unknown){if(run!==generation)return;const desc=describePageError(e,{subject:'学习记录读取失败'});error.value=desc.title+'：'+desc.hint}finally{if(run===generation)loading.value=false}}
let lastQuestion=0
watch(()=>[props.questionId,props.refresh],()=>{if(lastQuestion!==props.questionId){report.value=null;front.value='';back.value='';saved.value=false;savedCardId.value=null;saveError.value='';lastQuestion=props.questionId}void load()},{immediate:true})
onUnmounted(()=>{generation++})
function options(raw:string):string[]{try{const parsed:unknown=JSON.parse(raw);return Array.isArray(parsed)?parsed.map(item=>{if(item && typeof item==='object' && !Array.isArray(item)){const value='content' in item?item.content:item.text;return value==null?JSON.stringify(item):String(value)}return String(item)}):['选项快照格式不可读']}catch{return ['选项快照格式不可读']}}
const cardRoute=(id:number,kid?:number)=>resolvePlanResourceSelector('FLASHCARD',id,{type:'FLASHCARD',title:'关联闪卡',reason:'本人卡与题目共享已确认知识映射',route:'/learning/flashcards'},{knowledgeId:kid})
const chapterRoute=(id:number)=>resolveContextualRoute(stampActionContext({type:'EVIDENCE_PRACTICE',title:'返回专题',reason:'回到实际来源章节',route:`/learning/chapter/${id}`},{learningNodeId:id}))
async function saveCard(){const id=report.value?.baselineAnswerRecordId;if(!id||saving.value||!await confirmDialog('确认保存这张本人卡片？知识映射仍需单独审核。', {"title":"保存复习卡？","confirmText":"保存卡片","cancelText":"返回编辑","icon":"check-circle","variant":"default"}))return;saving.value=true;saveError.value='';try{const result=await quizApi.createReviewCard(id,front.value,back.value);savedCardId.value=result.cardId;savedMappingStatus.value=result.mappingStatus;saved.value=true;await load()}catch(e:unknown){const desc=describePageError(e,{subject:'卡片保存失败'});saveError.value=desc.title+'：'+desc.hint}finally{saving.value=false}}
</script>
<style scoped>
.learning-review{background:var(--kf-card);border-radius:var(--kf-radius-md);padding:var(--kf-space-4);margin-block:var(--kf-space-4);color:var(--kf-foreground);overflow-wrap:anywhere}
.learning-review h3,.learning-review h4{font-weight:var(--kf-fw-semibold);margin-block:var(--kf-space-3)}
.learning-review p,.attempt-list li{margin-block:var(--kf-space-2)}
.review-actions{display:flex;gap:var(--kf-space-3);align-items:center;flex-wrap:wrap;margin-block:var(--kf-space-3)}
.learning-review a{color:var(--kf-primary)}
.learning-review label{display:block;margin-block:var(--kf-space-3)}
</style>
