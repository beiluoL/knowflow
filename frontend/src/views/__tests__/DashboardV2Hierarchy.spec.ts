// @vitest-environment happy-dom
import { expect,it } from 'vitest'
import { parse } from '@vue/compiler-sfc'
import home from '../Home.vue?raw'
import center from '../LearningCenterPage.vue?raw'
// DOM 顺序同时决定首屏层级和键盘顺序，不使用视觉排序技巧。
it('places actual learning action before optional interview package on Home',()=>{const {descriptor}=parse(home);const template=descriptor.template!.content;expect(template.indexOf('<NextActionCard')).toBeLessThan(template.indexOf('<InterviewSeedCard'))})
it('marks center header action secondary and preserves its real handler',()=>{const {descriptor}=parse(center);const template=descriptor.template!.content;const header=template.slice(template.indexOf('<header'),template.indexOf('</header>'));expect(header).toContain('variant="secondary"');expect(header).toContain('@click="startMainAction"')})
