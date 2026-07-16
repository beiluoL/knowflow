import type { Doc, TocItem } from '@/types'

export const docDetailContent = `
<h2 id="introduction">一、引言</h2>
<p>Vue 3 带来了全新的 Composition API，这是一种基于函数的 API，它让我们可以更灵活地组织组件逻辑。与 Options API 相比，Composition API 提供了更好的代码复用性和类型推断支持。</p>

<h3 id="what-is-composition-api">1.1 什么是 Composition API</h3>
<p>Composition API 是 Vue 3 中引入的一组新的 API，它允许我们使用函数来组织组件逻辑。通过 <code>setup</code> 函数，我们可以在组件创建之前执行代码，并返回响应式数据和方法供模板使用。</p>

<blockquote>
  <p>Composition API 并不是要取代 Options API，而是提供了另一种选择。在实际项目中，你可以根据需要混合使用两种 API。</p>
</blockquote>

<h3 id="why-composition-api">1.2 为什么需要 Composition API</h3>
<p>在大型组件中，使用 Options API 可能会导致相关逻辑分散在不同的选项中（data、methods、computed 等）。Composition API 解决了这个问题，让我们可以将相关逻辑组织在一起。</p>

<h2 id="core-concepts">二、核心概念</h2>

<h3 id="ref">2.1 ref</h3>
<p><code>ref</code> 用于创建一个响应式的引用对象。它接受一个内部值并返回一个响应式且可变的 ref 对象。</p>

<pre><code class="language-typescript">import { ref } from 'vue'

const count = ref(0)
console.log(count.value) // 0

count.value++
console.log(count.value) // 1</code></pre>

<p>ref 的特点：</p>
<ul>
  <li>可以包装基本类型和对象类型</li>
  <li>访问值需要使用 <code>.value</code></li>
  <li>在模板中会自动解包，不需要 <code>.value</code></li>
</ul>

<h3 id="reactive">2.2 reactive</h3>
<p><code>reactive</code> 用于创建一个响应式的对象。与 ref 不同，reactive 直接返回原始对象的响应式代理。</p>

<pre><code class="language-typescript">import { reactive } from 'vue'

const state = reactive({
  count: 0,
  name: 'Vue 3'
})

console.log(state.count) // 0
state.count++</code></pre>

<h3 id="computed">2.3 computed</h3>
<p><code>computed</code> 用于创建计算属性。计算属性的值会根据其依赖的响应式数据自动更新。</p>

<pre><code class="language-typescript">import { ref, computed } from 'vue'

const firstName = ref('张')
const lastName = ref('三')

const fullName = computed(() => {
  return firstName.value + lastName.value
})

console.log(fullName.value) // 张三</code></pre>

<h3 id="watch">2.4 watch</h3>
<p><code>watch</code> 用于监听响应式数据的变化，并在变化时执行副作用函数。</p>

<pre><code class="language-typescript">import { ref, watch } from 'vue'

const count = ref(0)

watch(count, (newVal, oldVal) => {
  console.log(\`count 从 \${oldVal} 变成了 \${newVal}\`)
})

count.value++ // count 从 0 变成了 1</code></pre>

<h2 id="best-practices">三、最佳实践</h2>

<h3 id="composable">3.1 使用 Composables 复用逻辑</h3>
<p>Composition API 最大的优势之一是可以轻松地将逻辑抽取为可复用的函数，称为 Composables。</p>

<table>
  <thead>
    <tr>
      <th>特性</th>
      <th>Options API</th>
      <th>Composition API</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>逻辑组织</td>
      <td>按选项分散</td>
      <td>按功能集中</td>
    </tr>
    <tr>
      <td>代码复用</td>
      <td>mixin（有缺点）</td>
      <td>Composables（推荐）</td>
    </tr>
    <tr>
      <td>类型推断</td>
      <td>较差</td>
      <td>优秀</td>
    </tr>
    <tr>
      <td>学习曲线</td>
      <td>平缓</td>
      <td>略陡</td>
    </tr>
  </tbody>
</table>

<h3 id="script-setup">3.2 使用 &lt;script setup&gt; 语法糖</h3>
<p><code>&lt;script setup&gt;</code> 是 Vue 3 的编译时语法糖，它让我们可以更简洁地编写 Composition API 代码。</p>

<pre><code class="language-vue">&lt;script setup lang="ts"&gt;
import { ref, computed } from 'vue'

const count = ref(0)
const doubled = computed(() => count.value * 2)

const increment = () => {
  count.value++
}
&lt;/script&gt;</code></pre>

<h2 id="summary">四、总结</h2>
<p>Composition API 是 Vue 3 最重要的新特性之一。它提供了更灵活的代码组织方式、更好的类型推断和更强大的代码复用能力。虽然学习曲线比 Options API 略陡，但对于大型项目和复杂组件来说，收益是非常显著的。</p>

<p>建议从简单的组件开始尝试使用 Composition API，逐步体会它的优势。随着经验的积累，你会发现它能让你的代码更加清晰和可维护。</p>
`

