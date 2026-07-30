package com.knowflow.mapper;

import com.knowflow.entity.PrivateConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私聊会话 Mapper
 */
@Mapper
public interface PrivateConversationMapper extends BaseMapper<PrivateConversation> {
}
