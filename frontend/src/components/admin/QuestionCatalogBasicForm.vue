<template>
  <form class="catalog-basic" @submit.prevent="save">
    <h3 class="kb-h3">统一基本信息</h3>
    <Input v-model="form.title" label="标题" :disabled="saving" required />
    <label>难度<select v-model="form.difficulty" class="kb-select kb-form-control" :disabled="saving"><option value="1">简单</option><option value="2">中等</option><option value="3">困难</option></select></label>
    <Input v-model="form.tags" label="标签（逗号分隔）" :disabled="saving || question.sourceType === 'SCENARIO'" />
    <p v-if="question.sourceType === 'SCENARIO'" class="text-muted-foreground">场景来源没有标签字段。</p>
    <template v-if="hasQuizMetadata">
      <label>知识库<select v-model="form.categoryId" class="kb-select kb-form-control" :disabled="saving"><option value="">未关联（清除）</option><option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
      <Input v-model="form.topicCode" label="知识点编码（现有 topicCode）" placeholder="输入已存在的编码，留空清除" :disabled="saving" />
      <p class="text-muted-foreground">知识点编码与知识图谱实体 ID 是不同字段，由服务端验证真实编码。</p>
    </template>
    <template v-if="question.sourceType === 'SCENARIO'">
      <Input v-model="form.knowledgeId" label="知识图谱实体 ID（最多一个）" placeholder="输入真实实体 ID，留空清除关联" :disabled="saving" />
      <p class="text-muted-foreground">场景仅支持单个真实知识图谱实体关联，由服务端验证归属字段。</p>
    </template>
    <p class="text-muted-foreground">题干、选项、答案、场景背景与覆盖要点及测试用例继续在专业编辑器维护。修改 AI / 编程题后，审核状态由服务端重新判定。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <Button type="submit" :loading="saving">保存基本信息</Button>
  </form>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import { questionCatalogApi, type CatalogQuestion, type CatalogBasicInput } from '@/api/questionCatalog'
import type { CategoryVO } from '@/api/types'
import { getApiError } from '@/utils/toast'
const props = defineProps<{ question: CatalogQuestion; categories: CategoryVO[] }>()
const emit = defineEmits<{ saved: [] }>()
const form = reactive({ title: '', difficulty: '1', tags: '', categoryId: '', topicCode: '', knowledgeId: '' })
const saving = ref(false), error = ref('')
let request = 0
const hasQuizMetadata = computed(() => props.question.sourceType === 'QUIZ' || props.question.sourceType === 'CODE_PROBLEM')
watch(() => props.question, (question) => {
  request++; saving.value = false; error.value = ''
  Object.assign(form, { title: question.title, difficulty: String(question.difficulty ?? 1), tags: Array.isArray(question.tags) ? question.tags.join(',') : question.tags || '', categoryId: question.categoryId == null ? '' : String(question.categoryId), topicCode: question.knowledgePointCodes?.[0] || '', knowledgeId: question.knowledgePointIds?.[0] == null ? '' : String(question.knowledgePointIds[0]) })
}, { immediate: true })
async function save() {
  if (saving.value) return
  error.value = ''
  if (!form.title.trim()) { error.value = '请填写题目标题'; return }
  if (props.question.sourceType === 'SCENARIO' && form.knowledgeId && (!/^[1-9]\d*$/.test(form.knowledgeId) || !Number.isSafeInteger(Number(form.knowledgeId)))) { error.value = '知识图谱实体 ID 必须是有效正整数'; return }
  const target = props.question
  const payload: CatalogBasicInput = { title: form.title.trim(), difficulty: Number(form.difficulty), tags: target.sourceType === 'SCENARIO' ? '' : form.tags.trim(), expectedSourceVersion: typeof target.sourceVersion === 'number' ? target.sourceVersion : null, expectedUpdatedAt: target.updatedAt ?? null, expectedEditToken: target.editToken ?? null }
  if (hasQuizMetadata.value) { payload.categoryId = form.categoryId === '' ? null : Number(form.categoryId); payload.topicCode = form.topicCode.trim() || null }
  if (target.sourceType === 'SCENARIO') payload.knowledgePointIds = form.knowledgeId ? [Number(form.knowledgeId)] : []
  const requestId = ++request; saving.value = true
  try { await questionCatalogApi.updateBasic(target.catalogId, payload); if (requestId === request) emit('saved') }
  catch (e: unknown) { if (requestId === request) error.value = getApiError(e) }
  finally { if (requestId === request) saving.value = false }
}
onBeforeUnmount(() => { request++ })
</script>
<style scoped>
.catalog-basic { display: flex; flex-direction: column; gap: var(--kb-space-3); padding-block: var(--kb-space-4); border-block: 1px solid var(--kb-border); }
.catalog-basic label { display: flex; flex-direction: column; gap: var(--kb-space-2); }
.catalog-basic p { font-size: var(--kb-fs-body-sm); }
</style>
