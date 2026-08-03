// 全局背景设置 Store：管理背景类型、图片/视频数据、颜色、渐变、预设、透明度等，持久化到 localStorage
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { listPresets, savePreset, deletePreset, type UserBackgroundPresetVO, type PresetSaveDTO } from '@/api/backgroundPreset'

export type BackgroundType = 'none' | 'image' | 'video' | 'color' | 'gradient' | 'preset'
export type SizeMode = 'cover' | 'contain' | 'repeat' | 'center'

export interface GradientConfig {
  from: string
  to: string
  angle: number
}

export interface PresetTheme {
  id: string
  name: string
  type: 'gradient' | 'color'
  value: string
  thumbnail: string
}

export interface BackgroundState {
  type: BackgroundType
  imageData: string
  videoData: string
  color: string
  gradient: GradientConfig
  presetId: string
  opacity: number
  blur: number
  sizeMode: SizeMode
}

/**
 * 页面级背景覆盖：路由 meta.backgroundOverride 使用此类型。
 * - 'none' 表示该页面强制关闭背景
 * - 对象形式可部分覆盖全局设置的任意字段
 */
export type BackgroundOverride = 'none' | {
  type?: BackgroundType
  color?: string
  gradient?: Partial<GradientConfig>
  presetId?: string
  opacity?: number
  blur?: number
  sizeMode?: SizeMode
}

const STORAGE_KEY = 'knowflow_background'

const PRESET_THEMES: PresetTheme[] = [
  { id: 'ocean', name: '深海蓝', type: 'gradient', value: 'linear-gradient(135deg, #0c4a6e 0%, #0369a1 50%, #0284c7 100%)', thumbnail: 'linear-gradient(135deg, #0c4a6e, #0284c7)' },
  { id: 'sunset', name: '日落橙', type: 'gradient', value: 'linear-gradient(135deg, #7c2d12 0%, #ea580c 50%, #fbbf24 100%)', thumbnail: 'linear-gradient(135deg, #7c2d12, #fbbf24)' },
  { id: 'forest', name: '森林绿', type: 'gradient', value: 'linear-gradient(135deg, #14532d 0%, #16a34a 50%, #4ade80 100%)', thumbnail: 'linear-gradient(135deg, #14532d, #4ade80)' },
  { id: 'galaxy', name: '星河紫', type: 'gradient', value: 'linear-gradient(135deg, #1e1b4b 0%, #4c1d95 50%, #7c3aed 100%)', thumbnail: 'linear-gradient(135deg, #1e1b4b, #7c3aed)' },
  { id: 'aurora', name: '极光', type: 'gradient', value: 'linear-gradient(135deg, #042f2e 0%, #0d9488 33%, #6366f1 66%, #c026d3 100%)', thumbnail: 'linear-gradient(135deg, #042f2e, #0d9488, #6366f1, #c026d3)' },
  { id: 'midnight', name: '午夜', type: 'gradient', value: 'linear-gradient(180deg, #0f172a 0%, #1e293b 100%)', thumbnail: 'linear-gradient(180deg, #0f172a, #1e293b)' },
  { id: 'rose', name: '玫瑰', type: 'gradient', value: 'linear-gradient(135deg, #831843 0%, #e11d48 50%, #fb7185 100%)', thumbnail: 'linear-gradient(135deg, #831843, #fb7185)' },
  { id: 'ink', name: '水墨', type: 'color', value: '#1a1a2e', thumbnail: '#1a1a2e' },
]

function loadState(): BackgroundState {
  const defaults: BackgroundState = {
    type: 'none',
    imageData: '',
    videoData: '',
    color: '#1a1a2e',
    gradient: { from: '#3B6FE0', to: '#8B5CF6', angle: 135 },
    presetId: '',
    opacity: 1,
    blur: 0,
    sizeMode: 'cover',
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return defaults
    const saved = JSON.parse(raw) as Partial<BackgroundState>
    return { ...defaults, ...saved, gradient: { ...defaults.gradient, ...saved.gradient } }
  } catch {
    return defaults
  }
}

