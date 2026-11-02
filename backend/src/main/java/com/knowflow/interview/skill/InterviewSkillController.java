package com.knowflow.interview.skill;

import com.knowflow.common.Result;
import com.knowflow.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试 Skill Registry 只读接口。
 *
 * <p>技能与题库方向全部来自服务端白名单；请求只用于推荐排序，不能注册新技能或覆盖题目。</p>
 */
@RestController
@RequestMapping("/api/interview/skills")
public class InterviewSkillController {

    private final InterviewSkillRegistry registry;

    public InterviewSkillController(InterviewSkillRegistry registry) {
        this.registry = registry;
    }

    public record RecommendRequest(
            @Size(max = 20000, message = "JD 文本超过 20000 字") String jd,
            @Size(max = 60, message = "目标岗位不能超过 60 字") String role,
            @Pattern(regexp = "EASY|MEDIUM|HARD|SENIOR", message = "难度无效") String difficulty,
            @Min(value = 0, message = "经验年限范围为 0 到 40 年")
            @Max(value = 40, message = "经验年限范围为 0 到 40 年") Integer years) {
    }

    @GetMapping
    public Result<Map<String, Object>> list() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", "v1");
        body.put("skills", registry.list());
        body.put("tracks", registry.tracks().stream().map(t -> Map.of(
                "code", t.code(), "name", t.name(), "description", t.description(),
                "questionPoolSize", t.questions().size())).toList());
        return Result.success(body);
    }

    @GetMapping("/{id}")
    public Result<InterviewSkillRegistry.Skill> detail(@PathVariable String id) {
        InterviewSkillRegistry.Skill skill = registry.find(id);
        if (skill == null) throw new BusinessException(404, "面试技能不存在");
        return Result.success(skill);
    }

    @PostMapping("/recommend")
    public Result<Map<String, Object>> recommend(@Valid @RequestBody RecommendRequest request) {
        List<InterviewSkillRegistry.Match> matches = registry.recommend(
                request == null ? null : request.jd(),
                request == null ? null : request.role(),
                request == null ? null : request.difficulty(),
                request == null ? null : request.years());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skills", matches);
        body.put("detectedKeywords", registry.detectKeywords(request == null ? null : request.jd()));
        body.put("note", matches.isEmpty() ? "未识别到已登记技能；请补充岗位或技术关键词" : "推荐仅依据已登记技能与关键词命中");
        return Result.success(body);
    }
}
