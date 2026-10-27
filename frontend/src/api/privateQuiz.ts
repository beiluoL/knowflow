import {apiGet,apiPost} from './request'
export interface PrivateQuestion {type:'TRUE_FALSE'|'SINGLE_CHOICE';content:string;options:string[];answer?:string;explanation?:string}
export interface PrivateQuiz {id:number;noteId:number;sourceRevision:number;title:string;state:'DRAFT'|'SAVED';version:number;questions:PrivateQuestion[];createdAt:string}
export interface PrivateAttempt {id:number;quizId:number;state:'IN_PROGRESS'|'SUBMITTED';version:number;createdAt:string;conditions:string;questions?:PrivateQuestion[];score?:number;correct?:number;total?:number;items?:{question:PrivateQuestion;userAnswer:string;correct:boolean;score:number}[]}
const root='/learning/private-quizzes'
export const privateQuizApi={
 list:(noteId:number)=>apiGet<PrivateQuiz[]>(root,{noteId}),
 preview:(noteId:number,expectedRevision:number,requestId:string)=>apiPost<PrivateQuiz>(`${root}/preview`,{noteId,expectedRevision,requestId},{timeout:90000}),
 detail:(id:number)=>apiGet<PrivateQuiz>(`${root}/${id}`),
 confirm:(id:number,questions:PrivateQuestion[])=>apiPost<PrivateQuiz>(`${root}/${id}/confirm`,{questions}),
 begin:(id:number,requestId:string)=>apiPost<PrivateAttempt>(`${root}/${id}/attempts`,{requestId}),
 history:(id:number)=>apiGet<PrivateAttempt[]>(`${root}/${id}/history`),
 submit:(id:number,answers:string[])=>apiPost<PrivateAttempt>(`${root}/attempts/${id}/submit`,{answers}),
}
