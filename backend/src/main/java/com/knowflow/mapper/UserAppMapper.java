package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.UserApp;
import org.apache.ibatis.annotations.Mapper;

/** user_app 表数据访问层。 */
@Mapper
public interface UserAppMapper extends BaseMapper<UserApp> {
}
