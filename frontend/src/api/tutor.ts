import {apiGet,apiPost} from './request'
import type {ServerNote} from './notes'
export interface TutorReadingSelection {noteId:number;revision:number;quote:string;start:number}
export type TutorMode='SOCRATIC'|'DIRECT'|'TEACH_BACK'
export interface TutorSession {conversationId:number;noteId:number;mode:TutorMode;revision:number;sourceRevision:number;title:string}
export interface TutorHints {hintCount:number;maxHintLevel:number;answerExposed:boolean;history:{hint_level:number;state:string}[]}
export interface AssessmentInput {requestId:string;text:string;topic:string;scenario:string;knowledgeId?:number;previousId?:number}
export interface Assessment {id:number;requestId:string;state:string;originalText:string;topic:string;knowledgeId:number|null;previousId:number|null;sourceRevision:number;rubricVersion:string;promptVersion:string;model:string;evaluatedAt:string|null;error:string|null;comparable:boolean;comparisonNote:string;result:null|{totalScore:number;correct:string[];technicalErrors:string[];missingKnowledge:string[];principleGaps:string[];engineeringGaps:string[];practiceDirections:string[];nextRetell:string[];referenceAnswer:string;confidence:string;uncertainty:string;evidence:{answerQuote:string;sourceQuote:string;reason:string}[];dimensions:{key:string;score:number;reason:string;evidenceIndex:number}[]}}
const root='/learning/tutor/sessions'
export const tutorApi={
 list:(noteId:number)=>apiGet<TutorSession[]>(root,{noteId}),
 create:(noteId:number,mode:TutorMode)=>apiPost<TutorSession>(root,{noteId,mode}),
 mode:(id:number,mode:TutorMode,expectedRevision:number)=>apiPost<TutorSession>(`${root}/${id}/mode`,{mode,expectedRevision}),
 appendAnswer:(id:number,messageId:number,expectedRevision:number)=>apiPost<ServerNote>(`${root}/${id}/answers/${messageId}/to-note`,{expectedRevision,confirmed:true}),
 assessments:(id:number)=>apiGet<Assessment[]>(`${root}/${id}/assessments`),
 assess:(id:number,input:AssessmentInput)=>apiPost<Assessment>(`${root}/${id}/assessments`,input,{timeout:90000}),
 hints:(id:number)=>apiGet<TutorHints>(`${root}/${id}/hints`),
 detail:(id:number)=>apiGet<TutorSession>(`${root}/${id}`),
}
