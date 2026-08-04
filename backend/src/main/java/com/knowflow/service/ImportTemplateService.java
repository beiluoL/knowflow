package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.ImportTemplateDTO;
import com.knowflow.entity.ImportTemplate;
import com.knowflow.vo.ImportTemplateVO;

import java.util.List;

/**
 * 导入规则模板服务：提供模板的增删改查、启用/停用、默认模板设置与可见范围过滤。
 */
public interface ImportTemplateService extends IService<ImportTemplate> {

    /** 查询模板列表：当前用户可见（预设模板 + 自己创建的），可按类型/启用状态过滤。 */
    List<ImportTemplateVO> listTemplates(String type, Integer enabled, Long userId);

    /** 获取模板详情（含解析后的统计信息）。 */
    ImportTemplateVO detail(Long id, Long userId);

    /** 创建模板（归属当前用户，非预设）。 */
    ImportTemplateVO create(ImportTemplateDTO dto, Long userId);

    /** 更新模板（仅本人创建的非预设模板可改）。 */
    ImportTemplateVO update(Long id, ImportTemplateDTO dto, Long userId);

    /** 删除模板（逻辑删除；预设模板不可删，返回 false）。 */
    boolean removeTemplate(Long id, Long userId);

    /** 启用/停用切换（仅本人模板）。 */
    boolean toggle(Long id, Long userId);

    /** 设为同类型默认模板（同类型其余模板取消默认）。 */
    boolean setDefault(Long id, Long userId);
}
