package com.knowflow.dto;

import lombok.Data;

/**
 * 代码在线运行结果。
 */
@Data
public class CodeRunResult {
    /**
     * 执行状态：
     * SUCCESS       运行结束且退出码为 0
     * COMPILE_ERROR 编译（Java/C++）失败
     * RUNTIME_ERROR 运行期异常（退出码非 0，如除零、未捕获异常、找不到模块等）
     * TIMEOUT       超过时间上限被强制终止
     * INTERNAL_ERROR 服务端不支持该语言 / 代码为空 / 内部异常
     */
    public enum Status {
        SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, INTERNAL_ERROR
    }

    private Status status;

    /** 标准输出（已截断时附带提示） */
    private String output;

    /** 标准错误 / 错误信息 */
    private String error;

    /** 子进程退出码（超时/内部错误为 null 或 -1） */
    private Integer exitCode;

    /** 耗时（毫秒） */
    private Long timeUsedMs;
}
