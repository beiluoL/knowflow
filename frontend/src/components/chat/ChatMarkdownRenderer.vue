<!-- 聊天消息渲染层：Markdown 分组 + 代码块组件 + 整条 JSON Viewer，输出统一经过 DOMPurify -->
<template>
  <div class="chat-markdown prose-chat" @click="onClick">
    <template v-if="parsed.json !== null">
      <ChatJsonViewer :value="parsed.json" :raw="parsed.jsonRaw" />
    </template>
    <template v-else>
      <template v-for="(block, index) in parsed.blocks" :key="index">
        <ChatCodeBlock v-if="block.kind === 'code'" :code="block.code" :lang="block.lang" :streaming="streaming" />
        <!-- eslint-disable-next-line vue/no-v-html -- 内容已经过 DOMPurify 与协议白名单清洗 -->
        <div v-else class="chat-markdown-html" v-html="block.html"></div>
      </template>
      <span v-if="streaming" class="chat-markdown-cursor" aria-hidden="true"></span>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ChatCodeBlock from './ChatCodeBlock.vue'
import ChatJsonViewer from './ChatJsonViewer.vue'
import { parseChatContent } from '@/utils/chat/contentParser'

const props = withDefaults(defineProps<{
  content: string
  streaming?: boolean
}>(), { streaming: false })

const emit = defineEmits<{
  (e: 'preview-image', payload: { images: string[]; index: number }): void
}>()

const parsed = computed(() => parseChatContent(props.content, { streaming: props.streaming }))

/** 图片点击：收集当前消息内全部图片，交给 ChatImageViewer 统一查看 */
function onClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target || target.tagName !== 'IMG') return
  const img = target as HTMLImageElement
  const clicked = img.getAttribute('src') || img.currentSrc
  if (!clicked) return
  event.preventDefault()
  const root = event.currentTarget as HTMLElement
  const images = Array.from(root.querySelectorAll('img'))
    .map((el) => (el.getAttribute('src') || '').trim())
    .filter(Boolean)
  const index = Math.max(0, images.indexOf(clicked))
  emit('preview-image', { images: images.length ? images : [clicked], index })
}
</script>

<style scoped>
.chat-markdown {
  font-size: var(--kb-fs-body-lg);
  line-height: 1.72;
  color: var(--kb-foreground);
  overflow-wrap: anywhere;
}
.chat-markdown-html { display: block; }
.chat-markdown :deep(p) { margin: 0 0 0.85em; }
.chat-markdown :deep(p:last-child) { margin-bottom: 0; }
.chat-markdown :deep(:is(h1, h2, h3, h4)) {
  margin: 1.35em 0 0.6em;
  line-height: 1.35;
  font-weight: 650;
  letter-spacing: -0.01em;
}
.chat-markdown :deep(h1) { font-size: 1.45em; }
.chat-markdown :deep(h2) { font-size: 1.25em; }
.chat-markdown :deep(h3) { font-size: 1.1em; }
.chat-markdown :deep(h4) { font-size: 1em; }
.chat-markdown :deep(:is(ul, ol)) {
  margin: 0.5em 0 0.9em;
  padding-inline-start: 1.4em;
}
.chat-markdown :deep(li) { margin: 0.28em 0; }
.chat-markdown :deep(li > p) { margin: 0; }
.chat-markdown :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.2em 0 0.2em 0.9em;
  border-inline-start: 3px solid var(--kb-border);
  color: var(--kb-muted-foreground);
}
.chat-markdown :deep(a) {
  color: var(--kb-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-word;
}
.chat-markdown :deep(hr) {
  margin: 1.2em 0;
  border: 0;
  border-top: 1px solid var(--kb-border);
}
.chat-markdown :deep(code:not(pre code)) {
  padding: 0.12em 0.36em;
  border-radius: var(--kb-radius-xs);
  background: var(--kb-muted, rgba(15, 23, 42, 0.06));
  font-family: var(--kb-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 0.88em;
}
.chat-markdown :deep(pre) { margin: 0; }
.chat-markdown :deep(table) {
  display: block;
  width: 100%;
  margin: 0.9em 0;
  overflow-x: auto;
  border-collapse: collapse;
  font-size: 0.94em;
}
.chat-markdown :deep(th),
.chat-markdown :deep(td) {
  padding: 7px 11px;
  border: 1px solid var(--kb-border);
  text-align: left;
  vertical-align: top;
}
.chat-markdown :deep(th) {
  background: var(--kb-muted, #f6f7f9);
  font-weight: 600;
}
.chat-markdown :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--kb-radius-sm);
  cursor: zoom-in;
  margin-block: 0.4em;
}
.chat-markdown :deep(input[type='checkbox']) { margin-inline-end: 6px; }
.chat-markdown :deep(.contains-task-list) { list-style: none; padding-inline-start: 0.4em; }
.chat-markdown :deep(.template-variable) {
  padding: 0 3px;
  border-radius: 4px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.12));
  color: var(--kb-primary);
}
.chat-markdown-cursor {
  display: inline-block;
  width: 7px;
  height: 1.05em;
  margin-inline-start: 2px;
  vertical-align: text-bottom;
  background: var(--kb-primary);
  animation: chat-cursor-blink 1s steps(2, start) infinite;
}
@keyframes chat-cursor-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
</style>
