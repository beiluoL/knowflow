import type { PlanItem } from '@/api/learning-actions'
import { stampActionContext, resolveContextualRoute, resolvePlanResourceSelector } from '@/learning/action-contract'
import type { NextLearningAction } from '@/learning/types'
/** 计划资源入口复用现有带上下文盖章的路由白名单，不接受模型 URL。 */
export function planResourceRoute(item: PlanItem): string {
 const context={knowledgeId:item.knowledgeId ?? undefined,learningNodeId:item.resourceType==='CHAPTER'?item.resourceId:null}
 let action:NextLearningAction
 const common={title:item.title,reason:item.reason,route:'/learning/center'}
 if((item.resourceType==='FLASHCARD'||item.resourceType==='QUIZ')&&item.resourceId&&item.type!=='CODE')return resolvePlanResourceSelector(item.resourceType,item.resourceId,{...common,type:'EVIDENCE_PRACTICE'},context)
 if(item.resourceType==='CHAPTER'&&item.resourceId)action={...common,type:'EVIDENCE_PRACTICE',route:`/learning/chapter/${item.resourceId}`}
 else if(item.type==='REVIEW')action={...common,type:'REVIEW',knowledgeId:item.knowledgeId ?? (item.resourceType==='KNOWLEDGE'?item.resourceId ?? undefined:undefined)}
 else if(item.resourceType==='KNOWLEDGE'&&item.resourceId)action={...common,type:'KNOWLEDGE',knowledgeId:item.resourceId}
 else if(item.type==='INTERVIEW')action={...common,type:'MINI_APP',appKey:'mock-interview'}
 else if(item.type==='CODE')action={...common,type:'MINI_APP',appKey:'coding'}
 else if(item.type==='PRACTICE'||item.resourceType==='QUIZ')action={...common,type:'QUIZ'}
 else action={...common,type:'EVIDENCE_PRACTICE',route:'/learning/first-principles'}
 return resolveContextualRoute(stampActionContext(action,context),context)
}
