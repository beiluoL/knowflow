package com.knowflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FocusSessionEndDTO {

    private Long id;

    @NotNull(message = "专注时长不能为空")
    @Min(value = 0, message = "专注时长不能为负")
    private Integer durationMin;

    @Min(value = 0, message = "分心次数不能为负")
    private Integer distractionCount;

    @Min(value = 0, message = "完成番茄数不能为负")
    private Integer completedPomodoros;

    private Long associatedTaskId;

    @Min(value = 1, message = "质量评分最小为1")
    @Max(value = 5, message = "质量评分最大为5")
    private Integer qualityRating;

    @Size(max = 500, message = "复盘记录不能超过500字")
    private String note;
}
