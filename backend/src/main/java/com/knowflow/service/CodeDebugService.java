package com.knowflow.service;

import com.knowflow.dto.CodeDebugRequest;
import com.knowflow.dto.CodeDebugResult;

/** 轻量在线调试服务（推进 2.1）：逐行追踪 + 错误精确定位。 */
public interface CodeDebugService {
    CodeDebugResult debug(CodeDebugRequest request, Long userId);
}
