<template>
  <div ref="rootRef" class="select-control kf-form-field w-full" :class="{'kf-form-field--unlabelled':!label}">
    <label v-if="label" :for="triggerId" class="select-label kf-form-label"
      >{{ label }}<span v-if="required" class="select-required" aria-hidden="true"> *</span></label
    >
    <button
      ref="triggerRef"
      :id="triggerId"
      type="button"
      aria-haspopup="listbox"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :aria-controls="listId"
      :aria-invalid="!!error || undefined"
      :aria-describedby="descriptionIds"
      :disabled="disabled"
      class="kf-select-trigger"
      :class="[
        'select-trigger--' + variant,
        {
          'kf-select-trigger-error': error,
          'kf-select-trigger-disabled': disabled,
        },
      ]"
      @click.stop="toggle"
      @keydown="onTriggerKey"
    >
      <Icon
        v-if="prefixIcon"
        :name="prefixIcon"
        :size="16"
        class="kf-select-prefix"
      />
      <span
        :class="selectedLabel ? 'kf-select-value' : 'kf-select-placeholder'"
        >{{ selectedLabel || placeholder }}</span
      >
      <Icon
        :name="effectiveLoading ? 'loader' : 'chevron-down'"
        :size="14"
        :class="{
          'animate-spin': effectiveLoading,
          'kf-select-arrow-up': open,
        }"
        class="kf-select-arrow shrink-0"
      />
    </button>
    <select
      v-if="required"
      class="select-validation"
      tabindex="-1"
      aria-hidden="true"
      :required="required"
      :disabled="disabled"
      :multiple="multiple"
      @invalid.prevent="triggerRef?.focus()"
    >
      <option
        value=""
        :selected="!values.some((v) => v !== null && v !== '')"
      ></option>
      <option
        v-for="choice in validationChoices"
        :key="choice.key"
        :value="validationKey(choice.value)"
        :selected="values.includes(choice.value)"
        :disabled="choice.disabled"
      >
        {{ choice.label }}
      </option>
    </select>
    <Teleport :to="teleportTarget">
      <div
        v-if="open"
        ref="panelRef"
        tabindex="-1"
        class="kf-select-panel select-panel-v2"
        :class="{ 'select-panel--searchable': searchEnabled }"
        :style="panelStyle"
        @keydown="onPanelKey"
        @click.stop
      >
        <div v-if="searchEnabled" class="select-search kf-input-surface">
          <Icon name="search" :size="16" aria-hidden="true" />
          <input
            ref="searchInputRef"
            v-model="query"
            role="combobox"
            aria-autocomplete="list"
            aria-expanded="true"
            :aria-label="'搜索' + (label || ariaLabel || '选项')"
            :aria-controls="listId"
            :aria-activedescendant="activeId"
            placeholder="搜索选项…"
            :disabled="loading"
            @compositionstart="composing = true"
            @compositionend="composing = false"
            @keydown="onSearchKey"
          />
        </div>
        <div v-if="effectiveLoading" role="status" class="select-message">
          正在加载选项…
        </div>
        <div
          v-else-if="effectiveError"
          role="alert"
          class="select-message select-error"
        >
          {{ effectiveError
          }}<button
            type="button"
            data-select-retry
            class="select-retry"
            @click="retry"
          >
            重试
          </button>
        </div>
        <template v-else>
          <div v-if="multiple && (clearable || selectAll)" class="select-bulk">
            <button v-if="selectAll" type="button" @click="chooseAll">
              全选当前结果</button
            ><button v-if="clearable" type="button" @click="commit([], null)">
              清除选择
            </button>
          </div>
          <SelectChoices
            v-if="filtered.length"
            ref="choicesRef"
            :id="listId"
            :label="label || ariaLabel || placeholder"
            :choices="filtered"
            :values="values"
            :multiple="multiple"
            :required="required"
            @choose="choose"
            @active="activeId = $event"
            ><template #option="slotProps"
              ><slot name="option" v-bind="slotProps"
                ><span class="select-option-label">{{ slotProps.option[labelKey] }}</span
                ><small
                  v-if="slotProps.option.description"
                  class="select-description"
                  >{{ slotProps.option.description }}</small
                ></slot
              ></template
            ></SelectChoices
          >
          <div
            v-else
            :id="listId"
            role="listbox"
            :aria-label="label || ariaLabel || placeholder"
            tabindex="-1"
            class="select-message"
          >
            {{ query ? "无匹配项" : "暂无选项" }}
          </div>
        </template>
      </div>
    </Teleport>
    <div v-if="error || missing || help" class="kf-form-field__meta">
      <p v-if="help" :id="triggerId+'-help'" class="kf-form-help">{{help}}</p>
    <p v-if="error" :id="errorId" class="select-error select-help">
      {{ error }}
    </p>
    <p v-if="missing" :id="missingId" class="select-help" role="status">
      选项已不可用，请重新选择；原值已保留。
    </p>
    </div>
  </div>
