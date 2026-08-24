package com.knowflow.service;

import com.knowflow.dto.TaskDTO;
import com.knowflow.dto.TaskListDTO;
import com.knowflow.vo.CalendarEventVO;
import com.knowflow.vo.TaskListVO;
import com.knowflow.vo.TaskVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Things3 式任务清单服务：智能列表查询、任务与清单的增删改查，全部按 user_id 隔离。
 */
public interface TaskService {

    /** 按智能列表（inbox/today/upcoming/someday/logbook/all）返回嵌套任务树。 */
    List<TaskVO> listBySmartList(Long userId, String smart);

    /** 返回某清单 / 项目下的顶层任务树。 */
    List<TaskVO> listByList(Long userId, Long listId);

    /** 新建任务，返回新 ID。 */
    Long createTask(Long userId, TaskDTO dto);

    /** 更新任务（前端应传完整任务对象）。 */
    void updateTask(Long userId, Long id, TaskDTO dto);

    /** 删除任务及其子任务（逻辑删除）。 */
    void deleteTask(Long userId, Long id);

    /** 设置任务状态（0 待办 / 1 已完成）。 */
    void setStatus(Long userId, Long id, Integer status);

    /** 看板视图：返回当前用户顶层任务（parent_id=0）扁平列表，按 stage 分组由前端完成。 */
    List<TaskVO> listBoard(Long userId);

    /** 更新看板阶段（0 待办 / 1 进行中 / 2 已完成），并同步 status（已完成阶段 ⟺ status=1）。 */
    void updateStage(Long userId, Long id, Integer stage);

    /** 当前用户全部清单 / 项目 / 领域（含任务计数）。 */
    List<TaskListVO> listTaskLists(Long userId);

    /** 新建清单，返回新 ID。 */
    Long createTaskList(Long userId, TaskListDTO dto);

    /** 更新清单。 */
    void updateTaskList(Long userId, Long id, TaskListDTO dto);

    /** 删除清单（其任务退回收件箱、子清单提升为顶级）。 */
    void deleteTaskList(Long userId, Long id);

    /** 按时间区间查询日历事件（start/end 必填），供月/周/日视图与范围筛选共用。 */
    List<CalendarEventVO> listByRange(Long userId, LocalDateTime start, LocalDateTime end, Integer status, Long listId);
}
