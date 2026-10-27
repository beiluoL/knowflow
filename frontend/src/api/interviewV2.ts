import { apiGet, apiPost } from './request'
export interface V2Turn { id:number; questionId:string; question:string; depth:number; answer:string; source:string; referencePoints?:string[]; selfScore:number|null; evaluation?:{dimensions:Record<string,number|null>; strengths:string[]; gaps:string[]; citations:string[]; suggestion:string; coveredKnowledge?:string[];errors?:string[];confidence:number; model:string;promptVersion?:string; rubricVersion:string;radarGrade?:{score:number|null;level:string|null;passed:boolean;points:Record<string,number|null>}}|null; decision?:string }
export interface V2Report {overallScore:number|null;radarOverallScore?:number|null;dimensionScores:Record<string,number|null>;evaluatedTurns:number;answeredTurns:number;rubricVersion:string;weights:Record<string,number>;coverage:unknown;historyComparison:{previousSessionId:number;previousScore:number|null;currentScore:number|null}|null}
export interface V2Session {sessionId:number;version:number;state:string;mode:string;role:string;difficulty:string;questionIndex:number;questionCount:number;followupDepth:number;maxDepth:number;remainingSeconds:number;deadline:string;currentQuestion:{id:string;title:string;source?:'QUESTION_BANK'|'AI'|'PRESET'}|null;turns:V2Turn[];message?:string;report?:V2Report;referencePoints?:string[]}
export interface V2Start { track:string;questionId?:string;questionCount:number;mode:'STRICT'|'TEACHING';role:string;durationMinutes:number;maxDepth:number;difficulty:string;requestId:string;projectContext?:string;projectDocumentIds?:number[] }
export interface V2Answer {version:number;requestId:string;answer:string;selfScore?:number;speechId?:number}
export interface SpeechCapability {enabled:boolean;supportedFormats:string[];maxBytes:number;maxDurationSeconds:number;reason?:string}
export interface SpeechResult {id:number;questionId:string;status:'TRANSCRIBING'|'TRANSCRIBED'|'FAILED';rawText:string;error?:string}
export const startV2 = (body:V2Start) => apiPost<V2Session>('/interview/v2/sessions',body)
export const getV2 = (id:number) => apiGet<V2Session>(`/interview/v2/sessions/${id}`)
export const answerV2 = (id:number, body:V2Answer) => apiPost<V2Session>(`/interview/v2/sessions/${id}/answers`,body)
export const transitionV2 = (id:number, action:'pause'|'resume'|'finish',body:{version:number;requestId:string}) => apiPost<V2Session>(`/interview/v2/sessions/${id}/${action}`,body)
export const getSpeechCapability = () => apiGet<SpeechCapability>('/interview/speech/capability')
export function uploadSpeech(id:number,file:Blob,questionId:string,requestId:string,durationMs:number) {
 const body=new FormData(); body.append('file',file,'answer.wav');body.append('questionId',questionId);body.append('requestId',requestId);body.append('durationMs',String(durationMs))
 return apiPost<SpeechResult>(`/interview/sessions/${id}/speech`,body,{timeout:150000})
}
export interface RemediationPreview {sessionId:number;resources:Array<{knowledgeId:number;title:string;sourceCitations:string[];reason:string}>;message:string;evidenceIngestion:'UNSUPPORTED'}
export const previewRemediation=(id:number)=>apiGet<RemediationPreview>(`/interview/v2/sessions/${id}/remediation`)
export const confirmRemediation=(id:number,body:{requestId:string;knowledgeIds:number[]})=>apiPost<Array<{knowledgeId:number;taskId:number}>>(`/interview/v2/sessions/${id}/remediation/confirm`,body)

export interface InterviewScenario {id:string;title:string}
export const getTrackQuestions=(code:string)=>apiGet<InterviewScenario[]>(`/interview/tracks/${encodeURIComponent(code)}/questions`)
