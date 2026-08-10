<template>
  <div class="space-y-4 animate-fade-in">
    <!-- 顶部：返回 + 赛道信息 + 进度 -->
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div class="flex items-center gap-3 min-w-0">
        <button
          type="button"
          class="w-9 h-9 shrink-0 rounded-lg border border-[var(--kb-border)] bg-[var(--kb-card)] flex items-center justify-center transition-colors hover:bg-[var(--kb-muted)] hover:border-[var(--kb-primary)] active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          style="color: var(--kb-foreground);"
          @click="router.push('/challenge')"
        >
          <Icon name="arrow-left" :size="18" />
        </button>
        <div class="min-w-0">
          <h1 class="kb-h2 truncate" style="color: var(--kb-foreground);">{{ detail?.title || '编程挑战' }}</h1>
          <p class="kb-body-sm truncate">
            {{ detail ? `${detail.clearedLevels}/${detail.levelCount} 关 · 逐关解锁，通关得星` : '加载中...' }}
          </p>
        </div>
      </div>
      <div v-if="detail" class="flex items-center flex-wrap gap-4">
        <span class="flex items-center gap-2 text-sm font-semibold tabular-nums" style="color: var(--kb-state-warning);">
          <Icon name="star" :size="16" />{{ detail.earnedStars }}/{{ detail.levelCount * 3 }}
        </span>
        <span class="flex items-center gap-2 text-sm font-semibold tabular-nums" style="color: var(--kb-primary);">
          <Icon name="zap" :size="16" />{{ detail.earnedPoints }}/{{ detail.totalPoints }} 分
        </span>
        <span
          v-if="detail.completed"
          class="flex items-center gap-1 text-[length:var(--kb-fs-caption)] font-medium px-3 py-1 rounded-full"
          style="background: rgba(16, 185, 129, 0.1); color: var(--kb-state-success);"
        >
          <Icon name="trophy" :size="12" />已通关
        </span>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="animate-pulse space-y-3">
        <div class="h-4 rounded" style="background: var(--kb-muted); width: 40%;"></div>
        <div class="h-16 rounded" style="background: var(--kb-muted);"></div>
        <div class="h-4 rounded" style="background: var(--kb-muted); width: 70%;"></div>
      </div>
    </div>

    <template v-else-if="detail">
      <!-- 关卡地图 -->
      <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="kb-h3 mb-4">关卡地图</h3>
        <div class="level-map">
          <template v-for="(lv, idx) in detail.levels" :key="lv.id">
            <div
              class="level-node"
              role="button"
              tabindex="0"
              :class="{
                locked: lv.locked,
                passed: lv.passed,
                current: currentLevel?.id === lv.id,
              }"
              @click="selectLevel(lv)"
              @keydown.enter.prevent="($event.target as HTMLElement).click()"
            >
              <div class="level-circle" :style="levelCircleStyle(lv)">
                <Icon v-if="lv.locked" name="lock" :size="16" />
                <Icon v-else-if="lv.passed" name="check" :size="18" />
                <span v-else class="text-[length:var(--kb-fs-body-md)] font-bold tabular-nums">{{ lv.levelNo }}</span>
              </div>
              <div class="flex items-center gap-1 mt-2">
                <Icon
                  v-for="s in 3"
                  :key="s"
                  name="star"
                  :size="12"
                  :style="{ color: s <= lv.stars ? 'var(--kb-warning)' : 'var(--kb-muted)' }"
                />
              </div>
              <p class="text-[length:var(--kb-fs-caption)] mt-1 truncate max-w-[72px] text-center" style="color: var(--kb-muted-foreground);">
                {{ lv.title }}
              </p>
            </div>
            <div
              v-if="idx < detail.levels.length - 1"
              class="level-link"
              :style="{ background: lv.passed ? themeColor : 'var(--kb-muted)' }"
            ></div>
          </template>
        </div>
      </div>

      <!-- 做题区 -->
      <div v-if="currentLevel" class="flex gap-4 flex-col lg:flex-row items-start">
        <!-- 左：题目 -->
        <div class="w-full lg:w-2/5 shrink-0 space-y-4">
          <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="flex items-center justify-between mb-3">
              <h3 class="kb-h3 min-w-0 truncate">第 {{ currentLevel.levelNo }} 关 · {{ currentLevel.title }}</h3>
              <div class="flex items-center gap-2 shrink-0">
                <span class="diff-badge" :class="`diff-${currentLevel.difficulty ?? 0}`">
                  {{ difficultyLabel(currentLevel.difficulty) }}
                </span>
                <span class="flex items-center gap-1 text-[length:var(--kb-fs-body-sm)] font-semibold tabular-nums" style="color: var(--kb-primary);">
                  <Icon name="zap" :size="14" />{{ currentLevel.points }} 分
                </span>
              </div>
            </div>
            <p class="kb-body whitespace-pre-wrap mb-4" style="color: var(--kb-foreground);">
              {{ currentLevel.description }}
            </p>

            <div v-if="currentLevel.exampleInput" class="mb-3">
              <p class="kb-body-sm font-medium mb-1">示例输入</p>
              <pre class="example-block">{{ currentLevel.exampleInput }}</pre>
            </div>
            <div v-if="currentLevel.exampleOutput" class="mb-3">
              <p class="kb-body-sm font-medium mb-1">示例输出</p>
              <pre class="example-block">{{ currentLevel.exampleOutput }}</pre>
            </div>

            <!-- 提示 -->
            <div v-if="currentLevel.hint">
              <button
                type="button"
                class="flex items-center gap-2 text-[length:var(--kb-fs-body-sm)] font-medium rounded-[var(--kb-radius-sm)] transition-opacity hover:opacity-80 active:opacity-70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                style="color: var(--kb-state-warning);"
                @click="showHint = !showHint"
              >
                <Icon name="lightbulb" :size="14" />{{ showHint ? '收起提示' : '查看提示' }}
              </button>
              <p
                v-if="showHint"
                class="mt-2 text-[length:var(--kb-fs-body-sm)] rounded-lg p-3"
                style="background: rgba(245, 158, 11, 0.08); color: var(--kb-foreground);"
              >{{ currentLevel.hint }}</p>
            </div>
          </div>

          <!-- 本关状态 -->
          <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="grid grid-cols-3 gap-3 text-center">
              <div>
                <div class="flex items-center justify-center gap-1">
                  <Icon
                    v-for="s in 3"
                    :key="s"
                    name="star"
                    :size="16"
                    :style="{ color: s <= currentLevel.stars ? 'var(--kb-warning)' : 'var(--kb-muted)' }"
                  />
                </div>
                <p class="kb-body-sm mt-1">已获星级</p>
              </div>
              <div>
                <p class="text-lg font-bold tabular-nums" style="color: var(--kb-primary);">{{ currentLevel.pointsEarned }}</p>
                <p class="kb-body-sm">已获积分</p>
              </div>
              <div>
                <p class="text-lg font-bold tabular-nums" style="color: var(--kb-foreground);">{{ currentLevel.attempts }}</p>
                <p class="kb-body-sm">提交次数</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 右：编辑器 + 测试结果 -->
        <div class="flex-1 min-w-0 space-y-4 w-full">
          <div class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
            <div class="flex items-center justify-between gap-3 px-4 py-3 border-b" style="border-color: var(--kb-border);">
              <span class="min-w-0 truncate text-[length:var(--kb-fs-body-sm)] font-medium" style="color: var(--kb-foreground);">
                {{ langLabel(currentLevel.language || detail.language) }} 编辑器
              </span>
              <button
                type="button"
                class="flex items-center gap-1 shrink-0 rounded-[var(--kb-radius-sm)] text-[length:var(--kb-fs-body-sm)] text-[color:var(--kb-muted-foreground)] transition-colors hover:text-[color:var(--kb-primary)] active:opacity-70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="resetCode"
              >
                <Icon name="refresh-cw" :size="14" />重置模板
              </button>
            </div>
            <textarea
              ref="codeAreaRef"
              v-model="userCode"
              class="code-editor"
              spellcheck="false"
              placeholder="在这里编写你的代码..."
              @keydown="handleKeydown"
            ></textarea>
            <div class="flex items-center justify-between flex-wrap gap-3 px-4 py-3 border-t" style="border-color: var(--kb-border);">
              <span class="min-w-0 flex-1 text-[length:var(--kb-fs-caption)]" style="color: var(--kb-muted-foreground);">
                共 {{ testCases.length }} 个测试用例，全部通过即可通关
              </span>
              <button
                type="button"
                class="flex items-center gap-2 shrink-0 px-4 py-2 rounded-lg text-sm font-medium transition-opacity hover:opacity-90 active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:opacity-60"
                :style="{ background: themeColor, color: '#fff' }"
                :disabled="submitting"
                @click="submitLevel"
              >
                <Icon name="play" :size="14" />{{ submitting ? '判题中...' : '运行并提交' }}
              </button>
            </div>
          </div>

          <!-- 用例结果 -->
          <div
            v-if="tcResults.length > 0"
            class="rounded-xl border p-4 space-y-2"
            style="background: var(--kb-card); border-color: var(--kb-border);"
          >
            <h3 class="kb-h3 mb-2">测试结果（{{ passedCount }}/{{ tcResults.length }} 通过）</h3>
            <div
              v-for="(r, i) in tcResults"
              :key="i"
              class="rounded-lg p-3 break-words text-[length:var(--kb-fs-body-sm)]"
              :style="{
                background: r.passed ? 'rgba(16, 185, 129, 0.06)' : 'rgba(239, 68, 68, 0.06)',
                border: `1px solid ${r.passed ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.25)'}`,
              }"
            >
              <div class="flex items-center gap-2 mb-1">
                <Icon
                  :name="r.passed ? 'check' : 'x'"
                  :size="14"
                  :style="{ color: r.passed ? 'var(--kb-state-success)' : 'var(--kb-state-error)' }"
                />
                <span class="font-medium" style="color: var(--kb-foreground);">用例 {{ i + 1 }}</span>
              </div>
              <p style="color: var(--kb-muted-foreground);">期望：{{ r.expected }}</p>
              <p style="color: var(--kb-muted-foreground);">实际：{{ r.actual || '(无输出)' }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 通关弹窗 -->
    <div v-if="resultModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-card">
        <div class="text-center">
          <div
            class="w-14 h-14 rounded-full mx-auto flex items-center justify-center mb-3"
            :style="{
              background: resultModal.passed ? 'rgba(16, 185, 129, 0.12)' : 'rgba(245, 158, 11, 0.12)',
              color: resultModal.passed ? 'var(--kb-state-success)' : 'var(--kb-state-warning)',
            }"
          >
            <Icon :name="resultModal.passed ? 'trophy' : 'zap'" :size="24" />
          </div>
          <h3 class="kb-h3 mb-1">
            {{ resultModal.challengeCompleted ? '恭喜通关全部关卡！' : resultModal.passed ? '恭喜通关本关！' : '再接再厉！' }}
          </h3>
          <p class="text-sm mb-4" style="color: var(--kb-muted-foreground);">
            {{ resultModal.passCount }}/{{ resultModal.total }} 个用例通过
            {{ resultModal.passed && !resultModal.firstPass ? '（本关已通关，不重复计分）' : '' }}
          </p>

          <!-- 星级展示 -->
          <div v-if="resultModal.passed" class="flex items-center justify-center gap-2 mb-4">
            <Icon
              v-for="s in 3"
              :key="s"
              name="star"
              :size="32"
              :style="{ color: s <= resultModal.stars ? 'var(--kb-warning)' : 'var(--kb-muted)' }"
            />
          </div>
          <p v-if="resultModal.firstPass" class="text-sm font-semibold mb-4" style="color: var(--kb-primary);">
            +{{ resultModal.pointsEarned }} 积分（累计 {{ resultModal.totalPoints }} 分 · {{ resultModal.totalStars }} 星）
          </p>

          <div class="flex items-center justify-center flex-wrap gap-3">
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium border border-[var(--kb-border)] bg-[var(--kb-card)] transition-colors hover:bg-[var(--kb-muted)] hover:border-[var(--kb-primary)] active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="color: var(--kb-foreground);"
              @click="closeModal"
            >{{ resultModal.passed ? '留在本关' : '继续尝试' }}</button>
            <button
              v-if="resultModal.passed && resultModal.unlockedNext"
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium transition-opacity hover:opacity-90 active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              :style="{ background: themeColor, color: '#fff' }"
              @click="goNextLevel"
            >挑战下一关</button>
            <button
              v-if="resultModal.challengeCompleted"
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-medium transition-opacity hover:opacity-90 active:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              :style="{ background: themeColor, color: '#fff' }"
              @click="router.push('/challenge')"
            >返回挑战列表</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 编程闯关页：关卡地图（逐关解锁）+ 浏览器端执行测试用例判题 + 后端计星级/积分。
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { challengeApi } from '@/api/challenge'
import { notify, getApiError } from '@/utils/toast'
import { normalizeNewlines } from '@/utils/string'
import type { ChallengeDetailVO, ChallengeLevelVO, ChallengeSubmitResultVO } from '@/api/types'

