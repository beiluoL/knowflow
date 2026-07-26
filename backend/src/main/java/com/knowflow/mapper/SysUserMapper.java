package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户表数据访问层，基于 MyBatis-Plus 提供用户的增删改查。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
