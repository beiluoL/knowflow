package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.KbMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库成员表数据访问层，基于 MyBatis-Plus 提供 CRUD 能力。
 */
@Mapper
public interface KbMemberMapper extends BaseMapper<KbMember> {
}