interface TestCase {
  input: string
  expected: string
}

interface TcResult {
  passed: boolean
  expected: string
  actual: string
}

const route = useRoute()
const router = useRouter()

const detail = ref<ChallengeDetailVO | null>(null)
const currentLevel = ref<ChallengeLevelVO | null>(null)
const loading = ref(false)
const submitting = ref(false)
const showHint = ref(false)
const userCode = ref('')
const tcResults = ref<TcResult[]>([])
const resultModal = ref<ChallengeSubmitResultVO | null>(null)
const codeAreaRef = ref<HTMLTextAreaElement | null>(null)

const challengeId = computed(() => Number(route.params.id))
const themeColor = computed(() => detail.value?.themeColor || 'var(--kb-primary)')
const passedCount = computed(() => tcResults.value.filter((r) => r.passed).length)

/** 当前关卡的测试用例（JSON 解析） */
const testCases = computed<TestCase[]>(() => {
  if (!currentLevel.value?.testCases) return []
  try {
    const parsed = JSON.parse(currentLevel.value.testCases) as TestCase[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
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
  return map[lang || ''] || 'JavaScript'
}

/** 关卡节点圆圈配色：锁定灰 / 已通关主题色实底 / 当前描边 */
const levelCircleStyle = (lv: ChallengeLevelVO) => {
  if (lv.locked) {
    return { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }
  }
  if (lv.passed) {
    return { background: themeColor.value, color: '#fff' }
  }
  return {
    background: (detail.value?.themeColor || '#3B6FE0') + '14',
    color: themeColor.value,
    border: `2px solid ${themeColor.value}`,
  }
}

/** 选择关卡：锁定关卡不可进入 */
const selectLevel = (lv: ChallengeLevelVO) => {
  if (lv.locked) {
    notify('该关卡尚未解锁，请先通关上一关', 'warning')
    return
  }
  currentLevel.value = lv
  showHint.value = false
  tcResults.value = []
  userCode.value = normalizeNewlines(lv.lastCode || lv.codeTemplate || '')
}

const resetCode = () => {
  if (!currentLevel.value) return
  userCode.value = normalizeNewlines(currentLevel.value.codeTemplate || '')
  tcResults.value = []
  notify('代码已重置为模板', 'info')
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

/** 浏览器端执行 JS：捕获 console 输出（与代码练习判题一致） */
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
    return { output: logs.join('\n'), error: null }
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

/** 运行全部用例并提交后端判定星级/积分/解锁 */
const submitLevel = async () => {
  if (!detail.value || !currentLevel.value) return
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  if (testCases.value.length === 0) {
    notify('该关卡暂无测试用例', 'warning')
    return
  }
  submitting.value = true
  await new Promise((r) => setTimeout(r, 200))

  // 浏览器端逐用例执行：用户代码 + 用例 input，比较 console 输出与期望
  const results: TcResult[] = []
  for (const tc of testCases.value) {
    const wrapped = `${userCode.value}\ntry { ${tc.input} } catch(e) { console.error(e.message); }`
    const result = runJavaScript(wrapped)
    const actual = result.output.trim()
    const expected = (tc.expected || '').trim()
    const passed = !result.error && (actual === expected || (expected !== '' && actual.includes(expected)))
    results.push({ passed, expected, actual: result.error ? result.error : actual })
  }
  tcResults.value = results
  const passCount = results.filter((r) => r.passed).length

  try {
    const res = await challengeApi.submitLevel(detail.value.id, currentLevel.value.id, {
      code: userCode.value,
      total: results.length,
      passCount,
    })
    resultModal.value = res
    // 同步本地状态（星级/积分/解锁下一关），避免整页刷新
    applyResult(res)
  } catch (e: unknown) {
    notify('提交失败：' + getApiError(e), 'error')
  } finally {
    submitting.value = false
  }
}

/** 将提交结果同步到本地关卡地图状态 */
const applyResult = (res: ChallengeSubmitResultVO) => {
  if (!detail.value || !currentLevel.value) return
  currentLevel.value.attempts = res.attempts
  currentLevel.value.lastCode = userCode.value
  if (res.firstPass) {
    currentLevel.value.passed = true
    currentLevel.value.stars = res.stars
    currentLevel.value.pointsEarned = res.pointsEarned
    detail.value.clearedLevels = res.clearedLevels
    detail.value.earnedPoints = res.totalPoints
    detail.value.earnedStars = res.totalStars
    detail.value.completed = res.challengeCompleted
    // 解锁下一关
    const idx = detail.value.levels.findIndex((l) => l.id === currentLevel.value?.id)
    if (idx >= 0 && idx + 1 < detail.value.levels.length) {
      detail.value.levels[idx + 1].locked = false
    }
  }
}

const closeModal = () => {
  resultModal.value = null
}

/** 通关后进入下一关 */
const goNextLevel = () => {
  if (!detail.value || !currentLevel.value) return
  const idx = detail.value.levels.findIndex((l) => l.id === currentLevel.value?.id)
  const next = idx >= 0 ? detail.value.levels[idx + 1] : undefined
  resultModal.value = null
  if (next) {
    selectLevel(next)
  }
}

const loadDetail = async () => {
  loading.value = true
  try {
    detail.value = await challengeApi.detail(challengeId.value)
    // 默认定位到第一个未通关且已解锁的关卡（全通则第一关）
    const target =
      detail.value.levels.find((l) => !l.locked && !l.passed) || detail.value.levels[0]
    if (target) {
      selectLevel(target)
    }
  } catch (e: unknown) {
    notify('加载挑战详情失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 关卡地图 */
.level-map {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 4px;
  row-gap: 16px;
}

.level-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  min-width: 72px;
  border-radius: var(--kb-radius-md);
}

.level-node.locked {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 键盘焦点环：role="button" 节点可 Tab 聚焦 */
.level-node:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.level-circle {
  width: 44px;
  height: 44px;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.level-node:not(.locked):hover .level-circle {
  transform: scale(1.08);
}

.level-node:not(.locked):active .level-circle {
  transform: scale(0.96);
}

.level-node.current .level-circle {
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.25);
}

.level-link {
  flex: 1;
  min-width: 12px;
  height: 3px;
  border-radius: 2px;
  margin-top: 21px;
}

/* 代码编辑器 */
.code-editor {
  width: 100%;
  min-height: 300px;
  padding: var(--kb-space-4);
  border: none;
  outline: none;
  resize: vertical;
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-family: var(--font-mono);
  font-size: var(--kb-fs-body-sm);
  line-height: 1.65;
  tab-size: 2;
}

.code-editor:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}

/* 示例块 */
.example-block {
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: var(--kb-space-3);
  font-family: var(--font-mono);
  font-size: var(--kb-fs-body-sm);
  line-height: 1.6;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  word-break: break-all;
}

/* 通关弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: var(--kb-space-4);
}

.modal-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: var(--kb-space-6) var(--kb-space-8);
  width: 100%;
  max-width: 400px;
  animation: popIn 0.25s ease-out;
}

@keyframes popIn {
  from { opacity: 0; transform: scale(0.92); }
  to { opacity: 1; transform: scale(1); }
}

/* 难度徽标 */
.diff-badge {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
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

.kb-h2 {
  font-size: var(--kb-fs-h4);
  font-weight: 700;
  line-height: 1.35;
}

.kb-h3 {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: 1.4;
}

.kb-body {
  font-size: var(--kb-fs-body-md);
  line-height: 1.7;
}

.kb-body-sm {
  font-size: var(--kb-fs-body-sm);
  line-height: var(--kb-lh-body-sm);
  color: var(--kb-muted-foreground);
}
</style>
