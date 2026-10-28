<template>
  <div class="playground-page" :data-section="section">
    <ChapterResourceLinks resource-kind="code" :resource-id="Number(route.params.id)" />
    <!-- ===== 顶部导航栏 ===== -->
    <div class="playground-header">
      <div class="header-left">
        <button type="button" class="back-btn" title="返回列表" aria-label="返回列表" @click="goBack">
          <Icon name="arrow-left" :size="20" />
        </button>
        <div class="header-info">
          <span class="diff-badge" :class="`diff-${question.difficulty ?? 0}`">{{ difficultyLabel(question.difficulty) }}</span>
          <span class="lang-tag" :style="{ background: langColorBg(question.language), color: langColor(question.language) }">{{ langLabel(question.language) }}</span>
          <h1 class="header-title">{{ question.title || '加载中...' }}</h1>
        </div>
      </div>
      <div class="header-right">
        <select :value="selectedLanguage" class="lang-select kf-select kf-form-control" :disabled="unavailable || workspaceMode" aria-label="选择编程语言" @change="onLanguageChange">
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
          :disabled="unavailable || !aiCapability.aiAvailable"
          @click="handleAiAnswer"
        >
          <Icon name="sparkles" :size="14" />
          <span>{{ aiAnswering ? 'AI 思考中...' : 'AI 回答' }}</span>
        </button>
        <button type="button" class="header-btn hint-btn" :disabled="loading || !!loadError" :aria-pressed="showHint" @click="showHint = !showHint">
          <Icon name="lightbulb" :size="14" />
          <span>提示</span>
        </button>
        <button
          type="button"
          class="header-btn ws-toggle-btn" :disabled="unavailable" :aria-pressed="workspaceMode"
          :class="{ active: workspaceMode }"
          @click="toggleWorkspace"
        >
          <Icon name="folder" :size="14" />
          <span>工作区模式</span>
        </button>
      </div>
    </div>

    <nav class="workspace-sections" aria-label="工作台分区">
      <button v-for="item in [{id:'problem',name:'题目'}, {id:'code',name:'代码'}, {id:'result',name:'结果'}]" :key="item.id" type="button" :aria-pressed="section === item.id" @click="section = item.id as typeof section">{{ item.name }}</button>
    </nav>
    <div class="capability-notice" role="status">{{ selectedLanguage === 'sql' ? 'SQL 仅使用内置示例数据模拟，不连接数据库，不计判题通过。' : '当前未接入安全判题服务；运行、调试及评估仅限已授权的可信开发环境。' }} <span v-if="!aiCapability.aiAvailable">AI 当前不可用，仍可阅读和编辑。</span></div>
    <p v-if="workspaceError" class="workspace-error" role="alert">{{ workspaceError }}</p>
    <!-- ===== 主体：左右分栏 ===== -->
    <div class="playground-body" :style="{ gridTemplateColumns: `minmax(0, ${panelWidth}fr) var(--kf-space-1) minmax(0, ${100 - panelWidth}fr)` }">
      <!-- ===== 左侧：题目描述 ===== -->
      <section class="problem-panel" aria-label="题目说明">
        <StateView :status="loading ? 'loading' : loadError ? 'error' : 'ready'" error-title="题目加载失败" :error-description="loadError" @retry="loadQuestion">
          <!-- 工作区面板（SC1-IDE-02 可信开发文件工作区，仅工作区模式显示） -->
          <div v-if="workspaceMode" class="panel-section ws-panel">
            <div class="ws-header">
              <h3 class="subsection-heading">
                <Icon name="folder" :size="14" style="display: inline; margin-right: 4px; color: var(--kf-primary);" />
                我的工作区
              </h3>
              <div class="ws-actions">
                <button type="button" class="ws-mini-btn" :disabled="unavailable" title="新建文件" aria-label="新建文件" @click="createWsFilePrompt">
                  <Icon name="plus" :size="14" />
                </button>
                <button type="button" class="ws-mini-btn" :disabled="unavailable" title="重置工作区" aria-label="重置工作区" @click="resetWorkspace">
                  <Icon name="rotate-ccw" :size="14" />
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
                role="button"
                tabindex="0"
                @click="openWsFile(f.path)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              >
                <Icon :name="wsIcon(f.language)" :size="14" />
                <span class="ws-file-name">{{ f.name }}</span>
                <button type="button" class="ws-del" :disabled="unavailable" title="删除" aria-label="删除文件" @click.stop="deleteWsFile(f.path)">
                  <Icon name="trash-2" :size="12" />
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
              <div class="problem-desc markdown-body" v-html="descriptionHtml" @click="handleCodeCopyClick"></div>
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
                <Icon name="lightbulb" :size="14" style="color: var(--kf-state-warning); display: inline; margin-right: 4px;" />
                提示
              </h3>
              <div class="hint-block">{{ question.hint }}</div>
            </div>

            <!-- SQL 题目显示可用表 -->
            <div v-if="selectedLanguage === 'sql'" class="panel-section">
              <h3 class="subsection-heading">SQL 示例数据（模拟）</h3>
              <p class="problem-desc" style="font-size: var(--kf-fs-body-sm);">
                可用表：<code class="example-code">users</code>、<code class="example-code">orders</code>、<code class="example-code">products</code>
              </p>
              <p class="problem-desc" style="font-size: var(--kf-fs-caption); margin-top: 4px;">
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
              <h3 class="subsection-heading">历史提交统计</h3><p class="editor-status">历史记录不代表当前安全判题结果或个人掌握度。</p>
              <div class="stats-block">
                <div class="stats-item">
                  <span class="stats-label">累计提交</span>
                  <span class="stats-value">{{ question.submitCount }} 次</span>
                </div>
                <div class="stats-item">
                  <span class="stats-label">通过次数</span>
                  <span class="stats-value" style="color: var(--kf-state-success);">{{ question.passCount || 0 }} 次</span>
                </div>
                <div class="stats-item">
                  <span class="stats-label">通过率</span>
                  <span class="stats-value">{{ passRate }}%</span>
                </div>
              </div>
            </div>
          </template>
        </StateView>
      </section>

      <!-- ===== 拖拽分隔线 ===== -->
      <div
        class="resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整题目面板宽度"
        tabindex="0" :aria-valuenow="panelWidth" :aria-valuemin="25" :aria-valuemax="65"
        @keydown.left.prevent="panelWidth = Math.max(25, panelWidth - 5)"
        @keydown.right.prevent="panelWidth = Math.min(65, panelWidth + 5)"
        @mousedown="startResize"
      ></div>

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
              <button type="button" class="editor-action-btn" title="复制代码" aria-label="复制代码" :disabled="loading || !!loadError" @click="copyCode"><Icon name="copy" :size="14" /></button>
              <button type="button" class="editor-action-btn" title="重置代码" aria-label="重置代码" :disabled="unavailable" @click="resetCode">
                <Icon name="rotate-ccw" :size="14" />
              </button>
              <span title="格式化代码（Shift+Alt+F，macOS：Shift+Option+F）"><Button class="format-button" variant="secondary" size="sm" icon-name="align-left" :disabled="unavailable" :loading="formatting" title="格式化代码（Shift+Alt+F，macOS：Shift+Option+F）" @click="formatManually">格式化</Button></span>
              <details class="format-settings" @keydown.esc="closeFormatSettings">
                <summary title="设置自动格式化">自动格式化</summary>
                <div class="format-settings-panel" role="group" aria-label="自动格式化设置">
                  <label class="kf-checkbox-row"><Checkbox as="span" v-model="formatSettings.enabled" />启用自动格式化</label>
                  <label class="kf-checkbox-row"><Checkbox as="span" v-model="formatSettings.paste"  :disabled="!formatSettings.enabled" />粘贴后格式化</label>
                  <label class="kf-checkbox-row"><Checkbox as="span" v-model="formatSettings.save"  :disabled="!formatSettings.enabled" />保存前格式化</label>
                  <label class="kf-checkbox-row"><Checkbox as="span" v-model="formatSettings.input"  :disabled="!formatSettings.enabled" />输入时格式化（高级）</label>
                  <p>粘贴后等待内容更新；输入停顿 600ms 后格式化。语法未完成时保留原文。</p>
                  <p>当前支持 JavaScript、TypeScript。普通练习是临时草稿，仅工作区文件支持保存。</p>
                  <Button variant="text" size="sm" @click="resetFormatSettings">恢复推荐设置</Button>
                  <p v-if="formatPersistenceError" role="status">{{ formatPersistenceError }}</p>
                </div>
              </details>
            </div>
          </div>
          <p id="editor-keyboard-help" class="editor-status" role="status" aria-live="polite">{{ workspaceMode ? (dirty ? '有未保存修改' : '工作区文件内容') : dirty ? '临时代码 · 未保存修改' : '临时代码 · 尚未保存' }} · Tab 切换焦点，Ctrl+] 缩进</p>
          <p v-if="formatMessage || !formatSupported" class="editor-status format-status" role="status" aria-live="polite">{{ formatMessage || '当前语言暂不支持格式化，可继续编辑和保存原代码' }}</p>
          <div class="editor-wrapper">
            <div class="line-numbers" aria-hidden="true">
              <span v-for="n in lineCount" :key="n">{{ n }}</span>
            </div>
            <textarea
              ref="codeAreaRef"
              v-model="userCode"
              class="code-textarea" aria-label="练习代码" aria-describedby="editor-keyboard-help" :disabled="unavailable"
              spellcheck="false"
              placeholder="在此编写你的代码..."
              @keydown="handleKeydown"
              @paste="formatter.onPaste"
              @input="formatter.onInput"
              @compositionstart="formatter.onCompositionStart"
              @compositionend="formatter.onCompositionEnd"
              @scroll="syncScroll"
            ></textarea>
          </div>
        </div>

        <label v-if="selectedLanguage !== 'sql'" class="stdin-field">标准输入（可选）<textarea class="kf-textarea kf-form-control" v-model="stdin" aria-label="标准输入" :disabled="unavailable" rows="1" placeholder="传给程序的输入内容"></textarea></label>
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
          <div class="console-body" aria-live="polite" :aria-busy="busy">
            <!-- 运行结果 Tab -->
            <div v-show="activeTab === 'output'">
              <div v-if="running" class="console-empty" role="status"><span class="spinner"></span><p>正在请求运行，请等待服务端返回…</p></div>
              <div v-else-if="!runResult" class="console-empty">
                <Icon name="terminal" :size="32" style="opacity: 0.3;" />
                <p>运行请求的输出与错误会显示在这里</p>
              </div>
              <div v-else class="console-output">
                <div class="output-status" :class="runStatusClass">
                  <Icon :name="runStatusIcon" :size="14" />
                  <span>{{ statusLabel(runResult?.status) }}</span>
                  <span class="output-time">{{ runResult?.time }}ms</span>
                  <button v-if="runResult?.error" type="button" class="output-ai-btn" :disabled="unavailable || !aiCapability.aiAvailable" title="用 AI 解释这个错误" @click="explainError">
                    <Icon name="sparkles" :size="12" /> AI 解释
                  </button>
                  <button v-if="runResult?.error" type="button" class="output-ai-btn mistake" title="归集到错题本" :disabled="collectingMistake || unavailable" @click="collectMistake">
                    <Icon name="bookmark" :size="12" /> {{ collectingMistake ? '归集...' : '归集错题' }}
                  </button>
                </div>
                <pre v-if="runResult.output" class="output-content">{{ runResult.output }}</pre>
                <pre v-if="runResult.error" class="output-error">{{ runResult.error }}</pre>

                <!-- SC1-AI-03 错题归集结果 -->
                <div v-if="mistakeResult" class="mistake-block">
                  <div class="mistake-head">
                    <Icon name="bookmark" :size="14" />
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
                <p>安全判题尚未接入。用例仅供阅读，不会生成通过记录。</p>
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
                <Icon name="sparkles" :size="24" style="opacity: 0.4" />
                <p>AI 助手可解释运行错误、回答编程问题。</p>
                <button v-if="runResult && runResult.error" type="button" :disabled="unavailable || !aiCapability.aiAvailable" class="ai-explain-btn" @click="explainError">
                  <Icon name="sparkles" :size="14" /> 用 AI 解释这个错误
                </button>
              </div>
              <div v-else class="ai-output">
                <div v-if="aiAssisting" class="ai-thinking">
                  <span class="spinner" style="width: 16px; height: 16px"></span> AI 思考中…
                </div>
                <pre v-if="aiAnswer" class="ai-answer">{{ aiAnswer }}</pre>
                <div v-if="!aiConfigured" class="ai-unconfigured">
                  AI 当前不可用，请稍后重试；编辑内容不会因此丢失。
                </div>
              </div>
              <div class="ai-input-row">
                <textarea
                  v-model="aiQuestion"
                  class="ai-input kf-textarea kf-form-control" aria-label="向 AI 提问" :disabled="unavailable || !aiCapability.aiAvailable"
                  placeholder="问 AI 任何编程问题，例如：如何优化这段代码？"
                  @keydown.ctrl.enter="askAi"
                ></textarea>
                <button type="button" class="ai-ask-btn" :disabled="unavailable || !aiCapability.aiAvailable" @click="askAi">
                  <Icon name="send" :size="12" /> 提问
                </button>
              </div>
            </div>

            <!-- 调试 Tab（推进 2.1 在线调试器） -->
            <div v-show="activeTab === 'debug'" class="debug-tab">
              <div v-if="debugging" class="console-empty" role="status"><span class="spinner"></span><p>正在请求调试…</p></div>
              <div v-else-if="!debugResult" class="console-empty">
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
              <div v-if="assessing" class="console-empty" role="status"><span class="spinner"></span><p>正在请求评估…</p></div>
              <div v-else-if="!assessResult" class="console-empty">
                <Icon name="list-checks" :size="32" style="opacity: 0.3;" />
                <p>评估需要可信开发授权，成功后才显示服务端返回的报告。</p>
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
                  AI 报告未生成。下方只展示服务端实际返回的评估结果。
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
            <Button v-if="workspaceMode" :disabled="unavailable || !dirty || !activeWsFile" :loading="saving" @click="saveWorkspace">保存文件</Button>
            <button type="button" class="footer-btn debug-foot-btn" :disabled="unavailable || !canRun || selectedLanguage === 'sql'" @click="debugCode">
              <Icon name="target" :size="14" />
              <span>{{ debugging ? '调试中...' : '调试运行' }}</span>
            </button>
            <button type="button" class="footer-btn assess-foot-btn" :disabled="unavailable || !canRun || selectedLanguage === 'sql'" @click="assessCode">
              <Icon name="list-checks" :size="14" />
              <span>{{ assessing ? '评估中...' : '提交评估' }}</span>
            </button>
            <button type="button" class="footer-btn run-btn" :disabled="unavailable || !canRun" @click="runCode">
              <Icon name="play" :size="14" />
              <span>{{ running ? '请求处理中…' : selectedLanguage === 'sql' ? '模拟 SQL' : '请求运行' }}</span>
            </button>
            <button type="button" class="footer-btn submit-btn" disabled title="安全判题服务尚未接入，不上传客户端通过数">
              <Icon name="send" :size="14" />
              <span>安全判题未接入</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import ChapterResourceLinks from '@/components/Learning/ChapterResourceLinks.vue'
