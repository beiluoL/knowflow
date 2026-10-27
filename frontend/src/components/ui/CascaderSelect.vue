<template>
  <div ref="root" class="cascader-control">
    <button
      ref="trigger"
      type="button"
      class="kb-select-trigger cascade-trigger"
      :class="'cascade-trigger--' + variant"
      :aria-label="label"
      aria-haspopup="tree"
      :aria-expanded="open"
      :aria-controls="treeId"
      :disabled="disabled"
      @click.stop="open ? close() : show()"
      @keydown="triggerKey"
    >
      <span class="cascade-value">{{
        selected?.path.map((n) => n.label).join(" / ") ||
        (modelValue != null ? String(modelValue) + "（不可用）" : placeholder)
      }}</span
      ><Icon name="chevron-down" :size="14" />
    </button>
    <p
      v-if="modelValue != null && (!selected || selected.option.disabled)"
      class="cascade-help"
      role="status"
    >
      选项已不可用，请重新选择；原值已保留。
    </p>
    <Teleport :to="target">
      <div
        v-if="open"
        ref="popup"
        :id="treeId"
        role="tree"
        :aria-label="label"
        class="cascade-tree"
        @keydown="treeKey"
        @click.stop
        @pointerleave="leave"
        @pointerenter="clearHover"
      >
        <section
          v-for="column in visibleColumns"
          :key="column.depth"
          :ref="(el) => setColumn(el, column.depth)"
          class="cascade-panel"
          :data-cascade-column="column.depth"
          :style="styles[column.depth]"
          role="group"
          :aria-label="column.depth ? path[column.depth - 1]?.label : label"
          :id="treeId + '-group-' + column.depth"
        >
          <div v-if="mobile && column.depth" class="cascade-mobile-head">
            <button
              type="button"
              aria-label="返回上一级"
              @click="back(column.depth)"
            >
              <Icon name="chevron-left" :size="16" />返回</button
            ><span>{{ path.map((n) => n.label).join(" / ") }}</span>
          </div>
          <div
            v-if="column.depth === 0 && searchEnabled"
            class="cascade-search kb-input-surface"
          >
            <Icon name="search" :size="16" /><input
              :ref="setSearch"
              type="search"
              aria-label="搜索分类"
              v-model="query"
              placeholder="搜索选项…"
              @keydown="searchKey"
              @compositionstart="composing = true"
              @compositionend="composing = false"
            />
          </div>
          <p v-if="column.depth===0 && loading" class="cascade-help" role="status">正在加载选项…</p>
          <div v-else-if="column.depth===0 && loadError" class="cascade-help" role="alert">{{ loadError }}<button type="button" class="cascade-retry" data-cascade-retry @click="$emit('retry')">重试</button></div>
          <div v-else class="cascade-list" @scroll="columnScroll(column.depth)">
            <button
              v-if="column.depth === 0 && !query && emptyLabel"
              type="button"
              class="cascade-empty"
              role="treeitem"
              aria-level="1"
              :aria-selected="modelValue === null"
              tabindex="-1"
              data-cascade-empty
              @click="commit(null)"
            >
              {{ emptyLabel
              }}<Icon v-if="modelValue === null" name="check" :size="16" />
            </button>
            <div
              v-for="node in column.items"
              :key="node.id"
              class="cascade-row"
            >
              <button
                type="button"
                role="treeitem"
                tabindex="-1"
                :id="nodeId(node)"
                :title="resultLabel(node)"
                :aria-level="column.depth + 1"
                :aria-selected="modelValue === node.value"
                :aria-expanded="
                  hasChildren(node)
                    ? path[column.depth]?.id === node.id
                    : undefined
                "
                :aria-owns="
                  hasChildren(node) && path[column.depth]?.id === node.id
                    ? treeId + '-group-' + (column.depth + 1)
                    : undefined
                "
                :disabled="
                  !hasChildren(node) &&
                  (node.disabled || node.selectable === false)
                "
                :data-depth="column.depth"
                :data-node="node.id"
                :class="{ 'is-selected': modelValue === node.value }"
                @pointerenter="hover(node, column.depth, $event)"
                @click="activate(node, column.depth)"
                @focus="active = node"
              >
                <Icon
                  v-if="node.icon"
                  :name="node.icon"
                  :size="16"
                  :style="{ color: node.color }"
                /><span
                  ><span>{{ resultLabel(node) }}</span
                  ><small v-if="node.description">{{
                    node.description
                  }}</small></span
                ><Icon
                  v-if="modelValue === node.value"
                  name="check"
                  :size="16"
                /><Icon
                  v-if="hasChildren(node)"
                  name="chevron-right"
                  :size="14"
                />
              </button>
              <button
                v-if="hasChildren(node)"
                type="button"
                class="cascade-parent-select"
                :aria-label="'选择 ' + node.label"
                :disabled="node.disabled || node.selectable === false"
                @click="commit(node)"
              >
                选择
              </button>
            </div>
            <p v-if="!column.items.length" role="status" class="cascade-help">
              {{ query ? "未找到匹配项" : "暂无选项" }}
            </p>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>
