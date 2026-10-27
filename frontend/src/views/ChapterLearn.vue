<template>
  <div class="chapter-learn">
    <!-- 主体：左侧正文 + 右侧大纲 -->
    <div class="chapter-columns">
      <!-- 左侧课程内容 -->
      <div class="flex-1 min-w-0">
        <!-- 面包屑 -->
        <nav class="flex items-center gap-2 text-sm px-6 py-3 breadcrumb-bar">
          <router-link to="/learning/center" class="font-medium breadcrumb-link">学习首页</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <router-link to="/learning/paths" class="font-medium breadcrumb-link">学习路径</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <router-link
            v-if="pathDetail"
            :to="`/learning/path/${pathDetail.id}`"
            class="font-medium breadcrumb-link"
          >{{ pathDetail.title }}</router-link>
          <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
          <span class="font-medium" style="color: var(--kb-foreground);">{{ currentChapter?.title || '章节学习' }}</span>
        </nav>

        <ChapterProgress v-if="pathChapters.length" :chapters="pathChapters" :current-id="currentChapterId" :progress="pathProgress" @select="selectChapter" />

        <!-- 课程内容正文 -->
        <div class="content-body" ref="readingRoot" @click="handleReadingClick">
          <!-- 加载态 -->
          <div v-if="loading" class="space-y-4">
            <div class="h-8 rounded animate-pulse" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-3/4" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-full" style="background: var(--kb-muted);"></div>
            <div class="h-4 rounded animate-pulse w-5/6" style="background: var(--kb-muted);"></div>
          </div>
          <!-- 正文 -->
          <StateView v-else-if="chapterError" status="error" error-title="章节加载失败" :error-description="chapterError" error-scope="正文暂不可用，不会显示上一章内容。" @retry="loadChapter(currentChapterId)" />
          <template v-else-if="currentChapter">
            <header class="reading-header">
              <span class="chapter-number">第 {{ currentChapterIndex+1 }} 章</span>
              <h1>{{ currentChapter.title }}</h1>
              <p v-if="reading.summary || currentChapter.description" class="chapter-summary">{{ reading.summary || currentChapter.description }}</p>
              <div class="chapter-meta">
                <span><Icon name="clock" :size="14" />{{ currentChapter.duration ? `预计 ${currentChapter.duration} 分钟` : '时长未提供' }}</span>
                <span><Icon name="bar-chart-3" :size="14" />{{ pathDetail?.level || '难度未提供' }}</span>
                <span v-if="reading.importance"><Icon name="star" :size="14" />面试重要度 {{ reading.importance }}</span>
                <span :class="{completed:currentChapter.completed}"><Icon :name="currentChapter.completed?'check-circle':'circle'" :size="14" />{{ currentChapter.completed ? '本章已完成' : currentChapter.locked ? '前置未完成' : '尚未完成' }}</span>
              </div>
              <p v-if="navigationError" class="resource-error">路径导航加载失败：{{ navigationError }} <button type="button" @click="loadChapter(currentChapterId)">重试</button></p>
            </header>
            <section class="reading-bookmark" aria-label="阅读位置保存">
              <p>阅读位置仅用于继续阅读，不代表知识掌握。</p>

              <Button v-if="currentChapter.readingAnchor && !readingPositionStale" variant="secondary" size="sm" data-reading-resume type="button" @click="resumeReading">恢复已保存位置</Button>
              <p v-if="readingPositionStale">正文已更新，请重新选择并保存阅读位置。</p>
              <p v-if="readingSaveSuccess" role="status">阅读位置已保存，可关闭页面后继续。</p>
              <p v-else-if="readingDirty" role="status">当前位置尚未保存。</p>
              <p v-if="readingSaveError" role="alert">{{ readingSaveError }} <button type="button" @click="saveReadingPosition">重试保存</button> <button type="button" @click="loadChapter(currentChapterId)">重新加载位置</button></p>
            </section>
            <LearningObjectives :items="reading.objectives" />
            <!-- 提取专业英语：基于当前章节正文或选区生成候选，人工确认后才入库 -->
            <div class="chapter-extract-row">
              <Button variant="secondary" size="sm" type="button" @click="openExtract">提取专业英语</Button>
              <span class="chapter-extract-hint">从本章提取技术词汇候选，需人工确认后进入个人待审列表</span>
            </div>
            <article class="chapter-article" v-html="renderedContent"></article>
            <details v-if="revision || curatedReading" class="original-reading"><summary>查看保留的原版章节正文</summary><div v-html="originalReading"></div></details>
            <section class="reading-bookmark" aria-label="章节关联练习">
              <h2>关联练习（已审核）</h2>
              <p v-if="knowledgeLoading" role="status">正在加载关联练习…</p>
              <p v-else-if="knowledgeError" role="alert">关联练习加载失败 <button type="button" @click="loadRelatedKnowledge(currentChapterId)">重试</button></p>
              <div v-else-if="mappedPractice.length"><Button variant="secondary" @click="router.push(chapterPracticeRoute(mappedPractice[0]!.id))" data-chapter-specialty>开始本章专项练习</Button><ul><li v-for="question in mappedPractice" :key="question.id"><router-link :to="chapterPracticeRoute(question.id)" data-chapter-practice>{{ question.title }}</router-link></li></ul></div>
              <p v-else>暂无可用的已审核关联练习；专题题库待审核或尚未确认关联，不会按标题自动猜题。</p>
            </section>
            <InterviewSeedChapter id="chapter-training" :key="currentChapter.id" :chapter-id="currentChapter.id" @revision="onRevision" @reading="curatedReading = $event" @resources="trainingResources = $event" @questions="chapterQuestions = $event" />
          </template>
          <!-- 空态 -->
          <p v-else class="text-center py-12" style="color: var(--kb-muted-foreground);">本章暂无内容</p>
          <!-- 划词学习：章节教材正文选中英文单词/缩写即可查询已发布术语 -->
          <SelectionTermLookup
            :target="readingRoot"
            :enabled="!loading && !chapterError && !!currentChapter"
            :context-domain="chapterDomain"
            @open-extract="openExtract"
          />
          <TermExtractDialog
            :open="extractOpen"
            source-type="TEXT"
            :source-title="currentChapter?.title || ''"
            :article-text="readingSource"
            :selection-text="extractSelection"
            @close="extractOpen = false"
          />
        </div>
      </div>

      <!-- 右侧大纲 -->
      <aside class="outline-sidebar" v-if="currentChapter">
        <div class="outline-sticky">
          <button class="outline-toggle" type="button" :aria-expanded="outlineExpanded" aria-controls="chapter-outline-content" @click="outlineExpanded = !outlineExpanded">{{ outlineExpanded ? '收起章节目录' : '展开章节目录' }}</button>
          <div id="chapter-outline-content" class="outline-content" :class="{ 'is-collapsed': !outlineExpanded }">
          <!-- 本章大纲 -->
          <h4 class="outline-title">本章目录</h4>
          <ChapterTOC :items="outlineItems" :active-index="activeOutlineIndex" @select="scrollToHeading" />

          <ChapterStudyTools :flashcard-route="flashcardRoute" :card-count="cardCount" :card-error="cardError" :question-count="questionCount" :exercise-count="exerciseCount" @note="openChapterNote" @questions="scrollToTrainingSection('questions')" @exercises="scrollToTrainingSection('exercises')" @retry="loadCardCount(currentChapterId)" />

          <!-- A-FB-01 相关概念 -->
          <div v-if="relatedConcepts.length > 0" class="mt-6 pt-4 related-section">
            <h4 class="outline-title">关联知识点</h4>
            <div class="flex flex-wrap gap-2 px-2">
              <router-link
                v-for="concept in (conceptsExpanded ? relatedConcepts : relatedConcepts.slice(0,6))"
                :key="concept.id"
                :to="resolveChapterResourceRoute('knowledge',concept.id,currentChapterId)"
                :title="`查看 ${concept.name} 的关联资料`"
                class="related-concept-chip"
              >{{ concept.name }}</router-link>
            </div>
            <button v-if="relatedConcepts.length>6" class="related-expand" @click="conceptsExpanded=!conceptsExpanded">{{ conceptsExpanded ? '收起关联' : `展开其余 ${relatedConcepts.length-6} 项` }}</button>
          </div>
          <p v-else-if="!knowledgeError" class="resource-caption">本章暂无关联知识；不会按标题猜测生成。</p>
          <p v-if="knowledgeError" class="resource-error">知识关联加载失败 <button @click="loadRelatedKnowledge(currentChapterId)">重试</button></p>
          <p v-if="docsError" class="resource-error">关联文档加载失败 <button @click="loadRelatedDocs(currentChapter)">重试</button></p>

          <!-- A-FB-01 相关文档 -->
          <div v-if="relatedDocs.length > 0" class="mt-6 pt-4 related-section">
            <h4 class="outline-title">相关文档</h4>
            <div class="space-y-1 px-1">
              <router-link
                v-for="doc in relatedDocs.slice(0,2)"
                :key="doc.id"
                :to="`/doc/${doc.id}`"
                class="related-doc-item"
              >
                <span class="related-doc-title">{{ doc.title }}</span>
                <span v-if="doc.summary" class="related-doc-summary">{{ doc.summary }}</span>
              </router-link>
            </div>
          </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 底部固定操作栏 -->
    <div v-if="currentChapter && !loading" class="bottom-action-bar" aria-label="章节学习操作">
      <!-- 上一章 -->
      <button
        type="button"
        class="flex items-center gap-2 text-sm font-medium px-4 py-2 rounded-lg transition-colors bottom-prev"
        :disabled="!hasPrevChapter"
        @click="goToPrevChapter"
      >
        <Icon name="arrow-left" :size="16" />
        <span>上一章</span>
      </button>

      <!-- 中间操作 -->
      <div class="flex items-center gap-3">
        <Button variant="secondary" size="sm" data-reading-save type="button" :disabled="readingSaving || currentChapter.locked" @click="saveReadingPosition">{{ readingSaving ? '保存中…' : '保存当前阅读位置' }}</Button>
        <button
          type="button"
          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bottom-icon-btn"
          @click="toggleFavorite"
          :disabled="favoriteBusy || !!favoriteError"
        >
          <Icon :name="isFavorite ? 'bookmark-check' : 'bookmark'" :size="16" />
          {{ isFavorite ? '已收藏' : '收藏' }}
        </button>
        <button
          type="button"
          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bottom-icon-btn"
          @click="showNote = !showNote"
        >
          <Icon name="notebook-pen" :size="16" />
          写本章笔记
        </button>
      </div>

      <!-- 下一章：完成操作与其并排，位于右侧 -->
      <div class="flex items-center gap-2">
        <!-- 完成当前章节：未完成未锁定→可点击；已完成→状态徽标；已锁定→禁用 -->
        <template v-if="currentChapter">
          <button
            v-if="!currentChapter.completed && !currentChapter.locked"
            type="button"
            class="complete-btn-inline"
            :disabled="completing || readingSaving"
            @click="completeCurrentChapter"
          >
            <Icon name="check" :size="16" />
            <span>{{ completing ? '提交中...' : '标记本章已完成' }}</span>
          </button>
          <div v-else-if="currentChapter.completed" class="complete-status-inline">
            <Icon name="check-circle" :size="16" style="color: var(--kb-accent);" />
            <span>本章已完成</span>
          </div>
          <div v-else class="complete-status-inline locked">
            <Icon name="lock" :size="14" />
            <span>完成前置章节后解锁</span>
          </div>
        </template>

        <button
          type="button"
          class="flex items-center gap-2 text-sm font-medium px-4 py-2 rounded-lg bottom-next"
          :disabled="!hasNextChapter"
          @click="goToNextChapter"
        >
          <span>继续下一章</span>
          <Icon name="arrow-right" :size="16" />
        </button>
      </div>
    </div>
    <p v-if="favoriteError" class="resource-error">收藏状态加载失败 <button @click="loadFavorite(currentChapterId)">重试</button></p>
    <section v-if="showNote && currentChapter" class="chapter-note" aria-label="本章笔记">
      <h2>记录你仍讲不清的地方</h2>
      <p>保存为本章的新笔记，不覆盖已有速记。</p>
      <label for="chapter-note-text">笔记内容</label>
      <textarea id="chapter-note-text" v-model="noteText" class="kb-textarea kb-form-control" rows="5" :disabled="noteSaving" />
      <button class="note-save" type="button" :disabled="noteSaving || !noteText.trim()" @click="saveNote">{{ noteSaving ? '保存中…' : '保存笔记' }}</button>
      <p v-if="noteError" role="alert">{{ noteError }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 章节学习页（沉浸式双栏阅读）
 * 设计稿对齐：左侧面包屑 + 进度点 + Markdown 正文；右侧固定大纲 + 章节信息；底部固定操作栏（上一章/收藏/笔记/下一章）。
 */
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import Button from '@/components/ui/Button.vue';
import InterviewSeedChapter from '@/components/Learning/InterviewSeedChapter.vue';
import ChapterProgress from '@/components/Learning/ChapterProgress.vue';
import ChapterTOC from '@/components/Learning/ChapterTOC.vue';
import LearningObjectives from '@/components/Learning/LearningObjectives.vue';
import ChapterStudyTools from '@/components/Learning/ChapterStudyTools.vue';
import SelectionTermLookup from '@/components/tech-english/SelectionTermLookup.vue';
import TermExtractDialog from '@/components/tech-english/TermExtractDialog.vue';
import { inferTermDomain } from '@/components/tech-english/term-labels';
import { useAuthStore } from '@/stores/auth';
import '@/styles/chapter-reading-v3.css';
import StateView from '@/components/ui/StateView.vue';
import { chapterReading } from '@/utils/chapterReading';
import { displayPathProgress } from './learning-path-display';
import { stampActionContext, resolveContextualRoute, resolveChapterResourceRoute, resolvePlanResourceSelector } from '@/learning/action-contract';
import { handleCodeCopyClick as handleCodeCopy } from '@/utils/codeCopy';
import type { CollectionsRevision, InterviewQuestion } from '@/api/interviewSeed';
import { bindChapterQuizReferences } from '@/utils/chapterQuizReferences';
import { learningApi, docsApi } from '@/api';
import { notify, getApiError, confirmDialog } from '@/utils/toast';
import { celebrateCertificate } from '@/utils/celebrate';
import type { LearningChapterVO, LearningPathVO, DocVO, LearningCertificateVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

const extractOpen = ref(false);
const extractSelection = ref('');
/** 只捕获章节正文内的选区，避免把页面其他区域的文字当成提取来源 */
function openExtract() {
  // 登录态只在点击提取时读取，章节阅读本身不依赖 Pinia，避免给纯阅读路径增加全局状态依赖
  const auth = useAuthStore();
  if (!auth.isLoggedIn) {
    notify('登录后才能提取专业词汇并保存候选', 'info');
    void router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  const selection = window.getSelection();
  const hasRange = !!selection && selection.rangeCount > 0;
  const inside = hasRange && readingRoot.value
    ? readingRoot.value.contains(selection!.getRangeAt(0).commonAncestorContainer)
    : false;
  extractSelection.value = inside ? selection!.toString().trim() : '';
  extractOpen.value = true;
}

const loading = ref(false);
const currentChapterId = computed(() => Number(route.params.id));
const currentChapter = ref<LearningChapterVO | null>(null);
const pathChapters = ref<LearningChapterVO[]>([]);
const pathDetail = ref<LearningPathVO | null>(null);
const isFavorite = ref(false);
const activeOutlineIndex = ref(0);
const readingRoot = ref<HTMLElement | null>(null);
/** 章节所属技术领域提示（只用于术语多义项排序，不作为权限依据） */
const chapterDomain = computed(() =>
  inferTermDomain([pathDetail.value?.title, currentChapter.value?.title].filter(Boolean).join(' ')),
);
const readingSaving = ref(false), readingSaveError = ref(''), readingSaveSuccess = ref(false);
const readingInteracted = ref(false);
const currentReadingAnchor = computed(() => outlineItems.value[activeOutlineIndex.value]?.id || 'top');
const readingDirty = computed(() => readingInteracted.value && currentReadingAnchor.value !== (currentChapter.value?.readingAnchor || 'top'));
async function saveReadingPosition() {
  if (!currentChapter.value || readingSaving.value) return;
  const chapter = currentChapter.value, anchor = currentReadingAnchor.value;
  if (!chapter.contentHash) {readingSaveError.value='缺少正文版本，请重新加载章节';return;}
  readingSaving.value = true; readingSaveError.value = ''; readingSaveSuccess.value = false;
  try {
    const presentation = await presentationFingerprint(readingSource.value);
    const saved = await learningApi.saveReadingPosition(chapter.id, anchor, chapter.readingRevision || 0, chapter.contentHash, presentation);
    if (chapter.id === currentChapterId.value) {
      currentChapter.value = {...currentChapter.value!, readingAnchor:saved.readingAnchor, readingRevision:saved.readingRevision, readingSavedAt:saved.readingSavedAt, readingPositionStale:saved.readingPositionStale, readingPresentationHash:saved.readingPresentationHash};
      readingSaveSuccess.value = true;
    }
  } catch (e) { if (chapter.id === currentChapterId.value) readingSaveError.value = getApiError(e); }
  finally { readingSaving.value = false; }
}
function resumeReading() {
  const anchor = currentChapter.value?.readingAnchor;
  if (!anchor || readingPositionStale.value) return;
  if (anchor === 'top') { readingRoot.value?.scrollIntoView({block:'start'}); return; }
  const heading = outlineItems.value.find(item => item.id === anchor);
  if (heading) scrollToHeading(heading.id);
  else readingSaveError.value = '已保存的标题已不存在，请重新选择并保存。';
}
async function protectChapterLeave() {
  if (readingSaving.value || noteSaving.value) { notify('正在保存，请稍候。','warning'); return false; }
  if (readingDirty.value || noteText.value.trim()) return await confirmDialog('阅读位置或笔记尚未保存，仍要离开吗？', {"title":"放弃未保存的修改？","confirmText":"放弃并离开","cancelText":"返回编辑","icon":"log-out","variant":"default"});
  return true;
}
onBeforeRouteLeave(protectChapterLeave);
onBeforeRouteUpdate(protectChapterLeave);

const chapterQuestions = ref<InterviewQuestion[]>([]);
function handleReadingClick(event:MouseEvent) {
 handleCodeCopy(event);
 const button=(event.target as HTMLElement)?.closest<HTMLButtonElement>('[data-question-target]');
 if(!button || button.disabled)return;
 const question=readingRoot.value?.querySelector<HTMLDetailsElement>(`#interview-question-${button.dataset.questionTarget}`);
 if(!question)return;
 for(let node:HTMLElement|null=question;node;node=node.parentElement)if(node instanceof HTMLDetailsElement)node.open=true;
 question.scrollIntoView({behavior:'smooth',block:'center'});
 question.querySelector<HTMLElement>('summary')?.focus();
}
const chapterError = ref('');
const navigationError = ref('');
const favoriteError = ref('');
const favoriteBusy = ref(false);
const showNote = ref(false);
const noteText = ref('');
const noteSaving = ref(false);
const noteError = ref('');
let chapterRequest = 0;
const revision = ref<CollectionsRevision | null>(null);
const curatedReading = ref<string|null>(null);
const trainingResources=ref<{questions:number;exercises:number}|null>(null);
const cardCount=ref<number|null>(null),cardError=ref('');
const questionCount=computed(()=>revision.value?.questions.length ?? trainingResources.value?.questions ?? null);
const exerciseCount=computed(()=>revision.value?.exercises.length ?? trainingResources.value?.exercises ?? null);
async function loadCardCount(id:number){cardCount.value=null;cardError.value='';try{const cards=await learningApi.flashcards(undefined,id);if(id===currentChapterId.value)cardCount.value=cards.length}catch(e){if(id===currentChapterId.value)cardError.value=getApiError(e)}}
function onRevision(value:CollectionsRevision|null){revision.value=value;if(value)void loadCardCount(currentChapterId.value)}
const pathProgress = computed(() => pathDetail.value ? displayPathProgress(pathDetail.value) : null);
const conceptsExpanded = ref(false);
const flashcardRoute = computed(()=>resolveContextualRoute(stampActionContext({type:'FLASHCARD',title:'本章闪卡',reason:'用户选择本章回忆',route:''},{learningNodeId:currentChapterId.value})));
const scrollToTrainingSection = (kind:'questions'|'exercises') => (readingRoot.value?.querySelector(`#chapter-${kind}-${currentChapterId.value}`) || readingRoot.value?.querySelector('#chapter-training'))?.scrollIntoView({behavior:'smooth',block:'start'});
const openChapterNote = async () => { showNote.value=true; await nextTick(); document.getElementById('chapter-note-text')?.focus(); };

// ===== 面包屑与章节导航 =====
const currentChapterIndex = computed(() =>
  pathChapters.value.findIndex((c) => c.id === currentChapterId.value),
);
const hasPrevChapter = computed(() => currentChapterIndex.value > 0);
const hasNextChapter = computed(() => currentChapterIndex.value >= 0 && currentChapterIndex.value < pathChapters.value.length - 1);
const selectChapter=(id:number)=>{const chapter=pathChapters.value.find(c=>c.id===id);if(chapter && !chapter.locked)void router.push(`/learning/chapter/${id}`)};
const goToPrevChapter = () => {
  if (hasPrevChapter.value) {
    const prev = pathChapters.value[currentChapterIndex.value - 1];
    router.push(`/learning/chapter/${prev.id}`);
  }
};
const goToNextChapter = () => {
  if (hasNextChapter.value) {
    const next = pathChapters.value[currentChapterIndex.value + 1];
    router.push(`/learning/chapter/${next.id}`);
  }
};

// ===== 完成章节 =====
const completing = ref(false);
const refreshPathProgress = async () => {
  const pathId=currentChapter.value?.pathId;
  if(!pathId)return;
  try {const detail=await learningApi.pathDetail(pathId); if(currentChapter.value?.pathId===pathId)pathDetail.value=detail;}
  catch(e){navigationError.value=`完成已保存，但进度刷新失败：${getApiError(e)}`;}
};

// 完成最后一章（路径达成 100%）后，查询该路径证书并触发全屏庆祝，避免无感获得证书
const checkPathCertificate = async () => {
  const pathId = currentChapter.value?.pathId;
  if (!pathId || hasNextChapter.value) return;
  try {
    const list = await learningApi.certificates();
    const cert: LearningCertificateVO | undefined = list.find((c) => c.pathId === pathId);
    if (cert?.id) {
      celebrateCertificate({
        id: cert.id,
        pathTitle: cert.pathTitle,
        userName: cert.userName,
        certNo: cert.certNo,
        issueDate: cert.issueDate,
      });
    }
  } catch {
    // 证书查询失败不阻塞学习，静默忽略
  }
};

// 标记当前章节完成；全部章节完成后后端自动颁发数字证书
const completeCurrentChapter = async () => {
  if (!currentChapter.value || currentChapter.value.completed) return;
  if (currentChapter.value.locked) {
    notify('请先完成前置章节', 'warning');
    return;
  }
  completing.value = true;
  try {
    await learningApi.completeChapter(currentChapter.value.id);
    // 更新当前章节与进度点状态
    currentChapter.value.completed = true;
    const idx = currentChapterIndex.value;
    if (idx >= 0) {
      pathChapters.value[idx] = { ...pathChapters.value[idx], completed: true };
    }
    notify('章节已完成！', 'success');
    await refreshPathProgress();

    // 完成最后一章后检测证书并弹出全屏庆祝
    await checkPathCertificate();

    // 还有下一章则自动跳转
    if (hasNextChapter.value) {
      await nextTick();
      goToNextChapter();
    }
  } catch (e) {
    const msg = getApiError(e);
    // 未报名路径时引导报名：确认后先在章节页报名，再自动重新完成
    if (msg.includes('报名')) {
      const p = currentChapter.value?.pathId;
      const ok = await confirmDialog('尚未报名该学习路径，是否立即报名后完成本章？', {"title":"报名并继续学习？","confirmText":"报名并继续","cancelText":"暂不报名","icon":"graduation-cap","variant":"default"});
      if (ok && p) {
        completing.value = true;
        try {
          await learningApi.enroll(p);
          notify('报名成功，正在完成章节...', 'success');
          await learningApi.completeChapter(currentChapter.value!.id);
          currentChapter.value!.completed = true;
          const idx = currentChapterIndex.value;
          if (idx >= 0) {
            pathChapters.value[idx] = { ...pathChapters.value[idx], completed: true };
          }
          notify('章节已完成！', 'success');
          await refreshPathProgress();
          // 完成最后一章后检测证书并弹出全屏庆祝
          await checkPathCertificate();
          if (hasNextChapter.value) {
            await nextTick();
            goToNextChapter();
          }
        } catch {
          notify('操作失败，请稍后重试', 'error');
        }
      }
    } else {
      notify(msg || '提交失败，请重试', 'error');
    }
  } finally {
    completing.value = false;
  }
};

// ===== 收藏与笔记 =====
const loadFavorite = async (id: number) => {
  favoriteError.value = '';
  try { const r = await learningApi.favorite(id); if (id === currentChapterId.value) isFavorite.value = r.favorited; }
  catch (e) { if (id === currentChapterId.value) favoriteError.value = getApiError(e); }
};
const toggleFavorite = async () => {
  if (favoriteBusy.value || !currentChapter.value) return;
  favoriteBusy.value = true;
  const id = currentChapter.value.id;
  try { const r = await learningApi.toggleChapterFavorite(id); if (id === currentChapterId.value) { isFavorite.value = r.favorited; notify(r.favorited ? '已收藏本章' : '已取消收藏', 'success'); } }
  catch (e) { notify(getApiError(e), 'error'); }
  finally { favoriteBusy.value = false; }
};
const saveNote = async () => {
  if (!currentChapter.value || noteSaving.value || !noteText.value.trim()) return;
  noteSaving.value = true; noteError.value = '';
  const id = currentChapter.value.id;
  try { await learningApi.createChapterNote({ chapterId: id, pathId: currentChapter.value.pathId, title: `${currentChapter.value.title} · 学习笔记`, content: noteText.value.trim() }); if (id === currentChapterId.value) { noteText.value = ''; showNote.value = false; } notify('本章笔记已保存', 'success'); }
  catch (e) { noteError.value = getApiError(e); }
  finally { noteSaving.value = false; }
};

// ===== 大纲：从正文标题（h2/h3）提取 =====
const readingSource = computed(() => revision.value?.content || curatedReading.value || currentChapter.value?.content || '');
const presentationHash = ref('');
let fingerprintRequest = 0;
async function presentationFingerprint(content:string):Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256',new TextEncoder().encode(content));
  return Array.from(new Uint8Array(hash),byte=>byte.toString(16).padStart(2,'0')).join('');
}
watch(readingSource, async source => {
  const request=++fingerprintRequest;presentationHash.value='';
  try {const hash=await presentationFingerprint(source);if(request===fingerprintRequest)presentationHash.value=hash;}
  catch {if(request===fingerprintRequest)readingSaveError.value='当前浏览器无法验证阅读版本，请重新加载后重试。';}
});
const readingPositionStale = computed(() => !!currentChapter.value?.readingPositionStale || !!(currentChapter.value?.readingPresentationHash && presentationHash.value && currentChapter.value.readingPresentationHash !== presentationHash.value));
const reading = computed(() => chapterReading(readingSource.value, {numberHeadings:true,inlineSourceImages:currentChapter.value?.inlineSourceImages}));
const originalReading = computed(() => chapterReading(currentChapter.value?.content || '',{inlineSourceImages:currentChapter.value?.inlineSourceImages}).html.replace(/ id="heading-\d+"/g, ''));
const outlineItems = computed(() => reading.value.outline);

const scrollToHeading = (id: string) => {
  readingInteracted.value = true; readingSaveSuccess.value = false;
  const el = readingRoot.value?.querySelector(`#${id}`);
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  activeOutlineIndex.value = outlineItems.value.findIndex(item => item.id === id);
};
const updateOutline = (event?: Event) => {
  if (event?.isTrusted && !loading.value) readingInteracted.value = true;
  let active = 0;
  outlineItems.value.forEach((item,index) => {
    const heading=readingRoot.value?.querySelector(`#${item.id}`);
    if(heading && heading.getBoundingClientRect().top<=140)active=index;
  });
  if (activeOutlineIndex.value !== active) readingSaveSuccess.value = false;
  activeOutlineIndex.value=active;
};
onMounted(()=>window.addEventListener('scroll',updateOutline,true));

// ===== Markdown 渲染 =====
const renderedContent = computed(() => reading.value.html);

// 转义 HTML 特殊字符，防止注入并正确显示代码内容
const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// ===== 数据加载 =====
const loadChapter = async (id: number) => {
  const request = ++chapterRequest;
  readingInteracted.value=false;readingSaveError.value='';readingSaveSuccess.value=false;
  loading.value = true;
  chapterError.value = ''; navigationError.value = '';
  currentChapter.value = null; pathChapters.value = []; pathDetail.value = null;
  revision.value = null;
  curatedReading.value = null; conceptsExpanded.value=false;
  trainingResources.value=null;cardCount.value=null;cardError.value='';
  isFavorite.value = false; favoriteError.value = ''; relatedDocs.value = []; relatedKnowledge.value = [];
  showNote.value = false; noteText.value = ''; noteError.value = ''; activeOutlineIndex.value = 0;
  try {
    const c = await learningApi.chapterDetail(id);
    if (request !== chapterRequest) return;
    currentChapter.value = c;
    if (c.pathId) {
      const [chapters, detail] = await Promise.allSettled([
        learningApi.chapters(c.pathId),
        learningApi.pathDetail(c.pathId),
      ]);
      if (request !== chapterRequest) return;
      if (chapters.status === 'fulfilled') pathChapters.value = chapters.value;
      else navigationError.value = getApiError(chapters.reason);
      if (detail.status === 'fulfilled') pathDetail.value = detail.value;
      else navigationError.value = getApiError(detail.reason);
    }
    void loadRelatedDocs(c);
    void loadRelatedKnowledge(id);
    void loadFavorite(id);
    void loadCardCount(id);
  } catch (e) {
    if (request === chapterRequest) chapterError.value = getApiError(e);
  } finally {
    if (request !== chapterRequest) return;
    loading.value = false;
    // 渲染完成后滚动到顶部
    await nextTick();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

// ===== A-FB-01 相关概念/文档推荐 =====
const relatedDocs = ref<DocVO[]>([]);
const relatedKnowledge = ref<{ id: number; name: string }[]>([]);
const relatedConcepts = computed(() => revision.value?.knowledge || relatedKnowledge.value);
const outlineExpanded = ref(false);
const knowledgeError = ref('');
const knowledgeLoading = ref(false);
const mappedPractice = ref<{id:number;title:string;questionType:string}[]>([]);
const chapterPracticeRoute = (id:number) => resolvePlanResourceSelector('QUIZ',id,{type:'EVIDENCE_PRACTICE',title:'章节练习',reason:'用户选择已审核关联练习',route:'/learning/quiz'},{learningNodeId:currentChapterId.value});
const docsError = ref('');
const loadRelatedKnowledge = async (id: number) => {
  knowledgeLoading.value=true; knowledgeError.value = ''; mappedPractice.value=[];
  try { const r = await learningApi.chapterKnowledge(id); if (id === currentChapterId.value) {relatedKnowledge.value = r.knowledge; mappedPractice.value=r.questions || [];} }
  catch (e) { if (id === currentChapterId.value) knowledgeError.value = getApiError(e); }
  finally {if(id===currentChapterId.value)knowledgeLoading.value=false;}
};

const loadRelatedDocs = async (chapter: LearningChapterVO | null) => {
  relatedDocs.value = [];
  docsError.value = '';
  const ids = (chapter?.docIds || '').split(',').map(Number).filter(id => Number.isSafeInteger(id) && id > 0);
  if (!chapter || !ids.length) return;
  try {
    const docs = await Promise.all(ids.map(id => docsApi.detail(id)));
    if (chapter.id === currentChapterId.value) relatedDocs.value = docs;
  } catch (e) {
    if (chapter.id === currentChapterId.value) docsError.value = getApiError(e);
  }
};

// 外链播放器不提供可信的播放事件，阅读停留时间不能作为观看证据。

// 将通用视频链接转换为可内嵌的 iframe 地址
const toEmbedUrl = (raw: string): string => {
  const url = raw.trim();
  // YouTube：watch?v= 或 youtu.be/
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/);
  if (yt) return `https://www.youtube.com/embed/${yt[1]}`;
  // Bilibili：BV 号视频
  const bv = url.match(/bilibili\.com\/video\/([A-Za-z0-9]+)/);
  if (bv) return `https://player.bilibili.com/player.html?bvid=${bv[1]}&page=1`;
  // 已是可嵌入地址或其它外链则原样嵌入
  return url;
};

// ===== L-FORM-02 / A-FB-02 章节内嵌即时测验 =====
interface EmbeddedQuiz {
  question: string;
  options: string[];
  answer: string;
  explanation: string;
}

// 解析约定 quiz 文本：`Q:` 题干、`A./B./...` 选项、`答案:` 与 `解析:`
const parseQuiz = (raw: string): EmbeddedQuiz | null => {
  const lines = raw.split('\n').map((l) => l.trim()).filter(Boolean);
  const qLine = lines.find((l) => l.startsWith('Q:'));
  if (!qLine) return null;
  const question = qLine.replace(/^Q:\s*/, '');
  const options: string[] = [];
  for (const l of lines) {
    const m = /^([A-H])[.、．]\s*(.+)$/.exec(l);
    if (m) options.push(`${m[1]}. ${m[2]}`);
  }
  const ansLine = lines.find((l) => /^答案[:：]/.test(l));
  const ansMatch = ansLine ? /^答案[:：]\s*([A-H])/.exec(ansLine) : null;
  const answer = ansMatch ? ansMatch[1].toUpperCase() : '';
  const expLine = lines.find((l) => /^解析[:：]/.test(l));
  const explanation = expLine ? expLine.replace(/^解析[:：]\s*/, '') : '';
  if (!question || options.length === 0 || !answer) return null;
  return { question, options, answer, explanation };
};

const bindInteractiveQuiz = (container: HTMLElement) => {
  if (container.querySelector('.quiz-card')) return;
  const raw = container.getAttribute('data-quiz') || '';
  const quiz = parseQuiz(raw);
  if (!quiz) {
    container.innerHTML = '<pre class="quiz-invalid">测验格式有误，请检查 `Q:` / 选项 / `答案:` 定义</pre>';
    return;
  }
  const optionBtns = quiz.options.map((opt) =>
    `<button type="button" class="quiz-opt" data-value="${escapeHtml(opt[0])}"><span class="quiz-opt-label">${escapeHtml(opt)}</span></button>`,
  ).join('');
  container.innerHTML = `
    <div class="quiz-card">
      <div class="quiz-head">
        <span class="quiz-badge">即时测验</span>
      </div>
      <p class="quiz-question">${escapeHtml(quiz.question)}</p>
      <div class="quiz-options">${optionBtns}</div>
      <p class="quiz-feedback" style="display:none;"></p>
    </div>`;
  container.querySelectorAll<HTMLButtonElement>('.quiz-opt').forEach((btn) => {
    btn.addEventListener('click', () => {
      const chosen = btn.getAttribute('data-value') || '';
      const correct = chosen === quiz.answer;
      container.querySelectorAll<HTMLButtonElement>('.quiz-opt').forEach((b) => {
        b.classList.remove('quiz-correct', 'quiz-wrong');
        if (b.getAttribute('data-value') === quiz.answer) b.classList.add('quiz-correct');
        if (b === btn && !correct) b.classList.add('quiz-wrong');
        b.disabled = true;
      });
      const feedback = container.querySelector<HTMLElement>('.quiz-feedback');
      if (feedback) {
        feedback.style.display = 'block';
        feedback.textContent = correct
          ? `✅ 回答正确${quiz.explanation ? `：${quiz.explanation}` : ''}`
          : `❌ 回答错误，正确答案是 ${quiz.answer}${quiz.explanation ? `。${quiz.explanation}` : ''}`;
        feedback.classList.toggle('quiz-correct-msg', correct);
        feedback.classList.toggle('quiz-wrong-msg', !correct);
      }
    });
  });
};

// 正文渲染完成后初始化视频与声明式测验；源码由公共 renderer 安全展示。
watch([renderedContent, originalReading, loading], ([, , busy]) => {
  if (busy) return;
  nextTick(() => {
    const root = readingRoot.value;
    if (!root) return;
    const holders = root.querySelectorAll<HTMLElement>('.chapter-video[data-video-url]');
    if (holders.length > 0) {
      holders.forEach((holder) => {
        if (holder.querySelector('iframe')) return;
        const url = holder.getAttribute('data-video-url') || '';
        const embed = document.createElement('iframe');
        embed.src = toEmbedUrl(url);
        embed.setAttribute('frameborder', '0');
        embed.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
        embed.setAttribute('allowfullscreen', '');
        // No allow-same-origin: even a same-site video URL must not inherit
        // the authenticated reader's storage/DOM privileges.
        embed.setAttribute('sandbox', 'allow-scripts allow-presentation');
        embed.title = '章节参考视频';
        embed.classList.add('chapter-video-frame');
        holder.appendChild(embed);
        const notice = document.createElement('p');
        notice.textContent = '外部视频不自动记录观看进度。理解内容后，可手动标记本章已完成。';
        holder.appendChild(notice);
      });
    }
    const quizBlocks = root.querySelectorAll<HTMLElement>('.interactive-quiz[data-quiz]');
    quizBlocks.forEach(bindInteractiveQuiz);
  });
});

watch(currentChapterId, id => void loadChapter(id), { immediate: true });
watch([chapterQuestions,renderedContent],async()=>{await nextTick();if(readingRoot.value)bindChapterQuizReferences(readingRoot.value,chapterQuestions.value);if(route.hash===`#chapter-questions-${currentChapterId.value}`)scrollToTrainingSection('questions')});
watch(()=>route.hash,async hash=>{await nextTick();if(hash===`#chapter-questions-${currentChapterId.value}`)scrollToTrainingSection('questions')});

onBeforeUnmount(() => { chapterRequest++; window.removeEventListener('scroll',updateOutline,true); });
</script>

<style scoped>
.chapter-learn { min-width: 0; }
.chapter-columns { display: grid; grid-template-columns: minmax(0, 1fr) var(--kb-chapter-outline-width); gap: var(--kb-space-6); }
.content-body { max-width: var(--kb-chapter-reading-width); padding: var(--kb-space-6); margin-inline: auto; overflow-wrap: anywhere; font-size: var(--kb-fs-body-md); }
.reading-header { padding-block: var(--kb-space-4); margin-bottom: var(--kb-space-6); border-bottom: 1px solid var(--kb-border); }
.reading-header h1 { font-size: var(--kb-fs-h1); line-height: var(--kb-lh-heading, 1.3); font-weight: 700; color: var(--kb-foreground); }
.chapter-meta { display: flex; flex-wrap: wrap; gap: var(--kb-space-4); margin-top: var(--kb-space-3); font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); }
.outline-sticky { position: sticky; top: var(--kb-chapter-sticky-offset); max-height: calc(100vh - var(--kb-chapter-sticky-offset) - var(--kb-chapter-action-height)); overflow-y: auto; padding: var(--kb-space-4); }
.resource-error { margin: var(--kb-space-3); color: var(--kb-state-error); font-size: var(--kb-fs-body-sm); }
.resource-error button { text-decoration: underline; margin-inline-start: var(--kb-space-2); }
.resource-caption { padding: var(--kb-space-2); font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.chapter-learn .bottom-action-bar { position: sticky; bottom: 0; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--kb-space-3); padding: var(--kb-space-3); border: 1px solid var(--kb-border); z-index: 10; }
.bottom-action-bar > button, .bottom-action-bar > div { min-width: 0; max-width: 100%; }
.bottom-action-bar button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bottom-prev, .bottom-next { max-width: var(--kb-chapter-outline-width); }
.chapter-note { padding: var(--kb-space-6); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); }
.chapter-note label { display: block; margin-block: var(--kb-space-3); }
.note-save { padding: var(--kb-space-2) var(--kb-space-4); background: var(--kb-primary); color: var(--kb-primary-foreground); border-radius: var(--kb-radius-sm); margin-top: var(--kb-space-3); }
.note-save:disabled { opacity: .5; cursor: not-allowed; }
.content-body :deep(table) { width: 100%; border-collapse: collapse; margin-block: var(--kb-space-4); font-size: var(--kb-fs-body-sm); }
.content-body :deep(th), .content-body :deep(td) { border: 1px solid var(--kb-border); padding: var(--kb-space-2); text-align: start; }
.content-body :deep(th) { background: var(--kb-muted); }
.content-body :deep(a) { color: var(--kb-primary); text-decoration: underline; text-underline-offset: .2em; }
.content-body :deep(.code-block-wrapper) { margin-block: var(--kb-space-4); border-radius: var(--kb-radius-md); overflow: hidden; background: var(--kb-foreground); color: var(--kb-background); }
.content-body :deep(.code-block-header) { display: flex; justify-content: space-between; align-items: center; padding: var(--kb-space-2) var(--kb-space-4); border-bottom: 1px solid var(--kb-muted-foreground); }
.content-body :deep(.code-copy-btn) { display: inline-flex; gap: var(--kb-space-2); align-items: center; font-size: var(--kb-fs-caption); }
.content-body :deep(.code-block-body) { display: flex; min-width: 0; }
.content-body :deep(.code-line-numbers) { display: none; }
.content-body :deep(.code-block-body pre) { margin: 0; width: 100%; white-space: pre-wrap; overflow-wrap: anywhere; }
.content-body :deep(.code-block-body code) { font-family: var(--font-mono); }
.outline-toggle { display: none; color: var(--kb-primary); padding: var(--kb-space-3); }
@media (max-width: 1100px) { .chapter-columns { grid-template-columns: minmax(0, 1fr); } .chapter-learn .outline-sidebar { display: block; border: 1px solid var(--kb-border); } .outline-sticky { position: static; max-height: none; } .outline-sidebar { order: -1; } .outline-toggle { display: block; } .outline-content.is-collapsed { display: none; } .content-body { padding-inline: var(--kb-space-4); } }
@media (prefers-reduced-motion: reduce) { .chapter-learn *, .chapter-learn :deep(*) { scroll-behavior: auto; transition: none; } }
/* 面包屑 */
.breadcrumb-bar {
  border-bottom: 1px solid var(--kb-border);
}
.breadcrumb-link {
  color: var(--kb-muted-foreground);
  transition: color 0.15s ease;
}
.breadcrumb-link:hover {
  color: var(--kb-primary);
}
.breadcrumb-sep {
  color: var(--kb-muted-foreground);
}

/* 进度点 */
.progress-bar-area {
  border-bottom: 1px solid var(--kb-border);
}
.progress-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--kb-muted);
  cursor: pointer;
  transition: transform 0.15s ease;
}
.progress-dot:hover {
  transform: scale(1.2);
}
.progress-dot.is-done {
  background: var(--kb-accent);
}
.progress-dot.is-current {
  background: var(--kb-primary);
  box-shadow: 0 0 0 2px var(--kb-card), 0 0 0 4px var(--kb-primary);
}
.progress-line {
  width: 24px;
  height: 1px;
  flex-shrink: 0;
}

