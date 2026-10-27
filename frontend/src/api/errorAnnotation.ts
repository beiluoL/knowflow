import {apiGet,apiPost} from './request'
import type {MicroSession} from './microChallenge'
export type ErrorSource='TUTOR_ASSESSMENT'|'PRIVATE_ATTEMPT'|'INTERVIEW_TURN'
export interface ErrorFinding {label:string;answerQuote:string;basisQuote:string;issue:string;missingKnowledge:string;practice:string}
export interface ErrorData {labels:string[];findings?:ErrorFinding[];uncertainty:string;confidence?:string;note?:string}
export interface ErrorAnnotation {id:number;state:string;revision:number;sourceType:ErrorSource;sourceId:number;questionIndex:number;model:string;promptVersion:string;taxonomyVersion:string;error:string|null;original:ErrorData|null;current:ErrorData|null;history:{revision:number;kind:string;data:ErrorData;createdAt:string}[]}
export interface ErrorPractice {annotationId:number;noteId:number|null;quizId?:number;questionIndex?:number;assessmentId:number|null;target:string;completedCount:number;completed:MicroSession[]}
const root='/learning/error-annotations'
export const errorAnnotationApi={list:(sourceType:string,sourceId:number,questionIndex=0)=>apiGet<ErrorAnnotation[]>(root,{sourceType,sourceId,questionIndex}),create:(input:{sourceType:string;sourceId:number;questionIndex:number;requestId:string})=>apiPost<ErrorAnnotation>(root,input,{timeout:90000}),correct:(id:number,input:{expectedRevision:number;requestId:string;labels:string[];note:string})=>apiPost<ErrorAnnotation>(`${root}/${id}/corrections`,input),practice:(id:number)=>apiGet<ErrorPractice>(`${root}/${id}/practice`),linkPractice:(id:number,microId:number)=>apiPost<ErrorPractice>(`${root}/${id}/practice`,{microId})}
