// @vitest-environment happy-dom
import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createRouter,createMemoryHistory} from 'vue-router'
import ExamCompose from '../ExamCompose.vue'
const api=vi.hoisted(()=>({availability:vi.fn(),compose:vi.fn(),startAttempt:vi.fn()}))
vi.mock('@/api/exam',()=>({examApi:api,TOPIC_LABELS:{HASHMAP:'HashMap'}}))
vi.mock('@/stores/aiCapability',()=>({useAiCapabilityStore:()=>({useBasicMode:true,configured:false})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),getApiError:(e:Error)=>e.message}))
beforeEach(()=>{vi.resetAllMocks();api.availability.mockResolvedValue({total:3,available:{SINGLE_CHOICE:3},sufficient:false,message:'题量不足'})})
async function open(query='') {const router=createRouter({history:createMemoryHistory(),routes:[{path:'/exam/compose',component:ExamCompose}]});await router.push('/exam/compose'+query);await router.isReady();const wrapper=mount(ExamCompose,{global:{plugins:[router],stubs:{MiniAppShell:{template:'<div><slot /></div>'}}}});await flushPromises();return wrapper}
it('NEW recipe sends exclusion to availability and changing checkbox refreshes it',async()=>{const w=await open();expect(w.findAllComponents({name:'Select'}).find(s=>s.props('ariaLabel')==='练习目的')!.props('options')).toHaveLength(10);await choosePreset(w);await new Promise(r=>setTimeout(r,400));await flushPromises();expect(api.availability).toHaveBeenLastCalledWith(expect.objectContaining({includeAnswered:false,strategy:'RANDOM'}));const checkbox=w.findAll('input[type=checkbox]').find(c=>c.element.closest('label')?.textContent?.includes('包含已作答'))!;await checkbox.setValue(true);await new Promise(r=>setTimeout(r,400));expect(api.availability).toHaveBeenLastCalledWith(expect.objectContaining({includeAnswered:true}));w.unmount()})
it('restores a permitted mistake strategy from URL',async()=>{const w=await open('?strategy=MISTAKE');expect(api.availability).toHaveBeenCalledWith(expect.objectContaining({strategy:'MISTAKE'}));w.unmount()})
it('availability failure clears old counts and exposes retry instead of pretending the bank is empty',async()=>{const w=await open();api.availability.mockRejectedValueOnce(new Error('隔离网络故障'));await choosePreset(w);await new Promise(r=>setTimeout(r,400));await flushPromises();expect(w.text()).toContain('隔离网络故障');const retry=w.findAll('button').find(b=>b.text()==='重试题量查询');expect(retry).toBeDefined();await retry!.trigger('click');await flushPromises();expect(w.text()).not.toContain('隔离网络故障');w.unmount()})

it('configuration selectors keep field layout and do not compose or start an attempt',async()=>{const w=await open();const selects=w.findAllComponents({name:'Select'});expect(selects).toHaveLength(3);expect(selects.every(s=>s.props('panelWidth')===240)).toBe(true);const strategy=selects.find(s=>s.props('ariaLabel')==='组卷策略')!;strategy.vm.$emit('update:modelValue','WEAK');await new Promise(r=>setTimeout(r,400));await flushPromises();expect(api.availability).toHaveBeenLastCalledWith(expect.objectContaining({strategy:'WEAK'}));expect(api.compose).not.toHaveBeenCalled();expect(api.startAttempt).not.toHaveBeenCalled();w.unmount()})

async function choosePreset(w: Awaited<ReturnType<typeof open>>) { const s=w.findAllComponents({name:'Select'}).find(s=>s.props('ariaLabel')==='练习目的')!;s.vm.$emit('update:modelValue','NEW');await flushPromises();s.vm.$emit('change',{value:'NEW'});await flushPromises() }