/* 正文 Markdown 排版（与设计稿 .content-body 对齐） */
.content-body :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: 24px 0 12px;
  color: var(--kb-foreground);
  scroll-margin-top: 80px;
}
.content-body :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--kb-foreground);
  scroll-margin-top: 80px;
}
.content-body :deep(p) {
  margin: 8px 0;
  line-height: 1.7;
  color: var(--kb-card-foreground);
}
.content-body :deep(code) {
  background: var(--kb-muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 14px;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
}
.content-body :deep(pre) {
  background: var(--kb-foreground);
  color: var(--kb-background);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  font-size: 14px;
  line-height: 1.6;
}
.content-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.content-body :deep(ul),
.content-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.content-body :deep(li) {
  margin: 4px 0;
  line-height: 1.6;
}

/* L-FORM-01 章节嵌入视频 */
.content-body :deep(.chapter-video) {
  position: relative;
  width: 100%;
  margin: var(--kb-space-4) 0;
  border-radius: var(--kb-radius-lg);
  overflow: hidden;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.content-body :deep(.chapter-video-frame) {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 0;
  display: block;
}

/* L-FORM-02 互动讲义：内嵌可运行代码 */
.interactive-code {
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--kb-border);
}
.code-runner {
  display: flex;
  flex-direction: column;
}
.code-runner-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--kb-muted);
}
.code-runner-lang {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  font-family: 'Fira Code', 'Consolas', monospace;
}
.code-run-btn {
  padding: 4px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.code-run-btn:hover {
  opacity: 0.9;
}
.code-run-input {
  width: 100%;
  min-height: 140px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  font-family: 'Fira Code', 'Consolas', monospace;
  color: var(--kb-foreground);
  background: var(--kb-card);
  border: none;
  border-top: 1px solid var(--kb-border);
  resize: vertical;
  outline: none;
}
.code-run-output {
  min-height: 40px;
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  font-family: 'Fira Code', 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--kb-foreground);
  color: var(--kb-background);
  border-top: 1px solid var(--kb-border);
}
.code-run-output.has-error {
  color: var(--kb-danger-tint);
}

