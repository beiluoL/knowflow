package com.knowflow.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * 文档正文抽取服务：基于 Apache Tika 统一解析 PDF/DOC/DOCX/PPT/PPTX/TXT/HTML/MD 等常见格式。
 *
 * <p>设计要点：
 * 1. 仅依赖文件内容（magic bytes）自动探测类型，不依赖扩展名，兼容性最佳；
 * 2. 限制抽取文本最大长度（50 万字符），防止超大文件导致 OOM；
 * 3. 增加解析超时（默认 60 秒），避免损坏/加密 PDF 等卡死请求线程；
 * 4. 解析失败（文件损坏/不支持类型/超时）兜底返回空串，绝不阻断上传主流程。
 */
@Slf4j
@Component
public class DocumentTextExtractor {

    /** 抽取文本上限：50 万字符，约 1MB 纯文本，足够覆盖绝大多数文档正文，同时保护内存。 */
    private static final int MAX_EXTRACT_CHARS = 500_000;

    /** 单文档解析超时：60 秒。对一般 <10MB 的 PDF/DOC 绰绰有余，超大/损坏文件直接放弃。 */
    private static final Duration EXTRACT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 单例 Tika（内部会加载 Parser 并做 ServiceLoader，一次即可）。
     * 经验：Tika 本身是线程安全的；具体 Parser 在 parseToString 内部每次新建，
     * 不会跨线程共享有状态的 Parser，符合经验 734636 的并发模型要求。
     */
    private final Tika tika = new Tika();

    /**
     * 小型线程池，每个解析任务独立一个线程跑，配合 Future#get(timeout) 实现超时中断。
     * 使用核心 2、最大 8 的弹性池，瞬时并发不高时快速回收。
     */
    private final ExecutorService extractPool = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "tika-extract");
                t.setDaemon(true);
                return t;
            },
            // 调用者线程直接执行：当解析并发瞬间超过上限时，退化到当前线程阻塞执行，避免丢弃任务
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 抽取上传文件的纯文本正文，带超时中断兜底。
     *
     * @param file 上传的文件（可为空）
     * @return 抽取出的文本；文件为空/解析失败/超时均返回空串
     */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        Future<String> future = extractPool.submit(() -> doExtract(file));
        try {
            return future.get(EXTRACT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("文档文本抽取超时（>{}}ms），返回空串，文件名：{}",
                    EXTRACT_TIMEOUT.toMillis(), file.getOriginalFilename());
            // 务必取消底层解析线程，防止它继续占用资源读取加密/损坏的 PDF
            future.cancel(true);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("文档文本抽取被中断：{}", file.getOriginalFilename());
            future.cancel(true);
            return "";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("文档文本抽取执行异常：{}", file.getOriginalFilename(), cause);
            return "";
        }
    }

    /** 核心抽取逻辑：Tika 解析，异常兜底空串。 */
    private String doExtract(MultipartFile file) {
        try (var in = file.getInputStream()) {
            Metadata metadata = new Metadata();
            // Tika.parseToString 内部：自动探测 MIME → 实例化对应 Parser → BodyContentHandler(-1 长度由 MAX_EXTRACT_CHARS 控制)
            String text = tika.parseToString(in, metadata, MAX_EXTRACT_CHARS);
            if (text == null) return "";
            // 轻量清理：去掉 NUL 字符、压缩连续空白，减少后续入库冗余
            text = text.replace('\u0000', ' ').replaceAll("[ \\t]+", " ").trim();
            return text;
        } catch (IOException e) {
            log.warn("文档文本抽取 IO 失败：{}", file.getOriginalFilename(), e);
            return "";
        } catch (Exception e) {
            // 损坏文档 / 加密 PDF / 不支持类型都会走到这里，兜底空串
            log.warn("文档文本抽取失败（可能损坏或不支持）：{}", file.getOriginalFilename(), e);
            return "";
        }
    }

    @PreDestroy
    public void shutdown() {
        extractPool.shutdownNow();
    }
}
