package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CodeChallenge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编程挑战赛道表数据访问层，基于 MyBatis-Plus 提供赛道的增删改查。
 */
@Mapper
public interface CodeChallengeMapper extends BaseMapper<CodeChallenge> {
}
