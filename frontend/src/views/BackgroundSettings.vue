<template>
  <div class="bg-settings animate-fade-in">
    <PageHeader
      title="背景设置"
      :crumbs="[{ label: '首页', to: '/' }, { label: '背景设置' }]"
    >
      <template #actions>
        <button v-if="bg.isActive" type="button" class="clear-btn" @click="handleClear">
          <Icon name="x-circle" :size="15" />
          <span>清除背景</span>
        </button>
      </template>
    </PageHeader>

    <div class="settings-grid">
      <!-- 左侧：类型选择 + 配置 -->
      <div class="settings-main">
        <!-- 类型选择 -->
        <section class="settings-card">
          <h3 class="card-title">背景类型</h3>
          <div class="type-tabs">
            <button
              v-for="t in typeOptions"
              :key="t.value"
              type="button"
              class="type-tab"
              :class="{ 'type-tab-active': bg.type === t.value }"
              @click="bg.setType(t.value)"
            >
              <Icon :name="t.icon" :size="18" />
              <span>{{ t.label }}</span>
            </button>
          </div>
        </section>

        <!-- 图片上传 -->
        <section v-if="bg.type === 'image'" class="settings-card">
          <h3 class="card-title">图片背景</h3>
          <div
            class="upload-zone"
            :class="{ 'upload-zone-drag': isDragging }"
            @click="triggerImageUpload"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleImageDrop"
          >
            <template v-if="bg.imageData">
              <img :src="bg.imageData" class="upload-preview" alt="背景预览" />
              <div class="upload-overlay">
                <Icon name="refresh-cw" :size="20" />
                <span>点击更换图片</span>
              </div>
            </template>
            <template v-else>
              <Icon name="image" :size="40" class="upload-icon" />
              <p class="upload-text">点击或拖拽上传图片</p>
              <p class="upload-hint">支持 JPG / PNG / WebP，建议不超过 5MB</p>
            </template>
          </div>
          <input ref="imageInput" type="file" accept="image/*" hidden @change="handleImageSelect" />

          <!-- 显示方式 -->
          <div v-if="bg.imageData" class="control-row">
            <label class="control-label">显示方式</label>
            <div class="size-mode-group">
              <button
                v-for="m in sizeModes"
                :key="m.value"
                type="button"
                class="size-mode-btn"
                :class="{ 'size-mode-active': bg.sizeMode === m.value }"
                @click="bg.setSizeMode(m.value)"
              >{{ m.label }}</button>
            </div>
          </div>
        </section>

        <!-- 视频上传 -->
        <section v-if="bg.type === 'video'" class="settings-card">
          <h3 class="card-title">视频背景</h3>
          <div
            class="upload-zone"
            :class="{ 'upload-zone-drag': isDragging }"
            @click="triggerVideoUpload"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleVideoDrop"
          >
            <template v-if="bg.videoData">
              <video :src="bg.videoData" class="upload-preview" autoplay loop muted playsinline></video>
              <div class="upload-overlay">
                <Icon name="refresh-cw" :size="20" />
                <span>点击更换视频</span>
              </div>
            </template>
            <template v-else>
              <Icon name="video" :size="40" class="upload-icon" />
              <p class="upload-text">点击或拖拽上传视频</p>
              <p class="upload-hint">支持 MP4 / WebM，建议不超过 10MB</p>
            </template>
          </div>
          <input ref="videoInput" type="file" accept="video/*" hidden @change="handleVideoSelect" />

          <div v-if="bg.videoData" class="control-row">
            <label class="control-label">显示方式</label>
            <div class="size-mode-group">
              <button
                v-for="m in sizeModes"
                :key="m.value"
                type="button"
                class="size-mode-btn"
                :class="{ 'size-mode-active': bg.sizeMode === m.value }"
                @click="bg.setSizeMode(m.value)"
              >{{ m.label }}</button>
            </div>
          </div>
        </section>

        <!-- 纯色背景 -->
        <section v-if="bg.type === 'color'" class="settings-card">
          <h3 class="card-title">纯色背景</h3>
          <div class="control-row">
            <label class="control-label">选择颜色</label>
            <div class="color-row">
              <input type="color" :value="bg.color" class="color-picker" @input="bg.setColor(($event.target as HTMLInputElement).value)" />
              <input type="text" :value="bg.color" class="color-text" readonly />
            </div>
          </div>
          <div class="color-presets">
            <button
              v-for="c in colorPresets"
              :key="c"
              type="button"
              class="color-swatch"
              :style="{ background: c }"
              :class="{ 'color-swatch-active': bg.color === c }"
              @click="bg.setColor(c)"
            ></button>
          </div>
        </section>

        <!-- 渐变背景 -->
        <section v-if="bg.type === 'gradient'" class="settings-card">
          <h3 class="card-title">渐变背景</h3>
          <div class="control-row">
            <label class="control-label">起始颜色</label>
            <div class="color-row">
              <input type="color" :value="bg.gradient.from" class="color-picker" @input="updateGradient('from', ($event.target as HTMLInputElement).value)" />
              <input type="text" :value="bg.gradient.from" class="color-text" readonly />
            </div>
          </div>
          <div class="control-row">
            <label class="control-label">结束颜色</label>
            <div class="color-row">
              <input type="color" :value="bg.gradient.to" class="color-picker" @input="updateGradient('to', ($event.target as HTMLInputElement).value)" />
              <input type="text" :value="bg.gradient.to" class="color-text" readonly />
            </div>
          </div>
          <div class="control-row">
            <label class="control-label">角度：{{ bg.gradient.angle }}°</label>
            <input type="range" min="0" max="360" :value="bg.gradient.angle" class="slider" @input="updateGradient('angle', Number(($event.target as HTMLInputElement).value))" />
          </div>
          <div class="gradient-preview" :style="{ background: `linear-gradient(${bg.gradient.angle}deg, ${bg.gradient.from}, ${bg.gradient.to})` }"></div>
        </section>

        <!-- 预设主题 -->
        <section v-if="bg.type === 'preset'" class="settings-card">
          <h3 class="card-title">预设主题</h3>
          <div class="preset-grid">
            <button
              v-for="preset in bg.presets"
              :key="preset.id"
              type="button"
              class="preset-card"
              :class="{ 'preset-card-active': bg.presetId === preset.id }"
              @click="bg.setPreset(preset.id)"
            >
              <div class="preset-thumb" :style="{ background: preset.thumbnail }"></div>
              <span class="preset-name">{{ preset.name }}</span>
            </button>
          </div>
        </section>

        <!-- 通用调节 -->
        <section v-if="bg.isActive" class="settings-card">
          <h3 class="card-title">显示调节</h3>
          <div class="control-row">
            <label class="control-label">透明度：{{ Math.round(bg.opacity * 100) }}%</label>
            <input type="range" min="0" max="100" :value="bg.opacity * 100" class="slider" @input="bg.setOpacity(Number(($event.target as HTMLInputElement).value) / 100)" />
          </div>
          <div class="control-row">
            <label class="control-label">背景模糊：{{ bg.blur }}px</label>
            <input type="range" min="0" max="30" :value="bg.blur" class="slider" @input="bg.setBlur(Number(($event.target as HTMLInputElement).value))" />
          </div>
        </section>
      </div>

      <!-- 右侧：实时预览 -->
      <div class="settings-preview">
        <section class="settings-card preview-card">
          <h3 class="card-title">实时预览</h3>
          <div class="preview-window">
            <!-- 模拟页面内容 -->
            <div class="preview-mock">
              <div class="mock-navbar">
                <div class="mock-nav-item"></div>
                <div class="mock-nav-item"></div>
                <div class="mock-nav-item"></div>
                <div class="mock-nav-circle"></div>
              </div>
              <div class="mock-content">
                <div class="mock-card">
                  <div class="mock-line mock-line-title"></div>
                  <div class="mock-line"></div>
                  <div class="mock-line mock-line-short"></div>
                </div>
                <div class="mock-card">
                  <div class="mock-line mock-line-title"></div>
                  <div class="mock-line"></div>
                  <div class="mock-line mock-line-short"></div>
                </div>
              </div>
            </div>
            <!-- 背景层 -->
            <div class="preview-bg" :style="previewBgStyle">
              <div v-if="bg.blur > 0" class="preview-bg-blur" :style="{ backdropFilter: `blur(${bg.blur}px)` }"></div>
              <div class="preview-bg-overlay" :style="{ opacity: 1 - bg.opacity }"></div>
            </div>
          </div>

          <!-- 当前状态 -->
          <div class="preview-status">
            <div class="status-row">
              <span class="status-label">当前类型</span>
              <span class="status-value">{{ typeLabel }}</span>
            </div>
            <div v-if="bg.isActive" class="status-row">
              <span class="status-label">透明度</span>
              <span class="status-value tabular-nums">{{ Math.round(bg.opacity * 100) }}%</span>
            </div>
            <div v-if="bg.blur > 0" class="status-row">
              <span class="status-label">模糊</span>
              <span class="status-value tabular-nums">{{ bg.blur }}px</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Icon from '@/components/ui/Icon.vue'
