import { apiGet, apiPost } from './request'
export interface InterviewAiAdvice { commentary: string; strengths: string[]; gaps: string[]; followup: string; citations: string[] }
export interface InterviewAiTurn { id: number; round: number; status: 'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED'; result: InterviewAiAdvice|null; error: string|null; answerChanged: boolean; followupAnswer: string }
export interface InterviewAiStart { mode: 'AI'|'BASIC'; turn?: InterviewAiTurn; reason?: string; referencePoints?: string[]; followups?: string[] }
export const interviewAiApi = {
 list: (sessionId:number,questionId:string) => apiGet<InterviewAiTurn[]>(`/interview/sessions/${sessionId}/ai-turns`,{questionId}),
 start: (sessionId:number,body:{questionId:string;requestId:string;useAi:boolean;followupAnswer?:string}) => apiPost<InterviewAiStart>(`/interview/sessions/${sessionId}/ai-turns`,body),
 cancel: (sessionId:number,id:number) => apiPost<InterviewAiTurn>(`/interview/sessions/${sessionId}/ai-turns/${id}/cancel`),
}