</template>
<script setup lang="ts" generic="T extends SelectValue = SelectValue">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  ref,
  useId,
  watch,
  type CSSProperties,
} from "vue";
import Icon from "./Icon.vue";
import SelectChoices from "./SelectChoices.vue";
import { placeSelect } from "./select-position";
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
    modelValue?: T;
    options: readonly SelectOption[];
    labelKey?: string;
    valueKey?: string;
    groupKey?: string;
    placeholder?: string;
    label?: string;
    ariaLabel?: string;
    required?: boolean;
    disabled?: boolean;
    searchable?: boolean;
    searchThreshold?: number;
    /** 可选独立浮层宽度；未传时保留既有字段等宽策略。 */
    panelWidth?: number;
    debounceMs?: number;
    loadOptions?: (
      query: string,
      signal: AbortSignal,
    ) => Promise<readonly SelectOption[]>;
    multiple?: boolean;
    loading?: boolean;
    loadError?: string;
    help?: string;
    error?: string;
    prefixIcon?: string;
    variant?: "field" | "pill" | "subtle";
    clearable?: boolean;
    selectAll?: boolean;
    closeOnSelect?: boolean;
    remote?: boolean;
    filterOption?: (option: SelectOption, query: string) => boolean;
  }>(),
  {
    labelKey: "label",
    valueKey: "value",
    groupKey: "group",
    placeholder: "请选择",
    label: "",
    required: false,
    disabled: false,
    searchable: undefined,
    searchThreshold: 9,
    debounceMs: 250,
    multiple: false,
    loading: false,
    loadError: "",
    error: "",
    variant: "field",
    clearable: false,
    selectAll: false,
    remote: false,
    closeOnSelect: undefined,
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: T];
  change: [option: SelectOption | SelectOption[] | null];
  search: [query: string];
  retry: [];
}>();
const uid = useId(),
  triggerId = "select-" + uid,
  listId = triggerId + "-list",
  errorId = triggerId + "-error",
  missingId = triggerId + "-missing";
const rootRef = ref<HTMLElement | null>(null),
  triggerRef = ref<HTMLButtonElement | null>(null),
  panelRef = ref<HTMLElement | null>(null),
  searchInputRef = ref<HTMLInputElement | null>(null);
const choicesRef = ref<InstanceType<typeof SelectChoices> | null>(null),
  open = ref(false),
  query = ref(""),
  composing = ref(false),
  activeId = ref<string>(),
  teleportTarget = ref<string | HTMLElement>("body"),
  panelStyle = ref<CSSProperties>({});
const remoteOptions = ref<readonly SelectOption[] | null>(null),
  remoteLoading = ref(false),
  remoteError = ref("");
const effectiveLoading = computed(() => props.loading || remoteLoading.value);
const effectiveError = computed(() => props.loadError || remoteError.value);
const searchEnabled = computed(
  () =>
    props.searchable ??
    (!!props.loadOptions || normalized.value.length >= props.searchThreshold),
);
function validationKey(value: SelectAtom) {
  return value === null || value === "" ? "" : typeof value + ":" + value;
}