import { useBackgroundStore } from '@/stores/background'
import { notify, confirmDialog } from '@/utils/toast'

const bg = useBackgroundStore()

const imageInput = ref<HTMLInputElement | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)

const typeOptions = [
  { value: 'none' as const, label: '无背景', icon: 'ban' },
  { value: 'image' as const, label: '图片', icon: 'image' },
  { value: 'video' as const, label: '视频', icon: 'video' },
  { value: 'color' as const, label: '纯色', icon: 'square' },
  { value: 'gradient' as const, label: '渐变', icon: 'palette' },
  { value: 'preset' as const, label: '预设', icon: 'layout-grid' },
]

const sizeModes = [
  { value: 'cover' as const, label: '铺满' },
  { value: 'contain' as const, label: '适应' },
  { value: 'center' as const, label: '居中' },
  { value: 'repeat' as const, label: '平铺' },
]

const colorPresets = [
  '#1a1a2e', '#0f172a', '#1e293b', '#312e81',
  '#0c4a6e', '#134e4a', '#14532d', '#7c2d12',
  '#831843', '#581c87', '#1e1b4b', '#0c0a09',
]

const typeLabel = computed(() => {
  const opt = typeOptions.find((t) => t.value === bg.type)
  return opt?.label || '无'
})

// 预览背景样式
const previewBgStyle = computed(() => {
  const base: Record<string, string> = { opacity: String(bg.opacity) }
  if (bg.type === 'image' && bg.imageData) {
    base.backgroundImage = `url(${bg.imageData})`
    base.backgroundSize = bg.sizeMode === 'cover' ? 'cover' : bg.sizeMode === 'contain' ? 'contain' : 'auto'
    base.backgroundPosition = 'center'
    base.backgroundRepeat = bg.sizeMode === 'repeat' ? 'repeat' : 'no-repeat'
  } else if (bg.type === 'video' && bg.videoData) {
    // 视频在预览中用首帧截图简化处理，此处用黑色占位
    base.background = '#000'
  } else if (bg.type === 'color') {
    base.background = bg.color
  } else if (bg.type === 'gradient') {
    base.background = `linear-gradient(${bg.gradient.angle}deg, ${bg.gradient.from}, ${bg.gradient.to})`
  } else if (bg.type === 'preset' && bg.currentPreset) {
    base.background = bg.currentPreset.value
  }
  return base
})

