package com.knowflow.service;

import com.knowflow.dto.MindMapDTO;
import com.knowflow.vo.MindMapSummaryVO;
import com.knowflow.vo.MindMapVO;

import java.util.List;

/**
 * 思维导图业务接口：列表 / 详情 / 创建 / 更新 / 删除，均按 user_id 维度隔离。
 */
public interface MindMapService {

    /** 当前用户的思维导图列表（按更新时间倒序） */
    List<MindMapSummaryVO> listMindMaps(Long userId);

    /** 思维导图详情（含整图数据），校验所属用户 */
    MindMapVO getMindMap(Long id, Long userId);

    /** 新建思维导图，返回新记录 ID */
    Long createMindMap(MindMapDTO dto, Long userId);

    /** 更新思维导图（标题与整图数据，可部分更新） */
    void updateMindMap(Long id, MindMapDTO dto, Long userId);

    /** 删除思维导图（逻辑删除） */
    void deleteMindMap(Long id, Long userId);
}
