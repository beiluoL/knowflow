package com.knowflow.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.knowflow.dto.CodeAssessRequest;
import com.knowflow.dto.CodeAssessResult;
import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;
import com.knowflow.entity.CodeQuestion;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.CodeAssessService;
import com.knowflow.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动化代码评估实现（SC1-AI-02）。
 * <p>评估 = 动态测试用例执行（真实沙箱）+ 静态代码检查 + AI 综合能力评判。
 * <ul>
 *   <li>动态评测：将题目测试用例逐条送入 {@link CodeExecutionService} 真实运行，比对标准输出与期望值；</li>
 *   <li>静态检查：行长度、调试打印残留、TODO 遗留、括号失衡等启发式规则；</li>
 *   <li>AI 报告：综合代码与评测结果生成能力评估（未配置大模型时优雅降级）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAssessServiceImpl implements CodeAssessService {

    private final CodeExecutionService codeExecutionService;
    private final CodeQuestionMapper codeQuestionMapper;
    private final AiService aiService;

    private static final int DEFAULT_TIME_LIMIT = 8000;

    @Override
    public CodeAssessResult assess(CodeAssessRequest request, Long userId) {
        String language = request.getLanguage();
        String code = request.getCode() == null ? "" : request.getCode();
        long timeLimit = (request.getTimeLimitMs() != null && request.getTimeLimitMs() > 0)
                ? request.getTimeLimitMs() : DEFAULT_TIME_LIMIT;

        // 1) 获取测试用例
        List<TestCase> testCases = resolveTestCases(request);

        // 2) 动态评测：逐用例真实运行
        int passed = 0;
        int total = testCases.size();
        if (total > 0) {
            for (TestCase tc : testCases) {
                CodeRunRequest runReq = new CodeRunRequest();
                runReq.setLanguage(language);
                // 解释型语言：用户代码后追加调用语句（如 solution(1,2)）捕获输出
                runReq.setCode(code + "\n" + (tc.input == null ? "" : tc.input));
                runReq.setTimeLimitMs(timeLimit);
                try {
                    CodeRunResult r = codeExecutionService.execute(runReq, userId);
                    String actual = r.getOutput() == null ? "" : r.getOutput().trim();
                    String expected = tc.expected == null ? "" : tc.expected.trim();
                    boolean ok = CodeRunResult.Status.SUCCESS.equals(r.getStatus())
                            && (actual.equals(expected) || (!expected.isEmpty() && actual.contains(expected)));
                    if (ok) {
                        passed++;
                    }
                } catch (Exception e) {
                    log.warn("[Assess] 用例运行异常：{}", e.getMessage());
                }
            }
        }

        // 3) 静态检查
        List<CodeAssessResult.StaticIssue> staticIssues = staticCheck(code);

        // 4) AI 报告（优雅降级）
        String aiReport = null;
        boolean aiConfigured = false;
        try {
            String system = "你是编程学习平台的代码评估助手。请根据用户的代码、测试用例通过情况与静态检查结果，"
                    + "用简体中文给出能力评估报告，结构：①优势 ②不足 ③改进建议。控制在 220 字以内，聚焦可操作建议。";
            String user = buildAiUserPrompt(language, code, passed, total, staticIssues);
            aiReport = aiService.complete(system, user, null, userId);
            aiConfigured = true;
        } catch (BusinessException e) {
            aiConfigured = false;
            aiReport = null;
        }

        // 5) 综合评分
        double ratio = total > 0 ? (double) passed / total : (code.isBlank() ? 0 : 1);
        int penalty = Math.min(staticIssues.size() * 5, 30);
        int score = (int) Math.max(0, Math.round(ratio * 70 + (30 - penalty)));
        String level = score >= 85 ? "熟练" : score >= 60 ? "进阶" : "入门";
        boolean allPassed = total > 0 && passed == total;
        String summary = String.format("通过 %d/%d 个测试用例，静态检查发现 %d 个问题，综合评级：%s（%d 分）",
                passed, total, staticIssues.size(), level, score);

        return CodeAssessResult.builder()
                .score(score)
                .level(level)
                .passedTests(passed)
                .totalTests(total)
                .passed(allPassed)
                .staticIssues(staticIssues)
                .aiReport(aiReport)
                .aiConfigured(aiConfigured)
                .summary(summary)
                .build();
    }

    /** 解析测试用例：优先用题目自带，其次用请求传入的 JSON */
    private List<TestCase> resolveTestCases(CodeAssessRequest request) {
        String json = null;
        if (request.getQuestionId() != null) {
            CodeQuestion q = codeQuestionMapper.selectById(request.getQuestionId());
            if (q != null && q.getTestCases() != null && !q.getTestCases().isBlank()) {
                json = q.getTestCases();
            }
        }
        if (json == null) {
            json = request.getTestCasesJson();
        }
        List<TestCase> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject jo = arr.getJSONObject(i);
                result.add(new TestCase(
                        jo.getStr("input"),
                        jo.getStr("expected")));
            }
        } catch (Exception e) {
            log.warn("[Assess] 测试用例解析失败：{}", e.getMessage());
        }
        return result;
    }

    /** 启发式静态检查 */
    private List<CodeAssessResult.StaticIssue> staticCheck(String code) {
        List<CodeAssessResult.StaticIssue> issues = new ArrayList<>();
        if (code == null || code.isBlank()) {
            return issues;
        }
        String[] lines = code.split("\n", -1);
        int open = 0, close = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int idx = line.indexOf("//");
            String stripped = idx >= 0 ? line.substring(0, idx) : line;
            // 长行
            if (line.length() > 120) {
                issues.add(CodeAssessResult.StaticIssue.builder()
                        .rule("LONG_LINE").message("该行超过 120 字符，建议拆分提升可读性").line(i + 1).build());
            }
            // 调试打印残留
            if (stripped.contains("console.log") || stripped.contains("print(") || stripped.contains("System.out.println")) {
                issues.add(CodeAssessResult.StaticIssue.builder()
                        .rule("DEBUG_PRINT").message("存在调试输出语句，提交前建议移除或改为正式日志").line(i + 1).build());
            }
            // TODO 遗留
            if (line.contains("TODO") || line.contains("FIXME") || line.contains("XXX")) {
                issues.add(CodeAssessResult.StaticIssue.builder()
                        .rule("TODO_LEFT").message("存在未完成的 TODO/FIXME 标记").line(i + 1).build());
            }
            // 括号计数（粗略）
            for (char c : stripped.toCharArray()) {
                if (c == '(' || c == '{' || c == '[') open++;
                else if (c == ')' || c == '}' || c == ']') close++;
            }
        }
        if (open != close) {
            issues.add(CodeAssessResult.StaticIssue.builder()
                    .rule("BRACE_UNBALANCED").message("括号/花括号数量不匹配，可能存在语法问题").line(0).build());
        }
        return issues;
    }

    private String buildAiUserPrompt(String language, String code, int passed, int total,
                                     List<CodeAssessResult.StaticIssue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("【编程语言】").append(language == null ? "未知" : language).append("\n");
        sb.append("【测试用例通过】").append(passed).append("/").append(total).append("\n");
        if (!issues.isEmpty()) {
            sb.append("【静态检查问题】\n");
            for (CodeAssessResult.StaticIssue it : issues) {
                sb.append("- ").append(it.getRule()).append("：").append(it.getMessage());
                if (it.getLine() > 0) sb.append("（第 ").append(it.getLine()).append(" 行）");
                sb.append("\n");
            }
        }
        sb.append("【用户代码】\n```").append(language == null ? "" : language).append("\n")
                .append(code).append("\n```\n");
        sb.append("请基于以上信息给出能力评估与改进建议。");
        return sb.toString();
    }

    /** 测试用例内部模型 */
    private static class TestCase {
        final String input;
        final String expected;

        TestCase(String input, String expected) {
            this.input = input;
            this.expected = expected;
        }
    }
}
