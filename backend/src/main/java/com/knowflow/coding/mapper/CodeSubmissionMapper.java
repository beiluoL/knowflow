package com.knowflow.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.coding.entity.CodeSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供编程题提交记录的数据访问映射。
 */
@Mapper
public interface CodeSubmissionMapper extends BaseMapper<CodeSubmission> {
}
