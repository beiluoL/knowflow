package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.StudyGroupMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习小组成员 Mapper
 */
@Mapper
public interface StudyGroupMemberMapper extends BaseMapper<StudyGroupMember> {
}