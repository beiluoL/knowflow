package com.knowflow.service;

import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;

/**
 * 代码在线执行引擎（SC1-IDE-01 地基）。
 * 通过进程直执行用户代码，支持 Python/Java/JavaScript/C++ 真实运行。
 */
public interface CodeExecutionService {

    /**
     * 执行一段用户代码并返回结果。
     *
     * @param request 含语言、代码、可选 stdin 与超时；工作区模式额外携带 workspace/entryFile
     * @param userId  当前登录用户 ID（工作区模式用于定位持久目录；单文件模式可为 null）
     * @return 统一结果（状态 / 输出 / 错误 / 退出码 / 耗时）
     */
    CodeRunResult execute(CodeRunRequest request, Long userId);
}
