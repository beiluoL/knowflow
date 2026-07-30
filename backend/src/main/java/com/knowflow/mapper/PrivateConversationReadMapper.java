package com.knowflow.mapper;

import com.knowflow.entity.PrivateConversationRead;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私聊已读游标 Mapper
 */
@Mapper
public interface PrivateConversationReadMapper extends BaseMapper<PrivateConversationRead> {
}