const normalized = computed(() =>
  normalizeChoices(
    remoteOptions.value ?? props.options,
    props.labelKey,
    props.valueKey,
    props.groupKey,
  ),
);
const values = computed<SelectAtom[]>(() =>
  uniqueValues(
    Array.isArray(props.modelValue)
      ? props.modelValue
      : props.modelValue === undefined
        ? []
        : [props.modelValue as SelectAtom],
  ),
);
// 远程搜索返回的是查询窗口，不是完整候选集合；保留真实已选项的标签。
const selectedCache = ref(new Map<SelectAtom, SelectChoice>());
watch(
  [normalized, values],
  () => {
    const next = new Map<SelectAtom, SelectChoice>();
    for (const value of values.value) {
      const choice =
        normalized.value.find((c) => c.value === value) ||
        selectedCache.value.get(value);
      if (choice) next.set(value, choice);
    }
    selectedCache.value = next;
  },
  { immediate: true },
);
const validationChoices = computed(() =>
  props.remote || props.loadOptions
    ? [
        ...normalized.value,
        ...[...selectedCache.value.values()].filter(
          (c) => !normalized.value.some((n) => n.value === c.value),
        ),
      ]
    : normalized.value,
);
const missing = computed(
  () =>
    !props.remote &&
    !props.loadOptions &&
    !effectiveLoading.value &&
    !effectiveError.value &&
    values.value.some((v) => !normalized.value.some((c) => c.value === v)) &&
    props.modelValue !== null &&
    props.modelValue !== "",
);
const selectedLabel = computed(() =>
  values.value
    .filter((v) => v !== null || normalized.value.some((c) => c.value === null))
    .map(
      (v) =>
        normalized.value.find((c) => c.value === v)?.label ||
        (props.remote || props.loadOptions
          ? selectedCache.value.get(v)?.label ||
            (v === null || v === "" ? "" : String(v))
          : v === ""
            ? ""
            : String(v) + "（不可用）"),
    )
    .join("、"),
);
const filtered = computed(() => {
  const q = query.value.trim().toLocaleLowerCase();
  return !q || props.remote || !!props.loadOptions
    ? normalized.value
    : normalized.value.filter((c) =>
        props.filterOption
          ? props.filterOption(c.source, query.value)
          : (c.label + " " + (c.description || "") + " " + c.group)
              .toLocaleLowerCase()
              .includes(q),
      );
});
const descriptionIds = computed(
  () =>
    [props.help ? triggerId+"-help" : undefined, props.error ? errorId : undefined, missing.value ? missingId : undefined]
      .filter(Boolean)
      .join(" ") || undefined,
);
let remoteEpoch = 0,
  remoteController: AbortController | undefined,
  remoteTimer: ReturnType<typeof setTimeout> | undefined;
function cancelRemote() {
  remoteEpoch++;
  remoteController?.abort();
  remoteController = undefined;
  clearTimeout(remoteTimer);
  remoteLoading.value = false;
}
function scheduleRemote(delay = props.debounceMs) {
  cancelRemote();
  if (!props.loadOptions || !open.value) return;
  const epoch = remoteEpoch,
    loader = props.loadOptions;
  remoteLoading.value = true;
  remoteError.value = "";
  remoteTimer = setTimeout(
    async () => {
      const controller = new AbortController();
      remoteController = controller;
      try {
        const items = await loader(query.value, controller.signal);
        if (!disposed && open.value && epoch === remoteEpoch)
          remoteOptions.value = items;
      } catch (error: unknown) {
        if (
          !disposed &&
          open.value &&
          epoch === remoteEpoch &&
          !controller.signal.aborted
        )
          remoteError.value =
            error instanceof Error ? error.message : "选项加载失败，请重试";
      } finally {
        if (epoch === remoteEpoch) remoteLoading.value = false;
      }
    },
    Math.max(0, delay),
  );
}
function retry() {
  if (props.loadOptions) scheduleRemote(0);
  else emit("retry");
}
let disposed = false,
  generation = 0,
  observer: ResizeObserver | undefined;
