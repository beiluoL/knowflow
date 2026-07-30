package com.knowflow.mapper;

import com.knowflow.entity.PrivateMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私聊消息 Mapper
 */
@Mapper
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {
}
