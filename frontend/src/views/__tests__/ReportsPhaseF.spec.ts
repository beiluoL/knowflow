// @vitest-environment happy-dom
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
const api=vi.hoisted(()=>({getReport:vi.fn(),mastery:vi.fn(),categoryMastery:vi.fn(),knowledgeMastery:vi.fn()}))
const next=vi.hoisted(()=>({loaded:true,error:'',load:vi.fn(),nextAction:{type:'EXPLORE',title:'权威下一步',reason:'Store给出的动作',route:'/learning/center'}}))
vi.mock('@/api',()=>({learningApi:api}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>next}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({push:vi.fn(),back:vi.fn()})}))
import Report from '../LearningReportPage.vue'
import Mastery from '../MasteryDashboard.vue'
const report=()=>({period:'month',startDate:'2026-09-17',endDate:'2026-10-16',checkinDays:0,continuousDays:0,studyMinutes:0,docsRead:0,flashcardReviewed:0,flashcardMastered:0,mistakeCount:0,mistakeMastered:0,codeSubmissions:0,codePassed:0,quizAnswered:0,quizCorrect:0,dailyActivity:[],categoryMastery:[],weeklyTrend:[{weekStart:'2026-10-12',studyMinutes:0,checkinDays:0}]})
const opts={global:{stubs:{Icon:true,RouterLink:{template:'<a><slot/></a>'},NextActionCard:{props:['action'],template:'<div>{{action.title}}</div>'}}}}
beforeEach(()=>{vi.clearAllMocks();api.getReport.mockResolvedValue(report());api.mastery.mockResolvedValue({flashcardTotal:0,flashcardDiffEasy:0,flashcardDiffMedium:0,flashcardDiffHard:0,flashcardDue:0,flashcardReviewed:0,mistakeMastered:0,mistakePending:0});api.categoryMastery.mockResolvedValue([]);api.knowledgeMastery.mockResolvedValue([])})
describe('Phase F report semantics',()=>{
 it('零趋势显示空态而非空坐标图',async()=>{const w=mount(Report,opts);await flushPromises();expect(w.text()).toContain('暂无周趋势数据');w.unmount()})
 it('近7天/30天与累计口径明确，下一步只复用Store',async()=>{const w=mount(Report,opts);await flushPromises();expect(w.text()).toContain('近 7 天');expect(w.text()).toContain('累计阅读时长');expect(w.text()).toContain('权威下一步');await w.findAll('button').find(b=>b.text()==='近 7 天')!.trigger('click');await flushPromises();expect(api.getReport).toHaveBeenLastCalledWith('week');w.unmount()})
 it('只有知识点结果也不能显示整页无数据',async()=>{api.knowledgeMastery.mockResolvedValue([{knowledgeId:10,name:'仅有证据的知识点',masteryScore:42,confidenceScore:30,forgettingRisk:20,learningStatus:'LEARNING',weaknessTypes:[],lastLearnedAt:'2026-10-16T12:00:00'}]);const w=mount(Mastery,opts);await flushPromises();expect(w.text()).toContain('仅有证据的知识点');expect(w.find('[data-test="mastery-empty"]').exists()).toBe(false);expect(w.find('[data-test="knowledge-panel"] [tabindex="0"]').exists()).toBe(true);w.unmount()})
})
