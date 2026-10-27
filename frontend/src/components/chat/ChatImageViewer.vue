<script setup lang="ts">
/**
 * 聊天图片查看器：多图切换、缩放、下载、加载失败占位。
 * 只负责展示，不改变消息数据；图片地址由调用方提供。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { normalizeUploadUrl } from '@/utils/markdown'

const props = defineProps<{
  open: boolean
  images: string[]
  index?: number
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const current = ref(0)
const scale = ref(1)
const failed = ref(false)

const list = computed(() => (props.images || []).filter(Boolean))

const currentSrc = computed(() => normalizeUploadUrl(list.value[current.value] || ''))

watch(
  () => props.open,
  (open) => {
    if (open) {
      current.value = Math.min(Math.max(props.index ?? 0, 0), Math.max(list.value.length - 1, 0))
      scale.value = 1
      failed.value = false
      window.addEventListener('keydown', onKeydown)
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function onKeydown(event: KeyboardEvent): void {
  if (!props.open) return
  if (event.key === 'Escape') emit('close')
  else if (event.key === 'ArrowLeft') step(-1)
  else if (event.key === 'ArrowRight') step(1)
}

function step(delta: number): void {
  if (list.value.length <= 1) return
  current.value = (current.value + delta + list.value.length) % list.value.length
  scale.value = 1
  failed.value = false
}

function zoom(delta: number): void {
  scale.value = Math.min(3, Math.max(0.4, Number((scale.value + delta).toFixed(2))))
}

function resetZoom(): void {
  scale.value = 1
}
</script>

<template>
  <div v-if="open && list.length" class="chat-image-viewer" role="dialog" aria-modal="true" aria-label="图片预览" @click.self="emit('close')">
    <div class="chat-image-viewer-bar">
      <span class="chat-image-viewer-count">{{ current + 1 }} / {{ list.length }}</span>
      <div class="chat-image-viewer-tools">
        <button type="button" class="chat-image-tool" aria-label="缩小" title="缩小" @click="zoom(-0.2)">
          <Icon name="minus" :size="15" />
        </button>
        <button type="button" class="chat-image-tool" aria-label="放大" title="放大" @click="zoom(0.2)">
          <Icon name="plus" :size="15" />
        </button>
        <button type="button" class="chat-image-tool" aria-label="还原缩放" title="还原" @click="resetZoom">
          {{ Math.round(scale * 100) }}%
        </button>
        <a class="chat-image-tool" :href="currentSrc" download target="_blank" rel="noopener noreferrer" aria-label="下载图片" title="下载图片">
          <Icon name="download" :size="15" />
        </a>
        <button type="button" class="chat-image-tool" aria-label="关闭图片预览" title="关闭" @click="emit('close')">
          <Icon name="x" :size="15" />
        </button>
      </div>
    </div>

    <button v-if="list.length > 1" type="button" class="chat-image-nav is-prev" aria-label="上一张" @click.stop="step(-1)">
      <Icon name="chevron-left" :size="20" />
    </button>
    <div class="chat-image-viewer-stage" @click.self="emit('close')">
      <img
        v-if="!failed"
        :key="currentSrc"
        :src="currentSrc"
        :style="{ transform: `scale(${scale})` }"
        class="chat-image-viewer-img"
        alt="聊天图片"
        @error="failed = true"
        @dblclick="scale === 1 ? zoom(0.5) : resetZoom()"
      />
      <div v-else class="chat-image-viewer-failed" role="alert">
        图片加载失败，链接可能已失效或无权访问。
      </div>
    </div>
    <button v-if="list.length > 1" type="button" class="chat-image-nav is-next" aria-label="下一张" @click.stop="step(1)">
      <Icon name="chevron-right" :size="20" />
    </button>
  </div>
</template>

<style scoped>
.chat-image-viewer {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(12, 16, 24, 0.82);
  padding: 3rem 1rem 1.5rem;
}
.chat-image-viewer-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  color: #fff;
}
.chat-image-viewer-count {
  font-size: 0.8125rem;
  opacity: 0.8;
}
.chat-image-viewer-tools {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}
.chat-image-tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2rem;
  height: 2rem;
  padding: 0 0.5rem;
  border-radius: 0.5rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  font-size: 0.75rem;
}
.chat-image-tool:hover { background: rgba(255, 255, 255, 0.22); }
.chat-image-tool:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
.chat-image-viewer-stage {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  overflow: auto;
}
.chat-image-viewer-img {
  max-width: min(90vw, 68rem);
  max-height: calc(100vh - 7rem);
  object-fit: contain;
  border-radius: 0.5rem;
  transition: transform 0.15s ease;
  transform-origin: center center;
  background: #fff;
}
.chat-image-viewer-failed {
  color: #fff;
  font-size: 0.875rem;
  padding: 1.5rem;
  border-radius: 0.75rem;
  background: rgba(255, 255, 255, 0.08);
}
.chat-image-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 999px;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
}
.chat-image-nav:hover { background: rgba(255, 255, 255, 0.26); }
.chat-image-nav.is-prev { left: 1rem; }
.chat-image-nav.is-next { right: 1rem; }
</style>
