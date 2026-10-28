package com.knowflow.coding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.coding.entity.CodeDraft;
import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeSubmission;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.coding.entity.QuestionTopic;
import com.knowflow.coding.judge.CaseResult;
import com.knowflow.coding.judge.CodeJudgeGateway;
import com.knowflow.coding.judge.JudgeRequest;
import com.knowflow.coding.judge.JudgeStatus;
import com.knowflow.coding.judge.JudgeVerdict;
import com.knowflow.coding.mapper.CodeDraftMapper;
import com.knowflow.coding.mapper.CodeProblemMapper;
import com.knowflow.coding.mapper.CodeSubmissionMapper;
import com.knowflow.coding.mapper.CodeTestCaseMapper;
import com.knowflow.coding.mapper.QuestionTopicMapper;
import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.util.CodeDiff;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.common.LearningEventType;
import com.knowflow.common.PageResult;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.MistakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 算法题库服务实现。判题只经 {@link CodeJudgeGateway}；平台失败保持 score=null，不记错题。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeProblemServiceImpl implements CodeProblemService {

    private final org.mybatis.spring.SqlSessionTemplate catalogSessions;
    private QuizQuestion lockQuestion(Long id) {
        // Read the full current row under the lock; clearing cache alone cannot clear an InnoDB snapshot.
        catalogSessions.clearCache();
        QuizQuestion q = questionMapper.selectOne(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getId, id).last("FOR UPDATE"));
        if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
            throw new BusinessException(404, "算法题不存在");
        }
        return q;
    }
    private final QuizQuestionMapper questionMapper;
    private final CodeProblemMapper problemMapper;
    private final CodeTestCaseMapper caseMapper;
    private final CodeSubmissionMapper submissionMapper;
    private final CodeDraftMapper draftMapper;
    private final QuestionTopicMapper topicMapper;
    private final CodeJudgeGateway judgeGateway;
    @org.springframework.beans.factory.annotation.Value("${knowflow.product.java-practice.enabled:false}")
    private boolean javaPracticeEnabled;
    private final com.knowflow.learning.actions.PersonalLearningResourceScope personalScope;
    private final com.knowflow.mapper.DocDocumentMapper sourceDocuments;

    /** 所有用户入口共享个人学习资格；ADMIN管理权限不能用来旁路私人来源。 */
    private QuizQuestion requirePersonalQuestion(Long questionId, Long userId) {
        QuizQuestion q = requireVisibleQuestion(questionId);
        if (javaPracticeEnabled && (!personalScope.category(q.getCategoryId(), userId)
                || (q.getDocId()!=null && !personalScope.document(sourceDocuments.selectById(q.getDocId()), userId))))
            throw new BusinessException(404,"算法题不存在或无权访问");
        return q;
    }
    private final MistakeService mistakeService;
    private final LearningEventService learningEventService;
    private final ObjectMapper objectMapper;
    private final AiService aiService;

    private static final int MAX_SOURCE_BYTES = 128 * 1024;

    // ==================== 用户侧：列表/详情 ====================

    @Override
    public PageResult<CodingVos.ProblemListItem> pagePublished(String keyword, String topicCode,
                                                               Integer difficulty, int page, int pageSize, Long userId) {
        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 50));
        LambdaQueryWrapper<QuizQuestion> qw = new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuestionType, QuizQuestion.TYPE_CODE)
                .eq(QuizQuestion::getStatus, 1)
                .and(w -> w.isNull(QuizQuestion::getReviewStatus)
                        .or().eq(QuizQuestion::getReviewStatus, QuizQuestion.REVIEW_APPROVED));
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(QuizQuestion::getTitle, kw).or().like(QuizQuestion::getContent, kw)
                    .or().like(QuizQuestion::getTags, kw));
        }
        if (topicCode != null && !topicCode.isBlank()) {
            // 二级分类查询同时命中其子节点（前端传一级时由 controller 展开）
            qw.eq(QuizQuestion::getTopicCode, topicCode.trim());
        }
        if (difficulty != null) {
            qw.eq(QuizQuestion::getDifficulty, difficulty);
        }
        qw.orderByAsc(QuizQuestion::getDifficulty).orderByDesc(QuizQuestion::getUpdateTime)
                .orderByDesc(QuizQuestion::getId);
        Page<QuizQuestion> pg = questionMapper.selectPage(new Page<>(p, size), qw);

        Map<String, String> topicNames = topicNameMap();
        Map<Long, String> myStatus = myStatusMap(userId, pg.getRecords().stream().map(QuizQuestion::getId).toList());
        List<CodingVos.ProblemListItem> items = pg.getRecords().stream().map(q -> {
            CodingVos.ProblemListItem it = new CodingVos.ProblemListItem();
            it.setId(q.getId());
            it.setTitle(q.getTitle());
            it.setDifficulty(q.getDifficulty());
            it.setTopicCode(q.getTopicCode());
            it.setTopicName(topicNames.get(q.getTopicCode()));
            it.setTags(q.getTags());
            it.setMyStatus(myStatus.getOrDefault(q.getId(), "NONE"));
            it.setSubmissionCount(0);
            return it;
        }).toList();
        PageResult<CodingVos.ProblemListItem> out = PageResult.of(new Page<CodingVos.ProblemListItem>(p, size, pg.getTotal()));
        out.setRecords(items);
        return out;
    }

    @Override
    public CodingVos.ProblemDetail detailForUser(Long questionId, Long userId) {
        QuizQuestion q = requirePersonalQuestion(questionId, userId);
        CodeProblem problem = problemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                .eq(CodeProblem::getQuestionId, questionId));
        if (problem == null) {
            throw new BusinessException(404, "算法题数据不完整");
        }
        CodingVos.ProblemDetail d = new CodingVos.ProblemDetail();
        d.setId(q.getId());
        d.setTitle(q.getTitle());
        d.setContent(q.getContent());
        d.setDifficulty(q.getDifficulty());
        d.setTopicCode(q.getTopicCode());
        QuestionTopic topic = q.getTopicCode() == null ? null
                : topicMapper.selectOne(new LambdaQueryWrapper<QuestionTopic>().eq(QuestionTopic::getCode, q.getTopicCode()));
        d.setTopicName(topic == null ? null : topic.getName());
        d.setTags(q.getTags());
        d.setSupportedLanguages(problem.getSupportedLanguages());
        d.setFunctionSignature(problem.getFunctionSignature());
        d.setStarterCode(problem.getStarterCode());
        d.setConstraints(problem.getConstraints());
        d.setExamples(problem.getExamples());
        d.setTimeLimitMs(problem.getTimeLimitMs());
        d.setMemoryLimitMb(problem.getMemoryLimitMb());
        d.setJudgeMode(problem.getJudgeMode());

        // 仅返回公开用例；隐藏用例 input/expected 绝不下发
        List<CodingVos.TestCaseView> publicCases = caseMapper.selectList(new LambdaQueryWrapper<CodeTestCase>()
                        .eq(CodeTestCase::getProblemId, problem.getId())
                        .eq(CodeTestCase::getVisibility, CodeTestCase.VIS_PUBLIC)
                        .orderByAsc(CodeTestCase::getOrderNo)).stream()
                .map(tc -> {
                    CodingVos.TestCaseView v = new CodingVos.TestCaseView();
                    v.setOrderNo(tc.getOrderNo());
                    v.setInput(tc.getInput());
                    v.setExpectedOutput(tc.getExpectedOutput());
                    v.setWeight(tc.getWeight());
                    return v;
                }).toList();
        d.setPublicCases(publicCases);

        CodeDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<CodeDraft>()
                .eq(CodeDraft::getUserId, userId).eq(CodeDraft::getQuestionId, questionId));
        d.setDraftCode(draft == null ? null : draft.getSourceCode());

        boolean solved = submissionMapper.selectCount(new LambdaQueryWrapper<CodeSubmission>()
                .eq(CodeSubmission::getUserId, userId)
                .eq(CodeSubmission::getQuestionId, questionId)
                .eq(CodeSubmission::getSubmissionType, CodeSubmission.TYPE_SUBMIT)
                .eq(CodeSubmission::getJudgeStatus, JudgeStatus.ACCEPTED)) > 0;
        d.setSolved(solved);
        // 题解/复杂度仅本人通过后开放
        if (solved) {
            d.setSolutionExplanation(problem.getSolutionExplanation());
            d.setComplexityAnalysis(problem.getComplexityAnalysis());
            d.setFollowUps(problem.getFollowUps());
            d.setCommonMistakes(problem.getCommonMistakes());
        }
        d.setJudgeExecutionEnabled(judgeGateway.executionEnabled());
        d.setJudgeMode2(judgeGateway.mode());
        return d;
    }

    // ==================== 草稿 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long userId, Long questionId, CodingVos.DraftRequest req) {
        requirePersonalQuestion(questionId, userId);
        String lang = normalizeLanguage(req.getLanguage());
        String code = req.getSourceCode() == null ? "" : req.getSourceCode();
        if (code.getBytes().length > MAX_SOURCE_BYTES) {
            throw new BusinessException(400, "代码超过大小限制（128KB）");
        }
        CodeDraft existing = draftMapper.selectOne(new LambdaQueryWrapper<CodeDraft>()
                .eq(CodeDraft::getUserId, userId).eq(CodeDraft::getQuestionId, questionId));
        if (existing == null) {
            CodeDraft draft = new CodeDraft();
            draft.setUserId(userId);
            draft.setQuestionId(questionId);
            draft.setLanguage(lang);
            draft.setSourceCode(code);
            try {
                draftMapper.insert(draft);
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // 并发首存：唯一索引兜底，改为更新
                CodeDraft again = draftMapper.selectOne(new LambdaQueryWrapper<CodeDraft>()
                        .eq(CodeDraft::getUserId, userId).eq(CodeDraft::getQuestionId, questionId));
                if (again != null) {
                    again.setLanguage(lang);
                    again.setSourceCode(code);
                    draftMapper.updateById(again);
                }
            }
        } else {
            existing.setLanguage(lang);
            existing.setSourceCode(code);
            draftMapper.updateById(existing);
        }
    }

    // ==================== 提交判题 ====================

    @Override
    public CodingVos.SubmitResult submitPractice(Long userId, Long questionId, CodingVos.SubmitRequest req) {
        requirePersonalQuestion(questionId, userId);
        boolean runOnly = CodeSubmission.TYPE_RUN.equalsIgnoreCase(req.getType());
        CodeSubmission saved = judgeForContext(userId, questionId,
                normalizeLanguage(req.getLanguage()), req.getSourceCode(),
                !runOnly, CodeSubmission.CONTEXT_PRACTICE, null);

        QuizQuestion q = questionMapper.selectById(questionId);
        // 只有正式提交（SUBMIT）才记学习事件/错题；RUN 是自助试运行
        if (!runOnly) {
            recordPracticeOutcome(userId, q, saved);
        }
        return toSubmitResult(saved);
    }

    /** 事件 + 错题：平台失败不发事件、不记错；score 保持 null（System Error 不是用户答错）。 */
    private void recordPracticeOutcome(Long userId, QuizQuestion q, CodeSubmission sub) {
        if (JudgeStatus.isPlatformFailure(sub.getJudgeStatus())) {
            return;
        }
        boolean accepted = JudgeStatus.ACCEPTED.equals(sub.getJudgeStatus());
        learningEventService.record(userId, LearningEventType.CODE_SUBMITTED, "CODE_QUESTION", q.getId(),
                Map.of("language", sub.getLanguage() == null ? "java" : sub.getLanguage(),
                        "passed", accepted,
                        "judgeStatus", sub.getJudgeStatus(),
                        "total", sub.getTotalCount() == null ? 0 : sub.getTotalCount(),
                        "passCount", sub.getPassedCount() == null ? 0 : sub.getPassedCount(),
                        "platformFailure", JudgeStatus.isPlatformFailure(sub.getJudgeStatus())));
        if (JudgeStatus.isUserFailure(sub.getJudgeStatus())) {
            LearningMistake mistake = new LearningMistake();
            mistake.setQuestion(q.getTitle());
            mistake.setWrongAnswer(statusLabel(sub.getJudgeStatus())
                    + (sub.getPassedCount() != null && sub.getTotalCount() != null
                    ? "（" + sub.getPassedCount() + "/" + sub.getTotalCount() + " 用例通过）" : ""));
            mistake.setCorrectAnswer("通过全部公开与隐藏用例；题解在通过后开放");
            mistake.setCategory(q.getTopicCode() == null ? "算法" : q.getTopicCode());
            mistake.setDifficulty(q.getDifficulty());
            mistake.setSource("CODE");
            mistake.setQuestionId(q.getId());
            try {
                mistakeService.addMistake(mistake, userId);
            } catch (Exception e) {
                log.warn("代码错题归集失败 questionId={}: {}", q.getId(), e.getMessage());
            }
        }
    }

    private String statusLabel(String s) {
        return switch (s) {
            case JudgeStatus.WRONG_ANSWER -> "答案错误";
            case JudgeStatus.COMPILE_ERROR -> "编译错误";
            case JudgeStatus.RUNTIME_ERROR -> "运行时错误";
            case JudgeStatus.TIME_LIMIT_EXCEEDED -> "超时";
            case JudgeStatus.MEMORY_LIMIT_EXCEEDED -> "超内存";
            default -> s;
        };
    }

    @Override
    public CodeSubmission judgeForContext(Long userId, Long questionId, String language, String sourceCode,
                                          boolean submitAll, String contextType, Long contextRefId) {
        CodeProblem problem = loadProblemForJudge(questionId);
        List<CodeTestCase> allCases = loadAllCases(problem.getId());
        String lang = normalizeLanguage(language);
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new BusinessException(400, "代码不能为空");
        }
        if (sourceCode.getBytes().length > MAX_SOURCE_BYTES) {
            throw new BusinessException(400, "代码超过大小限制（128KB）");
        }
        if (problem.getSupportedLanguages() != null
                && !List.of(problem.getSupportedLanguages().split(",")).contains(lang)) {
            throw new BusinessException(400, "本题不支持语言：" + lang);
        }
        CodeSubmission sub = new CodeSubmission();
        sub.setUserId(userId);
        sub.setQuestionId(questionId);
        sub.setProblemVersion(problem.getTestCaseVersion());
        sub.setLanguage(lang);
        sub.setSourceCode(sourceCode);
        sub.setSubmissionType(submitAll ? CodeSubmission.TYPE_SUBMIT : CodeSubmission.TYPE_RUN);
        sub.setJudgeStatus(JudgeStatus.QUEUED);
        sub.setPassedCount(0);
        sub.setTotalCount(0);
        sub.setContextType(contextType);
        sub.setContextRefId(contextRefId);
        submissionMapper.insert(sub);

        JudgeVerdict verdict;
        try {
            // F11开启后，旧提交/考试入口也不能绕过独立隔离门禁调用宿主或mock。
            if (javaPracticeEnabled && !("ISOLATED".equals(judgeGateway.mode())
                    && judgeGateway.executionEnabled() && judgeGateway.isolatedExecutionVerified())) {
                verdict = new JudgeVerdict(JudgeStatus.JUDGE_UNAVAILABLE, null, 0, 0,
                        null, null, null, List.of(), judgeGateway.mode(), Map.of("reason", "ISOLATION_NOT_VERIFIED"));
            } else {
                verdict = judgeGateway.judge(new JudgeRequest(userId, questionId, lang, sourceCode,
                        submitAll, problem, allCases));
            }
        } catch (Exception e) {
            log.warn("[Judge] 网关异常 questionId={}: {}", questionId, e.getMessage());
            verdict = new JudgeVerdict(JudgeStatus.SYSTEM_ERROR, null, 0,
                    submitAll ? allCases.size() : (int) allCases.stream()
                            .filter(c -> CodeTestCase.VIS_PUBLIC.equals(c.getVisibility())).count(),
                    null, null, null, List.of(), judgeGateway.mode(),
                    Map.of("exception", e.getClass().getSimpleName()));
        }

        sub.setJudgeStatus(verdict.status());
        sub.setScore(verdict.score());
        sub.setPassedCount(verdict.passedCount());
        sub.setTotalCount(verdict.totalCount());
        sub.setExecutionTimeMs(verdict.executionTimeMs());
        sub.setMemoryUsedKb(verdict.memoryUsedKb());
        sub.setCompileOutput(verdict.compileOutput());
        sub.setCaseResults(writeJson(verdict.cases() == null ? List.of()
                : verdict.cases().stream().map(CaseResult::toView).toList()));
        Map<String, Object> detail = new HashMap<>(verdict.detail() == null ? Map.of() : verdict.detail());
        detail.put("runnerMode", verdict.runnerMode());
        sub.setJudgeDetail(writeJson(detail));
        submissionMapper.updateById(sub);
        return sub;
    }

    @Override
    public CodeProblem loadProblemForJudge(Long questionId) {
        CodeProblem problem = problemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                .eq(CodeProblem::getQuestionId, questionId));
        if (problem == null) {
            throw new BusinessException(404, "算法题扩展数据不存在");
        }
        return problem;
    }

    @Override
    public List<CodeTestCase> loadAllCases(Long problemId) {
        return caseMapper.selectList(new LambdaQueryWrapper<CodeTestCase>()
                .eq(CodeTestCase::getProblemId, problemId)
                .orderByAsc(CodeTestCase::getOrderNo));
    }

    // ==================== 提交历史 ====================

    @Override
    public PageResult<CodingVos.SubmissionVO> mySubmissions(Long userId, Long questionId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 50));
        LambdaQueryWrapper<CodeSubmission> qw = new LambdaQueryWrapper<CodeSubmission>()
                .eq(CodeSubmission::getUserId, userId)
                .eq(CodeSubmission::getSubmissionType, CodeSubmission.TYPE_SUBMIT)
                .orderByDesc(CodeSubmission::getId);
        if (questionId != null) {
            qw.eq(CodeSubmission::getQuestionId, questionId);
        }
        Page<CodeSubmission> pg = submissionMapper.selectPage(new Page<>(p, size), qw);
        List<CodingVos.SubmissionVO> records = pg.getRecords().stream().map(s -> toSubmissionVO(s, false)).toList();
        PageResult<CodingVos.SubmissionVO> out = PageResult.of(new Page<CodingVos.SubmissionVO>(p, size, pg.getTotal()));
        out.setRecords(records);
        return out;
    }

    @Override
    public CodingVos.SubmissionVO mySubmission(Long userId, Long submissionId) {
        CodeSubmission s = submissionMapper.selectById(submissionId);
        if (s == null || !Objects.equals(s.getUserId(), userId)) {
            throw new BusinessException(404, "提交记录不存在");
        }
        if (javaPracticeEnabled) requirePersonalQuestion(s.getQuestionId(), userId);
        return toSubmissionVO(s, true);
    }

    @Override
    public CodingVos.SubmissionDiffVO diffSubmissions(Long userId, Long questionId,
                                                      Long submissionAId, Long submissionBId) {
        if (submissionAId == null || submissionBId == null
                || Objects.equals(submissionAId, submissionBId)) {
            throw new BusinessException(400, "请选择两条不同的提交进行对比");
        }
        if (javaPracticeEnabled) requirePersonalQuestion(questionId, userId);
        CodeSubmission a = submissionMapper.selectById(submissionAId);
        CodeSubmission b = submissionMapper.selectById(submissionBId);
        // 归属与范围校验：必须都是本人、本题、正式提交（RUN 临时运行不进对比）
        for (CodeSubmission s : new CodeSubmission[]{a, b}) {
            if (s == null || !Objects.equals(s.getUserId(), userId)) {
                throw new BusinessException(404, "提交记录不存在");
            }
            if (!Objects.equals(s.getQuestionId(), questionId)) {
                throw new BusinessException(400, "只能对比同一题目的提交");
            }
            if (!CodeSubmission.TYPE_SUBMIT.equals(s.getSubmissionType())) {
                throw new BusinessException(400, "仅支持对比正式提交（SUBMIT），「运行示例」不保留为可对比版本");
            }
        }
        // 方向由时间确定（相同时间用 id 兜底），避免前端传反
        boolean aFirst = a.getCreateTime() == null || (b.getCreateTime() != null
                && (a.getCreateTime().isBefore(b.getCreateTime())
                    || (a.getCreateTime().equals(b.getCreateTime()) && a.getId() < b.getId())));
        CodeSubmission older = aFirst ? a : b;
        CodeSubmission newer = aFirst ? b : a;

        CodeDiff.Result diff = CodeDiff.diff(older.getSourceCode(), newer.getSourceCode());
        CodingVos.SubmissionDiffVO vo = new CodingVos.SubmissionDiffVO();
        vo.setFrom(toDiffRef(older));
        vo.setTo(toDiffRef(newer));
        vo.setLines(diff.lines());
        vo.setChangedLines(diff.changedLines());
        vo.setTruncated(diff.truncated());
        return vo;
    }

    private CodingVos.SubmissionDiffVO.DiffRef toDiffRef(CodeSubmission s) {
        CodingVos.SubmissionDiffVO.DiffRef ref = new CodingVos.SubmissionDiffVO.DiffRef();
        ref.setId(s.getId());
        ref.setJudgeStatus(s.getJudgeStatus());
        ref.setScore(s.getScore());
        ref.setPassedCount(s.getPassedCount());
        ref.setTotalCount(s.getTotalCount());
        ref.setExecutionTimeMs(s.getExecutionTimeMs());
        ref.setMemoryUsedKb(s.getMemoryUsedKb());
        ref.setCreateTime(s.getCreateTime() == null ? LocalDateTime.now().toString() : s.getCreateTime().toString());
        return ref;
    }

    // ==================== Phase 3H：AI 辅助（点评/解释/分级提示） ====================

    @Override
    public CodingVos.AiAssistVO aiAssist(Long userId, Long questionId, CodingVos.AiAssistRequest req) {
        QuizQuestion q = requirePersonalQuestion(questionId, userId);
        CodeProblem problem = problemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                .eq(CodeProblem::getQuestionId, questionId));
        if (problem == null) {
            throw new BusinessException(404, "算法题数据不完整");
        }
        String kind = req == null || req.getKind() == null || req.getKind().isBlank()
                ? "review" : req.getKind().trim().toLowerCase();
        if (!List.of("review", "explain", "hint").contains(kind)) {
            throw new BusinessException(400, "kind 仅支持 review / explain / hint");
        }
        int hintLevel = 0;
        if ("hint".equals(kind)) {
            hintLevel = req.getHintLevel() == null ? 1 : Math.max(1, Math.min(5, req.getHintLevel()));
        }

        // 指定提交必须归属本人且属于本题；否则取最近一次提交
        CodeSubmission sub = null;
        if (req != null && req.getSubmissionId() != null) {
            sub = submissionMapper.selectById(req.getSubmissionId());
            if (sub == null || !Objects.equals(sub.getUserId(), userId)
                    || !Objects.equals(sub.getQuestionId(), questionId)) {
                throw new BusinessException(404, "提交记录不存在");
            }
        } else {
            List<CodeSubmission> latest = submissionMapper.selectList(new LambdaQueryWrapper<CodeSubmission>()
                    .eq(CodeSubmission::getUserId, userId)
                    .eq(CodeSubmission::getQuestionId, questionId)
                    .orderByDesc(CodeSubmission::getId).last("limit 1"));
            if (!latest.isEmpty()) {
                sub = latest.get(0);
            }
        }

        boolean solved = submissionMapper.selectCount(new LambdaQueryWrapper<CodeSubmission>()
                .eq(CodeSubmission::getUserId, userId)
                .eq(CodeSubmission::getQuestionId, questionId)
                .eq(CodeSubmission::getSubmissionType, CodeSubmission.TYPE_SUBMIT)
                .eq(CodeSubmission::getJudgeStatus, JudgeStatus.ACCEPTED)) > 0;

        CodingVos.AiAssistVO vo = new CodingVos.AiAssistVO();
        vo.setQuestionId(questionId);
        vo.setKind(kind);
        vo.setHintLevel("hint".equals(kind) ? hintLevel : null);
        vo.setSubmissionId(sub == null ? null : sub.getId());
        vo.setJudgeExecutionEnabled(judgeGateway.executionEnabled());
        if (sub != null) {
            // 判题事实原样回显：AI 点评永远不改变 Accepted / Wrong Answer
            vo.setJudgeStatus(sub.getJudgeStatus());
            vo.setScore(sub.getScore());
            vo.setPassedCount(sub.getPassedCount());
            vo.setTotalCount(sub.getTotalCount());
            vo.setCompileOutput(sub.getCompileOutput());
            vo.setExecutionTimeMs(sub.getExecutionTimeMs());
        }
        // 标准题解仅本人通过后开放（与详情接口同一门禁）
        if (solved) {
            vo.setSolutionExplanation(problem.getSolutionExplanation());
            vo.setComplexityAnalysis(problem.getComplexityAnalysis());
            vo.setCommonMistakes(problem.getCommonMistakes());
        }

        String sourceCode = sub != null ? sub.getSourceCode() : null;
        if (sourceCode == null || sourceCode.isBlank()) {
            CodeDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<CodeDraft>()
                    .eq(CodeDraft::getUserId, userId).eq(CodeDraft::getQuestionId, questionId));
            sourceCode = draft == null ? null : draft.getSourceCode();
        }

        // AI 可选增强：配置可用且有代码时尝试；失败按 kind 走预置降级。绝不触发判题。
        if (sourceCode != null && !sourceCode.isBlank() && aiService.isConfigured(userId)) {
            try {
                String systemPrompt = buildAssistSystemPrompt(kind, hintLevel);
                String userPrompt = buildAssistUserPrompt(q, problem, sub, sourceCode, kind, hintLevel);
                String out = aiService.complete(systemPrompt, userPrompt, null, userId);
                if (out != null && !out.isBlank()) {
                    vo.setMode("AI");
                    vo.setContent(out.trim());
                    return vo;
                }
                vo.setFailureKind("AI_INVALID_RESPONSE");
            } catch (AiCapabilityException e) {
                log.warn("代码 AI 辅助降级 userId={} kind={} reason={}", userId, kind, e.getKind());
                vo.setFailureKind("AI_" + e.getKind().name());
            } catch (Exception e) {
                log.warn("代码 AI 辅助降级 userId={} kind={}: {}", userId, kind, e.getMessage());
                vo.setFailureKind("AI_UNKNOWN");
            }
        } else if (sourceCode == null || sourceCode.isBlank()) {
            vo.setFailureKind("NO_SUBMISSION");
        } else {
            vo.setFailureKind("AI_UNCONFIGURED");
        }

        vo.setMode("BASIC");
        vo.setContent(buildPresetAssist(q, problem, sub, solved, kind, hintLevel));
        return vo;
    }

    private String buildAssistSystemPrompt(String kind, int hintLevel) {
        if ("hint".equals(kind)) {
            return "你是一名算法教学老师。用户请求第 " + hintLevel + " 级提示（共 5 级："
                    + "1 理解题意、2 算法思路、3 关键数据结构、4 伪代码、5 完整题解）。"
                    + "只能给出第 " + hintLevel + " 级对应粒度的引导：低级别严禁直接给答案或完整代码，"
                    + "用提问和分步引导为主；第 5 级可以给出完整思路与参考实现讲解。"
                    + "不要泄露隐藏测试用例，不要编造运行时间与内存指标。用中文 Markdown 回答。";
        }
        if ("explain".equals(kind)) {
            return "你是一名 Java 算法助教。逐段解释用户代码的执行过程，指出边界条件处理是否完备，"
                    + "分析时间复杂度与空间复杂度，并给出可选优化方向。"
                    + "只能依据用户代码与已知判题事实评论，不得编造运行时间/内存数据，结论标注为 AI 建议。"
                    + "用中文 Markdown 回答。";
        }
        return "你是一名资深 Java 代码评审。从四个维度点评用户代码：1) 解题思路与正确性；"
                + "2) 时间与空间复杂度；3) 可读性与命名；4) 具体可执行的改进建议。"
                + "已知判题结果是事实，不得推翻或重新判定（不改变 Accepted / Wrong Answer）；"
                + "不得编造运行时间/内存指标；不要改写整份代码，只给关键片段建议。用中文 Markdown 回答。";
    }

    private String buildAssistUserPrompt(QuizQuestion q, CodeProblem problem, CodeSubmission sub,
                                         String sourceCode, String kind, int hintLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("题目：").append(q.getTitle()).append("\n\n").append(q.getContent()).append("\n");
        if (problem.getConstraints() != null && !problem.getConstraints().isBlank()) {
            sb.append("\n约束：\n").append(problem.getConstraints()).append("\n");
        }
        if (problem.getExamples() != null && !problem.getExamples().isBlank()) {
            sb.append("\n示例：\n").append(problem.getExamples()).append("\n");
        }
        if (sub != null && sub.getJudgeStatus() != null) {
            sb.append("\n最近一次提交的判题事实：status=").append(sub.getJudgeStatus());
            if (sub.getPassedCount() != null && sub.getTotalCount() != null) {
                sb.append("，通过 ").append(sub.getPassedCount()).append("/").append(sub.getTotalCount()).append(" 个用例");
            }
            if (sub.getCompileOutput() != null && !sub.getCompileOutput().isBlank()) {
                sb.append("\n编译/运行输出：\n").append(sub.getCompileOutput());
            }
            sb.append("\n（平台失败如 SYSTEM_ERROR/JUDGE_UNAVAILABLE 不算用户错误）\n");
        }
        if ("hint".equals(kind)) {
            sb.append("\n请只给第 ").append(hintLevel).append(" 级提示。\n");
        }
        sb.append("\n用户代码（").append(sub == null ? "草稿" : sub.getLanguage()).append("）：\n")
                .append(sourceCode).append("\n");
        return sb.toString();
    }

    /**
     * 预置降级内容：经过审核的通用引导 + 已知判题事实 +（通过后的）标准题解。
     * 不包含任何模型生成内容，AI 关闭时用户仍能获得确定性帮助。
     */
    private String buildPresetAssist(QuizQuestion q, CodeProblem problem, CodeSubmission sub,
                                     boolean solved, String kind, int hintLevel) {
        if ("hint".equals(kind)) {
            return switch (hintLevel) {
                case 1 -> "【提示 1/5 · 理解题意】\n先不要想怎么写代码。请用自己的话复述："
                        + "输入是什么、要求输出什么、数据范围多大？列出边界情况（空输入、单元素、最大值、重复元素），"
                        + "再把题目示例手工演算一遍，确认你理解的输入输出与示例一致。";
                case 2 -> "【提示 2/5 · 算法思路】\n先写一个最朴素的暴力解法并估算其复杂度，"
                        + "然后找出其中重复计算最多的环节，问自己：它能否被排序、哈希查找、双指针、单调栈、"
                        + "二分或动态规划消除？选一个方向把思路讲成一句话，再验证它在边界情况成立。";
                case 3 -> "【提示 3/5 · 关键数据结构】\n根据上一步的策略确定数据结构："
                        + "需要按值快速定位→HashMap/HashSet；维护两端→队列/双指针；持续取最值→堆；"
                        + "频繁求区间和→前缀和；状态可复用→DP 数组。先写清楚每个结构存的是什么、键和值的含义。";
                case 4 -> "【提示 4/5 · 伪代码】\n按「初始化 → 循环不变量 → 状态转移 → 终止条件」写伪代码，"
                        + "并用一个示例手工走查，确认每轮循环结束后各变量的含义与不变量保持一致，最后再翻译成 Java。";
                default -> solved
                        ? "【提示 5/5 · 完整题解】\n你已通过本题，页面下方已展示标准题解与复杂度分析，"
                            + "建议对照你的实现比较：思路是否一致、常数与边界处理谁更简洁，并阅读「常见错误」复盘。"
                        : "【提示 5/5 · 完整题解暂未解锁】\n标准题解在你独立通过本题后开放，避免提前剧透。"
                            + "请先按 1-4 级提示完成并提交；通过后回到这里可查看完整题解、复杂度分析与常见错误。";
            };
        }
        StringBuilder sb = new StringBuilder();
        sb.append(kind.equals("explain") ? "【基础模式 · 代码解释】\n" : "【基础模式 · 代码点评】\n");
        sb.append("AI 点评暂时不可用，以下为不依赖模型的确定性信息，请据此自查：\n");
        if (sub != null && sub.getJudgeStatus() != null) {
            sb.append("- 判题结果：").append(sub.getJudgeStatus());
            if (sub.getPassedCount() != null && sub.getTotalCount() != null) {
                sb.append("（通过 ").append(sub.getPassedCount()).append("/").append(sub.getTotalCount()).append(" 个用例）");
            }
            if (JudgeStatus.isPlatformFailure(sub.getJudgeStatus())) {
                sb.append("\n- 该状态属于平台侧失败，不计为你的答案错误，可稍后重新提交。");
            }
            sb.append("\n");
            if (sub.getCompileOutput() != null && !sub.getCompileOutput().isBlank()) {
                sb.append("- 编译/运行输出：\n```\n").append(sub.getCompileOutput()).append("\n```\n");
            }
        } else {
            sb.append("- 你还没有提交记录，先点击「运行」验证公开用例，再「提交」进行完整判题。\n");
        }
        sb.append("- 自查清单：是否处理空/单元素/最大值边界？循环下标与终止条件是否正确？")
                .append("整型是否可能溢出？容器的键是否可能为 null？\n");
        if (solved) {
            sb.append("- 你已通过本题，可对照页面中的标准题解、复杂度分析与常见错误进行复盘。\n");
        } else {
            sb.append("- 标准题解在通过后解锁；可使用分级提示（1-5 级）获得循序渐进的引导。\n");
        }
        return sb.toString();
    }

    @Override
    public List<CodingVos.TopicVO> listTopics() {
        return topicMapper.selectList(new LambdaQueryWrapper<QuestionTopic>()
                        .eq(QuestionTopic::getStatus, 1)
                        .orderByAsc(QuestionTopic::getLevel)
                        .orderByAsc(QuestionTopic::getSortOrder)
                        .orderByAsc(QuestionTopic::getCode)).stream()
                .map(t -> {
                    CodingVos.TopicVO v = new CodingVos.TopicVO();
                    v.setCode(t.getCode());
                    v.setName(t.getName());
                    v.setParentCode(t.getParentCode());
                    v.setLevel(t.getLevel());
                    v.setSortOrder(t.getSortOrder());
                    return v;
                }).toList();
    }

    // ==================== 管理侧 ====================

    @Override
    public PageResult<QuizQuestion> adminPage(String keyword, String topicCode, Integer difficulty,
                                              String reviewStatus, Integer status, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));
        LambdaQueryWrapper<QuizQuestion> qw = new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuestionType, QuizQuestion.TYPE_CODE);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(QuizQuestion::getTitle, keyword.trim())
                    .or().like(QuizQuestion::getContent, keyword.trim()));
        }
        if (topicCode != null && !topicCode.isBlank()) {
            qw.eq(QuizQuestion::getTopicCode, topicCode.trim());
        }
        if (difficulty != null) {
            qw.eq(QuizQuestion::getDifficulty, difficulty);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            if ("NULL".equalsIgnoreCase(reviewStatus)) {
                qw.isNull(QuizQuestion::getReviewStatus);
            } else {
                qw.eq(QuizQuestion::getReviewStatus, reviewStatus.trim().toUpperCase());
            }
        }
        if (status != null) {
            qw.eq(QuizQuestion::getStatus, status);
        }
        qw.orderByDesc(QuizQuestion::getId);
        return PageResult.of(questionMapper.selectPage(new Page<>(p, size), qw));
    }

    @Override
    public CodingVos.AdminProblemDetail adminDetail(Long questionId) {
        QuizQuestion q = questionMapper.selectById(questionId);
        if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
            throw new BusinessException(404, "算法题不存在");
        }
        CodingVos.AdminProblemDetail d = new CodingVos.AdminProblemDetail();
        d.setId(q.getId());
        d.setTitle(q.getTitle());
        d.setContent(q.getContent());
        d.setDifficulty(q.getDifficulty());
        d.setTopicCode(q.getTopicCode());
        d.setTags(q.getTags());
        d.setCanonicalKey(q.getCanonicalKey());
        d.setStatus(q.getStatus());
        d.setReviewStatus(q.getReviewStatus());
        d.setVersion(q.getVersion());
        d.setCreatorId(q.getCreatorId());
        CodeProblem problem = problemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                .eq(CodeProblem::getQuestionId, questionId));
        if (problem != null) {
            d.setProblemJson(writeJson(problemSafeMap(problem)));
            d.setCases(caseMapper.selectList(new LambdaQueryWrapper<CodeTestCase>()
                            .eq(CodeTestCase::getProblemId, problem.getId())
                            .orderByAsc(CodeTestCase::getOrderNo)).stream()
                    .map(tc -> {
                        CodingVos.SaveCaseRequest c = new CodingVos.SaveCaseRequest();
                        c.setInput(tc.getInput());
                        c.setExpectedOutput(tc.getExpectedOutput());
                        c.setVisibility(tc.getVisibility());
                        c.setWeight(tc.getWeight());
                        c.setOrderNo(tc.getOrderNo());
                        return c;
                    }).toList());
        }
        return d;
    }

    /** 管理端返回的 problem JSON 含参考解答（full=true，仅 ADMIN 接口可达）。 */
    private Map<String, Object> problemSafeMap(CodeProblem p) {
        Map<String, Object> m = new HashMap<>();
        m.put("supportedLanguages", p.getSupportedLanguages());
        m.put("functionSignature", p.getFunctionSignature());
        m.put("starterCode", p.getStarterCode());
        m.put("constraints", p.getConstraints());
        m.put("examples", p.getExamples());
        m.put("timeLimitMs", p.getTimeLimitMs());
        m.put("memoryLimitMb", p.getMemoryLimitMb());
        m.put("judgeMode", p.getJudgeMode());
        m.put("checker", p.getChecker());
        m.put("toleranceValue", p.getToleranceValue());
        m.put("referenceSolution", p.getReferenceSolution());
        m.put("solutionExplanation", p.getSolutionExplanation());
        m.put("complexityAnalysis", p.getComplexityAnalysis());
        m.put("followUps", p.getFollowUps());
        m.put("commonMistakes", p.getCommonMistakes());
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProblem(CodingVos.SaveProblemRequest req, Long adminUserId) {
        validateSaveRequest(req);
        QuizQuestion q;
        CodeProblem problem;
        boolean isNew = req.getId() == null;
        if (isNew) {
            q = new QuizQuestion();
            q.setQuestionType(QuizQuestion.TYPE_CODE);
            q.setSource(QuizQuestion.SOURCE_MANUAL);
            q.setCreatorId(adminUserId);
            q.setVersion(1);
            problem = new CodeProblem();
        } else {
            q = lockQuestion(req.getId());
            if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
                throw new BusinessException(404, "算法题不存在");
            }
            problem = problemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                    .eq(CodeProblem::getQuestionId, q.getId()).last("FOR UPDATE"));
            if (problem == null) {
                throw new BusinessException(500, "题目扩展数据缺失");
            }
            q.setVersion((q.getVersion() == null ? 1 : q.getVersion()) + 1);
        }
        q.setTitle(req.getTitle().trim());
        q.setContent(req.getContent());
        q.setDifficulty(req.getDifficulty() == null ? 2 : req.getDifficulty());
        q.setTopicCode(req.getTopicCode());
        q.setTags(req.getTags());
        q.setCanonicalKey(req.getCanonicalKey());
        q.setAnswer(null);
        if (isNew) {
            q.setStatus(0);
            q.setReviewStatus(QuizQuestion.REVIEW_PENDING);
            questionMapper.insert(q);
            if (q.getCanonicalKey() == null || q.getCanonicalKey().isBlank()) {
                q.setCanonicalKey("CODE-" + q.getId());
            }
        }
        // 直接发布（管理员操作）等价于审核通过入库；保存为草稿则进待审核队列
        if (req.getStatus() != null && req.getStatus() == 1) {
            q.setStatus(1);
            q.setReviewStatus(QuizQuestion.REVIEW_APPROVED);
        } else {
            q.setStatus(0);
            if (isNew || QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus())) {
                q.setReviewStatus(QuizQuestion.REVIEW_PENDING);
            }
        }
        questionMapper.updateById(q);

        applyProblemFields(problem, req, q.getId());
        if (isNew) {
            problemMapper.insert(problem);
        } else {
            problemMapper.updateById(problem);
        }

        // 用例全量替换（逻辑删除旧行 + 插入新行），保留历史提交快照不受影响（提交只存 source/结果）
        caseMapper.delete(new LambdaQueryWrapper<CodeTestCase>().eq(CodeTestCase::getProblemId, problem.getId()));
        List<CodeTestCase> cases = new ArrayList<>();
        int order = 0;
        int publicCount = 0;
        for (CodingVos.SaveCaseRequest c : req.getCases()) {
            validateJson(c.getInput(), "用例输入");
            validateJson(c.getExpectedOutput(), "用例期望输出");
            CodeTestCase tc = new CodeTestCase();
            tc.setProblemId(problem.getId());
            tc.setInput(c.getInput());
            tc.setExpectedOutput(c.getExpectedOutput());
            tc.setVisibility(CodeTestCase.VIS_HIDDEN.equalsIgnoreCase(c.getVisibility())
                    ? CodeTestCase.VIS_HIDDEN : CodeTestCase.VIS_PUBLIC);
            tc.setWeight(c.getWeight() == null || c.getWeight() <= 0 ? 1 : c.getWeight());
            tc.setOrderNo(c.getOrderNo() == null ? order : c.getOrderNo());
            tc.setVersion(problem.getTestCaseVersion());
            cases.add(tc);
            if (CodeTestCase.VIS_PUBLIC.equals(tc.getVisibility())) {
                publicCount++;
            }
            order++;
        }
        if (cases.isEmpty()) {
            throw new BusinessException(400, "至少需要 1 个测试用例");
        }
        if (publicCount == 0) {
            throw new BusinessException(400, "至少需要 1 个公开用例");
        }
        for (CodeTestCase tc : cases) {
            caseMapper.insert(tc);
        }
        return q.getId();
    }

    private void applyProblemFields(CodeProblem p, CodingVos.SaveProblemRequest req, Long questionId) {
        p.setQuestionId(questionId);
        p.setSupportedLanguages(req.getSupportedLanguages() == null || req.getSupportedLanguages().isBlank()
                ? "java" : req.getSupportedLanguages().trim());
        p.setFunctionSignature(req.getFunctionSignature());
        p.setStarterCode(req.getStarterCode());
        p.setConstraints(req.getConstraints());
        p.setExamples(req.getExamples());
        p.setTimeLimitMs(req.getTimeLimitMs() == null || req.getTimeLimitMs() <= 0 ? 1000 : req.getTimeLimitMs());
        p.setMemoryLimitMb(req.getMemoryLimitMb() == null || req.getMemoryLimitMb() <= 0 ? 256 : req.getMemoryLimitMb());
        p.setJudgeMode(req.getJudgeMode() == null ? CodeProblem.MODE_FUNCTION : req.getJudgeMode());
        p.setChecker(req.getChecker() == null ? CodeProblem.CHECKER_EXACT : req.getChecker());
        p.setToleranceValue(req.getToleranceValue() == null ? 0d : req.getToleranceValue());
        if (p.getTestCaseVersion() == null) {
            p.setTestCaseVersion("v1");
        }
        p.setReferenceSolution(req.getReferenceSolution());
        p.setSolutionExplanation(req.getSolutionExplanation());
        p.setComplexityAnalysis(req.getComplexityAnalysis());
        p.setFollowUps(req.getFollowUps());
        p.setCommonMistakes(req.getCommonMistakes());
    }

    private void validateSaveRequest(CodingVos.SaveProblemRequest req) {
        if (req == null) {
            throw new BusinessException(400, "请求体为空");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(400, "题目标题不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(400, "题干不能为空");
        }
        if (req.getFunctionSignature() == null || req.getFunctionSignature().isBlank()) {
            throw new BusinessException(400, "函数签名不能为空");
        }
        if (req.getStarterCode() == null || req.getStarterCode().isBlank()) {
            throw new BusinessException(400, "初始代码不能为空");
        }
        if (req.getReferenceSolution() == null || req.getReferenceSolution().isBlank()) {
            throw new BusinessException(400, "参考解答不能为空：代码题必须具备可验证标准答案");
        }
        if (req.getCases() == null || req.getCases().isEmpty()) {
            throw new BusinessException(400, "至少需要 1 个测试用例");
        }
    }

    private void validateJson(String json, String label) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, label + "不是合法 JSON：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long questionId, String decision, String comment) {
        QuizQuestion q = lockQuestion(questionId);
        if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
            throw new BusinessException(404, "算法题不存在");
        }
        String d = decision == null ? "" : decision.trim().toUpperCase();
        switch (d) {
            case QuizQuestion.REVIEW_APPROVED -> {
                q.setReviewStatus(QuizQuestion.REVIEW_APPROVED);
                q.setStatus(1);
            }
            case QuizQuestion.REVIEW_REJECTED -> { q.setReviewStatus(QuizQuestion.REVIEW_REJECTED); q.setStatus(0); }
            default -> throw new BusinessException(400, "decision 必须是 APPROVED 或 REJECTED");
        }
        questionMapper.updateById(q);
        log.info("[CodeReview] questionId={} decision={} comment={}", questionId, d, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long questionId, boolean published) {
        QuizQuestion q = lockQuestion(questionId);
        if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
            throw new BusinessException(404, "算法题不存在");
        }
        if (published && !QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus())) {
            throw new BusinessException(409, "题目尚未审核通过，不能发布");
        }
        q.setStatus(published ? 1 : 0);
        questionMapper.updateById(q);
    }

    // ==================== 私有工具 ====================

    private QuizQuestion requireVisibleQuestion(Long questionId) {
        QuizQuestion q = questionMapper.selectById(questionId);
        if (q == null || !QuizQuestion.TYPE_CODE.equals(q.getQuestionType()) || q.getStatus() == null || q.getStatus() != 1
                || (q.getReviewStatus() != null && !QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus()))) {
            throw new BusinessException(404, "题目不存在或未发布");
        }
        return q;
    }

    private String normalizeLanguage(String lang) {
        return lang == null || lang.isBlank() ? "java" : lang.trim().toLowerCase();
    }

    private Map<String, String> topicNameMap() {
        Map<String, String> m = new HashMap<>();
        topicMapper.selectList(null).forEach(t -> m.put(t.getCode(), t.getName()));
        return m;
    }

    private Map<Long, String> myStatusMap(Long userId, List<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<CodeSubmission> mine = submissionMapper.selectList(new LambdaQueryWrapper<CodeSubmission>()
                .eq(CodeSubmission::getUserId, userId)
                .in(CodeSubmission::getQuestionId, ids));
        for (CodeSubmission s : mine) {
            out.merge(s.getQuestionId(),
                    JudgeStatus.ACCEPTED.equals(s.getJudgeStatus()) ? "SOLVED" : "ATTEMPTED",
                    (a, b) -> "SOLVED".equals(a) || "SOLVED".equals(b) ? "SOLVED" : "ATTEMPTED");
        }
        return out;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private CodingVos.SubmitResult toSubmitResult(CodeSubmission s) {
        CodingVos.SubmitResult r = new CodingVos.SubmitResult();
        r.setSubmissionId(s.getId());
        r.setJudgeStatus(s.getJudgeStatus());
        r.setScore(s.getScore());
        r.setPassedCount(s.getPassedCount());
        r.setTotalCount(s.getTotalCount());
        r.setCompileOutput(s.getCompileOutput());
        r.setCaseResults(parseCaseViews(s.getCaseResults()));
        r.setExecutionEnabled(judgeGateway.executionEnabled());
        if (JudgeStatus.JUDGE_UNAVAILABLE.equals(s.getJudgeStatus())) {
            r.setMessage("判题服务尚未就绪：提交已保存，隔离 Runner 上线后可重新判题（本次不计分、不记错）");
        } else if (JudgeStatus.SYSTEM_ERROR.equals(s.getJudgeStatus())) {
            r.setMessage("判题系统异常：本次不计分、不记入错题，可稍后重试");
        }
        return r;
    }

    private CodingVos.SubmissionVO toSubmissionVO(CodeSubmission s, boolean withSource) {
        CodingVos.SubmissionVO v = new CodingVos.SubmissionVO();
        v.setId(s.getId());
        v.setQuestionId(s.getQuestionId());
        if (withSource) {
            v.setSourceCode(s.getSourceCode());
        }
        v.setLanguage(s.getLanguage());
        v.setSubmissionType(s.getSubmissionType());
        v.setJudgeStatus(s.getJudgeStatus());
        v.setScore(s.getScore());
        v.setPassedCount(s.getPassedCount());
        v.setTotalCount(s.getTotalCount());
        v.setExecutionTimeMs(s.getExecutionTimeMs());
        v.setCompileOutput(s.getCompileOutput());
        v.setCaseResults(parseCaseViews(s.getCaseResults()));
        v.setCreateTime(s.getCreateTime() == null ? LocalDateTime.now().toString() : s.getCreateTime().toString());
        v.setContextType(s.getContextType());
        return v;
    }

    @SuppressWarnings("unchecked")
    private List<CodingVos.CaseResultView> parseCaseViews(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CodingVos.CaseResultView.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
