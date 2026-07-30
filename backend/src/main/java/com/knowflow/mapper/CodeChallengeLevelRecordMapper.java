package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CodeChallengeLevelRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户关卡通关记录表数据访问层，基于 MyBatis-Plus 提供通关记录的增删改查。
 */
@Mapper
public interface CodeChallengeLevelRecordMapper extends BaseMapper<CodeChallengeLevelRecord> {
}