/**
 * 代码练习编辑器（LeetCode 风格）
 * 左侧题目描述 + 右侧代码编辑器 + 控制台输出
 * 代码编辑不等同保存；SQL 仅示例模拟，宿主运行仍由服务端显式授权。
 * 不在主应用执行用户源码，不以客户端用例统计冒充可信判题。
 */
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAiCapabilityStore } from '@/stores/aiCapability'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeCopyClick } from '@/utils/codeCopy'
import { notify, getApiError, confirmDialog, promptDialog } from '@/utils/toast'
import { normalizeNewlines } from '@/utils/string'
import { chatApi } from '@/api/chat'
import { codeQuestionApi } from '@/api/codeQuestion'
import { codeRunApi } from '@/api/codeRun'
import type { CodeAssessResultDTO, CodeDebugResultDTO, CodeMistakeCollectResultDTO } from '@/api/codeRun'
import { codeWorkspaceApi } from '@/api/codeWorkspace'
import type { CodeWorkspaceFileDTO } from '@/api/codeWorkspace'
import { runSql } from '@/utils/sqlSimulator'
import { useCodeFormatting } from '@/composables/useCodeFormatting'
import { supportsFormatting } from '@/code-format/client'
import type { CodeQuestionVO, CodeTestCase } from '@/api/types'

