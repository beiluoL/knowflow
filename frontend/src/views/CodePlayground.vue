<template>
  <div class="playground-page">
    <!-- ===== 顶部导航栏 ===== -->
    <div class="playground-header">
      <div class="header-left">
        <button type="button" class="back-btn" title="返回列表" @click="goBack">
          <Icon name="arrow-left" :size="18" />
        </button>
        <div class="header-info">
          <span class="diff-badge" :class="`diff-${question.difficulty ?? 0}`">{{ difficultyLabel(question.difficulty) }}</span>
          <span class="lang-tag" :style="{ background: langColorBg(question.language), color: langColor(question.language) }">{{ langLabel(question.language) }}</span>
          <h1 class="header-title">{{ question.title || '加载中...' }}</h1>
        </div>
      </div>
      <div class="header-right">
        <select v-model="selectedLanguage" class="lang-select" @change="onLanguageChange">
          <option value="javascript">JavaScript</option>
          <option value="typescript">TypeScript</option>
          <option value="python">Python</option>
          <option value="java">Java</option>
          <option value="sql">SQL</option>
        </select>
        <button
          type="button"
          class="header-btn ai-btn"
          :disabled="aiAnswering"
          @click="handleAiAnswer"
        >
          <Icon name="sparkles" :size="14" />
          <span>{{ aiAnswering ? 'AI 思考中...' : 'AI 回答' }}</span>
        </button>
        <button type="button" class="header-btn hint-btn" @click="showHint = !showHint">
          <Icon name="lightbulb" :size="14" />
          <span>提示</span>
        </button>
      </div>
    </div>

    <!-- ===== 主体：左右分栏 ===== -->
    <div class="playground-body">
      <!-- ===== 左侧：题目描述 ===== -->
      <div class="problem-panel">
        <!-- 加载状态 -->
        <div v-if="loading" class="loading-block">
          <div class="spinner"></div>
          <p>加载题目...</p>
        </div>

        <template v-else>
          <div v-if="loadError" class="error-block">{{ loadError }}</div>

          <template v-else>
            <div class="panel-section">
              <h2 class="section-heading">题目描述</h2>
              <p class="problem-desc">{{ question.description || '暂无描述' }}</p>
            </div>

            <div v-if="question.exampleInput || question.exampleOutput" class="panel-section">
              <h3 class="subsection-heading">示例</h3>
              <div class="example-block">
                <div v-if="question.exampleInput" class="example-row">
                  <span class="example-label">输入：</span>
                  <code class="example-code">{{ question.exampleInput }}</code>
                </div>
                <div v-if="question.exampleOutput" class="example-row">
                  <span class="example-label">输出：</span>
                  <code class="example-code">{{ question.exampleOutput }}</code>
                </div>
              </div>
            </div>

            <div v-if="question.hint && showHint" class="panel-section">
              <h3 class="subsection-heading">
                <Icon name="lightbulb" :size="14" style="color: var(--kb-state-warning); display: inline; margin-right: 4px;" />
                提示
              </h3>
              <div class="hint-block">{{ question.hint }}</div>
            </div>

            <!-- SQL 题目显示可用表 -->
            <div v-if="selectedLanguage === 'sql'" class="panel-section">
              <h3 class="subsection-heading">内置示例数据库</h3>
              <p class="problem-desc" style="font-size: 13px;">
                可用表：<code class="example-code">users</code>、<code class="example-code">orders</code>、<code class="example-code">products</code>
              </p>
              <p class="problem-desc" style="font-size: 12px; margin-top: 4px;">
                可使用 <code class="example-code">SHOW TABLES</code> 查看所有表，<code class="example-code">DESCRIBE 表名</code> 查看表结构
              </p>
            </div>

            <div v-if="testCases.length > 0" class="panel-section">
              <h3 class="subsection-heading">测试用例</h3>
              <div v-for="(tc, idx) in testCases" :key="idx" class="testcase-item">
                <div class="testcase-header">
                  <span class="testcase-num">用例 {{ idx + 1 }}</span>
                  <span v-if="tcResults[idx]" class="testcase-result" :class="tcResults[idx].passed ? 'pass' : 'fail'">
                    <Icon :name="tcResults[idx].passed ? 'check-circle' : 'x-circle'" :size="12" />
                    {{ tcResults[idx].passed ? '通过' : '失败' }}
                  </span>
                </div>
                <div class="testcase-body">
                  <div class="testcase-row">
                    <span class="testcase-label">输入：</span>
                    <code>{{ tc.input || '(无)' }}</code>
                  </div>
                  <div class="testcase-row">
                    <span class="testcase-label">期望：</span>
                    <code>{{ tc.expected || '(无)' }}</code>
                  </div>
                  <div v-if="tcResults[idx]" class="testcase-row">
                    <span class="testcase-label">实际：</span>
                    <code :class="tcResults[idx].passed ? 'text-success' : 'text-error'">{{ tcResults[idx].actual || '(无输出)' }}</code>
                  </div>
                </div>
              </div>
            </div>

            <!-- 提交统计 -->
            <div v-if="question.submitCount" class="panel-section">
              <h3 class="subsection-heading">提交统计</h3>
              <div class="stats-block">
                <div class="stats-item">
                  <span class="stats-label">累计提交</span>
                  <span class="stats-value">{{ question.submitCount }} 次</span>
                </div>
                <div class="stats-item">
                  <span class="stats-label">通过次数</span>
                  <span class="stats-value" style="color: var(--kb-state-success);">{{ question.passCount || 0 }} 次</span>
                </div>
                <div class="stats-item">
                  <span class="stats-label">通过率</span>
                  <span class="stats-value">{{ passRate }}%</span>
                </div>
              </div>
            </div>
          </template>
        </template>
      </div>

      <!-- ===== 拖拽分隔线 ===== -->
      <div class="resizer" @mousedown="startResize"></div>

      <!-- ===== 右侧：代码编辑器 + 控制台 ===== -->
      <div class="editor-panel">
        <!-- 代码编辑器 -->
        <div class="editor-container">
          <div class="editor-toolbar">
            <div class="editor-tabs">
              <span class="editor-tab active">
                <Icon name="code" :size="14" />
                <span>code.{{ fileExt }}</span>
              </span>
            </div>
            <div class="editor-actions">
              <button type="button" class="editor-action-btn" title="重置代码" @click="resetCode">
                <Icon name="rotate-ccw" :size="14" />
              </button>
              <button type="button" class="editor-action-btn" title="格式化" @click="formatCode">
                <Icon name="align-left" :size="14" />
              </button>
            </div>
          </div>
          <div class="editor-wrapper">
            <div class="line-numbers">
              <span v-for="n in lineCount" :key="n">{{ n }}</span>
            </div>
            <textarea
              ref="codeAreaRef"
              v-model="userCode"
              class="code-textarea"
              spellcheck="false"
              placeholder="在此编写你的代码..."
              @keydown="handleKeydown"
              @scroll="syncScroll"
            ></textarea>
          </div>
        </div>

        <!-- 控制台输出 -->
        <div class="console-container" :style="{ height: consoleHeight + 'px' }">
          <div class="console-header">
            <div class="console-tabs">
              <button
                type="button"
                class="console-tab"
                :class="{ active: activeTab === 'output' }"
                @click="activeTab = 'output'"
              >
                <Icon name="terminal" :size="12" />
                运行结果
              </button>
              <button
                type="button"
                class="console-tab"
                :class="{ active: activeTab === 'testcase' }"
                @click="activeTab = 'testcase'"
              >
                <Icon name="check-square" :size="12" />
                测试结果
                <span v-if="testCaseSummary" class="tab-badge" :class="testCaseSummary.allPassed ? 'pass' : 'fail'">
                  {{ testCaseSummary.passed }}/{{ testCaseSummary.total }}
                </span>
              </button>
            </div>
            <button v-if="runResult || tcResults.length" type="button" class="console-clear" @click="clearConsole">
              <Icon name="x" :size="12" />
              清除
            </button>
          </div>
          <div class="console-body">
            <!-- 运行结果 Tab -->
            <div v-show="activeTab === 'output'">
              <div v-if="!runResult" class="console-empty">
                <Icon name="terminal" :size="32" style="opacity: 0.3;" />
                <p>点击「运行代码」查看输出结果</p>
              </div>
              <div v-else class="console-output">
                <div class="output-status" :class="runResult.error ? 'error' : 'success'">
                  <Icon :name="runResult.error ? 'x-circle' : 'check-circle'" :size="14" />
                  <span>{{ runResult.error ? '运行出错' : '运行完成' }}</span>
                  <span class="output-time">{{ runResult.time }}ms</span>
                </div>
                <pre v-if="runResult.output" class="output-content">{{ runResult.output }}</pre>
                <pre v-if="runResult.error" class="output-error">{{ runResult.error }}</pre>
              </div>
            </div>
            <!-- 测试结果 Tab -->
            <div v-show="activeTab === 'testcase'">
              <div v-if="tcResults.length === 0" class="console-empty">
                <Icon name="check-square" :size="32" style="opacity: 0.3;" />
                <p>点击「提交答案」运行全部测试用例</p>
              </div>
              <div v-else class="console-output">
                <div class="output-status" :class="testCaseSummary?.allPassed ? 'success' : 'error'">
                  <Icon :name="testCaseSummary?.allPassed ? 'check-circle' : 'x-circle'" :size="14" />
                  <span>{{ testCaseSummary?.allPassed ? '全部通过' : '部分未通过' }}</span>
                  <span class="output-time">{{ testCaseSummary?.passed }}/{{ testCaseSummary?.total }} 个用例通过</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="editor-footer">
          <div class="footer-left">
            <span class="footer-info">
              <Icon name="file-code" :size="12" />
              {{ selectedLanguage }}
            </span>
            <span class="footer-info">
              <Icon name="hash" :size="12" />
              {{ lineCount }} 行
            </span>
            <span class="footer-info">
              <Icon name="type" :size="12" />
              {{ userCode.length }} 字符
            </span>
          </div>
          <div class="footer-right">
            <button
              type="button"
              class="footer-btn ai-foot-btn"
              :disabled="aiAnswering"
              @click="handleAiAnswer"
              :title="aiAnswering ? 'AI 正在生成代码...' : 'AI 自动识别题目并生成代码'"
            >
              <Icon name="sparkles" :size="14" />
              <span>{{ aiAnswering ? 'AI 思考中...' : 'AI 回答' }}</span>
            </button>
            <button type="button" class="footer-btn run-btn" :disabled="running" @click="runCode">
              <Icon name="play" :size="14" />
              <span>{{ running ? '运行中...' : '运行代码' }}</span>
            </button>
            <button type="button" class="footer-btn submit-btn" :disabled="submitting" @click="submitCode">
              <Icon name="send" :size="14" />
              <span>{{ submitting ? '提交中...' : '提交答案' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 代码练习编辑器（LeetCode 风格）
 * 左侧题目描述 + 右侧代码编辑器 + 控制台输出
 * 支持 JavaScript/TypeScript 浏览器端运行、SQL 内置模拟执行、其他语言给出友好提示
 * 题目数据从后端 /api/code-questions/{id} 加载，提交结果上报后端统计
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { chatApi } from '@/api/chat'
import { codeQuestionApi } from '@/api/codeQuestion'
import { runSql } from '@/utils/sqlSimulator'
import type { CodeQuestionVO, CodeTestCase } from '@/api/types'

const route = useRoute()
const router = useRouter()

const question = ref<CodeQuestionVO>({})
const selectedLanguage = ref('javascript')
const userCode = ref('')
const showHint = ref(false)
const running = ref(false)
const submitting = ref(false)
const aiAnswering = ref(false)
const loading = ref(true)
const loadError = ref('')
const activeTab = ref<'output' | 'testcase'>('output')
const runResult = ref<{ output: string; error: string | null; time: number } | null>(null)
const codeAreaRef = ref<HTMLTextAreaElement | null>(null)

// 测试用例
const testCases = ref<CodeTestCase[]>([])
const tcResults = ref<{ passed: boolean; actual: string }[]>([])

// 控制台高度
const consoleHeight = ref(200)

// 默认代码模板（当后端未提供 codeTemplate 时使用）
const DEFAULT_TEMPLATES: Record<string, string> = {
  javascript: `// 在此编写你的 JavaScript 代码
// 可以使用 console.log 输出结果

function solution() {
  // TODO: 实现你的解决方案
  console.log('Hello, KnowFlow!');
}

solution();`,
  typescript: `// 在此编写你的 TypeScript 代码
// 可以使用 console.log 输出结果

function solution(): void {
  // TODO: 实现你的解决方案
  console.log('Hello, KnowFlow!');
}

solution();`,
  python: `# 在此编写你的 Python 代码
# 注意：在线运行暂仅支持 JavaScript/TypeScript/SQL

def solution():
    # TODO: 实现你的解决方案
    print("Hello, KnowFlow!")

solution()`,
  java: `// 在此编写你的 Java 代码
// 注意：在线运行暂仅支持 JavaScript/TypeScript/SQL

public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello, KnowFlow!");
    }
}`,
  sql: `-- 在此编写你的 SQL 代码
-- 内置示例数据库：users、orders、products
-- 可执行 SELECT / SHOW TABLES / DESCRIBE

SELECT * FROM users LIMIT 5;`,
}

const fileExt = computed(() => {
  const map: Record<string, string> = { javascript: 'js', typescript: 'ts', python: 'py', java: 'java', sql: 'sql' }
  return map[selectedLanguage.value] || 'js'
})

const lineCount = computed(() => userCode.value.split('\n').length)

const testCaseSummary = computed(() => {
  if (tcResults.value.length === 0) return null
  const passed = tcResults.value.filter((r) => r.passed).length
  return { passed, total: tcResults.value.length, allPassed: passed === tcResults.value.length }
})

const passRate = computed(() => {
  const submit = question.value.submitCount || 0
  if (submit === 0) return 0
  return Math.round(((question.value.passCount || 0) / submit) * 100)
})

const difficultyLabel = (d?: number) => (d === 0 ? '简单' : d === 1 ? '中等' : '困难')
const langLabel = (lang?: string) => {
  const map: Record<string, string> = {
    javascript: 'JavaScript',
    typescript: 'TypeScript',
    python: 'Python',
    java: 'Java',
    sql: 'SQL',
  }
  return map[lang || ''] || lang || '-'
}

const LANG_COLORS: Record<string, string> = {
  javascript: '#3B6FE0',
  typescript: '#3B6FE0',
  python: '#10B981',
  java: '#F59E0B',
  sql: '#EF4444',
}
const langColor = (lang?: string) => LANG_COLORS[lang || ''] || 'var(--kb-muted-foreground)'
const langColorBg = (lang?: string) => {
  const hex = LANG_COLORS[lang || '']
  return hex ? `${hex}14` : 'var(--kb-muted)'
}

const onLanguageChange = () => {
  userCode.value = DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
  runResult.value = null
  tcResults.value = []
}

const resetCode = () => {
  // 优先使用题目自带的代码模板
  if (question.value.codeTemplate) {
    userCode.value = question.value.codeTemplate
  } else {
    userCode.value = DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
  }
  runResult.value = null
  tcResults.value = []
  notify('代码已重置', 'info')
}

const formatCode = () => {
  const lines = userCode.value.split('\n')
  const formatted = lines
    .map((line) => line.replace(/\s+$/, ''))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
  userCode.value = formatted
  notify('代码已格式化', 'success')
}

const clearConsole = () => {
  runResult.value = null
  tcResults.value = []
}

/** Tab 键支持：插入两个空格而非切换焦点 */
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Tab') {
    e.preventDefault()
    const el = codeAreaRef.value
    if (!el) return
    const start = el.selectionStart
    const end = el.selectionEnd
    userCode.value = userCode.value.substring(0, start) + '  ' + userCode.value.substring(end)
    nextTick(() => {
      el.selectionStart = el.selectionEnd = start + 2
    })
  }
}

