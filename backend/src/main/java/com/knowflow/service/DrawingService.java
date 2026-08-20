package com.knowflow.service;

import com.knowflow.dto.DrawingDTO;
import com.knowflow.vo.DrawingSummaryVO;
import com.knowflow.vo.DrawingVO;

import java.util.List;

/**
 * 绘图业务接口：列表 / 详情 / 创建 / 更新 / 删除，均按 user_id 维度隔离。
 */
public interface DrawingService {

    /** 当前用户的绘图列表（按更新时间倒序） */
    List<DrawingSummaryVO> listDrawings(Long userId);

    /** 绘图详情（含整图数据），校验所属用户 */
    DrawingVO getDrawing(Long id, Long userId);

    /** 新建绘图，返回新记录 ID */
    Long createDrawing(DrawingDTO dto, Long userId);

    /** 更新绘图（标题与整图数据，可部分更新） */
    void updateDrawing(Long id, DrawingDTO dto, Long userId);

    /** 删除绘图（逻辑删除） */
    void deleteDrawing(Long id, Long userId);
}
