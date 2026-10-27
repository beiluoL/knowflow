package com.knowflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 3H：错题分页筛选条件。userId 由服务端认证上下文填充，前端不可指定。
 */
@Data
public class MistakePageQuery {

    private Long userId;

    private String category;

    /** 0 未掌握 / 1 已掌握；与 reviewStatus 同时存在时以 reviewStatus 为准。 */
    private Integer mastered;

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** 题型筛选：SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER / CODE。 */
    private String questionType;

    /** 知识点编码（quiz_question.topic_code）。 */
    private String topicCode;

    /** 来源：精确 QUIZ / CODE；也支持沙箱来源前缀 "code:"。 */
    private List<String> sources;

    /**
     * 错误原因（仅 CODE 题有效）：WRONG_ANSWER / COMPILE_ERROR / RUNTIME_ERROR /
     * TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED。
     */
    private String errorReason;

    /** 复习状态快捷筛选：pending 未掌握 / mastered 已掌握 / due 已到期（未掌握且到复习窗口）。 */
    private String reviewStatus;

    private LocalDateTime from;
    private LocalDateTime to;
}
