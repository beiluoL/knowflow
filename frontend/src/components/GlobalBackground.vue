<template>
  <!-- 全局背景层：fixed 定位铺满视口，z-index: -1 确保不遮挡内容 -->
  <div v-if="bg.isActive" class="global-bg-layer" :style="layerStyle">
    <!-- 视频背景 -->
    <video
      v-if="bg.type === 'video' && bg.videoData"
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
      v-else-if="bg.type === 'image' && bg.imageData"
      class="global-bg-image"
      :style="imageStyle"
    ></div>

    <!-- 纯色/渐变/预设背景 -->
    <div
      v-else
      class="global-bg-solid"
      :style="bg.backgroundStyle"
    ></div>

    <!-- 模糊遮罩层 -->
    <div v-if="bg.blur > 0" class="global-bg-blur" :style="{ backdropFilter: `blur(${bg.blur}px)` }"></div>

    <!-- 半透明遮罩层（提升内容可读性） -->
    <div class="global-bg-overlay" :style="{ opacity: 1 - bg.opacity }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useBackgroundStore } from '@/stores/background'

const bg = useBackgroundStore()

const layerStyle = computed(() => ({
  opacity: bg.opacity,
}))

const mediaStyle = computed(() => {
  const modes: Record<string, string> = {
    cover: 'object-fit: cover;',
    contain: 'object-fit: contain;',
    center: 'object-fit: none; object-position: center;',
    repeat: 'object-fit: none; object-position: center;',
  }
  return modes[bg.sizeMode] || modes.cover
})

const imageStyle = computed(() => {
  const base = { backgroundImage: `url(${bg.imageData})` }
  switch (bg.sizeMode) {
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