const route = useRoute()
const router = useRouter()
const aiCapability = useAiCapabilityStore()
const baselineCode = ref('')
const previousLanguage = ref('javascript')
const section = ref<'problem' | 'code' | 'result'>('code')
const saving = ref(false)
const confirming = ref(false)
const panelWidth = ref(40)
const workspaceError = ref('')
const stdin = ref('')
let questionRequest = 0
const dirty = computed(() => userCode.value !== baselineCode.value)
const busy = computed(() => running.value || debugging.value || assessing.value || aiAnswering.value || aiAssisting.value || saving.value || wsLoading.value || confirming.value)
const unavailable = computed(() => busy.value || loading.value || !!loadError.value)
const canRun = computed(() => selectedLanguage.value !== 'typescript')
const descriptionHtml = computed(() => renderMarkdown(normalizeNewlines(question.value.description || '暂无题目说明')))
// 在任何会覆盖编辑器的操作前保护草稿；临时模板也不标成服务端已保存。
async function confirmOverwrite(message: string) {
  if (confirming.value) return false
  confirming.value = true
  try {
    return await confirmDialog(message, {title:'放弃未保存的修改？',confirmText:'放弃修改',cancelText:'返回编辑',icon:'log-out'})
  } finally { confirming.value = false }
}
async function allowDiscard() {
  if (busy.value) { notify('操作仍在进行，请稍后再试', 'info'); return false }
  return !dirty.value || await confirmOverwrite('代码有未保存的修改，确定放弃这些修改？')
}
async function copyCode() {
  try { await navigator.clipboard.writeText(userCode.value); notify('代码已复制（尚未保存）', 'success') }
  catch { notify('复制失败，请选中代码手动复制', 'error') }
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(allowDiscard)
onBeforeRouteUpdate(allowDiscard)

const question = ref<CodeQuestionVO>({} as CodeQuestionVO)
const selectedLanguage = ref('javascript')
const userCode = ref('')
const showHint = ref(false)
const running = ref(false)
const aiAnswering = ref(false)
const loading = ref(true)
const loadError = ref('')
const activeTab = ref<'output' | 'testcase' | 'debug' | 'assess' | 'ai'>('output')
const runResult = ref<{ output: string; error: string | null; time: number; status?: string } | null>(null)
const codeAreaRef = ref<HTMLTextAreaElement | null>(null)

// ===== 工作区模式（SC1-IDE-02 可信开发文件工作区） =====
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

const formatContext = computed(() => `${route.params.id}:${questionRequest}:${workspaceMode.value}:${activeWsFile.value}`)
const formatter = useCodeFormatting({ source: userCode, language: selectedLanguage, context: formatContext, area: codeAreaRef, blocked: unavailable })
const { settings: formatSettings, persistenceError: formatPersistenceError, reset: resetFormatSettings,
  formatting, message: formatMessage, format: formatEditor } = formatter
const formatSupported = computed(() => supportsFormatting(selectedLanguage.value))
function formatManually() {
  if (unavailable.value || formatting.value) return
  codeAreaRef.value?.focus({ preventScroll: true })
  void formatEditor('manual')
}
function closeFormatSettings(event: KeyboardEvent) {
  const details = event.currentTarget as HTMLDetailsElement
  details.open = false
  details.querySelector('summary')?.focus()
}




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
# 注意：运行需要可信开发授权；当前没有安全判题服务。

def solution():
    # TODO: 实现你的解决方案
    print("Hello, KnowFlow!")

solution()`,
  java: `// 在此编写你的 Java 代码
// 注意：运行需要可信开发授权；当前没有安全判题服务。

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
-- SQL 示例数据（模拟）：users、orders、products
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
  if (r.error || (r.status && !['SUCCESS', 'SIMULATED'].includes(r.status))) return 'error'
  return 'success'
})
const runStatusIcon = computed(() => (runStatusClass.value === 'error' ? 'x-circle' : 'check-circle'))

