import {apiGet,apiPost} from './request'
import type {V2Session} from './interviewV2'
export interface MockTopic {topic:string;count:number;available:boolean;syntheticFixture:boolean}
export const productMockInterviewApi={topics:()=>apiGet<MockTopic[]>('/learning/mock-interviews/topics'),start:(input:{requestId:string;role:string;topic:string;projectDocumentIds:number[]})=>apiPost<V2Session>('/learning/mock-interviews',input)}
