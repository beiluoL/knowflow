<template>
  <div class="code-solve min-h-screen flex flex-col bg-gray-50">
    <!-- 顶栏 -->
    <header class="h-14 bg-white border-b border-border flex items-center px-4 gap-3 shrink-0">
      <button
        class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        @click="goBack"
      >
        <Icon name="chevron-left" :size="18" /> 题库
      </button>
      <div class="min-w-0 flex-1">
        <h1 class="text-sm font-semibold text-foreground truncate">{{ problem?.title || '算法练习' }}</h1>
      </div>
      <span v-if="problem" class="text-xs px-2 py-0.5 rounded-full font-medium" :class="diffClass(problem.difficulty)">
        {{ diffLabel(problem.difficulty) }}
      </span>
      <!-- 窄屏 Tab -->
      <div class="lg:hidden flex items-center gap-1 border border-border rounded-lg p-0.5">
        <button
          v-for="t in mobileTabs"
          :key="t.key"
          class="px-2.5 py-1 rounded-md text-xs"
          :class="mobileTab === t.key ? 'bg-primary-500 text-white' : 'text-muted-foreground'"
          @click="mobileTab = t.key"
        >{{ t.label }}</button>
      </div>
    </header>

    <!-- 加载 / 错误 -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <Icon name="loader" :size="28" class="animate-spin text-primary-500 mx-auto mb-3" />
        <p class="text-sm text-muted-foreground">正在加载题目…</p>
      </div>
    </div>
    <div v-else-if="loadError" class="flex-1 flex items-center justify-center p-6">
      <div class="text-center max-w-sm">
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">题目加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ loadError }}</p>
        <button class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600" @click="load">
          重试
        </button>
      </div>
    </div>

    <main v-else-if="problem" class="flex-1 flex flex-col lg:flex-row overflow-hidden">
      <!-- 左：题目面板 -->
      <section
        class="problem-pane flex-1 min-w-0 overflow-y-auto p-5 sm:p-7 bg-white"
        :class="{ 'hidden lg:block': mobileTab !== 'desc' }"
        :style="lgPaneStyle"
      >
        <div class="max-w-3xl mx-auto space-y-5">
          <!-- 判题服务门禁提示 -->
          <div
            v-if="!problem.judgeExecutionEnabled"
            class="rounded-xl border border-warning-200 bg-warning-50 px-4 py-3 flex items-start gap-2.5"
          >
            <Icon name="shield-check" :size="18" class="text-warning-600 mt-0.5 shrink-0" />
            <div class="text-xs text-warning-700 leading-relaxed">
              <p class="font-semibold text-sm">安全判题服务尚未就绪</p>
              <p class="mt-0.5">
                当前环境未通过隔离沙箱安全门禁，真实代码执行保持禁用。你仍可编写代码、保存草稿与提交（提交将返回「判题服务未就绪」，不会计分、不会记入错题）。
              </p>
            </div>
          </div>

          <div class="flex items-center gap-2 flex-wrap">
            <span v-if="problem.topicName" class="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-muted-foreground">
              {{ problem.topicName }}
            </span>
            <span
              v-for="tag in tagList"
              :key="tag"
              class="text-xs px-2 py-0.5 rounded-full bg-primary-50 text-primary-600"
            >{{ tag }}</span>
            <span
              v-if="problem.solved"
              class="text-xs px-2 py-0.5 rounded-full bg-success-100 text-success-700 inline-flex items-center gap-1"
            >
              <Icon name="check-circle" :size="11" /> 已通过
            </span>
          </div>

          <h2 class="text-lg font-semibold text-foreground">{{ problem.title }}</h2>
          <p class="text-sm text-foreground/80 leading-7 whitespace-pre-wrap">{{ problem.content }}</p>

          <!-- 函数签名 -->
          <div v-if="problem.functionSignature" class="rounded-xl border border-border bg-gray-50 p-4">
            <p class="text-xs text-muted-foreground mb-1.5">函数签名（固定 Harness，请勿修改方法名/参数）</p>
            <code class="text-[13px] font-mono text-foreground whitespace-pre-wrap">{{ problem.functionSignature }}</code>
          </div>

          <!-- 示例 -->
          <div v-if="parsedExamples.length" class="space-y-2">
            <h3 class="text-sm font-semibold text-foreground">示例</h3>
            <div
              v-for="(ex, i) in parsedExamples"
              :key="i"
              class="rounded-xl border border-border p-3 text-[13px] font-mono leading-6"
            >
              <p><span class="text-muted-foreground">输入：</span>{{ ex.input }}</p>
              <p><span class="text-muted-foreground">输出：</span>{{ ex.output }}</p>
              <p v-if="ex.explanation"><span class="text-muted-foreground">解释：</span>{{ ex.explanation }}</p>
            </div>
          </div>

          <!-- 约束 -->
          <div v-if="problem.constraints" class="rounded-xl border border-border p-4">
            <h3 class="text-sm font-semibold text-foreground mb-1.5">约束</h3>
            <p class="text-[13px] text-foreground/80 whitespace-pre-wrap leading-6">{{ problem.constraints }}</p>
            <p class="text-[11px] text-muted-foreground mt-2">
              时间限制 {{ problem.timeLimitMs ?? 1000 }}ms · 内存限制 {{ problem.memoryLimitMb ?? 256 }}MB
            </p>
          </div>

          <!-- 公开用例（隐藏用例不出现在此处） -->
          <div v-if="problem.publicCases.length" class="space-y-2">
            <h3 class="text-sm font-semibold text-foreground">公开测试用例</h3>
            <div
              v-for="c in problem.publicCases"
              :key="c.orderNo"
              class="rounded-xl border border-border p-3 text-[13px] font-mono leading-6"
            >
              <p><span class="text-muted-foreground">输入：</span>{{ c.input }}</p>
              <p><span class="text-muted-foreground">期望：</span>{{ c.expectedOutput }}</p>
            </div>
          </div>

          <!-- Phase 3H：AI 辅助（可选增强；不可用时回退判题事实 + 预置引导，绝不改变判题结论） -->
          <div class="rounded-xl border border-primary-200 bg-primary-50/40 p-4 space-y-3">
            <div class="flex items-center justify-between gap-2 flex-wrap">
              <h3 class="text-sm font-semibold text-foreground inline-flex items-center gap-1.5">
                <Icon name="sparkles" :size="15" class="text-primary-500" />
                AI 辅助
                <span
                  class="text-[11px] font-normal px-1.5 py-0.5 rounded-full"
                  :class="aiCapability.aiAvailable ? 'bg-success-100 text-success-700' : 'bg-gray-100 text-muted-foreground'"
                >{{ aiCapability.aiAvailable ? 'AI 可用' : aiStatusText }}</span>
              </h3>
              <label class="text-[11px] text-muted-foreground inline-flex items-center gap-1 cursor-pointer select-none kb-checkbox-row">
                <Checkbox as="span"

                  :checked="aiCapability.userEnabled"
                  @native-change="onToggleAi" />
                启用 AI 增强
              </label>
            </div>

            <div class="flex flex-wrap items-center gap-1.5">
              <span class="text-[11px] text-muted-foreground mr-0.5">分级提示</span>
              <button
                v-for="lv in 5"
                :key="lv"
                class="px-2 py-1 rounded-md text-[11px] border transition-colors"
                :class="aiActive === `hint-${lv}` ? 'border-primary-400 bg-primary-100 text-primary-700' : 'border-border bg-white text-muted-foreground hover:border-primary-300'"
                :disabled="aiBusy"
                :title="HINT_LEVEL_LABELS[lv - 1]"
                @click="requestAi('hint', lv)"
              >提示 {{ lv }}</button>
              <span class="w-px h-4 bg-border mx-1"></span>
              <button
                class="px-2.5 py-1 rounded-md text-[11px] border border-border bg-white text-muted-foreground hover:border-primary-300 disabled:opacity-50"
                :disabled="aiBusy"
                @click="requestAi('explain')"
              >解释我的代码</button>
              <button
                class="px-2.5 py-1 rounded-md text-[11px] border border-border bg-white text-muted-foreground hover:border-primary-300 disabled:opacity-50"
                :disabled="aiBusy"
                @click="requestAi('review')"
              >代码点评</button>
            </div>

            <div v-if="aiBusy" class="flex items-center gap-2 text-xs text-muted-foreground py-1">
              <Icon name="loader" :size="14" class="animate-spin text-primary-500" />
              AI 分析中（超时会自动回退基础信息，不影响判题）…
            </div>
            <div v-else-if="aiResult" class="space-y-2.5">
              <div class="flex items-center gap-2 flex-wrap">
                <span
                  class="text-[11px] px-1.5 py-0.5 rounded-full font-medium"
                  :class="aiResult.mode === 'AI' ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-muted-foreground'"
                >{{ aiResult.mode === 'AI' ? 'AI 建议（仅供参考，不改变判题结果）' : '基础模式' }}</span>
                <span v-if="aiResult.kind" class="text-[11px] text-muted-foreground">{{ aiKindLabel(aiResult) }}</span>
                <span v-if="aiResult.mode === 'BASIC' && aiResult.failureKind" class="text-[11px] text-warning-700">
                  AI 暂不可用：{{ aiFailureText(aiResult.failureKind) }}
                </span>
              </div>
              <p v-if="aiResult.content" class="text-[13px] text-foreground/90 whitespace-pre-wrap leading-7">{{ aiResult.content }}</p>
              <p v-else class="text-[13px] text-muted-foreground">暂无 AI 内容，也没有可展示的预置引导。</p>

              <!-- 基础模式事实数据：编译/测试结果原样回显，不做任何改写 -->
              <div v-if="aiResult.judgeStatus" class="rounded-lg border border-border bg-white p-3 space-y-1.5 text-xs">
                <p class="flex items-center gap-2">
                  <span class="text-muted-foreground">最近提交：</span>
                  <span :class="statusClass(aiResult.judgeStatus as JudgeStatusCode)" class="font-medium">
                    {{ statusLabel(aiResult.judgeStatus as JudgeStatusCode) }}
                  </span>
                  <span v-if="aiResult.score != null" class="text-muted-foreground tabular-nums">
                    {{ aiResult.score }} 分<template v-if="aiResult.totalCount">（{{ aiResult.passedCount }}/{{ aiResult.totalCount }}）</template>
                  </span>
                  <span v-if="aiResult.executionTimeMs != null" class="text-muted-foreground tabular-nums">{{ aiResult.executionTimeMs }}ms</span>
                </p>
                <pre v-if="aiResult.compileOutput" class="mt-1 max-h-40 overflow-auto whitespace-pre-wrap text-[11px] font-mono text-red-600 bg-red-50/60 rounded-md p-2">{{ aiResult.compileOutput }}</pre>
                <p v-if="!aiResult.judgeExecutionEnabled" class="text-[11px] text-warning-700">
                  安全判题服务未就绪：提交不计分、不记错；AI 不可用也不会触发任何不安全的代码执行。
                </p>
              </div>
            </div>
            <p v-else class="text-[11px] text-muted-foreground leading-relaxed">
              提示分 5 级（理解题意 → 完整题解），可逐阶解锁。AI 关闭或不可用时，这里只展示编译/测试事实与预置引导；
              运行、提交、历史记录等基础功能始终可用。
            </p>
          </div>

          <!-- 题解：仅本人通过后开放 -->
          <div v-if="problem.solved && (problem.solutionExplanation || problem.complexityAnalysis)" class="space-y-3">
            <h3 class="text-sm font-semibold text-foreground inline-flex items-center gap-1.5">
              <Icon name="book-open" :size="15" /> 题解（通过后解锁）
            </h3>
            <div v-if="problem.solutionExplanation" class="rounded-xl border border-success-200 bg-success-50/50 p-4">
              <p class="text-[13px] text-foreground/90 whitespace-pre-wrap leading-7">{{ problem.solutionExplanation }}</p>
            </div>
            <div v-if="problem.complexityAnalysis" class="rounded-xl border border-border p-4">
              <p class="text-xs text-muted-foreground mb-1">复杂度分析</p>
              <p class="text-[13px] text-foreground/80 whitespace-pre-wrap leading-6">{{ problem.complexityAnalysis }}</p>
            </div>
            <div v-if="problem.followUps" class="rounded-xl border border-border p-4">
              <p class="text-xs text-muted-foreground mb-1">面试追问</p>
              <p class="text-[13px] text-foreground/80 whitespace-pre-wrap leading-6">{{ problem.followUps }}</p>
            </div>
            <div v-if="problem.commonMistakes" class="rounded-xl border border-border p-4">
              <p class="text-xs text-muted-foreground mb-1">常见错误</p>
              <p class="text-[13px] text-foreground/80 whitespace-pre-wrap leading-6">{{ problem.commonMistakes }}</p>
            </div>
          </div>

          <!-- 历史提交 -->
          <div class="space-y-2 pb-6">
            <h3 class="text-sm font-semibold text-foreground inline-flex items-center gap-1.5">
              <Icon name="clock" :size="15" /> 我的提交
            </h3>
            <p v-if="!history.length" class="text-xs text-muted-foreground">暂无正式提交记录（「运行示例」不计入历史）。</p>
            <div
              v-for="s in history"
              :key="s.id"
              class="rounded-lg border border-border px-3 py-2 flex items-center gap-3 text-xs cursor-pointer hover:border-primary-300"
              @click="viewSubmission(s)"
            >
              <span class="font-medium w-20" :class="statusClass(s.judgeStatus)">{{ statusLabel(s.judgeStatus) }}</span>
              <span class="text-muted-foreground tabular-nums">
                {{ s.score == null ? '待判' : s.score + ' 分' }}
                <template v-if="s.totalCount">（{{ s.passedCount }}/{{ s.totalCount }}）</template>
              </span>
              <span class="ml-auto text-muted-foreground">{{ formatTime(s.createTime) }}</span>
            </div>

            <!-- Phase 3H：两次提交确定性代码对比（无 AI） -->
            <div v-if="history.length >= 2" class="rounded-lg border border-border px-3 py-2.5 space-y-2">
              <div class="flex items-center gap-1.5 text-xs font-medium text-foreground">
                <Icon name="git-branch" :size="14" class="text-primary" /> 代码对比
                <span class="text-muted-foreground font-normal">（服务端按提交时间自动确定旧 → 新）</span>
              </div>
              <div class="flex items-center gap-1.5 flex-wrap">
                <select
                  v-model.number="diffAId"
                  class="flex-1 min-w-0 px-2 py-1 text-[11px] outline-none kb-select kb-form-control"
                >
                  <option :value="null" disabled>选择提交 A</option>
                  <option v-for="s in history" :key="s.id" :value="s.id">{{ diffOptionLabel(s) }}</option>
                </select>
                <span class="text-muted-foreground text-xs">↔</span>
                <select
                  v-model.number="diffBId"
                  class="flex-1 min-w-0 px-2 py-1 text-[11px] outline-none kb-select kb-form-control"
                >
                  <option :value="null" disabled>选择提交 B</option>
                  <option v-for="s in history" :key="s.id" :value="s.id">{{ diffOptionLabel(s) }}</option>
                </select>
                <button
                  class="px-2.5 py-1 rounded-md text-xs border border-primary-300 text-primary hover:bg-primary-50 disabled:opacity-50 inline-flex items-center gap-1"
                  :disabled="diffLoading || diffAId == null || diffBId == null"
                  @click="loadDiff"
                >
                  <Icon v-if="diffLoading" name="loader" :size="12" class="animate-spin" />
                  对比
                </button>
              </div>

              <div v-if="diffData" class="rounded-md border border-border overflow-hidden">
                <div class="px-2.5 py-1.5 bg-muted/40 text-[11px] flex items-center gap-2 flex-wrap">
                  <span class="font-medium">#{{ diffData.from.id }}</span>
                  <span :class="statusClass(diffData.from.judgeStatus)">{{ diffRefMetrics(diffData.from) }}</span>
                  <span class="text-muted-foreground">→</span>
                  <span class="font-medium">#{{ diffData.to.id }}</span>
                  <span :class="statusClass(diffData.to.judgeStatus)">{{ diffRefMetrics(diffData.to) }}</span>
                  <span class="ml-auto text-muted-foreground">变化 {{ diffData.changedLines }} 行</span>
                </div>
                <p v-if="diffData.truncated" class="px-2.5 py-1 text-[11px] bg-amber-50 text-amber-700">
                  代码超过单次对比行数上限，结果已截断显示。
                </p>
                <pre class="max-h-72 overflow-auto text-[11px] leading-5 m-0"><code><div
                  v-for="(ln, i) in diffData.lines"
                  :key="i"
                  class="px-2.5 flex gap-2"
                  :class="{
                    'bg-red-50 text-red-700': ln.type === 'REMOVED',
                    'bg-emerald-50 text-emerald-700': ln.type === 'ADDED',
                  }"
                ><span class="select-none text-muted-foreground/70 w-8 text-right tabular-nums shrink-0">{{ ln.oldLine ?? '' }}</span
                  ><span class="select-none text-muted-foreground/70 w-8 text-right tabular-nums shrink-0">{{ ln.newLine ?? '' }}</span
                  ><span class="select-none w-3 shrink-0">{{ ln.type === 'REMOVED' ? '-' : ln.type === 'ADDED' ? '+' : '' }}</span
                  ><span class="whitespace-pre">{{ ln.text || ' ' }}</span></div></code></pre>
                <p class="px-2.5 py-1 text-[11px] text-muted-foreground">
                  行级差异为确定性算法生成；运行时间 / 内存 / 判题结论均取自提交记录，AI 不会改写这些指标。
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 拖拽分隔条（仅桌面） -->
      <div
        class="hidden lg:block w-1.5 cursor-col-resize bg-border hover:bg-primary-300 transition-colors shrink-0"
        @mousedown="startDrag"
      ></div>

      <!-- 右：代码 + 结果面板 -->
      <section
        class="editor-pane flex-1 min-w-0 flex flex-col overflow-hidden bg-white"
        :class="{ 'hidden lg:flex': mobileTab !== 'code' }"
        :style="rgPaneStyle"
      >
        <!-- 工具栏 -->
        <div class="shrink-0 border-b border-border px-4 py-2.5 flex items-center gap-2 flex-wrap">
          <select
            v-model="language"
            class="px-2.5 py-1.5 text-sm outline-none kb-select kb-form-control"
          >
            <option v-for="l in languages" :key="l" :value="l">Java 17</option>
          </select>
          <button
            class="px-2.5 py-1.5 rounded-lg text-xs border border-border text-muted-foreground hover:border-primary-300 inline-flex items-center gap-1"
            @click="resetCode"
          >
            <Icon name="refresh-cw" :size="13" /> 重置
          </button>
          <button
            class="px-2.5 py-1.5 rounded-lg text-xs border border-border text-foreground hover:border-primary-300 inline-flex items-center gap-1"
            :disabled="savingDraft"
            @click="manualSaveDraft"
          >
            <Icon name="check" :size="13" /> {{ savingDraft ? '保存中…' : '保存草稿' }}
          </button>
          <span class="text-[11px] text-muted-foreground ml-1">{{ draftStatus }}</span>

          <div class="ml-auto flex items-center gap-2">
            <button
              class="px-3 py-1.5 rounded-lg text-xs border border-border text-foreground hover:border-primary-300 inline-flex items-center gap-1 disabled:opacity-50"
              :disabled="busy || !problem.judgeExecutionEnabled"
              :title="problem.judgeExecutionEnabled ? '仅运行公开用例' : '判题服务未就绪'"
              @click="runCode"
            >
              <Icon name="play" :size="13" /> 运行示例
            </button>
            <button
              class="px-3 py-1.5 rounded-lg text-xs bg-primary-500 text-white hover:bg-primary-600 inline-flex items-center gap-1 disabled:opacity-50"
              :disabled="busy"
              @click="submitCode"
            >
              <Icon name="send" :size="13" /> {{ submitting ? '判题中…' : '提交判题' }}
            </button>
          </div>
        </div>

        <!-- 编辑器（无 Monaco/CodeMirror 依赖：等宽 textarea，满足语法场景的稳定降级） -->
        <div class="relative flex-1 min-h-[240px]">
          <textarea
            v-model="code"
            spellcheck="false"
            class="absolute inset-0 w-full h-full p-4 text-[13px] leading-6 font-mono bg-white text-foreground outline-none resize-none"
            placeholder="在此编写 Java 代码…"
          ></textarea>
        </div>

        <!-- 结果区 -->
        <div
          class="shrink-0 border-t border-border max-h-[45%] overflow-y-auto"
          :class="{ 'hidden lg:block': mobileTab !== 'result' }"
        >
          <div v-if="!result" class="p-6 text-center text-xs text-muted-foreground">
            <Icon name="play-circle" :size="22" class="mx-auto mb-2 opacity-60" />
            点击「运行示例」验证公开用例，或「提交判题」运行全部用例
          </div>
          <div v-else class="p-4 space-y-3">
            <div class="flex items-center gap-2.5 flex-wrap">
              <span class="text-sm font-semibold" :class="statusClass(result.judgeStatus)">
                {{ statusLabel(result.judgeStatus) }}
              </span>
              <span v-if="result.score != null" class="text-xs text-muted-foreground tabular-nums">
                得分 {{ result.score }} · {{ result.passedCount }}/{{ result.totalCount }} 用例通过
              </span>
              <span v-if="result.executionTimeMs" class="text-xs text-muted-foreground tabular-nums">
                {{ result.executionTimeMs }}ms
              </span>
            </div>

            <div
              v-if="result.message"
              class="rounded-lg px-3 py-2 text-xs"
              :class="isPlatformFailure(result.judgeStatus)
                ? 'bg-warning-50 text-warning-700 border border-warning-200'
                : 'bg-gray-50 text-muted-foreground border border-border'"
            >
              {{ result.message }}
            </div>

            <!-- 编译输出 -->
            <div v-if="result.compileOutput" class="rounded-lg border border-red-200 bg-red-50 p-3">
              <p class="text-xs font-semibold text-red-600 mb-1">编译输出</p>
              <pre class="text-[12px] text-red-700 whitespace-pre-wrap font-mono leading-5">{{ result.compileOutput }}</pre>
            </div>

            <!-- 用例结果：公开用例展示输入/期望/实际；隐藏用例仅汇总，不泄露内容 -->
            <div v-if="result.caseResults?.length" class="space-y-1.5">
              <div
                v-for="(c, i) in result.caseResults"
                :key="i"
                class="rounded-lg border px-3 py-2 text-xs"
                :class="c.passed ? 'border-success-200 bg-success-50/50' : 'border-red-200 bg-red-50/60'"
              >
                <div class="flex items-center gap-2">
                  <Icon :name="c.passed ? 'check-circle' : 'x'" :size="13" :class="c.passed ? 'text-success-600' : 'text-red-500'" />
                  <span class="font-medium">{{ c.hidden ? `隐藏用例 #${c.orderNo + 1}` : `用例 #${c.orderNo + 1}` }}</span>
                  <span class="text-muted-foreground">{{ c.status }}</span>
                  <span v-if="c.timeMs" class="ml-auto text-muted-foreground tabular-nums">{{ c.timeMs }}ms</span>
                </div>
                <div v-if="!c.hidden" class="mt-1 font-mono leading-5 text-muted-foreground">
                  <p>输入：{{ c.input }}</p>
                  <p>期望：{{ c.expected }}</p>
                  <p>实际：{{ c.actual }}</p>
                </div>
                <p v-else class="mt-1 text-muted-foreground">
                  隐藏用例详情不公开，仅展示通过情况。
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import {
  codeBankApi,
  DIFFICULTY_LABELS,
  JUDGE_STATUS_LABELS,
  type CodeAiAssistVO,
  type CodeProblemDetail,
  type CodeSubmitResult,
  type CodeSubmission,
  type CodeSubmissionDiff,
  type JudgeStatusCode,
  type PageData,
} from '@/api/codeBank'
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { useAiCapabilityStore } from '@/stores/aiCapability'
import { AI_STATUS_LABELS, failureKindLabel } from '@/api/aiCapability'

