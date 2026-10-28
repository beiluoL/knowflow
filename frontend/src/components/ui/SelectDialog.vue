<template>
  <AppDialog
    :open="open"
    :title="title"
    :description="description"
    scrollable
    :persistent="busy"
    @cancel="cancel"
  >
    <div class="select-dialog" :aria-busy="loading || busy">
      <Input
        v-model="query"
        label="搜索选项"
        placeholder="搜索…"
        :disabled="loading || busy"
        @keydown="onSearchKey"
      />
      <p v-if="loading" role="status">正在加载选项…</p>
      <div v-else-if="loadError" role="alert">
        <p>{{ loadError }}</p>
        <Button variant="secondary" @click="emit('retry')">重试</Button>
      </div>
      <template v-else>
        <SelectChoices
          :id="listId"
          ref="choicesRef"
          :label="title"
          :choices="filtered"
          :values="values"
          :multiple="multiple"
          :required="required"
          @choose="choose"
        />
        <div v-if="!filtered.length" role="status">
          <slot name="empty">{{
            choices.length ? "无匹配项" : "暂无可选项"
          }}</slot>
        </div>
      </template>
      <p v-if="missing" role="alert">
        选项已不可用，请移除不可用选择后确认。<button
          type="button"
          @click="removeMissing"
        >
          移除不可用选择
        </button>
      </p>
      <p v-if="error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <Button variant="secondary" :disabled="busy" @click="cancel">取消</Button>
      <Button
        :loading="busy"
        :disabled="
          loading || !!loadError || missing || (required && !values.length)
        "
        @click="confirm"
        >确定</Button
      >
    </template>
  </AppDialog>
</template>
<script setup lang="ts" generic="T extends SelectValue">
import { computed, ref, shallowRef, useId, watch } from "vue";
import AppDialog from "./AppDialog.vue";
import Input from "./Input.vue";
import Button from "./Button.vue";
import SelectChoices from "./SelectChoices.vue";
import {
  normalizeChoices,
  uniqueValues,
  type SelectAtom,
  type SelectChoice,
  type SelectOption,
  type SelectValue,
} from "./select-types";
const props = withDefaults(
  defineProps<{
    open: boolean;
    modelValue: T;
    options: readonly SelectOption[];
    title?: string;
    description?: string;
    multiple?: boolean;
    required?: boolean;
    loading?: boolean;
    loadError?: string;
    error?: string;
    busy?: boolean;
    labelKey?: string;
    valueKey?: string;
    groupKey?: string;
  }>(),
  {
    title: "选择选项",
    multiple: false,
    required: false,
    loading: false,
    busy: false,
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: T];
  confirm: [value: T];
  cancel: [];
  retry: [];
}>();
const query = ref<string | number>(""),
  pending = shallowRef<SelectValue>(null);
const choicesRef = ref<InstanceType<typeof SelectChoices> | null>(null),
  listId = "select-dialog-" + useId();
const choices = computed(() =>
  normalizeChoices(
    props.options,
    props.labelKey,
    props.valueKey,
    props.groupKey,
  ),
);
const missing = computed(
  () =>
    !props.loading &&
    !props.loadError &&
    values.value.some(
      (value) => !choices.value.some((c) => c.value === value && !c.disabled),
    ),
);
function removeMissing() {
  const valid = values.value.filter((value) =>
    choices.value.some((c) => c.value === value && !c.disabled),
  );
  pending.value = props.multiple ? valid : (valid[0] ?? null);
}
const filtered = computed(() =>
  choices.value.filter((choice) =>
    choice.label
      .toLocaleLowerCase()
      .includes(String(query.value).trim().toLocaleLowerCase()),
  ),
);
const values = computed<SelectAtom[]>(() =>
  Array.isArray(pending.value)
    ? uniqueValues(pending.value)
    : pending.value === null
      ? []
      : [pending.value],
);
watch(
  () => props.open,
  (open) => {
    if (open) {
      pending.value = Array.isArray(props.modelValue)
        ? [...props.modelValue]
        : props.modelValue;
      query.value = "";
    }
  },
  { immediate: true },
);
function choose(choice: SelectChoice) {
  if (props.busy || props.loading || props.loadError || choice.disabled) return;
  if (props.multiple) {
    pending.value = values.value.includes(choice.value)
      ? values.value.filter((value) => value !== choice.value)
      : [...values.value, choice.value];
  } else pending.value = choice.value;
}
function cancel() {
  if (!props.busy) emit("cancel");
}
function confirm() {
  if (
    props.busy ||
    props.loading ||
    props.loadError ||
    missing.value ||
    (props.required && !values.value.length)
  )
    return;
  const value = (
    Array.isArray(pending.value) ? uniqueValues(pending.value) : pending.value
  ) as T;
  emit("update:modelValue", value);
  emit("confirm", value);
}
function onSearchKey(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return;
  if (["ArrowDown", "ArrowUp"].includes(event.key)) {
    event.preventDefault();
    choicesRef.value?.focus(event.key === "ArrowUp");
  }
}
</script>
<style scoped>
.select-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  min-width: 0;
}
.select-dialog :deep(.kf-select-list) {
  max-height: var(--kf-select-list-max-height);
  overflow-y: auto;
}
.select-dialog [role="alert"] {
  color: var(--kf-danger-text);
}
</style>
