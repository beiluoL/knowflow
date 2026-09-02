/**
 * 第三方插件 markdown-it-task-lists 的类型声明垫片。
 *
 * Phase UI-1 修复说明：该包以纯 JS 发布、未附带 .d.ts，
 * 在 strict 模式下触发 TS7016（隐式 any），导致 `vue-tsc -b` 构建失败。
 * 这里按官方用法（md.use(taskLists)）声明最小可用签名，不改动运行时行为。
 */
declare module 'markdown-it-task-lists' {
  import type { PluginSimple } from 'markdown-it'

  interface TaskListsOptions {
    /** 是否启用复选框渲染，默认 true */
    enabled?: boolean
    /** 是否给 <ul> 加 contains-task-list 类，默认 true */
    label?: boolean
    /** 是否把复选框放在 <label> 内，默认 true */
    labelAfter?: boolean
  }

  const taskLists: PluginSimple | ((md: unknown, options?: TaskListsOptions) => void)
  export default taskLists
}