/** 运行结果状态中文标签 */
const statusLabel = (s?: string) => {
  const map: Record<string, string> = {
    SUCCESS: '运行成功',
    SIMULATED: '示例模拟完成',
    SIMULATION_ERROR: '示例模拟错误',
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
  javascript: 'var(--kf-primary)',
  typescript: 'var(--kf-primary)',
  python: 'var(--kf-success)',
  java: 'var(--kf-warning)',
  cpp: 'var(--kf-primary)',
  sql: 'var(--kf-danger)',
}
const langColor = (lang?: string) => LANG_COLORS[lang || ''] || 'var(--kf-muted-foreground)'
const langColorBg = (lang?: string) => {
  const hex = LANG_COLORS[lang || '']
  return hex ? `color-mix(in srgb, ${hex} 10%, var(--kf-card))` : 'var(--kf-muted)'
}

const onLanguageChange = async (event: Event) => {
  const select = event.target as HTMLSelectElement
  const target = select.value
  select.value = selectedLanguage.value
  if (!(await allowDiscard())) return
  selectedLanguage.value = target
  previousLanguage.value = target
  if (workspaceMode.value) {
    // 工作区模式下语言由当前文件扩展名决定，切换下拉不改变文件内容
    return
  }
  userCode.value = DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
  baselineCode.value = userCode.value
  runResult.value = null
  tcResults.value = []
}

const resetCode = async () => {
  if (unavailable.value || !(await confirmOverwrite('确定重置代码？当前编辑内容将被模板替换。'))) return
  if (workspaceMode.value) {
    notify('工作区模式下请使用「重置工作区」按钮', 'info')
    return
  }
  // 优先使用题目自带的代码模板
  if (question.value.codeTemplate && selectedLanguage.value === question.value.language) {
    userCode.value = normalizeNewlines(question.value.codeTemplate)
  } else {
    userCode.value = DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript
  }
  runResult.value = null
  tcResults.value = []
  baselineCode.value = userCode.value
  notify('代码已重置为临时模板，尚未保存', 'info')
}

const clearConsole = () => {
  runResult.value = null
  tcResults.value = []
  debugResult.value = null
  assessResult.value = null
  mistakeResult.value = null
}

/** Tab/Shift+Tab 保留自然焦点导航；Ctrl+] 为明确的缩进快捷键。 */
const handleKeydown = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) return
  if (formatter.handleHistory(event)) return
  if (event.shiftKey && event.altKey && !event.ctrlKey && !event.metaKey && (event.code === 'KeyF' || event.key.toLowerCase() === 'f')) {
    event.preventDefault()
    if (!formatting.value) void formatEditor('manual')
    return
  }
  if (event.key !== ']' || !event.ctrlKey) return
  event.preventDefault()
  const el = codeAreaRef.value
  if (!el) return
  const start = el.selectionStart
  userCode.value = userCode.value.slice(0, start) + '  ' + userCode.value.slice(el.selectionEnd)
  void nextTick(() => { el.selectionStart = el.selectionEnd = start + 2 })
}

/** 同步行号滚动 */
const syncScroll = (e: Event) => {
  const el = e.target as HTMLTextAreaElement
  const lineNumbers = el.previousElementSibling as HTMLElement
  if (lineNumbers) {
    lineNumbers.scrollTop = el.scrollTop
  }
}

/** 只复用现有受控宿主接口或 SQL 示例解析器；不在应用进程执行代码。 */
const runCode = async () => {
  if (unavailable.value || !canRun.value) return
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  running.value = true
  runResult.value = null
  activeTab.value = 'output'
  section.value = 'result'
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
      if (!(await formatter.beforeSave())) { notify('格式化未完成，本次未保存或运行，请检查代码或关闭保存前格式化', 'warning'); return }
      await codeWorkspaceApi.save({ path: activeWsFile.value, content: userCode.value })
      baselineCode.value = userCode.value
      const file = workspaceFiles.value.find(item => item.path === activeWsFile.value)
      if (file) file.content = userCode.value
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
      // 可信开发宿主进程，非沙盒；服务端检查启用状态、身份和授权名单。
      const res = await codeRunApi.run({ language: lang, code: userCode.value, stdin: stdin.value })
      output = res.output || ''
      error = res.error || null
      status = res.status
    } else if (lang === 'sql') {
      const result = runSql(userCode.value)
      output = result.output
      error = result.error
      status = result.error ? 'SIMULATION_ERROR' : 'SIMULATED'
    } else {
      throw new Error('当前语言未接入安全执行服务，可继续编辑和复制代码')
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

// 旧 submit 接口只接收客户端统计，不是可信 Judge；本阶段不调用、不伪造通过记录。
/** AI 回答：识别左侧题目，自动生成代码并填入编辑器 */
const handleAiAnswer = async () => {
  if (unavailable.value || !aiCapability.aiAvailable) return
  if (!(await allowDiscard())) return
  const original = userCode.value
  const request = questionRequest
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

    if (request !== questionRequest || userCode.value !== original) { notify('编辑内容已改变，未覆盖当前草稿', 'info'); return }
    userCode.value = code.trim()
    notify('AI 代码已填入临时草稿，请检查后再使用', 'success')
  } catch (e: unknown) {
    notify('AI 回答失败：' + getApiError(e), 'error')
  } finally {
    aiAnswering.value = false
  }
}

// ================= 工作区模式（SC1-IDE-02 可信开发文件工作区） =================

/** 切换工作区/练习模式 */
const toggleWorkspace = async () => {
  if (unavailable.value || !(await allowDiscard())) return
  workspaceError.value = ''
  workspaceMode.value = !workspaceMode.value
  if (workspaceMode.value) {
    await loadWorkspace()
  } else {
    selectedLanguage.value = previousLanguage.value = (question.value.language || 'javascript').toLowerCase()
    userCode.value = baselineCode.value = normalizeNewlines(question.value.codeTemplate || DEFAULT_TEMPLATES[selectedLanguage.value] || '')
  }
}

/** 加载工作区文件列表，并自动选中首个文件 / 建立起步文件 */
const loadWorkspace = async () => {
  wsLoading.value = true
  try {
    const files = await codeWorkspaceApi.list()
    workspaceFiles.value = files
    const target = files.find((f) => f.path === activeWsFile.value) || files[0]
    if (target) await openWsFile(target.path)
    else { activeWsFile.value = ''; userCode.value = baselineCode.value = '' }
  } catch (e: unknown) {
    workspaceError.value = '工作区不可用：' + getApiError(e)
    workspaceMode.value = false
    notify(workspaceError.value, 'error')
  } finally {
    wsLoading.value = false
  }
}

/** 打开（切换）工作区文件，将内容载入编辑器 */
const openWsFile = async (path: string) => {
  if (running.value || saving.value || debugging.value || assessing.value || aiAnswering.value || aiAssisting.value) return
  const f = workspaceFiles.value.find((x) => x.path === path)
  if (!f || (dirty.value && !(await confirmDialog('切换文件将放弃未保存修改，确定继续？', {"title":"放弃未保存的修改？","confirmText":"放弃并离开","cancelText":"返回编辑","icon":"log-out","variant":"default"})))) return
  activeWsFile.value = path
  userCode.value = baselineCode.value = normalizeNewlines(f.content || '')
  runResult.value = null
  tcResults.value = []
  const ext = (path.split('.').pop() || '').toLowerCase()
  const map: Record<string, string> = { py: 'python', java: 'java', js: 'javascript', ts: 'typescript', cpp: 'cpp', cc: 'cpp', cxx: 'cpp', c: 'cpp', sql: 'sql', go: 'go', rs: 'rust' }
  selectedLanguage.value = previousLanguage.value = map[ext] || 'text'
}

