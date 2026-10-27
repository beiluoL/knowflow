<template>
  <CascaderSelect
    :model-value="modelValue ?? null"
    :options="options"
    :disabled="disabled"
    :loading="loading"
    :load-error="loadError"
    @retry="emit('retry')"
    :placeholder="placeholder"
    :empty-label="emptyLabel"
    :label="ariaLabel"
    :variant="variant"
    @update:modelValue="
      (value) => emit('update:modelValue', value === null ? undefined : value)
    "
  />
</template>
<script setup lang="ts">
/** 保留既有 CategoryVO 和 number|undefined 合同；选父级与展开独立。 */
import { computed } from "vue";
import CascaderSelect from "./CascaderSelect.vue";
import type { CascaderOption } from "./cascader-types";
import type { CategoryVO } from "@/api/types";
import { resolveIconForRender } from "@/utils/presetIcons";
const props = withDefaults(
  defineProps<{
    categories: CategoryVO[];
    modelValue?: number | null;
    placeholder?: string;
    emptyLabel?: string;
    disabled?: boolean;
    loading?: boolean;
    loadError?: string;
    excludeIds?: number[];
    ariaLabel?: string;
    variant?: "field" | "pill" | "subtle";
  }>(),
  {
    placeholder: "请选择分类",
    emptyLabel: "作为顶级分类",
    disabled: false,
    excludeIds: () => [],
    ariaLabel: "选择分类",
    variant: "field",
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: number | undefined];
  retry: [];
}>();
const options = computed(() => {
  const seen = new Set<number>();
  function map(nodes: CategoryVO[]): CascaderOption[] {
    return nodes.flatMap((node) => {
      if (seen.has(node.id)) return [];
      seen.add(node.id);
      const icon = resolveIconForRender(node.icon || "folder");
      return [
        {
          id: node.id,
          value: node.id,
          label: node.name,
          icon: icon.name || "folder",
          color: icon.color,
          disabled: props.excludeIds.includes(node.id),
          children: node.children?.length ? map(node.children) : undefined,
        },
      ];
    });
  }
  return map(props.categories);
});
</script>
