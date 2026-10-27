package com.knowflow.exam.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.exam.vo.ExamVos;

import java.util.List;

/** 智能考试系统业务接口：模板/组卷 → 作答 → 评分 → 分析 → 错题。 */
public interface ExamService {

    /** 默认模板列表（checkAvailability=true 时附加题库是否足量）。 */
    List<ExamVos.TemplateVO> listTemplates(boolean checkAvailability, Long userId);

    /** 按稳定模板生成一份新试卷（保存快照）。 */
    ExamVos.PaperVO createFromTemplate(String templateKey, ExamRequests.TemplateInstantiateRequest req, Long userId);

    /** 题库可用性预览（不生成试卷）。 */
    ExamVos.BankAvailabilityVO availability(ExamRequests.ComposeRequest req, Long userId);

    /** 确定性规则智能组卷。 */
    ExamVos.PaperVO compose(ExamRequests.ComposeRequest req, Long userId);

    /** AI 智能出卷（个人临时卷，不进共享题库）。 */
    ExamVos.PaperVO composeAi(ExamRequests.AiComposeRequest req, Long userId);

    /** 开始/继续一场考试（恢复同卷未交卷的尝试）。 */
    ExamVos.AttemptVO startAttempt(Long paperId, Long userId);

    /** 获取进行中尝试（含草稿，不下发答案）。 */
    ExamVos.AttemptVO getAttempt(Long attemptId, Long userId);

    /** 自动保存草稿（仅 IN_PROGRESS）。 */
    ExamVos.AttemptVO saveDraft(Long attemptId, ExamRequests.SaveDraftRequest req, Long userId);

    /** 交卷：规则判分客观题、主观题置 PENDING，幂等。 */
    ExamVos.ResultVO submit(Long attemptId, ExamRequests.SubmitRequest req, Long userId);

    /**
     * 扫描并自动交卷已过服务端截止时间、仍处于 IN_PROGRESS 的尝试（以截止前草稿为准判分）。
     * 条件认领保证与手动交卷/多实例调度并发时每场只判分一次。返回本次自动交卷场次数。
     */
    int autoSubmitExpiredAttempts(int batchSize);

    /** 成绩与解析（含题型/知识点分析）。 */
    ExamVos.ResultVO getResult(Long attemptId, Long userId);

    /** 我的考试记录。 */
    List<ExamVos.AttemptListItemVO> listMyAttempts(Long userId);

    /** 试卷列表（公共 + 我的 AI 卷）。 */
    List<ExamVos.PaperVO> listPapers(boolean mineOnly, Long userId);

    /** 对 PENDING 主观题执行 AI 五维评卷；未配置/失败保持 PENDING。 */
    ExamVos.AiGradeResultVO runAiGrading(Long attemptId, Long userId);

    /** Phase 3G：对交卷后等待判题的 CODE 题重试代码判题（条件落分，幂等；平台失败继续保持待判）。 */
    ExamVos.ResultVO rejudgeCodeAnswers(Long attemptId, Long userId);

    /** 考试错题本（source=EXAM），支持知识点/掌握状态/时间筛选。 */
    IPage<ExamVos.ExamMistakeVO> examMistakes(String topic, Integer mastered, String from, String to,
                                             Integer pageNum, Integer pageSize, Long userId);

    /** 标记考试错题为已掌握（仅本人）。 */
    void markMistakeMastered(Long mistakeId, Long userId);

    /** 考试中心首页数据（全部来自真实数据，样本不足留空）。 */
    ExamVos.DashboardVO dashboard(Long userId);
}