async function saveWorkspace() {
  if (unavailable.value || !workspaceMode.value || !activeWsFile.value) return
  saving.value = true
  try {
    if (!(await formatter.beforeSave())) { notify('格式化未完成，本次未保存，请检查代码或关闭保存前格式化', 'warning'); return }
    const snapshot = userCode.value
    await codeWorkspaceApi.save({ path: activeWsFile.value, content: snapshot })
    baselineCode.value = snapshot
    const file = workspaceFiles.value.find(item => item.path === activeWsFile.value)
    if (file) file.content = snapshot
    notify('工作区文件已保存', 'success')
  } catch (e) { notify('保存失败，草稿仍保留：' + getApiError(e), 'error') }
  finally { saving.value = false }
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
  await confirmDialog('确定删除文件 ' + path + ' ？', { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              await codeWorkspaceApi.remove(path);
              if (activeWsFile.value === path)
                  activeWsFile.value = '';
              await loadWorkspace();
              if (workspaceFiles.value.length)
                  await openWsFile(workspaceFiles.value[0].path);
              notify('已删除 ' + path, 'success');
          }
          catch (e: unknown) {
              notify('删除失败：' + getApiError(e), 'error');
              throw e;
          }
      } });
}

/** 重置工作区：清空全部文件 */
const resetWorkspace = async () => {
  await confirmDialog('确定重置工作区？将清空你保存的全部文件（不可恢复）。', {
    title:'清空工作区？',confirmText:'确认清空',cancelText:'取消',icon:'trash-2',variant:'danger',
    onConfirm:async()=>{
      await codeWorkspaceApi.reset()
      workspaceFiles.value=[];activeWsFile.value='';userCode.value='';runResult.value=null
      notify('工作区已重置','success')
    }
  })
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
  if (unavailable.value || !aiCapability.aiAvailable) return
  aiAssisting.value = true
  activeTab.value = 'ai'
  section.value = 'result'
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
  if (unavailable.value || !canRun.value || selectedLanguage.value === 'sql') return
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  debugging.value = true
  debugResult.value = null
  activeTab.value = 'debug'
  section.value = 'result'
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
  if (unavailable.value || !canRun.value || selectedLanguage.value === 'sql') return
  if (!userCode.value.trim()) {
    notify('请先编写代码', 'warning')
    return
  }
  assessing.value = true
  assessResult.value = null
  activeTab.value = 'assess'
  section.value = 'result'
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
  if (collectingMistake.value || unavailable.value) return
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
  const request = ++questionRequest
  question.value = {} as CodeQuestionVO
  runResult.value = null; debugResult.value = null; assessResult.value = null; aiAnswer.value = ''; workspaceMode.value = false
  workspaceFiles.value = []; activeWsFile.value = ''; workspaceError.value = ''; testCases.value = []; tcResults.value = []
  userCode.value = baselineCode.value = ''
  loading.value = true
  loadError.value = ''
  try {
    const id = Number(route.params.id)
    if (!id) {
      loadError.value = '题目 ID 无效'
      return
    }
    const data = await codeQuestionApi.detail(id)
    if (request !== questionRequest) return
    question.value = data
    // 设置语言：优先用题目主语言
    selectedLanguage.value = previousLanguage.value = (data.language || 'javascript').toLowerCase()
    // 设置初始代码：优先用题目模板
    userCode.value = normalizeNewlines(data.codeTemplate || DEFAULT_TEMPLATES[selectedLanguage.value] || DEFAULT_TEMPLATES.javascript)
    baselineCode.value = userCode.value
    // 解析测试用例
    try {
      const parsed = data.testCases ? JSON.parse(data.testCases) : []
      testCases.value = Array.isArray(parsed) ? parsed : []
    } catch {
      testCases.value = []
    }
  } catch (e: unknown) {
    if (request === questionRequest) loadError.value = '加载题目失败：' + getApiError(e)
  } finally {
    if (request === questionRequest) loading.value = false
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
  if (window.innerWidth > 900 && leftWidth > 25 && leftWidth < 65) {
    panelWidth.value = Math.round(leftWidth)
  }
}

const stopResize = () => {
  isResizing = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  void loadQuestion()
  void aiCapability.refresh()
  window.addEventListener('beforeunload', beforeUnload)
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
})

watch(() => route.params.id, () => { void loadQuestion() })

onUnmounted(() => {
  questionRequest++
  stopResize()
  window.removeEventListener('beforeunload', beforeUnload)
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.format-settings { position: relative; font-size: var(--kf-fs-body-sm); }
.format-settings summary { cursor: pointer; color: var(--kf-primary); padding: var(--kf-space-2); border-radius: var(--kf-radius-sm); }
.format-settings summary:focus-visible { outline: var(--kf-field-focus-width) solid var(--kf-ring); }
.format-settings-panel { position: absolute; inset-inline-end: 0; top: 100%; z-index: 30; width: min(20rem, calc(100vw - var(--kf-space-8))); padding: var(--kf-space-4); display: grid; gap: var(--kf-space-3); background: var(--kf-popover); color: var(--kf-popover-foreground); border: var(--kf-studio-rule) solid var(--kf-border); border-radius: var(--kf-radius-md); box-shadow: var(--shadow-dropdown-2); }
.format-settings-panel label { display: flex; align-items: center; gap: var(--kf-space-2); }
.format-settings-panel input { accent-color: var(--kf-primary); }
.format-settings-panel p { color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption); line-height: var(--kf-lh-body-sm); }
.format-status { overflow-wrap: anywhere; }
.editor-toolbar, .editor-actions { flex-wrap: wrap; gap: var(--kf-space-2); }
.editor-toolbar { overflow: visible; }
.editor-container { overflow: visible; }

.playground-page {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  min-width: 0;
  color: var(--kf-foreground);
  background: var(--kf-background);
}

/* 统一键盘焦点环：等价于 focus-visible:ring-2 ring-[--kf-ring] ring-offset-2
 * 覆盖本页所有可交互元素（按钮 / 下拉 / 输入 / role=button 列表项） */
.playground-page :is(button, select, textarea, [role='button']):focus-visible:not(.kf-form-control, .kf-input, .kf-textarea, .kf-select, .kf-select-trigger) {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-radius: var(--kf-radius-sm);
}

/* ===== 顶部导航栏 ===== */
.playground-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--kf-notes-rail-width);
  padding: var(--kf-space-3) var(--kf-space-5);
  flex-wrap: wrap;
  gap: var(--kf-space-3);
  background: var(--kf-card);
  border-bottom: 1px solid var(--kf-border);
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
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
  flex-shrink: 0;
}
.back-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-primary);
}
.back-btn:active {
  transform: scale(0.96);
  background: var(--kf-muted);
}
.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.header-title {
  font-size: var(--kf-fs-body-lg);
  line-height: var(--kf-lh-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
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
.lang-select:not(.kf-form-control) {
  height: 32px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.lang-select {
  padding: 0 12px;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  cursor: pointer;
}
.lang-select:not(.kf-form-control):hover {
  border-color: var(--kf-primary);
}
.lang-select:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kf-primary) 10%, transparent);
}
.header-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.header-btn:hover {
  background: var(--kf-muted);
}
.header-btn:active:not(:disabled) {
  transform: scale(0.98);
}
.header-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* AI 回答按钮（头部） */
.ai-btn {
  border-color: var(--kf-primary);
  background: color-mix(in srgb, var(--kf-primary) 8%, transparent);
  color: var(--kf-primary);
}
.ai-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-primary) 15%, transparent);
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
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  flex-shrink: 0;
}

