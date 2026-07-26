// 前端静态假数据（学习中心：任务、宠物、榜单、路径、章节、闪卡与复习计划）。
export interface StudyTask {
  id: string
  title: string
  duration: number
  completed: boolean
}

export interface LearningPet {
  name: string
  level: number
  mood: string
  energy: number
  exp: number
  maxExp: number
  avatar: string
}

export interface RankItem {
  id: string
  name: string
  avatar: string
  studyHours: number
  rank: number
  isCurrentUser?: boolean
}

export interface LearningPath {
  id: string
  title: string
  description: string
  difficulty: 'beginner' | 'intermediate' | 'advanced'
  progress: number
  chaptersCount: number
  totalDuration: number
  coverGradient: string
  icon: string
  suitableFor: string
}

export interface Chapter {
  id: string
  pathId: string
  title: string
  duration: number
  order: number
  completed: boolean
  isCurrent?: boolean
  content: ChapterContent
}

export interface ChapterContent {
  title: string
  sections: {
    heading: string
    content: string
    code?: string
  }[]
  knowledgePoints: string[]
}

export interface FlashCard {
  id: string
  category: string
  difficulty: 'easy' | 'medium' | 'hard'
  question: string
  answer: string
  nextReviewDate?: string
}

export interface ReviewDay {
  date: string
  cards: FlashCard[]
}

export const todayStudyData = {
  completedTasks: 3,
  totalTasks: 5,
  studyMinutes: 45,
  streakDays: 7,
  goalProgress: 60,
  pomodorosCompleted: 2,
}

export const studyTasks: StudyTask[] = [
  { id: '1', title: '阅读 Vue 3 组合式 API 文档', duration: 30, completed: true },
  { id: '2', title: '完成 TypeScript 基础练习', duration: 25, completed: true },
  { id: '3', title: '复习 Tailwind CSS 常用类名', duration: 15, completed: true },
  { id: '4', title: '学习 Pinia 状态管理', duration: 40, completed: false },
  { id: '5', title: '做一道算法题', duration: 30, completed: false },
]

export const learningPet: LearningPet = {
  name: '小鹰',
  level: 5,
  mood: '开心',
  energy: 75,
  exp: 350,
  maxExp: 500,
  avatar: 'owl',
}

export const weeklyRank: RankItem[] = [
  { id: '1', name: '学霸君', avatar: 'X', studyHours: 42.5, rank: 1 },
  { id: '2', name: '努力的小明', avatar: 'M', studyHours: 38.2, rank: 2 },
  { id: '3', name: '张三', avatar: 'Z', studyHours: 28.6, rank: 3, isCurrentUser: true },
  { id: '4', name: '学习达人', avatar: 'D', studyHours: 25.3, rank: 4 },
  { id: '5', name: '代码小能手', avatar: 'N', studyHours: 22.1, rank: 5 },
  { id: '6', name: '前端小白', avatar: 'Q', studyHours: 18.7, rank: 6 },
  { id: '7', name: '后端大佬', avatar: 'H', studyHours: 15.4, rank: 7 },
]

