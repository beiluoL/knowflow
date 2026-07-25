<template>
  <div class="space-y-6 animate-fade-in">
    <div v-if="loading" class="practice-loading">
      <div class="loading-spinner"></div>
      <p class="text-gray-500 mt-3">加载题目中...</p>
    </div>

    <div v-else-if="error" class="practice-error">
      <Icon name="alert-circle" :size="48" class="text-red-400" />
      <p class="text-gray-700 font-medium mt-3">加载失败</p>
      <p class="text-gray-500 text-sm mt-1">{{ error }}</p>
      <Button variant="primary" size="sm" class="mt-4" @click="loadQuestions">
        <Icon name="refresh-cw" :size="14" class="mr-1" />
        重新加载
      </Button>
    </div>

    <div v-else-if="questions.length === 0" class="practice-empty">
      <div class="empty-icon-box">
        <Icon name="inbox" :size="48" class="text-gray-300" />
      </div>
      <p class="text-gray-700 font-medium mt-3">暂无练习题</p>
      <p class="text-gray-500 text-sm mt-1">稍后再来看看吧～</p>
    </div>

    <template v-else>
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">代码练习</h1>
          <p class="text-gray-500 text-sm mt-1">通过实战练习提升编程能力</p>
        </div>
        <div class="flex items-center gap-3">
          <Badge variant="primary" class="text-sm">
            <Icon name="zap" :size="14" class="mr-1" />
            连续打卡 {{ streak }} 天
          </Badge>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-6">
          <Card>
            <template #header>
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <Badge :variant="currentQuestion?.difficulty === 'easy' ? 'success' : currentQuestion?.difficulty === 'medium' ? 'warning' : 'danger'">
                    {{ currentQuestion?.difficulty === 'easy' ? '简单' : currentQuestion?.difficulty === 'medium' ? '中等' : '困难' }}
                  </Badge>
                  <span class="text-sm text-gray-500">{{ currentQuestion?.category }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <button class="p-2 hover:bg-gray-100 rounded-lg transition-colors" title="上一题">
                    <Icon name="chevron-left" :size="20" />
                  </button>
                  <span class="text-sm text-gray-500">{{ currentIndex + 1 }} / {{ questions.length }}</span>
                  <button class="p-2 hover:bg-gray-100 rounded-lg transition-colors" title="下一题">
                    <Icon name="chevron-right" :size="20" />
                  </button>
                </div>
              </div>
            </template>

            <div class="space-y-4">
              <h3 class="text-lg font-medium text-gray-800">{{ currentQuestion?.title }}</h3>
              <p class="text-gray-600 leading-relaxed">{{ currentQuestion?.description }}</p>

              <div class="bg-gray-50 rounded-lg p-4">
                <h4 class="text-sm font-medium text-gray-700 mb-2">示例</h4>
                <div class="space-y-2">
                  <div class="text-sm">
                    <span class="text-gray-500">输入：</span>
                    <code class="bg-gray-100 px-2 py-0.5 rounded">{{ currentQuestion?.example?.input }}</code>
                  </div>
                  <div class="text-sm">
                    <span class="text-gray-500">输出：</span>
                    <code class="bg-gray-100 px-2 py-0.5 rounded">{{ currentQuestion?.example?.output }}</code>
                  </div>
                </div>
              </div>

              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium text-gray-700">编写代码</label>
                  <select
                    v-model="selectedLanguage"
                    class="px-3 py-1 border border-gray-200 rounded text-sm focus:outline-none focus:border-primary-500"
                  >
                    <option value="javascript">JavaScript</option>
                    <option value="python">Python</option>
                    <option value="java">Java</option>
                    <option value="typescript">TypeScript</option>
                  </select>
                </div>
                <textarea
                  v-model="userCode"
                  rows="12"
                  class="w-full px-4 py-3 border border-gray-200 rounded-lg font-mono text-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
                  placeholder="在此输入你的代码..."
                ></textarea>
              </div>

              <div class="flex items-center gap-3">
                <Button icon-name="play" @click="runCode">运行代码</Button>
                <Button variant="secondary" icon-name="check" @click="submitCode">提交答案</Button>
                <Button variant="ghost" icon-name="lightbulb" @click="showHint = !showHint">提示</Button>
              </div>

              <div v-if="showHint" class="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <div class="flex items-start gap-2">
                  <Icon name="lightbulb" :size="18" class="text-yellow-500 flex-shrink-0 mt-0.5" />
                  <p class="text-sm text-yellow-800">{{ currentQuestion?.hint }}</p>
                </div>
              </div>

              <div v-if="runResult" class="border rounded-lg overflow-hidden">
                <div class="px-4 py-2 bg-gray-50 border-b flex items-center justify-between">
                  <span class="text-sm font-medium" :class="runResult.success ? 'text-green-600' : 'text-red-600'">
                    {{ runResult.success ? '运行成功' : '运行失败' }}
                  </span>
                  <span class="text-xs text-gray-500">耗时 {{ runResult.time }}ms</span>
                </div>
                <div class="p-4 font-mono text-sm">
                  <pre class="text-gray-700">{{ runResult.output }}</pre>
                </div>
              </div>
            </div>
          </Card>
        </div>

        <div class="space-y-6">
          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">今日进度</h3>
            </template>
            <div class="space-y-4">
              <div>
                <div class="flex items-center justify-between mb-1">
                  <span class="text-sm text-gray-500">完成题目</span>
                  <span class="text-sm font-medium">{{ todayProgress.completed }}/{{ todayProgress.total }}</span>
                </div>
                <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-primary-500 rounded-full transition-all duration-500"
                    :style="{ width: (todayProgress.completed / todayProgress.total * 100) + '%' }"
                  ></div>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div class="text-center p-3 bg-green-50 rounded-lg">
                  <p class="text-2xl font-bold text-green-600">{{ todayProgress.correct }}</p>
                  <p class="text-xs text-gray-500">正确</p>
                </div>
                <div class="text-center p-3 bg-red-50 rounded-lg">
                  <p class="text-2xl font-bold text-red-600">{{ todayProgress.wrong }}</p>
                  <p class="text-xs text-gray-500">错误</p>
                </div>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">题目列表</h3>
            </template>
            <div class="space-y-2 max-h-96 overflow-y-auto">
              <button
                v-for="(q, index) in questions" :key="q.id"
                class="w-full text-left px-3 py-2 rounded-lg transition-colors flex items-center gap-3"
                :class="[
                  currentIndex === index ? 'bg-primary-50 text-primary-700' : 'hover:bg-gray-50',
                  q.status === 'completed' ? 'opacity-60' : ''
                ]"
                @click="currentIndex = index"
              >
                <span class="text-sm font-medium w-6">{{ index + 1 }}</span>
                <span class="text-sm flex-1 truncate">{{ q.title }}</span>
                <Icon v-if="q.status === 'completed'" name="check-circle" :size="16" class="text-green-500" />
              </button>
            </div>
          </Card>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { notify } from '@/utils/toast'
import { ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'

const streak = ref(7)
const currentIndex = ref(0)
const selectedLanguage = ref('javascript')
const userCode = ref('')
const showHint = ref(false)
const runResult = ref<{ success: boolean; output: string; time: number } | null>(null)
const loading = ref(true)
const error = ref('')

const todayProgress = ref({
  completed: 3,
  total: 5,
  correct: 2,
  wrong: 1,
})

interface Question {
  id: string
  title: string
  description: string
  category: string
  difficulty: 'easy' | 'medium' | 'hard'
  example: { input: string; output: string }
  hint: string
  status: 'pending' | 'completed'
}

const mockQuestions: Question[] = [
  {
    id: '1',
    title: '两数之和',
    description: '给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。',
    category: '数组',
    difficulty: 'easy',
    example: { input: 'nums = [2,7,11,15], target = 9', output: '[0,1]' },
    hint: '可以使用哈希表来优化查找过程，将时间复杂度降低到 O(n)。',
    status: 'completed',
  },
  {
    id: '2',
    title: '反转链表',
    description: '给你单链表的头节点 head，请你反转链表，并返回反转后的链表。',
    category: '链表',
    difficulty: 'medium',
    example: { input: 'head = [1,2,3,4,5]', output: '[5,4,3,2,1]' },
    hint: '使用三个指针：prev、current、next，迭代反转指针方向。',
    status: 'pending',
  },
  {
    id: '3',
    title: '二叉树的中序遍历',
    description: '给定一个二叉树的根节点 root，返回它的中序遍历。',
    category: '树',
    difficulty: 'medium',
    example: { input: 'root = [1,null,2,3]', output: '[1,3,2]' },
    hint: '中序遍历顺序：左子树 -> 根节点 -> 右子树。可以使用递归或栈实现。',
    status: 'pending',
  },
]

const questions = ref<Question[]>([])
const currentQuestion = ref<Question | null>(null)

async function loadQuestions(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await new Promise(resolve => setTimeout(resolve, 600))
    questions.value = [...mockQuestions]
    currentQuestion.value = questions.value[0] || null
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败'
    error.value = `题目加载失败：${message}`
    notify('题目加载失败', 'error')
  } finally {
    loading.value = false
  }
}

loadQuestions()

const runCode = () => {
  runResult.value = {
    success: true,
    output: '[0, 1]\n运行成功！',
    time: 45,
  }
}

const submitCode = () => {
  notify('答案提交成功！', 'success')
  if (questions.value[currentIndex.value]) {
    questions.value[currentIndex.value].status = 'completed'
  }
  todayProgress.value.completed++
  todayProgress.value.correct++
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

.practice-loading,
.practice-error,
.practice-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e5e7eb;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-icon-box {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
