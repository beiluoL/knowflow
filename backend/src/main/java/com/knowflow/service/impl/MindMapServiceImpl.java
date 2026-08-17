package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.MindMapDTO;
import com.knowflow.entity.MindMap;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.MindMapMapper;
import com.knowflow.service.MindMapService;
import com.knowflow.vo.MindMapSummaryVO;
import com.knowflow.vo.MindMapVO;
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
 * 思维导图业务实现：整图以 JSON 文本持久化，读取时反序列化为对象返回给前端。
 * 数据列 data 存储结构：{ nodes:[{id,text,x,y,parentId,collapsed,color}], edges:[{id,source,target}], view:{scale,tx,ty} }。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MindMapServiceImpl implements MindMapService {

    private final MindMapMapper mindMapMapper;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY_DATA =
            "{\"nodes\":[],\"edges\":[],\"view\":{\"scale\":1,\"tx\":0,\"ty\":0}}";

    @Override
    public List<MindMapSummaryVO> listMindMaps(Long userId) {
        LambdaQueryWrapper<MindMap> w = new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getUserId, userId)
                .orderByDesc(MindMap::getUpdateTime);
        return mindMapMapper.selectList(w).stream().map(e -> {
            MindMapSummaryVO vo = new MindMapSummaryVO();
            vo.setId(e.getId());
            vo.setTitle(e.getTitle());
            vo.setCreateTime(e.getCreateTime());
            vo.setUpdateTime(e.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public MindMapVO getMindMap(Long id, Long userId) {
        return toVO(requireOwn(mindMapMapper.selectById(id), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMindMap(MindMapDTO dto, Long userId) {
        MindMap e = new MindMap();
        e.setUserId(userId);
        e.setTitle(requireNotBlank(dto.getTitle(), "思维导图标题不能为空"));
        e.setData(serializeData(dto.getData()));
        mindMapMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMindMap(Long id, MindMapDTO dto, Long userId) {
        MindMap e = requireOwn(mindMapMapper.selectById(id), userId);
        if (StringUtils.hasText(dto.getTitle())) {
            e.setTitle(requireNotBlank(dto.getTitle(), "思维导图标题不能为空"));
        }
        if (dto.getData() != null) {
            e.setData(serializeData(dto.getData()));
        }
        mindMapMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMindMap(Long id, Long userId) {
        MindMap e = requireOwn(mindMapMapper.selectById(id), userId);
        mindMapMapper.deleteById(e.getId());
    }

    private MindMapVO toVO(MindMap e) {
        MindMapVO vo = new MindMapVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setTitle(e.getTitle());
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
            log.warn("思维导图数据解析失败，回退空结构: {}", ex.getMessage());
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
            throw new BusinessException("思维导图数据序列化失败");
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

    private MindMap requireOwn(MindMap e, Long userId) {
        if (e == null) {
            throw new BusinessException("思维导图不存在");
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
