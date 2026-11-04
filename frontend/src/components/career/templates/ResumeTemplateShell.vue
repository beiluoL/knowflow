<template>
  <article
    class="rt"
    :class="[`rt--${variant}`, { 'rt--two-col': layout === 'double' }]"
    :style="styleVars"
  >
    <header class="rt-head">
      <div class="rt-identity">
        <h1 class="rt-name">{{ doc.name }}</h1>
        <p v-if="doc.headline" class="rt-headline">{{ doc.headline }}</p>
      </div>
      <ul v-if="contactItems.length" class="rt-contact">
        <li v-for="item in contactItems" :key="item"><a v-if="item === doc.email" :href="`mailto:${item}`">{{ item }}</a><a v-else-if="item === doc.phone" :href="`tel:${item}`">{{ item }}</a><span v-else>{{ item }}</span></li>
      </ul>
    </header>

    <div class="rt-body">
      <section
        v-for="section in orderedSections"
        :key="section.key"
        class="rt-section"
        :class="`rt-section--${section.key}`"
      >
        <h2 class="rt-section-title">{{ section.label }}</h2>

        <ul v-if="section.chips.length" class="rt-chips">
          <li v-for="chip in section.chips" :key="chip" class="rt-chip">{{ chip }}</li>
        </ul>

        <div v-for="(item, index) in section.items" :key="index" class="rt-item" :class="{ 'rt-item--bold': item.textStyle === 'bold', 'rt-item--italic': item.textStyle === 'italic' }">
          <div v-if="item.title || item.subtitle || item.period" class="rt-item-head">
            <span class="rt-item-title">
              {{ item.title }}
              <span v-if="item.subtitle" class="rt-item-sub">{{ item.subtitle }}</span>
            </span>
            <span v-if="item.period" class="rt-item-period">{{ item.period }}</span>
          </div>
          <ul v-if="item.bullets.length > 1" class="rt-bullets">
            <li v-for="(line, i) in item.bullets" :key="i">{{ line }}</li>
          </ul>
          <p v-else class="rt-paragraph">{{ item.bullets[0] }}</p>
        </div>
      </section>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * 模板渲染外壳：所有模板共享「身份区 + 章节」结构，差异通过 variant 的排版规则体现。
 *
 * 为什么不是 6 个各写一遍：章节渲染逻辑（条目/要点/标签）完全一致，重复 6 份只会让
 * 分页与字体规则漂移；真正的模板差异集中在版式（单/双栏、标题样式、间距、强调色）。
 * 每个模板仍是独立组件 + 独立注册项，可单独替换实现。
 */
import { computed } from 'vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import { orderSections } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

const props = defineProps<{
  doc: ResumeDocumentViewModel
  theme: ResumeTheme
  variant: 'classic' | 'minimal' | 'backend' | 'ai' | 'modern' | 'graduate'
  layout: 'single' | 'double'
}>()

const FONT_STACKS: Record<string, string> = {
  sans: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
  serif: '"Noto Serif SC", "Songti SC", "SimSun", Georgia, serif',
  mono: '"JetBrains Mono", "SFMono-Regular", Consolas, monospace',
}

const orderedSections = computed(() =>
  orderSections(props.doc, props.theme.sectionOrder, props.theme.visibleSections),
)

const contactItems = computed(() => {
  const items: string[] = []
  if (props.doc.phone) items.push(props.doc.phone)
  if (props.doc.email) items.push(props.doc.email)
  if (props.doc.city) items.push(props.doc.city)
  return items
})

const styleVars = computed(() => ({
  '--rt-font': FONT_STACKS[props.theme.fontFamily] ?? FONT_STACKS.sans,
  '--rt-size': `${props.theme.fontSize}pt`,
  '--rt-lh': String(props.theme.lineHeight),
  '--rt-accent': props.theme.accentColor,
  '--rt-gap': `${props.theme.sectionGap}px`,
  '--rt-mt': `${props.theme.margin.top}mm`,
  '--rt-mb': `${props.theme.margin.bottom}mm`,
  '--rt-ml': `${props.theme.margin.left}mm`,
  '--rt-mr': `${props.theme.margin.right}mm`,
}))
</script>

