// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
import Panel from '../EvidenceReportPanel.vue'
const mocks=vi.hoisted(()=>({get:vi.fn(),entityGraph:vi.fn()}))
vi.mock('@/api/evidenceReport',()=>({evidenceReportApi:mocks}))
vi.mock('@/api',()=>({knowledgeApi:{entityGraph:mocks.entityGraph}}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({user:{id:1}})}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({loaded:false,load:vi.fn()})}))
it('keeps unknown original first attempt distinct and shows only original comparison',async()=>{mocks.entityGraph.mockResolvedValue({nodes:[]});mocks.get.mockResolvedValue({from:'2026-10-20',until:'2026-10-27',items:[{kind:'T02_QUIZ_ANSWER',id:1,at:'2026-10-27',knowledgeIds:[],original:{assessmentKind:'OBJECTIVE',score:0,maxScore:100,firstAttemptRef:null,firstTrackedAttemptRef:{id:9}}},{kind:'INTERVIEW_RETEST',id:2,at:'2026-10-27',knowledgeIds:[],original:{comparison:{comparable:false,note:'条件不同'}}}],counts:{},mastery:[],boundedByKind:{},unavailableCount:1});const w=mount(Panel,{global:{stubs:{NextActionCard:true}}});await flushPromises();expect(w.text()).toContain('原始首轮：UNKNOWN · 追踪起点：#9');expect(w.text()).toContain('不可比较；条件不同');expect(w.text()).toContain('已隐藏正文');expect(w.text()).not.toContain('提高40%');w.unmount()})
