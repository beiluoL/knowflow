<template>
  <div ref="rootRef" class="w-full">
    <label v-if="label" class="block text-sm font-medium text-foreground mb-1.5">
      {{ label }}
      <span v-if="required" class="text-danger-500 ml-0.5">*</span>
    </label>
    <div class="relative">
      <button
        type="button"
        :class="['kb-select-trigger', error ? 'kb-select-trigger-error' : '', disabled ? 'kb-select-trigger-disabled' : '']"
        :disabled="disabled"
        @click="toggle"
      >
        <Icon v-if="prefixIcon" :name="prefixIcon" :size="14" class="kb-select-prefix" />
        <span v-if="selectedLabel" class="kb-select-value">{{ selectedLabel }}</span>
        <span v-else class="kb-select-placeholder">{{ placeholder }}</span>
        <Icon name="chevron-down" :size="14" :class="['kb-select-arrow', open ? 'kb-select-arrow-up' : '']" />
      </button>

      <transition name="kb-select">
        <div v-if="open" class="kb-select-panel">
          <div v-if="searchable" class="kb-select-search">
            <Icon name="search" :size="14" class="kb-select-search-icon" />
            <input
              ref="searchInputRef"
              v-model="query"
              type="text"
              class="kb-select-search-input"
              placeholder="搜索…"
            />
          </div>
          <div class="kb-select-list">
            <button
              v-for="opt in filtered"
              :key="String(opt[valueKey])"
              type="button"
              :class="['kb-select-option', isSelected(opt) ? 'kb-select-option-active' : '']"
              @click="select(opt)"
            >
              <slot name="option" :option="opt">
                {{ opt[labelKey] }}
              </slot>
            </button>
            <div v-if="filtered.length === 0" class="kb-select-empty">无匹配项</div>
          </div>
        </div>
      </transition>
    </div>
    <p v-if="error" class="mt-1.5 text-xs text-danger-500">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
// U6：通用下拉选择，替代原生 select，支持搜索/自定义渲染/错误态，v-model 双绑。
// 复用 .kb-select 视觉令牌；面板用 absolute 浮层 + 点击外部关闭 + Transition 过渡。
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Option {
  [key: string]: any
}

interface Props {
  modelValue?: string | number | null
  options: Option[]
  labelKey?: string
  valueKey?: string
  placeholder?: string
  label?: string
  required?: boolean
  disabled?: boolean
  searchable?: boolean
  error?: string
  prefixIcon?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  labelKey: 'label',
  valueKey: 'value',
  placeholder: '请选择',
  required: false,
  disabled: false,
  searchable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
  change: [option: Option | null]
}>()

const rootRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const open = ref(false)
const query = ref('')

const selected = computed(() =>
  props.options.find((o) => o[props.valueKey] === props.modelValue) || null,
)

const selectedLabel = computed(() => {
  if (!selected.value) return ''
  return selected.value[props.labelKey]
})

const filtered = computed(() => {
  if (!props.searchable || !query.value.trim()) return props.options
  const q = query.value.toLowerCase()
  return props.options.filter((o) =>
    String(o[props.labelKey]).toLowerCase().includes(q),
  )
})

const isSelected = (opt: Option) => opt[props.valueKey] === props.modelValue

const select = (opt: Option) => {
  emit('update:modelValue', opt[props.valueKey])
  emit('change', opt)
  open.value = false
  query.value = ''
}

const toggle = () => {
  if (props.disabled) return
  open.value = !open.value
  if (open.value && props.searchable) {
    nextTick(() => searchInputRef.value?.focus())
  }
}

const onDocClick = (e: MouseEvent) => {
  if (rootRef.value && !rootRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

watch(open, (v) => {
  if (v) document.addEventListener('mousedown', onDocClick)
  else document.removeEventListener('mousedown', onDocClick)
})

onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))
</script>

<style scoped>
.kb-select {
  transition: transform 0.15s ease;
}
.kb-select-enter-from,
.kb-select-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
.kb-select-enter-active,
.kb-select-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
</style>
