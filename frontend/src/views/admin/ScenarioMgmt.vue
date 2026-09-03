<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 页头 -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-foreground">场景练习管理</h1>
        <p class="text-muted-foreground text-sm mt-1">维护 Scenario Lab 内容：情景 / 问题 / 覆盖要点（关键词仅后台可见），支持上下架与删除</p>
      </div>
      <div class="flex items-center gap-3">
        <Button variant="secondary" icon-name="refresh-cw" :disabled="loading" @click="loadScenarios">刷新</Button>
        <Button icon-name="plus" @click="openCreate">新建场景</Button>
      </div>
    </div>

    <!-- 列表 -->
    <Card padding="none">
      <div class="p-4 border-b border-border">
        <div class="flex flex-col sm:flex-row sm:items-center gap-4">
          <div class="flex-1">
            <Input v-model="keyword" placeholder="搜索标题 / 问题..." prefix-icon-name="search" />
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <select
              v-model="statusFilter"
              class="px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:border-primary-500 hover:border-primary-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            >
              <option value="">全部状态</option>
              <option value="1">已上架</option>
              <option value="0">已下架</option>
            </select>
            <span class="text-xs text-muted-foreground">共 {{ filtered.length }} 个场景</span>
          </div>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border text-left text-xs text-muted-foreground">
              <th class="px-4 py-3 font-medium">场景</th>
              <th class="px-4 py-3 font-medium whitespace-nowrap">难度</th>
              <th class="px-4 py-3 font-medium whitespace-nowrap">要点</th>
              <th class="px-4 py-3 font-medium whitespace-nowrap">状态</th>
              <th class="px-4 py-3 font-medium whitespace-nowrap">更新时间</th>
              <th class="px-4 py-3 font-medium whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in filtered"
              :key="row.id"
              class="border-b border-border last:border-0 hover:bg-gray-50 transition-colors"
            >
              <td class="px-4 py-3 min-w-[260px] max-w-md">
                <p class="font-medium text-foreground line-clamp-1">{{ row.title }}</p>
                <p class="text-xs text-muted-foreground line-clamp-1 mt-0.5">{{ row.questionText }}</p>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <Badge :variant="difficultyVariant(row.difficulty)">{{ difficultyText(row.difficulty) }}</Badge>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-foreground">
                {{ row.keyPoints?.length ?? 0 }} 点
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <Badge :variant="row.status === 1 ? 'success' : 'default'">{{ row.status === 1 ? '已上架' : '已下架' }}</Badge>
              </td>
              <td class="px-4 py-3 whitespace-nowrap text-xs text-muted-foreground">
                {{ fmtTime(row.updateTime || row.createTime) }}
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <div class="flex items-center gap-1">
                  <button
                    class="p-1 hover:bg-gray-100 rounded text-muted-foreground hover:text-primary-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                    :title="row.status === 1 ? '下架（学习端不再可见）' : '上架'"
                    :aria-label="row.status === 1 ? '下架' : '上架'"
                    @click.stop="toggleStatus(row)"
                  >
                    <Icon :name="row.status === 1 ? 'eye-off' : 'eye'" :size="16" aria-hidden="true" />
                  </button>
                  <button
                    class="p-1 hover:bg-gray-100 rounded text-muted-foreground hover:text-primary-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                    title="编辑"
                    aria-label="编辑"
                    @click.stop="openEdit(row)"
                  >
                    <Icon name="edit" :size="16" aria-hidden="true" />
                  </button>
                  <button
                    class="p-1 hover:bg-gray-100 rounded text-muted-foreground hover:text-danger-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                    title="删除"
                    aria-label="删除"
                    @click.stop="onDelete(row)"
                  >
                    <Icon name="trash-2" :size="16" aria-hidden="true" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="filtered.length === 0" class="text-center text-muted-foreground text-sm py-12">
          {{ loading ? '加载中...' : '暂无场景数据' }}
        </p>
      </div>
    </Card>

    <!-- 新建 / 编辑弹窗 -->
    <div
      v-if="showModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeModal"
    >
      <div class="bg-card rounded-xl w-full max-w-3xl shadow-xl animate-dropdown max-h-[92vh] flex flex-col">
        <div class="flex items-center justify-between px-6 py-4 border-b border-border shrink-0">
          <h3 class="text-lg font-semibold text-foreground">{{ editingId ? '编辑场景' : '新建场景' }}</h3>
          <button
            class="p-1 hover:bg-gray-100 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            :aria-label="'关闭'"
            @click="closeModal"
          >
            <Icon name="x" :size="20" aria-hidden="true" />
          </button>
        </div>

        <div class="px-6 py-4 space-y-4 overflow-y-auto">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div class="md:col-span-2">
              <label class="block text-sm font-medium text-foreground mb-1.5">场景标题 *</label>
              <Input v-model="form.title" placeholder="如：线上 Full GC 频繁排查" :maxlength="200" />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-sm font-medium text-foreground mb-1.5">难度</label>
                <select
                  v-model.number="form.difficulty"
                  class="w-full px-3 py-2 border border-border rounded-sm text-sm focus:outline-none focus:border-primary-500 hover:border-primary-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                >
                  <option :value="1">简单</option>
                  <option :value="2">中等</option>
                  <option :value="3">困难</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-foreground mb-1.5">状态</label>
                <select
                  v-model.number="form.status"
                  class="w-full px-3 py-2 border border-border rounded-sm text-sm focus:outline-none focus:border-primary-500 hover:border-primary-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                >
                  <option :value="1">上架</option>
                  <option :value="0">下架</option>
                </select>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">情景背景（可选）</label>
            <textarea
              v-model="form.contextText"
              rows="2"
              placeholder="给用户一段贴近真实工作 / 学习的情景描述，便于代入作答"
              class="w-full px-3 py-2 border border-border rounded-sm text-sm focus:outline-none focus:border-primary-500 hover:border-primary-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            ></textarea>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">要用户回答的问题 *</label>
            <textarea
              v-model="form.questionText"
              rows="2"
              placeholder="如：请描述你的排查思路（覆盖要点关键词即可命中）"
              :maxlength="1000"
              class="w-full px-3 py-2 border border-border rounded-sm text-sm focus:outline-none focus:border-primary-500 hover:border-primary-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            ></textarea>
          </div>

          <!-- 覆盖要点编辑器 -->
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <label class="block text-sm font-medium text-foreground">覆盖要点 *</label>
              <span class="text-xs text-muted-foreground">评分口径：作答命中任一关键词即覆盖该要点；关键词仅后台可见</span>
            </div>
            <div class="space-y-2">
              <div
                v-for="(point, index) in form.points"
                :key="index"
                class="flex flex-col sm:flex-row sm:items-center gap-2"
              >
                <Input
                  v-model="point.label"
                  :placeholder="'要点 ' + (index + 1) + ' 名称，如：GC 日志'"
                  class="sm:flex-1"
                />
                <Input
                  v-model="point.keywordsLine"
                  placeholder="命中关键词，多个用逗号分隔：如 gc日志, gclog, fullgc次数"
                  class="sm:flex-[1.6]"
                />
                <button
                  class="p-2 hover:bg-gray-100 rounded shrink-0 text-muted-foreground hover:text-danger-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                  :aria-label="'删除要点 ' + (index + 1)"
                  :title="'删除该要点'"
                  @click="form.points.splice(index, 1)"
                >
                  <Icon name="x-circle" :size="18" aria-hidden="true" />
                </button>
              </div>
            </div>
            <button
              class="mt-2 inline-flex items-center gap-1 text-sm text-primary-500 hover:text-primary-600 px-2 py-1 rounded hover:bg-primary-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              @click="form.points.push({ label: '', keywordsLine: '' })"
            >
              <Icon name="plus" :size="16" aria-hidden="true" />添加要点
            </button>
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border shrink-0">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-场景练习内容：维护 Scenario Lab 场景（标题/情景/问题/覆盖要点关键词），
// 支持上下架切换、新建/覆盖式编辑、逻辑删除。内容全用户共享；不触碰掌握度/事件。
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { adminApi } from '@/api'
import type { ScenarioAdminVO, ScenarioPointRow } from '@/api/admin'
import { dialog } from '@/utils/dialog'
import { getApiError, notify } from '@/utils/toast'

