package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CommunityCommentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评论点赞关系表数据访问层（F-06）。
 *
 * <p>注意：本表<b>不使用逻辑删除</b>。唯一约束 {@code uk_comment_like(comment_id, user_id)}
 * 不包含 deleted 列，若取消点赞走逻辑删除，残留行会永久占位，导致用户「取消后无法再次点赞」
 * （insert 触发 DuplicateKeyException，计数也不再累加）。因此这里用原生 SQL 做物理删除与存在性判断，
 * 绕开 MyBatis-Plus 自动追加的 {@code deleted = 0} 条件。</p>
 */
@Mapper
public interface CommunityCommentLikeMapper extends BaseMapper<CommunityCommentLike> {

    /**
     * 查询点赞记录 id（不受逻辑删除标记影响，物理行存在即视为已点赞）。
     *
     * @return 记录主键；未点赞返回 null
     */
    @Select("SELECT id FROM community_comment_like WHERE comment_id = #{commentId} AND user_id = #{userId} LIMIT 1")
    Long findLikeId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 物理删除点赞记录，保证唯一约束键位被真正释放。
     *
     * @return 实际删除行数（0 表示本就未点赞，调用方据此避免误减计数）
     */
    @Delete("DELETE FROM community_comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int physicalDelete(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 评论被删除时清理其全部点赞记录，避免脏数据长期占用唯一键。
     */
    @Delete("DELETE FROM community_comment_like WHERE comment_id = #{commentId}")
    int physicalDeleteByComment(@Param("commentId") Long commentId);

    /**
     * 顶级评论被删除时，一并清理其所有子回复的点赞记录。
     */
    @Delete("DELETE FROM community_comment_like WHERE comment_id IN "
            + "(SELECT id FROM community_comment WHERE parent_id = #{parentId})")
    int physicalDeleteByParentComment(@Param("parentId") Long parentId);

    /**
     * 帖子被删除时，清理该帖下全部评论的点赞记录。
     */
    @Delete("DELETE FROM community_comment_like WHERE comment_id IN "
            + "(SELECT id FROM community_comment WHERE post_id = #{postId})")
    int physicalDeleteByPost(@Param("postId") Long postId);
}
