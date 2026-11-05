// @vitest-environment happy-dom
import { beforeEach,it,expect,vi } from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
const api=vi.hoisted(()=>({getConfig:vi.fn(),modelCenter:vi.fn(),platformModels:vi.fn(),saveConfig:vi.fn(),testConnection:vi.fn(),deleteAll:vi.fn()}))
vi.mock('@/api/aiConfig',()=>({aiConfigApi:api}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),confirmDialog:vi.fn()}))
const cap=vi.hoisted(()=>({status:{status:'DISABLED',enabled:false},userEnabled:true,aiAvailable:false,error:'',loading:false,refresh:vi.fn()}))
vi.mock('@/stores/aiCapability',()=>({useAiCapabilityStore:()=>cap}))
const auth = vi.hoisted(() => ({ isAdmin: false }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
import Config from '../ChatConfig.vue'
beforeEach(()=>{vi.clearAllMocks();auth.isAdmin=false;cap.status={status:'DISABLED',enabled:false};api.getConfig.mockResolvedValue(null);api.platformModels.mockResolvedValue([])})
const render=()=>mount(Config,{global:{stubs:{Icon:true}}})
it('加载失败可重试，不能作为空配置保存',async()=>{api.getConfig.mockRejectedValueOnce(new Error('offline'));const w=render();await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(true);expect(w.findAll('button').some(b=>b.text()==='保存配置')).toBe(false);await w.get('.kf-state-retry').trigger('click');await flushPromises();expect(w.text()).toContain('保存配置');w.unmount()})
it('保存同步锁定且失败保留私有输入',async()=>{let fail!:(e:Error)=>void;api.saveConfig.mockImplementation(()=>new Promise((_,r)=>fail=r));const w=render();await flushPromises();const vm=w.vm as unknown as {form:{apiKey:string};handleSave:()=>Promise<void>};vm.form.apiKey='isolated-test-input';const one=vm.handleSave(),two=vm.handleSave();expect(api.saveConfig).toHaveBeenCalledTimes(1);fail(new Error('offline'));await Promise.all([one,two]);await flushPromises();expect(vm.form.apiKey).toBe('isolated-test-input');expect(w.find('[role="alert"]').exists()).toBe(true);w.unmount()})
it('系统不可用时不发起连通性请求',async()=>{const w=render();await flushPromises();await (w.vm as unknown as {handleTest:()=>Promise<void>}).handleTest();expect(api.testConnection).not.toHaveBeenCalled();w.unmount()})

it('系统开启但尚未配置时允许测试新的配置',async()=>{cap.status={status:'UNCONFIGURED',enabled:true};api.testConnection.mockResolvedValue({success:false,message:'本地测试返回',elapsedMs:0});const w=render();await flushPromises();await (w.vm as unknown as {handleTest:()=>Promise<void>}).handleTest();expect(api.testConnection).toHaveBeenCalledTimes(1);w.unmount()})

it('仅管理员读取模型目录，并区分上报和估算用量', async () => {
  const regular = render()
  await flushPromises()
  expect(api.modelCenter).not.toHaveBeenCalled()
  regular.unmount()

  auth.isAdmin = true
  api.modelCenter.mockResolvedValue({
    models: [{ provider: 'test', label: '本地测试', model: 'example', defaultModel: true }],
    usage: { day: '2026-11-05', reportedTokens: 120, estimatedTokens: 50,
      unreportedCalls: 1, unpricedTokens: 20, pricedCostUsd: 0.002, dailyTokenLimit: 1000 },
  })
  const admin = render()
  await flushPromises()
  expect(api.modelCenter).toHaveBeenCalledOnce()
  expect(admin.text()).toContain('上报 Token120')
  expect(admin.text()).toContain('估算 Token50')
  expect(admin.text()).toContain('本地测试 · example')
  admin.unmount()
})
