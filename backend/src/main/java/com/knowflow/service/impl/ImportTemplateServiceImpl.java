package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.ImportTemplateDTO;
import com.knowflow.entity.ImportTemplate;
import com.knowflow.mapper.ImportTemplateMapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.ImportTemplateService;
import com.knowflow.vo.ImportTemplateVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入规则模板服务实现。
 * <p>可见范围：预设模板(is_preset=1)对所有用户可见；用户自定义模板仅创建者可见。</p>
 */
@Service
public class ImportTemplateServiceImpl extends ServiceImpl<ImportTemplateMapper, ImportTemplate>
        implements ImportTemplateService {

    private static final long SYS_USER_ID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ImportTemplateVO> listTemplates(String type, Integer enabled, Long userId) {
        LambdaQueryWrapper<ImportTemplate> qw = new LambdaQueryWrapper<>();
        // 可见范围：预设模板 或 本人创建
        qw.and(w -> w.eq(ImportTemplate::getIsPreset, 1).or().eq(ImportTemplate::getUserId, userId));
        qw.eq(ImportTemplate::getDeleted, 0);
        if (type != null && !type.isEmpty()) {
            qw.eq(ImportTemplate::getType, type);
        }
        if (enabled != null) {
            qw.eq(ImportTemplate::getEnabled, enabled);
        }
        qw.orderByDesc(ImportTemplate::getIsPreset)
          .orderByDesc(ImportTemplate::getIsDefault)
          .orderByDesc(ImportTemplate::getUpdateTime);
        List<ImportTemplate> list = list(qw);
        List<ImportTemplateVO> vos = new ArrayList<>();
        for (ImportTemplate t : list) {
            vos.add(toVO(t));
        }
        return vos;
    }

    @Override
    public ImportTemplateVO detail(Long id, Long userId) {
        ImportTemplate t = getById(id);
        if (t == null || t.getDeleted() == 1) {
            return null;
        }
        if (t.getIsPreset() != 1 && !t.getUserId().equals(userId)) {
            return null;
        }
        return toVO(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportTemplateVO create(ImportTemplateDTO dto, Long userId) {
        ImportTemplate t = new ImportTemplate();
        t.setUserId(userId);
        t.setName(dto.getName());
        t.setType(dto.getType());
        t.setDescription(dto.getDescription());
        t.setContent(dto.getContent());
        t.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        t.setIsDefault(0);
        t.setIsPreset(0);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        t.setDeleted(0);
        save(t);
        return toVO(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportTemplateVO update(Long id, ImportTemplateDTO dto, Long userId) {
        ImportTemplate t = getById(id);
        if (t == null || t.getDeleted() == 1) {
            throw new IllegalArgumentException("模板不存在");
        }
        // 预设模板不可改；仅本人可改
        if (t.getIsPreset() == 1) {
            throw new IllegalArgumentException("预设模板不可编辑，请基于预设复制后修改");
        }
        if (!t.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权编辑该模板");
        }
        t.setName(dto.getName());
        t.setType(dto.getType());
        t.setDescription(dto.getDescription());
        t.setContent(dto.getContent());
        if (dto.getEnabled() != null) {
            t.setEnabled(dto.getEnabled());
        }
        t.setUpdateTime(LocalDateTime.now());
        updateById(t);
        return toVO(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTemplate(Long id, Long userId) {
        ImportTemplate t = getById(id);
        if (t == null || t.getDeleted() == 1) {
            return false;
        }
        if (t.getIsPreset() == 1) {
            return false; // 预设模板不可删除
        }
        if (!t.getUserId().equals(userId)) {
            return false;
        }
        // 借助 @TableLogic（deleted 字段）由框架执行逻辑删除 UPDATE ... SET deleted=1
        removeById(t.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long id, Long userId) {
        ImportTemplate t = getById(id);
        if (t == null || t.getDeleted() == 1 || t.getIsPreset() == 1 || !t.getUserId().equals(userId)) {
            return false;
        }
        t.setEnabled(t.getEnabled() == 1 ? 0 : 1);
        t.setUpdateTime(LocalDateTime.now());
        updateById(t);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long id, Long userId) {
        ImportTemplate t = getById(id);
        if (t == null || t.getDeleted() == 1 || t.getEnabled() != 1) {
            return false;
        }
        // 仅可见模板可设默认
        if (t.getIsPreset() != 1 && !t.getUserId().equals(userId)) {
            return false;
        }
        // 同类型其余模板取消默认
        LambdaQueryWrapper<ImportTemplate> qw = new LambdaQueryWrapper<>();
        qw.eq(ImportTemplate::getType, t.getType());
        qw.eq(ImportTemplate::getIsDefault, 1);
        qw.eq(ImportTemplate::getDeleted, 0);
        list(qw).forEach(o -> {
            o.setIsDefault(0);
            o.setUpdateTime(LocalDateTime.now());
            updateById(o);
        });
        t.setIsDefault(1);
        t.setUpdateTime(LocalDateTime.now());
        updateById(t);
        return true;
    }

    /** 将实体转为 VO，并解析 content 生成统计与规则摘要。 */
    private ImportTemplateVO toVO(ImportTemplate t) {
        ImportTemplateVO vo = new ImportTemplateVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setName(t.getName());
        vo.setType(t.getType());
        vo.setDescription(t.getDescription());
        vo.setContent(t.getContent());
        vo.setEnabled(t.getEnabled());
        vo.setIsDefault(t.getIsDefault());
        vo.setIsPreset(t.getIsPreset());
        vo.setCreateTime(t.getCreateTime());
        vo.setUpdateTime(t.getUpdateTime());
        if (t.getContent() != null && !t.getContent().isEmpty()) {
            try {
                JsonNode root = objectMapper.readTree(t.getContent());
                JsonNode fs = root.get("fieldSchema");
                JsonNode va = root.get("validation");
                vo.setFieldCount(fs != null && fs.isArray() ? fs.size() : 0);
                vo.setValidationCount(va != null && va.isArray() ? va.size() : 0);
                JsonNode rules = root.get("rules");
                if (rules != null) {
                    StringBuilder sb = new StringBuilder();
                    JsonNode hl = rules.get("headingLevel");
                    if (hl != null) {
                        sb.append("H").append(hl.asInt());
                    }
                    JsonNode mp = rules.get("maxPerDoc");
                    if (mp != null) {
                        sb.append(" · 单篇 ≤").append(mp.asInt());
                    }
                    JsonNode qt = rules.get("questionTypes");
                    if (qt != null && qt.isArray() && qt.size() > 0) {
                        sb.append(" · ");
                        for (int i = 0; i < qt.size(); i++) {
                            if (i > 0) sb.append("/");
                            sb.append(qt.get(i).asText());
                        }
                    }
                    vo.setRuleSummary(sb.toString());
                }
            } catch (Exception e) {
                vo.setRuleSummary("解析失败");
            }
        }
        return vo;
    }
}
