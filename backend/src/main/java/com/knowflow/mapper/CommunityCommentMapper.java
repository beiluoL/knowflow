package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CommunityComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区评论表数据访问层，基于 MyBatis-Plus 提供评论的增删改查。
 */
@Mapper
public interface CommunityCommentMapper extends BaseMapper<CommunityComment> {
}