/* ===== 主体布局 ===== */
.playground-body {
  display: grid;
  grid-template-columns: minmax(0, 2fr) var(--kf-space-1) minmax(0, 3fr);
  min-width: 0;
  flex: 1;
  min-height: 0;
}

/* ===== 拖拽分隔线 ===== */
.resizer {
  cursor: col-resize;
  background: var(--kf-border);
  transition: background 0.15s ease;
}
.resizer:hover,
.resizer:active {
  background: var(--kf-primary);
}

/* ===== 左侧题目面板 ===== */
.problem-panel {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: var(--kf-card);
}
.panel-section {
  margin-bottom: 24px;
}
.section-heading {
  font-size: var(--kf-fs-h4);
  font-weight: var(--kf-fw-h4);
  line-height: var(--kf-lh-h4);
  color: var(--kf-foreground);
  margin-bottom: 12px;
}
.subsection-heading {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  line-height: var(--kf-lh-body-md);
  color: var(--kf-foreground);
  margin-bottom: 8px;
}
.problem-desc {
  font-size: var(--kf-fs-body-md);
  line-height: 1.7;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.example-block {
  background: var(--kf-background);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
  border: 1px solid var(--kf-border);
}
.example-row {
  display: flex;
  gap: 8px;
  font-size: var(--kf-fs-body-sm);
  margin-bottom: 4px;
}
.example-row:last-child {
  margin-bottom: 0;
}
.example-label {
  color: var(--kf-muted-foreground);
  font-weight: 500;
  flex-shrink: 0;
}
.example-code {
  font-family: var(--font-mono);
  color: var(--kf-primary);
  min-width: 0;
  word-break: break-word;
}
.hint-block {
  background: color-mix(in srgb, var(--kf-warning) 6%, transparent);
  border: 1px solid color-mix(in srgb, var(--kf-warning) 15%, transparent);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.6;
  color: var(--kf-foreground);
}
.testcase-item {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  margin-bottom: 8px;
  overflow: hidden;
}
.testcase-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--kf-background);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.testcase-num {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.testcase-result {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kf-fs-xs);
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
}
.testcase-result.pass {
  background: color-mix(in srgb, var(--kf-success) 10%, transparent);
  color: var(--kf-state-success);
}
.testcase-result.fail {
  background: color-mix(in srgb, var(--kf-danger) 10%, transparent);
  color: var(--kf-state-error);
}
.testcase-body {
  padding: 12px;
}
.testcase-row {
  display: flex;
  gap: 8px;
  font-size: var(--kf-fs-caption);
  margin-bottom: 4px;
  font-family: var(--font-mono);
}
.testcase-row code {
  min-width: 0;
  word-break: break-word;
}
.testcase-row:last-child {
  margin-bottom: 0;
}
.testcase-label {
  color: var(--kf-muted-foreground);
  flex-shrink: 0;
}
.text-success {
  color: var(--kf-state-success);
}
.text-error {
  color: var(--kf-state-error);
}

/* 提交统计 */
.stats-block {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
}
.stats-item {
  text-align: center;
  min-width: 0;
}
.stats-label {
  display: block;
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  margin-bottom: 4px;
}
.stats-value {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
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
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
}
.error-block {
  color: var(--kf-state-error);
}
.spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--kf-border);
  border-top-color: var(--kf-primary);
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
  background: var(--kf-card);
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
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-background);
  flex-shrink: 0;
}
.editor-tabs {
  display: flex;
  gap: 4px;
}
.editor-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-primary);
  background: var(--kf-card);
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
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
  border-radius: var(--kf-radius-sm);
  border: none;
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.editor-action-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-primary);
}
.editor-action-btn:active {
  transform: scale(0.94);
  background: var(--kf-muted);
}

/* 代码编辑器 */
.editor-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
  background: var(--kf-background);
  min-width: 0;
  position: relative;
}
.line-numbers {
  display: flex;
  flex-direction: column;
  padding: 16px 8px 16px 16px;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  line-height: 1.6;
  color: var(--kf-muted-foreground);
  text-align: right;
  user-select: none;
  overflow: hidden;
  flex-shrink: 0;
  min-width: 40px;
}
.line-numbers span {
  line-height: 1.6;
}
.code-textarea {
  flex: 1;
  padding: 16px 16px 16px 8px;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  line-height: 1.6;
  background: transparent;
  color: var(--kf-foreground);
  min-width: 0;
  border: none;
  outline: none;
  resize: none;
  white-space: pre;
  overflow: auto;
  tab-size: 2;
}
.code-textarea::placeholder {
  color: var(--kf-muted-foreground);
}