export const learningPaths: LearningPath[] = [
  {
    id: '1',
    title: 'Vue 3 前端开发入门到精通',
    description: '从基础到进阶，系统学习 Vue 3 全家桶，掌握现代前端开发技能',
    difficulty: 'beginner',
    progress: 35,
    chaptersCount: 12,
    totalDuration: 480,
    coverGradient: 'from-green-400 to-blue-500',
    icon: 'Code',
    suitableFor: '前端初学者、想要系统学习 Vue 的开发者',
  },
  {
    id: '2',
    title: 'TypeScript 高级编程',
    description: '深入理解 TypeScript 类型系统，提升代码质量和开发效率',
    difficulty: 'intermediate',
    progress: 60,
    chaptersCount: 8,
    totalDuration: 320,
    coverGradient: 'from-blue-500 to-purple-600',
    icon: 'FileCode',
    suitableFor: '有 JavaScript 基础，想要进阶 TypeScript 的开发者',
  },
  {
    id: '3',
    title: '算法与数据结构',
    description: '系统学习常用算法和数据结构，提升编程思维和解题能力',
    difficulty: 'advanced',
    progress: 20,
    chaptersCount: 15,
    totalDuration: 600,
    coverGradient: 'from-orange-400 to-red-500',
    icon: 'Brain',
    suitableFor: '想要提升算法能力的开发者',
  },
  {
    id: '4',
    title: 'React 18 实战指南',
    description: '掌握 React 最新特性，构建高性能现代化应用',
    difficulty: 'intermediate',
    progress: 0,
    chaptersCount: 10,
    totalDuration: 420,
    coverGradient: 'from-cyan-400 to-blue-500',
    icon: 'Layers',
    suitableFor: '有前端基础，想要学习 React 的开发者',
  },
  {
    id: '5',
    title: 'Node.js 后端开发',
    description: '从零开始学习 Node.js，构建完整的后端服务',
    difficulty: 'beginner',
    progress: 45,
    chaptersCount: 14,
    totalDuration: 560,
    coverGradient: 'from-emerald-500 to-teal-600',
    icon: 'Server',
    suitableFor: '前端转全栈、想要学习后端开发的同学',
  },
  {
    id: '6',
    title: '设计模式精讲',
    description: '学习 23 种经典设计模式，写出优雅可维护的代码',
    difficulty: 'advanced',
    progress: 10,
    chaptersCount: 23,
    totalDuration: 720,
    coverGradient: 'from-violet-500 to-fuchsia-600',
    icon: 'Puzzle',
    suitableFor: '有一定编程经验，想要提升架构能力的开发者',
  },
]

