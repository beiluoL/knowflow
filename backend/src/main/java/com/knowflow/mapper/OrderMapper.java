package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/** Order 表数据访问层。 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
