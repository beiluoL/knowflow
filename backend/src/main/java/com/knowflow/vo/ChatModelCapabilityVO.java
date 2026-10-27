package com.knowflow.vo;

import com.knowflow.ai.capability.ChatModelCapability;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天模型能力视图：供前端「深度思考」开关判断当前模型是否支持推理内容，
 * 以及不支持时的原因说明。
 */
@Data
public class ChatModelCapabilityVO {

    /** 当前生效的 provider（平台默认通道可能为空）。 */
    private String provider;

    /** 当前生效的默认模型。 */
    private String defaultModel;

    private List<ChatModelCapability> models = new ArrayList<>();
}