export const chapters: Chapter[] = [
  {
    id: '1-1',
    pathId: '1',
    title: 'Vue 3 简介与环境搭建',
    duration: 25,
    order: 1,
    completed: true,
    content: {
      title: 'Vue 3 简介与环境搭建',
      sections: [
        {
          heading: 'Vue 3 是什么',
          content: 'Vue (发音为 /vjuː/，类似 view) 是一款用于构建用户界面的 JavaScript 框架。它基于标准 HTML、CSS 和 JavaScript 构建，并提供了一套声明式的、组件化的编程模型，帮助你高效地开发用户界面。无论是简单还是复杂的界面，Vue 都可以胜任。',
        },
        {
          heading: '环境搭建',
          content: '在开始使用 Vue 3 之前，我们需要先搭建开发环境。推荐使用 Vite 来创建 Vue 项目，它提供了极快的开发体验。',
          code: `npm create vue@latest

cd your-project
npm install
npm run dev`,
        },
        {
          heading: '第一个 Vue 组件',
          content: '让我们来创建第一个 Vue 组件，感受一下 Vue 的声明式编程风格。',
          code: `<template>
  <div class="hello">
    <h1>{{ message }}</h1>
    <button @click="count++">
      点击了 {{ count }} 次
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const message = 'Hello Vue 3!'
const count = ref(0)
</script>

<style scoped>
.hello {
  text-align: center;
  padding: 20px;
}
</style>`,
        },
      ],
      knowledgePoints: ['Vue 3 核心概念', 'Vite 项目搭建', '单文件组件', '响应式基础', '模板语法'],
    },
  },
  {
    id: '1-2',
    pathId: '1',
    title: '模板语法与指令',
    duration: 30,
    order: 2,
    completed: true,
    content: {
      title: '模板语法与指令',
      sections: [
        {
          heading: '插值',
          content: '数据绑定最常见的形式就是使用 "Mustache" 语法 (双大括号) 的文本插值。',
          code: `<span>Message: {{ msg }}</span>`,
        },
        {
          heading: '常用指令',
          content: 'Vue 提供了许多内置指令，用于操作 DOM、条件渲染、列表渲染等。',
          code: `<!-- v-bind 动态绑定属性 -->
<img v-bind:src="imageSrc" />
<img :src="imageSrc" />

<!-- v-on 事件监听 -->
<button v-on:click="handleClick">点击</button>
<button @click="handleClick">点击</button>

<!-- v-if 条件渲染 -->
<p v-if="isVisible">你能看到我</p>

<!-- v-for 列表渲染 -->
<ul>
  <li v-for="item in items" :key="item.id">
    {{ item.name }}
  </li>
</ul>

<!-- v-model 双向绑定 -->
<input v-model="message" />`,
        },
      ],
      knowledgePoints: ['文本插值', 'v-bind', 'v-on', 'v-if/v-show', 'v-for', 'v-model', '修饰符'],
    },
  },
  {
    id: '1-3',
    pathId: '1',
    title: '响应式基础',
    duration: 35,
    order: 3,
    completed: false,
    isCurrent: true,
    content: {
      title: '响应式基础',
      sections: [
        {
          heading: 'ref 和 reactive',
          content: 'Vue 3 提供了两种创建响应式数据的方式：ref 和 reactive。ref 用于基本类型，reactive 用于对象类型。',
          code: `import { ref, reactive } from 'vue'

// ref - 基本类型
const count = ref(0)
console.log(count.value) // 0
count.value++
console.log(count.value) // 1

// reactive - 对象类型
const state = reactive({
  name: 'Vue',
  version: '3.0'
})
console.log(state.name) // 'Vue'
state.version = '3.2'`,
        },
        {
          heading: 'computed 计算属性',
          content: '计算属性用于基于现有状态派生出新的值，具有缓存特性。',
          code: `import { ref, computed } from 'vue'

const firstName = ref('张')
const lastName = ref('三')

const fullName = computed(() => {
  return firstName.value + lastName.value
})

console.log(fullName.value) // '张三'`,
        },
      ],
      knowledgePoints: ['ref', 'reactive', 'computed', 'watch', 'watchEffect', '响应式原理'],
    },
  },
  {
    id: '1-4',
    pathId: '1',
    title: '组合式 API',
    duration: 40,
    order: 4,
    completed: false,
    content: {
      title: '组合式 API',
      sections: [
        {
          heading: '为什么使用组合式 API',
          content: '组合式 API 是 Vue 3 引入的新特性，它提供了一种更灵活的方式来组织组件逻辑，特别适合复杂组件和逻辑复用。',
        },
        {
          heading: 'setup 函数',
          content: 'setup 函数是组合式 API 的入口点，所有的响应式数据、计算属性、方法等都在这里定义。',
          code: `<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const count = ref(0)
const doubled = computed(() => count.value * 2)

const increment = () => {
  count.value++
}

onMounted(() => {
  console.log('组件已挂载')
})
</script>`,
        },
      ],
      knowledgePoints: ['setup 函数', '生命周期钩子', '自定义 Hook', 'provide/inject', '逻辑复用'],
    },
  },
  {
    id: '1-5',
    pathId: '1',
    title: '组件基础',
    duration: 35,
    order: 5,
    completed: false,
    content: {
      title: '组件基础',
      sections: [
        {
          heading: '组件注册与使用',
          content: '组件是 Vue 应用的基本构建块，学习如何定义、注册和使用组件是非常重要的。',
        },
        {
          heading: 'Props 与事件',
          content: '父子组件之间通过 Props 向下传递数据，通过事件向上传递消息。',
          code: `// 子组件
const props = defineProps<{
  title: string
  count?: number
}>()

const emit = defineEmits<{
  (e: 'update', value: number): void
  (e: 'close'): void
}>()`,
        },
      ],
      knowledgePoints: ['组件定义', 'Props', 'Events', 'v-model', '插槽 Slots', '动态组件'],
    },
  },
]