/* L-FORM-02 / A-FB-02 章节内嵌即时测验 */
.content-body :deep(.interactive-quiz) {
  margin: var(--kb-space-4) 0;
}
.content-body :deep(.quiz-card) {
  padding: var(--kb-space-4);
  border-radius: var(--kb-radius-lg);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
}
.content-body :deep(.quiz-head) {
  margin-bottom: var(--kb-space-2);
}
.content-body :deep(.quiz-badge) {
  display: inline-block;
  padding: var(--kb-space-1) var(--kb-space-3);
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-radius: var(--kb-badge-radius);
}
.content-body :deep(.quiz-question) {
  margin: var(--kb-space-2) 0 var(--kb-space-3);
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  line-height: 1.6;
  color: var(--kb-foreground);
}
.content-body :deep(.quiz-options) {
  display: flex;
  flex-direction: column;
  gap: var(--kb-space-2);
}
.content-body :deep(.quiz-opt) {
  display: flex;
  align-items: center;
  padding: var(--kb-space-3);
  font-size: var(--kb-fs-body-sm);
  text-align: left;
  color: var(--kb-card-foreground);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.content-body :deep(.quiz-opt:hover:not(:disabled)) {
  border-color: var(--kb-primary);
}
.content-body :deep(.quiz-opt:disabled) {
  cursor: default;
}
.content-body :deep(.quiz-opt-label) {
  line-height: 1.5;
}
.content-body :deep(.quiz-opt.quiz-correct) {
  border-color: var(--kb-accent);
  background: var(--kb-success-soft);
}
.content-body :deep(.quiz-opt.quiz-wrong) {
  border-color: var(--kb-danger);
  background: var(--kb-danger-soft);
}
.content-body :deep(.quiz-feedback) {
  margin-top: var(--kb-space-3);
  padding: var(--kb-space-3);
  font-size: var(--kb-fs-body-sm);
  line-height: 1.6;
  border-radius: var(--kb-radius-md);
}
.content-body :deep(.quiz-feedback.quiz-correct-msg) {
  color: var(--kb-state-success);
  background: var(--kb-success-soft);
}
.content-body :deep(.quiz-feedback.quiz-wrong-msg) {
  color: var(--kb-state-error);
  background: var(--kb-danger-soft);
}
.content-body :deep(.quiz-invalid) {
  padding: var(--kb-space-3);
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-state-error);
  background: var(--kb-danger-soft);
  border-radius: var(--kb-radius-md);
}

