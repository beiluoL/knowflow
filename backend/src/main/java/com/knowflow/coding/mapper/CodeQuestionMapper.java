package com.knowflow.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.coding.entity.CodeQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 代码题目表数据访问层，基于 MyBatis-Plus 提供题目的增删改查。
 */
@Mapper
public interface CodeQuestionMapper extends BaseMapper<CodeQuestion> {
    /** 私有导入台账键；涉及多个所有者时，只要存在他人归属即拒绝。 */
    @Select("SELECT resource_key FROM interview_seed_resource WHERE resource_type='CODE' "
            + "AND resource_id=#{id} AND resource_key LIKE 'user/%'")
    List<String> selectPrivateResourceKeys(@Param("id") Long id);
    /** 使用绑定的用户前缀追加私有台账可见性条件。 */
    default void restrictToActor(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CodeQuestion> wrapper, Long actor) {
            wrapper.apply("NOT EXISTS (SELECT 1 FROM interview_seed_resource private_code "
                            + "WHERE private_code.resource_type='CODE' "
                            + "AND private_code.resource_id=code_question.id "
                            + "AND private_code.resource_key LIKE 'user/%' "
                            + "AND private_code.resource_key NOT LIKE {0})",
                    "user/" + actor + "/%");
    }
}

