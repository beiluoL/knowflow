package com.knowflow.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.community.entity.CommunityPostLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区帖子点赞关系Mapper，维护用户对帖子的点赞记录。
 */
@Mapper
public interface CommunityPostLikeMapper extends BaseMapper<CommunityPostLike> {
}
