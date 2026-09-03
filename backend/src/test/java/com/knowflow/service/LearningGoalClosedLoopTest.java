package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.GoalDTO;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningGoal;
import com.knowflow.entity.LearningPath;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningGoalMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.vo.LearningGoalVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.0 学习目标闭环集成测试（真实 H2，无 mock）。
 * <p>
 * 覆盖闭环起点「Goal」侧：创建 → 关联公开路径（自动报名）→ 完成章节后目标进度实时联动 →
 * 状态流转 / 逻辑删除 / 跨用户隔离 / 私有路径越权拦截 / 标题空校验。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningGoalClosedLoopTest {

    /** 独立测试用户，避开 data.sql 种子用户与其它测试常量。 */
    private static final Long UID_A = 80001L;
    private static final Long UID_B = 80002L;

    @Autowired private LearningGoalService goalService;
    @Autowired private LearningService learningService;
    @Autowired private LearningGoalMapper goalMapper;
    @Autowired private LearningPathMapper pathMapper;
    @Autowired private LearningChapterMapper chapterMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UID_A, null, List.of()));
        // 隔离：清掉本测试可能残留的目标（每次 H2 全新加载，此处为幂等兜底）
        goalMapper.delete(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, UID_A).or().eq(LearningGoal::getUserId, UID_B));
    }

    private GoalDTO newGoalDto(String title) {
        GoalDTO dto = new GoalDTO();
        dto.setTitle(title);
        dto.setDescription("通过 Architecture-4.0 闭环跑通一个真实目标");
        dto.setTarget("能用小白语言解释并通过测验");
        dto.setDeadline(LocalDate.now().plusDays(7));
        return dto;
    }

    @Test
    @DisplayName("A4.0-1: 创建目标（无路径）→ 列表可见 ACTIVE 在前 → activeGoal 返回它")
    void createAndActive() {
        assertNull(goalService.activeGoal(UID_A), "无目标时 activeGoal 应为 null");

        LearningGoalVO vo = goalService.create(UID_A, newGoalDto("7 天掌握 JVM GC"));
        assertNotNull(vo.getId());
        assertEquals("ACTIVE", vo.getStatus());
        assertEquals("7 天掌握 JVM GC", vo.getTitle());
        assertNull(vo.getPathId());
        assertEquals(0, vo.getPathProgress());
        assertNull(vo.getPathTitle());

        LearningGoalVO active = goalService.activeGoal(UID_A);
        assertNotNull(active);
        assertEquals(vo.getId(), active.getId());
    }

    @Test
    @DisplayName("A4.0-2: 创建目标并关联公开路径 → 自动报名 → 完成章节后目标进度实时联动")
    void goalLinkPathProgressFollowsChapterCompletion() {
        // 关联种子公开路径（Java 后端开发实战，seed id=2，共 6 章）
        GoalDTO dto = newGoalDto("30 天掌握 Java 后端开发实战");
        dto.setPathId(2L);
        LearningGoalVO vo = goalService.create(UID_A, dto);
        assertNotNull(vo.getPathId());
        assertEquals(2L, vo.getPathId().longValue());
        assertEquals("Java 后端开发实战", vo.getPathTitle());
        assertEquals(6, vo.getPathChapterCount());
        assertEquals(0, vo.getPathProgress(), "刚报名进度应为 0");

        // 真实完成第一个无前置章节（进度 1/6 ≈ 16.67 → 取整 17）
        LearningChapter first = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                        .eq(LearningChapter::getPathId, 2L)
                        .orderByAsc(LearningChapter::getSortOrder)
                        .last("LIMIT 1"))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("seed 路径2缺少章节"));
        learningService.completeChapter(first.getId(), UID_A);

        // 目标 VO 重新读取 → 进度实时反映（非冗余存储）
        LearningGoalVO after = goalService.activeGoal(UID_A);
        assertNotNull(after);
        assertEquals(17, after.getPathProgress(), "1/6 完成 → 进度应取整为 17");
    }

    @Test
    @DisplayName("A4.0-3: 更新状态流转 COMPLETED → activeGoal 让位给下一个 ACTIVE")
    void statusTransition() {
        LearningGoalVO g1 = goalService.create(UID_A, newGoalDto("目标一"));
        LearningGoalVO g2 = goalService.create(UID_A, newGoalDto("目标二"));

        assertEquals(g1.getId(), goalService.activeGoal(UID_A).getId(), "最早创建的 ACTIVE 胜出");

        GoalDTO complete = new GoalDTO();
        complete.setTitle(g1.getTitle());
        complete.setStatus("COMPLETED");
        complete.setDeadline(g1.getDeadline());
        goalService.update(UID_A, g1.getId(), complete);

        LearningGoalVO active = goalService.activeGoal(UID_A);
        assertNotNull(active);
        assertEquals(g2.getId(), active.getId(), "目标一完成后应让位给目标二");
    }

    @Test
    @DisplayName("A4.0-4: 跨用户隔离 —— B 的目标不出现在 A 的列表")
    void userIsolation() {
        goalService.create(UID_A, newGoalDto("A 的目标"));
        goalService.create(UID_B, newGoalDto("B 的目标"));

        List<LearningGoalVO> listA = goalService.listGoals(UID_A);
        assertEquals(1, listA.size());
        assertEquals("A 的目标", listA.get(0).getTitle());

        // 越权读取他人目标应 404
        LearningGoal ofB = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, UID_B)).stream().findFirst().orElseThrow();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> goalService.update(UID_A, ofB.getId(), new GoalDTO()));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("A4.0-5: 关联私有路径需本人 —— 他人私有路径被 403 拦截")
    void cannotLinkOthersPrivatePath() {
        LearningPath privatePath = new LearningPath();
        privatePath.setTitle("他人私有路径");
        privatePath.setStatus(1);
        privatePath.setOwnerUserId(90001L);
        pathMapper.insert(privatePath);

        GoalDTO dto = newGoalDto("越权测试");
        dto.setPathId(privatePath.getId());
        BusinessException ex = assertThrows(BusinessException.class, () -> goalService.create(UID_A, dto));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("A4.0-6: 空标题创建被拒 + 逻辑删除后不再出现")
    void validationAndRemove() {
        assertThrows(BusinessException.class, () -> goalService.create(UID_A, new GoalDTO()));

        LearningGoalVO vo = goalService.create(UID_A, newGoalDto("待删除目标"));
        goalService.remove(UID_A, vo.getId());
        assertTrue(goalService.listGoals(UID_A).isEmpty(), "逻辑删除后列表应为空");

        // 删除后越权更新仍 404（不泄露）
        assertThrows(BusinessException.class, () -> goalService.update(UID_A, vo.getId(), new GoalDTO()));
    }
}
