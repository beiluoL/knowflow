package com.knowflow.coding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.learning.contract.LearningEventType;
import com.knowflow.common.Result;
import cn.hutool.core.util.StrUtil;
import com.knowflow.common.SecurityUtils;
import com.knowflow.coding.entity.CodeQuestion;
import com.knowflow.coding.entity.CodeSubmitRecord;
import com.knowflow.coding.mapper.CodeQuestionMapper;
import com.knowflow.coding.mapper.CodeSubmitRecordMapper;
import com.knowflow.learning.contract.LearningEventRecorder;
import com.knowflow.coding.dto.CodeSubmitDTO;
import com.knowflow.coding.vo.CodeSubmitResultVO;
import org.springframework.stereotype.Service;
import com.knowflow.coding.vo.CodeSubmitRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前台-代码题库接口：面向所有登录用户，仅返回已发布的题目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeQuestionService {

    private final CodeQuestionMapper questionMapper;
    private final CodeSubmitRecordMapper submitRecordMapper;
    private final LearningEventRecorder learningEventService;
    private final com.knowflow.security.DocumentAccessPolicy documentAccess;

    /** 私有导入台账是独立于发布状态的归属边界。 */
    private void requireIndexedCodeAccess(Long id, Long actor) {
        if (documentAccess.admin(actor)) {
            return;
        }
        List<String> privateKeys = questionMapper.selectPrivateResourceKeys(id);
        String ownerPrefix = "user/" + actor + "/";
        if (privateKeys.stream().anyMatch(key -> !key.startsWith(ownerPrefix))) {
            throw new com.knowflow.exception.BusinessException(403, "无权访问该私人代码练习");
        }
    }

    /** 默认编程语言 */
    private static final String DEFAULT_LANG = "javascript";

    /** 列出认证用户可见的已发布题目，保留原有排序和筛选。 */
    public Result<List<CodeQuestion>> list(
            String keyword,
            Integer difficulty,
            String language) {
        LambdaQueryWrapper<CodeQuestion> wrapper = new LambdaQueryWrapper<CodeQuestion>()
                .eq(CodeQuestion::getStatus, 1)
                .orderByAsc(CodeQuestion::getSortOrder)
                .orderByDesc(CodeQuestion::getCreateTime);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(CodeQuestion::getTitle, keyword.trim())
                    .or().like(CodeQuestion::getTags, keyword.trim()));
        }
        if (difficulty != null) {
            wrapper.eq(CodeQuestion::getDifficulty, difficulty);
        }
        if (language != null && !language.trim().isEmpty()) {
            wrapper.eq(CodeQuestion::getLanguage, language.trim());
        }
        Long actor = SecurityUtils.getCurrentUserId();
        if (!documentAccess.admin(actor)) {
            questionMapper.restrictToActor(wrapper, actor);
        }
        return Result.success(questionMapper.selectList(wrapper));
    }

    /** 私有台账授权前保留历史上不存在或未发布时的返回结果。 */
    public Result<CodeQuestion> detail(Long id) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null || q.getStatus() == null || q.getStatus() != 1) {
            return Result.error(404, "题目不存在或已下架");
        }
        requireIndexedCodeAccess(id, SecurityUtils.getCurrentUserId());
        return Result.success(q);
    }

    /**
     * 提交答案：前端执行代码并完成测试用例校验后，调用此接口记录提交统计。
     * 实际代码执行（JS/TS 浏览器端运行、SQL 模拟执行）由前端完成，
     * 后端只负责累计 submitCount / passCount，避免在服务端引入脚本引擎依赖。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<CodeSubmitResultVO> submit(Long id, CodeSubmitDTO payload) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null || q.getStatus() == null || q.getStatus() != 1) {
            return Result.error(404, "题目不存在或已下架");
        }
        requireIndexedCodeAccess(id, SecurityUtils.getCurrentUserId());
        int total = payload.getTotal() == null ? 0 : payload.getTotal();
        int pass = payload.getPassCount() == null ? 0 : payload.getPassCount();
        boolean allPassed = total > 0 && pass == total;

        // 累计提交次数；全部通过才累计通过次数
        int newSubmit = (q.getSubmitCount() == null ? 0 : q.getSubmitCount()) + 1;
        int newPass = (q.getPassCount() == null ? 0 : q.getPassCount()) + (allPassed ? 1 : 0);
        CodeQuestion update = new CodeQuestion();
        update.setId(id);
        update.setSubmitCount(newSubmit);
        update.setPassCount(newPass);
        questionMapper.updateById(update);

        recordSubmission(id, payload, total, pass, allPassed);

        CodeSubmitResultVO vo = new CodeSubmitResultVO();
        vo.setPassed(allPassed);
        vo.setTotal(total);
        vo.setPassCount(pass);
        vo.setSubmitCount(newSubmit);
        vo.setPassTotal(newPass);
        return Result.success(vo);
    }

    /** 在提交事务内保留历史记录和事件的尽力写入边界。 */
    private void recordSubmission(Long id, CodeSubmitDTO payload, int total, int pass, boolean allPassed) {
        // P-CODE-03 提交记录持久化
        if (StrUtil.isNotBlank(payload.getCode())) {
            try {
                CodeSubmitRecord record = new CodeSubmitRecord();
                record.setUserId(SecurityUtils.getCurrentUserId());
                record.setQuestionId(id);
                record.setCode(payload.getCode());
                record.setLanguage(payload.getLanguage() != null ? payload.getLanguage() : DEFAULT_LANG);
                record.setTotal(total);
                record.setPassCount(pass);
                record.setPassed(allPassed ? 1 : 0);
                submitRecordMapper.insert(record);
                // Phase 1：一次代码提交 = 一条核心事件 CODE_SUBMITTED（行为+结果合一，结果经 passed/total/passCount 表达）
                // 事件经 LearningEventService 写入（统一入口），不直接触碰 LearningEventMapper
                Long uid = record.getUserId();
                learningEventService.record(uid, LearningEventType.CODE_SUBMITTED, "CODE_QUESTION", id,
                        Map.of("language", record.getLanguage(), "total", total, "passCount", pass, "passed", allPassed));
            } catch (Exception e) {
                log.warn("提交记录写入失败(不影响): {}", e.getMessage());
            }
        }

    }

    /** 校验私有台账授权后返回当前用户最近 50 次提交。 */
    public Result<List<CodeSubmitRecordVO>> submissions(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireIndexedCodeAccess(id, userId);
        List<CodeSubmitRecord> list = submitRecordMapper.selectList(
                new LambdaQueryWrapper<CodeSubmitRecord>()
                        .eq(CodeSubmitRecord::getUserId, userId)
                        .eq(CodeSubmitRecord::getQuestionId, id)
                        .orderByDesc(CodeSubmitRecord::getCreateTime)
                        .last("LIMIT 50"));
        List<CodeSubmitRecordVO> result = list.stream().map(r -> {
            CodeSubmitRecordVO vo = new CodeSubmitRecordVO();
            vo.setId(r.getId());
            vo.setQuestionId(r.getQuestionId());
            vo.setCode(r.getCode());
            vo.setLanguage(r.getLanguage());
            vo.setTotal(r.getTotal());
            vo.setPassCount(r.getPassCount());
            vo.setPassed(r.getPassed() != null && r.getPassed() == 1);
            vo.setErrorMsg(r.getErrorMsg());
            vo.setCreateTime(r.getCreateTime() != null ? r.getCreateTime().toString() : null);
            return vo;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

}