interface PointRow {
  label: string
  keywordsLine: string
}

interface FormState {
  title: string
  contextText: string
  questionText: string
  difficulty: number
  status: number
  points: PointRow[]
}

const rows = ref<ScenarioAdminVO[]>([])
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive<FormState>({
  title: '',
  contextText: '',
  questionText: '',
  difficulty: 1,
  status: 1,
  points: [{ label: '', keywordsLine: '' }],
})

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const st = statusFilter.value === '' ? null : Number(statusFilter.value)
  return rows.value.filter((r) => {
    const hitKeyword =
      !q ||
      r.title.toLowerCase().includes(q) ||
      (r.questionText ?? '').toLowerCase().includes(q)
    const hitStatus = st === null || r.status === st
    return hitKeyword && hitStatus
  })
})

const difficultyText = (d: number): string => (d === 3 ? '困难' : d === 2 ? '中等' : '简单')
const difficultyVariant = (d: number): 'success' | 'warning' | 'danger' =>
  d === 3 ? 'danger' : d === 2 ? 'warning' : 'success'

const fmtTime = (t?: string | null): string => {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('zh-CN', { hour12: false })
}

/** 将「逗号/顿号/分号/空白」分隔的关键词行拆为去重数组 */
const splitKeywords = (line: string): string[] => {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of line.split(/[,，、;；\s]+/)) {
    const k = raw.trim()
    if (k && !seen.has(k)) {
      seen.add(k)
      out.push(k)
    }
  }
  return out
}

