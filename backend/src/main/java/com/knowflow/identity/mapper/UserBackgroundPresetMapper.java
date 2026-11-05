package com.knowflow.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.identity.entity.UserBackgroundPreset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供用户背景预设的数据访问映射。
 */
@Mapper
public interface UserBackgroundPresetMapper extends BaseMapper<UserBackgroundPreset> {
}
