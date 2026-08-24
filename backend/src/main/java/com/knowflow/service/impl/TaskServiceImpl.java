package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.knowflow.dto.TaskDTO;
import com.knowflow.dto.TaskListDTO;
import com.knowflow.entity.Task;
import com.knowflow.entity.TaskList;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.TaskListMapper;
import com.knowflow.mapper.TaskMapper;
import com.knowflow.service.TaskService;
import com.knowflow.vo.CalendarEventVO;
import com.knowflow.vo.TaskListVO;
import com.knowflow.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Things3 式任务清单服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskListMapper taskListMapper;

    // ===== 智能列表 =====

    @Override
    public List<TaskVO> listBySmartList(Long userId, String smart) {
        List<Task> all = allTasks(userId);
        List<TaskVO> roots = buildTree(all);
        LocalDate today = LocalDate.now();
        return roots.stream()
                .filter(t -> matchSmart(t, smart, today))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskVO> listByList(Long userId, Long listId) {
        List<Task> all = allTasks(userId);
        List<TaskVO> roots = buildTree(all);
        return roots.stream()
                .filter(t -> listId.equals(t.getListId()) && t.getStatus() == 0)
                .collect(Collectors.toList());
    }

    // ===== 任务写操作 =====

    @Override
    public Long createTask(Long userId, TaskDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException("任务标题不能为空");
        }
        Task t = new Task();
        t.setUserId(userId);
        t.setTitle(dto.getTitle().trim());
        t.setListId(dto.getListId());
        t.setParentId(dto.getParentId() != null && dto.getParentId() > 0 ? dto.getParentId() : 0L);
        t.setNotes(dto.getNotes());
        t.setScheduledDate(dto.getScheduledDate());
        t.setDueDate(dto.getDueDate());
        // 定时事件：写入起止时间；若未给 scheduledDate 则取开始日期，保证月视图归类正确
        t.setStartTime(dto.getStartTime());
        t.setEndTime(dto.getEndTime());
        if (dto.getStartTime() != null && dto.getScheduledDate() == null) {
            t.setScheduledDate(dto.getStartTime().toLocalDate());
        }
        t.setSomeday(dto.getSomeday() != null && dto.getSomeday() ? 1 : 0);
        t.setImportant(dto.getImportant() != null ? dto.getImportant() : 0);
        t.setUrgent(dto.getUrgent() != null ? dto.getUrgent() : 0);
        t.setStage(dto.getStage() != null ? dto.getStage() : 0);
        t.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        t.setStatus(0);
        taskMapper.insert(t);
        return t.getId();
    }

    @Override
    public void updateTask(Long userId, Long id, TaskDTO dto) {
        Task t = ownedTask(userId, id);
        if (dto.getTitle() != null) t.setTitle(dto.getTitle().trim());
        if (dto.getListId() != null) t.setListId(dto.getListId());
        if (dto.getParentId() != null) t.setParentId(dto.getParentId() > 0 ? dto.getParentId() : 0L);
        if (dto.getNotes() != null) t.setNotes(dto.getNotes());
        if (dto.getScheduledDate() != null) t.setScheduledDate(dto.getScheduledDate());
        if (dto.getDueDate() != null) t.setDueDate(dto.getDueDate());
        // 起止时间：仅在显式提供时更新（保持与其它部分更新调用方的兼容，避免误清空）
        if (dto.getStartTime() != null) t.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) t.setEndTime(dto.getEndTime());
        if (dto.getSomeday() != null) t.setSomeday(dto.getSomeday() ? 1 : 0);
        if (dto.getImportant() != null) t.setImportant(dto.getImportant());
        if (dto.getUrgent() != null) t.setUrgent(dto.getUrgent());
        if (dto.getStage() != null) t.setStage(dto.getStage());
        if (dto.getSortOrder() != null) t.setSortOrder(dto.getSortOrder());
        if (dto.getStatus() != null) t.setStatus(dto.getStatus());
        taskMapper.updateById(t);
    }

    @Override
    public void deleteTask(Long userId, Long id) {
        ownedTask(userId, id);
        softDeleteWithChildren(id, userId);
    }

    @Override
    public void setStatus(Long userId, Long id, Integer status) {
        Task t = ownedTask(userId, id);
        t.setStatus(status);
        taskMapper.updateById(t);
    }

    @Override
    public List<TaskVO> listBoard(Long userId) {
        QueryWrapper<Task> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("deleted", 0).eq("parent_id", 0)
                .orderByAsc("sort_order").orderByAsc("id");
        List<Task> tasks = taskMapper.selectList(qw);
        List<TaskVO> result = new ArrayList<>();
        for (Task t : tasks) {
            TaskVO vo = toVO(t);
            result.add(vo);
        }
        return result;
    }

    @Override
    public void updateStage(Long userId, Long id, Integer stage) {
        if (stage == null || stage < 0 || stage > 2) {
            throw new BusinessException("非法的看板阶段");
        }
        Task t = ownedTask(userId, id);
        t.setStage(stage);
        // 看板「已完成」列与 status 完成态保持同步：拖到已完成即标记完成，拖出则回到待办
        t.setStatus(stage == 2 ? 1 : 0);
        taskMapper.updateById(t);
    }

    // ===== 清单 =====

    @Override
    public List<TaskListVO> listTaskLists(Long userId) {
        QueryWrapper<TaskList> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("deleted", 0)
                .orderByAsc("sort_order").orderByAsc("id");
        List<TaskList> lists = taskListMapper.selectList(qw);
        List<Task> tasks = allTasks(userId);

        List<TaskListVO> result = new ArrayList<>();
        for (TaskList l : lists) {
            TaskListVO vo = new TaskListVO();
            vo.setId(l.getId());
            vo.setName(l.getName());
            vo.setKind(l.getKind());
            vo.setParentId(l.getParentId() == null ? 0L : l.getParentId());
            vo.setColor(l.getColor());
            vo.setIcon(l.getIcon());
            vo.setSortOrder(l.getSortOrder() == null ? 0 : l.getSortOrder());
            int open = 0;
            int done = 0;
            for (Task t : tasks) {
                if (l.getId().equals(t.getListId())) {
                    if (t.getStatus() != null && t.getStatus() == 1) {
                        done++;
                    } else {
                        open++;
                    }
                }
            }
            vo.setTaskCount(open);
            vo.setDoneCount(done);
            result.add(vo);
        }
        return result;
    }

    @Override
    public Long createTaskList(Long userId, TaskListDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("清单名称不能为空");
        }
        TaskList l = new TaskList();
        l.setUserId(userId);
        l.setName(dto.getName().trim());
        l.setKind(dto.getKind() != null ? dto.getKind() : "list");
        l.setParentId(dto.getParentId() != null && dto.getParentId() > 0 ? dto.getParentId() : 0L);
        l.setColor(dto.getColor());
        l.setIcon(dto.getIcon());
        l.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        taskListMapper.insert(l);
        return l.getId();
    }

    @Override
    public void updateTaskList(Long userId, Long id, TaskListDTO dto) {
        TaskList l = ownedList(userId, id);
        if (dto.getName() != null) l.setName(dto.getName().trim());
        if (dto.getKind() != null) l.setKind(dto.getKind());
        if (dto.getParentId() != null) l.setParentId(dto.getParentId() > 0 ? dto.getParentId() : 0L);
        if (dto.getColor() != null) l.setColor(dto.getColor());
        if (dto.getIcon() != null) l.setIcon(dto.getIcon());
        if (dto.getSortOrder() != null) l.setSortOrder(dto.getSortOrder());
        taskListMapper.updateById(l);
    }

    @Override
    public void deleteTaskList(Long userId, Long id) {
        ownedList(userId, id);
        // 该清单下的任务退回收件箱
        UpdateWrapper<Task> uw = new UpdateWrapper<>();
        uw.eq("user_id", userId).eq("list_id", id).set("list_id", null);
        taskMapper.update(null, uw);
        // 子清单提升为顶级
        UpdateWrapper<TaskList> luw = new UpdateWrapper<>();
        luw.eq("user_id", userId).eq("parent_id", id).set("parent_id", 0);
        taskListMapper.update(null, luw);
        TaskList self = taskListMapper.selectById(id);
        if (self != null) {
            self.setDeleted(1);
            taskListMapper.updateById(self);
        }
    }

    // ===== 日历范围查询 =====

    @Override
    public List<CalendarEventVO> listByRange(Long userId, LocalDateTime start, LocalDateTime end, Integer status, Long listId) {
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        List<CalendarEventVO> list = taskMapper.selectCalendarRange(userId, start, end, startDate, endDate, status, listId);
        for (CalendarEventVO vo : list) {
            vo.setAllDay(vo.getStartTime() == null);
        }
        return list;
    }

    // ===== 内部工具 =====

    private List<Task> allTasks(Long userId) {
        QueryWrapper<Task> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("deleted", 0)
                .orderByAsc("sort_order").orderByAsc("id");
        return taskMapper.selectList(qw);
    }

    private Task ownedTask(Long userId, Long id) {
        Task t = taskMapper.selectById(id);
        if (t == null || t.getDeleted() != null && t.getDeleted() == 1 || !userId.equals(t.getUserId())) {
            throw new BusinessException("任务不存在");
        }
        return t;
    }

    private TaskList ownedList(Long userId, Long id) {
        TaskList l = taskListMapper.selectById(id);
        if (l == null || l.getDeleted() != null && l.getDeleted() == 1 || !userId.equals(l.getUserId())) {
            throw new BusinessException("清单不存在");
        }
        return l;
    }

    private void softDeleteWithChildren(Long parentId, Long userId) {
        QueryWrapper<Task> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("deleted", 0).eq("parent_id", parentId);
        List<Task> children = taskMapper.selectList(qw);
        for (Task c : children) {
            softDeleteWithChildren(c.getId(), userId);
        }
        Task self = taskMapper.selectById(parentId);
        if (self != null) {
            self.setDeleted(1);
            taskMapper.updateById(self);
        }
    }

    private List<TaskVO> buildTree(List<Task> all) {
        Map<Long, TaskVO> map = new LinkedHashMap<>();
        for (Task t : all) {
            map.put(t.getId(), toVO(t));
        }
        List<TaskVO> roots = new ArrayList<>();
        for (Task t : all) {
            TaskVO vo = map.get(t.getId());
            if (t.getParentId() != null && t.getParentId() > 0 && map.containsKey(t.getParentId())) {
                TaskVO parent = map.get(t.getParentId());
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(vo);
                parent.setHasChildren(true);
            } else {
                roots.add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<TaskVO> nodes) {
        nodes.sort((a, b) -> {
            int c = Integer.compare(a.getSortOrder(), b.getSortOrder());
            return c != 0 ? c : Long.compare(a.getId(), b.getId());
        });
        for (TaskVO n : nodes) {
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                sortTree(n.getChildren());
            }
        }
    }

    private TaskVO toVO(Task t) {
        TaskVO vo = new TaskVO();
        vo.setId(t.getId());
        vo.setListId(t.getListId());
        vo.setParentId(t.getParentId() == null ? 0L : t.getParentId());
        vo.setTitle(t.getTitle());
        vo.setNotes(t.getNotes());
        vo.setStatus(t.getStatus() == null ? 0 : t.getStatus());
        vo.setScheduledDate(t.getScheduledDate());
        vo.setDueDate(t.getDueDate());
        vo.setSomeday(t.getSomeday() != null && t.getSomeday() == 1);
        vo.setImportant(t.getImportant() != null && t.getImportant() == 1 ? 1 : 0);
        vo.setUrgent(t.getUrgent() != null && t.getUrgent() == 1 ? 1 : 0);
        vo.setStage(t.getStage() == null ? 0 : t.getStage());
        vo.setSortOrder(t.getSortOrder() == null ? 0 : t.getSortOrder());
        vo.setHasChildren(false);
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    private boolean matchSmart(TaskVO t, String smart, LocalDate today) {
        boolean open = t.getStatus() == 0;
        switch (smart) {
            case "inbox":
                return open && t.getListId() == null && !t.getSomeday();
            case "today":
                return open && t.getScheduledDate() != null && !t.getScheduledDate().isAfter(today);
            case "upcoming":
                return open && t.getScheduledDate() != null && t.getScheduledDate().isAfter(today);
            case "someday":
                return open && t.getSomeday();
            case "logbook":
                return t.getStatus() == 1;
            case "all":
            default:
                return open;
        }
    }
}
