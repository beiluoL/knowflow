package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.FocusSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FocusSessionMapper extends BaseMapper<FocusSession> {
    /** 串行化同一会话的结束请求，防止重试重复发奖。 */
    @org.apache.ibatis.annotations.Select("SELECT id FROM focus_session WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockSession(@org.apache.ibatis.annotations.Param("id") Long id);
}
