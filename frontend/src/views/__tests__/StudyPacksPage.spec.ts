// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi,beforeEach} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn(),detail:vi.fn(),retry:vi.fn(),upload:vi.fn(),saveCards:vi.fn(),accept:vi.fn()}))
vi.mock('@/api/studyPack',()=>({studyPackApi:api}))
const routing=vi.hoisted(()=>({route:null as unknown as {params:{id:string}}}))
vi.mock('vue-router',async()=>{const {reactive}=await import('vue');routing.route=reactive({params:{id:'7'}});return {useRoute:()=>routing.route,useRouter:()=>({push:vi.fn()})}})
beforeEach(()=>{routing.route.params.id='7';vi.clearAllMocks()})
import StudyPacksPage from '../StudyPacksPage.vue'
it('部分成功保留笔记与草稿，仅重试失败步骤，错误不伪装空内容',async()=>{
 const pack={id:7,fileName:'course.txt',state:'PARTIAL_READY',revision:1,steps:[{kind:'NOTE',state:'SUCCEEDED',runCount:1},{kind:'CARDS',state:'FAILED',errorMessage:'模型超时',runCount:1}],events:[],note:{id:3,title:'已成功笔记',content:'## 已有内容'},cardDrafts:[],flashcardIds:[]}
 api.list.mockResolvedValue([pack]);api.detail.mockResolvedValue(pack);api.retry.mockRejectedValue(Error('重试失败'))
 const w=mount(StudyPacksPage,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'},ChatMarkdownRenderer:{props:['content'],template:'<pre>{{content}}</pre>'},NotesPrivateQuiz:true,NotesTutor:true}}});await flushPromises()
 expect(w.text()).toContain('已成功笔记');expect(w.text()).toContain('模型超时');await w.get('button[data-retry="CARDS"]').trigger('click');await flushPromises();expect(api.retry).toHaveBeenCalledWith(7,'CARDS');expect(w.text()).toContain('重试失败');expect(w.text()).toContain('已有内容');w.unmount()
})

it('切换学习包后，旧包保存返回不能覆盖新包',async()=>{
 const cardDrafts=Array.from({length:5},(_,i)=>({front:`问题${i}`,back:`答案${i}`}));
 const a={id:7,fileName:'课程A.txt',state:'DRAFT_READY',revision:1,steps:[],events:[],cardDrafts,flashcardIds:[],note:{id:3,title:'A笔记',content:'## A'}};
 const b={...a,id:8,fileName:'课程B.txt',cardDrafts:[],note:{id:4,title:'B笔记',content:'## B'}};
 let resolveSave!:(value:unknown)=>void;api.list.mockResolvedValue([a,b]);api.detail.mockImplementation((id:number)=>Promise.resolve(id===7?a:b));api.saveCards.mockImplementation(()=>new Promise(resolve=>{resolveSave=resolve}));
 const w=mount(StudyPacksPage,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'},ChatMarkdownRenderer:{props:['content'],template:'<pre>{{content}}</pre>'},NotesPrivateQuiz:true,NotesTutor:true}}});await flushPromises();
 await w.findAll('button').find(btn=>btn.text()==='保存闪卡草稿')!.trigger('click');routing.route.params.id='8';await flushPromises();expect(w.text()).toContain('B笔记');
 resolveSave({...a,note:{...a.note,title:'旧包保存返回'}});await flushPromises();expect(w.text()).toContain('B笔记');expect(w.text()).not.toContain('旧包保存返回');w.unmount()
})

it('阅读侧栏打开时只显示一个导师，关闭后重新读取课程导师',async()=>{
 vi.stubEnv('VITE_PRODUCT_TUTOR_COACHING','true');vi.resetModules();const Page=(await import('../StudyPacksPage.vue')).default
 const pack={id:7,fileName:'课程.txt',state:'DRAFT_READY',revision:1,steps:[],events:[],cardDrafts:[],flashcardIds:[],conversationId:42,note:{id:3,title:'课程笔记',content:'Read View',revision:1}}
 api.list.mockResolvedValue([pack]);api.detail.mockResolvedValue(pack)
 const w=mount(Page,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'},ChatMarkdownRenderer:true,NotesTutor:{template:'<div data-base-tutor>课程导师</div>'},NotesReadingTutor:{emits:['openChange'],template:'<div><slot/><button data-open-reading @click="$emit(\'openChange\',true)">打开阅读导师</button><button data-close-reading @click="$emit(\'openChange\',false)">关闭阅读导师</button></div>'}}}});await flushPromises();expect(w.findAll('[data-base-tutor]')).toHaveLength(1);await w.get('[data-open-reading]').trigger('click');expect(w.findAll('[data-base-tutor]')).toHaveLength(0);await w.get('[data-close-reading]').trigger('click');expect(w.findAll('[data-base-tutor]')).toHaveLength(1);w.unmount();vi.unstubAllEnvs()
})
