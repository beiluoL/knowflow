<template>
  <!-- Architecture-4.0：学习目标（Learning Goal）—— 学习闭环的起点与「为什么学」的总控台。
       目标可关联一条真实学习路径（learning_path），路径进度实时回显为目标进度；
       「AI 生成路线」复用既有 personalized-path → adopt 链路，不重复建第二套。 -->
  <div class="lg-page animate-fade-in">
    <!-- ===== 页头 ===== -->
    <div class="lg-header">
      <div>
        <h1 class="kb-h1">学习目标</h1>
        <p class="lg-subtitle">告诉系统「你为什么要学」，剩下的路线 / 进度 / 下一步交给学习闭环。</p>
      </div>
      <button class="lg-btn-primary" type="button" @click="openCreate">
        <Icon name="plus" :size="16" />
        <span>新建目标</span>
      </button>
    </div>

    <!-- ===== 当前目标（进行中）：NextAction 的上文 ===== -->
    <section v-if="activeGoal" class="lg-hero reveal-item" style="--reveal-index: 1">
      <div class="lg-hero-icon"><Icon name="target" :size="22" /></div>
      <div class="lg-hero-main">
        <div class="lg-hero-topline">
          <span class="lg-pill lg-pill-active">进行中</span>
          <span v-if="activeGoal.deadline" class="lg-hero-deadline">
            <Icon name="calendar" :size="12" />
            {{ deadlineText(activeGoal.deadline) }}
          </span>
        </div>
        <h2 class="lg-hero-title">{{ activeGoal.title }}</h2>
        <p v-if="activeGoal.description" class="lg-hero-desc">{{ activeGoal.description }}</p>
        <div v-if="activeGoal.pathId" class="lg-hero-path">
          <div class="lg-path-line">
            <Icon name="route" :size="14" />
            <span class="lg-path-name">{{ activeGoal.pathTitle || '关联路线' }}</span>
            <span v-if="activeGoal.pathChapterCount" class="lg-path-count">{{ activeGoal.pathChapterCount }} 章</span>
            <span class="lg-path-pct tabular-nums">{{ pathPercent(activeGoal) }}%</span>
          </div>
          <div class="lg-progress"><div class="lg-progress-fill" :style="{ width: pathPercent(activeGoal) + '%' }"></div></div>
        </div>
        <div v-else class="lg-hero-unlinked">
          <Icon name="route" :size="14" />
          <span>还没有关联学习路线 —— 用 AI 生成一条，或从现有路线中选择。</span>
        </div>
        <!-- Architecture-4.0-3：当前节点（真实 DAG available 节点 = 下一步该学哪一节） -->
        <div v-if="activeGoal.pathId" class="lg-hero-node">
          <template v-if="nodeLoading">
            <Icon name="sparkles" :size="13" class="spin" />
            <span>读取路线进度…</span>
          </template>
          <template v-else-if="currentNode">
            <Icon name="book-open" :size="13" />
            <span class="lg-node-label">当前节点：</span>
            <button type="button" class="lg-node-link" @click="goChapter(currentNode.id)">
              {{ currentNode.title }}
            </button>
          </template>
          <template v-else-if="pathCompleted">
            <Icon name="check-circle" :size="13" />
            <span>整条路线已完成，可以设置新的目标了 🎉</span>
          </template>
          <template v-else>
            <Icon name="route" :size="13" />
            <span>{{ nodeError ? '路线进度加载失败 ——' : '暂无可用节点（章节依赖未解锁）——' }}</span>
            <button type="button" class="lg-node-link" @click="goLearn(activeGoal)">查看路线</button>
          </template>
        </div>
      </div>
      <div class="lg-hero-actions">
        <button v-if="activeGoal.pathId" type="button" class="lg-btn-primary" @click="heroPrimaryGo(activeGoal)">
          <Icon name="play" :size="15" />
          <span>{{ heroPrimaryLabel(activeGoal) }}</span>
        </button>
        <button v-else type="button" class="lg-btn-ghost" @click="aiGenerate(activeGoal)">
          <Icon v-if="busyId === activeGoal.id" name="sparkles" :size="15" class="spin" />
          <Icon v-else name="sparkles" :size="15" />
          <span>{{ busyId === activeGoal.id ? '生成中…' : 'AI 生成路线' }}</span>
        </button>
        <button type="button" class="lg-btn-ghost" @click="openEdit(activeGoal)">
          <Icon name="pen-line" :size="15" />
          <span>编辑</span>
        </button>
        <button type="button" class="lg-btn-ghost lg-done" @click="toggleComplete(activeGoal)">
          <Icon name="check-circle" :size="15" />
          <span>标记完成</span>
        </button>
      </div>
    </section>

    <!-- 无进行中目标时的空引导 -->
    <section v-else-if="loaded" class="lg-empty reveal-item" style="--reveal-index: 1">
      <div class="lg-empty-inner">
        <Icon name="flag" :size="28" />
        <p class="lg-empty-title">还没有进行中的学习目标</p>
        <p class="lg-empty-text">例：<button type="button" class="lg-empty-sample" @click="quickCreate('7 天掌握 JVM GC')">7 天掌握 JVM GC</button>
          ·
          <button type="button" class="lg-empty-sample" @click="quickCreate('30 天掌握 Spring AI')">30 天掌握 Spring AI</button>
          ·
          <button type="button" class="lg-empty-sample" @click="quickCreate('准备 Java 高级面试')">准备 Java 高级面试</button></p>
        <button type="button" class="lg-btn-primary" @click="openCreate">
          <Icon name="plus" :size="16" />
          <span>设定我的第一个目标</span>
        </button>
      </div>
    </section>

    <!-- ===== 全部目标列表 ===== -->
    <section class="lg-list-section reveal-item" style="--reveal-index: 2">
      <div class="lg-section-head">
        <h2 class="kb-h2">全部目标</h2>
        <span class="lg-section-badge">{{ goals.length }} 个</span>
      </div>

      <div v-if="!loaded" class="lg-loading">
        <Icon name="sparkles" :size="18" class="spin" />
        <span>加载学习目标…</span>
      </div>

      <div v-else-if="goals.length === 0" class="lg-list-empty">暂无目标，从上方「新建目标」开始你的第一条学习闭环。</div>

      <div v-else class="lg-grid">
        <div v-for="g in goals" :key="g.id" class="lg-card" :class="{ 'lg-card-active': g.id === activeGoal?.id }">
          <div class="lg-card-top">
            <h3 class="lg-card-title">{{ g.title }}</h3>
            <span class="lg-pill" :class="statusPillClass(g.status)">{{ statusText(g.status) }}</span>
          </div>
          <p v-if="g.target" class="lg-card-target">
            <Icon name="check" :size="13" />
            {{ g.target }}
          </p>
          <p v-if="g.description" class="lg-card-desc">{{ g.description }}</p>
          <div v-if="g.deadline" class="lg-card-meta">
            <Icon name="calendar" :size="12" />
            <span>{{ deadlineText(g.deadline) }}</span>
          </div>

          <!-- 关联路线区 -->
          <div class="lg-card-path">
            <template v-if="g.pathId">
              <div class="lg-path-line">
                <router-link :to="`/learning/path/${g.pathId}`" class="lg-path-link">
                  <Icon name="route" :size="14" />
                  <span class="lg-path-name">{{ g.pathTitle || '路线 #' + g.pathId }}</span>
                </router-link>
                <span class="lg-path-pct tabular-nums">{{ pathPercent(g) }}%</span>
              </div>
              <div class="lg-progress"><div class="lg-progress-fill" :style="{ width: pathPercent(g) + '%' }"></div></div>
            </template>
            <div v-else class="lg-card-path-empty">
              <Icon name="route" :size="13" />
              <span>未关联路线</span>
            </div>
          </div>

          <div class="lg-card-actions">
            <button v-if="g.pathId" type="button" class="lg-act lg-act-go" @click="goLearn(g)">
              <Icon name="play" :size="13" /><span>去学习</span>
            </button>
            <button v-else type="button" class="lg-act" :disabled="busyId === g.id" @click="aiGenerate(g)">
              <Icon name="sparkles" :size="13" class="spin" v-if="busyId === g.id" />
              <Icon name="sparkles" :size="13" v-else />
              <span>{{ busyId === g.id ? '生成中…' : 'AI 生成路线' }}</span>
            </button>
            <button type="button" class="lg-act" @click="openEdit(g)">
              <Icon name="pen-line" :size="13" /><span>编辑</span>
            </button>
            <button type="button" class="lg-act" @click="toggleComplete(g)">
              <Icon name="check-circle" :size="13" />
              <span>{{ g.status === 'ACTIVE' ? '完成' : '重新开始' }}</span>
            </button>
            <button v-if="g.pathId" type="button" class="lg-act" @click="openLinkPath(g)">
              <Icon name="route" :size="13" /><span>换路线</span>
            </button>
            <button type="button" class="lg-act lg-act-danger" @click="removeGoal(g)">
              <Icon name="trash-2" :size="13" /><span>删除</span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 新建 / 编辑弹窗 ===== -->
    <div v-if="showModal" class="lg-overlay" @click.self="showModal = false">
      <div class="lg-modal">
        <div class="lg-modal-head">
          <h3 class="lg-modal-title">
            <Icon name="target" :size="17" />
            {{ editId ? '编辑学习目标' : '新建学习目标' }}
          </h3>
          <button type="button" class="lg-modal-close" @click="showModal = false"><Icon name="x" :size="16" /></button>
        </div>
        <div class="lg-modal-body">
          <label class="lg-label">目标标题 <span class="lg-req">*</span></label>
          <input v-model="form.title" type="text" class="lg-input" placeholder="如：7 天掌握 JVM GC" maxlength="100" />
          <div v-if="formError" class="lg-form-error">{{ formError }}</div>

          <label class="lg-label">达标标准（可选）</label>
          <input v-model="form.target" type="text" class="lg-input" placeholder="如：能用小白语言解释 GC 并用测验自测通过" maxlength="200" />

          <label class="lg-label">目标描述（可选）</label>
          <textarea v-model="form.description" class="lg-input lg-textarea" rows="2" placeholder="为什么学？背景…" maxlength="300"></textarea>

          <div class="lg-form-grid">
            <div>
              <label class="lg-label">截止日期（可选）</label>
              <input v-model="form.deadline" type="date" class="lg-input" />
            </div>
            <div>
              <label class="lg-label">每日投入（分钟）</label>
              <input v-model.number="form.dailyMinutes" type="number" class="lg-input" min="0" max="600" placeholder="30" />
            </div>
          </div>

          <label class="lg-label">关联学习路线（可选）</label>
          <select v-model="form.pathId" class="lg-input">
            <option :value="null">暂不关联 —— 稍后再生成 / 选择</option>
            <option v-for="p in pathOptions" :key="p.id" :value="p.id">
              {{ p.title }}（{{ p.enrolled ? '已报名 ' + Math.round(p.progress ?? 0) + '%' : '未报名' }}）
            </option>
          </select>
        </div>
        <div class="lg-modal-foot">
          <button type="button" class="lg-btn-cancel" @click="showModal = false">取消</button>
          <button type="button" class="lg-btn-primary" :disabled="saving" @click="saveGoal">
            <Icon name="check" :size="15" v-if="!saving" />
            <span>{{ saving ? '保存中…' : '保存目标' }}</span>
          </button>
        </div>
      </div>
    </div>
    <!-- ===== 换路线轻弹层 ===== -->
    <div v-if="linkGoal" class="lg-overlay" @click.self="linkGoal = null">
      <div class="lg-modal">
        <div class="lg-modal-head">
          <h3 class="lg-modal-title">
            <Icon name="route" :size="17" />
            为「{{ linkGoal.title }}」选择路线
          </h3>
          <button type="button" class="lg-modal-close" @click="linkGoal = null"><Icon name="x" :size="16" /></button>
        </div>
        <div class="lg-modal-body">
          <label class="lg-label">学习路线</label>
          <select v-model="linkSelect" class="lg-input">
            <option :value="null">解除关联 —— 不关联任何路线</option>
            <option v-for="p in pathOptions" :key="p.id" :value="p.id">
              {{ p.title }}（{{ p.enrolled ? '已报名 ' + Math.round(p.progress ?? 0) + '%' : '未报名，关联后自动报名' }}）
            </option>
          </select>
          <p class="lg-card-desc" style="margin-top: 8px;">换路线不会删除原路线的学习记录；目标进度将跟随新路线实时显示。</p>
        </div>
        <div class="lg-modal-foot">
          <button type="button" class="lg-btn-cancel" @click="linkGoal = null">取消</button>
          <button type="button" class="lg-btn-primary" :disabled="linkSaving" @click="saveLink">
            <Icon name="check" :size="15" v-if="!linkSaving" />
            <span>{{ linkSaving ? '保存中…' : '保存关联' }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api'
import { dialog } from '@/utils/dialog'
import { notify, getApiError } from '@/utils/toast'
import { resolveCurrentChapterNode, isPathFullyCompleted } from '@/learning/path-node'
import type { LearningGoalStatus, LearningGoalVO, LearningPathVO, ChapterNodeVO } from '@/api/types'

const router = useRouter()

const goals = ref<LearningGoalVO[]>([])
const pathOptions = ref<LearningPathVO[]>([])
const loaded = ref(false)
const busyId = ref<number | null>(null)

/* ============ 派生 ============ */
const activeGoal = computed(() => goals.value.find((g) => g.status === 'ACTIVE') ?? null)

function statusText(s: LearningGoalStatus): string {
  return s === 'ACTIVE' ? '进行中' : s === 'COMPLETED' ? '已完成' : '已归档'
}
function statusPillClass(s: LearningGoalStatus): string {
  return s === 'ACTIVE' ? 'lg-pill-active' : s === 'COMPLETED' ? 'lg-pill-done' : 'lg-pill-archived'
}
function pathPercent(g: LearningGoalVO): number {
  return Math.min(100, Math.max(0, Math.round(g.pathProgress ?? 0)))
}
function deadlineText(d?: string | null): string {
  if (!d) return ''
  const t = new Date(d + 'T00:00:00')
  const now = new Date()
  const dayMs = 24 * 3600 * 1000
  const diff = Math.ceil((t.getTime() - now.getTime()) / dayMs)
  const fmt = `${t.getMonth() + 1}/${t.getDate()} 截止`
  if (diff < 0) return `${fmt}（已逾期 ${-diff} 天）`
  if (diff === 0) return `${fmt}（今天）`
  return `${fmt}（剩 ${diff} 天）`
}

/* ============ Architecture-4.0-3：目标工作区 · 当前节点（真实 DAG） ============ */
const dagNodes = ref<ChapterNodeVO[]>([])
const nodeLoading = ref(false)
const nodeError = ref(false)

const currentNode = computed(() => resolveCurrentChapterNode(dagNodes.value))
const pathCompleted = computed(() => isPathFullyCompleted(dagNodes.value))

async function loadActiveGoalDag() {
  const g = activeGoal.value
  if (!g?.pathId) {
    dagNodes.value = []
    nodeError.value = false
    return
  }
  nodeLoading.value = true
  nodeError.value = false
  try {
    const dag = await learningApi.dag(g.pathId)
    dagNodes.value = dag?.nodes ?? []
  } catch {
    nodeError.value = true
    dagNodes.value = []
  } finally {
    nodeLoading.value = false
  }
}

function goChapter(chapterId: number) {
  router.push(`/learning/chapter/${chapterId}`)
}

/** 主 CTA：有当前节点 → 直接进该节；否则进路线详情 */
function heroPrimaryGo(g: LearningGoalVO) {
  if (currentNode.value) goChapter(currentNode.value.id)
  else goLearn(g)
}

function heroPrimaryLabel(g: LearningGoalVO): string {
  if (!currentNode.value) return '去学习'
  return (g.pathProgress ?? 0) > 0 ? '继续本节' : '开始本节'
}

/* ============ 数据加载 ============ */
async function load() {
  loaded.value = false
  try {
    const [goalList, paths] = await Promise.all([learningApi.goals(), learningApi.paths()])
    goals.value = goalList
    pathOptions.value = paths.filter((p) => p.enrolled || p.id != null)
  } catch (e) {
    notify('加载失败：' + getApiError(e), 'error')
  } finally {
    loaded.value = true
  }
}

function goLearn(g: LearningGoalVO) {
  if (g.pathId) router.push(`/learning/path/${g.pathId}`)
  else router.push('/learning/center')
}

/** 覆盖式提交载荷：把目标现状完整带回（后端语义：pathId=null 解除关联）。 */
function payloadOf(g: LearningGoalVO): {
  title: string
  description: string | null
  target: string | null
  status: LearningGoalStatus
  pathId: number | null
  startDate: string | null
  deadline: string | null
  dailyMinutes: number
} {
  return {
    title: g.title,
    description: g.description ?? null,
    target: g.target ?? null,
    status: g.status,
    pathId: g.pathId ?? null,
    startDate: g.startDate ?? null,
    deadline: g.deadline ?? null,
    dailyMinutes: g.dailyMinutes ?? 0,
  }
}

/* ============ 新建 / 编辑 ============ */
const showModal = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const formError = ref('')
const form = reactive<{
  title: string
  description: string
  target: string
  deadline: string
  dailyMinutes: number
  pathId: number | null
}>({ title: '', description: '', target: '', deadline: '', dailyMinutes: 30, pathId: null })

function openCreate() {
  editId.value = null
  formError.value = ''
  Object.assign(form, { title: '', description: '', target: '', deadline: '', dailyMinutes: 30, pathId: null })
  showModal.value = true
}

function openEdit(g: LearningGoalVO) {
  editId.value = g.id
  formError.value = ''
  Object.assign(form, {
    title: g.title,
    description: g.description ?? '',
    target: g.target ?? '',
    deadline: g.deadline ? g.deadline.slice(0, 10) : '',
    dailyMinutes: g.dailyMinutes ?? 30,
    pathId: g.pathId ?? null,
  })
  showModal.value = true
}

async function saveGoal() {
  const title = form.title.trim()
  if (!title) {
    formError.value = '目标标题不能为空，例如「7 天掌握 JVM GC」'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = {
      title,
      description: form.description.trim() || null,
      target: form.target.trim() || null,
      deadline: form.deadline || null,
      dailyMinutes: Math.max(0, Math.min(600, form.dailyMinutes || 0)),
      pathId: form.pathId,
    }
    if (editId.value) {
      const cur = goals.value.find((g) => g.id === editId.value)
      if (cur) await learningApi.updateGoal(cur.id, { ...payloadOf(cur), ...payload })
    } else {
      await learningApi.createGoal({ ...payload, status: 'ACTIVE' })
    }
    showModal.value = false
    notify(editId.value ? '目标已更新' : '目标已创建', 'success')
    await load()
  } catch (e) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

/** 空态示例快速创建（不带路线，保持轻量） */
async function quickCreate(title: string) {
  try {
    await learningApi.createGoal({ title, status: 'ACTIVE' })
    notify('目标已创建：' + title, 'success')
    await load()
  } catch (e) {
    notify('创建失败：' + getApiError(e), 'error')
  }
}

/* ============ 目标状态动作 ============ */
async function toggleComplete(g: LearningGoalVO) {
  const target: LearningGoalStatus = g.status === 'ACTIVE' ? 'COMPLETED' : 'ACTIVE'
  try {
    await learningApi.updateGoal(g.id, { ...payloadOf(g), status: target })
    notify(target === 'COMPLETED' ? '目标已完成，辛苦了 🎉' : '目标已重新开始', 'success')
    await load()
  } catch (e) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

async function removeGoal(g: LearningGoalVO) {
  const ok = await dialog.confirm({
    title: '删除学习目标',
    message: `确定删除「${g.title}」吗？\n仅删除目标本身，已关联的学习路径与学习记录不受影响。`,
    variant: 'danger',
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await learningApi.removeGoal(g.id)
    notify('目标已删除', 'success')
    await load()
  } catch (e) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

/* ============ 路线关联 ============ */
/** 打开换路线：基于 dialog.prompt 数字 id 不友好，改用 select —— 暂用卡片内下拉（见模板 openLinkPath 弹层） */
const linkGoal = ref<LearningGoalVO | null>(null)
const linkSelect = ref<number | null>(null)
const linkSaving = ref(false)

function openLinkPath(g: LearningGoalVO) {
  linkGoal.value = g
  linkSelect.value = g.pathId ?? null
}
async function saveLink() {
  const g = linkGoal.value
  if (!g) return
  linkSaving.value = true
  try {
    await learningApi.updateGoal(g.id, { ...payloadOf(g), pathId: linkSelect.value })
    linkGoal.value = null
    notify(linkSelect.value == null ? '已解除路线关联' : '已关联学习路线', 'success')
    await load()
  } catch (e) {
    notify('关联失败：' + getApiError(e), 'error')
  } finally {
    linkSaving.value = false
  }
}

/** AI 生成个性化路线 → 采用落地 → 关联到目标（复用既有 personalized-path / adopt 链路） */
async function aiGenerate(g: LearningGoalVO) {
  busyId.value = g.id
  try {
    const pp = await learningApi.personalizedPath({
      goal: g.title,
      level: '入门',
      dailyMinutes: g.dailyMinutes || 30,
    })
    if (!pp.id) {
      throw new Error('AI 未返回可采用的路线，请稍后重试')
    }
    const adopted = await learningApi.adoptPersonalizedPath(pp.id)
    if (!adopted?.pathId) {
      throw new Error('路线落地失败，请稍后重试')
    }
    await learningApi.updateGoal(g.id, { ...payloadOf(g), pathId: adopted.pathId })
    notify('已生成并关联学习路线，去学习吧', 'success')
    await load()
  } catch (e) {
    notify('AI 生成路线失败：' + getApiError(e), 'error')
  } finally {
    busyId.value = null
  }
}

watch(activeGoal, () => {
  void loadActiveGoalDag()
})

onMounted(() => {
  void load()
})
</script>

<style scoped>
.lg-page {
  padding: 24px;
  max-width: 1080px;
  margin: 0 auto;
  animation: pageFadeIn 0.6s ease-out;
}
@keyframes pageFadeIn {
  from { opacity: 0; }
}
.lg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.lg-subtitle {
  margin-top: 4px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
}

/* 按钮 */
.lg-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border-radius: var(--kb-radius-md);
  border: none;
  cursor: pointer;
  font-size: var(--kb-fs-body-sm);
  font-weight: var(--kb-fw-body-md);
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  transition: background 0.15s ease, opacity 0.15s ease;
}
.lg-btn-primary:hover { background: var(--kb-primary-hover); }
.lg-btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.lg-btn-cancel {
  padding: 9px 16px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  background: transparent;
}
.lg-btn-cancel:hover { background: var(--kb-bg-subtle); }
.lg-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  font-size: var(--kb-fs-caption);
  color: var(--kb-foreground);
  background: var(--kb-card);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.lg-btn-ghost:hover { border-color: var(--kb-primary); background: var(--kb-primary-subtle); color: var(--kb-primary); }
.lg-done { color: var(--kb-state-success); }
.lg-done:hover { border-color: var(--kb-state-success); background: var(--kb-danger-soft); }

/* 进行中 hero */
.lg-hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 22px;
  border-radius: var(--kb-radius-lg);
  background: var(--kb-primary-subtle);
  border: 1px solid var(--kb-primary-tint);
  margin-bottom: 22px;
}
.lg-hero-icon {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: var(--kb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-primary);
  background: var(--kb-primary-muted);
}
.lg-hero-main { flex: 1; min-width: 0; }
.lg-hero-topline { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.lg-hero-deadline { display: inline-flex; align-items: center; gap: 4px; font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.lg-hero-title { margin: 6px 0 4px; font-size: var(--kb-fs-h2); font-weight: var(--kb-fw-h2); color: var(--kb-foreground); }
.lg-hero-desc { margin: 0 0 10px; font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); }
.lg-hero-actions { display: flex; flex-wrap: wrap; gap: 8px; flex-shrink: 0; }
.lg-hero-unlinked { display: flex; align-items: center; gap: 6px; font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); }

/* 进度条 */
.lg-progress { height: 8px; border-radius: 999px; background: var(--kb-bg-subtle); overflow: hidden; }
.lg-progress-fill { height: 100%; border-radius: 999px; background: var(--kb-primary); transition: width 0.4s ease; }
.lg-path-line { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: var(--kb-fs-body-sm); }
.lg-path-name { font-weight: var(--kb-fw-body-md); color: var(--kb-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lg-path-count { color: var(--kb-muted-foreground); }
.lg-path-pct { margin-left: auto; color: var(--kb-primary); font-weight: var(--kb-fw-body-md); }

/* 空态 */
.lg-empty {
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  padding: 34px 20px;
  margin-bottom: 22px;
  text-align: center;
}
.lg-empty-inner { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--kb-muted-foreground); }
.lg-empty-title { margin: 4px 0 0; font-size: var(--kb-fs-h3); color: var(--kb-foreground); }
.lg-empty-text { font-size: var(--kb-fs-body-sm); }
.lg-empty-sample {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  color: var(--kb-primary);
  font-size: inherit;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.lg-empty-sample:hover { color: var(--kb-primary-hover); }
.lg-empty .lg-btn-primary { margin-top: 10px; }

/* 列表 */
.lg-section-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.lg-section-badge {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  background: var(--kb-bg-subtle);
  padding: 2px 10px;
  border-radius: 999px;
}
.lg-loading, .lg-list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--kb-muted-foreground);
  padding: 40px 0;
  font-size: var(--kb-fs-body-sm);
}
.lg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.lg-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 16px 18px;
  box-shadow: var(--kb-shadow-sm);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.lg-card:hover { border-color: var(--kb-neutral-border-strong); box-shadow: var(--kb-shadow-lg); }
.lg-card-active { border-color: var(--kb-primary-tint-strong); }
.lg-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.lg-card-title { margin: 0; font-size: var(--kb-fs-h4); font-weight: var(--kb-fw-h4); color: var(--kb-foreground); line-height: 1.4; }
.lg-card-target { display: flex; align-items: flex-start; gap: 6px; margin: 10px 0 0; font-size: var(--kb-fs-body-sm); color: var(--kb-state-info); }
.lg-card-desc { margin: 8px 0 0; font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); }
.lg-card-meta { display: flex; align-items: center; gap: 5px; margin-top: 8px; font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.lg-card-path { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--kb-border); }
.lg-card-path-empty { display: flex; align-items: center; gap: 6px; font-size: var(--kb-fs-caption); color: var(--kb-muted-foreground); }
.lg-path-link { display: inline-flex; align-items: center; gap: 6px; min-width: 0; text-decoration: none; }
.lg-path-link:hover .lg-path-name { color: var(--kb-primary); }
.lg-card-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; }
.lg-act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: var(--kb-radius-sm);
  border: none;
  cursor: pointer;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  background: var(--kb-bg-subtle);
  transition: color 0.15s ease, background 0.15s ease;
}
.lg-act:hover { color: var(--kb-primary); background: var(--kb-primary-subtle); }
.lg-act:disabled { opacity: 0.6; cursor: not-allowed; }
.lg-act-go { color: var(--kb-primary); background: var(--kb-primary-muted); }
.lg-act-danger:hover { color: var(--kb-danger); background: var(--kb-danger-soft); }

