package com.knowflow.agent.tool;

/**
 * 工具权限等级：
 * SAFE      只读、无副作用（代码只读执行、文件读取）—— 可直接执行
 * WRITE     写操作（文件写入）—— 需用户二次确认
 * DANGEROUS 高危（数据库写/删、命令执行）—— 需显式授权，默认禁用
 */
public enum ToolPermission {
    SAFE,
    WRITE,
    DANGEROUS
}
