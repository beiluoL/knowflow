package com.knowflow.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.coding.entity.CodeDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供代码草稿的数据访问映射。
 */
@Mapper
public interface CodeDraftMapper extends BaseMapper<CodeDraft> {
}
