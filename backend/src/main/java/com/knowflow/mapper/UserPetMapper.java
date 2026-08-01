package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.UserPet;
import org.apache.ibatis.annotations.Mapper;

/** 用户学习宠物 Mapper */
@Mapper
public interface UserPetMapper extends BaseMapper<UserPet> {
}