export const useBackgroundStore = defineStore('background', () => {
  const initialState = loadState()

  const type = ref<BackgroundType>(initialState.type)
  const imageData = ref<string>(initialState.imageData)
  const videoData = ref<string>(initialState.videoData)
  const color = ref<string>(initialState.color)
  const gradient = ref<GradientConfig>(initialState.gradient)
  const presetId = ref<string>(initialState.presetId)
  const opacity = ref<number>(initialState.opacity)
  const blur = ref<number>(initialState.blur)
  const sizeMode = ref<SizeMode>(initialState.sizeMode)

  const presets = PRESET_THEMES

  // 用户自定义预设（从后端加载）
  const userPresets = ref<UserBackgroundPresetVO[]>([])
  const userPresetsLoading = ref(false)

  // 合并内置预设和用户自定义预设
  const allPresets = computed<PresetTheme[]>(() => {
    const custom: PresetTheme[] = userPresets.value.map((p) => ({
      id: `custom-${p.id}`,
      name: p.name,
      type: p.bgType as 'gradient' | 'color',
      value: p.bgValue,
      thumbnail: p.thumbnail,
    }))
    return [...PRESET_THEMES, ...custom]
  })

  const currentPreset = computed(() => allPresets.value.find((p) => p.id === presetId.value) || null)

  const isActive = computed(() => type.value !== 'none')

  // 计算背景 CSS 值
  const backgroundStyle = computed(() => {
    switch (type.value) {
      case 'color':
        return { background: color.value }
      case 'gradient':
        return { background: `linear-gradient(${gradient.value.angle}deg, ${gradient.value.from}, ${gradient.value.to})` }
      case 'preset': {
        const preset = currentPreset.value
        return preset ? { background: preset.value } : {}
      }
      default:
        return {}
    }
  })

  function setType(t: BackgroundType) {
    type.value = t
  }

  function setImage(dataUrl: string) {
    imageData.value = dataUrl
    type.value = 'image'
  }

  function setVideo(dataUrl: string) {
    videoData.value = dataUrl
    type.value = 'video'
  }

  function setColor(c: string) {
    color.value = c
    type.value = 'color'
  }

  function setGradient(from: string, to: string, angle: number) {
    gradient.value = { from, to, angle }
    type.value = 'gradient'
  }

  function setPreset(id: string) {
    presetId.value = id
    type.value = 'preset'
  }

  function setOpacity(v: number) {
    opacity.value = Math.max(0, Math.min(1, v))
  }

  function setBlur(v: number) {
    blur.value = Math.max(0, Math.min(30, v))
  }

  function setSizeMode(m: SizeMode) {
    sizeMode.value = m
  }

  function clearBackground() {
    type.value = 'none'
    imageData.value = ''
    videoData.value = ''
    presetId.value = ''
  }

  function clearMedia() {
    imageData.value = ''
    videoData.value = ''
  }

  // ===== 用户自定义预设管理 =====

  /** 从后端加载用户自定义预设 */
  async function loadUserPresets() {
    userPresetsLoading.value = true
    try {
      userPresets.value = await listPresets()
    } catch {
      // 静默失败，不影响基本使用
    } finally {
      userPresetsLoading.value = false
    }
  }

  /** 保存当前配置为自定义预设 */
  async function saveUserPreset(dto: PresetSaveDTO) {
    const saved = await savePreset(dto)
    // 更新本地列表（同名替换）
    const idx = userPresets.value.findIndex((p) => p.name === dto.name)
    if (idx >= 0) {
      userPresets.value[idx] = saved
    } else {
      userPresets.value.unshift(saved)
    }
    return saved
  }

  /** 删除自定义预设 */
  async function removeUserPreset(id: number) {
    await deletePreset(id)
    userPresets.value = userPresets.value.filter((p) => p.id !== id)
  }

  /** 应用自定义预设 */
  function applyUserPreset(preset: UserBackgroundPresetVO) {
    presetId.value = `custom-${preset.id}`
    type.value = 'preset'
  }

  // 持久化（排除大体积的 media 数据时单独处理）
  function persist() {
    const state: BackgroundState = {
      type: type.value,
      imageData: imageData.value,
      videoData: videoData.value,
      color: color.value,
      gradient: gradient.value,
      presetId: presetId.value,
      opacity: opacity.value,
      blur: blur.value,
      sizeMode: sizeMode.value,
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // localStorage 容量不足时，尝试仅保存非媒体字段
      const lightState = { ...state, imageData: '', videoData: '' }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(lightState))
    }
  }

  watch(
    [type, imageData, videoData, color, gradient, presetId, opacity, blur, sizeMode],
    persist,
    { deep: true },
  )

  return {
    type,
    imageData,
    videoData,
    color,
    gradient,
    presetId,
    opacity,
    blur,
    sizeMode,
    presets,
    allPresets,
    userPresets,
    userPresetsLoading,
    currentPreset,
    isActive,
    backgroundStyle,
    setType,
    setImage,
    setVideo,
    setColor,
    setGradient,
    setPreset,
    setOpacity,
    setBlur,
    setSizeMode,
    clearBackground,
    clearMedia,
    loadUserPresets,
    saveUserPreset,
    removeUserPreset,
    applyUserPreset,
  }
})
