package com.knowflow.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 专注完成获得经验 DTO。
 */
@Data
public class FocusGainDTO {
    /** 本次专注分钟数 */
    @Min(value = 0, message = "专注分钟数不能为负")
    private Integer minutes;

    /** 本次完成番茄数 */
    @Min(value = 0, message = "番茄数不能为负")
    private Integer pomodoros;
}
