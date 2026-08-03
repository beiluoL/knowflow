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
          <option value="cpp">C++</option>
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
        <button
          type="button"
          class="header-btn ws-toggle-btn"
          :class="{ active: workspaceMode }"
          @click="toggleWorkspace"
        >
          <Icon name="folder" :size="14" />
          <span>沙箱模式</span>
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

          <!-- 工作区面板（SC1-IDE-02 可重置实验沙箱，仅沙箱模式显示） -->
          <div v-if="workspaceMode" class="panel-section ws-panel">
            <div class="ws-header">
              <h3 class="subsection-heading">
                <Icon name="folder" :size="14" style="display: inline; margin-right: 4px; color: var(--kb-primary);" />
                我的工作区
              </h3>
              <div class="ws-actions">
                <button class="ws-mini-btn" title="新建文件" @click="createWsFilePrompt">
                  <Icon name="plus" :size="13" />
                </button>
                <button class="ws-mini-btn" title="重置沙箱" @click="resetWorkspace">
                  <Icon name="rotate-ccw" :size="13" />
                </button>
              </div>
            </div>
            <div v-if="wsLoading" class="ws-loading">加载中…</div>
            <ul v-else class="ws-file-list">
              <li
                v-for="f in workspaceFiles"
                :key="f.path"
                class="ws-file-item"
                :class="{ active: f.path === activeWsFile }"
                @click="openWsFile(f.path)"
              >
                <Icon :name="wsIcon(f.language)" :size="13" />
                <span class="ws-file-name">{{ f.name }}</span>
                <button class="ws-del" title="删除" @click.stop="deleteWsFile(f.path)">
                  <Icon name="trash" :size="12" />
                </button>
              </li>
              <li v-if="!wsLoading && workspaceFiles.length === 0" class="ws-empty">
                暂无文件，点击右上角 + 新建
              </li>
            </ul>
          </div>

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
              <button
                type="button"
                class="console-tab"
                :class="{ active: activeTab === 'debug' }"
                @click="activeTab = 'debug'"
              >
                <Icon name="target" :size="12" />
                调试
              </button>
              <button
                type="button"
                class="console-tab"
                :class="{ active: activeTab === 'assess' }"
                @click="activeTab = 'assess'"
              >
                <Icon name="list-checks" :size="12" />
                能力评估
              </button>
              <button
                type="button"
                class="console-tab"
                :class="{ active: activeTab === 'ai' }"
                @click="activeTab = 'ai'"
              >
                <Icon name="sparkles" :size="12" />
                AI 助手
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
                <div class="output-status" :class="runStatusClass">
                  <Icon :name="runStatusIcon" :size="14" />
                  <span>{{ statusLabel(runResult?.status) }}</span>
                  <span class="output-time">{{ runResult?.time }}ms</span>
                  <button v-if="runResult?.error" class="output-ai-btn" title="用 AI 解释这个错误" @click="explainError">
                    <Icon name="sparkles" :size="12" /> AI 解释
                  </button>
                  <button v-if="runResult?.error" class="output-ai-btn mistake" title="归集到错题本" :disabled="collectingMistake" @click="collectMistake">
                    <Icon name="bookmark" :size="12" /> {{ collectingMistake ? '归集...' : '归集错题' }}
                  </button>
                </div>
                <pre v-if="runResult.output" class="output-content">{{ runResult.output }}</pre>
                <pre v-if="runResult.error" class="output-error">{{ runResult.error }}</pre>

                <!-- SC1-AI-03 错题归集结果 -->
                <div v-if="mistakeResult" class="mistake-block">
                  <div class="mistake-head">
                    <Icon name="bookmark" :size="13" />
                    <span>已归集到错题本</span>
                    <span class="mistake-type">{{ mistakeResult.errorType }}</span>
                  </div>
                  <p class="mistake-summary">{{ mistakeResult.errorSummary }}</p>
                  <div v-if="mistakeResult.relatedDocs.length" class="mistake-docs">
                    <div class="mistake-docs-title">关联知识库文档</div>
                    <ul class="mistake-doc-list">
                      <li v-for="doc in mistakeResult.relatedDocs" :key="doc.id" class="mistake-doc-item">
                        <Icon name="file-text" :size="12" />
                        <div class="mistake-doc-info">
                          <span class="mistake-doc-name">{{ doc.title }}</span>
                          <span class="mistake-doc-snippet">{{ doc.snippet }}</span>
                        </div>
                      </li>
                    </ul>
                  </div>
                  <div v-else class="mistake-nodocs">暂未匹配到相关知识库文档</div>
                </div>
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

            <!-- AI 助手 Tab（SC1-AI-01 内联联动） -->
            <div v-show="activeTab === 'ai'" class="ai-tab">
              <div v-if="!aiAnswer && !aiAssisting" class="ai-empty">
                <Icon name="sparkles" :size="22" style="opacity: 0.4" />
                <p>AI 助手可解释运行错误、回答编程问题。</p>
                <button v-if="runResult && runResult.error" class="ai-explain-btn" @click="explainError">
                  <Icon name="sparkles" :size="14" /> 用 AI 解释这个错误
                </button>
              </div>
              <div v-else class="ai-output">
                <div v-if="aiAssisting" class="ai-thinking">
                  <span class="spinner" style="width: 16px; height: 16px"></span> AI 思考中…
                </div>
                <pre v-if="aiAnswer" class="ai-answer">{{ aiAnswer }}</pre>
                <div v-if="!aiConfigured" class="ai-unconfigured">
                  ⚠️ AI 服务未配置：请在服务端 <code>application.yml</code> 设置有效的
                  <code>ai.api-key</code>（或用户在个人设置中填写 API Key）后即可启用。
                </div>
              </div>
              <div class="ai-input-row">
                <textarea
                  v-model="aiQuestion"
                  class="ai-input"
                  placeholder="问 AI 任何编程问题，例如：如何优化这段代码？"
                  @keydown.ctrl.enter="askAi"
                ></textarea>
                <button class="ai-ask-btn" :disabled="aiAssisting" @click="askAi">
                  <Icon name="send" :size="12" /> 提问
                </button>
              </div>
            </div>

            <!-- 调试 Tab（推进 2.1 在线调试器） -->
            <div v-show="activeTab === 'debug'" class="debug-tab">
              <div v-if="!debugResult" class="console-empty">
                <Icon name="target" :size="32" style="opacity: 0.3;" />
                <p>点击「调试运行」逐行追踪代码执行</p>
              </div>
              <div v-else class="console-output">
                <div class="output-status" :class="debugResult.status === 'SUCCESS' ? 'success' : 'error'">
                  <Icon :name="debugResult.status === 'SUCCESS' ? 'check-circle' : 'x-circle'" :size="14" />
                  <span>{{ statusLabel(debugResult.status) }}</span>
                  <span class="output-time">{{ debugResult.timeUsedMs }}ms</span>
                </div>
                <div v-if="debugResult.errorLine" class="debug-errline">⛔ 错误定位：第 {{ debugResult.errorLine }} 行</div>
                <pre v-if="debugResult.output" class="output-content">{{ debugResult.output }}</pre>
                <pre v-if="debugResult.error" class="output-error">{{ debugResult.error }}</pre>
                <div v-if="debugResult.trace && debugResult.trace.length" class="debug-trace">
                  <div class="debug-trace-title">逐行执行追踪（Python）</div>
                  <div
                    v-for="(step, i) in debugResult.trace"
                    :key="i"
                    class="debug-step"
                    :class="{ 'debug-error-line': debugResult.errorLine && step.line === debugResult.errorLine }"
                  >
                    <span class="debug-line-no">L{{ step.line }}</span>
                    <span class="debug-event">{{ step.event }}</span>
                    <pre class="debug-vars">{{ step.vars }}</pre>
                  </div>
                </div>
              </div>
            </div>

            <!-- 能力评估 Tab（SC1-AI-02 自动化代码评估） -->
            <div v-show="activeTab === 'assess'" class="assess-tab">
              <div v-if="!assessResult" class="console-empty">
                <Icon name="list-checks" :size="32" style="opacity: 0.3;" />
                <p>点击「提交评估」对代码进行动态评测 + 静态检查 + AI 报告</p>
              </div>
              <div v-else class="console-output">
                <div class="assess-score" :class="assessResult.passed ? 'pass' : 'fail'">
                  <div class="assess-score-num">{{ assessResult.score }}</div>
                  <div class="assess-score-meta">
                    <span class="assess-level">{{ assessResult.level }}</span>
                    <span class="assess-tests">通过 {{ assessResult.passedTests }}/{{ assessResult.totalTests }} 个用例</span>
                  </div>
                </div>
                <p class="assess-summary">{{ assessResult.summary }}</p>
                <div v-if="assessResult.staticIssues.length" class="assess-issues">
                  <div class="assess-issues-title">静态检查建议</div>
                  <div v-for="(iss, i) in assessResult.staticIssues" :key="i" class="assess-issue-item">
                    <span class="assess-issue-rule">{{ iss.rule }}</span>
                    <span v-if="iss.line" class="assess-issue-line">L{{ iss.line }}</span>
                    <span class="assess-issue-msg">{{ iss.message }}</span>
                  </div>
                </div>
                <div v-if="assessResult.aiReport" class="assess-ai">
                  <div class="assess-ai-title">
                    <Icon name="sparkles" :size="12" /> AI 评估报告
                  </div>
                  <pre class="assess-ai-body">{{ assessResult.aiReport }}</pre>
                </div>
                <div v-else-if="!assessResult.aiConfigured" class="assess-ai-unconf">
                  ⚠️ AI 报告未生成：服务端未配置有效的 AI Key（动态评测与静态检查仍可用）。
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
            <button type="button" class="footer-btn debug-foot-btn" :disabled="debugging" @click="debugCode">
              <Icon name="target" :size="14" />
              <span>{{ debugging ? '调试中...' : '调试运行' }}</span>
            </button>
            <button type="button" class="footer-btn assess-foot-btn" :disabled="assessing" @click="assessCode">
              <Icon name="list-checks" :size="14" />
              <span>{{ assessing ? '评估中...' : '提交评估' }}</span>
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
import { notify, getApiError, confirmDialog, promptDialog } from '@/utils/toast'
import { normalizeNewlines } from '@/utils/string'
import { chatApi } from '@/api/chat'
import { codeQuestionApi } from '@/api/codeQuestion'
import { codeRunApi } from '@/api/codeRun'
import type { CodeAssessResultDTO, CodeDebugResultDTO, CodeMistakeCollectResultDTO } from '@/api/codeRun'
import { codeWorkspaceApi } from '@/api/codeWorkspace'
import type { CodeWorkspaceFileDTO } from '@/api/codeWorkspace'
import { runSql } from '@/utils/sqlSimulator'
import type { CodeQuestionVO, CodeTestCase } from '@/api/types'

