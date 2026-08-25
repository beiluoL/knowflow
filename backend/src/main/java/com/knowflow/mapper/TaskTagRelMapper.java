package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TaskTagRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务-标签关联数据访问层。
 */
@Mapper
public interface TaskTagRelMapper extends BaseMapper<TaskTagRel> {
}
