package com.knowflow.interview.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Read published authored material through the same document ownership boundary.
 * Existing sessions keep their question snapshots even when content changes.
 */
@Component @RequiredArgsConstructor
public class ContentStudioInterviewBank {
    private final QuizQuestionMapper questions;
    private final DocumentAccessPolicy access;
    private final com.knowflow.mapper.DocDocumentMapper documents;
    public List<InterviewQuestionBank.Track> tracks(Long actor) {
        if (actor == null) return List.of();
        var query=new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuestionType,QuizQuestion.TYPE_SHORT_ANSWER)
                .eq(QuizQuestion::getStatus,1).eq(QuizQuestion::getReviewStatus,QuizQuestion.REVIEW_APPROVED)
                .like(QuizQuestion::getTags,"content-studio-persistent").orderByDesc(QuizQuestion::getVersion);
        Map<String,QuizQuestion> unique=new LinkedHashMap<>();
        for(var q:questions.selectList(query)) {
            if (!access.canReadCategory(q.getCategoryId(),actor)) continue;
            if(q.getDocId()!=null && !access.canRead(documents.selectById(q.getDocId()),actor)) continue;
            String key=q.getCanonicalKey()==null?"question/"+q.getId():q.getCanonicalKey();
            unique.putIfAbsent(key,q);
        }
        Map<String,List<InterviewQuestionBank.SeedQuestion>> groups=new LinkedHashMap<>();
        for(var q:unique.values()) {
            if(q.getAnswer()==null||q.getAnswer().isBlank()) continue;
            String topic=Objects.toString(q.getTopicCode(),"");
            String group=topic.equals("RAG")||topic.equals("AI_LLM")||topic.equals("AGENT_MCP")?"AI":topic.equals("JAVA_AI_PRACTICE")?"PROJECT":"JAVA";
            var material=followups(q,unique);
            groups.computeIfAbsent(group,k->new ArrayList<>()).add(new InterviewQuestionBank.SeedQuestion(
                "STUDIO-"+q.getId(),q.getTitle(),material.points(),material.questions(),
                new InterviewQuestionBank.Rubric("概念有遗漏或边界不清","能说明关键概念与参考要点","能解释机制、工程应用与限制；仅供真实回答后的自评")));
        }
        return groups.entrySet().stream().map(e->new InterviewQuestionBank.Track("CONTENT_"+e.getKey(),name("CONTENT_"+e.getKey()),"来自可阅读、已发布的 Content Studio 资料；会话保存独立快照。",e.getValue())).toList();
    }
    private static final String SOURCE_METADATA_MARKER = "来源与审核原记录：\n";
    private static final com.fasterxml.jackson.databind.ObjectMapper METADATA_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private record FollowupMaterial(List<String> questions, List<String> points) {}

    private FollowupMaterial followups(QuizQuestion question, Map<String,QuizQuestion> readable) {
        List<String> prompts = new ArrayList<>();
        List<String> points = new ArrayList<>();
        points.add(question.getAnswer());
        String explanation = question.getExplanation();
        int marker = explanation == null ? -1 : explanation.lastIndexOf(SOURCE_METADATA_MARKER);
        if (marker < 0) return new FollowupMaterial(List.of(), List.copyOf(points));
        try {
            var metadata = METADATA_JSON.readTree(explanation.substring(marker + SOURCE_METADATA_MARKER.length()));
            if (metadata == null || !metadata.isObject()) throw new IllegalArgumentException("来源记录必须为对象");
            var tree = metadata.get("followupTree");
            if (tree == null || tree.isNull()) return new FollowupMaterial(List.of(), List.copyOf(points));
            if (tree.isArray()) {
                for (var node : tree) {
                    if (!node.isObject() || !node.path("question").isTextual()
                            || node.path("question").asText().isBlank()) {
                        throw new IllegalArgumentException("追问必须包含问题文本");
                    }
                    if (node.has("level") && (!node.path("level").isIntegralNumber()
                            || node.path("level").asInt() < 1 || node.path("level").asInt() > 3)) {
                        throw new IllegalArgumentException("追问层级必须为1至3");
                    }
                    if (node.hasNonNull("referenceAnswer") && !node.path("referenceAnswer").isTextual()) {
                        throw new IllegalArgumentException("追问参考答案必须为文本");
                    }
                    if (prompts.size() < 5) appendFollowup(prompts, points,
                            node.path("question").asText(), node.path("referenceAnswer").asText(""),
                            node.path("level").asInt(1));
                }
            } else if (tree.isObject() && tree.path("childKeys").isArray()) {
                for (var key : tree.path("childKeys")) {
                    if (!key.isTextual()) throw new IllegalArgumentException("追问标识必须为文本");
                    QuizQuestion child = readable.get(key.asText());
                    if (child != null && child.getTitle() != null && !child.getTitle().isBlank()
                            && prompts.size() < 5) {
                        appendFollowup(prompts, points, child.getTitle(), child.getAnswer(), 1);
                    }
                }
            } else {
                throw new IllegalArgumentException("追问结构必须为数组或childKeys对象");
            }
            return new FollowupMaterial(List.copyOf(prompts), List.copyOf(points));
        } catch (Exception error) {
            throw new com.knowflow.exception.BusinessException(500,
                    "固化面试追问来源记录损坏，questionId=" + question.getId());
        }
    }

    private void appendFollowup(List<String> prompts, List<String> points,
                                String prompt, String answer, int level) {
        prompts.add(prompt);
        if (answer != null && !answer.isBlank()) {
            points.add("第" + level + "层追问：" + prompt + "\n参考答案：" + answer);
        }
    }

    public static String name(String code) {
        return switch(code) {case "CONTENT_JAVA"->"固化教材 · Java";case "CONTENT_AI"->"固化教材 · AI / RAG";case "CONTENT_PROJECT"->"固化教材 · 项目答辩";default->code;};
    }
}
