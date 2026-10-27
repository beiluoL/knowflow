// @vitest-environment happy-dom
import {describe,it,expect,vi} from 'vitest'
import {mount} from '@vue/test-utils'
import ScenarioPreview from '../ScenarioPreview.vue'
import {scenarioLinks,scenarioStates} from '../scenarios-v6'
describe('V6 scenario isolation',()=>{
 it('renders all five explicit states for all seven fixtures without requests',async()=>{const fetch=vi.spyOn(globalThis,'fetch');for(const scene of scenarioLinks)for(const state of scenarioStates){const w=mount(ScenarioPreview,{props:{name:scene.id,fixture:{scenarioState:state}},global:{stubs:{Markdown:true,ExecutionTimeline:true,ToolCard:true}}});expect(w.text()).toContain('Demo 数据');if(state==='loading')expect(w.get('[role=status]').text()).toContain('加载');if(state==='empty')expect(w.text()).toContain('暂无');if(state==='error'){expect(w.get('[role=alert]').text()).toContain('失败');await w.findAll('button').find(b=>b.text()==='重试场景')!.trigger('click');expect(w.emitted('event')).toContainEqual(['fixture-retry',scene.id])}if(state==='disabled')expect(w.find('[inert]').exists()).toBe(true);w.unmount()}expect(fetch).not.toHaveBeenCalled();fetch.mockRestore()})
 it('stops AI stream interval when leaving a story',async()=>{vi.useFakeTimers();const w=mount(ScenarioPreview,{props:{name:'AIChat'},global:{stubs:{Markdown:true,AIComposer:true}}});await w.findAll('button').find(b=>b.text()==='模拟流式回答')!.trigger('click');expect(vi.getTimerCount()).toBe(1);w.unmount();expect(vi.getTimerCount()).toBe(0);vi.useRealTimers()})
})