/* 状态 pill */
.lg-pill {
  flex-shrink: 0;
  font-size: var(--kb-fs-caption);
  padding: 2px 10px;
  border-radius: 999px;
  white-space: nowrap;
}
.lg-pill-active { color: var(--kb-primary); background: var(--kb-primary-subtle); }
.lg-pill-done { color: var(--kb-state-success); background: var(--kb-danger-soft); }
.lg-pill-archived { color: var(--kb-muted-foreground); background: var(--kb-bg-subtle); }

/* 弹窗 */
.lg-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 20px;
}
.lg-modal {
  width: 100%;
  max-width: 480px;
  max-height: 86vh;
  overflow-y: auto;
  background: var(--kb-card);
  border-radius: var(--kb-radius-lg);
  box-shadow: var(--kb-shadow-lg);
}
.lg-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}
.lg-modal-title { display: flex; align-items: center; gap: 8px; margin: 0; font-size: var(--kb-fs-h4); color: var(--kb-foreground); }
.lg-modal-close {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: var(--kb-radius-sm);
}
.lg-modal-close:hover { background: var(--kb-bg-subtle); color: var(--kb-foreground); }
.lg-modal-body { padding: 16px 20px; display: flex; flex-direction: column; gap: 6px; }
.lg-label { font-size: var(--kb-fs-caption); font-weight: var(--kb-fw-caption); color: var(--kb-muted-foreground); margin-top: 8px; }
.lg-req { color: var(--kb-danger); }
.lg-input {
  width: 100%;
  padding: 8px 12px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-input, var(--kb-card));
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
  outline: none;
  box-sizing: border-box;
}
.lg-input:focus { border-color: var(--kb-primary); box-shadow: 0 0 0 3px var(--kb-ring); }
.lg-textarea { resize: vertical; font-family: inherit; }
.lg-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.lg-form-error { color: var(--kb-danger); font-size: var(--kb-fs-caption); margin-top: 4px; }
.lg-modal-foot { display: flex; justify-content: flex-end; gap: 10px; padding: 14px 20px; border-top: 1px solid var(--kb-border); }

/* Architecture-4.0-3：当前节点行（真实 DAG available 节点） */
.lg-hero-node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.lg-hero-node svg { flex-shrink: 0; }
.lg-node-link {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-size: inherit;
  font-weight: var(--kb-fw-body-md);
  color: var(--kb-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.lg-node-link:hover { color: var(--kb-primary-hover); }

.spin { animation: lgSpin 1s linear infinite; }
@keyframes lgSpin { to { transform: rotate(360deg); } }

@media (max-width: 640px) {
  .lg-hero { flex-direction: column; }
  .lg-form-grid { grid-template-columns: 1fr; }
}
</style>
