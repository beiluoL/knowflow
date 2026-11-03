import { apiGet, apiPost } from '@/api/request';

export interface ResumeStudioSkill {
  id: string;
  version: string;
  title: string;
  targetRole: string;
  inputs: string[];
  realEvidence: string;
  outputSchema: Record<string, unknown>;
  factValidation: string;
  review: string;
  promptVersion: string;
  modelRequired: boolean;
  highRiskProhibitions: string[];
}
export interface ResumeStudioCheck {
  resumeId: number;
  resumeVersion: number;
  skill: ResumeStudioSkill;
  modelSource: 'RULE';
  writesResume: boolean;
  findings: Array<{
    id: string;
    title: string;
    status: 'EVIDENCE_FOUND' | 'NEEDS_CONFIRMATION';
    evidence: Array<{ section: string; originalQuote: string }>;
    question: string;
    suggestion: string;
  }>;
  disclaimer: string;
}
export const listStudioSkills = () => apiGet<ResumeStudioSkill[]>('/interview/career/studio/skills');
export const checkStudioResume = (body: { resumeId: number; resumeVersion: number; skillId: string }) =>
  apiPost<ResumeStudioCheck>('/interview/career/studio/check', body);
