// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import ChapterResourceLinks from '../ChapterResourceLinks.vue'
const state=vi.hoisted(()=>({query:{chapterId:'24'} as Record<string,unknown>,chapter:vi.fn()}))
vi.mock('vue-router',()=>({useRoute:()=>({query:state.query})}))
vi.mock('@/api/interviewSeed',()=>({interviewSeedApi:{chapter:state.chapter}}))
const stubs={RouterLink:{props:['to'],template:'<a :data-to="typeof to===\'string\'?to:JSON.stringify(to)"><slot/></a>'}}
beforeEach(()=>{state.query={chapterId:'24'};state.chapter.mockReset();state.chapter.mockResolvedValue({docId:17,revision:{docId:38,knowledge:[{id:1,name:'HashMap'}],exercises:[{id:48}]}})})
describe('chapter association links',()=>{
 it('uses server relations for knowledge → document → questions and return',async()=>{const w=mount(ChapterResourceLinks,{props:{resourceKind:'knowledge',resourceId:1},global:{stubs}});await flushPromises();expect(w.text()).toContain('返回本章');expect(w.find('[data-to="/doc/38?chapterId=24"]').exists()).toBe(true);expect(w.text()).toContain('本章面试题')})
 it('rejects a forged chapter association',async()=>{const w=mount(ChapterResourceLinks,{props:{resourceKind:'code',resourceId:99},global:{stubs}});await flushPromises();expect(w.findAll('a')).toHaveLength(0);expect(w.text()).toContain('未在该章节关联')})
 it('exposes retry on an API error instead of invented links',async()=>{state.chapter.mockRejectedValueOnce(new Error('offline'));const w=mount(ChapterResourceLinks,{props:{resourceKind:'document',resourceId:38},global:{stubs}});await flushPromises();expect(w.find('[role=alert]').exists()).toBe(true);await w.get('button').trigger('click');await flushPromises();expect(w.text()).toContain('返回本章')})
 it('does not fetch without a canonical context',async()=>{state.query={chapterId:'-1'};const w=mount(ChapterResourceLinks,{props:{resourceKind:'code',resourceId:48},global:{stubs}});await flushPromises();expect(state.chapter).not.toHaveBeenCalled();expect(w.findAll('a')).toHaveLength(0)})
})
