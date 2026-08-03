package com.knowflow.agent.tool;

/**
 * 工具执行/校验异常：参数非法、权限不足、运行失败等统一抛出。
 */
public class ToolException extends Exception {
    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