/** 同步行号滚动 */
const syncScroll = (e: Event) => {
  const el = e.target as HTMLTextAreaElement
  const lineNumbers = el.previousElementSibling as HTMLElement
  if (lineNumbers) {
    lineNumbers.scrollTop = el.scrollTop
  }
}

/** 运行 JavaScript 代码（浏览器端实际执行） */
const runJavaScript = (code: string): { output: string; error: string | null } => {
  const logs: string[] = []
  const originalLog = console.log
  const originalError = console.error
  const originalWarn = console.warn

  console.log = (...args: unknown[]) => {
    logs.push(args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '))
  }
  console.error = (...args: unknown[]) => {
    logs.push('[ERROR] ' + args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '))
  }
  console.warn = (...args: unknown[]) => {
    logs.push('[WARN] ' + args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '))
  }

  try {
    // eslint-disable-next-line no-new-func
    const fn = new Function(code)
    const result = fn()
    if (result !== undefined) {
      logs.push(String(result))
    }
    return { output: logs.join('\n') || '(无输出)', error: null }
  } catch (e) {
    return {
      output: logs.join('\n'),
      error: e instanceof Error ? e.message : String(e),
    }
  } finally {
    console.log = originalLog
    console.error = originalError
    console.warn = originalWarn
  }
}

/** 运行代码：JS/TS 浏览器端执行；SQL 调用模拟执行器；其他语言提示 */
const runCode = async () => {
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  running.value = true
  activeTab.value = 'output'
  const startTime = performance.now()

  // 模拟异步执行（保证 UI 响应）
  await new Promise((r) => setTimeout(r, 100))

  try {
    let output = ''
    let error: string | null = null

    if (selectedLanguage.value === 'javascript' || selectedLanguage.value === 'typescript') {
      const result = runJavaScript(userCode.value)
      output = result.output
      error = result.error
    } else if (selectedLanguage.value === 'sql') {
      const result = runSql(userCode.value)
      output = result.output
      error = result.error
    } else {
      output = `[${selectedLanguage.value}] 在线运行暂仅支持 JavaScript/TypeScript/SQL\n当前语言请参考代码模板在本地环境运行。\n\n代码预览：\n${userCode.value.substring(0, 200)}${userCode.value.length > 200 ? '...' : ''}`
    }

    const elapsed = Math.round(performance.now() - startTime)
    runResult.value = { output, error, time: elapsed }
    if (error) {
      notify('代码运行出错，请检查控制台', 'error')
    }
  } catch (e) {
    runResult.value = {
      output: '',
      error: e instanceof Error ? e.message : String(e),
      time: Math.round(performance.now() - startTime),
    }
  } finally {
    running.value = false
  }
}

/**
 * 提交答案：对所有测试用例执行用户代码并校验输出。
 * - JS/TS：浏览器端 new Function 执行，捕获 console 输出
 * - SQL：调用 sqlSimulator 执行每条用例的 input（用例 input 即为待执行的 SQL）
 * - 其他语言：标记为「暂不支持在线评测」
 * 校验完成后调用后端 /submit 接口记录统计。
 */
const submitCode = async () => {
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  if (testCases.value.length === 0) {
    notify('该题目暂无测试用例，无法提交', 'warning')
    return
  }
  submitting.value = true
  activeTab.value = 'testcase'

  await new Promise((r) => setTimeout(r, 200))

  const results: { passed: boolean; actual: string }[] = []

  for (const tc of testCases.value) {
    if (selectedLanguage.value === 'javascript' || selectedLanguage.value === 'typescript') {
      // 拼装代码：用户代码 + 测试用例的 input（input 可调用函数或为表达式）
      // 简化策略：执行用户代码 + input，捕获 console 输出，与 expected 比对
      const wrapped = `${userCode.value}\ntry { ${tc.input} } catch(e) { console.error(e.message); }`
      const result = runJavaScript(wrapped)
      const actual = result.output.trim()
      const expected = (tc.expected || '').trim()
      // 通过条件：实际输出与期望完全一致，或包含期望
      const passed = !result.error && (actual === expected || (expected && actual.includes(expected)))
      results.push({ passed, actual: result.error ? result.error : actual })
    } else if (selectedLanguage.value === 'sql') {
      // SQL 题目：用例的 input 即为要执行的 SQL；执行后比较 output
      const result = runSql(tc.input || userCode.value)
      const actual = result.output.trim()
      const expected = (tc.expected || '').trim()
      const passed = !result.error && (actual === expected || (expected && actual.includes(expected)))
      results.push({ passed, actual: result.error ? result.error : actual })
    } else {
      results.push({ passed: false, actual: '(暂不支持该语言在线评测)' })
    }
  }
  tcResults.value = results

  const passed = results.filter((r) => r.passed).length
  const total = results.length

  // 上报到后端记录统计
  try {
    await codeQuestionApi.submit(Number(route.params.id), {
      code: userCode.value,
      language: selectedLanguage.value,
      total,
      passCount: passed,
    })
  } catch (e: unknown) {
    // 上报失败不影响用户验证结果展示，仅提示
    console.warn('提交统计上报失败：', getApiError(e))
  }

  if (passed === total) {
    notify(`恭喜！全部 ${total} 个测试用例通过`, 'success')
  } else {
    notify(`${passed}/${total} 个测试用例通过，请继续努力`, 'warning')
  }
  submitting.value = false
}

/** AI 回答：识别左侧题目，自动生成代码并填入编辑器 */
const handleAiAnswer = async () => {
  if (aiAnswering.value) return
  if (!question.value.title) {
    notify('题目尚未加载', 'warning')
    return
  }
  aiAnswering.value = true
  try {
    const langLabelMap: Record<string, string> = {
      javascript: 'JavaScript',
      typescript: 'TypeScript',
      python: 'Python',
      java: 'Java',
      sql: 'SQL',
    }
    const targetLang = langLabelMap[selectedLanguage.value] || 'JavaScript'
    const prompt = `你是一个编程专家。请根据以下编程题目，用 ${targetLang} 编写一个完整可运行的解决方案。

题目标题：${question.value.title}
难度：${difficultyLabel(question.value.difficulty)}
题目描述：
${question.value.description || ''}

示例：
输入：${question.value.exampleInput || ''}
输出：${question.value.exampleOutput || ''}

${question.value.solutionHint ? '解题提示：' + question.value.solutionHint : ''}

要求：
1. 使用 ${targetLang} 语言编写
2. 代码必须完整可运行，包含必要的函数定义和调用
3. 如有需要可使用 console.log / print / System.out.println 输出结果
4. 只返回代码本身，不要使用 markdown 代码块包裹，不要解释说明
5. 代码要简洁高效，有适当注释`

    const res = await chatApi.send({ content: prompt } as never)
    const text = res && (res as { content?: string }).content
    if (!text) throw new Error('AI 未返回内容')

    // 清理 AI 返回的 markdown 代码块标记
    let code = text.trim()
    const codeBlockMatch = code.match(/^```[a-zA-Z]*\n([\s\S]*?)\n```$/)
    if (codeBlockMatch) {
      code = codeBlockMatch[1]
    } else {
      code = code.replace(/^```[a-zA-Z]*\n?/, '').replace(/\n?```$/, '')
    }

    userCode.value = code.trim()
    notify('AI 已生成代码并填入编辑器，点击「运行代码」查看结果', 'success')
  } catch (e: unknown) {
    notify('AI 回答失败：' + getApiError(e), 'error')
  } finally {
    aiAnswering.value = false
  }
}

const goBack = () => {
  router.push('/learning/code-practice')
}

/** 从后端加载题目详情 */
const loadQuestion = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const id = Number(route.params.id)
    if (!id) {
      loadError.value = '题目 ID 无效'
      return
    }
    const data = await codeQuestionApi.detail(id)
    question.value = data
    // 设置语言：优先用题目主语言
    selectedLanguage.value = (data.language || 'javascript').toLowerCase()
    // 设置初始代码：优先用题目模板
    userCode.value = data.codeTemplate || DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
    // 解析测试用例
    try {
      const parsed = data.testCases ? JSON.parse(data.testCases) : []
      testCases.value = Array.isArray(parsed) ? parsed : []
    } catch {
      testCases.value = []
    }
  } catch (e: unknown) {
    loadError.value = '加载题目失败：' + getApiError(e)
  } finally {
    loading.value = false
  }
}

// 拖拽调整面板大小
let isResizing = false

const startResize = () => {
  isResizing = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const handleResize = (e: MouseEvent) => {
  if (!isResizing) return
  const container = document.querySelector('.playground-body') as HTMLElement
  if (!container) return
  const rect = container.getBoundingClientRect()
  const leftWidth = ((e.clientX - rect.left) / rect.width) * 100
  if (leftWidth > 20 && leftWidth < 80) {
    container.style.gridTemplateColumns = `${leftWidth}% 6px 1fr`
  }
}

const stopResize = () => {
  isResizing = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  loadQuestion()
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.playground-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--kb-background);
}

/* ===== 顶部导航栏 ===== */
.playground-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}
.back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.lang-select {
  height: 32px;
  padding: 0 10px;
  font-size: 13px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  cursor: pointer;
}
.header-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease;
}
.header-btn:hover {
  background: var(--kb-muted);
}
.header-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* AI 回答按钮（头部） */
.ai-btn {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.ai-btn:hover:not(:disabled) {
  background: rgba(59, 111, 224, 0.15);
}
.ai-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.lang-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

/* ===== 主体布局 ===== */
.playground-body {
  display: grid;
  grid-template-columns: 40% 6px 1fr;
  flex: 1;
  min-height: 0;
}

/* ===== 拖拽分隔线 ===== */
.resizer {
  cursor: col-resize;
  background: var(--kb-border);
  transition: background 0.15s ease;
}
.resizer:hover {
  background: var(--kb-primary);
}

/* ===== 左侧题目面板 ===== */
.problem-panel {
  overflow-y: auto;
  padding: 20px;
  background: var(--kb-card);
}
.panel-section {
  margin-bottom: 24px;
}
.section-heading {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 10px;
}
.subsection-heading {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.problem-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-foreground);
  white-space: pre-wrap;
}
.example-block {
  background: var(--kb-background);
  border-radius: var(--kb-radius-sm);
  padding: 12px;
  border: 1px solid var(--kb-border);
}
.example-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 4px;
}
.example-row:last-child {
  margin-bottom: 0;
}
.example-label {
  color: var(--kb-muted-foreground);
  font-weight: 500;
  flex-shrink: 0;
}
.example-code {
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  color: var(--kb-primary);
}
.hint-block {
  background: rgba(245, 158, 11, 0.06);
  border: 1px solid rgba(245, 158, 11, 0.15);
  border-radius: var(--kb-radius-sm);
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-foreground);
}
.testcase-item {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  margin-bottom: 8px;
  overflow: hidden;
}
.testcase-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--kb-background);
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.testcase-num {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.testcase-result {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
}
.testcase-result.pass {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-state-success);
}
.testcase-result.fail {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-state-error);
}
.testcase-body {
  padding: 10px 12px;
}
.testcase-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
}
.testcase-row:last-child {
  margin-bottom: 0;
}
.testcase-label {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.text-success {
  color: var(--kb-state-success);
}
.text-error {
  color: var(--kb-state-error);
}

/* 提交统计 */
.stats-block {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 10px;
}
.stats-item {
  text-align: center;
}
.stats-label {
  display: block;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  margin-bottom: 2px;
}
.stats-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}

