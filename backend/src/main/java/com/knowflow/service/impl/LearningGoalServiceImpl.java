package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.GoalDTO;
import com.knowflow.entity.LearningGoal;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningGoalMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.service.LearningGoalService;
import com.knowflow.service.LearningService;
import com.knowflow.vo.LearningGoalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习目标服务实现（Architecture-4.0）。
 * <p>
 * 关联路径的进度读取 learning_user_path（0~100 口径，与 LearningService 一致），
 * 仅做只读查询与报名兜底，路径业务本身仍走 LearningService，避免双写口径分裂。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningGoalServiceImpl extends ServiceImpl<LearningGoalMapper, LearningGoal>
        implements LearningGoalService {

    /** 状态白名单：ACTIVE 进行中 / COMPLETED 已完成 / ARCHIVED 已归档。 */
    private static final Set<String> GOAL_STATUSES = Set.of("ACTIVE", "COMPLETED", "ARCHIVED");

    private static final Map<String, Integer> STATUS_ORDER = Map.of(
            "ACTIVE", 0,
            "COMPLETED", 1,
            "ARCHIVED", 2);

    private final LearningGoalMapper goalMapper;
    private final LearningPathMapper learningPathMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningService learningService;

    @Override
    public List<LearningGoalVO> listGoals(Long userId) {
        List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId));
        goals.sort(Comparator
                .comparingInt((LearningGoal g) -> STATUS_ORDER.getOrDefault(normalizeStatus(g.getStatus()), 0))
                .thenComparing(Comparator.comparing(LearningGoal::getCreateTime).reversed()));
        return goals.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public LearningGoalVO activeGoal(Long userId) {
        List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId)
                .eq(LearningGoal::getStatus, "ACTIVE")
                .orderByAsc(LearningGoal::getCreateTime));
        if (goals.isEmpty()) {
            return null;
        }
        return toVo(goals.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningGoalVO create(Long userId, GoalDTO dto) {
        String title = dto != null && dto.getTitle() != null ? dto.getTitle().trim() : "";
        if (title.isEmpty()) {
            throw new BusinessException("目标标题不能为空，例如「7 天掌握 JVM GC」");
        }
        LearningGoal goal = new LearningGoal();
        applyEditableFields(goal, dto, title);
        goal.setUserId(userId);
        goal.setStatus(normalizeStatus(dto != null ? dto.getStatus() : null));
        if (goal.getStartDate() == null) {
            goal.setStartDate(LocalDate.now());
        }
        this.save(goal);
        if (dto != null && dto.getPathId() != null) {
            attachPath(userId, goal, dto.getPathId());
            goalMapper.updateById(goal);
        }
        return toVo(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningGoalVO update(Long userId, Long goalId, GoalDTO dto) {
        LearningGoal goal = requireOwned(userId, goalId);
        if (dto == null) {
            return toVo(goal);
        }
        String title = dto.getTitle() != null ? dto.getTitle().trim() : goal.getTitle();
        if (title.isEmpty()) {
            throw new BusinessException("目标标题不能为空");
        }
        if (dto.getStatus() != null && !GOAL_STATUSES.contains(normalizeStatus(dto.getStatus()))) {
            throw new BusinessException("非法目标状态：" + dto.getStatus());
        }
        applyEditableFields(goal, dto, title);
        // 路径关联：显式 null 表示解除关联（覆盖式语义）
        if (dto.getPathId() != null) {
            attachPath(userId, goal, dto.getPathId());
        } else {
            goal.setPathId(null);
        }
        if (dto.getStatus() != null) {
            goal.setStatus(normalizeStatus(dto.getStatus()));
        }
        goalMapper.updateById(goal);
        return toVo(goal);
    }

    @Override
    public void remove(Long userId, Long goalId) {
        requireOwned(userId, goalId);
        goalMapper.deleteById(goalId);
    }

    // ==================== 私有工具 ====================

    /** 覆盖实体可编辑字段（不含 userId / pathId 归属与状态，状态单独处理）。 */
    private void applyEditableFields(LearningGoal goal, GoalDTO dto, String title) {
        goal.setTitle(title);
        goal.setDescription(trimToNull(dto != null ? dto.getDescription() : null));
        goal.setTarget(trimToNull(dto != null ? dto.getTarget() : null));
        goal.setStartDate(dto != null ? dto.getStartDate() : null);
        goal.setDeadline(dto != null ? dto.getDeadline() : null);
        Integer daily = dto != null ? dto.getDailyMinutes() : null;
        goal.setDailyMinutes(daily != null && daily >= 0 ? daily : 0);
    }

    /**
     * 校验路径可见性并建立关联；若用户尚未报名该公开/自有路径则自动报名，
     * 使后续路径进度可被目标实时带出。
     */
    private void attachPath(Long userId, LearningGoal goal, Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new BusinessException(404, "关联的学习路径不存在");
        }
        boolean owner = path.getOwnerUserId() == null || Objects.equals(path.getOwnerUserId(), 0L)
                || Objects.equals(path.getOwnerUserId(), userId);
        if (!owner) {
            throw new BusinessException(403, "无权关联该学习路径");
        }
        boolean enrolled = userPathMapper.selectCount(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, userId)
                .eq(LearningUserPath::getPathId, pathId)) > 0;
        if (!enrolled) {
            learningService.enrollPath(pathId, userId);
        }
        goal.setPathId(pathId);
    }

    /** 读取目标归属并返回实体（非本人/不存在均按 404 处理）。 */
    private LearningGoal requireOwned(Long userId, Long goalId) {
        LearningGoal goal = goalMapper.selectById(goalId);
        if (goal == null || !Objects.equals(goal.getUserId(), userId)) {
            throw new BusinessException(404, "学习目标不存在");
        }
        return goal;
    }

    /** 实体 → VO：填充关联路径标题 / 章节数 / 进度（实时读 learning_user_path，0~100）。 */
    private LearningGoalVO toVo(LearningGoal goal) {
        LearningGoalVO vo = BeanUtil.copyProperties(goal, LearningGoalVO.class);
        if (goal.getPathId() == null) {
            vo.setPathProgress(0);
            return vo;
        }
        LearningPath path = learningPathMapper.selectById(goal.getPathId());
        if (path != null) {
            vo.setPathTitle(path.getTitle());
            vo.setPathChapterCount(path.getChapterCount());
        }
        LearningUserPath up = userPathMapper.selectOne(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, goal.getUserId())
                .eq(LearningUserPath::getPathId, goal.getPathId()));
        if (up != null && up.getProgress() != null) {
            vo.setPathProgress((int) Math.round(up.getProgress().doubleValue()));
        } else {
            vo.setPathProgress(0);
        }
        return vo;
    }

    private String normalizeStatus(String status) {
        return StrUtil.isBlank(status) ? "ACTIVE" : status.trim();
    }

    private String trimToNull(String s) {
        return s == null ? null : (s.trim().isEmpty() ? null : s.trim());
    }
}
