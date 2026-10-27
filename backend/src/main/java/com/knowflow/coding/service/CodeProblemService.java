package com.knowflow.coding.service;

import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeSubmission;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.common.PageResult;

import java.util.List;

/** 统一算法题库（CODE 题型）业务服务：用户练习/提交与管理端审核共用一套题目真源。 */
public interface CodeProblemService {

    // ==================== 用户侧 ====================

    PageResult<CodingVos.ProblemListItem> pagePublished(String keyword, String topicCode,
                                                        Integer difficulty, int page, int pageSize, Long userId);

    CodingVos.ProblemDetail detailForUser(Long questionId, Long userId);

    void saveDraft(Long userId, Long questionId, CodingVos.DraftRequest req);

    CodingVos.SubmitResult submitPractice(Long userId, Long questionId, CodingVos.SubmitRequest req);

    PageResult<CodingVos.SubmissionVO> mySubmissions(Long userId, Long questionId, int page, int pageSize);

    CodingVos.SubmissionVO mySubmission(Long userId, Long submissionId);

    /**
     * Phase 3H：对比本人同一题目的两次正式提交（确定性行级 LCS，无 AI）。
     * 服务端按提交时间自动确定旧 → 新方向；只回传持久化的真实判题指标。
     */
    CodingVos.SubmissionDiffVO diffSubmissions(Long userId, Long questionId, Long submissionAId, Long submissionBId);

    /**
     * Phase 3H：代码 AI 辅助（点评 / 解释 / 分级提示）。
     * AI 不可用时降级为预置引导 + 编译/测试结果 + 标准题解（通过后）；不触发判题、不改变判题结论。
     */
    CodingVos.AiAssistVO aiAssist(Long userId, Long questionId, CodingVos.AiAssistRequest req);

    List<CodingVos.TopicVO> listTopics();

    // ==================== 考试侧复用 ====================

    /** 加载 CODE 题扩展与全部用例（含隐藏）；供考试交卷判题使用，调用方不得外泄。 */
    CodeProblem loadProblemForJudge(Long questionId);

    List<CodeTestCase> loadAllCases(Long problemId);

    /**
     * 执行判题并落提交记录（EXAM 上下文不记错/不发练习事件，由考试结算统一处理）。
     * 返回的提交记录 score=null 表示平台侧失败，调用方必须保持 exam_answer 待判，不得记 0。
     */
    CodeSubmission judgeForContext(Long userId, Long questionId, String language, String sourceCode,
                                   boolean submitAll, String contextType, Long contextRefId);

    // ==================== 管理侧 ====================

    PageResult<?> adminPage(String keyword, String topicCode, Integer difficulty,
                            String reviewStatus, Integer status, int page, int pageSize);

    CodingVos.AdminProblemDetail adminDetail(Long questionId);

    Long saveProblem(CodingVos.SaveProblemRequest req, Long adminUserId);

    void review(Long questionId, String decision, String comment);

    void publish(Long questionId, boolean published);
}
