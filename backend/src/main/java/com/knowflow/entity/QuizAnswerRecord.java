package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quiz_answer_record")
/** 在线答题记录实体，保存用户每次作答的答案、判分结果与耗时。 */
public class QuizAnswerRecord extends BaseEntity {

    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 题目ID（逻辑外键 quiz_question.id） */
    private Long questionId;

    /** 用户提交的答案 */
    private String userAnswer;

    /** 是否答对：0 错误 / 1 正确 */
    private Integer isCorrect;

    /** 本题得分（0-100） */
    private Integer score;

    /** 答题耗时（秒） */
    private Integer timeCost;

    /** AI 评语（简答题等主观题预留） */
    private String aiFeedback;
}
