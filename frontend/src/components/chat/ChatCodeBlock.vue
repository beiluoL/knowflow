<!-- 聊天代码块：语法高亮 / 复制 / 下载 / 行号 / 长代码折叠 / HTML 预览 / 一键格式化 / 全屏查看 -->
<template>
  <div class="chat-code">
    <div class="chat-code-head">
      <span class="chat-code-lang">{{ langLabel }}</span>
      <div class="chat-code-actions">
        <button
          v-if="canFormat"
          type="button"
          class="chat-code-btn"
          :class="{ 'is-done': formatted }"
          :disabled="formatting"
          :aria-label="formatted ? '已格式化' : '格式化代码'"
          :title="formatted ? '已按项目规范格式化' : '按项目规范格式化'"
          @click="formatCode"
        >
          <Icon :name="formatting ? 'loader' : formatted ? 'check' : 'wand-2'" :size="13" />
          <span>{{ formatting ? '格式化中' : formatted ? '已格式化' : '格式化' }}</span>
        </button>
        <button
          type="button"
          class="chat-code-btn code-copy-btn"
          :data-code="encodedCode"
          :class="{ 'is-done': copied }"
          :aria-label="copied ? '已复制代码' : '复制代码'"
          @click="copyCode"
        >
          <Icon :name="copied ? 'check' : 'copy'" :size="13" />
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
        <button type="button" class="chat-code-btn" aria-label="下载代码文件" @click="downloadCode">
          <Icon name="download" :size="13" />
          <span>下载</span>
        </button>
        <button
          v-if="canPreviewHtml"
          type="button"
          class="chat-code-btn"
          :class="{ 'is-active': previewOpen }"
          :aria-expanded="previewOpen"
          aria-label="HTML 预览"
          @click="previewOpen = !previewOpen"
        >
          <Icon name="eye" :size="13" />
          <span>预览</span>
        </button>
        <button
          type="button"
          class="chat-code-btn"
          :class="{ 'is-active': fullscreen }"
          :aria-expanded="fullscreen"
          aria-label="全屏查看代码"
          @click="fullscreen = true"
        >
          <Icon name="maximize" :size="13" />
          <span>全屏</span>
        </button>
        <button
          v-if="collapsible"
          type="button"
          class="chat-code-btn"
          :aria-label="expanded ? '收起代码' : '展开全部代码'"
          @click="expanded = !expanded"
        >
          <Icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="13" />
          <span>{{ expanded ? '收起' : '展开' }}</span>
        </button>
      </div>
    </div>

    <div
      class="chat-code-body"
      :class="{ 'is-collapsed': collapsible && !expanded, 'with-lines': lineNumbers }"
    >
      <div v-if="lineNumbers" class="chat-code-lines" aria-hidden="true">
        <span v-for="n in lineCount" :key="n">{{ n }}</span>
      </div>
      <pre class="chat-code-pre"><code class="hljs" :class="`language-${langKey}`" v-html="highlighted"></code></pre>
      <button
        v-if="collapsible && !expanded"
        type="button"
        class="chat-code-expand"
        @click="expanded = true"
      >展开全部 {{ lineCount }} 行</button>
    </div>

    <ChatHtmlPreview v-if="canPreviewHtml && previewOpen" :code="displayCode" :title="`${langLabel} 预览`" @close="previewOpen = false" />

    <Teleport to="body">
      <div
        v-if="fullscreen"
        class="chat-code-modal"
        role="dialog"
        aria-modal="true"
        :aria-label="`${langLabel} 代码全屏查看`"
        @click.self="fullscreen = false"
      >
        <div class="chat-code-modal-box">
          <div class="chat-code-modal-head">
            <span class="chat-code-lang">{{ langLabel }} · 全屏查看</span>
            <div class="chat-code-actions">
              <button
                v-if="canFormat"
                type="button"
                class="chat-code-btn"
                :class="{ 'is-done': formatted }"
                :disabled="formatting"
                aria-label="格式化代码"
                @click="formatCode"
              >
                <Icon :name="formatting ? 'loader' : formatted ? 'check' : 'wand-2'" :size="13" />
                <span>{{ formatted ? '已格式化' : '格式化' }}</span>
              </button>
              <button type="button" class="chat-code-btn" :class="{ 'is-done': copied }" aria-label="复制代码" @click="copyCode">
                <Icon :name="copied ? 'check' : 'copy'" :size="13" />
                <span>复制</span>
              </button>
              <button type="button" class="chat-code-btn" aria-label="下载代码文件" @click="downloadCode">
                <Icon name="download" :size="13" />
                <span>下载</span>
              </button>
              <button type="button" class="chat-code-btn" :class="{ 'is-active': true }" aria-label="关闭全屏" @click="fullscreen = false">
                <Icon name="x" :size="13" />
                <span>关闭</span>
              </button>
            </div>
          </div>
          <div class="chat-code-body with-lines chat-code-modal-body">
            <div v-if="lineNumbers" class="chat-code-lines" aria-hidden="true">
              <span v-for="n in lineCount" :key="n">{{ n }}</span>
            </div>
            <pre class="chat-code-pre"><code class="hljs" :class="`language-${langKey}`" v-html="highlighted"></code></pre>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import hljs from 'highlight.js/lib/common'
