<!-- HTML 安全预览：完全隔离 iframe（禁用脚本 / 同源 / 顶层导航 / 外部网络） -->
<template>
  <div class="html-preview" :class="{ 'is-fullscreen': fullscreen }">
    <div class="html-preview-bar">
      <span class="html-preview-title">
        <Icon name="code" :size="13" />
        {{ title || 'HTML 预览' }}
      </span>
      <div class="html-preview-actions">
        <div class="html-device-switch" role="group" aria-label="预览尺寸">
          <button type="button" :class="{ 'is-active': device === 'desktop' }" aria-label="桌面尺寸" @click="device = 'desktop'">
            <Icon name="monitor" :size="13" />
          </button>
          <button type="button" :class="{ 'is-active': device === 'mobile' }" aria-label="手机尺寸" @click="device = 'mobile'">
            <Icon name="smartphone" :size="13" />
          </button>
        </div>
        <button type="button" class="html-preview-btn" aria-label="刷新预览" @click="refresh">
          <Icon name="rotate-ccw" :size="13" />
        </button>
        <button type="button" class="html-preview-btn" :aria-label="fullscreen ? '退出全屏' : '全屏预览'" @click="fullscreen = !fullscreen">
          <Icon name="maximize" :size="13" />
        </button>
        <button type="button" class="html-preview-btn" :class="{ 'is-done': copied }" aria-label="复制源码" @click="copySource">
          <Icon :name="copied ? 'check' : 'copy'" :size="13" />
        </button>
        <button type="button" class="html-preview-btn" aria-label="下载 HTML 文件" @click="downloadHtml">
          <Icon name="download" :size="13" />
        </button>
        <button v-if="closable" type="button" class="html-preview-btn" aria-label="关闭预览" @click="$emit('close')">
          <Icon name="x" :size="13" />
        </button>
      </div>
    </div>

    <div class="html-preview-notice">
      已启用隔离沙箱：脚本、同源权限、顶层跳转与外部网络请求均被禁用。
    </div>

    <div class="html-preview-stage" :class="`is-${device}`">
      <iframe
        :key="refreshKey"
        class="html-preview-frame"
        :srcdoc="srcdoc"
        sandbox=""
        referrerpolicy="no-referrer"
        :title="title || 'HTML 预览'"
      ></iframe>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { copyTextToClipboard } from '@/utils/chat/clipboard'
import { downloadTextFile } from '@/utils/chat/fileTypes'

const props = withDefaults(defineProps<{
  code: string
  title?: string
  closable?: boolean
}>(), { title: '', closable: true })

defineEmits<{ (e: 'close'): void }>()

const device = ref<'desktop' | 'mobile'>('desktop')
const fullscreen = ref(false)
const refreshKey = ref(0)
const copied = ref(false)

const CSP = [
  "default-src 'none'",
  "style-src 'unsafe-inline'",
  "img-src data: blob:",
  "font-src data:",
  "media-src data: blob:",
].join('; ')

const META = `<meta http-equiv="Content-Security-Policy" content="${CSP}">`

/** 把所有外部资源与脚本挡在沙箱外；片段会被包装为完整文档 */
const srcdoc = computed(() => {
  const raw = props.code || ''
  if (/<html[\s>]/i.test(raw)) {
    if (/<head[\s>]/i.test(raw)) return raw.replace(/<head([^>]*)>/i, `<head$1>${META}`)
    return raw.replace(/<html([^>]*)>/i, `<html$1><head>${META}</head>`)
  }
  return `<!doctype html><html><head><meta charset="utf-8">${META}<style>body{margin:0;padding:16px;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#1f2328;line-height:1.6}</style></head><body>${raw}</body></html>`
})

function refresh() {
  refreshKey.value += 1
}

async function copySource() {
  const ok = await copyTextToClipboard(props.code)
  if (!ok) return
  copied.value = true
  setTimeout(() => { copied.value = false }, 1600)
}

function downloadHtml() {
  downloadTextFile('chat-preview.html', props.code, 'text/html')
}
</script>

<style scoped>
.html-preview {
  margin-block: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm, 8px);
  overflow: hidden;
  background: var(--kf-card);
}
.html-preview.is-fullscreen {
  position: fixed;
  inset: 16px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.32);
}
.html-preview-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px 6px 12px;
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-muted, #f6f7f9);
}
.html-preview-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kf-muted-foreground);
}
.html-preview-actions { display: flex; align-items: center; gap: 2px; }
.html-device-switch {
  display: inline-flex;
  padding: 2px;
  margin-inline-end: 4px;
  border: 1px solid var(--kf-border);
  border-radius: 999px;
  background: var(--kf-card);
}
.html-device-switch button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 20px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.html-device-switch button.is-active {
  background: var(--kf-primary-soft, rgba(59, 111, 224, 0.12));
  color: var(--kf-primary);
}
.html-preview-btn {
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
.html-preview-btn:hover { background: var(--kf-border); color: var(--kf-foreground); }
.html-preview-btn:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.html-preview-btn.is-done { color: var(--kf-state-success, #1a9f6a); }
.html-preview-notice {
  padding: 6px 12px;
  font-size: 11px;
  color: var(--kf-muted-foreground);
  background: var(--kf-muted, #f6f7f9);
  border-bottom: 1px solid var(--kf-border);
}
.html-preview-stage {
  display: flex;
  justify-content: center;
  background: var(--kf-muted, #f6f7f9);
}
.html-preview-stage.is-fullscreen,
.html-preview.is-fullscreen .html-preview-stage { flex: 1; min-height: 0; }
.html-preview-frame {
  width: 100%;
  height: 420px;
  border: 0;
  background: #fff;
}
.html-preview.is-fullscreen .html-preview-frame { height: 100%; }
.html-preview-stage.is-mobile .html-preview-frame {
  width: 390px;
  max-width: 100%;
  height: 640px;
  margin: 12px 0;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
</style>