const route = useRoute()
const router = useRouter()
const problemId = Number(route.params.id)
const aiCapability = useAiCapabilityStore()

/** Phase 3H：AI 辅助面板状态（与判题状态完全独立：AI 区失败不影响运行/提交/历史）。 */
const HINT_LEVEL_LABELS = ['理解题意', '算法思路', '关键数据结构', '伪代码', '完整题解']
const AI_KIND_LABELS: Record<string, string> = {
  hint: '分级提示',
  explain: '代码解释',
  review: '代码点评',
}
const aiBusy = ref(false)
const aiResult = ref<CodeAiAssistVO | null>(null)
const aiActive = ref('')
const aiStatusText = computed(() => AI_STATUS_LABELS[aiCapability.effectiveStatus])

function aiKindLabel(r: CodeAiAssistVO): string {
  const base = AI_KIND_LABELS[r.kind || ''] || r.kind || ''
  return r.kind === 'hint' && r.hintLevel ? `${base} Lv${r.hintLevel}（${HINT_LEVEL_LABELS[r.hintLevel - 1] || ''}）` : base
}
function aiFailureText(kind?: string | null): string {
  // 后端 failureKind 形如 AI_TIMEOUT / AI_RATE_LIMITED
  return failureKindLabel((kind || '').replace(/^AI_/, '')) || kind || 'AI 暂不可用'
}
function onToggleAi(e: Event): void {
  const enabled = (e.target as HTMLInputElement).checked
  aiCapability.setUserEnabled(enabled)
  notify(enabled ? '已开启 AI 增强' : '已关闭 AI 增强，基础学习功能不受影响', 'info', 2500)
}

