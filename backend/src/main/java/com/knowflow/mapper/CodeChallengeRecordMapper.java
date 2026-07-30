package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CodeChallengeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户挑战进度表数据访问层，基于 MyBatis-Plus 提供进度记录的增删改查。
 */
@Mapper
public interface CodeChallengeRecordMapper extends BaseMapper<CodeChallengeRecord> {
}
