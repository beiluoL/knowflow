export type CascaderValue = string | number;
export interface CascaderOption {
  id: CascaderValue;
  value: CascaderValue;
  label: string;
  icon?: string;
  color?: string;
  description?: string;
  disabled?: boolean;
  selectable?: boolean;
  children?: readonly CascaderOption[];
}
export interface CascaderEntry {
  option: CascaderOption;
  path: readonly CascaderOption[];
}
/** 防止错误/循环输入导致递归失控；不为业务生成虚拟节点。 */
export function indexCascader(
  options: readonly CascaderOption[],
): CascaderEntry[] {
  const entries: CascaderEntry[] = [],
    seen = new Set<CascaderOption>(),
    ids = new Set<string>();
  function walk(
    items: readonly CascaderOption[],
    path: readonly CascaderOption[],
  ) {
    for (const option of items) {
      const key = typeof option.id + ":" + option.id;
      if (seen.has(option) || ids.has(key)) continue;
      seen.add(option);
      ids.add(key);
      const next = [...path, option];
      entries.push({ option, path: next });
      if (option.children?.length) walk(option.children, next);
    }
  }
  walk(options, []);
  return entries;
}
