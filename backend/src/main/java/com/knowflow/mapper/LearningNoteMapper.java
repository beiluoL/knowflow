package com.knowflow.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningNote;
import org.apache.ibatis.annotations.*;
/** 显式 SQL 同时约束所有者、修订号和删除状态。 */
@Mapper public interface LearningNoteMapper extends BaseMapper<LearningNote> {
 @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
 @Select("SELECT * FROM learning_note WHERE id=#{id} AND user_id=#{userId}")
 LearningNote ownedIncludingDeleted(@Param("id") Long id,@Param("userId") Long userId);
 @Select("SELECT * FROM learning_note WHERE user_id=#{userId} AND import_key=#{key}")
 LearningNote imported(@Param("userId") Long userId,@Param("key") String key);
 @Update("UPDATE learning_note SET folder_id=#{n.folderId},favorite=#{n.favorite},source_doc_id=#{n.sourceDocId},title=#{n.title},content=#{n.content},tags=#{n.tags},source_title=#{n.sourceTitle},chapter_id=#{n.chapterId},path_id=#{n.pathId},source_type=#{n.sourceType},status=#{n.status},deleted=#{n.deleted},revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=#{n.id} AND user_id=#{n.userId} AND revision=#{expected} AND deleted=#{previousDeleted}")
 int compareAndSet(@Param("n") LearningNote note,@Param("expected") Long expected,@Param("previousDeleted") Integer previousDeleted);
 @Select("<script>SELECT * FROM learning_note WHERE user_id=#{userId} AND deleted=#{deleted}<if test='keyword != null and keyword != &quot;&quot;'> AND (title LIKE CONCAT('%',#{keyword},'%') OR content LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY update_time DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>")
 java.util.List<LearningNote> selectPageRows(@Param("userId") Long userId,@Param("deleted") int deleted,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
 @Select("<script>SELECT COUNT(*) FROM learning_note WHERE user_id=#{userId} AND deleted=#{deleted}<if test='keyword != null and keyword != &quot;&quot;'> AND (title LIKE CONCAT('%',#{keyword},'%') OR content LIKE CONCAT('%',#{keyword},'%'))</if></script>")
 long countPage(@Param("userId") Long userId,@Param("deleted") int deleted,@Param("keyword") String keyword);
}
