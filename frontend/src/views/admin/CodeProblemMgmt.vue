<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="text-xl font-bold text-foreground">算法题库管理（CODE）</h1>
        <p class="text-xs text-muted-foreground mt-1">
          题目创建 / 用例可见性 / 人工审核 / 发布下架。隐藏用例与参考解答仅 ADMIN 可见；AI 候选题必须审核后才能进入正式题库。
        </p>
      </div>
      <button
        class="px-4 py-2 rounded-lg text-sm bg-primary-500 text-white hover:bg-primary-600 inline-flex items-center gap-1.5"
        @click="createNew"
      >
        <Icon name="plus" :size="15" /> 新建算法题
      </button>
    </div>

    <!-- 筛选 -->
    <div class="rounded-xl border border-border bg-card p-4 flex flex-wrap items-center gap-3">
      <div class="relative">
        <Icon name="search" :size="15" class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input
          v-model="filters.keyword"
          type="text"
          placeholder="标题 / canonicalKey"
          class="w-56 pl-9 pr-3 py-2 text-sm outline-none kf-input kf-form-control"
          @keyup.enter="reload(1)"
        />
      </div>
      <select
        v-model="filters.reviewStatus"
        class="px-3 py-2 text-sm outline-none kf-select kf-form-control"
        @change="reload(1)"
      >
        <option value="">全部审核状态</option>
        <option value="PENDING_REVIEW">待审核</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <select
        v-model.number="filters.status"
        class="px-3 py-2 text-sm outline-none kf-select kf-form-control"
        @change="reload(1)"
      >
        <option :value="-1">全部上下架</option>
        <option :value="0">草稿/下架</option>
        <option :value="1">已发布</option>
      </select>
      <button
        class="px-3 py-2 rounded-lg text-sm border border-border hover:border-primary-300 inline-flex items-center gap-1.5"
        @click="reload(filters.page)"
      >
        <Icon name="refresh-cw" :size="14" /> 刷新
      </button>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ pageData?.total ?? 0 }} 题</span>
    </div>

    <!-- 列表 -->
    <div class="rounded-xl border border-border bg-card overflow-hidden">
      <div v-if="loading" class="p-10 text-center text-sm text-muted-foreground">
        <Icon name="loader" :size="22" class="animate-spin mx-auto mb-2" /> 加载中…
      </div>
      <div v-else-if="error" class="p-10 text-center">
        <p class="text-sm text-red-600 mb-3">{{ error }}</p>
        <button class="px-3 py-1.5 rounded-lg text-sm bg-primary-500 text-white" @click="reload(filters.page)">重试</button>
      </div>
      <table v-else-if="pageData && pageData.records.length" class="w-full text-sm">
        <thead class="bg-gray-50 text-xs text-muted-foreground">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">ID / 标题</th>
            <th class="text-left px-4 py-2.5 font-medium">分类</th>
            <th class="text-left px-4 py-2.5 font-medium">难度</th>
            <th class="text-left px-4 py-2.5 font-medium">审核</th>
            <th class="text-left px-4 py-2.5 font-medium">上架</th>
            <th class="text-right px-4 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in pageData.records"
            :key="row.id"
            class="border-t border-border"
          >
            <td class="px-4 py-2.5">
              <p class="font-medium text-foreground">#{{ row.id }} {{ row.title }}</p>
              <p class="text-[11px] text-muted-foreground mt-0.5">{{ row.canonicalKey || '—' }} · v{{ row.version ?? 1 }}</p>
            </td>
            <td class="px-4 py-2.5 text-xs">{{ row.topicCode || '—' }}</td>
            <td class="px-4 py-2.5 text-xs">{{ diffLabel(row.difficulty) }}</td>
            <td class="px-4 py-2.5">
              <span class="text-[11px] px-2 py-0.5 rounded-full" :class="reviewClass(row.reviewStatus)">
                {{ reviewLabel(row.reviewStatus) }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-xs">
              <span :class="row.status === 1 ? 'text-success-600' : 'text-muted-foreground'">
                {{ row.status === 1 ? '已发布' : '草稿/下架' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-right whitespace-nowrap">
              <button class="text-xs text-primary-600 hover:underline mr-3" @click="edit(row.id)">编辑</button>
              <button
                v-if="row.reviewStatus !== 'APPROVED'"
                class="text-xs text-success-600 hover:underline mr-3"
                @click="approve(row.id)"
              >通过</button>
              <button
                v-if="row.status !== 1"
                class="text-xs text-primary-600 hover:underline mr-3 disabled:opacity-40"
                :disabled="row.reviewStatus !== 'APPROVED'"
                :title="row.reviewStatus !== 'APPROVED' ? '审核通过后才能发布' : ''"
                @click="publish(row.id, true)"
              >发布</button>
              <button
                v-else
                class="text-xs text-warning-600 hover:underline"
                @click="publish(row.id, false)"
              >下架</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="p-10 text-center text-sm text-muted-foreground">
        <Icon name="inbox" :size="32" class="mx-auto mb-2 opacity-60" /> 暂无题目
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="pageData && pageData.pages > 1" class="flex items-center justify-center gap-3 text-sm">
      <button
        class="px-3 py-1.5 rounded-lg border border-border disabled:opacity-40"
        :disabled="filters.page <= 1"
        @click="reload(filters.page - 1)"
      >上一页</button>
      <span class="text-xs text-muted-foreground">{{ filters.page }} / {{ pageData.pages }}</span>
      <button
        class="px-3 py-1.5 rounded-lg border border-border disabled:opacity-40"
        :disabled="filters.page >= pageData.pages"
        @click="reload(filters.page + 1)"
      >下一页</button>
    </div>

    <!-- 编辑抽屉 -->
    <div
      v-if="editing"
      class="fixed inset-0 z-40 bg-black/30 flex justify-end"
      @click.self="closeEditor"
    >
      <div class="w-full max-w-3xl h-full bg-white overflow-y-auto p-6 space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold">{{ form.id ? `编辑 #${form.id}` : '新建算法题' }}</h2>
          <button class="text-muted-foreground hover:text-foreground" @click="closeEditor">
            <Icon name="x" :size="20" />
          </button>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">标题 *</span>
            <input v-model="form.title" class="w-full px-3 py-2 text-sm outline-none kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">canonicalKey（语义去重键）</span>
            <input v-model="form.canonicalKey" placeholder="如 ALGO_TWO_SUM" class="w-full px-3 py-2 text-sm outline-none font-mono kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">分类 topicCode *</span>
            <input v-model="form.topicCode" placeholder="如 ALGO_HASH" class="w-full px-3 py-2 text-sm outline-none font-mono kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">难度 *</span>
            <select v-model.number="form.difficulty" class="w-full px-3 py-2 text-sm outline-none kf-select kf-form-control">
              <option :value="1">简单</option>
              <option :value="2">中等</option>
              <option :value="3">困难</option>
            </select>
          </label>
        </div>

        <label class="block text-sm">
          <span class="block text-xs text-muted-foreground mb-1">题面（Markdown）*</span>
          <textarea v-model="form.content" rows="6" class="w-full px-3 py-2 text-sm leading-6 outline-none kf-textarea kf-form-control"></textarea>
        </label>

        <div class="grid grid-cols-2 gap-3">
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">函数签名 *</span>
            <input v-model="form.functionSignature" class="w-full px-3 py-2 text-sm font-mono kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">标签（逗号分隔）</span>
            <input v-model="form.tags" class="w-full px-3 py-2 text-sm kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">时间限制 ms</span>
            <input v-model.number="form.timeLimitMs" type="number" class="w-full px-3 py-2 text-sm kf-input kf-form-control" />
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">内存限制 MB</span>
            <input v-model.number="form.memoryLimitMb" type="number" class="w-full px-3 py-2 text-sm kf-input kf-form-control" />
          </label>
        </div>

        <label class="block text-sm">
          <span class="block text-xs text-muted-foreground mb-1">初始代码（starterCode）*</span>
          <textarea v-model="form.starterCode" rows="5" class="w-full px-3 py-2 rounded-lg border border-border text-xs font-mono leading-5"></textarea>
        </label>

        <div class="grid grid-cols-2 gap-3">
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">约束 constraints</span>
            <textarea v-model="form.constraints" rows="3" class="w-full px-3 py-2 text-sm kf-textarea kf-form-control"></textarea>
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">示例 examples（JSON 数组）</span>
            <textarea v-model="form.examples" rows="3" placeholder='[{"input":"...","output":"..."}]' class="w-full px-3 py-2 text-xs font-mono kf-textarea kf-form-control"></textarea>
          </label>
        </div>

        <label class="block text-sm">
          <span class="block text-xs text-muted-foreground mb-1">参考解答 referenceSolution *（不进入用户详情接口）</span>
          <textarea v-model="form.referenceSolution" rows="6" class="w-full px-3 py-2 rounded-lg border border-border text-xs font-mono leading-5"></textarea>
        </label>
        <div class="grid grid-cols-2 gap-3">
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">题解</span>
            <textarea v-model="form.solutionExplanation" rows="4" class="w-full px-3 py-2 text-sm kf-textarea kf-form-control"></textarea>
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">复杂度分析</span>
            <textarea v-model="form.complexityAnalysis" rows="4" class="w-full px-3 py-2 text-sm kf-textarea kf-form-control"></textarea>
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">面试追问</span>
            <textarea v-model="form.followUps" rows="3" class="w-full px-3 py-2 text-sm kf-textarea kf-form-control"></textarea>
          </label>
          <label class="text-sm">
            <span class="block text-xs text-muted-foreground mb-1">常见错误</span>
            <textarea v-model="form.commonMistakes" rows="3" class="w-full px-3 py-2 text-sm kf-textarea kf-form-control"></textarea>
          </label>
        </div>

        <!-- 测试用例：至少 1 个 PUBLIC -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-sm font-semibold">测试用例（至少 1 个公开用例）</h3>
            <button class="text-xs text-primary-600 inline-flex items-center gap-1" @click="addCase">
              <Icon name="plus" :size="13" /> 新增用例
            </button>
          </div>
          <div v-for="(c, i) in form.cases" :key="i" class="rounded-lg border border-border p-3 mb-2 space-y-2">
            <div class="flex items-center gap-2">
              <span class="text-xs text-muted-foreground w-14">#{{ i + 1 }}</span>
              <select v-model="c.visibility" class="px-2 py-1 text-xs kf-select kf-form-control">
                <option value="PUBLIC">公开</option>
                <option value="HIDDEN">隐藏</option>
              </select>
              <label class="text-xs text-muted-foreground inline-flex items-center gap-1">
                权重
                <input v-model.number="c.weight" type="number" min="1" class="w-16 px-2 py-1 text-xs kf-input kf-form-control" />
              </label>
              <button class="ml-auto text-xs text-red-500" @click="removeCase(i)">
                <Icon name="trash-2" :size="14" />
              </button>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <input v-model="c.input" placeholder="输入" class="px-2 py-1.5 text-xs font-mono kf-input kf-form-control" />
              <input v-model="c.expectedOutput" placeholder="期望输出" class="px-2 py-1.5 text-xs font-mono kf-input kf-form-control" />
            </div>
          </div>
        </div>

        <div class="flex items-center gap-3 border-t border-border pt-4">
          <label class="text-sm inline-flex items-center gap-2 kf-checkbox-row">
            <Checkbox as="span" v-model.number="form.status"  :checked="form.status === 1"
              @native-change="form.status = ($event.target as HTMLInputElement).checked ? 1 : 0" />
            管理员确认内容后发布（等同审核通过）
          </label>
          <button
            class="ml-auto px-4 py-2 rounded-lg text-sm bg-primary-500 text-white hover:bg-primary-600 disabled:opacity-50"
            :disabled="saving"
            @click="save"
          >
            {{ saving ? '保存中…' : '保存题目' }}
          </button>
          <button class="px-4 py-2 rounded-lg text-sm border border-border" @click="closeEditor">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { watch, onBeforeUnmount } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import {
  adminCodeProblemApi,
  DIFFICULTY_LABELS,
  type AdminCodeCase,
  type AdminCodeProblem,
  type AdminSaveProblemRequest,
  type PageData,
} from '@/api/codeBank'
import { confirmDialog, getApiError, notify } from '@/utils/toast'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const pageData = ref<PageData<AdminCodeProblem> | null>(null)
const editing = ref(false)

const filters = reactive({
  keyword: '',
  topicCode: '',
  difficulty: undefined as number | undefined,
  reviewStatus: '',
  status: -1,
  page: 1,
  pageSize: 20,
})

function emptyForm(): AdminSaveProblemRequest {
  return {
    title: '',
    content: '',
    difficulty: 1,
    topicCode: 'ALGO_HASH',
    tags: '',
    canonicalKey: '',
    supportedLanguages: 'java',
    functionSignature: '',
    starterCode: '',
    constraints: '',
    examples: '',
    timeLimitMs: 1000,
    memoryLimitMb: 256,
    judgeMode: 'FUNCTION',
    referenceSolution: '',
    solutionExplanation: '',
    complexityAnalysis: '',
    followUps: '',
    commonMistakes: '',
    status: 0,
    cases: [
      { input: '', expectedOutput: '', visibility: 'PUBLIC', weight: 1, orderNo: 0 },
    ],
  }
}

const form = reactive<AdminSaveProblemRequest>(emptyForm())

function diffLabel(d?: number): string {
  return DIFFICULTY_LABELS[(d || 1) - 1] || '未知'
}
function reviewLabel(s?: string): string {
  if (s === 'APPROVED') return '已通过'
  if (s === 'REJECTED') return '已驳回'
  return '待审核'
}
function reviewClass(s?: string): string {
  if (s === 'APPROVED') return 'bg-success-100 text-success-700'
  if (s === 'REJECTED') return 'bg-red-100 text-red-600'
  return 'bg-warning-100 text-warning-700'
}

async function reload(page = 1) {
  filters.page = page
  loading.value = true
  error.value = ''
  try {
    pageData.value = await adminCodeProblemApi.list({
      page,
      pageSize: filters.pageSize,
      keyword: filters.keyword || undefined,
      reviewStatus: filters.reviewStatus || undefined,
      status: filters.status < 0 ? undefined : filters.status,
    })
  } catch (e) {
    error.value = getApiError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

function createNew() {
  Object.assign(form, emptyForm())
  editing.value = true
}

function addCase() {
  form.cases.push({
    input: '',
    expectedOutput: '',
    visibility: 'PUBLIC',
    weight: 1,
    orderNo: form.cases.length,
  })
}
function removeCase(i: number) {
  form.cases.splice(i, 1)
  form.cases.forEach((c: AdminCodeCase, idx: number) => (c.orderNo = idx))
}

let catalogRequest = 0
async function edit(id: number) {
  const requestId = ++catalogRequest
  try {
    const d = await adminCodeProblemApi.detail(id)
    if (requestId !== catalogRequest) return
    const next = emptyForm()
    next.id = d.id
    next.title = d.title
    next.content = d.content
    next.difficulty = d.difficulty
    next.topicCode = d.topicCode || ''
    next.tags = d.tags || ''
    next.canonicalKey = d.canonicalKey || ''
    next.status = d.status ?? 0
    next.cases = (d.cases || []).map((c) => ({ ...c }))
    if (d.problemJson) {
      const p = JSON.parse(d.problemJson) as Partial<AdminSaveProblemRequest>
      Object.assign(next, {
        supportedLanguages: p.supportedLanguages || 'java',
        functionSignature: p.functionSignature || '',
        starterCode: p.starterCode || '',
        constraints: p.constraints || '',
        examples: p.examples || '',
        timeLimitMs: p.timeLimitMs ?? 1000,
        memoryLimitMb: (p.memoryLimitMb as number) ?? 256,
        judgeMode: p.judgeMode || 'FUNCTION',
        referenceSolution: p.referenceSolution || '',
        solutionExplanation: p.solutionExplanation || '',
        complexityAnalysis: p.complexityAnalysis || '',
        followUps: p.followUps || '',
        commonMistakes: p.commonMistakes || '',
      })
    }
    Object.assign(form, next)
    editing.value = true
  } catch (e) {
    if (requestId === catalogRequest) notify(getApiError(e, '题目详情加载失败'), 'error')
  }
}

async function save() {
  if (!form.title.trim() || !form.content.trim() || !form.functionSignature?.trim()
    || !form.starterCode?.trim() || !form.referenceSolution?.trim()) {
    notify('请补全标题 / 题面 / 函数签名 / 初始代码 / 参考解答', 'warning')
    return
  }
  const validCases = form.cases.filter((c) => c.input.trim() && c.expectedOutput.trim())
  if (!validCases.length) {
    notify('至少需要一个完整测试用例', 'warning')
    return
  }
  if (!validCases.some((c) => c.visibility === 'PUBLIC')) {
    notify('至少需要一个 PUBLIC 公开用例', 'warning')
    return
  }
  const examples = form.examples?.trim()
  if (examples) {
    try {
      const arr = JSON.parse(examples)
      if (!Array.isArray(arr)) throw new Error('not array')
    } catch {
      notify('示例 examples 必须是 JSON 数组', 'warning')
      return
    }
  }
  const payload = { ...form, cases: validCases.map((c, i) => ({ ...c, orderNo: i })) }
  saving.value = true
  try {
    await adminCodeProblemApi.save(payload)
    notify('保存成功。待审核题目不会进入用户题库与智能组卷。', 'success', 4000)
    editing.value = false
    await reload(filters.page)
  } catch (e) {
    notify(getApiError(e, '保存失败'), 'error', 5000)
  } finally {
    saving.value = false
  }
}

async function approve(id: number) {
  const ok = await confirmDialog('确认审核通过并发布该题？审核通过后题目将对普通用户可见并可进入智能组卷。', {"title":"提交审核结果？","confirmText":"确认审核","cancelText":"返回审核","icon":"check-circle","variant":"default"})
  if (!ok) return
  try {
    await adminCodeProblemApi.review(id, 'APPROVED', '人工审核通过')
    notify('已审核通过', 'success')
    await reload(filters.page)
  } catch (e) {
    notify(getApiError(e, '审核失败'), 'error')
  }
}

async function publish(id: number, published: boolean) {
  await confirmDialog(published ? '确认发布该题？' : '确认下架该题？已生成试卷快照不受影响。', { "title": "确认变更发布状态？", "confirmText": "确认变更", "cancelText": "取消", "icon": "check-circle", "variant": "default", onConfirm: async () => {
          try {
              await adminCodeProblemApi.publish(id, published);
              notify(published ? '已发布' : '已下架', 'success');
              await reload(filters.page);
          }
          catch (e) {
              notify(getApiError(e, '操作失败'), 'error');
              throw e;
          }
      } });
}

function closeEditor() {
  editing.value = false
}

onMounted(() => reload(1))
const catalogRoute = useRoute()
watch(() => catalogRoute.query.questionId, (raw) => {
  catalogRequest++
  if (raw === undefined) return
  if (typeof raw !== 'string' || !/^[1-9]\d*$/.test(raw) || !Number.isSafeInteger(Number(raw))) {
    notify('题目链接无效', 'error')
    return
  }
  void edit(Number(raw))
}, { immediate: true })
watch(() => catalogRoute.query.create, (value) => {
  if (value === '1' && catalogRoute.query.questionId === undefined) { catalogRequest++; createNew() }
}, { immediate: true })
onBeforeUnmount(() => { catalogRequest++ })
</script>