const buildPayload = (): ScenarioPointRow[] =>
  form.points
    .map((p) => ({ label: p.label.trim(), keywords: splitKeywords(p.keywordsLine) }))
    .filter((p) => p.label.length > 0 && p.keywords.length > 0)

const loadScenarios = async (): Promise<void> => {
  loading.value = true
  try {
    rows.value = await adminApi.scenarios()
  } catch (e: unknown) {
    notify('加载场景列表失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

const openCreate = (): void => {
  editingId.value = null
  form.title = ''
  form.contextText = ''
  form.questionText = ''
  form.difficulty = 1
  form.status = 1
  form.points = [{ label: '', keywordsLine: '' }]
  showModal.value = true
}

const openEdit = (row: ScenarioAdminVO): void => {
  editingId.value = row.id
  form.title = row.title ?? ''
  form.contextText = row.contextText ?? ''
  form.questionText = row.questionText ?? ''
  form.difficulty = row.difficulty ?? 1
  form.status = row.status === 0 ? 0 : 1
  form.points = (row.keyPoints ?? []).map((p) => ({
    label: p.label ?? '',
    keywordsLine: (p.keywords ?? []).join('，'),
  }))
  if (form.points.length === 0) form.points = [{ label: '', keywordsLine: '' }]
  showModal.value = true
}

const closeModal = (): void => {
  showModal.value = false
  editingId.value = null
}

const validate = (): string | null => {
  if (!form.title.trim()) return '请填写场景标题'
  if (!form.questionText.trim()) return '请填写要用户回答的问题'
  if (form.points.length === 0) return '至少需要一个覆盖要点'
  for (let i = 0; i < form.points.length; i += 1) {
    if (!form.points[i].label.trim()) return `第 ${i + 1} 条要点的名称不能为空`
    if (splitKeywords(form.points[i].keywordsLine).length === 0) {
      return `第 ${i + 1} 条要点至少需要一个命中关键词`
    }
  }
  return null
}

const save = async (): Promise<void> => {
  const problem = validate()
  if (problem) {
    notify(problem, 'warning')
    return
  }
  const payload = {
    title: form.title.trim(),
    contextText: form.contextText.trim(),
    questionText: form.questionText.trim(),
    keyPoints: buildPayload(),
    difficulty: form.difficulty,
    status: form.status,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateScenario(editingId.value, payload)
      notify('场景已更新', 'success')
    } else {
      await adminApi.createScenario(payload)
      notify('场景已创建', 'success')
    }
    closeModal()
    await loadScenarios()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row: ScenarioAdminVO): Promise<void> => {
  const next = row.status === 1 ? 0 : 1
  try {
    await adminApi.updateScenario(row.id, {
      title: row.title,
      contextText: row.contextText ?? '',
      questionText: row.questionText,
      keyPoints: row.keyPoints ?? [],
      difficulty: row.difficulty,
      knowledgeId: row.knowledgeId ?? null,
      status: next,
    })
    notify(next === 1 ? '场景已上架' : '场景已下架', 'success')
    await loadScenarios()
  } catch (e: unknown) {
    notify('操作失败：' + getApiError(e), 'error')
  }
}

const onDelete = async (row: ScenarioAdminVO): Promise<void> => {
  const ok = await dialog.confirm({
    title: '删除场景',
    message: `确定删除「${row.title}」？\n删除为逻辑删除，历史作答事件保留，但学习端将不再展示该场景。`,
    variant: 'danger',
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await adminApi.removeScenario(row.id)
    notify('删除成功', 'success')
    await loadScenarios()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

onMounted(loadScenarios)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}

@keyframes dropdown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
