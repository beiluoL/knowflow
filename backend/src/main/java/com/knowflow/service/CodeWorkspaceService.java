package com.knowflow.service;

import com.knowflow.dto.CodeWorkspaceFileVO;

import java.nio.file.Path;
import java.util.List;

/**
 * 代码工作区服务（SC1-IDE-02 可重置实验沙箱）。
 * <p>为每个登录用户维护一个持久化目录，文件跨运行保留，支持新建/编辑/删除/重置，
 * 与实时运行沙箱共享同一目录，从而支持多文件小项目开发。
 */
public interface CodeWorkspaceService {

    /** 解析并确保用户工作区目录存在，返回其绝对路径 */
    Path getWorkspaceDir(Long userId);

    /** 列出工作区全部文件（含内容） */
    List<CodeWorkspaceFileVO> listFiles(Long userId);

    /** 新建或覆盖写入一个文件，path 为单层文件名（含扩展名） */
    CodeWorkspaceFileVO saveFile(Long userId, String path, String content);

    /** 删除指定文件；不存在时忽略 */
    void deleteFile(Long userId, String path);

    /** 重置工作区：清空目录下所有文件（保留目录本身） */
    void reset(Long userId);
}
