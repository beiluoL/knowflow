import {apiGet,apiPost} from './request'
export interface InterleavedQuestion {type:string;content:string;options:string[];answer?:string;explanation?:string}
export interface InterleavedSession {id:number;state:string;createdAt:string;completedAt:string|null;gradingSource:string;items:{knowledgeId:number;quizId:number;questionIndex:number;quizVersion:number;attemptId:number;association:string;conditions:string;attempt:{state:string;questions?:InterleavedQuestion[];score?:number;items?:{question:InterleavedQuestion;userAnswer:string;correct:boolean;score:number}[]}}[]}
export interface InterleavedPool {quizzes:{id:number;title:string;noteId:number;questions:InterleavedQuestion[]}[]}
const root='/learning/interleaved-practice'
export const interleavedApi={pool:()=>apiGet<InterleavedPool>(root+'/pool'),history:()=>apiGet<InterleavedSession[]>(root),get:(id:number)=>apiGet<InterleavedSession>(root+'/'+id),start:(requestId:string,topics:{knowledgeId:number;quizId:number}[],count:number)=>apiPost<InterleavedSession>(root,{requestId,topics,count}),submit:(id:number,answers:string[])=>apiPost<InterleavedSession>(root+'/'+id+'/submit',{answers})}
