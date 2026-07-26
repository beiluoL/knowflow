<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">学习模式</h1>
          <p class="text-gray-500 text-sm mt-1">选择适合你的学习方式</p>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <Card
          v-for="mode in learningModes" :key="mode.id"
          hoverable
          class="relative overflow-hidden group cursor-pointer"
          :class="{ 'ring-2 ring-primary-500': selectedMode === mode.id }"
          @click="selectMode(mode.id)"
        >
          <div class="absolute top-0 right-0 w-24 h-24 opacity-10 transition-transform group-hover:scale-110">
            <Icon :name="mode.icon" :size="96" />
          </div>
          <div class="relative z-10">
            <div class="flex items-center justify-between mb-4">
              <div
                class="w-12 h-12 rounded-xl flex items-center justify-center"
                :style="{ backgroundColor: mode.color + '20' }"
              >
                <Icon :name="mode.icon" :size="24" :style="{ color: mode.color }" />
              </div>
              <Badge v-if="mode.isNew" variant="primary">新功能</Badge>
              <Badge v-if="mode.isPopular" variant="warning">热门</Badge>
            </div>
            <h3 class="text-lg font-bold text-gray-800 mb-2">{{ mode.name }}</h3>
            <p class="text-sm text-gray-500 mb-4 leading-relaxed">{{ mode.description }}</p>
            <div class="flex items-center gap-4 text-xs text-gray-400 mb-4">
              <span class="flex items-center gap-1">
                <Icon name="clock" :size="14" />
                {{ mode.duration }}
              </span>
              <span class="flex items-center gap-1">
                <Icon name="target" :size="14" />
                {{ mode.difficulty }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex -space-x-2">
                <div
                  v-for="(user, i) in mode.activeUsers.slice(0, 3)" :key="i"
                  class="w-7 h-7 rounded-full border-2 border-white bg-gray-200 flex items-center justify-center text-xs font-medium text-gray-600"
                >
                  {{ user[0] }}
                </div>
                <div v-if="mode.activeUsers.length > 3" class="w-7 h-7 rounded-full border-2 border-white bg-gray-100 flex items-center justify-center text-xs text-gray-500">
                  +{{ mode.activeUsers.length - 3 }}
                </div>
              </div>
              <span class="text-sm text-primary-500 font-medium group-hover:translate-x-1 transition-transform flex items-center gap-1">
                开始学习
                <Icon name="arrow-right" :size="16" />
              </span>
            </div>
          </div>
        </Card>
      </div>

      <Card v-if="selectedMode" class="animate-fade-in">
        <template #header>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div
                class="w-10 h-10 rounded-lg flex items-center justify-center"
                :style="{ backgroundColor: currentMode.color + '20' }"
              >
                <Icon :name="currentMode.icon" :size="20" :style="{ color: currentMode.color }" />
              </div>
              <div>
                <h3 class="font-semibold text-gray-800">{{ currentMode.name }} 设置</h3>
                <p class="text-sm text-gray-500">自定义你的学习体验</p>
              </div>
            </div>
          </div>
        </template>

        <div class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">学习时长</label>
              <div class="flex items-center gap-3">
                <input
                  v-model="settings.duration"
                  type="range"
                  min="15"
                  max="120"
                  step="15"
                  class="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
                />
                <span class="text-sm font-medium w-16 text-right">{{ settings.duration }} 分钟</span>
              </div>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">题目数量</label>
              <div class="flex items-center gap-3">
                <input
                  v-model="settings.questionCount"
                  type="range"
                  min="5"
                  max="50"
                  step="5"
                  class="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
                />
                <span class="text-sm font-medium w-16 text-right">{{ settings.questionCount }} 题</span>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">选择分类</label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="cat in categories" :key="cat"
                class="px-4 py-2 rounded-lg text-sm border transition-all"
                :class="settings.selectedCategories.includes(cat)
                  ? 'bg-primary-50 border-primary-500 text-primary-700'
                  : 'border-gray-200 text-gray-600 hover:border-gray-300'
                "
                @click="toggleCategory(cat)"
              >
                {{ cat }}
              </button>
            </div>
          </div>

          <div class="flex items-center justify-end gap-3 pt-4 border-t">
            <Button variant="secondary" @click="selectedMode = null">取消</Button>
            <Button icon-name="play" @click="startLearning">开始学习</Button>
          </div>
        </div>
      </Card>
    </div>
</template>

<script setup lang="ts">
// 学习模式选择页：展示多种学习模式卡片并分发到对应学习路由。
import { notify } from '@/utils/toast'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'

const router = useRouter()
const selectedMode = ref<string | null>(null)

const settings = ref({
  duration: 30,
  questionCount: 20,
  selectedCategories: [] as string[],
})

const categories = ['前端开发', '后端开发', '人工智能', '数据库', '算法', '运维']

const learningModes = [
  {
    id: 'challenge',
    name: '挑战模式',
    description: '限时答题挑战，测试你的知识掌握程度，适合快速检验学习成果。',
    icon: 'zap',
    color: '#F59E0B',
    duration: '15-30 分钟',
    difficulty: '中等',
    isPopular: true,
    isNew: false,
    activeUsers: ['张三', '李四', '王五', '赵六', '孙七'],
  },
  {
    id: 'flashcard',
    name: '闪卡记忆',
    description: '基于间隔重复算法的记忆模式，科学高效地记住知识点。',
    icon: 'layers',
    color: '#3B6FE0',
    duration: '20-40 分钟',
    difficulty: '简单',
    isPopular: false,
    isNew: false,
    activeUsers: ['张三', '李四'],
  },
  {
    id: 'quiz',
    name: '智能测验',
    description: 'AI 根据你的薄弱环节智能出题，针对性提升知识短板。',
    icon: 'help-circle',
    color: '#10B981',
    duration: '30-60 分钟',
    difficulty: '自适应',
    isPopular: false,
    isNew: true,
    activeUsers: ['王五', '赵六', '孙七', '周八'],
  },
  {
    id: 'code',
    name: '代码实战',
    description: '在线编程练习，从简单到困难，循序渐进提升编程能力。',
    icon: 'code',
    color: '#8B5CF6',
    duration: '45-90 分钟',
    difficulty: '困难',
    isPopular: true,
    isNew: false,
    activeUsers: ['张三', '李四', '王五'],
  },
  {
    id: 'review',
    name: '复习巩固',
    description: '系统自动安排需要复习的知识点，按照遗忘曲线科学复习。',
    icon: 'refresh-cw',
    color: '#EF4444',
    duration: '20-30 分钟',
    difficulty: '简单',
    isPopular: false,
    isNew: false,
    activeUsers: ['赵六', '孙七'],
  },
  {
    id: 'writing',
    name: '写作练习',
    description: '通过写作输出巩固知识，AI 辅助批改，提升知识表达能力。',
    icon: 'file-text',
    color: '#EC4899',
    duration: '30-60 分钟',
    difficulty: '中等',
    isPopular: false,
    isNew: true,
    activeUsers: ['张三', '周八', '吴九'],
  },
]

const currentMode = computed(() => learningModes.find(m => m.id === selectedMode.value) || learningModes[0])

const selectMode = (id: string) => {
  selectedMode.value = id
}

const toggleCategory = (cat: string) => {
  const index = settings.value.selectedCategories.indexOf(cat)
  if (index > -1) {
    settings.value.selectedCategories.splice(index, 1)
  } else {
    settings.value.selectedCategories.push(cat)
  }
}

// 按所选模式跳转到对应学习页面（仅代码实战/闪卡已实现专门路由）
const startLearning = () => {
  notify(`开始${currentMode.value.name}！`, 'info')
  if (selectedMode.value === 'code') {
    router.push('/learning/code-practice')
  } else if (selectedMode.value === 'flashcard') {
    router.push('/learning/flashcards')
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
</style>
