import {apiGet,apiPost,apiPut,apiUpload} from './request'
import type {ServerNote} from './notes'
export interface PackCard {front:string;back:string;difficulty?:number}
export interface PackStep {kind:'PARSE'|'NOTE'|'CARDS'|'QUIZ'|'TUTOR';state:string;resourceId:number|null;errorMessage?:string;runCount:number;elapsedMs?:number}
export interface StudyPack {id:number;fileName:string;sourceHash:string;state:string;revision:number;docId?:number;createTime:string;steps:PackStep[];events:{id:number;kind:string;state:string;elapsedMs?:number}[];note?:ServerNote;noteOutline?:{chapters:{title:string;knowledgePoints:string[]}[]};cardDrafts:PackCard[];flashcardIds:number[];quizId?:number;conversationId?:number}
const root='/learning/study-packs'
export const studyPackApi={
 list:()=>apiGet<StudyPack[]>(root),
 upload:(file:File)=>apiUpload<StudyPack>(root,file,{}),
 detail:(id:number)=>apiGet<StudyPack>(`${root}/${id}`),
 retry:(id:number,step:string)=>apiPost<StudyPack>(`${root}/${id}/retry`,{step}),
 saveCards:(id:number,expectedRevision:number,cards:PackCard[])=>apiPut<StudyPack>(`${root}/${id}/draft`,{expectedRevision,cards}),
 accept:(id:number,expectedRevision:number,cards:PackCard[])=>apiPost<StudyPack>(`${root}/${id}/accept`,{expectedRevision,cards}),
}
