package com.knowflow.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.community.entity.StudyGroupMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习小组成员 Mapper
 */
@Mapper
public interface StudyGroupMemberMapper extends BaseMapper<StudyGroupMember> {
}