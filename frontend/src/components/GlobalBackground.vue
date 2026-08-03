<template>
  <!-- 全局背景层：fixed 定位铺满视口，z-index: -1 确保不遮挡内容 -->
  <div v-if="effective.isActive" class="global-bg-layer" :style="{ opacity: effective.opacity }">
    <!-- 视频背景 -->
    <video
      v-if="effective.type === 'video' && bg.videoData"
      class="global-bg-video"
      :src="bg.videoData"
      autoplay
      loop
      muted
      playsinline
      :style="mediaStyle"
    ></video>

    <!-- 图片背景 -->
    <div
      v-else-if="effective.type === 'image' && bg.imageData"
      class="global-bg-image"
      :style="imageStyle"
    ></div>

    <!-- 纯色/渐变/预设背景 -->
    <div
      v-else
      class="global-bg-solid"
      :style="solidStyle"
    ></div>

    <!-- 模糊遮罩层 -->
    <div v-if="effective.blur > 0" class="global-bg-blur" :style="{ backdropFilter: `blur(${effective.blur}px)` }"></div>

    <!-- 半透明遮罩层（提升内容可读性） -->
    <div class="global-bg-overlay" :style="{ opacity: 1 - effective.opacity }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useBackgroundStore } from '@/stores/background'

const bg = useBackgroundStore()
const route = useRoute()

// 背景激活时在 <html> 上标记 class，供全局 CSS 覆盖布局容器的不透明背景
watchEffect(() => {
  document.documentElement.classList.toggle('has-global-bg', bg.isActive)
})

// 合并全局设置与路由 meta.backgroundOverride，计算生效状态
const effective = computed(() => {
  const override = route.meta.backgroundOverride

  // 'none' 强制关闭背景
  if (override === 'none') {
    return { isActive: false, type: 'none', opacity: 0, blur: 0, sizeMode: 'cover' as const }
  }

  // 无覆盖时使用全局设置
  if (!override) {
    return {
      isActive: bg.isActive,
      type: bg.type,
      opacity: bg.opacity,
      blur: bg.blur,
      sizeMode: bg.sizeMode,
    }
  }

  // 对象形式：部分覆盖全局设置
  return {
    isActive: override.type ? override.type !== 'none' : bg.isActive,
    type: override.type ?? bg.type,
    opacity: override.opacity ?? bg.opacity,
    blur: override.blur ?? bg.blur,
    sizeMode: override.sizeMode ?? bg.sizeMode,
  }
})

// 纯色/渐变/预设的 CSS 值（合并覆盖后的字段）
const solidStyle = computed(() => {
  const override = route.meta.backgroundOverride
  const oType = override && override !== 'none' ? override.type : undefined
  const type = oType ?? bg.type

  switch (type) {
    case 'color': {
      const color = override && override !== 'none' && override.color ? override.color : bg.color
      return { background: color }
    }
    case 'gradient': {
      const g = bg.gradient
      const from = override && override !== 'none' && override.gradient?.from ? override.gradient.from : g.from
      const to = override && override !== 'none' && override.gradient?.to ? override.gradient.to : g.to
      const angle = override && override !== 'none' && override.gradient?.angle != null ? override.gradient.angle : g.angle
      return { background: `linear-gradient(${angle}deg, ${from}, ${to})` }
    }
    case 'preset': {
      const presetId = override && override !== 'none' && override.presetId ? override.presetId : bg.presetId
      const preset = bg.presets.find((p) => p.id === presetId)
      return preset ? { background: preset.value } : {}
    }
    default:
      return {}
  }
})

const mediaStyle = computed(() => {
  const modes: Record<string, string> = {
    cover: 'object-fit: cover;',
    contain: 'object-fit: contain;',
    center: 'object-fit: none; object-position: center;',
    repeat: 'object-fit: none; object-position: center;',
  }
  return modes[effective.value.sizeMode] || modes.cover
})

const imageStyle = computed(() => {
  const base = { backgroundImage: `url(${bg.imageData})` }
  switch (effective.value.sizeMode) {
    case 'cover':
      return { ...base, backgroundSize: 'cover', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' }
    case 'contain':
      return { ...base, backgroundSize: 'contain', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' }
    case 'repeat':
      return { ...base, backgroundSize: 'auto', backgroundRepeat: 'repeat' }
    case 'center':
      return { ...base, backgroundSize: 'auto', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' }
    default:
      return base
  }
})
</script>

<style scoped>
.global-bg-layer {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.3s ease;
}

.global-bg-video {
  width: 100%;
  height: 100%;
  display: block;
}

.global-bg-image {
  width: 100%;
  height: 100%;
}

.global-bg-solid {
  width: 100%;
  height: 100%;
}

.global-bg-blur {
  position: absolute;
  inset: 0;
  background: transparent;
}

.global-bg-overlay {
  position: absolute;
  inset: 0;
  background: #ffffff;
  pointer-events: none;
}
</style>