import Icon from '@/components/ui/Icon.vue'
import ChatHtmlPreview from './ChatHtmlPreview.vue'
import { normalizeLang, buildCodeFileName, resolveFileType, downloadTextFile } from '@/utils/chat/fileTypes'
import { copyTextToClipboard } from '@/utils/chat/clipboard'
import { notify } from '@/utils/toast'

const props = withDefaults(defineProps<{
  code: string
  lang?: string
  streaming?: boolean
}>(), { lang: '', streaming: false })

const expanded = ref(false)
const copied = ref(false)
const previewOpen = ref(false)
const fullscreen = ref(false)
const formatting = ref(false)
const formatted = ref(false)

const langKey = computed(() => normalizeLang(props.lang) || 'text')
const langLabel = computed(() => langKey.value === 'text' ? '纯文本' : langKey.value)
const encodedCode = computed(() => encodeURIComponent(displayCode.value || ''))

/**
 * 展示用代码：默认同步父组件传入的 code（含流式增量），
 * 用户点「格式化」后切换为本地格式化副本。
 * 仅当父组件传入了「新的原始内容」（流式推进）时才回写，避免覆盖已格式化视图。
 */
const rawProp = ref(props.code)
const displayCode = ref(props.code)
watch(
  () => props.code,
  (nv) => {
    if (nv === rawProp.value && formatted.value) return
    rawProp.value = nv
    displayCode.value = nv
    formatted.value = false
    copied.value = false
  }
)

const lineCount = computed(() => {
  const text = (displayCode.value || '').replace(/\n$/, '')
  return Math.max(1, text.split('\n').length)
})
const collapsible = computed(() => lineCount.value > 28)
const lineNumbers = computed(() => lineCount.value > 1)

/** 未知语言不做自动猜测高亮，直接转义为纯文本，避免错误着色 */
const highlighted = computed(() => {
  const code = displayCode.value
  if (!langKey.value || langKey.value === 'text') return escapeHtml(code)
  if (hljs.getLanguage(langKey.value)) {
    try {
      return hljs.highlight(code, { language: langKey.value, ignoreIllegals: true }).value
    } catch {
      return escapeHtml(code)
    }
  }
  return escapeHtml(code)
})

const canPreviewHtml = computed(() => {
  const key = langKey.value
  if (key === 'html' || key === 'xml') return true
  return /<!doctype\s+html|<html[\s>]/i.test(displayCode.value)
})

/** 浏览器端可格式化的 Web 语言（Java/Vue 等需项目级格式化，这里禁用以免误导） */
const FORMAT_LANGS = new Set(['ts', 'tsx', 'js', 'jsx', 'mjs', 'cjs', 'json', 'css', 'scss', 'less', 'md', 'html', 'xml'])
const canFormat = computed(() => FORMAT_LANGS.has(langKey.value))

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

async function copyCode() {
  const ok = await copyTextToClipboard(displayCode.value)
  if (!ok) return
  copied.value = true
  setTimeout(() => { copied.value = false }, 1600)
}

function downloadCode() {
  const type = resolveFileType(langKey.value)
  downloadTextFile(buildCodeFileName(langKey.value), displayCode.value, type.mime)
}

/** 浏览器端 Prettier 插件与 parser 映射（按需动态加载，避免增大主包） */
function parserFor(key: string): string {
  if (key === 'ts' || key === 'tsx') return 'typescript'
  if (key === 'json') return 'json'
  if (key === 'js' || key === 'jsx' || key === 'mjs' || key === 'cjs') return 'babel'
  if (key === 'css' || key === 'scss' || key === 'less') return 'css'
  if (key === 'md') return 'markdown'
  if (key === 'html' || key === 'xml') return 'html'
  return 'babel'
}

