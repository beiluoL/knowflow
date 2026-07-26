package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息表数据访问层，基于 MyBatis-Plus 提供聊天消息的增删改查。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
