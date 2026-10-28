<template>
  <div
    :id="id"
    ref="list"
    class="kf-select-list"
    role="listbox"
    :aria-label="label"
    :aria-multiselectable="multiple || undefined"
    :aria-required="required || undefined"
    tabindex="-1"
    @keydown="onKey"
  >
    <div
      v-for="group in groups"
      :key="group.label"
      :role="group.label ? 'group' : undefined"
      :aria-label="group.label || undefined"
      class="select-choice-group"
    >
      <div v-if="group.label" class="select-choice-heading" aria-hidden="true">
        {{ group.label }}
      </div>
      <button
        v-for="choice in group.items"
        :id="optionId(choice)"
        :key="choice.key"
        type="button"
        role="option"
        tabindex="-1"
        :aria-selected="values.includes(choice.value)"
        :title="choice.label"
        :disabled="choice.disabled"
        class="kf-select-option"
        :class="{
          'select-option-focused': keyboardActive && activeKey === choice.key,
        }"
        @focus="setActive(choice)"
        @pointerdown="keyboardActive = false"
        @click.stop="!choice.disabled && emit('choose', choice)"
      >
        <Icon
          v-if="choice.icon"
          :name="choice.icon"
          :size="16"
          class="shrink-0"
        />
        <span
          v-if="choice.color"
          class="select-color-swatch"
          :style="{ background: choice.color }"
          aria-hidden="true"
        />
        <span class="select-option-copy"
          ><slot name="option" :option="choice.source"
            ><span>{{ choice.label }}</span
            ><small v-if="choice.description">{{
              choice.description
            }}</small></slot
          ></span
        >
        <Icon
          v-if="values.includes(choice.value)"
          name="check"
          :size="16"
          class="shrink-0"
        />
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import Icon from "./Icon.vue";
import type { SelectAtom, SelectChoice } from "./select-types";
const props = withDefaults(
  defineProps<{
    id: string;
    label: string;
    choices: readonly SelectChoice[];
    values: readonly SelectAtom[];
    multiple?: boolean;
    required?: boolean;
  }>(),
  { multiple: false, required: false },
);
const emit = defineEmits<{
  choose: [choice: SelectChoice];
  active: [id: string | undefined];
}>();
const list = ref<HTMLElement | null>(null),
  activeKey = ref(""),
  keyboardActive = ref(false);
const groups = computed(() => {
  const map = new Map<string, SelectChoice[]>();
  for (const c of props.choices) {
    const group = map.get(c.group) || [];
    group.push(c);
    map.set(c.group, group);
  }
  return [...map].map(([label, items]) => ({ label, items }));
});
const enabled = computed(() =>
  groups.value.flatMap((group) => group.items).filter((c) => !c.disabled),
);
function optionId(c: SelectChoice) {
  return props.id + "-option-" + props.choices.indexOf(c);
}
function setActive(c: SelectChoice) {
  activeKey.value = c.key;
  emit("active", optionId(c));
}
function scrollActive() {
  const el = Array.from(
    list.value?.querySelectorAll<HTMLElement>("[role=option]") || [],
  ).find(
    (el) =>
      el.id ===
      props.id +
        "-option-" +
        props.choices.findIndex((c) => c.key === activeKey.value),
  );
  if (!el || !list.value) return;
  const r = el.getBoundingClientRect(),
    l = list.value.getBoundingClientRect();
  if (r.bottom > l.bottom) list.value.scrollTop += r.bottom - l.bottom;
  else if (r.top < l.top) list.value.scrollTop -= l.top - r.top;
}
function focus(last = false) {
  keyboardActive.value = true;
  const c = last
    ? enabled.value.at(-1)
    : enabled.value.find((c) => props.values.includes(c.value)) ||
      enabled.value[0];
  if (c) setActive(c);
  const id = c ? optionId(c) : props.id;
  Array.from(list.value?.querySelectorAll<HTMLElement>("[role=option]") || [])
    .find((el) => el.id === id)
    ?.focus({ preventScroll: true });
  scrollActive();
}
function navigate(key: string, retainFocus = false) {
  keyboardActive.value = true;
  const choices = enabled.value;
  if (!choices.length) return false;
  let index = choices.findIndex((c) => c.key === activeKey.value);
  if (index < 0) {
    const focused = document.activeElement;
    index = choices.findIndex((c) => optionId(c) === focused?.id);
  }
  if (key === "Enter" || key === " ") {
    const c = choices[index < 0 ? 0 : index];
    if (c) emit("choose", c);
    return true;
  }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(key)) return false;
  const next =
    key === "Home"
      ? 0
      : key === "End"
        ? choices.length - 1
        : index < 0
          ? key === "ArrowUp"
            ? choices.length - 1
            : 0
          : (index + (key === "ArrowDown" ? 1 : -1) + choices.length) %
            choices.length;
  const c = choices[next]!;
  setActive(c);
  if (!retainFocus)
    Array.from(list.value?.querySelectorAll<HTMLElement>("[role=option]") || [])
      .find((el) => el.id === optionId(c))
      ?.focus({ preventScroll: true });
  void nextTick(scrollActive);
  return true;
}
function onKey(e: KeyboardEvent) {
  if (e.isComposing || e.keyCode === 229) return;
  if (navigate(e.key)) e.preventDefault();
}
watch(
  () => props.choices,
  () => {
    const current = enabled.value.find((c) => c.key === activeKey.value);
    if (current) emit("active", optionId(current));
    else {
      activeKey.value = "";
      emit("active", undefined);
    }
  },
  { flush: "post" },
);
defineExpose({ focus, navigate });
</script>
<style scoped>
.select-choice-group + .select-choice-group {
  margin-top: var(--kf-space-1);
  padding-top: var(--kf-space-1);
  border-top: 1px solid var(--kf-border);
}
.select-choice-heading {
  padding: var(--kf-space-2) var(--kf-space-3);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.kf-select-option {
  min-height: var(--kf-select-option-height);
  padding-block: var(--kf-space-2);
}
.select-option-copy :deep(.select-option-label) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.select-option-copy {
  min-width: 0;
  flex: 1;
  text-align: left;
  overflow-wrap: anywhere;
}
.select-option-copy small {
  display: block;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: var(--kf-lh-caption);
}
.select-color-swatch {
  width: var(--kf-space-4);
  height: var(--kf-space-4);
  border-radius: var(--kf-radius-full);
  border: 1px solid var(--kf-border);
  flex-shrink: 0;
}
.select-option-focused:not(:disabled) {
  background: var(--kf-menu-hover);
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: calc(-1 * var(--kf-field-focus-width));
}
</style>
