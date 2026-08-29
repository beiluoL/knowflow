package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.common.Result;
import cn.hutool.core.util.StrUtil;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.CodeQuestion;
import com.knowflow.entity.CodeSubmitRecord;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.mapper.CodeSubmitRecordMapper;
import com.knowflow.service.LearningEventService;
import com.knowflow.vo.CodeSubmitRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前台-代码题库接口：面向所有登录用户，仅返回已发布的题目。
 */
@Slf4j
@Tag(name = "代码题库")
@RestController
@RequestMapping("/api/code-questions")
@RequiredArgsConstructor
public class CodeQuestionController {

    private final CodeQuestionMapper questionMapper;
    private final CodeSubmitRecordMapper submitRecordMapper;
    private final LearningEventService learningEventService;

    /** 默认编程语言 */
    private static final String DEFAULT_LANG = "javascript";

    @Operation(summary = "已发布题目列表（支持按难度/语言/关键词筛选）")
    @GetMapping
    public Result<List<CodeQuestion>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String language) {
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
        return Result.success(questionMapper.selectList(wrapper));
    }

    @Operation(summary = "题目详情（仅已发布）")
    @GetMapping("/{id}")
    public Result<CodeQuestion> detail(@PathVariable Long id) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null || q.getStatus() == null || q.getStatus() != 1) {
            return Result.error(404, "题目不存在或已下架");
        }
        return Result.success(q);
    }

    /**
     * 提交答案：前端执行代码并完成测试用例校验后，调用此接口记录提交统计。
     * 实际代码执行（JS/TS 浏览器端运行、SQL 模拟执行）由前端完成，
     * 后端只负责累计 submitCount / passCount，避免在服务端引入脚本引擎依赖。
     */
    @Operation(summary = "提交答案：记录提交与通过统计")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<SubmitResultVO> submit(@PathVariable Long id, @RequestBody SubmitPayload payload) {
        CodeQuestion q = questionMapper.selectById(id);
        if (q == null || q.getStatus() == null || q.getStatus() != 1) {
            return Result.error(404, "题目不存在或已下架");
        }
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
                // Learning Event System（Phase 1）：代码提交事件，与业务完全解耦
                Long uid = record.getUserId();
                learningEventService.record(uid, LearningEventType.CODE_SUBMITTED, "CODE_QUESTION", id,
                        Map.of("language", record.getLanguage(), "total", total, "passCount", pass));
                if (allPassed) {
                    learningEventService.record(uid, LearningEventType.CODE_PASSED, "CODE_QUESTION", id,
                            Map.of("language", record.getLanguage()));
                } else {
                    learningEventService.record(uid, LearningEventType.CODE_FAILED, "CODE_QUESTION", id,
                            Map.of("language", record.getLanguage(), "total", total, "passCount", pass));
                }
            } catch (Exception e) {
                log.warn("提交记录写入失败(不影响): {}", e.getMessage());
            }
        }

        SubmitResultVO vo = new SubmitResultVO();
        vo.setPassed(allPassed);
        vo.setTotal(total);
        vo.setPassCount(pass);
        vo.setSubmitCount(newSubmit);
        vo.setPassTotal(newPass);
        return Result.success(vo);
    }

    /** 提交入参：由前端执行测试用例后上报结果。 */
    @Data
    public static class SubmitPayload {
        /** 代码内容（保留用于日志审计，当前不参与服务端校验） */
        private String code;
        /** 提交所用语言 */
        private String language;
        /** 测试用例总数 */
        private Integer total;
        /** 通过用例数 */
        private Integer passCount;
    }

    @Operation(summary = "我的提交记录（按题目ID筛选）")
    @GetMapping("/{id}/submissions")
    public Result<List<CodeSubmitRecordVO>> submissions(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
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

    /** 提交结果：返回最新统计便于前端展示通过率。 */
    @Data
    public static class SubmitResultVO {
        private Boolean passed;
        private Integer total;
        private Integer passCount;
        private Integer submitCount;
        private Integer passTotal;
    }
}
