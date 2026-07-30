package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CodeChallengeLevel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编程挑战关卡表数据访问层，基于 MyBatis-Plus 提供关卡的增删改查。
 */
@Mapper
public interface CodeChallengeLevelMapper extends BaseMapper<CodeChallengeLevel> {
}
