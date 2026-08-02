package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.LocalReaderResolveDTO;
import com.knowflow.service.LocalReaderService;
import com.knowflow.vo.LocalReaderScanVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地阅读器 Controller：通过后端代理读取本地文件系统。
 * <p>
 * 提供路径解析、目录扫描、文档内容读取、图片读取四类接口，
 * 支持用户通过输入绝对路径或相对路径加载本地 Markdown 仓库。
 * <p>
 * 所有接口均需登录认证（SecurityConfig 默认 anyRequest().authenticated()）。
 */
@RestController
@RequestMapping("/api/local-reader")
public class LocalReaderController {

    private final LocalReaderService localReaderService;

    public LocalReaderController(LocalReaderService localReaderService) {
        this.localReaderService = localReaderService;
    }

    /**
     * 路径解析：校验路径有效性并返回绝对路径。
     * <p>
     * 前端在用户输入路径后先调用此接口校验，通过后再调用 scan。
     *
     * @param dto 路径解析请求（path + relativeTo）
     * @return { absolutePath: string }
     */
    @PostMapping("/resolve")
    public Result<Map<String, String>> resolve(@RequestBody LocalReaderResolveDTO dto) {
        String absolutePath = localReaderService.resolvePath(dto);
        Map<String, String> data = new HashMap<>();
        data.put("absolutePath", absolutePath);
        return Result.success(data);
    }

    /**
     * 扫描目录：返回目录树与扁平文档列表。
     *
     * @param path 已校验的目录绝对路径
     * @return 目录扫描结果
     */
    @GetMapping("/scan")
    public Result<LocalReaderScanVO> scan(@RequestParam String path) {
        LocalReaderScanVO vo = localReaderService.scanDirectory(path);
        return Result.success(vo);
    }

    /**
     * 读取 Markdown 文档内容。
     *
     * @param rootAbsolutePath 根目录绝对路径
     * @param path              文档相对路径
     * @return { content: string }
     */
    @GetMapping("/content")
    public Result<Map<String, String>> content(
            @RequestParam String rootAbsolutePath,
            @RequestParam String path) {
        String content = localReaderService.readDocContent(rootAbsolutePath, path);
        Map<String, String> data = new HashMap<>();
        data.put("content", content);
        return Result.success(data);
    }

    /**
     * 读取图片：返回图片二进制流。
     * <p>
     * 支持通过 docPath 参数推断图片所在目录（Obsidian 仓库图片通常在 attachments 等目录）。
     *
     * @param rootAbsolutePath 根目录绝对路径
     * @param path             图片路径或文件名
     * @param docPath          引用该图片的文档相对路径（可选，用于推断图片目录）
     * @return 图片字节流
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> image(
            @RequestParam String rootAbsolutePath,
            @RequestParam String path,
            @RequestParam(required = false) String docPath) {
        byte[] data = localReaderService.findImage(rootAbsolutePath, path, docPath != null ? docPath : "");
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = guessContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(data);
    }

    /**
     * 根据文件扩展名猜测图片 MIME 类型。
     */
    private String guessContentType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG_VALUE;
            case "png":
                return MediaType.IMAGE_PNG_VALUE;
            case "gif":
                return MediaType.IMAGE_GIF_VALUE;
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "bmp":
                return "image/bmp";
            case "ico":
                return "image/x-icon";
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
