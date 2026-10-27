import {apiGet} from './request'
import type {KnowledgeMasteryVO} from './types'
import type {EvidencePage} from './learningEvidence'
export interface GraphInsight {knowledgeId:number;mastery:KnowledgeMasteryVO & {explanation?:string};evidence:EvidencePage;practiceNotes:{id:number;title:string;revision:number}[];notice:string;recentRetests?:{id:number;baselineTurn:number;retestTurnId:number|null;comparison?:{comparable:boolean;scoreDifference?:number;note:string}}[]}
export const graphInsightApi={detail:(id:number)=>apiGet<GraphInsight>(`/learning/graph-insights/${id}`)}
