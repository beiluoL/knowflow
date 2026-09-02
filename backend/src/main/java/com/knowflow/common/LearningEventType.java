package com.knowflow.common;

/**
 * 学习行为事件类型（Learning Event System，Phase 1）。
 * <p>
 * 仅做“追加记录”，绝不替换任何原有业务表（quiz_answer_record / code_submit_record /
 * learning_mistake / wb_review_card / wb_recall_session / doc_read_progress /
 * chat_message / learning_user_chapter / user_check_in 等）。
 * 统一事件层是掌握度引擎（Phase 2）、AI 教练（Phase 4）、学习计划（Phase 5）的数据底座。
 */
public enum LearningEventType {

    /** 文档阅读（DocReadProgress 写入/updateReadProgress） */
    DOCUMENT_READ,

    /** 章节开始学习（首次视频进度上报 updateVideoProgress 插入） */
    CHAPTER_START,

    /** 章节完成（completeChapter 插入 LearningUserChapter） */
    CHAPTER_COMPLETE,

    /** 题目作答（每次提交 QuizAnswerRecord；结果通过 metadata.correct/score 表达，Phase 1 起为答题唯一核心事件） */
    QUESTION_ANSWERED,

    /** 题目答对（历史结果事件，Phase 1 起合并进 QUESTION_ANSWERED 的 metadata，不再单独落库） */
    QUESTION_CORRECT,

    /** 题目答错（历史结果事件，Phase 1 起合并进 QUESTION_ANSWERED 的 metadata，不再单独落库） */
    QUESTION_WRONG,

    /** 错题归集（learning_mistake 写入：题目错题 / 代码错题自动归集） */
    MISTAKE,

    /** 错题复习完成（markMastered 标记已掌握，作为一次真实复习行为的完成信号） */
    MISTAKE_REVIEW,

    /** 代码提交（CodeSubmitRecord 写入；result 通过 metadata.passed/total/passCount 表达） */
    CODE_SUBMITTED,

    /** 代码全部测试用例通过（历史结果事件，Phase 1 起合并进 CODE_SUBMITTED 的 metadata，不再单独落库） */
    CODE_PASSED,

    /** 代码未全部通过（历史结果事件，Phase 1 起合并进 CODE_SUBMITTED 的 metadata，不再单独落库） */
    CODE_FAILED,

    /** 闪卡 / 复习卡复习（SM-2 gradeReview 写 WbReviewLog） */
    FLASHCARD_REVIEWED,

    /** 学习任务完成（LearningTask.status 置为已完成时记录） */
    LEARNING_TASK_COMPLETE,

    /** 主动回忆会话完成（三轮闭卷默写 submitRecallRound 状态 COMPLETED） */
    RECALL_COMPLETED,

    /** AI 对话（ChatMessage 落库） */
    AI_CHAT,

    /** 知识图谱查看（KnowledgeController /graph、/entity-graph） */
    KNOWLEDGE_VIEWED,

    /** 学习路径全部章节完成（completeChapter 触发 G-CERT-01） */
    PATH_COMPLETED,

    /** 每日签到（CheckIn 写入 UserCheckIn） */
    CHECK_IN;
}