async function requestAi(kind: 'hint' | 'explain' | 'review', hintLevel?: number): Promise<void> {
  if (!problem.value) return
  aiBusy.value = true
  aiActive.value = hintLevel ? `hint-${hintLevel}` : kind
  try {
    // 先静默保存草稿，保证 AI 输入是用户当前代码（后端只作输入，不触发判题）
    if (dirty.value) await persistDraft(true)
    aiResult.value = await codeBankApi.aiAssist(problem.value.id, { kind, hintLevel })
  } catch (e) {
    notify(getApiError(e, 'AI 辅助请求失败，请稍后重试'), 'error')
    aiResult.value = null
  } finally {
    aiBusy.value = false
  }
}

const loading = ref(true)
const loadError = ref('')
const problem = ref<CodeProblemDetail | null>(null)

const language = ref('java')
const code = ref('')
const initialCode = ref('')
const result = ref<CodeSubmitResult | null>(null)
const history = ref<CodeSubmission[]>([])
const historyPage = ref<PageData<CodeSubmission> | null>(null)

// Phase 3H：两次提交确定性代码对比（无 AI）
const diffAId = ref<number | null>(null)
const diffBId = ref<number | null>(null)
const diffLoading = ref(false)
const diffData = ref<CodeSubmissionDiff | null>(null)

