// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {ref} from 'vue'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import Voice from '../InterviewVoiceAnswer.vue'
import * as api from '@/api/interviewV2'
vi.mock('@/composables/useInterviewRecorder',()=>({useInterviewRecorder:()=>({state:ref('ready'),blob:ref(new Blob(['audio'])),seconds:ref(1),volume:ref(0),error:ref(''),discard:vi.fn(),start:vi.fn(),stop:vi.fn(),pause:vi.fn(),resume:vi.fn()})}))
vi.mock('@/api/interviewV2',()=>({getSpeechCapability:vi.fn().mockResolvedValue({enabled:true,maxBytes:100000,maxDurationSeconds:120,supportedFormats:['audio/wav']}),uploadSpeech:vi.fn().mockResolvedValue({id:5,status:'TRANSCRIBED',questionId:'q',rawText:'raw words'})}))
describe('voice confirmation boundary',()=>{
 beforeEach(()=>vi.clearAllMocks())
 it('allocates a new key only after a known failure and explicit retry',async()=>{
  vi.mocked(api.uploadSpeech).mockResolvedValueOnce({id:6,status:'FAILED',questionId:'q',rawText:'',error:'service unavailable'}).mockResolvedValueOnce({id:7,status:'TRANSCRIBED',questionId:'q',rawText:'recovered'})
  const w=mount(Voice,{props:{sessionId:1,questionId:'q'}});await flushPromises();await w.findAll('button').find(b=>b.text()==='转写录音')!.trigger('click');await flushPromises();expect(api.uploadSpeech).toHaveBeenCalledTimes(1);expect(w.emitted('confirm')).toBeUndefined();
  await w.findAll('button').find(b=>b.text()==='重试转写')!.trigger('click');await flushPromises();expect(vi.mocked(api.uploadSpeech).mock.calls[1]![3]).not.toBe(vi.mocked(api.uploadSpeech).mock.calls[0]![3]);expect(w.text()).toContain('recovered');expect(w.emitted('confirm')).toBeUndefined();w.unmount()
 })
 it.each(['network','TRANSCRIBING'])('reuses the key after uncertain %s without automatic retries',async(status)=>{
  const call=vi.mocked(api.uploadSpeech);if(status==='network')call.mockRejectedValueOnce(new Error('network'));else call.mockResolvedValueOnce({id:6,status:'TRANSCRIBING',questionId:'q',rawText:''});call.mockResolvedValueOnce({id:7,status:'TRANSCRIBED',questionId:'q',rawText:'done'});
  const w=mount(Voice,{props:{sessionId:1,questionId:'q'}});await flushPromises();await w.findAll('button').find(b=>b.text()==='转写录音')!.trigger('click');await flushPromises();expect(call).toHaveBeenCalledTimes(1);await w.findAll('button').find(b=>b.text()===(status==='network'?'转写录音':'重试转写'))!.trigger('click');await flushPromises();expect(call.mock.calls[1]![3]).toBe(call.mock.calls[0]![3]);w.unmount()
 })
 it('retains raw transcription independently and only emits edited words on explicit confirmation',async()=>{const w=mount(Voice,{props:{sessionId:1,questionId:'q'}});await flushPromises();await w.findAll('button').find(b=>b.text()==='转写录音')!.trigger('click');await flushPromises();expect(w.emitted('confirm')).toBeUndefined();await w.find('textarea').setValue('corrected words');expect(w.text()).toContain('raw words');await w.findAll('button').find(b=>b.text()==='确认文字，填入回答')!.trigger('click');expect(w.emitted('confirm')?.[0]).toEqual(['corrected words',5]);expect(api.uploadSpeech).toHaveBeenCalledTimes(1);w.unmount()})
})

it('distinguishes loading capability from a failed capability request',async()=>{let resolve!:(v:api.SpeechCapability)=>void;vi.mocked(api.getSpeechCapability).mockImplementationOnce(()=>new Promise(r=>resolve=r));const w=mount(Voice,{props:{sessionId:1,questionId:'q'}});await flushPromises();expect(w.text()).toContain('正在检查语音能力');expect(w.text()).not.toContain('重试语音能力');resolve({enabled:false,supportedFormats:[],maxBytes:0,maxDurationSeconds:0,reason:'语音未启用'});await flushPromises();expect(w.text()).toContain('语音未启用');w.unmount()})
