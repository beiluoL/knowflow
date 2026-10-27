import { apiGet, apiPost } from './request'
export interface InterviewSeedStatus {
 seedKey:string
 activated:boolean
 goalId?:number
 pathIds?:number[]
 paths?:{id:number;title:string}[]
 counts:Record<string,number>
}
export interface InterviewSeedChapter {
 chapterId:number
 docId:number
 docTitle:string
 questions:{id:number;title:string;answer:string;explanation:string;questionType?:string;options?:string[]|string}[]
 exercise:{id:number;title:string;description:string;codeTemplate:string;solutionHint:string}|null
 notes:{id:number;title:string;content:string}[]
 upgradeAvailable?:boolean
 upgradeKind?:'hashmap'|'generics'
 readingContent?:string|null
 readingUpgradeAvailable?:boolean
 rebuildAvailable?:boolean
 revision?:CollectionsRevision|null
}
export interface InterviewQuestion { id:number;title:string;answer:string;explanation:string;canonicalKey:string;parentKey:string|null;importance?:string;depth?:string;questionType?:string;options?:string[]|string }
export interface CollectionsRevision {
 version:string;content:string;docId:number;docTitle:string
 readingVersion?:string;previousReadingDocId?:number
 questions:InterviewQuestion[]
 exercises:{id:number;title:string;description:string;codeTemplate:string;solutionHint:string}[]
 knowledge:{id:number;name:string}[]
 relatedKnowledge?:{id:number;name:string}[]
 notes:{id:number;title:string;content:string}[]
 flashcardCount:number
}
export const interviewSeedApi={
 status:()=>apiGet<InterviewSeedStatus>('/learning/seed/java-ai-v1'),
 activate:()=>apiPost<InterviewSeedStatus>('/learning/seed/java-ai-v1',undefined,{timeout:120000}),
 chapter:(id:number)=>apiGet<InterviewSeedChapter|null>(`/learning/seed/java-ai-v1/chapters/${id}`),
 upgradeCollections:()=>apiPost<unknown>('/learning/seed/java-ai-v1/collections-depth',undefined,{timeout:120000}),
 upgradeGenerics:()=>apiPost<unknown>('/learning/seed/java-ai-v1/generics-depth',undefined,{timeout:120000}),
 rebuildDryRun:()=>apiGet<{dryRun:boolean;chapters:{key:string;chapterId:number;title:string;questions:number;flashcards:number}[]}>('/learning/seed/java-ai-v1/content-rebuild'),
 rebuildImport:()=>apiPost<unknown>('/learning/seed/java-ai-v1/content-rebuild',undefined,{timeout:120000}),
}