const busy = ref(false)
const running = ref(false)
const submitting = ref(false)
const savingDraft = ref(false)
const dirty = ref(false)
const draftStatus = ref('')
let draftTimer: ReturnType<typeof setTimeout> | undefined

const mobileTab = ref<'desc' | 'code' | 'result'>('desc')
const mobileTabs = [
  { key: 'desc' as const, label: '题目' },
  { key: 'code' as const, label: '代码' },
  { key: 'result' as const, label: '结果' },
]

const languages = computed(() =>
  (problem.value?.supportedLanguages || 'java').split(',').map((s) => s.trim()).filter(Boolean),
)
const tagList = computed(() =>
  (problem.value?.tags || '').split(',').map((s) => s.trim()).filter(Boolean),
)
const parsedExamples = computed(() => {
  if (!problem.value?.examples) return []
  try {
    const arr = JSON.parse(problem.value.examples)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})

// ---- 面板拖拽（仅桌面） ----
const splitPercent = ref(50)
const lgPaneStyle = computed(() => ({ flex: `0 0 ${splitPercent.value}%` }))
const rgPaneStyle = computed(() => ({ flex: `0 0 ${100 - splitPercent.value}%` }))
let dragging = false
function startDrag(e: MouseEvent) {
  dragging = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}
function onDragMove(e: MouseEvent) {
  if (!dragging) return
  const main = document.querySelector('.code-solve main')
  if (!main) return
  const rect = main.getBoundingClientRect()
  const pct = ((e.clientX - rect.left) / rect.width) * 100
  splitPercent.value = Math.min(70, Math.max(30, Math.round(pct)))
}
function endDrag() {
  if (!dragging) return
  dragging = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function diffLabel(d: number): string {
  return DIFFICULTY_LABELS[d - 1] || '未知'
}
function diffClass(d: number): string {
  if (d === 1) return 'bg-success-100 text-success-700'
  if (d === 2) return 'bg-warning-100 text-warning-700'
  if (d === 3) return 'bg-red-100 text-red-600'
  return 'bg-gray-100 text-muted-foreground'
}
function statusLabel(s: JudgeStatusCode): string {
  return JUDGE_STATUS_LABELS[s] || s
}
function isPlatformFailure(s: JudgeStatusCode): boolean {
  return s === 'SYSTEM_ERROR' || s === 'JUDGE_UNAVAILABLE'
}
function statusClass(s: JudgeStatusCode): string {
  if (s === 'ACCEPTED') return 'text-success-600'
  if (isPlatformFailure(s)) return 'text-warning-600'
  if (s === 'QUEUED' || s === 'COMPILING' || s === 'RUNNING') return 'text-primary-600'
  return 'text-red-500'
}
function formatTime(t?: string): string {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [detail, hist] = await Promise.all([
      codeBankApi.detail(problemId),
      codeBankApi.mySubmissions({ questionId: problemId, page: 1, pageSize: 20 }),
    ])
    problem.value = detail
    historyPage.value = hist
    history.value = hist.records
    language.value = (detail.supportedLanguages || 'java').split(',')[0].trim() || 'java'
    initialCode.value = detail.draftCode || detail.starterCode || ''
    code.value = initialCode.value
    dirty.value = false
    draftStatus.value = detail.draftCode ? '已恢复上次草稿' : ''
  } catch (e) {
    loadError.value = getApiError(e, '题目加载失败')
  } finally {
    loading.value = false
  }
}

async function persistDraft(silent: boolean) {
  if (!problem.value || !code.value) return
  savingDraft.value = true
  try {
    await codeBankApi.saveDraft(problem.value.id, { language: language.value, sourceCode: code.value })
    dirty.value = false
    draftStatus.value = `草稿已保存 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`
  } catch (e) {
    if (!silent) notify(getApiError(e, '草稿保存失败'), 'error')
  } finally {
    savingDraft.value = false
  }
}

function manualSaveDraft() {
  persistDraft(false)
}

async function resetCode() {
  const ok = await confirmDialog('确定重置为初始代码吗？未保存的修改将丢失（草稿不会被自动覆盖，需重新保存）。', {"title":"重置为初始内容？","confirmText":"确认重置","cancelText":"返回编辑","icon":"refresh-cw","variant":"danger"})
  if (!ok) return
  code.value = problem.value?.starterCode || ''
  dirty.value = true
  scheduleDraft()
}

function scheduleDraft() {
  dirty.value = true
  draftStatus.value = '有未保存修改…'
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => persistDraft(true), 2000)
}

