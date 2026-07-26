package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话表数据访问层，基于 MyBatis-Plus 提供会话的增删改查。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