function triggerImageUpload() {
  imageInput.value?.click()
}

function triggerVideoUpload() {
  videoInput.value?.click()
}

function handleImageSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) processImageFile(file)
}

function handleImageDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) processImageFile(file)
}

function processImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    notify('请选择图片文件', 'warning')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    notify('图片大小超过 5MB，可能影响存储，建议压缩后使用', 'warning')
  }
  const reader = new FileReader()
  reader.onload = () => {
    bg.setImage(reader.result as string)
    notify('图片背景已设置', 'success')
  }
  reader.onerror = () => notify('图片读取失败', 'error')
  reader.readAsDataURL(file)
}

function handleVideoSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) processVideoFile(file)
}

function handleVideoDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('video/')) processVideoFile(file)
}

function processVideoFile(file: File) {
  if (!file.type.startsWith('video/')) {
    notify('请选择视频文件', 'warning')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    notify('视频较大，可能无法持久化保存，刷新后需重新设置', 'warning')
  }
  const reader = new FileReader()
  reader.onload = () => {
    bg.setVideo(reader.result as string)
    notify('视频背景已设置', 'success')
  }
  reader.onerror = () => notify('视频读取失败', 'error')
  reader.readAsDataURL(file)
}

function updateGradient(field: 'from' | 'to' | 'angle', value: string | number) {
  if (field === 'angle') {
    bg.setGradient(bg.gradient.from, bg.gradient.to, value as number)
  } else {
    bg.setGradient(
      field === 'from' ? (value as string) : bg.gradient.from,
      field === 'to' ? (value as string) : bg.gradient.to,
      bg.gradient.angle,
    )
  }
}

