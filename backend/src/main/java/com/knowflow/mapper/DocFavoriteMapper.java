package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档收藏表数据访问层，基于 MyBatis-Plus 提供文档收藏的增删改查。
 */
@Mapper
public interface DocFavoriteMapper extends BaseMapper<DocFavorite> {
}
