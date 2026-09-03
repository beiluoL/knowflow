/**
 * UI/UX 3.3.2 图标清零回归测试
 *
 * 扫描全站所有 `<Icon ... name="字面量" ...>` 静态用法，断言每个字面量图标名：
 *   - 在 Icon.vue 的 `name === 'xxx'` 定义集合内（系统 SVG 图标），或
 *   - 以 `icon-` 开头（走 Icon.vue 的 iconfont 分支，非空白）。
 *
 * 目的：杜绝「未定义图标名 → 渲染空白方块」的回归。
 * 任何新增 `<Icon name="新名">` 若未加入 Icon.vue 且非 iconfont，本测试会失败。
 *
 * 使用 Vite 原生 `?raw` 与 `import.meta.glob`，避免依赖 node 类型。
 */
import { describe, it, expect } from 'vitest'
import iconVueSrc from '@/components/ui/Icon.vue?raw'
import { componentsUiIcons } from './icon-usage.helpers'

describe('icon usage: 所有静态 <Icon name> 必须已定义或走 iconfont', () => {
  const defined = componentsUiIcons(iconVueSrc)
  const usages = collectStaticIconNames()

  it(`Icon.vue 定义数应 >= 200（健全性检查，防止误删）`, () => {
    expect(defined.size).toBeGreaterThanOrEqual(200)
  })

  it(`共发现 ${usages.length} 处静态 <Icon name> 用法，全部合法`, () => {
    const bad: string[] = []
    for (const u of usages) {
      const ok = defined.has(u.name) || u.name.startsWith('icon-')
      if (!ok) bad.push(`${u.file}:${u.line}  name="${u.name}"`)
    }
    expect(bad, `以下图标名未定义（会渲染空白方块）：\n${bad.join('\n')}`).toEqual([])
  })
})

function collectStaticIconNames(): { name: string; file: string; line: number }[] {
  // 全站源码原始内容（排除本测试自身不影响：其无静态 <Icon name>）
  const modules = import.meta.glob('/src/**/*.{vue,ts,js,tsx,jsx}', {
    query: '?raw',
    import: 'default',
    eager: true,
  }) as Record<string, string>
  const out: { name: string; file: string; line: number }[] = []
  for (const [file, src] of Object.entries(modules)) {
    const lines = src.split('\n')
    lines.forEach((line: string, i: number) => {
      if (!/<Icon\b/.test(line)) return
      const re = /(?:\s)name\s*=\s*['"]([A-Za-z0-9_-]+)['"]/g
      let mm: RegExpExecArray | null
      while ((mm = re.exec(line))) {
        out.push({ name: mm[1], file, line: i + 1 })
      }
    })
  }
  return out
}
