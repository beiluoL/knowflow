package com.knowflow.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.store.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/** OrderItem 表数据访问层。 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
