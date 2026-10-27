package com.knowflow.learning.actions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper public interface LearningActionDraftMapper extends BaseMapper<LearningActionDraft> {
 @Select("SELECT * FROM learning_action_draft WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
 LearningActionDraft lockOwned(@Param("id") String id,@Param("userId") Long user);
}
