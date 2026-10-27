// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FlashcardExchangeDialog from '../FlashcardExchangeDialog.vue'
const api = vi.hoisted(() => ({ preview: vi.fn(), confirm: vi.fn(), export: vi.fn() }))
vi.mock('@/api/learning', () => ({ learningApi: { previewFlashcardImport: api.preview, confirmFlashcardImport: api.confirm, exportFlashcardContent: api.export } }))
const preview = { headers: ['front','back'], warnings: [], counts: {NEW:1,DUPLICATE:0,CONFLICT:0,INVALID:0}, cardCount:1, token:'signed', rows:[{line:2,status:'NEW',message:'新增',cards:1,note:{front:'Question',back:'Answer',type:'BASIC'}}] }
function start(){ return mount(FlashcardExchangeDialog, { props: { mode:'import' }, global:{stubs:{Teleport:true}} }) }
describe('content import confirmation',()=>{
 beforeEach(()=>{vi.clearAllMocks();api.preview.mockResolvedValue(preview);api.confirm.mockResolvedValue({insertedNotes:1,insertedCards:1,skipped:0,noteIds:[1]})})
 it('requires preview before any write and shows saved result',async()=>{
  const w=start();expect(w.find('[data-testid=import-confirm]').exists()).toBe(false)
  await w.get('textarea').setValue('front,back\nQuestion,Answer');await w.get('[data-testid=import-preview]').trigger('click');await flushPromises()
  expect(api.confirm).not.toHaveBeenCalled();expect(w.text()).toContain('Question')
  await w.get('[data-testid=import-confirm]').trigger('click');await flushPromises()
  expect(api.confirm).toHaveBeenCalledWith(expect.objectContaining({token:'signed',skipProblems:false}));expect(w.text()).toContain('已保存 1 条笔记');expect(w.emitted('saved')).toBeTruthy()
 })
 it('invalidates preview when content changes',async()=>{
  const w=start();await w.get('textarea').setValue('front,back\na,b');await w.get('[data-testid=import-preview]').trigger('click');await flushPromises()
  await w.get('textarea').setValue('front,back\na,changed');expect(w.find('[data-testid=import-confirm]').exists()).toBe(false)
 })
 it('keeps same token for retry after transport failure',async()=>{
  api.confirm.mockRejectedValueOnce(new Error('网络中断'));const w=start();await w.get('textarea').setValue('front,back\na,b');await w.get('[data-testid=import-preview]').trigger('click');await flushPromises()
  await w.get('[data-testid=import-confirm]').trigger('click');await flushPromises();expect(w.text()).toContain('网络中断')
  await w.get('[data-testid=import-confirm]').trigger('click');await flushPromises();expect(api.confirm.mock.calls[0][0]).toEqual(api.confirm.mock.calls[1][0])
 })
 it('ignores a slow preview when the user has changed its input',async()=>{
  let done: (v:unknown)=>void=()=>{};api.preview.mockReturnValue(new Promise(r=>{done=r}));const w=start()
  await w.get('textarea').setValue('front,back\na,b');await w.get('[data-testid=import-preview]').trigger('click')
  await w.get('textarea').setValue('front,back\na,new');done(preview);await flushPromises();expect(w.find('[data-testid=import-confirm]').exists()).toBe(false)
 })
 it('offers field mapping before a server preview exists',async()=>{
  const w=start();await w.get('textarea').setValue('问题,答案\na,b');await flushPromises()
  const selects=w.findAll('.mapping-grid select');expect(selects.length).toBeGreaterThan(0)
  await selects[0].setValue('0');await selects[1].setValue('1');await w.get('[data-testid=import-preview]').trigger('click');await flushPromises()
  expect(api.preview).toHaveBeenCalledWith(expect.objectContaining({mapping:{front:0,back:1}}))
 })
 it('does not overwrite newer input with a delayed file read',async()=>{
  let done:(v:ArrayBuffer)=>void=()=>{};const w=start();const file={name:'old.csv',size:25,arrayBuffer:()=>new Promise<ArrayBuffer>(r=>{done=r})}
  const input=w.get('input[type=file]');Object.defineProperty(input.element,'files',{value:[file],configurable:true});await input.trigger('change')
  await w.get('textarea').setValue('front,back\nnew,answer');done(new TextEncoder().encode('front,back\nold,answer').buffer);await flushPromises()
  expect((w.get('textarea').element as HTMLTextAreaElement).value).toContain('new,answer')
 })
})