function position() {
  const r = triggerRef.value?.getBoundingClientRect();
  if (!open.value || !r) return;
  const gap = 8,
    available = window.innerWidth - gap * 2,
    width = Math.min(
      Number.isFinite(props.panelWidth) && props.panelWidth! > 0
        ? props.panelWidth!
        : searchEnabled.value
          ? Math.min(Math.max(r.width, 300), 380)
          : Math.max(r.width, 208),
      available,
    ),
    list = panelRef.value?.querySelector<HTMLElement>(".kf-select-list"),
    listLimit = list ? parseFloat(getComputedStyle(list).maxHeight) : 280,
    natural = list
      ? Math.min(
          list.scrollHeight,
          Number.isFinite(listLimit) ? listLimit : 280,
        ) +
        (panelRef.value?.querySelector<HTMLElement>(".select-search")
          ?.offsetHeight || 0) +
        (panelRef.value?.querySelector<HTMLElement>(".select-bulk")
          ?.offsetHeight || 0)
      : Math.max(panelRef.value?.scrollHeight || 0, 48),
    panelLimit = searchEnabled.value
      ? parseFloat(getComputedStyle(panelRef.value!).getPropertyValue("--kf-select-panel-max-height")) || 352
      : Infinity,
    placed = placeSelect(r, width, Math.min(natural + (searchEnabled.value ? 2 : 8), panelLimit), {
      width: window.innerWidth,
      height: window.innerHeight,
    });
  panelStyle.value = {
    position: "fixed",
    left: placed.left + "px",
    top: placed.top + "px",
    bottom: "auto",
    right: "auto",
    width: placed.width + "px",
    maxHeight: placed.maxHeight + "px",
    zIndex: 1200,
  };
}
function close(restore = false) {
  generation++;
  cancelRemote();
  open.value = false;
  query.value = "";
  activeId.value = undefined;
  cleanup();
  if (restore && triggerRef.value?.isConnected)
    triggerRef.value.focus({ preventScroll: true });
}
async function show(last = false, keyboard = false) {
  if (props.disabled) return;
  const current = ++generation;
  teleportTarget.value =
    triggerRef.value?.closest<HTMLElement>("[role=dialog]") || "body";
  open.value = true;
  if (props.loadOptions) scheduleRemote(0);
  await nextTick();
  if (disposed || !open.value || current !== generation) return;
  position();
  if (disposed || !open.value || current !== generation) return;
  if (searchEnabled.value) searchInputRef.value?.focus({ preventScroll: true });
  else if (keyboard) choicesRef.value?.focus(last);
  else
    (
      panelRef.value?.querySelector<HTMLElement>(
        "[aria-selected=true]:not(:disabled),[role=option]:not(:disabled),[role=listbox]",
      ) || panelRef.value
    )?.focus({ preventScroll: true });
  document.addEventListener("pointerdown", outside);
  document.addEventListener("focusin", onFocus);
  window.addEventListener("scroll", onScroll, true);
  window.addEventListener("resize", position);
  if (typeof ResizeObserver !== "undefined") {
    observer?.disconnect();
    observer = new ResizeObserver(position);
    if (panelRef.value) observer.observe(panelRef.value);
    if (triggerRef.value) observer.observe(triggerRef.value);
  }
}
function toggle() {
  if (open.value) close();
  else void show();
}
function onTriggerKey(e: KeyboardEvent) {
  if (e.isComposing) return;
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    void show(e.key === "ArrowUp", true);
  } else if (e.key === "Escape" && open.value) {
    e.preventDefault();
    close(true);
  }
}
function onSearchKey(e: KeyboardEvent) {
  if (e.isComposing || composing.value || e.keyCode === 229) return;
  if (["ArrowDown", "ArrowUp", "Enter"].includes(e.key)) {
    e.preventDefault();
    choicesRef.value?.navigate(e.key, true);
  }
}
function onPanelKey(e: KeyboardEvent) {
  if (e.isComposing || composing.value || e.keyCode === 229) return;
  if (e.key === "Escape") {
    e.preventDefault();
    e.stopPropagation();
    close(true);
  } else if (e.key === "Tab") close(true);
}
function choose(c: SelectChoice) {
  if (
    props.disabled ||
    effectiveLoading.value ||
    effectiveError.value ||
    c.disabled
  )
    return;
  const next = props.multiple
    ? values.value.includes(c.value)
      ? values.value.filter((v) => v !== c.value)
      : uniqueValues([...values.value, c.value])
    : c.value;
  commit(next, c.source);
}
function commit(value: SelectValue, source: SelectOption | null) {
  if (props.disabled || effectiveLoading.value || effectiveError.value) return;
  const next = Array.isArray(value) ? uniqueValues(value) : value;
  const current = props.modelValue;
  const unchanged =
    Array.isArray(next) && Array.isArray(current)
      ? next.length === current.length && next.every((v, i) => v === current[i])
      : next === current;
  if (!unchanged) {
    emit("update:modelValue", next as T);
    emit(
      "change",
      props.multiple
        ? normalized.value
            .filter((c) => (next as SelectAtom[]).includes(c.value))
            .map((c) => c.source)
        : source,
    );
  }
  if (props.closeOnSelect ?? !props.multiple) close(true);
}
function chooseAll() {
  commit(
    uniqueValues([
      ...values.value,
      ...filtered.value.filter((c) => !c.disabled).map((c) => c.value),
    ]),
    null,
  );
}
function inside(target: EventTarget | null) {
  return (
    target instanceof Node &&
    (!!rootRef.value?.contains(target) || !!panelRef.value?.contains(target))
  );
}
function outside(e: Event) {
  if (!inside(e.target)) close();
}
function onFocus(e: Event) {
  if (!inside(e.target)) close();
}
function onScroll(e: Event) {
  if (!panelRef.value?.contains(e.target as Node)) close();
}
function cleanup() {
  document.removeEventListener("pointerdown", outside);
  document.removeEventListener("focusin", onFocus);
  window.removeEventListener("scroll", onScroll, true);
  window.removeEventListener("resize", position);
  observer?.disconnect();
  observer = undefined;
}
watch(
  () => props.loadOptions,
  () => {
    remoteOptions.value = null;
    cancelRemote();
    if (open.value && props.loadOptions) scheduleRemote(0);
  },
);
watch(query, (q) => {
  activeId.value = undefined;
  emit("search", q);
  if (open.value && props.loadOptions) scheduleRemote();
});
watch(
  () => props.disabled,
  (v) => {
    if (v) close();
  },
);
watch([normalized, effectiveLoading, effectiveError], () => {
  if (open.value)
    void nextTick(() => {
      position();
      if (document.activeElement === document.body)
        (searchEnabled.value && !effectiveLoading.value
          ? searchInputRef.value
          : panelRef.value
        )?.focus({ preventScroll: true });
    });
});
onBeforeUnmount(() => {
  disposed = true;
  cancelRemote();
  generation++;
  cleanup();
});
</script>
<style scoped>
.select-validation {
  position: absolute;
  inline-size: 1px;
  block-size: 1px;
  clip-path: inset(50%);
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}
.select-label {
  display: block;
  margin-bottom: 0;
  font-size: var(--kf-fs-body-sm);
  line-height:var(--kf-lh-body-sm);
  font-weight:var(--kf-fw-label);
  color: var(--kf-foreground);
}
.select-required,
.select-error {
  color: var(--kf-danger-text);
}
.select-help {
  margin-top: var(--kf-space-1);
  font-size: var(--kf-fs-caption);
}
.select-trigger--pill {
  border-color: transparent;
  border-radius: var(--kf-radius-full);
  background: var(--kf-select-trigger-bg);
}
.select-trigger--subtle {
  border-color: transparent;
  background: transparent;
}
.select-trigger--pill:hover:not(:disabled),
.select-trigger--subtle:hover:not(:disabled) {
  background: var(--kf-select-trigger-hover);
  border-color: transparent;
}
.kf-select-trigger:focus-visible {
  outline: 0;
  box-shadow: none;
}
.kf-select-trigger-error:focus-visible {
  outline-color: var(--kf-destructive);
}
.select-panel-v2 {
  display: flex;
  flex-direction: column;
  border-radius: var(--kf-select-content-radius);
  background: var(--kf-menu-surface);
  box-shadow: var(--kf-menu-shadow);
  overflow: hidden;
  min-width: 0;
}
.select-panel-v2 :deep(.kf-select-list) {
  min-height: 0;
  max-height: var(--kf-select-list-max-height);
  overflow: auto;
  flex: 1;
}
.select-panel--searchable :deep(.kf-select-list) {
  max-height: calc(var(--kf-select-panel-max-height) - var(--kf-select-search-height));
}
.select-search {
  box-sizing: border-box;
  height: var(--kf-select-search-height);
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  padding: 0 var(--kf-space-3);
  border-bottom: 1px solid var(--kf-border);
  flex-shrink: 0;
  color: var(--kf-muted-foreground);
}
.select-search > svg { flex-shrink: 0; }
.select-search input,
.select-search input:focus,
.select-search input:focus-visible {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  min-width: 0;
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  outline: 0;
  border-radius: 0;
  font: inherit;
  line-height: var(--kf-lh-body-md);
  color: var(--kf-foreground);
}
.select-message {
  padding: var(--kf-space-4);
  font-size: var(--kf-fs-body-sm);
  overflow: auto;
}
.select-retry {
  display: block;
  margin-top: var(--kf-space-2);
  color: var(--kf-primary);
}
.select-bulk {
  display: flex;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding: var(--kf-space-2) var(--kf-space-3);
  border-bottom: 1px solid var(--kf-border);
  font-size: var(--kf-fs-body-sm);
  flex-shrink: 0;
}
.select-description {
  display: block;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (forced-colors: active) {
  .select-search:focus-within {
    outline: 2px solid Highlight;
  }
}
</style>
