package com.knowflow.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.coding.entity.CodeProblem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供编程题定义的数据访问映射。
 */
@Mapper
public interface CodeProblemMapper extends BaseMapper<CodeProblem> {
}