async function judge(type: 'RUN' | 'SUBMIT') {
  if (!problem.value || busy.value) return
  if (!code.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  busy.value = true
  running.value = type === 'RUN'
  submitting.value = type === 'SUBMIT'
  result.value = null
  if (type === 'SUBMIT') mobileTab.value = 'result'
  try {
    const r = await codeBankApi.submit(problem.value.id, {
      language: language.value,
      sourceCode: code.value,
      type,
    })
    result.value = r
    if (type === 'SUBMIT') {
      if (r.judgeStatus === 'ACCEPTED') {
        notify('恭喜，全部用例通过！', 'success')
        problem.value!.solved = true
      } else if (isPlatformFailure(r.judgeStatus)) {
        notify(statusLabel(r.judgeStatus) + '：本次不计分、不记错，可稍后重试', 'warning', 5000)
      }
      const hist = await codeBankApi.mySubmissions({ questionId: problem.value.id, page: 1, pageSize: 20 })
      historyPage.value = hist
      history.value = hist.records
      // 通过后刷新详情以解锁题解（保留当前编辑器内容）
      if (r.judgeStatus === 'ACCEPTED') {
        const refreshed = await codeBankApi.detail(problem.value.id)
        const currentCode = code.value
        problem.value = refreshed
        code.value = currentCode
      }
    }
  } catch (e) {
    notify(getApiError(e, '判题请求失败'), 'error')
  } finally {
    busy.value = false
    running.value = false
    submitting.value = false
  }
}

function runCode() {
  judge('RUN')
}
function submitCode() {
  judge('SUBMIT')
}

async function viewSubmission(s: CodeSubmission) {
  try {
    const detail = await codeBankApi.submission(s.id)
    if (detail.sourceCode) code.value = detail.sourceCode
    result.value = {
      submissionId: detail.id,
      judgeStatus: detail.judgeStatus,
      score: detail.score,
      passedCount: detail.passedCount,
      totalCount: detail.totalCount,
      compileOutput: detail.compileOutput,
      caseResults: detail.caseResults,
      executionEnabled: true,
    }
    mobileTab.value = 'result'
    notify('已载入该次提交的代码与结果（编辑后会产生新版本）', 'info', 3000)
  } catch (e) {
    notify(getApiError(e, '提交详情加载失败'), 'error')
  }
}

function goBack() {
  router.push('/coding/bank')
}

// Phase 3H：对比两条正式提交（确定性 diff；判题指标只展示提交记录里的真实值）
async function loadDiff() {
  if (diffAId.value == null || diffBId.value == null) {
    notify('请选择两条要对比的提交', 'warning')
    return
  }
  if (diffAId.value === diffBId.value) {
    notify('请选择两条不同的提交', 'warning')
    return
  }
  diffLoading.value = true
  try {
    diffData.value = await codeBankApi.submissionDiff(problemId, diffAId.value, diffBId.value)
  } catch (e) {
    diffData.value = null
    notify(getApiError(e, '代码对比失败'), 'error')
  } finally {
    diffLoading.value = false
  }
}

function diffOptionLabel(s: CodeSubmission): string {
  const time = s.createTime ? formatTime(s.createTime) : ''
  const score = s.score == null ? '待判' : `${s.score}分`
  return `#${s.id} ${statusLabel(s.judgeStatus)} ${score} ${time}`
}

function diffRefMetrics(ref: CodeSubmissionDiff['from']): string {
  const parts = [statusLabel(ref.judgeStatus), ref.score == null ? '待判' : `${ref.score}分`]
  if (typeof ref.executionTimeMs === 'number') parts.push(`${ref.executionTimeMs}ms`)
  if (typeof ref.memoryUsedKb === 'number') parts.push(`${ref.memoryUsedKb}KB`)
  return parts.join(' · ')
}

// 编辑监听（自动草稿）
watch(code, () => {
  if (!loading.value) scheduleDraft()
})

onBeforeRouteLeave(async () => {
  if (dirty.value) {
    await persistDraft(true).catch(() => undefined)
  }
  return true
})

function beforeUnload() {
  if (dirty.value) persistDraft(true)
}

onMounted(() => {
  load()
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', endDrag)
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', endDrag)
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.code-solve textarea {
  tab-size: 2;
}
</style>