/* 加载/错误状态 */
.loading-block,
.error-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.error-block {
  color: var(--kb-state-error);
}
.spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 右侧编辑器面板 ===== */
.editor-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--kb-card);
}
.editor-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 12px;
  border-bottom: 1px solid var(--kb-border);
  background: var(--kb-background);
  flex-shrink: 0;
}
.editor-tabs {
  display: flex;
  gap: 4px;
}
.editor-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-primary);
  background: var(--kb-card);
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
}
.editor-actions {
  display: flex;
  gap: 4px;
}
.editor-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.editor-action-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 代码编辑器 */
.editor-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
  background: #1e1e2e;
  position: relative;
}
.line-numbers {
  display: flex;
  flex-direction: column;
  padding: 16px 8px 16px 16px;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #6c7086;
  text-align: right;
  user-select: none;
  overflow: hidden;
  flex-shrink: 0;
  min-width: 40px;
}
.line-numbers span {
  height: 20.8px;
}
.code-textarea {
  flex: 1;
  padding: 16px 16px 16px 8px;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: transparent;
  color: #cdd6f4;
  border: none;
  outline: none;
  resize: none;
  white-space: pre;
  overflow: auto;
  tab-size: 2;
}
.code-textarea::placeholder {
  color: #585b70;
}

