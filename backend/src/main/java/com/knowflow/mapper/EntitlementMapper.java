package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Entitlement;
import org.apache.ibatis.annotations.Mapper;

/** Entitlement 表数据访问层。 */
@Mapper
public interface EntitlementMapper extends BaseMapper<Entitlement> {
}
