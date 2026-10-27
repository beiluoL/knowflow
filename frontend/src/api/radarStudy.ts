import { apiGet, apiPost } from './request'
import type { V2Session } from './interviewV2'
export interface RadarQuestion { questionId:number;sourceQuestionId:string;sourceNamespace:string;title:string;domain:string;difficulty:number|null;priority:string|null;chapterId:number|null;reviewStatus:string|null;eligible:boolean;warnings:string[];sourceFiles:string[] }
export interface RadarQuestionRow { question:RadarQuestion;firstScore:number|null;currentScore:number|null;bestScore:number|null;answeredCount:number;firstTurnId:number|null;latestTurnId:number|null;latestSessionId:number|null;lastPracticedAt:string|null;passed:boolean;everPassed:boolean;flashcardId:number|null;reviewDue:boolean }
export interface RadarDomain {domain:string;total:number;answered:number;untested:number;graded:number;firstAverage:number|null;currentAverage:number|null;passed:number;low:number;coverage:number;sampleNotice:string}
export interface RadarOverview { domains:RadarDomain[];questionCount:number;assessmentType:string;rubricVersion:string;passScore:number }
export interface RadarStart { questionId?:number;stage:'CLOSED_BOOK'|'RETELL'|'MOCK';interviewMode:'SPECIALTY'|'COMPREHENSIVE'|'WEAK'|'PROJECT'|'TIMED';domain?:string;minutes:number;count:number;acknowledgeDraft:boolean;requestId:string;projectContext?:string;projectDocumentIds?:number[] }
export const radarStudyApi = {
 questions:()=>apiGet<RadarQuestionRow[]>('/interview/radar/questions'),
 overview:()=>apiGet<RadarOverview>('/interview/radar/overview'),
 start:(body:RadarStart)=>apiPost<V2Session>('/interview/radar/sessions',body),
 collectReview:(id:number)=>apiPost<{flashcardId:number;mistakeId:number}>(`/interview/radar/questions/${id}/review`),
 savePlan:(minutes:15|45|90)=>apiPost<{taskId:number}>('/interview/radar/plans',{minutes}),
 favorites:()=>apiGet<number[]>('/learning/chapters/favorites'),
 favorite:(id:number)=>apiPost<{favorited:boolean}>(`/learning/chapters/${id}/favorite`),
}
