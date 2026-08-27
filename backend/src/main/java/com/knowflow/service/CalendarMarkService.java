package com.knowflow.service;

import com.knowflow.dto.MemorialDTO;
import com.knowflow.vo.DateMarkVO;
import com.knowflow.vo.MemorialVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历日期标记业务接口：法定节假日（休/班）、中国传统节日（农历）、
 * 现代节日、自定义纪念日。三视图按区间拉取，保证数据一致。
 */
public interface CalendarMarkService {

    /** 区间内所有日期标记（按 date 升序、同类同天按 type 稳定排序） */
    List<DateMarkVO> listMarks(Long userId, LocalDate start, LocalDate end);

    /** 当前用户的纪念日列表（按创建时间倒序） */
    List<MemorialVO> listMemorials(Long userId);

    /** 新建纪念日，返回 ID */
    Long createMemorial(MemorialDTO dto, Long userId);

    /** 更新纪念日（校验所属用户） */
    void updateMemorial(Long id, MemorialDTO dto, Long userId);

    /** 删除纪念日（逻辑删除） */
    void deleteMemorial(Long id, Long userId);
}
