package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningFlashcard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 学习闪卡表数据访问层，基于 MyBatis-Plus 提供闪卡的增删改查。
 */
@Mapper
public interface LearningFlashcardMapper extends BaseMapper<LearningFlashcard> {
    @Select("SELECT * FROM learning_flashcard WHERE id=#{id} AND deleted=0 FOR UPDATE")
    LearningFlashcard selectForReview(Long id);

    @Select("SELECT fsrs_state FROM learning_flashcard WHERE id=#{id} AND deleted=0")
    String fsrsState(Long id);

    @Select("SELECT COUNT(*) FROM learning_flashcard_note_card WHERE card_id=#{id}")
    int countNoteLinks(Long id);
}
