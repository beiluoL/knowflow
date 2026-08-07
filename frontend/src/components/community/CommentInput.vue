<template>
  <div class="w-full">
    <!-- 回复 / 编辑模式的上下文提示 -->
    <div v-if="contextLabel" class="flex items-center gap-1.5 mb-1.5 text-[12px] text-gray-500">
      <Icon :name="mode === 'edit' ? 'pencil' : 'message-circle'" :size="12" />
      <span>{{ contextLabel }}</span>
    </div>

    <div class="flex items-start gap-2">
      <textarea
        ref="textareaRef"
        v-model="text"
        :rows="rows"
        :maxlength="maxLength"
        :placeholder="placeholder"
        :disabled="submitting"
        class="flex-1 px-3 py-2 rounded-lg border text-sm resize-none transition-colors focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed"
        :class="overLimit ? 'border-danger-400 focus:border-danger-500' : 'border-gray-200 hover:border-gray-300 focus:border-primary-400'"
        @keydown.ctrl.enter.prevent="handleSubmit"
        @keydown.meta.enter.prevent="handleSubmit"
        @keydown.esc="handleCancel"
      ></textarea>

      <div class="shrink-0 self-end flex items-center gap-2">
        <button
          v-if="cancelable"
          type="button"
          :disabled="submitting"
          class="px-3 py-2 rounded-lg text-sm font-medium text-gray-600 border border-gray-200 transition-colors hover:bg-gray-50 active:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-200 disabled:opacity-50"
          @click="handleCancel"
        >取消</button>
        <button
          type="button"
          :disabled="!canSubmit"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 transition-colors hover:bg-primary-600 active:bg-primary-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-200 disabled:opacity-50 disabled:cursor-not-allowed"
          @click="handleSubmit"
        >{{ submitting ? '提交中…' : submitText }}</button>
      </div>
    </div>

    <div class="flex items-center justify-between mt-1.5">
      <span class="text-[12px] text-gray-400">Ctrl / ⌘ + Enter 快捷发送</span>
      <span class="text-[12px] tabular-nums" :class="overLimit ? 'text-danger-500' : 'text-gray-400'">
        {{ text.length }} / {{ maxLength }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
// 评论输入框：同时承载「发表评论」「回复某人」「编辑评论」三种模式，由 mode 与 replyTo 控制文案。
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  /** create=发表顶级评论，reply=回复，edit=编辑已有评论 */
  mode?: 'create' | 'reply' | 'edit'
  /** 被回复者昵称，reply 模式下展示 */
  replyTo?: string
  /** 编辑模式的初始内容 */
  initialContent?: string
  submitting?: boolean
  /** 是否展示取消按钮（回复/编辑模式默认展示） */
  cancelable?: boolean
  autofocus?: boolean
  rows?: number
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  replyTo: '',
  initialContent: '',
  submitting: false,
  cancelable: false,
  autofocus: false,
  rows: 2,
})

const emit = defineEmits<{
  submit: [content: string]
  cancel: []
}>()

const maxLength = 1000
const text = ref(props.initialContent)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const overLimit = computed(() => text.value.length >= maxLength)
const canSubmit = computed(() => !props.submitting && text.value.trim().length > 0)

const placeholder = computed(() => {
  if (props.mode === 'reply') return `回复 ${props.replyTo || '该评论'}…`
  if (props.mode === 'edit') return '修改你的评论…'
  return '写下你的评论，友善交流~'
})

const submitText = computed(() => {
  if (props.mode === 'edit') return '保存'
  if (props.mode === 'reply') return '回复'
  return '发送'
})

const contextLabel = computed(() => {
  if (props.mode === 'reply' && props.replyTo) return `回复 @${props.replyTo}`
  if (props.mode === 'edit') return '编辑评论'
  return ''
})

watch(() => props.initialContent, (val) => {
  text.value = val
})

onMounted(() => {
  if (props.autofocus) {
    nextTick(() => textareaRef.value?.focus())
  }
})

function handleSubmit(): void {
  if (!canSubmit.value) return
  emit('submit', text.value.trim())
}

function handleCancel(): void {
  emit('cancel')
}

/** 供父组件在提交成功后清空输入 */
function clear(): void {
  text.value = ''
}

defineExpose({ clear })
</script>
