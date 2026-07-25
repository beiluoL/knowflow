<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">智能写作</h1>
          <p class="text-gray-500 text-sm mt-1">AI 辅助写作，提升知识表达能力</p>
        </div>
        <Button icon-name="zap" @click="showTemplateModal = true">选择模板</Button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-6">
          <Card>
            <template #header>
              <div class="flex items-center justify-between">
                <h3 class="font-semibold text-gray-800">写作区域</h3>
                <div class="flex items-center gap-2">
                  <span class="text-xs text-gray-400">{{ wordCount }} 字</span>
                  <Button size="sm" variant="ghost" icon-name="copy" @click="copyContent">复制</Button>
                </div>
              </div>
            </template>

            <div class="space-y-4">
              <input
                v-model="title"
                type="text"
                placeholder="请输入标题..."
                class="w-full px-4 py-3 text-lg font-medium border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
              />
              <textarea
                v-model="content"
                rows="16"
                placeholder="开始写作..."
                class="w-full px-4 py-3 border border-gray-200 rounded-lg resize-none focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 leading-relaxed"
              ></textarea>
            </div>
          </Card>

          <Card v-if="feedbackLoading">
            <template #header>
              <div class="flex items-center gap-2">
                <Icon name="bot" :size="20" class="text-primary-500" />
                <h3 class="font-semibold text-gray-800">AI 批改建议</h3>
              </div>
            </template>
            <div class="feedback-loading">
              <div class="loading-spinner"></div>
              <p class="text-gray-500 text-sm mt-3">AI 正在分析您的文章...</p>
            </div>
          </Card>

          <Card v-else-if="aiFeedback">
            <template #header>
              <div class="flex items-center gap-2">
                <Icon name="bot" :size="20" class="text-primary-500" />
                <h3 class="font-semibold text-gray-800">AI 批改建议</h3>
              </div>
            </template>
            <div class="space-y-4">
              <div class="flex items-center gap-4">
                <div class="text-center">
                  <div class="w-16 h-16 rounded-full bg-primary-50 flex items-center justify-center mb-1">
                    <span class="text-xl font-bold text-primary-600">{{ aiFeedback.score }}</span>
                  </div>
                  <span class="text-xs text-gray-500">综合评分</span>
                </div>
                <div class="flex-1 grid grid-cols-3 gap-2">
                  <div class="text-center p-2 bg-green-50 rounded-lg">
                    <p class="text-lg font-bold text-green-600">{{ aiFeedback.grammar }}</p>
                    <p class="text-xs text-gray-500">语法</p>
                  </div>
                  <div class="text-center p-2 bg-blue-50 rounded-lg">
                    <p class="text-lg font-bold text-blue-600">{{ aiFeedback.structure }}</p>
                    <p class="text-xs text-gray-500">结构</p>
                  </div>
                  <div class="text-center p-2 bg-purple-50 rounded-lg">
                    <p class="text-lg font-bold text-purple-600">{{ aiFeedback.content }}</p>
                    <p class="text-xs text-gray-500">内容</p>
                  </div>
                </div>
              </div>
              <div class="space-y-2">
                <div
                  v-for="(suggestion, index) in aiFeedback.suggestions" :key="index"
                  class="flex items-start gap-2 p-3 bg-gray-50 rounded-lg"
                >
                  <Icon name="lightbulb" :size="16" class="text-yellow-500 flex-shrink-0 mt-0.5" />
                  <p class="text-sm text-gray-700">{{ suggestion }}</p>
                </div>
              </div>
            </div>
          </Card>
        </div>

        <div class="space-y-6">
          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">AI 助手</h3>
            </template>
            <div class="space-y-3">
              <Button variant="secondary" class="w-full justify-start" icon-name="sparkles" @click="aiAssist('continue')">
                续写内容
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="edit" @click="aiAssist('polish')">
                润色优化
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="list" @click="aiAssist('outline')">
                生成大纲
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="check" @click="aiAssist('check')">
                语法检查
              </Button>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">写作统计</h3>
            </template>
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">字数</span>
                <span class="text-sm font-medium">{{ wordCount }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">段落</span>
                <span class="text-sm font-medium">{{ paragraphCount }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">阅读时间</span>
                <span class="text-sm font-medium">约 {{ Math.ceil(wordCount / 300) }} 分钟</span>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">历史记录</h3>
            </template>
            <div class="space-y-2 max-h-64 overflow-y-auto">
              <div
                v-for="doc in history" :key="doc.id"
                class="p-3 rounded-lg hover:bg-gray-50 cursor-pointer transition-colors"
                @click="loadDocument(doc)"
              >
                <h4 class="text-sm font-medium text-gray-800 truncate">{{ doc.title || '无标题' }}</h4>
                <p class="text-xs text-gray-400 mt-1">{{ doc.date }} · {{ doc.wordCount }} 字</p>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
import { confirmDialog, notify } from '@/utils/toast'
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'

const title = ref('')
const content = ref('')
const showTemplateModal = ref(false)
const feedbackLoading = ref(false)
const aiFeedback = ref<{
  score: number
  grammar: number
  structure: number
  content: number
  suggestions: string[]
} | null>(null)

const wordCount = computed(() => content.value.length)
const paragraphCount = computed(() => content.value.split('\n').filter(p => p.trim()).length)

const history = [
  { id: '1', title: 'Vue 3 组合式 API 学习笔记', date: '2024-01-15', wordCount: 1200 },
  { id: '2', title: 'JavaScript 闭包详解', date: '2024-01-14', wordCount: 856 },
  { id: '3', title: '数据库索引优化总结', date: '2024-01-13', wordCount: 2341 },
]

const aiAssist = async (type: string) => {
  switch (type) {
    case 'continue':
      content.value += '\n\n[AI 续写内容示例：在此基础上，我们可以进一步探讨...]'
      break
    case 'polish':
      notify('正在润色优化...', 'info')
      break
    case 'outline':
      content.value = '## 大纲\n\n1. 引言\n2. 核心概念\n3. 实践应用\n4. 总结\n\n' + content.value
      break
    case 'check':
      if (!content.value.trim()) {
        notify('请先输入内容再进行批改', 'warning')
        return
      }
      feedbackLoading.value = true
      aiFeedback.value = null
      try {
        await new Promise(resolve => setTimeout(resolve, 1000))
        aiFeedback.value = {
          score: 85,
          grammar: 90,
          structure: 80,
          content: 85,
          suggestions: [
            '建议增加更多实际案例来支撑论点',
            '部分段落可以进一步精简，提高可读性',
            '注意检查专业术语的使用是否准确',
          ],
        }
      } finally {
        feedbackLoading.value = false
      }
      break
  }
}

const copyContent = () => {
  navigator.clipboard.writeText(content.value)
  notify('内容已复制到剪贴板', 'success')
}

const loadDocument = async (doc: { title: string; wordCount: number }) => {
  if (await confirmDialog(`加载文档「${doc.title}」？`)) {
    title.value = doc.title
    content.value = `这是「${doc.title}」的内容示例...`
    aiFeedback.value = null
  }
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.feedback-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e5e7eb;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
