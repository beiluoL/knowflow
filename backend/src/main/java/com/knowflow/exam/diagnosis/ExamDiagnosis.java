package com.knowflow.exam.diagnosis;

import java.time.LocalDateTime;
import java.util.List;

/** Exam-local observations, never a replacement for Evidence aggregation or global Mastery. */
public record ExamDiagnosis(String ruleVersion,Long attemptId,String scope,int objectiveAnsweredCount,
 int objectiveCorrectCount,Double objectiveAccuracy,int unansweredCount,int ungradedCount,
 List<Topic> topics,Comparison comparison) {
 public record Evidence(Long attemptId,Long paperQuestionId,String title,Integer difficulty,Boolean correct,boolean answered,boolean graded) {}
 public record Candidate(String catalogId,Long questionId,String title,Integer difficulty,String questionType) {}
 public record Material(Long knowledgeId,String name) {}
 public record Topic(String topicCode,int answeredCount,int gradedCount,int wrongCount,int unansweredCount,int ungradedCount,
  String evidenceLevel,List<String> observations,List<Evidence> evidence,List<Candidate> practiceCandidates,List<Material> materials,
  int recommendedCount,LocalDateTime retestAfter) {}
 public record Comparison(Long previousAttemptId,boolean comparable,Double currentObjectiveAccuracy,Double previousObjectiveAccuracy,String note) {}
}