/* 右侧大纲 */
.outline-sidebar {
  background: var(--kb-sidebar);
  border-color: var(--kb-border);
}
.outline-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 12px;
  padding-left: 8px;
  color: var(--kb-muted-foreground);
}
.outline-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--kb-sidebar-foreground);
  text-decoration: none;
  transition: background-color 0.15s ease;
  cursor: pointer;
}
.outline-item:hover {
  background: var(--kb-muted);
}
.outline-item.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-weight: 500;
}
.outline-item.sub {
  padding-left: 32px;
  font-size: 13px;
}
.chapter-info-section {
  border-top: 1px solid var(--kb-border);
}
/* A-FB-01 相关概念/文档 */
.related-section {
  border-top: 1px solid var(--kb-border);
}
.related-concept-chip {
  padding: 4px 10px;
  font-size: 12px;
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  border: 1px solid rgba(59, 111, 224, 0.15);
  border-radius: 999px;
}
.related-doc-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 6px;
  text-decoration: none;
  transition: background-color 0.15s ease;
}
.related-doc-item:hover {
  background: var(--kb-muted);
}
.related-doc-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-sidebar-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.related-doc-summary {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 底部操作栏 */
.bottom-action-bar {
  background: var(--kb-card);
  border-color: var(--kb-border);
}
.bottom-prev {
  color: var(--kb-muted-foreground);
  transition: background 0.15s ease;
}
.bottom-prev:not(:disabled):hover {
  background: var(--kb-muted);
}
.bottom-prev:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.bottom-icon-btn {
  color: var(--kb-muted-foreground);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: background 0.15s ease, color 0.15s ease;
}
.bottom-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 底部栏「标记本章已完成」按钮：与下一章并排、适度强调（品牌绿描边+细阴影），区别于收藏/笔记的次要样式 */
.complete-btn-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
  background: var(--kb-card);
  border: 1.5px solid var(--kb-accent);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.18);
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease, opacity 0.15s ease;
}
.complete-btn-inline:hover:not(:disabled) {
  background: var(--kb-accent);
  color: #ffffff;
  transform: translateY(-1px);
}
.complete-btn-inline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.complete-status-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-accent);
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
}
.complete-status-inline.locked {
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
  border-color: var(--kb-border);
}

