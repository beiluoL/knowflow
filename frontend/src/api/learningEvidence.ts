import { apiGet, apiPost } from './request'
export type EvidenceSource = 'QUIZ_ANSWER' | 'EXAM_ANSWER' | 'FLASHCARD_REVIEW' | 'INTERVIEW_TURN'
export interface EvidenceAttemptRef { type: EvidenceSource; id: number }
export interface EvidenceReceipt {
  historyProjectionSemantics?: 'READ_TIME' | 'CAPTURE_TIME'
  historyProjection?: Pick<EvidenceReceipt, 'firstAttemptRef' | 'firstTrackedAttemptRef' | 'trackedHistoryCompleteness' | 'firstKnownAttemptRef' | 'historyCompleteness' | 'historyReasons' | 'latestQualifiedAttemptRef' | 'bestScore' | 'qualificationScope' | 'qualificationComplete' | 'qualificationScanLimit'>
  firstTrackedAttemptRef?: EvidenceAttemptRef | null; trackedHistoryCompleteness?: string
  firstAttemptRef?: EvidenceAttemptRef | null; firstKnownAttemptRef?: EvidenceAttemptRef
  historyCompleteness?: 'COMPLETE_FROM_TRACKING' | 'INCOMPLETE' | 'UNKNOWN'; historyReasons?: string[]
  latestQualifiedAttemptRef?: EvidenceAttemptRef | null; bestScore?: number | null
  qualificationScope?: string; qualificationComplete?: boolean; qualificationScanLimit?: number
  ruleVersion?: string | null; rubricVersion?: string | null; modelVersion?: string | null; modelIdentifier?: string | null; promptVersion?: string | null
  questionVersion?: number | string | null; answerExposed?: boolean | null; hintCount?: number | null
  answerMode?: string | null; assessmentStatus?: string; evaluatedAt?: string | null
  sourceType: EvidenceSource; sourceId: number; resourceKey: string; firstSourceId: number
  isFirstKnownAttempt: boolean; baselineSourceId: number | null; snapshotState: 'FROZEN' | 'LEGACY_UNAVAILABLE'
  assessmentKind: 'OBJECTIVE' | 'SELF_REPORTED' | 'AI_SUGGESTION' | 'ACTIVITY_ONLY' | 'JUDGE'
  score: number | null; maxScore: number | null; selfRating?: number; answer: string | null
  knowledgeIds: number[]; knowledgeKeys: string[]; occurredAt: string; capturedAt: string
  gradingRule: string | null; conditions: string | null; receiptSha: string
  questionSnapshot?: unknown; gradingBasis?: unknown
}
export interface EvidencePage { scannedCount?: number; totalSemantics?: 'OWN_RECEIPTS_INCLUDING_UNAVAILABLE'; items: EvidenceReceipt[]; page: number; size: number; total: number; unavailableCount: number; hasMore: boolean }
export interface EvidenceComparison { baseline: EvidenceReceipt; retest: EvidenceReceipt; explicitlyLinked: boolean; comparable: boolean; comparability?: 'COMPARABLE' | 'NOT_COMPARABLE'; reasons?: string[]; scoreDifference?: number; note: string }
export const learningEvidenceApi = {
  page: (page = 1, size = 10, sourceType?: EvidenceSource, knowledgeId?: number) => apiGet<EvidencePage>('/learning/evidence', { page, size, sourceType, knowledgeId }),
  get: (type: EvidenceSource, id: number) => apiGet<EvidenceReceipt>(`/learning/evidence/${type}/${id}`),
  capture: (type: EvidenceSource, id: number) => apiPost<EvidenceReceipt>(`/learning/evidence/${type}/${id}/capture`),
  compare: (baselineType: EvidenceSource, baselineId: number, retestType: EvidenceSource, retestId: number) => apiGet<EvidenceComparison>('/learning/evidence/comparisons', { baselineType, baselineId, retestType, retestId }),
  linkRetest: (baselineType: EvidenceSource, baselineId: number, retestType: EvidenceSource, retestId: number) => apiPost<EvidenceComparison>('/learning/evidence/retests', { baselineType, baselineId, retestType, retestId }),
}
