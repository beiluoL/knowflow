package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区帖子表数据访问层，基于 MyBatis-Plus 提供帖子的增删改查。
 */
@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
}
