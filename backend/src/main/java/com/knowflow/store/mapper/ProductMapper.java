package com.knowflow.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.store.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** Product 表数据访问层。 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
