/**
 * SFC 结构守护：禁止「游离在块之外的 CSS / 文本」。
 *
 * ── 为什么需要这条守护（2026-09-10 浏览器实测发现）──
 * `FlashCardsHub.vue` 曾在 `</script>` 之后、`<style scoped>` 之前插入了一大段 CSS
 * （`.learn-topbar` / `.learn-progress-track` / `.learn-icon-btn` 等）。
 * SFC 编译器**不会报错**（顶层块外文本被静默忽略），浏览器也**不会解析**这段样式，
 * 后果是真实可见的功能缺陷：
 *   - 进度条 `.learn-progress-track` 计算高度为 0 → 进度条完全不可见；
 *   - `.learn-topbar` 计算 `display` 退化为 `block` → 顶部控制区不成行、纵向堆叠。
 * 而当时 `vue-tsc` 与全部单测都是绿的 —— 只有浏览器里量 `getBoundingClientRect()`
 * 才暴露。本守护把「实测发现」固化为「机器可查」，杜绝同类问题复发。
 */
import { describe, it, expect } from 'vitest'
import { loadRawSourcesViaGlob } from './typography.helpers'

type BlockTag = 'template' | 'script' | 'style'

/**
 * 纯函数：返回「游离内容」的首几行（结构合法时返回空数组）。
 *
 * 实现为**逐行顶层块状态机**（而非正则切块）：
 *   - 本仓约定顶层块标签写在**第 0 列**，块内嵌套标签则带缩进；
 *   - 因此「第 0 列的 `<template|script|style>`」= 块开始，
 *     「第 0 列的 `</template|script|style>`」= 块结束；
 *   - 处于块内时整行跳过（thus 嵌套 `<template v-if>` 天然不误判）；
 *   - 块外只允许：空行、HTML 注释、块开始标签；出现其它任何文本即为游离。
 *
 * 兼容 `<template>` / `<script>` / `<style>` 三者**任意顺序**（本仓两种顺序都存在）。
 */
export function findOrphanedContent(source: string): string[] {
  const orphaned: string[] = []
  let inBlock: BlockTag | null = null
  let inComment = false

  for (const raw of source.split('\n')) {
    const atColumn0 = raw === raw.trimStart()
    const line = raw.trim()

    if (inComment) {
      if (line.includes('-->')) inComment = false
      continue
    }
    if (inBlock) {
      if (atColumn0 && new RegExp(`^</${inBlock}\\b`).test(line)) inBlock = null
      continue
    }
    if (!line) continue
    if (line.startsWith('<!--')) {
      if (!line.includes('-->')) inComment = true
      continue
    }
    const open = atColumn0 ? /^<(template|script|style)\b/.exec(line) : null
    if (open) {
      inBlock = open[1] as BlockTag
      continue
    }
    orphaned.push(line)
  }
  return orphaned.slice(0, 3)
}

/** 拼接测试用 SFC 片段，避免模板字符串里的反引号转义噪音 */
const sfc = (...lines: string[]) => lines.join('\n')

const GOOD_TEMPLATE_FIRST = sfc(
  '<template>',
  '  <div class="root" />',
  '</template>',
  '',
  '<script setup lang="ts">',
  'const a = 1',
  '</script>',
  '',
  '<style scoped>',
  '.bar { color: red; }',
  '</style>',
)

const GOOD_SCRIPT_FIRST_WITH_NESTED_TEMPLATE = sfc(
  '<script setup lang="ts">',
  'const a = 1',
  '</script>',
  '',
  '<template>',
  '  <div class="root">',
  '    <template v-if="a">',
  '      <span>ok</span>',
  '    </template>',
  '  </div>',
  '</template>',
  '',
  '<style scoped>',
  '.bar { color: red; }',
  '</style>',
)

const BAD_ORPHANED_CSS = sfc(
  '<template>',
  '  <div class="root" />',
  '</template>',
  '',
  '<script setup lang="ts">',
  'const a = 1',
  '</script>',
  '',
  '.foo {',
  '  height: 4px;',
  '}',
  '',
  '<style scoped>',
  '.bar { color: red; }',
  '</style>',
)

describe('SFC 结构守护：顶层块之外不得出现游离 CSS/文本', () => {
  const sources = loadRawSourcesViaGlob()
  const vueFiles = Object.entries(sources).filter(([p]) => p.endsWith('.vue'))

  it('至少扫描到一定数量的 .vue（防止 glob 失效导致假绿）', () => {
    expect(vueFiles.length).toBeGreaterThan(20)
  })

  it('每个 .vue 的顶层块之外只能有空行 / 注释（无游离内容）', () => {
    const offenders: string[] = []
    for (const [file, src] of vueFiles) {
      const orphaned = findOrphanedContent(src)
      if (orphaned.length > 0) {
        offenders.push(`${file} → ${orphaned.join(' | ')}`)
      }
    }
    expect(
      offenders,
      `以下 .vue 顶层块之外存在游离内容（浏览器不会解析，等于死代码）：\n${offenders.join('\n')}`,
    ).toHaveLength(0)
  })
})

describe('游离内容检测纯函数自检', () => {
  it('识别「</script> 之后、<style> 之前的裸 CSS」', () => {
    expect(findOrphanedContent(BAD_ORPHANED_CSS)).toEqual(['.foo {', 'height: 4px;', '}'])
  })

  it('template 在前的合法结构不误报', () => {
    expect(findOrphanedContent(GOOD_TEMPLATE_FIRST)).toEqual([])
  })

  it('script 在前 + 嵌套 <template v-if> 的合法结构不误报', () => {
    expect(findOrphanedContent(GOOD_SCRIPT_FIRST_WITH_NESTED_TEMPLATE)).toEqual([])
  })
})

describe('闪卡工作台顶栏：样式必须真正生效（回归守护）', () => {
  it('FlashCardsHub 的顶部条/进度条样式位于 <style> 块内且声明了可见高度', () => {
    const src = loadRawSourcesViaGlob()['views/FlashCardsHub.vue']
    expect(src, '未找到 views/FlashCardsHub.vue').toBeTruthy()

    // 结构：不得游离（否则样式不会被浏览器解析）
    expect(findOrphanedContent(src)).toEqual([])

    // 语义：进度条必须声明非 0 高度，否则会出现「有数值但看不见」的假进度条
    const trackHeight = /\.learn-progress-track\s*\{[^}]*height:\s*(\d+)px/.exec(src)
    expect(trackHeight, '.learn-progress-track 缺少 px 高度声明').not.toBeNull()
    expect(Number(trackHeight![1])).toBeGreaterThan(0)

    // 语义：顶部条必须为 flex 单行布局（否则控制区纵向堆叠）
    expect(/\.learn-topbar\s*\{[^}]*display:\s*flex/.test(src), '.learn-topbar 缺少 display:flex').toBe(true)
  })
})
