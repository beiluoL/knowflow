import { apiGet } from './request'

export interface ExamDiagnosisEvidence {
  attemptId: number
  paperQuestionId: number
  title: string
  difficulty: number | null
  correct: boolean | null
  answered: boolean
  graded: boolean
}
export interface ExamPracticeCandidate {
  catalogId: string
  questionId: number
  title: string
  difficulty: number | null
  questionType: string
}
export interface ExamDiagnosisTopic {
  topicCode: string
  answeredCount: number
  gradedCount: number
  wrongCount: number
  unansweredCount: number
  ungradedCount: number
  evidenceLevel: 'INSUFFICIENT' | 'OBSERVED'
  observations: string[]
  evidence: ExamDiagnosisEvidence[]
  practiceCandidates: ExamPracticeCandidate[]
  materials: { knowledgeId: number; name: string }[]
  recommendedCount: number
  retestAfter: string | null
}
export interface ExamDiagnosis {
  ruleVersion: 'exam-diagnosis-v1'
  attemptId: number
  scope: 'EXAM_OBSERVATIONS_NOT_GLOBAL_MASTERY'
  objectiveAnsweredCount: number
  objectiveCorrectCount: number
  objectiveAccuracy: number | null
  unansweredCount: number
  ungradedCount: number
  topics: ExamDiagnosisTopic[]
  comparison: {
    previousAttemptId: number | null
    comparable: boolean
    currentObjectiveAccuracy: number | null
    previousObjectiveAccuracy: number | null
    note: string
  }
}
export const examDiagnosisApi = {
  get: (attemptId: number) => apiGet<ExamDiagnosis>(`/exam/attempts/${attemptId}/diagnosis`),
}
