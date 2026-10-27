// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const mocks=vi.hoisted(()=>{const data={pathId:1,revision:0,lockedChapterIds:[],deferredKeys:[],suggestions:[{key:'knowledge:7',title:'Read View',reason:'错因 #3',method:'补练',chapterId:4,sourceRefs:[{type:'ERROR_ANNOTATION',id:3}]}],chapters:[{id:4,title:'MVCC',completed:false,locked:false}],annotations:[],knowledgeNodes:[],history:[]};return{data,store:{adaptivePaths:{1:data},loaded:false,load:vi.fn().mockResolvedValue(undefined),loadAdaptivePath:vi.fn().mockResolvedValue(data)},api:{preview:vi.fn().mockRejectedValue(Error('版本冲突，请重新预览'))}}})
vi.mock('@/stores/learning',()=>({useLearningStore:()=>mocks.store}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({user:{id:1}})}))
vi.mock('@/api/adaptivePath',()=>({adaptivePathApi:mocks.api}))
vi.mock('@/components/notes/NotesMicroChallenge.vue',()=>({default:{template:'<span />'}}))
vi.mock('@/components/workflow/NextActionCard.vue',()=>({default:{template:'<span />'}}))
import AdaptivePathPanel from '../AdaptivePathPanel.vue'
it('保留真实来源和服务端版本冲突，同参数使用同一预览标识重试',async()=>{const w=mount(AdaptivePathPanel,{props:{pathId:1},global:{stubs:{RouterLink:{template:'<a><slot/></a>'}}}});await flushPromises();expect(w.text()).toContain('ERROR_ANNOTATION #3');const button=w.findAll('button').find(b=>b.text()==='预览学习顺序建议')!;await button.trigger('click');await flushPromises();expect(w.text()).toContain('版本冲突');await button.trigger('click');await flushPromises();expect(mocks.api.preview.mock.calls[0][2]).toBe(mocks.api.preview.mock.calls[1][2]);expect(w.text()).not.toContain('已完成学习');w.unmount()})