async function handleClear() {
  const ok = await confirmDialog('确定清除当前背景设置吗？')
  if (!ok) return
  bg.clearBackground()
  notify('背景已清除', 'success')
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.bg-settings {
  max-width: 1100px;
  margin: 0 auto;
}

.settings-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 20px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}

.settings-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  padding: 20px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 16px;
}

/* 类型 Tab */
.type-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
@media (max-width: 600px) {
  .type-tabs {
    grid-template-columns: repeat(2, 1fr);
  }
}

.type-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 13px;
  font-weight: 500;
}
.type-tab:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.type-tab-active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-primary);
}

/* 上传区 */
.upload-zone {
  position: relative;
  border: 2px dashed var(--kb-border);
  border-radius: 12px;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  overflow: hidden;
  background: var(--kb-background);
}
.upload-zone:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.02);
}
.upload-zone-drag {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.05);
}
.upload-icon {
  color: var(--kb-muted-foreground);
  opacity: 0.5;
}
.upload-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.upload-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.upload-preview {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}
.upload-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.upload-zone:hover .upload-overlay {
  opacity: 1;
}

/* 控制行 */
.control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.control-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  white-space: nowrap;
}

/* 颜色选择 */
.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.color-picker {
  width: 36px;
  height: 36px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  cursor: pointer;
  background: transparent;
  padding: 2px;
}
.color-text {
  width: 90px;
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
}

.color-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.color-swatch {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s ease;
}
.color-swatch:hover {
  transform: scale(1.1);
}
.color-swatch-active {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.2);
}

/* 滑块 */
.slider {
  flex: 1;
  min-width: 150px;
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  outline: none;
  -webkit-appearance: none;
  appearance: none;
  cursor: pointer;
}
.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}
.slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}

/* 渐变预览 */
.gradient-preview {
  width: 100%;
  height: 60px;
  border-radius: 10px;
  margin-top: 14px;
  border: 1px solid var(--kb-border);
}

/* 显示方式按钮组 */
.size-mode-group {
  display: flex;
  gap: 6px;
}
.size-mode-btn {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.size-mode-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.size-mode-active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

/* 预设主题 */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
@media (max-width: 600px) {
  .preset-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.preset-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 12px;
  border: 2px solid var(--kb-border);
  background: var(--kb-background);
  cursor: pointer;
  transition: all 0.15s ease;
}
.preset-card:hover {
  border-color: var(--kb-primary);
  transform: translateY(-2px);
}
.preset-card-active {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.15);
}
.preset-thumb {
  width: 100%;
  height: 48px;
  border-radius: 8px;
}
.preset-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-foreground);
}

/* 清除按钮 */
.clear-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  padding: 7px 14px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-destructive);
  background: rgba(239, 68, 68, 0.06);
  color: var(--kb-destructive);
  cursor: pointer;
  transition: all 0.15s ease;
}
.clear-btn:hover {
  background: rgba(239, 68, 68, 0.12);
}

/* 预览面板 */
.preview-card {
  position: sticky;
  top: 70px;
}
.preview-window {
  position: relative;
  width: 100%;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.preview-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  transition: opacity 0.2s ease;
  background-size: cover;
  background-position: center;
}
.preview-bg-blur {
  position: absolute;
  inset: 0;
}
.preview-bg-overlay {
  position: absolute;
  inset: 0;
  background: #fff;
}
.preview-mock {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.mock-navbar {
  height: 32px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.mock-nav-item {
  width: 20px;
  height: 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.15);
}
.mock-nav-circle {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.15);
  margin-left: auto;
}
.mock-content {
  flex: 1;
  padding: 10px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.mock-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(6px);
  border-radius: 8px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.mock-line {
  height: 5px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.1);
}
.mock-line-title {
  width: 60%;
  height: 7px;
  background: rgba(0, 0, 0, 0.2);
}
.mock-line-short {
  width: 40%;
}

/* 预览状态 */
.preview-status {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--kb-border);
}
.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}
.status-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.status-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}
</style>
