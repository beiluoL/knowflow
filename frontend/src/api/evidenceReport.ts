import {apiGet} from './request'
export interface EvidenceReportItem {kind:string;id:number;at:string;knowledgeIds:number[];association:string;original:Record<string,unknown>}
export interface EvidenceReport {from:string;until:string;days:number;items:EvidenceReportItem[];counts:Record<string,number>;boundedByKind:Record<string,boolean>;unavailableCount:number;mastery:unknown[];firstAttemptRef:string;semantics:string}
export const evidenceReportApi={get:(knowledgeId?:number)=>apiGet<EvidenceReport>('/learning/evidence-report',{knowledgeId})}