export const flashCards: FlashCard[] = [
  {
    id: '1',
    category: 'Vue 3',
    difficulty: 'easy',
    question: 'Vue 3 中 ref 和 reactive 的区别是什么？',
    answer: 'ref 用于基本类型数据，需要通过 .value 访问值；reactive 用于对象类型数据，可以直接访问属性。ref 可以接收任意类型，当接收对象时内部会自动用 reactive 转换。reactive 有局限性，不能直接替换整个对象，否则会失去响应性。',
    nextReviewDate: '2024-01-15',
  },
  {
    id: '2',
    category: 'Vue 3',
    difficulty: 'medium',
    question: 'computed 和 watch 的区别是什么？',
    answer: 'computed 是计算属性，具有缓存特性，只有依赖变化时才重新计算，主要用于派生数据；watch 是侦听器，没有缓存，监听数据变化执行副作用操作。computed 适合同步计算场景，watch 适合异步或开销较大的操作。',
    nextReviewDate: '2024-01-16',
  },
  {
    id: '3',
    category: 'TypeScript',
    difficulty: 'easy',
    question: 'TypeScript 中 interface 和 type 的区别是什么？',
    answer: '1. interface 只能定义对象类型，type 可以定义任意类型\n2. interface 可以声明合并，type 不行\n3. type 可以使用联合类型、交叉类型、元组等\n4. interface 可以 extends 和 implements，type 用 & 交叉\n5. 推荐优先使用 interface，需要高级特性时用 type',
    nextReviewDate: '2024-01-15',
  },
  {
    id: '4',
    category: 'TypeScript',
    difficulty: 'hard',
    question: '什么是泛型？泛型的主要用途是什么？',
    answer: '泛型（Generics）是 TypeScript 中用于创建可重用组件的工具，允许组件可以处理多种类型而不是单一类型。\n\n主要用途：\n1. 创建可复用的函数、类、接口\n2. 保持类型安全，不丢失类型信息\n3. 用于工具类型，如 Partial、Required、Pick 等\n4. 约束类型，确保类型具有某些属性',
    nextReviewDate: '2024-01-17',
  },
  {
    id: '5',
    category: 'JavaScript',
    difficulty: 'medium',
    question: 'Promise 的三种状态是什么？状态转换有什么特点？',
    answer: 'Promise 有三种状态：pending（进行中）、fulfilled（已成功）、rejected（已失败）。\n\n特点：\n1. 状态只能从 pending 转换为 fulfilled 或 rejected\n2. 状态一旦改变就不会再变\n3. 状态改变后，再添加回调函数也会立即得到结果\n4. 状态转换只能发生一次',
    nextReviewDate: '2024-01-16',
  },
  {
    id: '6',
    category: 'JavaScript',
    difficulty: 'easy',
    question: 'var、let、const 的区别是什么？',
    answer: '1. var 是函数作用域，存在变量提升，可以重复声明\n2. let 是块级作用域，存在暂时性死区，不能重复声明\n3. const 也是块级作用域，声明时必须赋值，不能重新赋值（但对象属性可修改）\n\n推荐优先使用 const，需要重新赋值时用 let，尽量避免使用 var。',
    nextReviewDate: '2024-01-15',
  },
  {
    id: '7',
    category: 'CSS',
    difficulty: 'medium',
    question: 'Flex 布局的常用属性有哪些？',
    answer: '容器属性：\n- flex-direction: 主轴方向\n- flex-wrap: 是否换行\n- justify-content: 主轴对齐\n- align-items: 交叉轴对齐\n- align-content: 多轴线对齐\n\n项目属性：\n- flex-grow: 放大比例\n- flex-shrink: 缩小比例\n- flex-basis: 基准大小\n- flex: 简写\n- order: 排列顺序\n- align-self: 单独对齐',
    nextReviewDate: '2024-01-18',
  },
  {
    id: '8',
    category: '算法',
    difficulty: 'hard',
    question: '快速排序的时间复杂度是多少？最好和最坏情况分别是什么？',
    answer: '平均时间复杂度：O(n log n)\n最好情况：O(n log n) - 每次划分都均匀分成两部分\n最坏情况：O(n²) - 数组已排序或完全逆序，每次划分只减少一个元素\n\n空间复杂度：O(log n) - 递归调用栈\n\n优化方法：\n1. 三数取中法选择基准\n2. 随机选择基准\n3. 小数组使用插入排序',
    nextReviewDate: '2024-01-19',
  },
]

export const flashCardCategories = ['全部', 'Vue 3', 'TypeScript', 'JavaScript', 'CSS', '算法']
export const flashCardDifficulties = ['全部', 'easy', 'medium', 'hard']

export const reviewDays: ReviewDay[] = [
  {
    date: '2024-01-15',
    cards: [flashCards[0], flashCards[2], flashCards[5]],
  },
  {
    date: '2024-01-16',
    cards: [flashCards[1], flashCards[4]],
  },
  {
    date: '2024-01-17',
    cards: [flashCards[3]],
  },
  {
    date: '2024-01-18',
    cards: [flashCards[6]],
  },
  {
    date: '2024-01-19',
    cards: [flashCards[7]],
  },
]

export const todayReview = {
  total: 5,
  completed: 2,
  progress: 40,
}
