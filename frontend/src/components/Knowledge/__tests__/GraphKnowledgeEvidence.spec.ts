// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({detail:vi.fn()}))
vi.mock('@/api/graphInsight',()=>({graphInsightApi:api}))
vi.mock('@/components/notes/NotesMicroChallenge.vue',()=>({default:{template:'<span />'}}))
import GraphKnowledgeEvidence from '../GraphKnowledgeEvidence.vue'
it('来源切换不展示迟到原答，失败显示真实错误并可重试',async()=>{let finish!:(v:unknown)=>void;api.detail.mockImplementationOnce(()=>new Promise(r=>finish=r)).mockRejectedValueOnce(Error('来源已撤权'));const w=mount(GraphKnowledgeEvidence,{props:{knowledgeId:1}});await w.setProps({knowledgeId:2});await flushPromises();expect(w.text()).toContain('来源已撤权');finish({mastery:{reason:'旧私有内容'},evidence:{items:[]},practiceNotes:[]});await flushPromises();expect(w.text()).not.toContain('旧私有内容');api.detail.mockResolvedValue({mastery:{reason:'没有信号'},evidence:{items:[]},practiceNotes:[]});await w.find('button').trigger('click');await flushPromises();expect(w.text()).toContain('不按名称相似度补造关联');w.unmount()})
