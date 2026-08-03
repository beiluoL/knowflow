package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.AgentIntentDTO;
import com.knowflow.service.IntentService;
import com.knowflow.vo.AgentEvalVO;
import com.knowflow.vo.AgentIntentVO;
import com.knowflow.vo.AmbiguityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 编程 Agent 意图识别与答案生成优化接口（方案 P1~P3）。
 * <ul>
 *   <li>POST /api/agent/intent —— 多轮上下文意图分类（含歧义点）</li>
 *   <li>POST /api/agent/ambiguities —— 结构探针 + 语义歧义检测</li>
 *   <li>POST /api/agent/evaluate —— 输出准确率评估闭环</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentIntentController {

    private final IntentService intentService;

    @PostMapping("/intent")
    public Result<AgentIntentVO> intent(@RequestBody AgentIntentDTO dto) {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        return Result.success(intentService.classify(dto, userId));
    }

    @PostMapping("/ambiguities")
    public Result<List<AmbiguityVO>> ambiguities(@RequestBody AgentIntentDTO dto) {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        return Result.success(intentService.detectAmbiguities(dto, userId));
    }

    @PostMapping("/evaluate")
    public Result<AgentEvalVO> evaluate(@RequestBody EvalRequest req) {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        IntentService.EvalInput input = new IntentService.EvalInput(
                req.intent, req.slots, req.agentOutput, req.userFeedback,
                req.fromFeedback != null && req.fromFeedback, req.sessionId);
        return Result.success(intentService.evaluate(input, userId));
    }

    /** 评估请求体 */
    public record EvalRequest(
            String intent,
            Map<String, String> slots,
            String agentOutput,
            String userFeedback,
            Boolean fromFeedback,
            Long sessionId) {
    }
}
