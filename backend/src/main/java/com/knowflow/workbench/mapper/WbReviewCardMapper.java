package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbReviewCard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供工作台复习卡片的数据访问映射。
 */
@Mapper
public interface WbReviewCardMapper extends BaseMapper<WbReviewCard> {
}
