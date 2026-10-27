package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TaskTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 任务-标签关联数据访问层。
 */
@Mapper
public interface TaskTagRelMapper extends BaseMapper<TaskTagRel> {
    /** 恢复唯一键占用的历史关系；调用方已锁定所属任务并校验标签归属。 */
    @Update("UPDATE task_tag_rel SET deleted=0, update_time=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND tag_id=#{tagId} AND deleted=1")
    int restoreDeleted(@Param("taskId") Long taskId, @Param("tagId") Long tagId);
}