<script
  setup
  lang="ts"
  generic="T extends CascaderValue | null = CascaderValue | null"
>
import {
  computed,
  ref,
  shallowRef,
  nextTick,
  onBeforeUnmount,
  useId,
  watch,
  type CSSProperties,
  type ComponentPublicInstance,
} from "vue";
import Icon from "./Icon.vue";
import {
  indexCascader,
  type CascaderOption,
  type CascaderValue,
} from "./cascader-types";
import { placeSelect, type SelectRect } from "./select-position";
const props = withDefaults(
  defineProps<{
    modelValue?: T;
    options: readonly CascaderOption[];
    label?: string;
    placeholder?: string;
    emptyLabel?: string;
    disabled?: boolean;
    variant?: "field" | "pill" | "subtle";
    searchable?: boolean;
    searchThreshold?: number;
    loading?: boolean;
    loadError?: string;
  }>(),
  {
    label: "选择分类",
    placeholder: "请选择分类",
    emptyLabel: "",
    disabled: false,
    variant: "field",
    searchable: undefined,
    searchThreshold: 9,
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: T];
  change: [option: CascaderOption | null];
  retry: [];
}>();
const root = ref<HTMLElement>(),
  trigger = ref<HTMLButtonElement>(),
  popup = ref<HTMLElement>(),
  search = ref<HTMLInputElement>(),
  target = shallowRef<string | HTMLElement>("body");
const open = ref(false),
  query = ref(""),
  path = shallowRef<CascaderOption[]>([]),
  mobile = ref(false),
  styles = ref<Record<number, CSSProperties>>({}),
  active = shallowRef<CascaderOption>(),
  composing = ref(false);
const treeId = "cascade-" + useId(),
  columnElements = new Map<number, HTMLElement>();
const entries = computed(() => indexCascader(props.options)),
  selected = computed(() =>
    entries.value.find((e) => e.option.value === props.modelValue),
  ),
  searchEnabled = computed(
    () => props.searchable ?? entries.value.length >= props.searchThreshold,
  );
const queryText = computed(() => query.value.trim().toLocaleLowerCase());
// 搜索结果保留真实路径，分支本身仍可通过独立按钮选择。
const results = computed(() =>
  entries.value
    .filter((e) =>
      e.path
        .map((n) => n.label)
        .join(" / ")
        .toLocaleLowerCase()
        .includes(queryText.value),
    )
    .map((e) => e.option),
);
const columns = computed(() =>
  queryText.value
    ? [{ depth: 0, items: results.value }]
    : [
        { depth: 0, items: props.options },
        ...path.value.map((n, i) => ({
          depth: i + 1,
          items: n.children || [],
        })),
      ],
);
const visibleColumns = computed(() =>
  mobile.value ? [columns.value.at(-1)!] : columns.value,
);
function hasChildren(node: CascaderOption) {
  return !!node.children?.length;
}
function nodeId(node: CascaderOption) {
  return treeId + "-node-" + encodeURIComponent(typeof node.id + ":" + node.id);
}
function resultLabel(node: CascaderOption) {
  return queryText.value
    ? entries.value
        .find((e) => e.option === node)
        ?.path.map((n) => n.label)
        .join(" / ") || node.label
    : node.label;
}
function setSearch(el: Element | ComponentPublicInstance | null) {
  search.value = el instanceof HTMLInputElement ? el : undefined;
}
function setColumn(
  el: Element | ComponentPublicInstance | null,
  depth: number,
) {
  if (el instanceof HTMLElement) columnElements.set(depth, el);
  else columnElements.delete(depth);
}
let timer: ReturnType<typeof setTimeout> | undefined,
  epoch = 0,
  disposed = false,
  observer: ResizeObserver | undefined;
