import {apiGet,apiPost,apiPut} from './request'
import type {PrivateAttempt} from './privateQuiz'
export interface MicroSession {id:number;requestId:string;noteId:number;quizId:number;questionIndex:number;assessmentId:number|null;state:string;target:string;sourceRevision:number;analogy?:string;concept?:string;sourceQuote?:string;draftAnswer:string|null;createdAt:string;startedAt:string|null;completedAt:string|null;error:string|null;conditions:string;attempt?:PrivateAttempt}
export interface MicroStart {noteId:number;quizId:number;questionIndex:number;requestId:string;assessmentId?:number}
const root='/learning/micro-challenges'
export const microApi={list:(noteId:number)=>apiGet<MicroSession[]>(root,{noteId}),detail:(id:number)=>apiGet<MicroSession>(`${root}/${id}`),start:(input:MicroStart)=>apiPost<MicroSession>(root,input,{timeout:90000}),begin:(id:number)=>apiPost<MicroSession>(`${root}/${id}/begin`,{}),draft:(id:number,answer:string)=>apiPut<MicroSession>(`${root}/${id}/draft`,{answer}),submit:(id:number,answer:string)=>apiPost<MicroSession>(`${root}/${id}/submit`,{answer})}
