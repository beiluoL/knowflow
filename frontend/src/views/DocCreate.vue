<template>
  <div class="space-y-6 animate-fade-in">
    <section>
      <nav class="flex items-center gap-1.5 mb-4">
        <span class="text-sm text-gray-500">知识库</span>
        <Icon name="chevron-right" :size="14" class="text-gray-400" />
        <span class="text-sm text-gray-800">新增文档</span>
      </nav>
      <div class="flex items-center justify-between gap-4">
        <h1 class="text-[28px] font-bold truncate text-gray-800">新增文档</h1>
      </div>
    </section>

    <section class="border rounded-[10px] p-6 bg-white border-gray-200">
      <form @submit.prevent="handleSubmit" class="flex flex-col gap-5">
        <div class="flex flex-col gap-2">
          <label class="text-sm font-medium text-gray-700">
            文档标题 <span class="text-danger-500">*</span>
          </label>
          <input
            v-model="form.title"
            type="text"
            placeholder="请输入文档标题"
            class="px-4 py-2.5 rounded-lg border text-sm bg-white text-gray-800 border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 outline-none transition-all"
          />
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <label class="text-sm font-medium text-gray-700">
              所属分类 <span class="text-danger-500">*</span>
            </label>
            <select
              v-model="form.categoryId"
              class="px-4 py-2.5 rounded-lg border text-sm bg-white text-gray-800 border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 outline-none transition-all"
            >
              <option :value="null">请选择分类</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </div>
          <div class="flex flex-col gap-2">
            <label class="text-sm font-medium text-gray-700">
              文档标签
            </label>
            <input
              v-model="form.tags"
              type="text"
              placeholder="多个标签用逗号分隔"
              class="px-4 py-2.5 rounded-lg border text-sm bg-white text-gray-800 border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 outline-none transition-all"
            />
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <label class="text-sm font-medium text-gray-700">
            文档摘要
          </label>
          <textarea
            v-model="form.summary"
            rows="3"
            placeholder="简要描述文档内容（可选）"
            class="px-4 py-2.5 rounded-lg border text-sm bg-white text-gray-800 border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 outline-none transition-all resize-none"
          ></textarea>
        </div>

        <div class="flex flex-col gap-2">
          <div class="flex items-center justify-between">
            <label class="text-sm font-medium text-gray-700">
              文档内容 <span class="text-danger-500">*</span>
            </label>
            <div class="flex items-center gap-1">
              <button
                type="button"
                @click="isPreview = false"
                :class="[
                  'px-3 py-1 rounded text-xs transition-colors',
                  !isPreview ? 'bg-primary-500 text-white' : 'text-gray-500 hover:bg-gray-100',
                ]"
              >编辑</button>
              <button
                type="button"
                @click="isPreview = true"
                :class="[
                  'px-3 py-1 rounded text-xs transition-colors',
                  isPreview ? 'bg-primary-500 text-white' : 'text-gray-500 hover:bg-gray-100',
                ]"
              >预览</button>
            </div>
          </div>
          <div v-if="!isPreview">
            <textarea
              v-model="form.content"
              rows="16"
              placeholder="支持 Markdown 格式..."
              class="w-full px-4 py-3 rounded-lg border text-sm bg-white text-gray-800 border-gray-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 outline-none transition-all resize-none font-mono"
            ></textarea>
          </div>
          <div v-else class="border rounded-lg p-4 min-h-[384px] bg-gray-50 border-gray-200">
            <div class="prose prose-sm max-w-none text-gray-800" v-html="renderedContent"></div>
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 pt-2">
          <button
            type="button"
            @click="handleCancel"
            class="px-5 py-2.5 rounded-lg text-sm font-medium border text-gray-700 border-gray-200 bg-white hover:bg-gray-50 transition-colors"
          >取消</button>
          <button
            type="submit"
            :disabled="submitting"
            class="px-5 py-2.5 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ submitting ? '保存中...' : '保存文档' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
// 新增文档页：填写标题/分类/标签/内容，提供 Markdown 实时预览，提交创建。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { docsApi } from '@/api/docs'
import { categoriesApi } from '@/api/categories'
import { notify } from '@/utils/toast'
import type { CategoryVO } from '@/api/types'

const router = useRouter()
const submitting = ref(false)
const isPreview = ref(false)
const categories = ref<CategoryVO[]>([])

const form = ref({
  title: '',
  categoryId: null as number | null,
  tags: '',
  summary: '',
  content: '',
})

// 简易 Markdown 预览：标题/加粗/斜体/行内代码/换行的正则替换
const renderedContent = computed(() => {
  let html = form.value.content
    .replace(/^### (.*$)/gm, '<h3 class="text-lg font-semibold mt-4 mb-2">$1</h3>')
    .replace(/^## (.*$)/gm, '<h2 class="text-xl font-bold mt-5 mb-3">$1</h2>')
    .replace(/^# (.*$)/gm, '<h1 class="text-2xl font-bold mt-6 mb-4">$1</h1>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code class="bg-gray-200 px-1 rounded text-sm">$1</code>')
    .replace(/\n/g, '<br />')
  return html || '<p class="text-gray-400">暂无内容</p>'
})

async function fetchCategories() {
  try {
    const data = await categoriesApi.list()
    categories.value = data
  } catch (e) {
    console.error(e)
  }
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify('请输入文档标题', 'warning')
    return
  }
  if (!form.value.categoryId) {
    notify('请选择所属分类', 'warning')
    return
  }
  if (!form.value.content.trim()) {
    notify('请输入文档内容', 'warning')
    return
  }

  submitting.value = true
  try {
    const doc = await docsApi.create({
      title: form.value.title,
      categoryId: form.value.categoryId,
      tags: form.value.tags,
      summary: form.value.summary,
      content: form.value.content,
    })
    router.push(`/docs/${doc.id}`)
  } catch (e) {
    console.error(e)
    notify('创建失败，请重试', 'error')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.back()
}

onMounted(() => {
  fetchCategories()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.prose {
  line-height: 1.7;
}
</style>