function clearHover() {
  clearTimeout(timer);
}
function close(restore = false) {
  epoch++;
  clearHover();
  open.value = false;
  path.value = [];
  query.value = "";
  cleanup();
  if (restore) trigger.value?.focus({ preventScroll: true });
}
async function show(keyboard = false) {
  if (props.disabled) return;
  const current = ++epoch;
  mobile.value = window.innerWidth < 640;
  target.value = trigger.value?.closest<HTMLElement>("[role=dialog]") || "body";
  query.value = "";
  path.value = [];
  open.value = true;
  await nextTick();
  if (disposed || current !== epoch || !open.value) return;
  position();
  document.addEventListener("pointerdown", outside);
  document.addEventListener("focusin", focusOutside);
  window.addEventListener("resize", resize);
  window.addEventListener("scroll", scrollOutside, true);
  observer = new ResizeObserver(position);
  if (trigger.value) observer.observe(trigger.value);
  if (keyboard) focusAt(0);
  else if (searchEnabled.value) search.value?.focus({ preventScroll: true });
  else focusAt(0);
}
function position() {
  if (!open.value || !trigger.value) return;
  const viewport = { width: window.innerWidth, height: window.innerHeight },
    r = trigger.value.getBoundingClientRect();
  const next: Record<number, CSSProperties> = {};
  for (const column of visibleColumns.value) {
    const el = columnElements.get(column.depth);
    if (!el) continue;
    let anchor: SelectRect | undefined =
      mobile.value || column.depth === 0
        ? r
        : document
            .getElementById(nodeId(path.value[column.depth - 1]!))
            ?.getBoundingClientRect();
    if (!anchor) continue;
    // 横向贴齐父面板外沿，纵向贴齐当前行，避免覆盖父级独立选择按钮。
    if(!mobile.value && column.depth>0){const parent=columnElements.get(column.depth-1)?.getBoundingClientRect();if(parent)anchor={left:parent.left,right:parent.right,width:parent.width,top:anchor.top,bottom:anchor.bottom};}
    const list=el.querySelector<HTMLElement>('.cascade-list');
    const header=el.querySelector<HTMLElement>('.cascade-search, .cascade-mobile-head');
    const padding=parseFloat(getComputedStyle(el).paddingTop)||0;
    const limit=parseFloat(getComputedStyle(el).getPropertyValue('--kb-select-panel-max-height'))||352;
    const height = Math.min((list?.scrollHeight||el.scrollHeight)+(header?.offsetHeight||0)+padding*2,limit);
    const placed = placeSelect(
      anchor,
      mobile.value
        ? Math.max(r.width, 288)
        : Math.max(column.depth ? 224 : r.width, 224),
      height,
      viewport,
      !mobile.value && column.depth > 0,
      column.depth > 1 &&
        parseFloat(String(next[column.depth - 1]?.left)) <
          parseFloat(String(next[column.depth - 2]?.left)),
    );
    next[column.depth] = {
      position: "fixed",
      left: placed.left + "px",
      top: placed.top + "px",
      width: placed.width + "px",
      maxHeight: placed.maxHeight + "px",
    };
  }
  styles.value = next;
}
async function expand(node: CascaderOption, depth: number, focus = false) {
  clearHover();
  if (!hasChildren(node)) return;
  const current = epoch;
  if (queryText.value) {
    query.value = "";
    await nextTick();
    if (!open.value || disposed || current !== epoch) return;
    path.value = [
      ...(entries.value.find((entry) => entry.option === node)?.path || [node]),
    ];
    depth = path.value.length - 1;
  } else path.value = [...path.value.slice(0, depth), node];
  await nextTick();
  if (!open.value || disposed || current !== epoch) return;
  position();
  if (focus) focusAt(depth + 1);
}
function hover(node: CascaderOption, depth: number, event: PointerEvent) {
  if (mobile.value || event.pointerType === "touch" || queryText.value) return;
  clearHover();
  timer = setTimeout(() => {
    if (!open.value) return;
    if (hasChildren(node)) void expand(node, depth);
    else {
      path.value = path.value.slice(0, depth);
      void nextTick(position);
    }
  }, 120);
}
function leave() {
  clearHover(); /* 穿过列间空隙时维持路径；切换到另一行、外点或Escape才关闭。 */
}
function activate(node: CascaderOption, depth: number) {
  if (hasChildren(node)) void expand(node, depth, true);
  else commit(node);
}
function commit(node: CascaderOption | null) {
  if (props.disabled || (node && (node.disabled || node.selectable === false)))
    return;
  const value = node?.value ?? null;
  if (value !== props.modelValue) {
    emit("update:modelValue", value as T);
    emit("change", node);
  }
  close(true);
}
function focusAt(depth: number, last = false) {
  const items = columnElements
    .get(depth)
    ?.querySelectorAll<HTMLButtonElement>(
      "[role=treeitem]:not(:disabled):not([data-cascade-empty])",
    );
  (last ? items?.[items.length - 1] : items?.[0])?.focus({
    preventScroll: true,
  });
}
async function back(depth: number) {
  const parent = path.value[depth - 1];
  path.value = path.value.slice(0, Math.max(0, depth - 1));
  await nextTick();
  position();
  if (parent)
    document.getElementById(nodeId(parent))?.focus({ preventScroll: true });
}
function triggerKey(e: KeyboardEvent) {
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    void show(true);
  } else if (e.key === "Escape") close(true);
}
function treeKey(e: KeyboardEvent) {
  if (e.isComposing || composing.value || e.keyCode === 229) return;
  const item = (e.target as HTMLElement).closest<HTMLElement>(
      "[role=treeitem]",
    ),
    depth = Number(item?.dataset.depth || 0),
    node = entries.value.find((entry) => entry.option === active.value)?.option;
  if (e.key === "Escape") {
    e.preventDefault();
    e.stopPropagation();
    close(true);
    return;
  }
  if (e.key === "Tab") return; // 允许Tab触达父分类的独立选择按钮，焦点离开时关闭。
  if (!item) return;
  const items = [
      ...columnElements
        .get(depth)!
        .querySelectorAll<HTMLElement>("[role=treeitem]:not(:disabled)"),
    ],
    index = items.indexOf(item);
  if (["ArrowDown", "ArrowUp", "Home", "End"].includes(e.key)) {
    e.preventDefault();
    const next =
      e.key === "Home"
        ? 0
        : e.key === "End"
          ? items.length - 1
          : (index + (e.key === "ArrowUp" ? -1 : 1) + items.length) %
            items.length;
    items[next]?.focus({ preventScroll: true });
    items[next]?.scrollIntoView?.({ block: "nearest" });
  } else if (
    e.key === "ArrowRight" &&
    node &&
    !item.hasAttribute("data-cascade-empty")
  ) {
    e.preventDefault();
    void expand(node, depth, true);
  } else if (e.key === "ArrowLeft" && depth) {
    e.preventDefault();
    void back(depth);
  }
}
function searchKey(e: KeyboardEvent) {
  if (e.key === "Enter") {
    e.preventDefault();
    return;
  }
  if (e.isComposing || composing.value || e.keyCode === 229) return;
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    focusAt(0, e.key === "ArrowUp");
  }
}
function columnScroll(depth: number) {
  if (path.value.length > depth) {
    path.value = path.value.slice(0, depth);
    void nextTick(position);
  }
}
function inside(target: EventTarget | null) {
  return (
    target instanceof Node &&
    (root.value?.contains(target) || popup.value?.contains(target))
  );
}
function outside(e: Event) {
  if (!inside(e.target)) close();
}
function focusOutside(e: Event) {
  if (!inside(e.target)) close();
}
function scrollOutside(e: Event) {
  if (!(e.target instanceof Node && popup.value?.contains(e.target))) close();
}
function resize() {
  const small = window.innerWidth < 640;
  if (small !== mobile.value) path.value = [];
  mobile.value = small;
  void nextTick(position);
}
function cleanup() {
  document.removeEventListener("pointerdown", outside);
  document.removeEventListener("focusin", focusOutside);
  window.removeEventListener("resize", resize);
  window.removeEventListener("scroll", scrollOutside, true);
  observer?.disconnect();
  observer = undefined;
}
watch(()=>[props.loading,props.loadError],()=>void nextTick(position));
watch(query, () => {
  path.value = [];
  clearHover();
  void nextTick(position);
});
watch(
  () => props.options,
  () => {
    path.value = [];
    clearHover();
    void nextTick(() => {
      position();
      if (open.value && document.activeElement === document.body)
        (search.value || trigger.value)?.focus({ preventScroll: true });
    });
  },
);
watch(
  () => props.disabled,
  (v) => {
    if (v) close();
  },
);
onBeforeUnmount(() => {
  disposed = true;
  epoch++;
  clearHover();
  cleanup();
});
</script>
<style scoped>
.cascade-tree {
  position: fixed;
  inset: 0;
  z-index: 1200;
  pointer-events: none;
}
.cascade-panel {
  pointer-events: auto;
}
.cascader-control {
  min-width: 0;
  width: 100%;
}
.cascade-value {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cascade-trigger--pill {
  border-color: transparent;
  border-radius: var(--kb-radius-full);
  background: var(--kb-select-trigger-bg);
}
.cascade-trigger--subtle {
  border-color: transparent;
  background: transparent;
}
.cascade-trigger:hover:not(:disabled) {
  background: var(--kb-select-trigger-hover);
}
.cascade-trigger:focus-visible {
  outline: 0;
  outline-offset: var(--kb-space-1);
  box-shadow: none;
}
.cascade-panel {
  z-index: 1200;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--kb-space-2);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-select-content-radius);
  background: var(--kb-menu-surface);
  color: var(--kb-foreground);
  box-shadow: var(--kb-menu-shadow);
  overflow: hidden;
}
.cascade-list {
  min-height: 0;
  overflow: auto;
  scrollbar-width: thin;
  overscroll-behavior: contain;
}
.cascade-row {
  display: flex;
  align-items: stretch;
  gap: var(--kb-space-1);
}
.cascade-row > [role="treeitem"],
.cascade-empty {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
  flex: 1;
  text-align: left;
  min-height: var(--kb-menu-item-height);
  padding: var(--kb-space-2) var(--kb-space-3);
  border-radius: var(--kb-radius-md);
  font-size: var(--kb-fs-body-sm);
}
.cascade-row > [role="treeitem"] > span {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.cascade-row small {
  display: block;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
}
.cascade-row button:hover:not(:disabled),
.cascade-empty:hover {
  background: var(--kb-menu-hover);
}
.cascade-row .is-selected {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
}
.cascade-row button:focus-visible,
.cascade-empty:focus-visible,
.cascade-mobile-head button:focus-visible {
  outline: var(--kb-field-focus-width) solid var(--kb-field-focus-indicator);
  outline-offset: calc(-1 * var(--kb-field-focus-width));
}
.cascade-parent-select {
  padding: var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  border-radius: var(--kb-radius-md);
}
.cascade-row button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cascade-empty {
  width: 100%;
  flex-shrink: 0;
}
.cascade-search {
  display:flex;align-items:center;gap:var(--kb-space-2);flex-shrink:0;
  height:calc(var(--kb-space-8) + var(--kb-space-1));
  padding:0 var(--kb-space-3);margin-bottom:var(--kb-space-2);
  border:1px solid var(--kb-border);border-radius:var(--kb-radius-md);
  background:var(--kb-menu-surface);color:var(--kb-muted-foreground);
}
.cascade-search > svg {flex-shrink:0}
.cascade-search input,
.cascade-search input:focus,
.cascade-search input:focus-visible {
  flex:1;width:0;min-width:0;height:100%;min-height:0;padding:0;
  border:0;border-radius:0;background:transparent;
  font-family:inherit;font-size:var(--kb-fs-body-sm);line-height:normal;
  color:var(--kb-foreground);box-shadow:none;outline:none;
}
.cascade-search:focus-within {
  border-color:var(--kb-field-border);
  box-shadow:none;
}
.cascade-retry {color:var(--kb-primary);padding:var(--kb-space-2);border-radius:var(--kb-radius-sm)}
.cascade-help {
  padding: var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.cascade-mobile-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--kb-space-2);
  padding: var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  border-bottom: 1px solid var(--kb-border);
}
.cascade-mobile-head button {
  display: flex;
  align-items: center;
  gap: var(--kb-space-1);
  padding: var(--kb-space-2);
  border-radius: var(--kb-radius-md);
}
.cascade-mobile-head span {
  overflow-wrap: anywhere;
}
</style>
