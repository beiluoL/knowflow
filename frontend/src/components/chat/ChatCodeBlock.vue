<!-- 聊天代码块：语法高亮 / 复制 / 下载 / 行号 / 长代码折叠 / HTML 预览入口 -->
<template>
  <div class="chat-code">
    <div class="chat-code-head">
      <span class="chat-code-lang">{{ langLabel }}</span>
      <div class="chat-code-actions">
        <button type="button" class="chat-code-btn code-copy-btn" :data-code="encodedCode" :class="{ 'is-done': copied }" :aria-label="copied ? '已复制代码' : '复制代码'" @click="copyCode">
          <Icon :name="copied ? 'check' : 'copy'" :size="13" />
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
        <button type="button" class="chat-code-btn" aria-label="下载代码文件" @click="downloadCode">
          <Icon name="download" :size="13" />
          <span>下载</span>
        </button>
        <button v-if="canPreviewHtml" type="button" class="chat-code-btn" :class="{ 'is-active': previewOpen }" :aria-expanded="previewOpen" aria-label="HTML 预览" @click="previewOpen = !previewOpen">
          <Icon name="eye" :size="13" />
          <span>预览</span>
        </button>
        <button v-if="collapsible" type="button" class="chat-code-btn" :aria-label="expanded ? '收起代码' : '展开全部代码'" @click="expanded = !expanded">
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

    <ChatHtmlPreview v-if="canPreviewHtml && previewOpen" :code="code" :title="`${langLabel} 预览`" @close="previewOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import hljs from 'highlight.js/lib/common'
import Icon from '@/components/ui/Icon.vue'
import ChatHtmlPreview from './ChatHtmlPreview.vue'
import { normalizeLang, buildCodeFileName, resolveFileType, downloadTextFile } from '@/utils/chat/fileTypes'
import { copyTextToClipboard } from '@/utils/chat/clipboard'

const props = withDefaults(defineProps<{
  code: string
  lang?: string
  streaming?: boolean
}>(), { lang: '', streaming: false })

const expanded = ref(false)
const copied = ref(false)
const previewOpen = ref(false)

const langKey = computed(() => normalizeLang(props.lang) || 'text')
const langLabel = computed(() => langKey.value === 'text' ? '纯文本' : langKey.value)
const encodedCode = computed(() => encodeURIComponent(props.code || ''))

const lineCount = computed(() => {
  const text = props.code.replace(/\n$/, '')
  return Math.max(1, text.split('\n').length)
})
const collapsible = computed(() => lineCount.value > 28)
const lineNumbers = computed(() => lineCount.value > 1)

/** 未知语言不做自动猜测高亮，直接转义为纯文本，避免错误着色 */
const highlighted = computed(() => {
  const code = props.code
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
  return /<!doctype\s+html|<html[\s>]/i.test(props.code)
})

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

async function copyCode() {
  const ok = await copyTextToClipboard(props.code)
  if (!ok) return
  copied.value = true
  setTimeout(() => { copied.value = false }, 1600)
}

function downloadCode() {
  const type = resolveFileType(langKey.value)
  downloadTextFile(buildCodeFileName(langKey.value), props.code, type.mime)
}
</script>

<style scoped>
.chat-code {
  margin-block: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm, 8px);
  overflow: hidden;
  background: #1a1d23;
}
.chat-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px 6px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: #15181e;
}
.chat-code-lang {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: #9aa4b2;
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}
.chat-code-actions { display: flex; align-items: center; gap: 2px; }
.chat-code-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #b7c0cc;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.chat-code-btn:hover { background: rgba(255, 255, 255, 0.08); color: #eef2f7; }
.chat-code-btn:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.chat-code-btn.is-done { color: #4ec9a5; }
.chat-code-btn.is-active { color: #7ba7ff; }

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
  background: linear-gradient(to bottom, rgba(26, 29, 35, 0), #1a1d23);
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
  color: #5a6473;
  background: #171a20;
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
  color: #e6edf3;
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
  background: #22262e;
  color: #d5dce6;
  font-size: 12px;
  cursor: pointer;
  z-index: 1;
}
.chat-code-expand:hover { background: #2b3038; }
</style>
