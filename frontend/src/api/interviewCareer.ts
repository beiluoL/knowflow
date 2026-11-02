import request, { apiGet, apiPost, apiPut, apiDelete, apiUpload } from './request'
export interface CareerClaim { value:string; quote:string }
export interface CareerRecord { id:number; kind:'RESUME'|'JOB'; title:string; source:string; version:number; currentVersion:number; confirmed:boolean; isDefault?:boolean; sections:Record<string,CareerClaim[]> }
export interface CareerEdit { expectedVersion?:number; title:string; source:string; sections:Record<string,CareerClaim[]> }
export interface CareerDimension { requirement:string; resumeEvidence:string[]; matchStatus:string; gapDescription:string; evidenceSource:string }
export interface CareerMatch { id:number; resume:CareerRecord; job:CareerRecord; required:CareerDimension[]; preferred:CareerDimension[]; score:number|null; scoreMeaning:string; unassessedDimensions:string[]; dimensions?:{id:string;requirement:string;resumeEvidence:string[];matchStatus:string;confidence:string;gapDescription:string;requirementEvidence:string[]}[]; overallScore?:number|null }
export interface CareerPlan { id:number; questions:{questionId:string;title:string;questionPurpose:string;evidenceReference:string}[]; generationSource:string }
export const listCareerRecords=(kind:string)=>apiGet<CareerRecord[]>('/interview/career/records',{kind})
export const createCareerRecord=(kind:string,body:CareerEdit)=>apiPost<CareerRecord>(`/interview/career/records/${kind}`,body)
export const saveCareerRecord=(id:number,body:CareerEdit)=>apiPut<CareerRecord>(`/interview/career/records/${id}`,body)
export const getCareerRecord=(id:number,version?:number)=>apiGet<CareerRecord>(`/interview/career/records/${id}`,version?{version}:undefined)
export const confirmCareerRecord=(id:number,version:number)=>apiPost<CareerRecord>(`/interview/career/records/${id}/confirm`,{version})
export const deleteCareerRecord=(id:number)=>apiDelete(`/interview/career/records/${id}`)
export const parseCareerRecord=async(id:number,version:number)=>{const result=await request.post<unknown,{data:CareerRecord}>(`/interview/career/records/${id}/parse`,{version,allowModelProcessing:true},{timeout:300000});return result.data}
export const getCareerVersions=(id:number)=>apiGet<{version:number;confirmed:boolean;createdAt:string}[]>(`/interview/career/records/${id}/versions`)
export const createCareerMatch=(resume:CareerRecord,job:CareerRecord)=>apiPost<CareerMatch>('/interview/career/matches',{resumeId:resume.id,resumeVersion:resume.version,jobId:job.id,jobVersion:job.version,requestId:crypto.randomUUID()})
export interface CareerPlanSettings { projectCount:number;coreCount:number;gapCount:number;designCount:number;durationMinutes:number;difficulty:string }
export const createCareerPlan=(matchId:number,settings:CareerPlanSettings)=>apiPost<CareerPlan>('/interview/career/plans',{matchId,...settings,requestId:crypto.randomUUID()})
export const startCareerPlan=(id:number,requestId:string)=>apiPost<{sessionId:number}>(`/interview/career/plans/${id}/start`,{requestId})

export const uploadCareerRecord=(kind:string,file:File,onProgress?:(progress:number)=>void)=>apiUpload<CareerRecord>(`/interview/career/records/${kind}/upload`,file,{},onProgress)
export const getCareerFile=(id:number)=>request.get<Blob,Blob>(`/interview/career/records/${id}/file`,{responseType:'blob'})

export const setDefaultResume=(id:number)=>apiPost<CareerRecord>(`/interview/career/records/${id}/default`)

export interface CareerCorrection {requirement:string;matchStatus:string;resumeEvidence:string[];reason:string}
export const correctCareerMatch=(id:number,corrections:CareerCorrection[])=>apiPost<CareerMatch>(`/interview/career/matches/${id}/corrections`,{requestId:crypto.randomUUID(),corrections})
export const getCareerMatch=(id:number)=>apiGet<CareerMatch>(`/interview/career/matches/${id}`)
export const listCareerMatches=()=>apiGet<{id:number;resumeTitle:string;jobTitle:string}[]>('/interview/career/matches')
