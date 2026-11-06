package com.knowflow.learning.service.actions;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.productivity.entity.Task;
import com.knowflow.productivity.mapper.TaskMapper;
import com.knowflow.learning.vo.LearningTaskVO;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.knowflow.learning.actions.LearningWriteGuard;
/** 向学习界面展示同一批任务记录，不复制到 learning_task。 */
@Service
@RequiredArgsConstructor
public class LearningTaskProjection {
    private final TaskMapper tasks;
    private final DailyLearningPlanService plans;
    private final LearningWriteGuard guard;

    public List<LearningTaskVO> append(List<LearningTaskVO> legacy, Long user, String zone) {
        List<LearningTaskVO> result = new ArrayList<>();
        for (var t : legacy) {
            t.setSource("LEARNING");
            result.add(t);
        }
        LocalDate date = LocalDate.now(LearningContextService.zone(zone));
        for (var t :
                tasks.selectList(
                        new LambdaQueryWrapper<Task>()
                                .eq(Task::getUserId, user)
                                .in(Task::getStatus, 0, 1)
                                .isNotNull(Task::getLearningPlanRef)
                                .le(Task::getScheduledDate, date)
                                .orderByAsc(Task::getStatus)
                                .orderByAsc(Task::getScheduledDate)
                                .orderByAsc(Task::getSortOrder)
                                .last("LIMIT 100"))) {
            var item = plans.metadata(t);
            if (item == null) continue;
            var v = new LearningTaskVO();
            v.setId(t.getId());
            v.setSource("TASK");
            v.setTitle(t.getTitle());
            v.setDescription(
                    Objects.toString(t.getNotes(), "") + "\n预计时长：" + item.estimatedMinutes() + "分钟");
            v.setType(item.type());
            v.setTargetId(item.resourceId());
            v.setStatus(t.getStatus());
            v.setEstimatedMinutes(item.estimatedMinutes());
            v.setCompletionRequirement(guard.completionRequirement(t));
            v.setDeadline(t.getDueDate() == null ? null : t.getDueDate().atTime(23, 59));
            result.add(v);
        }
        return result;
    }
}
