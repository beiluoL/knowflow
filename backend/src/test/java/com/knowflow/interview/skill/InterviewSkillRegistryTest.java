package com.knowflow.interview.skill;

import com.knowflow.interview.core.InterviewQuestionBank;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Skill 目录是服务端白名单；这里验证真实加载、映射与确定性推荐，不依赖数据库或模型。 */
class InterviewSkillRegistryTest {

    private final InterviewSkillRegistry registry = new InterviewSkillRegistry();

    @Test
    void catalogLoadsUniqueSkillsMappedToRegisteredTracks() {
        List<InterviewSkillRegistry.Skill> skills = registry.list();
        assertTrue(skills.size() >= 12, "Phase 1 至少登记 12 条 Skill");
        assertEquals(skills.size(), new HashSet<>(skills.stream().map(InterviewSkillRegistry.Skill::id).toList()).size());
        var tracks = new HashSet<>(InterviewQuestionBank.TRACKS.stream().map(InterviewQuestionBank.Track::code).toList());
        tracks.add("PROJECT");
        for (InterviewSkillRegistry.Skill skill : skills) {
            assertTrue(tracks.contains(skill.trackCode()), "Skill 只能映射到既有题库方向：" + skill.id());
            assertFalse(skill.questionRules().isEmpty());
            assertFalse(skill.followupRules().isEmpty());
            assertFalse(skill.rubric().isEmpty());
        }
        assertNotNull(registry.find("java-ai-engineer"));
        assertNull(registry.find("unknown-skill"));
    }

    @Test
    void jdKeywordsAreDetectedOnlyWhenActuallyPresent() {
        List<String> found = registry.detectKeywords("负责 RAG 检索增强、Embedding 向量检索与 Agent 工具调用");
        assertTrue(found.contains("RAG"));
        assertTrue(found.contains("Embedding"));
        assertTrue(found.contains("Agent"));
        assertTrue(registry.detectKeywords("负责财务报表整理与会议安排").isEmpty());
        assertTrue(registry.detectKeywords("").isEmpty());
    }

    @Test
    void recommendUsesRealMatchAndReturnsEmptyWithoutEvidence() {
        List<InterviewSkillRegistry.Match> matches = registry.recommend(
                "招聘 Java 后端工程师，要求 Spring Boot、MySQL、Redis、并发编程经验", "Java 后端工程师", "MEDIUM", 3);
        assertFalse(matches.isEmpty());
        assertTrue(matches.get(0).score() > 0);
        assertTrue(matches.stream().anyMatch(m -> m.skill().id().equals("java-backend")));
        assertFalse(matches.get(0).matchedKeywords().isEmpty());
        assertTrue(registry.recommend("", "", null, null).isEmpty());
        assertTrue(matches.size() <= 5);
    }
}
