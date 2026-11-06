package com.knowflow.identity.dto;

import lombok.Data;

/** 背景预设输入，归属始终由服务端指定。 */
@Data
public class BackgroundPresetSaveDTO {
    /** 用户自定义的预设名称。 */
    private String name;
    /** 背景类型：color（纯色）、gradient（渐变）或 preset（预设）。 */
    private String bgType;
    /** CSS 背景值或内置预设标识。 */
    private String bgValue;
    /** CSS 缩略图值。 */
    private String thumbnail;
}