/* 控制台 */
.console-container {
  flex-shrink: 0;
  border-top: 1px solid var(--kf-border);
  background: var(--kf-card);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-background);
  flex-shrink: 0;
}
.console-tabs {
  display: flex;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.console-tabs::-webkit-scrollbar {
  display: none;
}
.console-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-muted-foreground);
  background: transparent;
  border: none;
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.console-tab:hover {
  background: var(--kf-muted);
}
.console-tab:active {
  transform: scale(0.98);
  background: var(--kf-muted);
}
.console-tab.active {
  color: var(--kf-primary);
  background: color-mix(in srgb, var(--kf-primary) 8%, transparent);
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 8px;
  font-size: var(--kf-fs-xs);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.tab-badge.pass {
  background: color-mix(in srgb, var(--kf-success) 15%, transparent);
  color: var(--kf-state-success);
}
.tab-badge.fail {
  background: color-mix(in srgb, var(--kf-danger) 15%, transparent);
  color: var(--kf-state-error);
}
.console-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}
.console-clear:hover {
  color: var(--kf-foreground);
  background: var(--kf-muted);
}
.console-clear:active {
  transform: scale(0.96);
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
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
}
.console-output {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
}
.output-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  margin-bottom: 8px;
}
.output-status.success {
  color: var(--kf-state-success);
}
.output-status.error {
  color: var(--kf-state-error);
}
.output-time {
  margin-left: auto;
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  font-weight: 400;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.output-content {
  background: var(--kf-background);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: var(--kf-foreground);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.output-error {
  background: color-mix(in srgb, var(--kf-danger) 6%, transparent);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: var(--kf-state-error);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 8px 0 0;
}

/* 底部操作栏 */
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--kf-notes-rail-width);
  padding: var(--kf-space-3) var(--kf-space-4);
  flex-wrap: wrap;
  gap: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
  background: var(--kf-card);
  flex-shrink: 0;
}
.footer-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}
.footer-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kf-fs-xs);
  color: var(--kf-muted-foreground);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.footer-right {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 16px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  border-radius: var(--kf-radius-sm);
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s ease, background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.footer-btn:active:not(:disabled) {
  transform: scale(0.98);
}
.run-btn {
  background: var(--kf-card);
  color: var(--kf-foreground);
  border: 1px solid var(--kf-border);
}
.run-btn:hover:not(:disabled) {
  background: var(--kf-muted);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.submit-btn {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}
.submit-btn:hover:not(:disabled) {
  opacity: 0.9;
}

/* AI 回答按钮（底部） */
.ai-foot-btn {
  background: color-mix(in srgb, var(--kf-primary) 8%, transparent);
  color: var(--kf-primary);
  border: 1px solid color-mix(in srgb, var(--kf-primary) 30%, transparent);
}
.ai-foot-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-primary) 15%, transparent);
  border-color: color-mix(in srgb, var(--kf-primary) 50%, transparent);
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
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  flex-shrink: 0;
}
.diff-0 {
  background: color-mix(in srgb, var(--kf-success) 10%, transparent);
  color: var(--kf-state-success);
}
.diff-1 {
  background: color-mix(in srgb, var(--kf-warning) 10%, transparent);
  color: var(--kf-state-warning);
}
.diff-2 {
  background: color-mix(in srgb, var(--kf-danger) 10%, transparent);
  color: var(--kf-state-error);
}

/* 小屏仅显示一个工作分区；桌面保留题目与编辑/结果并排。 */
.workspace-sections { display: none; }
.capability-notice, .workspace-error, .editor-status { padding: var(--kf-space-2) var(--kf-space-4); font-size: var(--kf-fs-caption); line-height: var(--kf-lh-body-md); color: var(--kf-muted-foreground); border-bottom: 1px solid var(--kf-border); }
.workspace-error { color: var(--kf-danger); }
.editor-status { background: var(--kf-card); }
.stdin-field { display: flex; align-items: center; gap: var(--kf-space-3); padding: var(--kf-space-2) var(--kf-space-4); font-size: var(--kf-fs-caption); border-top: 1px solid var(--kf-border); }
.stdin-field textarea { min-width: 0; flex: 1; background: var(--kf-background); color: var(--kf-foreground); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); padding: var(--kf-space-2); }
.problem-desc :deep(pre) { max-width: 100%; overflow: auto; }
.problem-desc :deep(table) { display: block; max-width: 100%; overflow-x: auto; }
.playground-page button, .playground-page select { min-height: var(--kf-btn-height-sm); }
@media (max-width: 900px) {
  .workspace-sections { display: flex; gap: var(--kf-space-2); padding: var(--kf-space-2) var(--kf-space-4); border-bottom: 1px solid var(--kf-border); }
  .workspace-sections button { flex: 1; padding: var(--kf-space-2); border-radius: var(--kf-radius-sm); color: var(--kf-muted-foreground); }
  .workspace-sections button[aria-pressed="true"] { color: var(--kf-primary); background: var(--kf-muted); }
  .playground-body { display: flex; min-height: 0; }
  .resizer { display: none; }
  .problem-panel, .editor-panel { flex: 1; min-height: 0; }
  [data-section="problem"] .editor-panel, [data-section="code"] .problem-panel, [data-section="result"] .problem-panel { display: none; }
  [data-section="code"] .console-container { display: none; }
  [data-section="result"] .editor-container, [data-section="result"] .stdin-field { display: none; }
  [data-section="result"] .console-container { height: auto !important; flex: 1; }
  .playground-header { padding: var(--kf-space-3) var(--kf-space-4); }
  .header-left, .header-right { max-width: 100%; flex-wrap: wrap; }
  .header-info { flex: 1; }
  .header-title { white-space: normal; overflow-wrap: anywhere; }
  .header-right { gap: var(--kf-space-2); }
  .editor-footer { padding: var(--kf-space-3); }
  .footer-left { width: 100%; }
  .footer-right { justify-content: flex-start; gap: var(--kf-space-2); }
  .footer-btn { padding: 0 var(--kf-space-2); }
}
@media (prefers-reduced-motion: reduce) { .playground-page *, .playground-page *::before { animation: none !important; transition: none !important; } }
/* ===== 工作区模式切换按钮（头部） ===== */
.ws-toggle-btn {
  border-color: var(--kf-border);
  color: var(--kf-muted-foreground);
}
.ws-toggle-btn.active {
  border-color: var(--kf-primary);
  background: color-mix(in srgb, var(--kf-primary) 10%, transparent);
  color: var(--kf-primary);
}
.ws-toggle-btn:hover:not(.active) {
  background: var(--kf-muted);
}

/* ===== 工作区面板（SC1-IDE-02） ===== */
.ws-panel {
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
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
  width: 28px;
  height: 28px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.ws-mini-btn:hover {
  background: var(--kf-muted);
  color: var(--kf-primary);
  border-color: var(--kf-primary);
}
.ws-mini-btn:active {
  transform: scale(0.94);
}
.ws-loading {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  padding: 8px 0;
}
.ws-file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
}
.ws-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  cursor: pointer;
  min-width: 0;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.ws-file-item:hover {
  background: var(--kf-muted);
}
.ws-file-item:active {
  background: var(--kf-muted);
  transform: scale(0.99);
}
.ws-file-item.active {
  background: color-mix(in srgb, var(--kf-primary) 12%, transparent);
  color: var(--kf-primary);
  font-weight: 500;
}
.ws-file-name {
  flex: 1;
  min-width: 0;
  font-family: var(--font-mono);
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
  color: var(--kf-muted-foreground);
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  flex-shrink: 0;
  transition: opacity 0.15s ease, color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}