/* 控制台 */
.console-container {
  flex-shrink: 0;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-card);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 12px;
  border-bottom: 1px solid var(--kb-border);
  background: var(--kb-background);
  flex-shrink: 0;
}
.console-tabs {
  display: flex;
  gap: 4px;
}
.console-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  border-radius: var(--kb-radius-sm);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.console-tab:hover {
  background: var(--kb-muted);
}
.console-tab.active {
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 600;
}
.tab-badge.pass {
  background: rgba(16, 185, 129, 0.15);
  color: var(--kb-state-success);
}
.tab-badge.fail {
  background: rgba(239, 68, 68, 0.15);
  color: var(--kb-state-error);
}
.console-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.15s ease;
}
.console-clear:hover {
  color: var(--kb-foreground);
}
.console-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.console-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.console-output {
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 13px;
}
.output-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
}
.output-status.success {
  color: var(--kb-state-success);
}
.output-status.error {
  color: var(--kb-state-error);
}
.output-time {
  margin-left: auto;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  font-weight: 400;
}
.output-content {
  background: var(--kb-background);
  border-radius: var(--kb-radius-sm);
  padding: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.output-error {
  background: rgba(239, 68, 68, 0.06);
  border-radius: var(--kb-radius-sm);
  padding: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-state-error);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 8px 0 0;
}

/* 底部操作栏 */
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-card);
  flex-shrink: 0;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.footer-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.footer-right {
  display: flex;
  gap: 8px;
}
.footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.run-btn {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}
.run-btn:hover:not(:disabled) {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.submit-btn {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.submit-btn:hover:not(:disabled) {
  opacity: 0.9;
}

/* AI 回答按钮（底部） */
.ai-foot-btn {
  background: rgba(139, 92, 246, 0.08);
  color: #8B5CF6;
  border: 1px solid rgba(139, 92, 246, 0.3);
}
.ai-foot-btn:hover:not(:disabled) {
  background: rgba(139, 92, 246, 0.15);
  border-color: rgba(139, 92, 246, 0.5);
}
.footer-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 难度徽标 */
.diff-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}
.diff-0 {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-state-success);
}
.diff-1 {
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-state-warning);
}
.diff-2 {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-state-error);
}

/* 响应式 */
@media (max-width: 768px) {
  .playground-body {
    grid-template-columns: 1fr;
    grid-template-rows: 40% 1fr;
  }
  .resizer {
    display: none;
  }
  .problem-panel {
    border-bottom: 1px solid var(--kb-border);
  }
}
</style>
