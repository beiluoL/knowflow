package com.knowflow.service;

import com.knowflow.dto.HabitDTO;
import com.knowflow.vo.HabitVO;

import java.util.List;

/**
 * 习惯打卡业务服务：习惯 CRUD、打卡、连续天数统计与进度可视化数据。
 */
public interface HabitService {

    /** 当前用户全部启用习惯（含今日打卡与进度）。 */
    List<HabitVO> listHabits(Long userId);

    /** 新建习惯，返回新 ID。 */
    Long createHabit(Long userId, HabitDTO dto);

    /** 更新习惯。 */
    void updateHabit(Long userId, Long id, HabitDTO dto);

    /** 删除习惯（逻辑删除，连带打卡记录保留但不再计入）。 */
    void deleteHabit(Long userId, Long id);

    /** 今日打卡（幂等累加：同日多次打卡则 count+1，达标后不再超目标）。 */
    HabitVO checkIn(Long userId, Long id);

    /** 撤销今日最近一次打卡（count-1，归零则删除当日记录）。 */
    HabitVO undoCheckIn(Long userId, Long id);

    /** 获取单个习惯详情（含进度）。 */
    HabitVO getHabit(Long userId, Long id);
}