const route = useRoute()
const router = useRouter()

const question = ref<CodeQuestionVO>({} as CodeQuestionVO)
const selectedLanguage = ref('javascript')
const userCode = ref('')
const showHint = ref(false)
const running = ref(false)
const submitting = ref(false)
const aiAnswering = ref(false)
const loading = ref(true)
const loadError = ref('')
const activeTab = ref<'output' | 'testcase' | 'debug' | 'assess' | 'ai'>('output')
const runResult = ref<{ output: string; error: string | null; time: number; status?: string } | null>(null)
const codeAreaRef = ref<HTMLTextAreaElement | null>(null)

// ===== 工作区模式（SC1-IDE-02 可重置实验沙箱） =====
const workspaceMode = ref(false)
const workspaceFiles = ref<CodeWorkspaceFileDTO[]>([])
const activeWsFile = ref('')
const wsLoading = ref(false)

// ===== AI 编程助手内联（SC1-AI-01） =====
const aiQuestion = ref('')
const aiAnswer = ref('')
const aiConfigured = ref(true)
const aiAssisting = ref(false)

// ===== 轻量在线调试（推进 2.1 在线调试器） =====
const debugging = ref(false)
const debugResult = ref<CodeDebugResultDTO | null>(null)

// ===== SC1-AI-02 自动化代码评估 =====
const assessing = ref(false)
const assessResult = ref<CodeAssessResultDTO | null>(null)