.bottom-next {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  transition: opacity 0.2s ease;
}
.bottom-next:not(:disabled):hover {
  opacity: 0.9;
}
.bottom-next:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 滚动条隐藏 */
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* Chapter V2: one quiet article surface; hierarchy lives in type and spacing. */
.chapter-learn {max-width: none;margin-inline: 0;padding-bottom:var(--kb-space-6);
  width: 100%;
  min-width: 0;
}
.chapter-columns {gap:var(--kb-chapter-column-gap)}
.content-body {padding:var(--kb-space-6) var(--kb-space-4) var(--kb-space-8);font-size:var(--kb-chapter-body-size);line-height:var(--kb-chapter-body-leading)}
.reading-header h1 {font-size:var(--kb-chapter-h1-size);line-height:1.3}
.chapter-summary {color:var(--kb-muted-foreground);margin-top:var(--kb-space-4)}
.chapter-progress {padding:var(--kb-space-4);border-bottom:1px solid var(--kb-border);font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground)}
.chapter-progress>div {display:flex;justify-content:space-between;gap:var(--kb-space-3);margin-bottom:var(--kb-space-2)}
.chapter-progress progress {display:block;width:100%;height:var(--kb-space-2);accent-color:var(--kb-primary);border:0;border-radius:var(--kb-radius-sm)}
.chapter-progress progress::-webkit-progress-bar {background:var(--kb-muted);border-radius:var(--kb-radius-sm)}
.chapter-progress progress::-webkit-progress-value {background:var(--kb-primary);border-radius:var(--kb-radius-sm)}
.chapter-progress progress::-moz-progress-bar {background:var(--kb-primary);border-radius:var(--kb-radius-sm)}
.learning-objectives {margin-block:var(--kb-space-6) var(--kb-space-8);padding:var(--kb-space-4) var(--kb-space-6);border-left:var(--kb-space-1) solid var(--kb-primary);background:var(--kb-primary-subtle)}
.content-body .learning-objectives h2 {font-size:var(--kb-fs-h4);margin:0 0 var(--kb-space-3)}
.learning-objectives p {font-size:var(--kb-fs-caption);color:var(--kb-muted-foreground)}
.chapter-article :deep(h2) {font-size:var(--kb-chapter-h2-size);font-weight:700;line-height:1.35;margin:var(--kb-space-8) 0 var(--kb-space-4)}
.chapter-article :deep(h3) {font-size:var(--kb-chapter-h3-size);font-weight:600;line-height:1.4;margin:var(--kb-space-6) 0 var(--kb-space-3)}
.chapter-article :deep(h4) {font-size:var(--kb-fs-body-lg);margin-block:var(--kb-space-4) var(--kb-space-2)}
.chapter-article :deep(p),.chapter-article :deep(li) {line-height:var(--kb-chapter-body-leading);color:var(--kb-card-foreground)}
.chapter-article :deep(p) {margin-block:var(--kb-space-4)}
.chapter-article :deep(ul),.chapter-article :deep(ol) {margin-block:var(--kb-space-4);padding-left:var(--kb-space-6)}
.chapter-article :deep(blockquote) {margin-block:var(--kb-space-6);padding:var(--kb-space-3) var(--kb-space-4);border-left:var(--kb-space-1) solid var(--kb-primary-tint);background:var(--kb-primary-subtle)}
.chapter-article :deep(hr) {border:0;border-top:1px solid var(--kb-border);margin-block:var(--kb-space-8)}
.chapter-article :deep(img) {max-width:100%;height:auto;border-radius:var(--kb-radius-sm)}
.chapter-article :deep(table) {display:block;max-width:100%;overflow-x:auto}
.content-body :deep(.code-block-body pre) {min-width:0;white-space:pre;overflow-wrap:normal;padding:var(--kb-space-4);font-size:var(--kb-chapter-code-size);line-height:1.65}
.content-body :deep(.code-block-body pre code) {font-size:inherit}
.chapter-toc {max-height:38vh;overflow-y:auto;overscroll-behavior:contain}
.outline-item {overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chapter-tools {margin-top:var(--kb-space-6);padding-top:var(--kb-space-4);border-top:1px solid var(--kb-border)}
.chapter-tools>a,.chapter-tools>button {display:block;text-align:left;padding:var(--kb-space-2);font-size:var(--kb-fs-body-sm);color:var(--kb-primary);width:100%}
.chapter-tools .resource-caption {line-height:1.6}
.related-expand {font-size:var(--kb-fs-caption);color:var(--kb-primary);padding:var(--kb-space-2)}
.chapter-learn .bottom-action-bar button {min-height:var(--kb-chapter-button-height);height:auto;display:inline-flex;align-items:center;gap:var(--kb-space-2);padding:var(--kb-space-2) var(--kb-space-3);border-radius:var(--kb-radius-sm);font-size:var(--kb-fs-body-sm);box-shadow:none;white-space:normal}
.bottom-action-bar button span {overflow:visible;text-overflow:clip;white-space:normal}
.bottom-action-bar .complete-btn-inline {color:var(--kb-muted-foreground);border:1px solid var(--kb-border)}
.bottom-action-bar .complete-btn-inline:hover:not(:disabled) {color:var(--kb-primary);background:var(--kb-primary-soft);transform:none}
.breadcrumb-bar {flex-wrap:wrap;font-size:var(--kb-fs-body-sm)}
.chapter-tools a:focus-visible,.outline-item:focus-visible {outline:var(--kb-studio-focus) solid var(--kb-primary);outline-offset:var(--kb-space-1)}

/* 响应式：移动端隐藏右侧大纲，底部操作栏简化 */
@media (max-width: 768px) {
  .outline-sidebar {
    display: none;
  }
  .bottom-prev span,
  .bottom-next span {
    display: none;
  }
}
</style>

<style scoped>
.reading-bookmark {padding:var(--kb-space-4);margin-bottom:var(--kb-space-4);border:1px solid var(--kb-border);border-radius:var(--kb-radius-md);background:var(--kb-muted);font-size:var(--kb-fs-body-sm)}
.reading-bookmark button {margin-inline-end:var(--kb-space-2);margin-block:var(--kb-space-2)}
.reading-bookmark p {margin-block:var(--kb-space-2)}
.chapter-extract-row {display:flex;align-items:center;gap:var(--kb-space-3);flex-wrap:wrap;margin-bottom:var(--kb-space-4)}
.chapter-extract-hint {color:var(--kb-muted-foreground);font-size:var(--kb-fs-caption)}
</style>
