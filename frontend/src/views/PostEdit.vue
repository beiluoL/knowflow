<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '社区讨论', to: '/community' }, { label: '发布新帖' }]"
      title="发布新帖"
    >
      <template #actions>
        <button
          @click="goBack"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-gray-600 border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          <Icon name="chevron-left" :size="16" />
          <span>返回</span>
        </button>
        <button
          @click="handleSubmit"
          :disabled="submitting || !canSubmit"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50"
        >
          <Icon name="pen-line" :size="16" />
          <span>{{ submitting ? '发布中...' : '发布' }}</span>
        </button>
      </template>
    </PageHeader>

    <div class="max-w-3xl mx-auto flex flex-col gap-5">
      <!-- 标题 -->
      <section class="border rounded-[10px] p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <label class="block text-sm font-medium text-gray-700 mb-2">标题</label>
        <input
          v-model="form.title"
          type="text"
          maxlength="60"
          placeholder="一句话概括你的主题，例如：大模型微调踩坑记录"
          class="w-full px-3 py-2.5 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-primary-400"
        />
        <p class="text-[12px] text-gray-400 mt-1.5 text-right">{{ form.title.length }}/60</p>
      </section>

      <!-- 分类 -->
      <section class="border rounded-[10px] p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <label class="block text-sm font-medium text-gray-700 mb-3">分类</label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="cat in categories"
            :key="cat.value"
            @click="form.category = cat.value"
            type="button"
            :class="[
              'px-3 py-1.5 rounded-lg text-sm border transition-colors',
              form.category === cat.value
                ? 'bg-primary-50 text-primary-600 border-primary-300 font-medium'
                : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300',
            ]"
          >{{ cat.label }}</button>
        </div>
      </section>

      <!-- 正文 -->
      <section class="border rounded-[10px] p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <label class="block text-sm font-medium text-gray-700 mb-2">正文</label>
        <textarea
          v-model="form.content"
          rows="14"
          placeholder="分享你的经验、问题或资源，支持换行..."
          class="w-full px-3 py-2.5 rounded-lg border border-gray-200 text-sm leading-relaxed focus:outline-none focus:border-primary-400 resize-none"
        ></textarea>
      </section>

      <!-- 底部提交（常驻可见） -->
      <div class="flex items-center justify-end gap-2 py-2">
        <button
          @click="goBack"
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors"
        >取消</button>
        <button
          @click="handleSubmit"
          :disabled="submitting || !canSubmit"
          type="button"
          class="px-5 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50"
        >{{ submitting ? '发布中...' : '发布帖子' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 发布帖子页：从社区列表跳转而来，独立页面承载表单，发布成功后回到社区列表。
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'

const router = useRouter()
const authStore = useAuthStore()

// 与社区列表侧边栏分类保持一致
const categories = [
  { value: '技术问答', label: '技术问答' },
  { value: '学习心得', label: '学习心得' },
  { value: '资源分享', label: '资源分享' },
  { value: '面试经验', label: '面试经验' },
]

const form = ref({ title: '', category: '', content: '' })
const submitting = ref(false)

const canSubmit = computed(() => form.value.title.trim().length > 0)

// 优先退回上一页（保持社区内的浏览位置），无历史记录时兜底回社区列表
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/community')
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify('请填写帖子标题', 'warning')
    return
  }
  if (!authStore.isLoggedIn) {
    notify('请先登录后再发布', 'warning')
    return
  }
  submitting.value = true
  try {
    await communityApi.createPost({
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      category: form.value.category,
    })
    notify('发布成功', 'success')
    router.push('/community')
  } catch {
    // 错误由拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
