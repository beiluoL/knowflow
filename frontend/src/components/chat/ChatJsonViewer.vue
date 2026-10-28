<!-- JSON Viewer：折叠 / 原始视图 / 复制 / 下载 / 评分卡片 -->
<template>
  <div class="json-viewer">
    <div class="json-viewer-bar">
      <span class="json-viewer-title">
        <Icon name="code" :size="13" />
        JSON
      </span>
      <div class="json-viewer-actions">
        <div class="json-view-switch" role="group" aria-label="JSON 视图切换">
          <button type="button" :class="{ 'is-active': view === 'formatted' }" @click="view = 'formatted'">格式化</button>
          <button type="button" :class="{ 'is-active': view === 'raw' }" @click="view = 'raw'">原始</button>
        </div>
        <button type="button" class="json-btn" :class="{ 'is-done': copied }" aria-label="复制 JSON" @click="copyJson">
          <Icon :name="copied ? 'check' : 'copy'" :size="13" />
        </button>
        <button type="button" class="json-btn" aria-label="下载 JSON 文件" @click="downloadJson">
          <Icon name="download" :size="13" />
        </button>
        <button type="button" class="json-btn" aria-label="折叠全部" @click="collapseAll">
          <Icon name="chevron-up" :size="13" />
        </button>
        <button type="button" class="json-btn" aria-label="展开全部" @click="expandAll">
          <Icon name="chevron-down" :size="13" />
        </button>
      </div>
    </div>

    <div v-if="grading" class="grading-card">
      <div class="grading-head">
        <div class="grading-score">
          <span class="grading-score-value">{{ grading.score }}</span>
          <span v-if="grading.grade" class="grading-grade">{{ grading.grade }}</span>
        </div>
        <p v-if="grading.gradeDescription" class="grading-desc">{{ grading.gradeDescription }}</p>
      </div>
      <p v-if="grading.overallComment" class="grading-comment">{{ grading.overallComment }}</p>
      <div v-if="grading.dimensions.length" class="grading-dims">
        <div v-for="(dim, index) in grading.dimensions" :key="`${dim.name}-${index}`" class="grading-dim">
          <div class="grading-dim-head">
            <span class="grading-dim-name">{{ dim.name }}</span>
            <span v-if="dim.score !== undefined" class="grading-dim-score">{{ dim.score }}</span>
          </div>
          <p v-if="dim.comment" class="grading-dim-comment">{{ dim.comment }}</p>
        </div>
      </div>
      <button type="button" class="grading-raw-toggle" @click="view = view === 'raw' ? 'formatted' : 'raw'">
        {{ view === 'raw' ? '收起 JSON 原文' : '查看 JSON 原文' }}
      </button>
    </div>

    <div v-if="view === 'raw'" class="json-raw">
      <pre>{{ prettyRaw }}</pre>
    </div>
    <div v-else class="json-tree">
      <ChatJsonNode :key="treeKey" :node="value" :default-expanded-depth="expandDepth" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import ChatJsonNode from './ChatJsonNode.vue'
import { detectGradingJson } from '@/utils/chat/contentParser'
import { copyTextToClipboard } from '@/utils/chat/clipboard'
import { downloadTextFile } from '@/utils/chat/fileTypes'

const props = withDefaults(defineProps<{
  value: unknown
  raw?: string
}>(), { raw: '' })

const view = ref<'formatted' | 'raw'>('formatted')
const copied = ref(false)
const treeKey = ref(0)
const expandDepth = ref(2)

const grading = computed(() => detectGradingJson(props.value))
const prettyRaw = computed(() => props.raw || safeStringify(props.value))

function safeStringify(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function collapseAll() {
  expandDepth.value = 0
  treeKey.value += 1
}

function expandAll() {
  expandDepth.value = 99
  treeKey.value += 1
}

async function copyJson() {
  const ok = await copyTextToClipboard(prettyRaw.value)
  if (!ok) return
  copied.value = true
  setTimeout(() => { copied.value = false }, 1600)
}

function downloadJson() {
  downloadTextFile('chat-response.json', prettyRaw.value, 'application/json')
}
</script>

<style scoped>
.json-viewer {
  margin-block: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm, 8px);
  background: var(--kf-card);
  overflow: hidden;
}
.json-viewer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px 6px 12px;
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-muted, #f6f7f9);
}
.json-viewer-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kf-muted-foreground);
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}
.json-viewer-actions { display: flex; align-items: center; gap: 2px; }
.json-view-switch {
  display: inline-flex;
  padding: 2px;
  margin-inline-end: 4px;
  border: 1px solid var(--kf-border);
  border-radius: 999px;
  background: var(--kf-card);
}
.json-view-switch button {
  padding: 2px 9px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-xs);
  cursor: pointer;
}
.json-view-switch button.is-active {
  background: var(--kf-primary-soft, rgba(59, 111, 224, 0.12));
  color: var(--kf-primary);
}
.json-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.json-btn:hover { background: var(--kf-border); color: var(--kf-foreground); }
.json-btn:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.json-btn.is-done { color: var(--kf-state-success, #1a9f6a); }
.json-tree { padding: 12px 14px; max-height: 520px; overflow: auto; }
.json-raw { max-height: 520px; overflow: auto; background: #1a1d23; }
.json-raw pre {
  margin: 0;
  padding: 14px;
  font-family: var(--kf-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: var(--kf-fs-caption);
  line-height: 1.6;
  color: #e6edf3;
  white-space: pre-wrap;
  word-break: break-word;
}

.grading-card {
  padding: 14px 16px;
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-primary-soft, rgba(59, 111, 224, 0.06));
}
.grading-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.grading-score { display: inline-flex; align-items: baseline; gap: 6px; }
.grading-score-value { font-size: var(--kf-fs-h2); font-weight: 700; color: var(--kf-primary); line-height: 1.1; }
.grading-grade { font-size: 13px; font-weight: 600; color: var(--kf-foreground); }
.grading-desc { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.grading-comment { margin: 8px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); line-height: 1.7; }
.grading-dims { margin-top: 10px; display: grid; gap: 8px; }
.grading-dim {
  padding: 8px 10px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
}
.grading-dim-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.grading-dim-name { font-size: 13px; font-weight: 600; color: var(--kf-foreground); }
.grading-dim-score { font-size: 13px; color: var(--kf-primary); font-variant-numeric: tabular-nums; }
.grading-dim-comment { margin: 4px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); line-height: 1.65; }
.grading-raw-toggle {
  margin-top: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
}
.grading-raw-toggle:hover { text-decoration: underline; }
</style>
