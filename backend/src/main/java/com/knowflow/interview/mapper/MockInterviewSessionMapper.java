package com.knowflow.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.interview.entity.MockInterviewSession;
import org.apache.ibatis.annotations.Mapper;

/** 模拟面试会话 Mapper。所有查询必须在服务层按 userId 归属过滤。 */
@Mapper
public interface MockInterviewSessionMapper extends BaseMapper<MockInterviewSession> {
}
