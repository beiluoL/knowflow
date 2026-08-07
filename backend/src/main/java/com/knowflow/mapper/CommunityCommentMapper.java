package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CommunityComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 社区评论表数据访问层，基于 MyBatis-Plus 提供评论的增删改查。
 *
 * <p>计数字段（like_count / reply_count）一律走下列原子 SQL 自增自减，
 * 严禁「先查询再 set」，避免并发场景下的计数丢失（P1-M10）。</p>
 */
@Mapper
public interface CommunityCommentMapper extends BaseMapper<CommunityComment> {

    /** 点赞数原子 +1 */
    @Update("UPDATE community_comment SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementLikeCount(@Param("id") Long id);

    /** 点赞数原子 -1（下限 0，防止并发导致负数） */
    @Update("UPDATE community_comment SET like_count = GREATEST(0, like_count - 1) WHERE id = #{id} AND deleted = 0")
    int decrementLikeCount(@Param("id") Long id);

    /** 回复数原子 +1 */
    @Update("UPDATE community_comment SET reply_count = reply_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementReplyCount(@Param("id") Long id);

    /** 回复数原子减 delta（下限 0） */
    @Update("UPDATE community_comment SET reply_count = GREATEST(0, reply_count - #{delta}) WHERE id = #{id} AND deleted = 0")
    int decrementReplyCount(@Param("id") Long id, @Param("delta") int delta);

    /** 逻辑删除某顶级评论下的全部子回复，返回受影响行数（用于回退帖子评论数） */
    @Update("UPDATE community_comment SET deleted = 1 WHERE parent_id = #{parentId} AND deleted = 0")
    int softDeleteRepliesByParent(@Param("parentId") Long parentId);

    /** 删除帖子时级联逻辑删除其全部评论，返回受影响行数 */
    @Update("UPDATE community_comment SET deleted = 1 WHERE post_id = #{postId} AND deleted = 0")
    int softDeleteByPostId(@Param("postId") Long postId);
}