.ws-file-item:hover .ws-del,
.ws-file-item:focus-within .ws-del,
.ws-del:focus-visible {
  opacity: 1;
}
.ws-del:hover {
  color: var(--kf-state-error);
  background: color-mix(in srgb, var(--kf-danger) 10%, transparent);
}
.ws-del:active {
  transform: scale(0.9);
  background: color-mix(in srgb, var(--kf-danger) 18%, transparent);
}
.ws-empty {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
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
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  text-align: center;
}
.ai-explain-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 16px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  border-radius: var(--kf-radius-sm);
  border: 1px solid color-mix(in srgb, var(--kf-primary) 40%, transparent);
  background: color-mix(in srgb, var(--kf-primary) 10%, transparent);
  color: var(--kf-primary);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.ai-explain-btn:hover {
  background: color-mix(in srgb, var(--kf-primary) 18%, transparent);
  border-color: color-mix(in srgb, var(--kf-primary) 60%, transparent);
}
.ai-explain-btn:active {
  transform: scale(0.98);
  background: color-mix(in srgb, var(--kf-primary) 24%, transparent);
}
.ai-output {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}
.ai-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  padding: 8px 0;
}
.ai-answer {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.7;
  color: var(--kf-foreground);
  font-family: var(--font-mono);
  margin: 0;
}
.ai-unconfigured {
  margin-top: 12px;
  padding: 8px 12px;
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: var(--kf-state-warning);
  background: color-mix(in srgb, var(--kf-warning) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--kf-warning) 20%, transparent);
  border-radius: var(--kf-radius-sm);
}
.ai-unconfigured code {
  font-family: var(--font-mono);
  color: var(--kf-primary);
}
.ai-input-row {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--kf-border);
  margin-top: 8px;
}
.ai-input:not(.kf-form-control) {
  height: 40px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-background);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.ai-input {
  flex: 1;
  min-width: 0;
  resize: none;
  padding: 8px 12px;
  font-size: var(--kf-fs-body-sm);
  font-family: inherit;
  color: var(--kf-foreground);
}
.ai-input:not(.kf-form-control):hover {
  border-color: var(--kf-primary);
}
.ai-input:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kf-primary) 10%, transparent);
}
.ai-ask-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 16px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  border: none;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  cursor: pointer;
  flex-shrink: 0;
  white-space: nowrap;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.ai-ask-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.ai-ask-btn:active:not(:disabled) {
  transform: scale(0.98);
  opacity: 0.85;
}
.ai-ask-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 运行结果中的 AI 解释快捷按钮 */
.output-ai-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: var(--kf-fs-xs);
  font-weight: 500;
  border-radius: var(--kf-radius-md);
  border: 1px solid color-mix(in srgb, var(--kf-primary) 40%, transparent);
  background: color-mix(in srgb, var(--kf-primary) 10%, transparent);
  color: var(--kf-primary);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.output-ai-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-primary) 20%, transparent);
}
.output-ai-btn:active:not(:disabled) {
  transform: scale(0.96);
}
.output-ai-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.output-ai-btn.mistake {
  border-color: color-mix(in srgb, var(--kf-primary) 40%, transparent);
  background: color-mix(in srgb, var(--kf-primary) 10%, transparent);
  color: var(--kf-primary);
}
.output-ai-btn.mistake:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-primary) 20%, transparent);
}

/* ===== SC1-AI-03 错题归集结果卡片 ===== */
.mistake-block {
  margin-top: 12px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  padding: 12px;
}
.mistake-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 6px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-foreground);
}
.mistake-type {
  margin-left: auto;
  font-size: var(--kf-fs-xs);
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--kf-danger) 12%, transparent);
  color: var(--kf-state-error);
}
.mistake-summary {
  margin: 8px 0 0;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.6;
  color: var(--kf-foreground);
  white-space: pre-wrap;
}
.mistake-docs {
  margin-top: 12px;
}
.mistake-docs-title {
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  color: var(--kf-muted-foreground);
  margin-bottom: 8px;
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
  padding: 8px 12px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
}
.mistake-doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.mistake-doc-name {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-primary);
}
.mistake-doc-snippet {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mistake-nodocs {
  margin-top: 8px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

/* ===== 调试 Tab（推进 2.1） ===== */
.debug-errline {
  margin-bottom: 8px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-state-error);
}
.debug-trace {
  margin-top: 12px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  overflow: hidden;
}
.debug-trace-title {
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  color: var(--kf-muted-foreground);
  padding: 8px 12px;
  background: var(--kf-background);
  border-bottom: 1px solid var(--kf-border);
}
.debug-step {
  display: flex;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--kf-border);
  font-size: var(--kf-fs-caption);
  align-items: flex-start;
}
.debug-step:last-child {
  border-bottom: none;
}
.debug-step.debug-error-line {
  background: color-mix(in srgb, var(--kf-danger) 10%, transparent);
}
.debug-line-no {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--kf-primary);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
.debug-event {
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
  text-transform: capitalize;
}
.debug-vars {
  flex: 1;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-xs);
  line-height: 1.5;
  color: var(--kf-foreground);
}

/* ===== 能力评估 Tab（SC1-AI-02） ===== */
.assess-score {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  border-radius: var(--kf-radius-sm);
  margin-bottom: 12px;
}
.assess-score.pass {
  background: color-mix(in srgb, var(--kf-success) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--kf-success) 25%, transparent);
}
.assess-score.fail {
  background: color-mix(in srgb, var(--kf-warning) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--kf-warning) 25%, transparent);
}
.assess-score-num {
  font-size: var(--kf-fs-h2);
  font-weight: var(--kf-fw-h2);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.assess-score.pass .assess-score-num {
  color: var(--kf-state-success);
}
.assess-score.fail .assess-score-num {
  color: var(--kf-state-warning);
}
.assess-score-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.assess-level {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}
.assess-tests {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.assess-summary {
  font-size: var(--kf-fs-body-sm);
  line-height: 1.7;
  color: var(--kf-foreground);
  white-space: pre-wrap;
  margin: 0 0 12px;
}
.assess-issues {
  margin-bottom: 12px;
}
.assess-issues-title {
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  color: var(--kf-muted-foreground);
  margin-bottom: 8px;
}
.assess-issue-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  padding: 8px 0;
  font-size: var(--kf-fs-caption);
  border-bottom: 1px dashed var(--kf-border);
}
.assess-issue-rule {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--kf-state-warning);
}
.assess-issue-line {
  flex-shrink: 0;
  color: var(--kf-primary);
  font-family: var(--font-mono);
}
.assess-issue-msg {
  color: var(--kf-foreground);
  min-width: 0;
  flex: 1;
  word-break: break-word;
}
.assess-ai {
  margin-top: 8px;
}
.assess-ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 600;
  color: var(--kf-primary);
  margin-bottom: 6px;
}
.assess-ai-body {
  margin: 0;
  padding: 12px;
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-caption);
  line-height: 1.7;
  color: var(--kf-foreground);
  white-space: pre-wrap;
  word-break: break-word;
}
.assess-ai-unconf {
  margin-top: 8px;
  padding: 8px 12px;
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: var(--kf-state-warning);
  background: color-mix(in srgb, var(--kf-warning) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--kf-warning) 20%, transparent);
  border-radius: var(--kf-radius-sm);
}

/* 底部调试 / 评估按钮 */
.debug-foot-btn {
  background: color-mix(in srgb, var(--kf-danger) 6%, transparent);
  color: var(--kf-state-error);
  border: 1px solid color-mix(in srgb, var(--kf-danger) 30%, transparent);
}
.debug-foot-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-danger) 14%, transparent);
  border-color: color-mix(in srgb, var(--kf-danger) 50%, transparent);
}
.assess-foot-btn {
  background: color-mix(in srgb, var(--kf-success) 6%, transparent);
  color: var(--kf-state-success);
  border: 1px solid color-mix(in srgb, var(--kf-success) 30%, transparent);
}
.assess-foot-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--kf-success) 14%, transparent);
  border-color: color-mix(in srgb, var(--kf-success) 50%, transparent);
}
</style>
