/**
 * 从 Icon.vue 原始源码提取所有 `name === 'xxx'` 定义的图标名集合。
 * 纯函数，便于测试复用且不依赖 node 类型。
 */
export function componentsUiIcons(iconVueSrc: string): Set<string> {
  const set = new Set<string>()
  const re = /name\s*===\s*['"]([A-Za-z0-9_-]+)['"]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(iconVueSrc))) set.add(m[1])
  return set
}