export const docToc: TocItem[] = [
  {
    id: 'introduction',
    text: '一、引言',
    level: 2,
    children: [
      { id: 'what-is-composition-api', text: '1.1 什么是 Composition API', level: 3 },
      { id: 'why-composition-api', text: '1.2 为什么需要 Composition API', level: 3 },
    ],
  },
  {
    id: 'core-concepts',
    text: '二、核心概念',
    level: 2,
    children: [
      { id: 'ref', text: '2.1 ref', level: 3 },
      { id: 'reactive', text: '2.2 reactive', level: 3 },
      { id: 'computed', text: '2.3 computed', level: 3 },
      { id: 'watch', text: '2.4 watch', level: 3 },
    ],
  },
  {
    id: 'best-practices',
    text: '三、最佳实践',
    level: 2,
    children: [
      { id: 'composable', text: '3.1 使用 Composables 复用逻辑', level: 3 },
      { id: 'script-setup', text: '3.2 使用 <script setup> 语法糖', level: 3 },
    ],
  },
  {
    id: 'summary',
    text: '四、总结',
    level: 2,
  },
]

export const relatedDocs: Doc[] = [
  {
    id: '2',
    title: 'TypeScript 高级类型详解',
    summary: '全面介绍 TypeScript 的高级类型特性，包括条件类型、映射类型、模板字面量类型等。',
    categoryId: '1',
    categoryName: '前端开发',
    tags: ['TypeScript', '类型系统'],
    author: '李四',
    viewCount: 2845,
    likeCount: 156,
    collectCount: 112,
    status: 'published',
    createdAt: '2024-05-20T09:00:00Z',
    updatedAt: '2024-07-08T11:20:00Z',
  },
  {
    id: '10',
    title: 'React Hooks 最佳实践',
    summary: '深入理解 React Hooks 的工作原理，掌握 useState、useEffect、useMemo 等的正确使用姿势。',
    categoryId: '1',
    categoryName: '前端开发',
    tags: ['React', 'Hooks', '前端'],
    author: '冯十二',
    viewCount: 2789,
    likeCount: 145,
    collectCount: 110,
    status: 'published',
    createdAt: '2024-05-25T10:00:00Z',
    updatedAt: '2024-07-09T13:45:00Z',
  },
  {
    id: '7',
    title: '动态规划算法精讲',
    summary: '系统讲解动态规划的核心思想、常见题型和解题套路，配合经典例题助你彻底掌握 DP。',
    categoryId: '6',
    categoryName: '算法与数据结构',
    tags: ['算法', '动态规划', 'LeetCode'],
    author: '周九',
    viewCount: 3567,
    likeCount: 201,
    collectCount: 178,
    status: 'published',
    createdAt: '2024-05-15T10:00:00Z',
    updatedAt: '2024-07-11T14:00:00Z',
  },
  {
    id: '5',
    title: 'Transformer 架构原理解析',
    summary: '从注意力机制到多头注意力，从 Encoder 到 Decoder，逐层拆解 Transformer 的核心架构与数学原理。',
    categoryId: '4',
    categoryName: '人工智能',
    tags: ['深度学习', 'Transformer', 'NLP'],
    author: '钱七',
    viewCount: 4521,
    likeCount: 267,
    collectCount: 203,
    status: 'published',
    createdAt: '2024-04-25T08:00:00Z',
    updatedAt: '2024-07-15T13:00:00Z',
  },
]

export const detailedDoc: Doc = {
  id: '1',
  title: 'Vue 3 Composition API 完全指南',
  summary: '深入理解 Vue 3 的 Composition API，包括 setup、ref、reactive、computed、watch 等核心概念的使用方法和最佳实践。',
  content: docDetailContent,
  categoryId: '1',
  categoryName: '前端开发',
  tags: ['Vue', 'Vue3', 'Composition API'],
  author: '张三',
  authorAvatar: '',
  viewCount: 3256,
  likeCount: 128,
  collectCount: 89,
  wordCount: 4520,
  readTime: 18,
  status: 'published',
  createdAt: '2024-06-15T10:00:00Z',
  updatedAt: '2024-07-10T14:30:00Z',
}