<style scoped>
.rt {
  font-family: var(--rt-font);
  font-size: var(--rt-size);
  line-height: var(--rt-lh);
  color: var(--kf-foreground);
  width: 100%;
}
.rt-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--kf-border);
  margin-bottom: 10px;
}
.rt-name {
  margin: 0;
  font-size: calc(var(--rt-size) * 1.9);
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.rt-headline {
  margin: 2px 0 0;
  font-size: calc(var(--rt-size) * 0.95);
  color: var(--kf-muted-foreground);
}
.rt-contact {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: calc(var(--rt-size) * 0.9);
  color: var(--kf-muted-foreground);
}
.rt-body {
  display: block;
}
.rt-section {
  margin-top: var(--rt-gap);
  break-inside: auto;
}
.rt-contact a { color: inherit; text-decoration: none; }
.rt-item--bold .rt-bullets, .rt-item--bold .rt-paragraph { font-weight: 700; }
.rt-item--italic .rt-bullets, .rt-item--italic .rt-paragraph { font-style: italic; }
.rt-section:first-child {
  margin-top: 0;
}
.rt-section-title {
  margin: 0 0 6px;
  font-size: calc(var(--rt-size) * 1.02);
  line-height: 1.4;
  font-weight: 600;
  color: var(--rt-accent);
  letter-spacing: 0.06em;
}
.rt-item {
  margin-bottom: 8px;
  break-inside: avoid;
}
.rt-item:last-child {
  margin-bottom: 0;
}
.rt-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.rt-item-title {
  font-weight: 600;
  color: var(--kf-foreground);
}
.rt-item-sub {
  font-weight: 400;
  color: var(--kf-muted-foreground);
  margin-left: 6px;
}
.rt-item-period {
  font-size: calc(var(--rt-size) * 0.88);
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.rt-bullets {
  margin: 4px 0 0;
  padding-left: 16px;
}
.rt-bullets li {
  margin-bottom: 2px;
}
.rt-paragraph {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.rt-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.rt-chip {
  padding: 1px 8px;
  border-radius: var(--kf-radius-sm, 4px);
  background: color-mix(in srgb, var(--rt-accent) 10%, white);
  color: var(--kf-foreground);
}

/* ── 各模板差异：只改版式，不改内容结构 ── */
.rt--classic .rt-section-title {
  color: var(--kf-foreground);
  border-bottom: 1px solid var(--kf-border);
  padding-bottom: 2px;
}
.rt--classic .rt-head {
  border-bottom: 2px solid var(--kf-foreground);
}
.rt--classic .rt-chip {
  background: var(--kf-surface-2);
}

.rt--minimal .rt-head {
  border-bottom: 0;
  align-items: flex-start;
}
.rt--minimal .rt-name {
  font-weight: 500;
  letter-spacing: 0.18em;
}
.rt--minimal .rt-section-title {
  font-weight: 500;
  letter-spacing: 0.24em;
  color: var(--kf-foreground);
}
.rt--minimal .rt-chip {
  border: 1px solid var(--kf-border);
  background: transparent;
}

.rt--backend .rt-section-title {
  border-left: 3px solid var(--rt-accent);
  padding-left: 6px;
  letter-spacing: 0.04em;
}
.rt--backend .rt-head {
  border-bottom: 2px solid var(--rt-accent);
}
.rt--backend .rt-chip {
  background: var(--kf-primary-subtle);
  border-radius: 4px;
}

.rt--ai .rt-section-title {
  color: var(--kf-foreground);
  background: linear-gradient(90deg, color-mix(in srgb, var(--rt-accent) 16%, white), transparent);
  padding: 3px 8px;
  border-radius: var(--kf-radius-sm, 4px);
}
.rt--ai .rt-head {
  border-bottom: 1px solid color-mix(in srgb, var(--rt-accent) 45%, white);
}
.rt--ai .rt-chip {
  background: color-mix(in srgb, var(--rt-accent) 12%, white);
  border-radius: 999px;
}

.rt--modern .rt-head {
  background: var(--rt-accent);
  color: var(--kf-surface-1);
  margin: calc(var(--rt-mt) * -1) calc(var(--rt-mr) * -1) 12px calc(var(--rt-ml) * -1);
  padding: var(--rt-mt) var(--rt-mr) 10px var(--rt-ml);
  border-bottom: 0;
}
.rt--modern .rt-name {
  color: var(--kf-surface-1);
}
.rt--modern .rt-headline,
.rt--modern .rt-contact {
  color: rgba(255, 255, 255, 0.86);
}
.rt--modern.rt--two-col .rt-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.1fr);
  gap: 16px;
  align-items: start;
}
.rt--modern.rt--two-col .rt-section--skills,
.rt--modern.rt--two-col .rt-section--certificates,
.rt--modern.rt--two-col .rt-section--basics,
.rt--modern.rt--two-col .rt-section--selfIntro {
  grid-column: 1;
}
.rt--modern.rt--two-col .rt-section--experience,
.rt--modern.rt--two-col .rt-section--projects,
.rt--modern.rt--two-col .rt-section--education,
.rt--modern.rt--two-col .rt-section--intention {
  grid-column: 2;
}
.rt--modern.rt--two-col .rt-section {
  margin-top: 0;
  margin-bottom: 14px;
}
.rt--modern .rt-chip {
  background: var(--kf-surface-2);
}

.rt--graduate .rt-head {
  border-bottom: 1px solid var(--rt-accent);
}
.rt--graduate .rt-section--education {
  order: -1;
}
.rt--graduate .rt-section-title {
  color: var(--rt-accent);
  letter-spacing: 0.12em;
}
.rt--graduate .rt-chip {
  border: 1px solid color-mix(in srgb, var(--rt-accent) 35%, white);
  background: transparent;
}

@media print {
  .rt {
    color: var(--kf-foreground);
  }
  .rt-section,
  .rt-item {
    break-inside: avoid;
  }
}
</style>
