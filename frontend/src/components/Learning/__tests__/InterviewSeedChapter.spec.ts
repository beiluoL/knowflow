// @vitest-environment happy-dom
import {beforeEach,expect,it,vi} from 'vitest'
import {flushPromises,mount} from '@vue/test-utils'
const state=vi.hoisted(()=>({auth:{isLoggedIn:false,isAdmin:false,user:null as {id:number}|null},chapter:vi.fn(),upgrade:vi.fn(),dryRun:vi.fn(),importRebuild:vi.fn()}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>state.auth}))
vi.mock('@/api/interviewSeed',()=>({interviewSeedApi:{chapter:state.chapter,upgradeCollections:state.upgrade,rebuildDryRun:state.dryRun,rebuildImport:state.importRebuild}}))
import InterviewSeedChapter from '../InterviewSeedChapter.vue'
beforeEach(()=>{vi.resetAllMocks();state.auth.isLoggedIn=false;state.auth.isAdmin=false;state.auth.user=null;state.chapter.mockResolvedValue(null)})
it('never causes an authentication redirect for anonymous public chapter readers',async()=>{const w=mount(InterviewSeedChapter,{props:{chapterId:4}});await flushPromises();expect(state.chapter).not.toHaveBeenCalled();expect(w.text()).toBe('');w.unmount()})
it('renders real resource links and interview answers without submitting evidence',async()=>{
state.auth.isLoggedIn=true;state.auth.user={id:77};state.chapter.mockResolvedValue({chapterId:4,docId:5,docTitle:'线程池材料',questions:[{id:7,title:'execute流程？',answer:'core → queue → max → reject',explanation:'有界队列'}],exercise:{id:8,title:'安全线程池',description:'人工检查',solutionHint:'考虑DB连接池'},notes:[{id:9,title:'线程池速记',content:'## 监控\n拒绝次数'}]});
const w=mount(InterviewSeedChapter,{props:{chapterId:4},global:{stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot /></a>'}}}});await flushPromises();expect(w.text()).toContain('execute流程');expect(w.find('a[href="/learning/flashcards?chapterId=4"]').exists()).toBe(true);expect(w.find('a[href="/learning/code-practice/8?chapterId=4"]').exists()).toBe(true);expect(w.text()).toContain('线程池速记');w.unmount()
})
it('publishes versioned reading content and real exercise links without replacing old resources',async()=>{
state.auth.isLoggedIn=true;state.auth.user={id:77};state.chapter.mockResolvedValue({chapterId:4,docId:5,docTitle:'Old',questions:[],exercise:null,notes:[],upgradeAvailable:true,revision:{version:'collections-depth-v1',content:'## Source\nDeep reading',docId:50,docTitle:'Deep',questions:[{id:70,title:'Why resize?',answer:'Masks',explanation:'Boundary',canonicalKey:'resize',parentKey:null}],exercises:[{id:80,title:'Mutable key lab',description:'Java17',codeTemplate:'public class Main {}',solutionHint:'Verify'}],knowledge:[],notes:[],flashcardCount:8}});
const w=mount(InterviewSeedChapter,{props:{chapterId:4},global:{stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot /></a>'}}}});await flushPromises();expect(w.emitted('revision')?.at(-1)?.[0]).toMatchObject({content:'## Source\nDeep reading'});expect(w.find('a[href="/learning/code-practice/80?chapterId=4"]').exists()).toBe(true);expect(w.find('a[href="/doc/50?chapterId=4"]').exists()).toBe(true);expect(w.text()).toContain('8');w.unmount()
})
it('failed upgrade retains the original training resources and offers retry',async()=>{
state.auth.isLoggedIn=true;state.auth.user={id:77};state.chapter.mockResolvedValue({chapterId:4,docId:5,docTitle:'Old',questions:[{id:7,title:'Original question',answer:'Original',explanation:''}],exercise:null,notes:[],upgradeAvailable:true,revision:null});state.upgrade.mockRejectedValue(new Error('offline'));
const w=mount(InterviewSeedChapter,{props:{chapterId:4},global:{stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot /></a>'}}}});await flushPromises();await w.find('button').trigger('click');await flushPromises();expect(w.text()).toContain('Original question');expect(w.text()).toContain('升级未完成');expect(w.emitted('revision')?.at(-1)?.[0]??null).toBeNull();w.unmount()
})
it('does not import when the rebuild dry run fails and keeps old content',async()=>{
 state.auth.isLoggedIn=true;state.auth.user={id:77};
 state.chapter.mockResolvedValue({chapterId:4,docId:5,questions:[],notes:[],rebuildAvailable:true,revision:null});
 state.dryRun.mockRejectedValue(new Error('missing relation'));
 const w=mount(InterviewSeedChapter,{props:{chapterId:4},global:{stubs:{RouterLink:true}}});await flushPromises();
 await w.get('button').trigger('click');await flushPromises();
 expect(state.importRebuild).not.toHaveBeenCalled();expect(w.text()).toContain('启用未完成');
 expect(w.get('button').attributes('disabled')).toBeUndefined();w.unmount();
})
it('validates then imports and refreshes the actual chapter revision',async()=>{
 state.auth.isLoggedIn=true;state.auth.user={id:77};
 const old={chapterId:4,docId:5,questions:[],notes:[],rebuildAvailable:true,revision:null};
 state.chapter.mockResolvedValueOnce(old).mockResolvedValue({...old,revision:{version:'java-ai-content-rebuild-v1',content:'## Deep',docId:50,questions:[],exercises:[],knowledge:[],notes:[],flashcardCount:10}});
 state.dryRun.mockResolvedValue({dryRun:true});state.importRebuild.mockResolvedValue({dryRun:false});
 const w=mount(InterviewSeedChapter,{props:{chapterId:4},global:{stubs:{RouterLink:true}}});await flushPromises();
 await w.get('button').trigger('click');await flushPromises();
 expect(state.dryRun).toHaveBeenCalledOnce();expect(state.importRebuild).toHaveBeenCalledOnce();
 expect(state.dryRun.mock.invocationCallOrder[0]).toBeLessThan(state.importRebuild.mock.invocationCallOrder[0]!);
 expect(w.text()).toContain('java-ai-content-rebuild-v1');expect(w.text()).not.toContain('校验并启用五章');w.unmount();
})