async function loadPlugins(key: string): Promise<unknown[]> {
  const list: Promise<{ default: unknown }>[] = []
  if (key === 'ts' || key === 'tsx') {
    list.push(import('prettier/plugins/typescript'))
    list.push(import('prettier/plugins/estree'))
  }
  if (['js', 'jsx', 'mjs', 'cjs', 'json'].includes(key)) {
    list.push(import('prettier/plugins/babel'))
    list.push(import('prettier/plugins/estree'))
  }
  if (['css', 'scss', 'less'].includes(key)) list.push(import('prettier/plugins/postcss'))
  if (key === 'md') list.push(import('prettier/plugins/markdown'))
  if (key === 'html' || key === 'xml') list.push(import('prettier/plugins/html'))
  const resolved = await Promise.all(list)
  return resolved.map((m) => m.default)
}

/** 一键格式化：调用浏览器端 Prettier（与项目 .prettierrc 一致：无分号、单引号、2 空格） */
async function formatCode() {
  if (!canFormat.value || formatting.value) return
  formatting.value = true
  try {
    const prettier = (await import('prettier/standalone')).default
    const plugins = await loadPlugins(langKey.value)
    const out = await prettier.format(displayCode.value, {
      parser: parserFor(langKey.value),
      plugins,
      semi: false,
      singleQuote: true,
      printWidth: 100,
      tabWidth: 2,
    } as Parameters<typeof prettier.format>[1])
    displayCode.value = out.replace(/\n$/, '')
    formatted.value = true
    notify('已按项目规范格式化', 'success')
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    notify(`该代码无法格式化：${msg.split('\n')[0]}`, 'error', 4000)
  } finally {
    formatting.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && fullscreen.value) fullscreen.value = false
}
onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.chat-code {
  margin-block: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm, 8px);
  overflow: hidden;
  /* 代码块暗色表面：复用项目令牌，禁止写死 hex */
  background: var(--kf-content-code-background);
}
.chat-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px 6px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: color-mix(in srgb, var(--kf-content-code-background) 90%, black);
}
.chat-code-lang {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--kf-muted-foreground);
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}
.chat-code-actions { display: flex; align-items: center; gap: 2px; flex-wrap: wrap; }
.chat-code-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.chat-code-btn:hover { background: rgba(255, 255, 255, 0.08); color: var(--kf-foreground); }
.chat-code-btn:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.chat-code-btn:disabled { opacity: 0.6; cursor: default; }
.chat-code-btn.is-done { color: var(--kf-state-success); }
.chat-code-btn.is-active { color: var(--kf-primary); }

.chat-code-body {
  position: relative;
  display: flex;
  max-width: 100%;
  overflow-x: auto;
}
.chat-code-body.is-collapsed {
  max-height: calc(28 * 1.55em + 16px);
  overflow: hidden;
}
.chat-code-body.is-collapsed::after {
  content: '';
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  height: 56px;
  pointer-events: none;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0), var(--kf-content-code-background));
}
.chat-code-lines {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  padding: 12px 8px 12px 12px;
  text-align: right;
  user-select: none;
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 13px;
  line-height: 1.55;
  color: var(--kf-muted-foreground);
  background: color-mix(in srgb, var(--kf-content-code-background) 96%, black);
}
.chat-code-pre {
  margin: 0;
  padding: 12px 14px;
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
}
.chat-code-pre code {
  display: block;
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 13px;
  line-height: 1.55;
  color: var(--kf-foreground);
  white-space: pre;
  tab-size: 2;
}
.chat-code-expand {
  position: absolute;
  inset-inline: 0;
  bottom: 8px;
  margin-inline: auto;
  width: fit-content;
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: color-mix(in srgb, var(--kf-content-code-background) 82%, black);
  color: var(--kf-muted-foreground);
  font-size: 12px;
  cursor: pointer;
  z-index: 1;
}
.chat-code-expand:hover { background: color-mix(in srgb, var(--kf-content-code-background) 75%, black); }

/* 全屏查看遮罩 */
.chat-code-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.62);
}
.chat-code-modal-box {
  display: flex;
  flex-direction: column;
  width: min(1000px, 100%);
  max-height: 86vh;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  overflow: hidden;
  background: var(--kf-content-code-background);
}
.chat-code-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: color-mix(in srgb, var(--kf-content-code-background) 90%, black);
}
.chat-code-modal-body {
  overflow: auto;
  max-height: calc(86vh - 44px);
}
</style>
