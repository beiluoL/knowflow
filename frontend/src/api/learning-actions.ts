import { apiGet, apiPost, apiDelete } from './request'
export interface PlanRequest { date?: string; zoneId?: string; availableMinutes?: number; focusTopics?: string[]; includePractice?: boolean; includeInterview?: boolean; useAi?: boolean; instruction?: string; smartReorder?: boolean; pathId?: number; taskMinutes?: Record<number,number> }
export interface PlanItem { key: string; title: string; type: string; estimatedMinutes: number; priority: 'HIGH'|'MEDIUM'|'LOW'; resourceType: string; resourceId: number|null; pathId: number|null; knowledgeId: number|null; reason: string; completionCondition: string; existingTaskId?: number|null; existingSource?: string|null }
export interface TaskRef { id: number; source: 'TASK'|'LEARNING'; title: string; status: number }
export interface LearningActionDraft { planId: string; version: number; expiresAt: string; state: 'PREVIEW'|'CONFIRMED'|'CANCELLED'|'EXPIRED'|'SOURCE_UNAVAILABLE'; source: 'RULE'|'AI_ASSISTED'; action: 'CREATE'|'UPDATE'|'STATUS'; date: string; zoneId: string; availableMinutes: number; tasks: PlanItem[]; warnings: string[]; confirmationToken: string|null; createdTasks: TaskRef[]; targetStatus?: number|null; preferences?: PlanRequest; changes?: {taskId:number;kind:string;oldDate:string;newDate:string;oldMinutes:number;newMinutes:number;reason:string}[] }
export interface LearningActionResponse { handled: boolean; text: string; planId: string|null; tasks: TaskRef[]; choices: TaskRef[]; conversationId: number }
export interface ConfirmPlan { version: number; confirmationToken: string; idempotencyKey: string; tasks: Pick<PlanItem,'key'|'title'|'estimatedMinutes'|'priority'>[] }
export interface LearningDashboard { date:string;zoneId:string;facts:{dueFlashcards:number;dueFlashcardsBounded:boolean;masteryRecordsWithEvidence:number};missingData:string[];actions:PlanItem[];weakSignals:PlanItem[];tasks:{id:number;title:string;status:number;eventId:number|null;item:PlanItem|null}[];bounded:boolean }
export const learningActionApi = {
 dashboard: (minutes=60,zoneId=Intl.DateTimeFormat().resolvedOptions().timeZone) => apiGet<LearningDashboard>('/learning/action-plans/dashboard',{minutes,zoneId}),
 preview: (request: PlanRequest) => apiPost<LearningActionDraft>('/learning/action-plans/daily/preview',request),
 get: (id: string) => apiGet<LearningActionDraft>(`/learning/action-plans/${encodeURIComponent(id)}`),
 confirm: (id: string, request: ConfirmPlan) => apiPost<LearningActionDraft>(`/learning/action-plans/${encodeURIComponent(id)}/confirm`,request),
 regenerate: (id: string, request: PlanRequest) => apiPost<LearningActionDraft>(`/learning/action-plans/${encodeURIComponent(id)}/regenerate`,request),
 cancel: (id: string) => apiDelete<void>(`/learning/action-plans/${encodeURIComponent(id)}`),
 chat: (conversationId: number, message: string, preferences?: PlanRequest, selectedTask?: TaskRef, signal?: AbortSignal) => apiPost<LearningActionResponse>('/chat/learning-actions',{ conversationId,message,preferences,selectedTaskId:selectedTask?.id,selectedTaskSource:selectedTask?.source }, {signal, timeout:30000}),
}
/** Server-owned structured message payload is reloaded with conversation history. */
export function parseLearningActionMessage(content: string): Partial<LearningActionResponse>|null {
 try { const value: unknown = JSON.parse(content); if(!value||typeof value!=='object')return null;const v=value as Record<string,unknown>;
  if(v.kind!=='KNOWFLOW_LEARNING_ACTION'||typeof v.text!=='string')return null;
  if(v.planId!=null&&(typeof v.planId!=='string'||! /^[a-f0-9-]{36}$/.test(v.planId)))return null;
  const refs=(v:unknown):v is TaskRef[]=>Array.isArray(v)&&v.every(t=>t&&Number.isSafeInteger(t.id)&&t.id>0&&['TASK','LEARNING'].includes(t.source)&&typeof t.title==='string');
  if(!refs(v.tasks)||!refs(v.choices))return null;
  return {text:v.text,planId:v.planId as string|null,tasks:v.tasks,choices:v.choices};
 } catch { return null }
}

/** UI routing hint only. The server repeats intent and authorization checks. */
export function isLearningActionInstruction(text:string):boolean {
 if(/不要生成|无需生成|不用安排|不要安排/.test(text))return false
 return /今天应该学什么|今日应该学什么|今天学什么|哪里.*薄弱|做错.*(?:CAS|题)|闪卡.*复习|哪些闪卡|下一步怎么学/s.test(text)||/(?:今天|今日).*(?:还有|有什么|查询|查看).*(?:任务|计划)/s.test(text)||/(?:我已|我已经|已经完成|完成了).+/s.test(text)||/(?:重新安排|重新规划|调整计划|只剩|没完成的任务调整到).+/s.test(text)||/(?:生成|安排|制定|规划).*(?:学习|今日|今天).*(?:任务|计划|小时|分钟)/s.test(text)
}

export interface SmartTask {id:number;title:string;status:number;scheduledDate:string;dueDate:string|null;estimatedMinutes:number;durationSource:string;locked:boolean;revision:string}
const smartRoot='/learning/action-plans/smart'
export const smartPlanApi={
 tasks:()=>apiGet<SmartTask[]>(smartRoot+'/tasks'),
 settings:(id:number,value:{revision:string;locked:boolean;estimatedMinutes:number})=>apiPost<void>(smartRoot+`/tasks/${id}/settings`,value),
 history:()=>apiGet<LearningActionDraft[]>(smartRoot+'/history'),
 preview:(r:PlanRequest)=>apiPost<LearningActionDraft>(smartRoot+'/preview',r),
 get:(id:string)=>apiGet<LearningActionDraft>(smartRoot+`/${encodeURIComponent(id)}`),
 confirm:(id:string,r:ConfirmPlan)=>apiPost<LearningActionDraft>(smartRoot+`/${encodeURIComponent(id)}/confirm`,r),
 regenerate:(id:string,r:PlanRequest)=>apiPost<LearningActionDraft>(smartRoot+`/${encodeURIComponent(id)}/regenerate`,r),
 cancel:(id:string)=>apiDelete<void>(smartRoot+`/${encodeURIComponent(id)}`),
}
