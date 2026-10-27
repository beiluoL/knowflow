package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话表数据访问层，基于 MyBatis-Plus 提供会话的增删改查。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
    @org.apache.ibatis.annotations.Update("UPDATE chat_conversation SET title=#{title},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{owner} AND deleted=0 AND (title IS NULL OR TRIM(title)='' OR title='新对话')")
    int initializeTitle(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("owner") Long owner, @org.apache.ibatis.annotations.Param("title") String title);
    @org.apache.ibatis.annotations.Update("UPDATE chat_conversation SET message_count=COALESCE(message_count,0)+2,last_message=#{text},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{owner} AND deleted=0")
    int completeTurn(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("owner") Long owner, @org.apache.ibatis.annotations.Param("text") String text);
}
