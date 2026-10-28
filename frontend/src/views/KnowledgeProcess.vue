<script setup lang="ts">
/**
 * Architecture-4.3：Knowledge Processing 2.0 页面（AI 整理资料）。
 *
 * 数据流：资料(doc) → POST /api/knowledge/process（AI 抽取+校验，零写库）→ 候选审核
 *   → POST /api/knowledge/process/accept（确定性落库 Knowledge Core + 可选关联学习节点）
 *   → 学习活动候选（Feynman / 第一性原理 / 场景 / 闪卡，复用 4.2 深链，零 AI）。
 *
 * Vue 页面只负责编排 API，不承担任何 prompt / JSON 解析 / 校验逻辑（§34）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { docsApi } from '@/api/docs'
import { learningApi } from '@/api/learning'
import { knowledgeApi } from '@/api/knowledge'
import { appendChapterQuery, appendKnowledgeQuery } from '@/learning/route'
import type {
  DocVO,
  LearningPathVO,
  LearningChapterVO,
  KnowledgeProcessConceptCandidate,
  KnowledgeProcessResultVO,
  KnowledgeAcceptResultVO,
  KnowledgeAcceptedKnowledge,
} from '@/api/types'

const router = useRouter()
const route = useRoute()

// ===== 资料来源 =====
// UI/UX 5.0 §十二：支持 /knowledge/process?source=paste 直达「粘贴新资料」（知识库导入下拉的第二入口）
const sourceTab = ref<'existing' | 'paste'>(route.query.source === 'paste' ? 'paste' : 'existing')
const keyword = ref('')
const docResults = ref<DocVO[]>([])
const searching = ref(false)
const selectedDoc = ref<DocVO | null>(null)
const pasteTitle = ref('')
const pasteContent = ref('')
const creatingPaste = ref(false)

const searchDocs = async () => {
  if (!keyword.value.trim()) return
  searching.value = true
  try {
    const page = await docsApi.list({ keyword: keyword.value.trim(), pageNum: 1, pageSize: 10 })
    docResults.value = page.records ?? []
  } catch {
    docResults.value = []
  } finally {
    searching.value = false
  }
}

const createPasteDoc = async () => {
  if (!pasteTitle.value.trim()) return
  if (!pasteContent.value.trim()) return
  creatingPaste.value = true
  try {
    const doc = await docsApi.create({ title: pasteTitle.value.trim(), content: pasteContent.value })
    selectedDoc.value = doc
    sourceTab.value = 'existing'
  } catch (e: unknown) {
    errorMsg.value = msgOf(e) || '新建资料失败'
  } finally {
    creatingPaste.value = false
  }
}

// ===== 关联学习节点（可选）=====
const paths = ref<LearningPathVO[]>([])
const selectedPathId = ref<number | null>(null)
const chapters = ref<LearningChapterVO[]>([])
const selectedChapterId = ref<number | null>(null)
const chaptersLoading = ref(false)

const loadPaths = async () => {
  try {
    paths.value = await learningApi.paths()
  } catch {
    paths.value = []
  }
}
const onPathChange = (raw: string) => {
  const n = raw ? Number(raw) : null
  selectedPathId.value = n
  selectedChapterId.value = null
  chapters.value = []
  if (n) loadChapters(n)
}
const loadChapters = async (pathId: number) => {
  chaptersLoading.value = true
  selectedChapterId.value = null
  try {
    const list = await learningApi.chapters(pathId)
    chapters.value = list ?? []
  } catch {
    chapters.value = []
  } finally {
    chaptersLoading.value = false
  }
}

// ===== 处理状态 =====
const mode = ref<'KNOWLEDGE_EXTRACTION' | 'SUMMARY'>('KNOWLEDGE_EXTRACTION')
const running = ref(false)
const errorMsg = ref('')
const result = ref<KnowledgeProcessResultVO | null>(null)
const accepted = ref<KnowledgeAcceptResultVO | null>(null)

const msgOf = (e: unknown): string => {
  const err = e as { response?: { data?: { message?: string } }; message?: string }
  return err?.response?.data?.message || err?.message || ''
}

const canRun = computed(
  () => !!selectedDoc.value && !running.value && !creatingPaste.value,
)

const run = async () => {
  if (!selectedDoc.value || running.value) return
  running.value = true
  errorMsg.value = ''
  accepted.value = null
  result.value = null
  try {
    result.value = await knowledgeApi.processMaterial({
      docId: selectedDoc.value.id,
      learningNodeId: selectedChapterId.value ?? null,
      mode: mode.value,
    })
    // 新结果 → 工作集重建 + 默认勾选（NEW/EXISTING 均默认接受，用户可取消）
    keepIds.value = new Set(
      result.value.concepts
        .filter((c) => c.status === 'NEW' || c.status === 'EXISTING')
        .map((c) => c.candidateId),
    )
    edits.value = new Map(result.value.concepts.map((c) => [c.candidateId, c.name]))
  } catch (e: unknown) {
    errorMsg.value = msgOf(e) || 'AI 处理失败，请稍后重试'
  } finally {
    running.value = false
  }
}

// ===== 候选工作集（勾选 / 改名 / 改类型；EXISTING 复用锁定）=====
const keepIds = ref<Set<string>>(new Set())
const edits = ref<Map<string, string>>(new Map())
const typeSel = ref<Map<string, string>>(new Map())

const TYPE_OPTIONS = ['CONCEPT', 'PRINCIPLE', 'TERM', 'TECHNIQUE', 'TOOL', 'OTHER'] as const

/** 概念类型当前选择（AI 默认，可编辑；EXISTING 复用实体不改）。 */
const typeOf = (c: KnowledgeProcessConceptCandidate): string => typeSel.value.get(c.candidateId) ?? c.type

