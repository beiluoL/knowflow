package com.knowflow.interview.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.TaskDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.service.TaskService;
import com.knowflow.interview.service.MockInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 面试建议显式确认后复用 task，不更新掌握度或 FSRS。 */
@Service
@RequiredArgsConstructor
public class InterviewLearningBridge {
    private final JdbcTemplate jdbc;
    private final MockInterviewService sessions;
    private final KgEntityMapper knowledge;
    private final DocumentAccessPolicy access;
    private final TaskService tasks;
    private final ObjectMapper json;
    public record Resource(Long knowledgeId, String title, List<String> sourceCitations, String reason) {}
    public record Preview(Long sessionId, List<Resource> resources, String message, String evidenceIngestion) {}
    public record ConfirmRequest(String requestId, List<Long> knowledgeIds) {}
    public record Confirmed(Long knowledgeId, Long taskId) {}
    private record EvaluationRow(Long id, String evaluation) {}

    public Preview preview(Long actor, Long sessionId) {
        requireCompleted(actor, sessionId, false);
        Map<Long, Resource> proposed = new LinkedHashMap<>();
        var entities = knowledge.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        var evaluations = jdbc.query("SELECT id, evaluation_json FROM mock_interview_v2_turn WHERE session_id=? AND user_id=? ORDER BY id",
                (rs, n) -> new EvaluationRow(rs.getLong("id"), rs.getString("evaluation_json")), sessionId, actor);
        for (var row : evaluations) {
            String raw = row.evaluation();
            if (raw == null || raw.isBlank()) continue;
            JsonNode evaluation;
            try { evaluation = json.readTree(raw); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BusinessException(500, "面试评价数据无法读取"); }
            // 新评价保留服务端已校验的模型缺口名称；旧评价继续兼容原gaps。
            JsonNode gaps = evaluation.has("missingKnowledge") ? evaluation.path("missingKnowledge") : evaluation.path("gaps");
            if (!gaps.isArray()) continue;
            for (JsonNode gap : gaps) {
                String text = gap.isTextual() ? gap.asText().trim() : gap.path("name").asText("").trim();
                for (var entity : entities) {
                    // 只接受完整名称相等，不将模型文字推断为资源 ID。
                    if (text.isEmpty() || !text.equalsIgnoreCase(entity.getName()) || !access.canReadKnowledge(entity, actor)) continue;
                    proposed.putIfAbsent(entity.getId(), new Resource(entity.getId(), entity.getName(),
                            List.of("interview-turn:" + row.id() + (evaluation.has("missingKnowledge") ? ":missingKnowledge" : ":gaps")), text));
                }
            }
        }
        return new Preview(sessionId, List.copyOf(proposed.values()), proposed.isEmpty() ? "没有匹配到当前可阅读的知识资源" : "请选择需要创建的复习任务", "UNSUPPORTED");
    }

    @Transactional
    public List<Confirmed> confirm(Long actor, Long sessionId, ConfirmRequest request) {
        if(request == null || request.requestId() == null || !request.requestId().matches("[A-Za-z0-9_-]{1,100}")
                || request.knowledgeIds() == null || request.knowledgeIds().isEmpty() || request.knowledgeIds().size() > 50
                || request.knowledgeIds().stream().anyMatch(Objects::isNull)) throw new BusinessException(400, "确认参数无效");
        requireCompleted(actor, sessionId, true);
        Map<Long, Resource> current = new HashMap<>();
        preview(actor, sessionId).resources().forEach(r -> current.put(r.knowledgeId(), r));
        Set<Long> selected = new LinkedHashSet<>(request.knowledgeIds());
        if (!current.keySet().containsAll(selected)) throw new BusinessException(400, "所选知识资源已不可用，请刷新建议");
        List<Confirmed> result = new ArrayList<>();
        for (Long id : selected) {
            var existing = jdbc.queryForList("SELECT task_id FROM interview_remediation WHERE session_id=? AND knowledge_id=? AND user_id=?", sessionId, id, actor);
            Long taskId;
            if (!existing.isEmpty()) taskId = ((Number)existing.get(0).get("task_id")).longValue();
            else {
                Resource resource = current.get(id);
                TaskDTO dto = new TaskDTO();
                dto.setTitle("面试复习：" + resource.title());
                // task.notes 保存真实资源身份；无前端消费合约时不伪造知识点深链。
                dto.setNotes("来源：面试 #" + sessionId + "；知识实体 #" + id + "（" + resource.title() + "）\n需复习：" + resource.reason() + "\n证据：" + String.join(", ",resource.sourceCitations()));
                taskId = tasks.createTask(actor, dto);
                jdbc.update("INSERT INTO interview_remediation(session_id,knowledge_id,user_id,task_id,request_id) VALUES (?,?,?,?,?)", sessionId,id,actor,taskId,request.requestId());
            }
            result.add(new Confirmed(id,taskId));
        }
        return List.copyOf(result);
    }
    private void requireCompleted(Long actor, Long sessionId, boolean lock) {
        if (actor == null) throw new BusinessException(401,"未登录");
        sessions.getOwn(actor,sessionId); // 已有服务校验父会话归属及逻辑删除，管理员无他人会话豁免。
        var rows=jdbc.queryForList("SELECT state FROM mock_interview_v2_state WHERE session_id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),sessionId,actor);
        if(rows.isEmpty()) throw new BusinessException(404,"面试会话不存在");
        if(!"COMPLETED".equals(rows.get(0).get("state"))) throw new BusinessException(409,"完成面试后才能生成复习建议");
    }
}
