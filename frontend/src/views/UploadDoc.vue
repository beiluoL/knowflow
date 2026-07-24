<template>
  <div class="space-y-6 animate-fade-in max-w-4xl">
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-800">上传文档</h1>
      <p class="text-gray-500 mt-1">支持 PDF、Word、Markdown、TXT 等格式</p>
    </div>

    <Card>
      <div
        :class="[
          'border-2 border-dashed rounded-lg p-12 text-center transition-all duration-300 cursor-pointer',
          isDragging
            ? 'border-primary-500 bg-primary-50 scale-[1.01]'
            : 'border-gray-200 hover:border-primary-300 hover:bg-gray-50'
        ]"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInputRef"
          type="file"
          class="hidden"
          accept=".pdf,.doc,.docx,.md,.txt,.ppt,.pptx"
          @change="handleFileSelect"
        />

        <div v-if="!selectedFile" class="animate-fade-in">
          <div :class="[
            'w-16 h-16 mx-auto mb-4 rounded-2xl flex items-center justify-center transition-colors',
            isDragging ? 'bg-primary-100' : 'bg-gray-100'
          ]">
            <Icon name="upload" :size="32" />
          </div>
          <h3 class="text-lg font-medium text-gray-800 mb-2">
            {{ isDragging ? '释放文件以上传' : '拖拽文件到此处' }}
          </h3>
          <p class="text-sm text-gray-500 mb-4">或者点击选择文件</p>
          <Button variant="secondary" icon-name="file-up">
            选择文件
          </Button>
          <p class="text-xs text-gray-400 mt-4">
            支持 PDF、Word、PPT、Markdown、TXT 格式，单个文件不超过 50MB
          </p>
        </div>

        <div v-else class="animate-fade-in">
          <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-primary-100 flex items-center justify-center">
            <Icon name="file-text" :size="32" />
          </div>
          <h3 class="text-lg font-medium text-gray-800 mb-1">{{ selectedFile.name }}</h3>
          <p class="text-sm text-gray-500 mb-2">
            {{ formatFileSize(selectedFile.size) }} · {{ getFileType(selectedFile.name) }}
          </p>
          <button
            class="text-sm text-danger-500 hover:text-danger-600 flex items-center gap-1 mx-auto"
            @click.stop="removeFile"
          >
            <Icon name="x" :size="16" />
            移除文件
          </button>
        </div>
      </div>
    </Card>

    <div v-if="selectedFile" class="mt-6 animate-slide-up">
      <Card>
        <template #header>
          <h2 class="text-lg font-semibold text-gray-800">文档信息</h2>
        </template>

        <div class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              文档标题 <span class="text-danger-500">*</span>
            </label>
            <input
              v-model="formData.title"
              type="text"
              placeholder="请输入文档标题"
              class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              分类 <span class="text-danger-500">*</span>
            </label>
            <div class="relative">
              <select
                v-model="formData.categoryId"
                class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all appearance-none bg-white cursor-pointer"
              >
                <option :value="''">请选择分类</option>
                <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                  {{ cat.name }}
                </option>
              </select>
              <Icon name="chevron-down" :size="16" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              标签
            </label>
            <div class="flex flex-wrap gap-2 mb-2">
              <Badge
                v-for="(tag, index) in formData.tags" :key="index"
                variant="primary"
                class="flex items-center gap-1 py-1"
              >
                {{ tag }}
                <button
                  class="ml-1 hover:text-primary-800 transition-colors"
                  @click="removeTag(index)"
                >
                  <Icon name="x" :size="12" />
                </button>
              </Badge>
            </div>
            <div class="flex gap-2">
              <input
                v-model="newTag"
                type="text"
                placeholder="输入标签后按回车添加"
                class="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                @keydown.enter.prevent="addTag"
              />
              <Button variant="secondary" @click="addTag" :disabled="!newTag.trim()">
                添加
              </Button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              描述
            </label>
            <textarea
              v-model="formData.description"
              rows="4"
              placeholder="请输入文档描述..."
              class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all resize-none"
            ></textarea>
          </div>
        </div>
      </Card>

      <div class="flex justify-end gap-3 mt-6">
        <Button variant="secondary" @click="handleCancel">
          取消
        </Button>
        <Button icon-name="upload" :loading="isUploading" @click="handleUpload" :disabled="!canSubmit">
          {{ isUploading ? '上传中...' : '上传文档' }}
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { notify } from '@/utils/toast'
import { ref, computed, watch, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO } from '@/api/types'

const router = useRouter()

const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const newTag = ref('')

const categories = ref<CategoryVO[]>([])

const formData = ref({
  title: '',
  categoryId: '' as number | string,
  tags: [] as string[],
  description: '',
})

const canSubmit = computed(() => {
  return selectedFile.value && formData.value.title.trim() && formData.value.categoryId !== ''
})

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleFileSelect = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0]
    if (!formData.value.title) {
      const name = target.files[0].name
      formData.value.title = name.substring(0, name.lastIndexOf('.')) || name
    }
  }
}

const handleDrop = (e: DragEvent) => {
  isDragging.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files.length > 0) {
    selectedFile.value = e.dataTransfer.files[0]
    if (!formData.value.title) {
      const name = e.dataTransfer.files[0].name
      formData.value.title = name.substring(0, name.lastIndexOf('.')) || name
    }
  }
}

const removeFile = () => {
  selectedFile.value = null
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !formData.value.tags.includes(tag)) {
    formData.value.tags.push(tag)
  }
  newTag.value = ''
}

const removeTag = (index: number) => {
  formData.value.tags.splice(index, 1)
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const getFileType = (filename: string): string => {
  const ext = filename.split('.').pop()?.toLowerCase()
  const typeMap: Record<string, string> = {
    pdf: 'PDF',
    doc: 'Word',
    docx: 'Word',
    md: 'Markdown',
    txt: 'TXT',
    ppt: 'PPT',
    pptx: 'PPT',
  }
  return typeMap[ext || ''] || ext?.toUpperCase() || '未知'
}

const handleCancel = () => {
  selectedFile.value = null
  formData.value = {
    title: '',
    categoryId: '',
    tags: [],
    description: '',
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const handleUpload = async () => {
  if (!canSubmit.value || !selectedFile.value) return

  isUploading.value = true
  try {
    const file = selectedFile.value
    let content = formData.value.description
    if (/\.(md|markdown|txt)$/i.test(file.name)) {
      try {
        content = await file.text()
      } catch {
        content = formData.value.description
      }
    }

    await docsApi.create({
      title: formData.value.title.trim(),
      summary: formData.value.description,
      content,
      categoryId: Number(formData.value.categoryId),
      tags: formData.value.tags.join(','),
      status: 1,
    })

    notify('文档上传成功！', 'success')
    router.push('/')
  } catch (e: unknown) {
    const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    notify('上传失败：' + (message || '请稍后再试'), 'error')
  } finally {
    isUploading.value = false
  }
}

watch(selectedFile, (newFile) => {
  if (newFile && !formData.value.title) {
    const name = newFile.name
    formData.value.title = name.substring(0, name.lastIndexOf('.')) || name
  }
})

onMounted(async () => {
  try {
    const tree = await categoriesApi.tree()
    const flat: CategoryVO[] = []
    const walk = (list: CategoryVO[]) => {
      for (const c of list) {
        flat.push(c)
        if (c.children) walk(c.children)
      }
    }
    walk(tree)
    categories.value = flat
  } catch {
    categories.value = []
  }
})
</script>

<style scoped>
@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slide-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fade-in 0.3s ease-out;
}

.animate-slide-up {
  animation: slide-up 0.4s ease-out;
}
</style>
