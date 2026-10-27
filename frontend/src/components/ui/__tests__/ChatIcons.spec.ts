// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { expect,it } from 'vitest'
import Icon from '../Icon.vue'
it('draws the up arrow with its shaft ending at the arrowhead instead of extending above it',()=>{const w=mount(Icon,{props:{name:'arrow-up'}});expect(w.findAll('path').map(p=>p.attributes('d'))).toEqual(['M12 19V5','m5 12 7-7 7 7']);w.unmount()})
it('renders the assistant entry as an SVG speech bubble and sparkle with currentColor',()=>{const w=mount(Icon,{props:{name:'message-square-sparkles',size:18}});expect(w.get('svg').attributes('viewBox')).toBe('0 0 24 24');expect(w.get('svg').attributes('stroke')).toBe('currentColor');expect(w.findAll('path')).toHaveLength(2);expect(w.find('i').exists()).toBe(false);w.unmount()})
