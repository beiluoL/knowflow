import { describe,it,expect } from 'vitest'
import { planResourceRoute } from '../plan-link'
import type { PlanItem } from '@/api/learning-actions'
const item:PlanItem={key:'q',title:'CAS',type:'PRACTICE',resourceType:'QUIZ',resourceId:123,pathId:null,knowledgeId:7,reason:'实际错题',completionCondition:'真实作答',estimatedMinutes:10,priority:'HIGH'}
describe('exact learning resources',()=>{
 it('keeps accepted knowledge and selects the actual question',()=>{expect(planResourceRoute(item)).toBe('/learning/quiz?questionId=123&knowledgeId=7')})
 it('uses one actual FSRS card rather than the whole private collection',()=>{expect(planResourceRoute({...item,type:'REVIEW',resourceType:'FLASHCARD'})).toBe('/learning/flashcards?cardId=123&knowledgeId=7')})
 it('rejects invalid identities',()=>expect(planResourceRoute({...item,resourceId:-1})).toBe('/learning/center'))
})