// ===== SC1-AI-03 代码错题归集 =====
const collectingMistake = ref(false)
const mistakeResult = ref<CodeMistakeCollectResultDTO | null>(null)

// 测试用例
const testCases = ref<CodeTestCase[]>([])
const tcResults = ref<{ passed: boolean; actual: string }[]>([])

// 控制台高度
const consoleHeight = ref(260)

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
  cpp: `// 在此编写你的 C++ 代码
#include <iostream>
using namespace std;

int main() {
    cout << "Hello, KnowFlow!" << endl;
    return 0;
}`,
  sql: `-- 在此编写你的 SQL 代码
-- 内置示例数据库：users、orders、products
-- 可执行 SELECT / SHOW TABLES / DESCRIBE

SELECT * FROM users LIMIT 5;`,
}

const fileExt = computed(() => {
  const map: Record<string, string> = { javascript: 'js', typescript: 'ts', python: 'py', java: 'java', cpp: 'cpp', sql: 'sql' }
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

/** 运行结果状态样式：出错或非 SUCCESS 显示红色 */
const runStatusClass = computed(() => {
  const r = runResult.value
  if (!r) return 'success'
  if (r.error || (r.status && r.status !== 'SUCCESS')) return 'error'
  return 'success'
})
const runStatusIcon = computed(() => (runStatusClass.value === 'error' ? 'x-circle' : 'check-circle'))

/** 运行结果状态中文标签 */
const statusLabel = (s?: string) => {
  const map: Record<string, string> = {
    SUCCESS: '运行成功',
    COMPILE_ERROR: '编译错误',
    RUNTIME_ERROR: '运行错误',
    TIMEOUT: '执行超时',
    INTERNAL_ERROR: '无法运行',
  }
  return (s && map[s]) || '运行完成'
}

const difficultyLabel = (d?: number) => (d === 0 ? '简单' : d === 1 ? '中等' : '困难')
const langLabel = (lang?: string) => {
  const map: Record<string, string> = {
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  python: 'Python',
  java: 'Java',
  cpp: 'C++',
  sql: 'SQL',
  }
  return map[lang || ''] || lang || '-'
}

const LANG_COLORS: Record<string, string> = {
  javascript: '#3B6FE0',
  typescript: '#3B6FE0',
  python: '#10B981',
  java: '#F59E0B',
  cpp: '#06B6D4',
  sql: '#EF4444',
}
const langColor = (lang?: string) => LANG_COLORS[lang || ''] || 'var(--kb-muted-foreground)'
const langColorBg = (lang?: string) => {
  const hex = LANG_COLORS[lang || '']
  return hex ? `${hex}14` : 'var(--kb-muted)'
}

const onLanguageChange = () => {
  if (workspaceMode.value) {
    // 沙箱模式下语言由当前文件扩展名决定，切换下拉不改变文件内容
    return
  }
  userCode.value = DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
  runResult.value = null
  tcResults.value = []
}

const resetCode = () => {
  if (workspaceMode.value) {
    notify('沙箱模式下请使用工作区的「重置沙箱」按钮', 'info')
    return
  }
  // 优先使用题目自带的代码模板
  if (question.value.codeTemplate) {
    userCode.value = normalizeNewlines(question.value.codeTemplate)
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
  debugResult.value = null
  assessResult.value = null
  mistakeResult.value = null
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

/**
 * 运行代码：
 * - Python / Java / JavaScript / C++ → 调后端真实沙箱 /api/code/run
 * - SQL → 前端模拟执行器（sqlSimulator）
 * - TypeScript 等 → 浏览器端 JS 执行（TS 类型语法可能报错，属预期）
 */
const runCode = async () => {
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  running.value = true
  activeTab.value = 'output'
  const startTime = performance.now()
  const lang = selectedLanguage.value

  try {
    let output = ''
    let error: string | null = null
    let status: string | undefined

    if (workspaceMode.value) {
      // ===== 工作区模式（SC1-IDE-02）：先保存当前文件，再运行持久目录中的入口文件 =====
      if (!activeWsFile.value) {
        notify('请先在工作区创建或选择文件', 'warning')
        running.value = false
        return
      }
      await codeWorkspaceApi.save({ path: activeWsFile.value, content: userCode.value })
      const ext = (activeWsFile.value.split('.').pop() || '').toLowerCase()
      const langByExt: Record<string, string> = {
        py: 'python', js: 'javascript', ts: 'typescript', java: 'java',
        cpp: 'cpp', cc: 'cpp', cxx: 'cpp', c: 'cpp', go: 'go', rs: 'rust', sql: 'sql',
      }
      const wsLang = langByExt[ext] || 'python'
      const res = await codeRunApi.run({
        language: wsLang,
        code: userCode.value,
        workspace: true,
        entryFile: activeWsFile.value,
      })
      output = res.output || ''
      error = res.error || null
      status = res.status
    } else if (['python', 'java', 'javascript', 'cpp'].includes(lang)) {
      // 真实沙箱：后端基于系统运行时执行（支持 stdin / 超时 / 编译错误）
      const res = await codeRunApi.run({ language: lang, code: userCode.value })
      output = res.output || ''
      error = res.error || null
      status = res.status
    } else if (lang === 'sql') {
      const result = runSql(userCode.value)
      output = result.output
      error = result.error
    } else {
      // TypeScript 等：浏览器端 JS 直执行
      const result = runJavaScript(userCode.value)
      output = result.output
      error = result.error
    }

    const elapsed = Math.round(performance.now() - startTime)
    runResult.value = { output, error, time: elapsed, status }
    if (error && status !== 'SUCCESS') {
      notify('代码运行出错，可点击「AI 解释」获取帮助', 'error')
    }
  } catch (e: unknown) {
    runResult.value = {
      output: '',
      error: getApiError(e),
      time: Math.round(performance.now() - startTime),
      status: 'INTERNAL_ERROR',
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
      const passed = !result.error && (actual === expected || Boolean(expected && actual.includes(expected)))
      results.push({ passed, actual: result.error ? result.error : actual })
    } else if (selectedLanguage.value === 'sql') {
      // SQL 题目：用例的 input 即为要执行的 SQL；执行后比较 output
      const result = runSql(tc.input || userCode.value)
      const actual = result.output.trim()
      const expected = (tc.expected || '').trim()
      const passed = !result.error && (actual === expected || Boolean(expected && actual.includes(expected)))
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

// ================= 工作区模式（SC1-IDE-02 可重置实验沙箱） =================

/** 切换沙箱/练习模式 */
const toggleWorkspace = async () => {
  workspaceMode.value = !workspaceMode.value
  if (workspaceMode.value) {
    await loadWorkspace()
    notify('已进入沙箱模式：文件持久保存在你的工作区，可随时「重置沙箱」', 'info')
  }
}

/** 加载工作区文件列表，并自动选中首个文件 / 建立起步文件 */
const loadWorkspace = async () => {
  wsLoading.value = true
  try {
    let files = await codeWorkspaceApi.list()
    if (files.length === 0) {
      await codeWorkspaceApi.save({ path: 'main.py', content: 'print("Hello from workspace!")\n' })
      files = await codeWorkspaceApi.list()
    }
    workspaceFiles.value = files
    const target = files.find((f) => f.path === activeWsFile.value) || files[0]
    if (target) await openWsFile(target.path)
  } catch (e: unknown) {
    notify('加载工作区失败：' + getApiError(e), 'error')
  } finally {
    wsLoading.value = false
  }
}

/** 打开（切换）工作区文件，将内容载入编辑器 */
const openWsFile = async (path: string) => {
  const f = workspaceFiles.value.find((x) => x.path === path)
  if (!f) return
  activeWsFile.value = path
  userCode.value = normalizeNewlines(f.content || '')
  runResult.value = null
  tcResults.value = []
  const ext = (path.split('.').pop() || '').toLowerCase()
  const map: Record<string, string> = { py: 'python', java: 'java', js: 'javascript', cpp: 'cpp' }
  if (map[ext]) selectedLanguage.value = map[ext]
}

/** 新建工作区文件（弹窗输入文件名，按扩展名给起步模板） */
const createWsFilePrompt = async () => {
  const name = await promptDialog('请输入文件名（如 main.py / Main.java / app.js / main.cpp）：', {
    placeholder: 'main.py',
  })
  if (!name) return
  const ext = (name.split('.').pop() || '').toLowerCase()
  const tmpl: Record<string, string> = {
    py: 'print("Hello from workspace!")\n',
    java: 'public class Main {\n    public static void main(String[] a) {\n        System.out.println("Hello from workspace!");\n    }\n}\n',
    js: 'console.log("Hello from workspace!");\n',
    cpp: '#include <iostream>\nusing namespace std;\nint main() {\n    cout << "Hello from workspace!" << endl;\n    return 0;\n}\n',
  }
  wsLoading.value = true
  try {
    await codeWorkspaceApi.save({ path: name, content: tmpl[ext] || '' })
    await loadWorkspace()
    await openWsFile(name)
    notify('已新建文件 ' + name, 'success')
  } catch (e: unknown) {
    notify('新建失败：' + getApiError(e), 'error')
  } finally {
    wsLoading.value = false
  }
}

/** 删除工作区文件 */
const deleteWsFile = async (path: string) => {
  if (!(await confirmDialog('确定删除文件 ' + path + ' ？'))) return
  try {
    await codeWorkspaceApi.remove(path)
    if (activeWsFile.value === path) activeWsFile.value = ''
    await loadWorkspace()
    if (workspaceFiles.value.length) await openWsFile(workspaceFiles.value[0].path)
    notify('已删除 ' + path, 'success')
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

/** 重置工作区：清空全部文件 */
const resetWorkspace = async () => {
  if (!(await confirmDialog('确定重置工作区？将清空你保存的全部文件（不可恢复）。'))) return
  try {
    await codeWorkspaceApi.reset()
    workspaceFiles.value = []
    activeWsFile.value = ''
    userCode.value = ''
    runResult.value = null
    notify('工作区已重置', 'success')
  } catch (e: unknown) {
    notify('重置失败：' + getApiError(e), 'error')
  }
}

/** 工作区文件图标 */
const wsIcon = (lang?: string) => {
  const m: Record<string, string> = {
    python: 'file-code', java: 'file-code', javascript: 'file-code',
    cpp: 'file-code', text: 'file', sql: 'database',
  }
  return m[lang || ''] || 'file'
}

// ================= AI 编程助手内联（SC1-AI-01） =================

/** 一键解释当前运行错误 */
const explainError = async () => {
  if (!runResult.value || !runResult.value.error) {
    notify('当前没有运行错误需要解释', 'info')
    return
  }
  await doAiAssist(runResult.value.error, runResult.value.output)
}

/** 自由提问 */
const askAi = async () => {
  if (!aiQuestion.value.trim()) {
    notify('请输入你的问题', 'warning')
    return
  }
  await doAiAssist(null, null, aiQuestion.value.trim())
}

/** 统一调用后端 AI 助手 */
const doAiAssist = async (error: string | null, output: string | null, question?: string) => {
  aiAssisting.value = true
  activeTab.value = 'ai'
  try {
    const res = await codeRunApi.aiAssist({
      language: selectedLanguage.value,
      code: userCode.value,
      error: error ?? null,
      output: output ?? null,
      question: question ?? undefined,
    })
    aiConfigured.value = res.configured
    aiAnswer.value = res.answer
  } catch (e: unknown) {
    notify('AI 助手调用失败：' + getApiError(e), 'error')
    aiAnswer.value = ''
  } finally {
    aiAssisting.value = false
  }
}

const goBack = () => {
  router.push('/learning/code-practice')
}

// ================= 轻量在线调试（推进 2.1 在线调试器） =================

/** 调试运行：逐行追踪（Python）或错误行号定位（其他语言） */
const debugCode = async () => {
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  debugging.value = true
  activeTab.value = 'debug'
  try {
    const res = await codeRunApi.debug({ language: selectedLanguage.value, code: userCode.value })
    debugResult.value = res
    if (res.status !== 'SUCCESS' && res.error) {
      notify(res.errorLine ? `调试发现错误，已定位到第 ${res.errorLine} 行` : '调试发现错误', 'error')
    } else {
      notify('调试完成，无运行时错误', 'success')
    }
  } catch (e: unknown) {
    notify('调试运行失败：' + getApiError(e), 'error')
  } finally {
    debugging.value = false
  }
}

// ================= SC1-AI-02 自动化代码评估 =================

/** 提交评估：动态评测 + 静态检查 + AI 报告（加权评分） */
const assessCode = async () => {
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  assessing.value = true
  activeTab.value = 'assess'
  try {
    const res = await codeRunApi.assess({
      questionId: question.value.id ?? null,
      language: selectedLanguage.value,
      code: userCode.value,
    })
    assessResult.value = res
    notify(`能力评估完成：得分 ${res.score}（${res.level}）`, res.passed ? 'success' : 'info')
  } catch (e: unknown) {
    notify('评估失败：' + getApiError(e), 'error')
  } finally {
    assessing.value = false
  }
}

// ================= SC1-AI-03 代码错题归集 =================

/** 将当前运行错误归集到错题本，并匹配关联知识库文档 */
const collectMistake = async () => {
  if (!runResult.value || !runResult.value.error) {
    notify('当前没有运行错误可归集', 'info')
    return
  }
  collectingMistake.value = true
  try {
    const res = await codeRunApi.collectMistake({
      language: selectedLanguage.value,
      error: runResult.value.error || '',
      code: userCode.value,
      questionId: question.value.id ?? null,
    })
    mistakeResult.value = res
    if (res.collected) {
      notify(`已归集到错题本（${res.errorType}）`, 'success')
    } else {
      notify('该错题已存在，未重复归集', 'info')
    }
  } catch (e: unknown) {
    notify('归集失败：' + getApiError(e), 'error')
  } finally {
    collectingMistake.value = false
  }
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
    userCode.value = normalizeNewlines(data.codeTemplate || DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript)
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
/* ===== 沙箱模式切换按钮（头部） ===== */
.ws-toggle-btn {
  border-color: var(--kb-border);
  color: var(--kb-muted-foreground);
}
.ws-toggle-btn.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}
.ws-toggle-btn:hover:not(.active) {
  background: var(--kb-muted);
}

/* ===== 工作区面板（SC1-IDE-02） ===== */
.ws-panel {
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 12px;
}
.ws-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.ws-actions {
  display: flex;
  gap: 4px;
}
.ws-mini-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.ws-mini-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.ws-loading {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  padding: 8px 0;
}
.ws-file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 220px;
  overflow-y: auto;
}
.ws-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease;
}
.ws-file-item:hover {
  background: var(--kb-muted);
}
.ws-file-item.active {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
  font-weight: 500;
}
.ws-file-name {
  flex: 1;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ws-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}
.ws-file-item:hover .ws-del {
  opacity: 1;
}
.ws-del:hover {
  color: var(--kb-state-error);
  background: rgba(239, 68, 68, 0.1);
}
.ws-empty {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  padding: 8px 4px;
}

/* ===== AI 助手面板（SC1-AI-01） ===== */
.ai-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ai-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  text-align: center;
}
.ai-explain-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid rgba(139, 92, 246, 0.4);
  background: rgba(139, 92, 246, 0.1);
  color: #8B5CF6;
  cursor: pointer;
  transition: background 0.15s ease;
}
.ai-explain-btn:hover {
  background: rgba(139, 92, 246, 0.18);
}
.ai-output {
  flex: 1;
  overflow-y: auto;
  padding: 4px 2px;
}
.ai-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  padding: 8px 0;
}
.ai-answer {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-foreground);
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  margin: 0;
}
.ai-unconfigured {
  margin-top: 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-state-warning);
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: var(--kb-radius-sm);
}
.ai-unconfigured code {
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  color: var(--kb-primary);
}
.ai-input-row {
  display: flex;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--kb-border);
  margin-top: 8px;
}
.ai-input {
  flex: 1;
  height: 40px;
  resize: none;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
}
.ai-input:focus {
  border-color: var(--kb-primary);
}
.ai-ask-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  cursor: pointer;
  flex-shrink: 0;
}
.ai-ask-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.ai-ask-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 运行结果中的 AI 解释快捷按钮 */
.output-ai-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 10px;
  border: 1px solid rgba(139, 92, 246, 0.4);
  background: rgba(139, 92, 246, 0.1);
  color: #8B5CF6;
  cursor: pointer;
  transition: background 0.15s ease;
}
.output-ai-btn:hover {
  background: rgba(139, 92, 246, 0.2);
}
.output-ai-btn.mistake {
  border-color: rgba(59, 111, 224, 0.4);
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}
.output-ai-btn.mistake:hover:not(:disabled) {
  background: rgba(59, 111, 224, 0.2);
}

