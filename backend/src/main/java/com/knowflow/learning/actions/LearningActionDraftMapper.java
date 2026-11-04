package com.knowflow.learning.actions;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

/** 学习行动草稿的 MyBatis 持久层：提供按主键+用户悲观锁定（FOR UPDATE）的读取，支撑草稿的并发安全确认写入。 */
@Mapper
public interface LearningActionDraftMapper extends BaseMapper<LearningActionDraft> {
    @Select("SELECT * FROM learning_action_draft WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    LearningActionDraft lockOwned(@Param("id") String id, @Param("userId") Long user);
}