const toggleKeep = (id: string) => {
  const next = new Set(keepIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  keepIds.value = next
}

const keepAll = (v: boolean) => {
  const list = result.value?.concepts ?? []
  const next = new Set<string>()
  if (v) list.forEach((c) => next.add(c.candidateId))
  keepIds.value = next
}

const conceptKeptCount = computed(() => keepIds.value.size)

const effectiveName = (c: KnowledgeProcessConceptCandidate): string =>
  c.status === 'EXISTING' && c.matchedName ? c.matchedName : (edits.value.get(c.candidateId) ?? c.name)

const editableConcept = (c: KnowledgeProcessConceptCandidate): boolean => c.status === 'NEW'

const droppedNormNames = computed(() => {
  const dropped = new Set<string>()
  for (const c of result.value?.concepts ?? []) {
    if (!keepIds.value.has(c.candidateId)) dropped.add(effectiveName(c).trim().toLowerCase())
  }
  return dropped
})

const selectableRelations = computed(() =>
  (result.value?.relations ?? []).filter(
    (r) =>
      r.status === 'VALID' &&
      !droppedNormNames.value.has(r.source.trim().toLowerCase()) &&
      !droppedNormNames.value.has(r.target.trim().toLowerCase()),
  ),
)

const acceptSelected = async () => {
  if (!result.value || !selectedDoc.value || running.value) return
  const concepts = result.value.concepts.filter((c) => keepIds.value.has(c.candidateId))
  // 概念改名 → 关系端点名同步（基于候选原始名映射到当前名）
  const renameMap = new Map<string, string>()
  for (const c of concepts) {
    const base = c.status === 'EXISTING' && c.matchedName ? c.matchedName : c.name
    const cur = effectiveName(c).trim() || base
    if (base.trim().toLowerCase() !== cur.toLowerCase()) renameMap.set(base.trim().toLowerCase(), cur)
  }
  const remap = (n: string) => renameMap.get(n.trim().toLowerCase()) ?? n.trim()
  const relations = selectableRelations.value.map((r) => ({
    source: remap(r.source),
    target: remap(r.target),
    relation: r.relation,
    description: r.description ?? '',
  }))
  running.value = true
  errorMsg.value = ''
  try {
    accepted.value = await knowledgeApi.acceptProcess({
      docId: selectedDoc.value.id,
      learningNodeId: selectedChapterId.value ?? null,
      concepts: concepts.map((c) => ({
        name: effectiveName(c),
        type: typeOf(c),
        description: c.description ?? '',
      })),
      relations,
    })
  } catch (e: unknown) {
    errorMsg.value = msgOf(e) || '接受失败，请重试'
  } finally {
    running.value = false
  }
}

// ===== 学习活动候选（4.2 深链，接受成功后生成；零 AI）=====
const activityLinks = (k: KnowledgeAcceptedKnowledge) => {
  const base: { label: string; icon: string; path: string }[] = []
  const common = appendKnowledgeQuery('/learning/feynman', k.knowledgeId, k.name)
  if (common !== '/learning/feynman') {
    base.push({ label: '费曼讲解', icon: 'message-square', path: appendKnowledgeQuery('/learning/feynman', k.knowledgeId, k.name) })
    base.push({ label: '第一性原理', icon: 'blocks', path: appendKnowledgeQuery('/learning/first-principles', k.knowledgeId, k.name) })
    base.push({ label: '场景练习', icon: 'flask-conical', path: appendKnowledgeQuery('/learning/scenario', k.knowledgeId, k.name) })
  }
  return base
}
const nodeFlashcardLink = computed(() =>
  accepted.value?.nodeLinked && accepted.value.chapterId
    ? appendChapterQuery('/learning/flashcards', accepted.value.chapterId)
    : null,
)

const go = (path: string) => {
  router.push(path)
}

const REL_LABEL: Record<string, string> = {
  PREREQUISITE: '前置',
  PART_OF: '组成',
  IS_A: '是一种',
  USES: '使用',
  CONTRASTS: '对比',
  RELATED_TO: '相关',
}

onMounted(() => {
  loadPaths()
  docsApi.list({ pageNum: 1, pageSize: 6, sort: 'time' }).then((p) => {
    docResults.value = p.records ?? []
  }).catch(() => {})
})
</script>

<template>
  <div class="animate-fade-in kp-page">
    <div class="kp-head">
      <button class="kp-back" type="button" aria-label="返回上一页" @click="router.back()">
        <Icon name="arrow-left" :size="16" />
      </button>
      <div class="kp-head-text">
        <h1 class="kp-title">AI 整理资料</h1>
        <p class="kp-subtitle">
          把资料加工成可学习的知识结构。AI 只负责提取与理解，确认后才写入知识库 —— 不会自动污染你的知识核心。
        </p>
      </div>
    </div>

    <!-- ===== 步骤 1：选择 / 新建资料 ===== -->
    <section class="kp-card">
      <div class="kp-card-title">
        <span class="kp-step">1</span> 选择资料
        <span class="kp-hint">（支持知识库已有文档，或粘贴正文新建）</span>
      </div>

      <div class="kp-tabs">
        <button type="button" class="kp-tab" :class="{ active: sourceTab === 'existing' }" @click="sourceTab = 'existing'">
          已有文档
        </button>
        <button type="button" class="kp-tab" :class="{ active: sourceTab === 'paste' }" @click="sourceTab = 'paste'">
          粘贴新资料
        </button>
      </div>

      <div v-if="sourceTab === 'existing'">
        <div class="kp-search-row">
          <div class="kp-search-input-wrap">
            <Icon name="search" :size="15" class="kp-search-ic" />
            <input
              v-model="keyword"
              type="text"
              placeholder="输入标题关键词检索文档（回车搜索）"
              class="kp-input kf-input kf-form-control"
              @keyup.enter="searchDocs"
            />
          </div>
          <button type="button" class="kp-btn" :disabled="searching || !keyword.trim()" @click="searchDocs">
            <Icon name="search" :size="14" />
            {{ searching ? '检索中…' : '检索' }}
          </button>
        </div>

        <div v-if="docResults.length > 0" class="kp-doc-list">
          <button
            v-for="d in docResults"
            :key="d.id"
            type="button"
            class="kp-doc-item"
            :class="{ active: selectedDoc?.id === d.id }"
            @click="selectedDoc = d"
          >
            <Icon name="file-text" :size="15" class="kp-doc-ic" />
            <span class="kp-doc-title">{{ d.title }}</span>
            <span v-if="selectedDoc?.id === d.id" class="kp-doc-check"><Icon name="check" :size="13" /></span>
          </button>
        </div>
        <div v-else-if="!searching" class="kp-empty">暂无匹配文档，可切换到「粘贴新资料」直接创建文本资料</div>
      </div>

      <div v-else>
        <input v-model="pasteTitle" type="text" placeholder="资料标题（如：JVM GC 原理）" class="kp-input kp-mb kf-input kf-form-control" />
        <textarea
          v-model="pasteContent"
          rows="7"
          placeholder="粘贴学习资料正文（Markdown / 纯文本均可）。示例：MVCC 通过 Undo Log 与 Read View 实现快照读…"
          class="kp-input kp-area kf-textarea kf-form-control"
        ></textarea>
        <button
          type="button"
          class="kp-btn kp-btn-primary"
          :disabled="creatingPaste || !pasteTitle.trim() || !pasteContent.trim()"
          @click="createPasteDoc"
        >
          <Icon name="plus" :size="14" />
          {{ creatingPaste ? '创建中…' : '创建资料并选择' }}
        </button>
        <div v-if="selectedDoc && pasteTitle.trim() === selectedDoc.title" class="kp-okline">
          <Icon name="check-circle" :size="14" /> 已创建并选中：{{ selectedDoc.title }}
        </div>
      </div>

      <div v-if="selectedDoc" class="kp-selected">
        <Icon name="file-text" :size="15" />
        当前资料：<b>{{ selectedDoc.title }}</b>
      </div>
    </section>

    <!-- ===== 步骤 2：关联学习节点 + 处理模式 ===== -->
    <section class="kp-card">
      <div class="kp-card-title">
        <span class="kp-step">2</span> 关联学习节点（可选）
        <span class="kp-hint">接受后文档会挂到该章节，打通「学习节点 → 知识」</span>
      </div>
      <div class="kp-node-row">
        <select
          class="kp-input kp-select kf-select kf-form-control"
          :value="selectedPathId ?? ''"
          @change="onPathChange(($event.target as HTMLSelectElement).value)"
        >
          <option value="">不关联学习路径</option>
          <option v-for="p in paths" :key="p.id" :value="p.id">{{ p.title }}</option>
        </select>
        <select
          class="kp-input kp-select kf-select kf-form-control"
          :value="selectedChapterId ?? ''"
          :disabled="!selectedPathId || chaptersLoading"
          @change="selectedChapterId = ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null"
        >
          <option value="">{{ !selectedPathId ? '先选择学习路径' : chaptersLoading ? '加载章节…' : '选择该路径下的章节' }}</option>
          <option v-for="c in chapters" :key="c.id" :value="c.id">{{ c.title }}</option>
        </select>
      </div>

      <div class="kp-card-title kp-mt">
        <span class="kp-step">2.5</span> 处理模式
      </div>
      <div class="kp-mode-row">
        <button
          type="button"
          class="kp-mode"
          :class="{ active: mode === 'KNOWLEDGE_EXTRACTION' }"
          @click="mode = 'KNOWLEDGE_EXTRACTION'"
        >
          <b>知识提取</b>
          <span>摘要 + 概念 + 关系 + 前置候选（可学习）</span>
        </button>
        <button
          type="button"
          class="kp-mode"
          :class="{ active: mode === 'SUMMARY' }"
          @click="mode = 'SUMMARY'"
        >
          <b>仅摘要</b>
          <span>只生成面向人的摘要（不产出知识候选）</span>
        </button>
      </div>
    </section>

    <!-- ===== 处理入口 / 错误 ===== -->
    <div v-if="errorMsg" class="kp-error">
      <div class="kp-error-head"><Icon name="info" :size="16" /> 处理失败</div>
      <p class="kp-error-msg">{{ errorMsg }}</p>
      <button type="button" class="kp-btn" @click="run"><Icon name="rotate-ccw" :size="14" /> 重新处理</button>
    </div>

    <div class="kp-run-row">
      <button
        type="button"
        class="kp-btn kp-btn-primary kp-run"
        :disabled="!canRun"
        @click="run"
      >
        <Icon name="sparkles" :size="15" />
        {{ running ? (mode === 'SUMMARY' ? 'AI 正在总结…' : 'AI 正在整理知识…') : '开始 AI 整理' }}
      </button>
      <button v-if="result && mode === 'KNOWLEDGE_EXTRACTION'" type="button" class="kp-btn" :disabled="running" @click="run">
        <Icon name="refresh-cw" :size="14" /> 重新处理
      </button>
    </div>

    <!-- ===== 步骤 3：候选审核（KNOWLEDGE_EXTRACTION）===== -->
    <template v-if="result && mode === 'KNOWLEDGE_EXTRACTION'">
      <section class="kp-card">
        <div class="kp-card-title">
          <span class="kp-step">3</span> AI 提取结果
          <span class="kp-hint">摘要给人看，知识结构给学习引擎用 —— 二者独立（§23）</span>
        </div>

        <!-- 摘要 -->
        <div v-if="result.summary" class="kp-summary">
          <div class="kp-summary-label"><Icon name="align-left" :size="13" /> 摘要</div>
          <p>{{ result.summary }}</p>
        </div>

        <!-- 统计 -->
        <div class="kp-stat-row">
          <span class="kp-stat good">提取概念 {{ result.conceptTotal }}</span>
          <span class="kp-stat new">新增 {{ result.conceptNew }}</span>
          <span class="kp-stat exist">已有 {{ result.conceptExisting }}</span>
          <span class="kp-stat warn">无效 {{ result.conceptInvalid }}</span>
          <span class="kp-stat good">关系 {{ result.relationValid }}</span>
          <span class="kp-stat warn">无效关系 {{ result.relationInvalid }}</span>
          <span class="kp-stat meta">{{ result.contentTruncated ? '内容过长已截断' : '全文已处理' }}</span>
          <span class="kp-stat meta">{{ Math.round((result.processingTimeMs ?? 0) / 1000) }}s · {{ result.promptVersion }}</span>
        </div>
        <div v-if="result.warnings.length > 0" class="kp-warnings">
          <p v-for="(w, i) in result.warnings" :key="i" class="kp-warn-line"><Icon name="info" :size="13" /> {{ w }}</p>
        </div>

        <!-- 概念候选 -->
        <div class="kp-cand-head">
          <div class="kp-sub-head">知识点候选</div>
          <div class="kp-cand-actions">
            <button type="button" class="kp-link-btn" @click="keepAll(true)">全选</button>
            <button type="button" class="kp-link-btn" @click="keepAll(false)">清空</button>
          </div>
        </div>
        <div class="kp-cand-list">
          <div
            v-for="c in result.concepts"
            :key="c.candidateId"
            class="kp-cand-item"
            :class="{ off: !keepIds.has(c.candidateId) }"
          >
            <button
              type="button"
              class="kp-check"
              :class="{ on: keepIds.has(c.candidateId) }"
              :aria-label="`${keepIds.has(c.candidateId) ? '取消接受' : '接受'}候选：${c.name}`"
              :aria-pressed="keepIds.has(c.candidateId)"
              @click="toggleKeep(c.candidateId)"
            >
              <Icon v-if="keepIds.has(c.candidateId)" name="check" :size="12" />
            </button>
            <div class="kp-cand-main">
              <div class="kp-cand-line1">
                <span v-if="c.status === 'EXISTING'" class="kp-badge exist" title="库中已有同名知识，接受即复用，不重复建实体">
                  已有 · 复用 {{ c.matchedName ? `「${c.matchedName}」` : '' }}
                </span>
                <span v-else class="kp-badge new">新增</span>
                <input
                  :value="edits.get(c.candidateId) ?? c.name"
                  class="kp-name-input kf-input kf-form-control"
                  :class="{ locked: !editableConcept(c) }"
                  :readonly="!editableConcept(c)"
                  @input="c.status === 'NEW' && edits.set(c.candidateId, ($event.target as HTMLInputElement).value)"
                />
                <select
                  :value="typeOf(c)"
                  class="kp-type-select kf-select kf-form-control"
                  :disabled="c.status === 'EXISTING'"
                  @change="typeSel.set(c.candidateId, ($event.target as HTMLSelectElement).value)"
                >
                  <option v-for="t in TYPE_OPTIONS" :key="t" :value="t">{{ t }}</option>
                </select>
              </div>
              <p class="kp-cand-desc">{{ c.description || '（无说明）' }}</p>
            </div>
          </div>
        </div>
        <div v-if="result.concepts.length === 0" class="kp-empty">未提取到任何知识候选，可「重新处理」或更换资料</div>

        <!-- 关系候选 -->
        <div v-if="selectableRelations.length > 0" class="kp-rel-block">
          <div class="kp-sub-head">关系 / 前置候选</div>
          <p class="kp-rel-note">接受后写入知识图谱；PREREQUISITE 方向的 source 是学习 target 前应掌握的前置知识。</p>
          <div class="kp-rel-list">
            <div v-for="r in selectableRelations" :key="r.candidateId" class="kp-rel-item">
              <span class="kp-rel-node">{{ r.source }}</span>
              <span class="kp-rel-arrow" :class="{ pre: r.relation === 'PREREQUISITE' }">
                {{ r.relation === 'PREREQUISITE' ? '◀前置' : `—${REL_LABEL[r.relation] ?? r.relation}→` }}
              </span>
              <span class="kp-rel-node">{{ r.target }}</span>
            </div>
          </div>
        </div>
        <div v-if="result.relations.filter((r) => r.status === 'INVALID').length > 0" class="kp-invalid-note">
          已忽略 {{ result.relations.filter((r) => r.status === 'INVALID').length }} 条无效关系（未知引用 / 自环 / 重复）。
        </div>

        <button
          type="button"
          class="kp-btn kp-btn-primary kp-accept"
          :disabled="running || conceptKeptCount === 0"
          @click="acceptSelected"
        >
          <Icon name="check-circle" :size="15" />
          {{ running ? '提交中…' : `接受所选知识（${conceptKeptCount}）进入知识库` }}
        </button>
        <p class="kp-accept-note">
          接受的规则：同名知识自动复用（不重复建实体），新建实体写入 Knowledge Core，并建立 DOC→知识 的 ACCEPTED 映射
          （学习引擎 4.2 章节知识链路由此消费）。
        </p>
      </section>
    </template>

    <!-- SUMMARY 结果 -->
    <section v-if="result && mode === 'SUMMARY'" class="kp-card">
      <div class="kp-card-title">
        <span class="kp-step">✓</span> AI 摘要
        <span class="kp-hint">只读结果，不写入文档（如需回填文档摘要请用文档详情的 AI 摘要）</span>
      </div>
      <p class="kp-summary-text">{{ result.summary || '（未返回摘要）' }}</p>
      <div class="kp-stat-row">
        <span class="kp-stat meta">{{ Math.round((result.processingTimeMs ?? 0) / 1000) }}s · {{ result.promptVersion }}</span>
      </div>
    </section>

    <!-- ===== 步骤 4：接受成功 → 已进入 Knowledge Core + 学习活动候选 ===== -->
    <section v-if="accepted && accepted.accepted.length > 0" class="kp-card kp-done">
      <div class="kp-card-title">
        <span class="kp-step ok">✓</span> 已进入知识库
        <span class="kp-hint">Knowledge Core：实体 / 关系 / DOC 映射均来自确定性规则，AI 未直接写库</span>
      </div>
      <div class="kp-stat-row">
        <span class="kp-stat new">新增 {{ accepted.entityCreated }}</span>
        <span class="kp-stat exist">复用 {{ accepted.entityReused }}</span>
        <span class="kp-stat good">关系 {{ accepted.relationsAdded }}</span>
        <span class="kp-stat good">映射 {{ accepted.mappingsCreated }}</span>
        <span class="kp-stat meta">{{ accepted.nodeLinked && accepted.learningNodeTitle ? `已关联章节：${accepted.learningNodeTitle}` : '未关联学习节点' }}</span>
      </div>

      <div class="kp-sub-head">学习活动候选</div>
      <p class="kp-rel-note">这些是「候选」：选择一种方法开始学习，不会自动执行（§42）。</p>
      <div class="kp-activity">
        <div v-for="k in accepted.accepted" :key="k.knowledgeId" class="kp-activity-row">
          <div class="kp-activity-k">
            <Icon name="brain" :size="14" />
            <b>{{ k.name }}</b>
            <span v-if="k.created" class="kp-badge new">新</span>
            <span v-else class="kp-badge exist">复用</span>
          </div>
          <div class="kp-activity-links">
            <button
              v-for="l in activityLinks(k)"
              :key="l.label"
              type="button"
              class="kp-link-chip"
              @click="go(l.path)"
            >
              <Icon :name="l.icon" :size="13" /> {{ l.label }}
            </button>
            <button
              v-if="nodeFlashcardLink"
              type="button"
              class="kp-link-chip"
              @click="go(nodeFlashcardLink)"
            >
              <Icon name="layers" :size="13" /> 本章节闪卡
            </button>
          </div>
        </div>
      </div>
    </section>

    <p v-if="!selectedDoc" class="kp-footnote">先选择或新建一份资料，再开始 AI 整理。</p>
  </div>
</template>

<style scoped>
.kp-page {
  max-width: none;
  margin-inline: 0;
  padding: 24px 20px 80px;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-14);

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}
.kp-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}
.kp-back {
  width: 34px;
  height: 34px;
  border-radius: var(--kf-radius-md);;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--kf-muted-foreground);
  transition: all 0.15s ease;
}
.kp-back:hover {
  color: var(--kf-primary);
  border-color: var(--kf-primary);
}
.kp-title {
  font-size: var(--kf-fs-20);
  font-weight: 700;
  margin: 0;
}
.kp-subtitle {
  margin: 4px 0 0;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-13);
}
.kp-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);;
  padding: 16px 18px;
  margin-bottom: 14px;
}
.kp-card-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;
  font-size: var(--kf-fs-14);
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.kp-step {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kf-primary);
  font-size: var(--kf-fs-12);
  align-items: center;
  justify-content: center;
  transform: translateY(2px);
}
.kp-step.ok {
  background: rgba(16, 185, 129, 0.12);
  color: #0f9d6e;
}
.kp-hint {
  font-weight: 400;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-12);
}
.kp-mt {
  margin-top: 16px;
}
.kp-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.kp-tab {
  padding: 6px 14px;
  border-radius: var(--kf-radius-md);;
  font-size: var(--kf-fs-13);
  border: 1px solid var(--kf-border);
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.kp-tab.active {
  background: rgba(59, 111, 224, 0.08);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  font-weight: 600;
}
.kp-search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.kp-search-input-wrap {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
}
.kp-search-ic {
  position: absolute;
  left: 10px;
  color: var(--kf-muted-foreground);
}
.kp-input:not(.kf-form-control) {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  outline: none;
  background: var(--kf-card);
  transition: border-color 0.15s ease;
}
.kp-input {
  width: 100%;;
  padding: 8px 10px 8px 32px;
  font-size: var(--kf-fs-13);
  color: var(--kf-foreground);
}
.kp-input:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
}
.kp-mb {
  margin-bottom: 10px;
}
.kp-area:not(.kf-form-control) {
  min-height: 120px;
}
.kp-area {
  resize: vertical;
  line-height: 1.6;
  min-height: 120px;
}
.kp-select {
  padding-left: 10px;
}
.kp-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--kf-radius-md);;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-13);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}
.kp-btn:hover:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.kp-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.kp-btn-primary {
  background: var(--kf-primary);
  border-color: var(--kf-primary);
  color: var(--kf-primary-foreground, #fff);
}
.kp-btn-primary:hover:not(:disabled) {
  color: #fff;
  opacity: 0.9;
}
.kp-doc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 260px;
  overflow: auto;
}
.kp-doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--kf-radius-md);;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}
.kp-doc-item:hover {
  border-color: var(--kf-primary);
}
.kp-doc-item.active {
  border-color: var(--kf-primary);
  background: rgba(59, 111, 224, 0.06);
}
.kp-doc-ic {
  color: var(--kf-muted-foreground);
  flex-shrink: 0;
}
.kp-doc-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--kf-fs-13);
}
.kp-doc-check {
  color: var(--kf-primary);
  display: inline-flex;
}
.kp-empty {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-13);
  padding: 14px 0;
  text-align: center;
}
.kp-okline {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: #0f9d6e;
  font-size: var(--kf-fs-13);
}
.kp-selected {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: var(--kf-radius-md);;
  background: rgba(59, 111, 224, 0.05);
  color: var(--kf-primary);
  font-size: var(--kf-fs-13);
}
.kp-node-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.kp-node-row .kp-input {
  width: 300px;
}
.kp-mode-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.kp-mode {
  flex: 1;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  padding: 10px 12px;
  border-radius: var(--kf-radius-md);;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  font-size: var(--kf-fs-13);
  color: var(--kf-muted-foreground);
  transition: all 0.15s ease;
}
.kp-mode.active {
  border-color: var(--kf-primary);
  color: var(--kf-foreground);
  background: rgba(59, 111, 224, 0.05);
}
.kp-error {
  border: 1px solid rgba(239, 68, 68, 0.4);
  background: rgba(239, 68, 68, 0.05);
  border-radius: var(--kf-radius-md);;
  padding: 12px 14px;
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.kp-error-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #dc2626;
}
.kp-error-msg {
  margin: 0;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-13);
}
.kp-run-row {
  display: flex;
  gap: 10px;
  margin: 4px 0 16px;
}
.kp-run {
  height: 42px;
  padding: 0 22px;
  font-size: var(--kf-fs-14);
}
.kp-summary {
  border-left: 3px solid var(--kf-primary);
  background: rgba(59, 111, 224, 0.04);
  border-radius: 0 8px 8px 0;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.kp-summary-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kf-primary);
  font-weight: 600;
  font-size: var(--kf-fs-12);
  margin-bottom: 4px;
}
.kp-summary p,
.kp-summary-text {
  margin: 0;
  line-height: 1.7;
  font-size: var(--kf-fs-13);
  color: var(--kf-foreground);
}
.kp-summary-text {
  padding: 6px 0;
}
.kp-stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 12px;
}
.kp-stat {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: var(--kf-fs-12);
  font-weight: 600;
}
.kp-stat.good {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
}
.kp-stat.new {
  background: rgba(16, 185, 129, 0.1);
  color: #0f9d6e;
}
.kp-stat.exist {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}
.kp-stat.warn {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}
.kp-stat.meta {
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-weight: 400;
}
.kp-warnings {
  margin-bottom: 10px;
}
.kp-warn-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 3px 0;
  color: #b45309;
  font-size: var(--kf-fs-12);
}
.kp-cand-head,
.kp-sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: var(--kf-fs-13);
  margin-bottom: 8px;
}
.kp-cand-actions {
  display: flex;
  gap: 8px;
}
.kp-link-btn {
  border: none;
  background: none;
  color: var(--kf-primary);
  font-size: var(--kf-fs-12);
  cursor: pointer;
  padding: 2px 4px;
}
.kp-cand-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kp-cand-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);;
  transition: opacity 0.15s ease;
}
.kp-cand-item.off {
  opacity: 0.45;
}
.kp-check {
  width: 20px;
  height: 20px;
  border-radius: var(--kf-radius-sm);;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-top: 2px;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.kp-check.on {
  background: var(--kf-primary);
  border-color: var(--kf-primary);
}
.kp-cand-main {
  flex: 1;
  min-width: 0;
}
.kp-cand-line1 {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.kp-badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: var(--kf-fs-11);
  font-weight: 600;
  white-space: nowrap;
}
.kp-badge.new {
  background: rgba(16, 185, 129, 0.12);
  color: #0f9d6e;
}
.kp-badge.exist {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}
.kp-name-input:not(.kf-form-control) {
  border: 1px solid transparent;
  background: transparent;
  outline: none;
  border-radius: var(--kf-radius-sm);;
  transition: all 0.15s ease;
}
.kp-name-input {
  flex: 1;
  min-width: 120px;
  font-size: var(--kf-fs-14);
  font-weight: 600;
  color: var(--kf-foreground);
  padding: 2px 4px;
}
.kp-name-input:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
  background: var(--kf-card);
}
.kp-name-input.locked {
  color: var(--kf-foreground);
}
.kp-type-select:not(.kf-form-control) {
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  border-radius: var(--kf-radius-sm);
}
.kp-type-select {;
  font-size: var(--kf-fs-11);
  color: var(--kf-muted-foreground);
  padding: 2px 4px;
}
.kp-cand-desc {
  margin: 4px 2px 0;
  font-size: var(--kf-fs-12);
  color: var(--kf-muted-foreground);
  line-height: 1.5;
}
.kp-rel-block {
  margin-top: 16px;
}
.kp-rel-note {
  margin: 0 0 8px;
  font-size: var(--kf-fs-12);
  color: var(--kf-muted-foreground);
}
.kp-rel-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.kp-rel-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 10px;
  border-radius: var(--kf-radius-md);;
  background: var(--kf-muted);
  font-size: var(--kf-fs-13);
}
.kp-rel-node {
  font-weight: 600;
}
.kp-rel-arrow {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-12);
}
.kp-rel-arrow.pre {
  color: var(--kf-primary);
  font-weight: 600;
}
.kp-invalid-note {
  margin-top: 8px;
  font-size: var(--kf-fs-12);
  color: var(--kf-muted-foreground);
}
.kp-accept {
  margin-top: 16px;
  width: 100%;
  justify-content: center;
  height: 42px;
}
.kp-accept-note {
  margin: 8px 0 0;
  font-size: var(--kf-fs-12);
  color: var(--kf-muted-foreground);
}
.kp-done {
  border-color: rgba(16, 185, 129, 0.35);
}
.kp-activity {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kp-activity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);;
}
.kp-activity-k {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.kp-activity-links {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.kp-link-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-12);
  cursor: pointer;
  transition: all 0.15s ease;
}
.kp-link-chip:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.kp-footnote {
  text-align: center;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-13);
  margin-top: 30px;
}
</style>