/* ===== SC1-AI-03 错题归集结果卡片 ===== */
.mistake-block {
  margin-top: 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  padding: 12px;
}
.mistake-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.mistake-type {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.12);
  color: var(--kb-state-error);
}
.mistake-summary {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-foreground);
  white-space: pre-wrap;
}
.mistake-docs {
  margin-top: 10px;
}
.mistake-docs-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
}
.mistake-doc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mistake-doc-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.mistake-doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.mistake-doc-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-primary);
}
.mistake-doc-snippet {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mistake-nodocs {
  margin-top: 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* ===== 调试 Tab（推进 2.1） ===== */
.debug-errline {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-state-error);
}
.debug-trace {
  margin-top: 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  overflow: hidden;
}
.debug-trace-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  padding: 6px 10px;
  background: var(--kb-background);
  border-bottom: 1px solid var(--kb-border);
}
.debug-step {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--kb-border);
  font-size: 12px;
  align-items: flex-start;
}
.debug-step:last-child {
  border-bottom: none;
}
.debug-step.debug-error-line {
  background: rgba(239, 68, 68, 0.1);
}
.debug-line-no {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--kb-primary);
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
}
.debug-event {
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
  text-transform: capitalize;
}
.debug-vars {
  flex: 1;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 11px;
  line-height: 1.5;
  color: var(--kb-foreground);
}

