package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MistakeVO {

    private Long id;

    private Long userId;

    private String question;

    private String wrongAnswer;

    private String correctAnswer;

    private String category;

    private Integer difficulty;

    private Integer reviewCount;

    private LocalDateTime lastReviewTime;

    private Integer mastered;

    private String source;

    private LocalDateTime createTime;
}
