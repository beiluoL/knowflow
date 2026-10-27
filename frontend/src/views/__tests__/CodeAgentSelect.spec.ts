// @vitest-environment happy-dom
import { shallowMount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import CodeAgent from '../CodeAgent.vue'
import {confirmDialog} from '@/utils/toast'
const api = vi.hoisted(() => ({
  models: vi.fn().mockResolvedValue({ userModels: [{ id: 7, provider: 'cloud', isActive: 1 }], platformModels: [{ provider: 'cloud', label: 'Cloud', providerType: 'CLOUD', baseUrl: '/cloud', defaultModel: 'model-a' }, { provider: 'local', label: 'Local', providerType: 'LOCAL', baseUrl: '/local', defaultModel: 'model-b' }] }),
  pageSessions: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  deleteModel: vi.fn(),
  saveConfig: vi.fn(), getConfig: vi.fn().mockResolvedValue(null),
}))
vi.mock('@/api', () => ({ codeAgentApi: { models: api.models, pageSessions: api.pageSessions }, aiConfigApi: { saveConfig: api.saveConfig }, ollamaApi: { getConfig: api.getConfig, deleteModel: api.deleteModel }, msg: vi.fn() }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), confirmDialog: vi.fn(), promptDialog: vi.fn(), getApiError: (_e: unknown, fallback: string) => fallback }))
it('preserves grouped providers, fills dependent defaults only when empty, and saves only on explicit action', async () => {
  const w = shallowMount(CodeAgent)
  await flushPromises()
  const vm = w.vm as unknown as { showConfigModal: boolean; configForm: { provider: string; baseUrl: string; model: string }; selectedConfigId: number | null }
  const model = w.findAllComponents({ name: 'Select' }).find(s => s.props('ariaLabel') === '当前对话模型')!
  model.vm.$emit('update:modelValue', 7)
  await flushPromises()
  expect(vm.selectedConfigId).toBe(7)
  expect(typeof vm.selectedConfigId).toBe('number')
  vm.showConfigModal = true
  await flushPromises()
  const provider = w.findAllComponents({ name: 'Select' }).find(s => s.props('ariaLabel') === '模型提供商')!
  expect(provider.props('options')).toEqual(expect.arrayContaining([expect.objectContaining({ value: 'cloud', group: '云端' }), expect.objectContaining({ value: 'local', group: '本地（OpenAI 兼容）' })]))
  provider.vm.$emit('update:modelValue', 'local')
  await flushPromises()
  expect(vm.configForm).toMatchObject({ provider: 'local', baseUrl: '/local', model: 'model-b' })
  provider.vm.$emit('update:modelValue', 'cloud')
  await flushPromises()
  expect(vm.configForm).toMatchObject({ provider: 'cloud', baseUrl: '/local', model: 'model-b' })
  expect(api.saveConfig).not.toHaveBeenCalled()
  w.unmount()
})

it('keeps an explicit Ollama deletion rejection in the confirmation retry path',async()=>{
  api.deleteModel.mockResolvedValueOnce({ok:false,error:'模型仍被使用'})
  vi.mocked(confirmDialog).mockImplementationOnce(async(_message,options)=>{await options?.onConfirm?.(true);return true})
  const w=shallowMount(CodeAgent);await flushPromises()
  await expect((w.vm as unknown as {deleteOllamaModel:(name:string)=>Promise<void>}).deleteOllamaModel('busy-model')).rejects.toThrow('模型仍被使用')
  w.unmount()
})