/* ===== 能力评估 Tab（SC1-AI-02） ===== */
.assess-score {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-sm);
  margin-bottom: 10px;
}
.assess-score.pass {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
}
.assess-score.fail {
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.25);
}
.assess-score-num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}
.assess-score.pass .assess-score-num {
  color: var(--kb-state-success);
}
.assess-score.fail .assess-score-num {
  color: var(--kb-state-warning);
}
.assess-score-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.assess-level {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.assess-tests {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.assess-summary {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  margin: 0 0 12px;
}
.assess-issues {
  margin-bottom: 12px;
}
.assess-issues-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
}
.assess-issue-item {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--kb-border);
}
.assess-issue-rule {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--kb-state-warning);
}
.assess-issue-line {
  flex-shrink: 0;
  color: var(--kb-primary);
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
}
.assess-issue-msg {
  color: var(--kb-foreground);
}
.assess-ai {
  margin-top: 8px;
}
.assess-ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #8B5CF6;
  margin-bottom: 6px;
}
.assess-ai-body {
  margin: 0;
  padding: 10px 12px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  line-height: 1.7;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  word-break: break-word;
}
.assess-ai-unconf {
  margin-top: 8px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kb-state-warning);
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: var(--kb-radius-sm);
}

/* 底部调试 / 评估按钮 */
.debug-foot-btn {
  background: rgba(239, 68, 68, 0.06);
  color: var(--kb-state-error);
  border: 1px solid rgba(239, 68, 68, 0.3);
}
.debug-foot-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.14);
  border-color: rgba(239, 68, 68, 0.5);
}
.assess-foot-btn {
  background: rgba(16, 185, 129, 0.06);
  color: var(--kb-state-success);
  border: 1px solid rgba(16, 185, 129, 0.3);
}
.assess-foot-btn:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.14);
  border-color: rgba(16, 185, 129, 0.5);
}
</style>
