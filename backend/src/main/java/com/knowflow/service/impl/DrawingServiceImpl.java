package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.DrawingDTO;
import com.knowflow.entity.Drawing;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DrawingMapper;
import com.knowflow.service.DrawingService;
import com.knowflow.vo.DrawingSummaryVO;
import com.knowflow.vo.DrawingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 绘图业务实现：整图以 JSON 文本持久化，读取时反序列化为对象返回给前端。
 * 数据列 data 存储结构（vue-flow 契约）：{ nodes:[{id,position,data,type}], edges:[{id,source,target}] }。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawingServiceImpl implements DrawingService {

    private final DrawingMapper drawingMapper;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY_DATA = "{\"nodes\":[],\"edges\":[]}";

    @Override
    public List<DrawingSummaryVO> listDrawings(Long userId) {
        LambdaQueryWrapper<Drawing> w = new LambdaQueryWrapper<Drawing>()
                .eq(Drawing::getUserId, userId)
                .orderByDesc(Drawing::getUpdateTime);
        return drawingMapper.selectList(w).stream().map(e -> {
            DrawingSummaryVO vo = new DrawingSummaryVO();
            vo.setId(e.getId());
            vo.setTitle(e.getTitle());
            vo.setType(e.getType());
            vo.setCreateTime(e.getCreateTime());
            vo.setUpdateTime(e.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public DrawingVO getDrawing(Long id, Long userId) {
        return toVO(requireOwn(drawingMapper.selectById(id), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDrawing(DrawingDTO dto, Long userId) {
        Drawing e = new Drawing();
        e.setUserId(userId);
        e.setTitle(requireNotBlank(dto.getTitle(), "绘图标题不能为空"));
        e.setType(StringUtils.hasText(dto.getType()) ? dto.getType() : "flowchart");
        e.setData(serializeData(dto.getData()));
        drawingMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDrawing(Long id, DrawingDTO dto, Long userId) {
        Drawing e = requireOwn(drawingMapper.selectById(id), userId);
        if (StringUtils.hasText(dto.getTitle())) {
            e.setTitle(requireNotBlank(dto.getTitle(), "绘图标题不能为空"));
        }
        if (StringUtils.hasText(dto.getType())) {
            e.setType(dto.getType());
        }
        if (dto.getData() != null) {
            e.setData(serializeData(dto.getData()));
        }
        drawingMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDrawing(Long id, Long userId) {
        Drawing e = requireOwn(drawingMapper.selectById(id), userId);
        drawingMapper.deleteById(e.getId());
    }

    private DrawingVO toVO(Drawing e) {
        DrawingVO vo = new DrawingVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setTitle(e.getTitle());
        vo.setType(e.getType());
        vo.setData(parseData(e.getData()));
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private Object parseData(String data) {
        if (!StringUtils.hasText(data)) {
            return defaultData();
        }
        try {
            return MAPPER.readValue(data, Object.class);
        } catch (Exception ex) {
            log.warn("绘图数据解析失败，回退空结构: {}", ex.getMessage());
            return defaultData();
        }
    }

    private String serializeData(Object data) {
        if (data == null) {
            return EMPTY_DATA;
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception ex) {
            throw new BusinessException("绘图数据序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Object defaultData() {
        try {
            return MAPPER.readValue(EMPTY_DATA, LinkedHashMap.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Drawing requireOwn(Drawing e, Long userId) {
        if (e == null) {
            throw new BusinessException("绘图不存在");
        }
        if (!Objects.equals(e.getUserId(), userId)) {
            throw new BusinessException("无权操作该记录");
        }
        return e;
    }

    private String requireNotBlank(String value, String msg) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(msg);
        }
        return value.trim();
    }
}
