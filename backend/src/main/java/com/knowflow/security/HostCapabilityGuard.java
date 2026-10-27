package com.knowflow.security;

import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Host trust boundary. No role/profile/IP auto opt-in. Not an OS sandbox. */
@Component @RequiredArgsConstructor
public class HostCapabilityGuard {
    public static final long MAX_TIMEOUT_MS = 10_000;
    public static final String DISABLED = "该能力仅在可信本地开发模式启用";
    private final HostCapabilityProperties properties;
    private final ThreadLocal<Long> capturedActor = new ThreadLocal<>();
    private final java.util.concurrent.Semaphore processes = new java.util.concurrent.Semaphore(8);

    public ProcessLease acquireProcess(long waitMs) {
        requireAccess();
        try {
            if (!processes.tryAcquire(Math.max(1,Math.min(MAX_TIMEOUT_MS,waitMs)),java.util.concurrent.TimeUnit.MILLISECONDS))
                throw new BusinessException(409,"执行队列繁忙，请稍后重试");
            return new ProcessLease();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException(409,"执行等待被中断"); }
    }

    public final class ProcessLease implements AutoCloseable {
        private boolean closed;
        public void close() { if (!closed) { closed=true; processes.release(); } }
    }

    /** Interrupts must not leave an uncounted child after the execution lease is released. */
    public boolean awaitProcess(Process process,long timeoutMs) throws InterruptedException {
        try { return process.waitFor(timeoutMs,java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (InterruptedException ex) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    public Long requireAccess() {
        Long actor = SecurityUtils.getCurrentUserIdNullable();
        if (actor == null) actor = capturedActor.get();
        if (actor == null) throw new BusinessException(401,"未登录");
        if (!properties.isEnabled() || properties.getAuthorizedUserIds() == null
                || !properties.getAuthorizedUserIds().contains(actor)) throw new BusinessException(403,DISABLED);
        return actor;
    }

    public void requireActor(Long suppliedActor) {
        if (!Objects.equals(requireAccess(), suppliedActor)) throw new BusinessException(403,"无权使用该宿主机能力");
    }

    /** Capture only real request authentication, never a caller-provided ToolContext user ID. */
    public Runnable wrapAuthenticated(Runnable work) {
        Long actor = SecurityUtils.getCurrentUserIdNullable();
        if (actor == null) actor = capturedActor.get();
        if (actor == null) throw new BusinessException(401,"未登录");
        Long snapshot = actor;
        return () -> {
            Long prior = capturedActor.get();
            capturedActor.set(snapshot);
            try { work.run(); }
            finally { if (prior == null) capturedActor.remove(); else capturedActor.set(prior); }
        };
    }

    public long executionTimeout(Long requested, long fallback) {
        requireAccess();
        if (requested != null && (requested <= 0 || requested > MAX_TIMEOUT_MS))
            throw new BusinessException(400,"执行超时必须在1至10000毫秒之间");
        return requested != null ? requested : Math.max(1,Math.min(MAX_TIMEOUT_MS,fallback));
    }

    public void validateCode(String language, String code) {
        requireAccess();
        if (language == null || !List.of("python","py","javascript","js","java","cpp","c++","cplusplus").contains(language.trim().toLowerCase()))
            throw new BusinessException(400,"不支持的执行语言");
        if (code == null || code.isBlank()) throw new BusinessException(400,"代码不能为空");
        if (code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1048576)
            throw new BusinessException(400,"代码超过允许大小");
    }

    private List<Path> roots() {
        requireAccess();
        List<Path> roots = new ArrayList<>();
        try {
            if (properties.getAllowedRoots() != null) for (String raw : properties.getAllowedRoots()) {
                if (raw == null || raw.isBlank()) throw denied();
                Path root = Path.of(raw).toAbsolutePath().normalize();
                if (root.getParent() == null || !Files.isDirectory(root)) throw denied();
                roots.add(root);
            }
        } catch (InvalidPathException ex) { throw denied(); }
        if (roots.isEmpty()) throw denied();
        return roots;
    }

    public Path resolveAllowed(String raw, String relativeTo) {
        requireAccess();
        if (raw == null || raw.isBlank()) throw new BusinessException(400,"路径不能为空");
        try {
            Path input = Path.of(raw);
            Path target;
            if (input.isAbsolute()) target = input;
            else if (relativeTo != null && !relativeTo.isBlank()) target = requireAllowed(Path.of(relativeTo)).resolve(input);
            else {
                List<Path> roots = roots();
                if (roots.size() != 1) throw new BusinessException(400,"请选择允许范围内的基准目录");
                target = roots.get(0).resolve(input);
            }
            return requireAllowed(target);
        } catch (InvalidPathException ex) { throw new BusinessException(400,"路径格式无效"); }
    }

    /** Check lexical boundary before any target existence probe, then check the real target. */
    public Path requireAllowed(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        for (Path root : roots()) {
            try {
                Path realRoot = root.toRealPath();
                if (!normalized.startsWith(root) && !normalized.startsWith(realRoot)) continue;
                // Detect an escaping intermediate symlink even for a missing final component.
                Path existing = normalized;
                while (!Files.exists(existing,java.nio.file.LinkOption.NOFOLLOW_LINKS) && existing.getParent()!=null)
                    existing=existing.getParent();
                if (!existing.toRealPath().startsWith(realRoot)) throw denied();
                Path real = normalized.toRealPath();
                if (!real.startsWith(realRoot)) throw denied();
                if (!Files.isReadable(real)) throw denied();
                return real;
            } catch (java.nio.file.NoSuchFileException ex) {
                throw new BusinessException(404,"资源不存在");
            } catch (IOException ex) { throw denied(); }
        }
        throw denied();
    }

    /** Enforce a server-owned workspace boundary, including symlinks and new-file parents. */
    public Path requireWorkspace(Path root, Path target) {
        requireAccess();
        Path normalized=root.toAbsolutePath().normalize();
        Path candidate=target.toAbsolutePath().normalize();
        if (!candidate.startsWith(normalized)) throw denied();
        try {
            if (Files.isSymbolicLink(normalized)) throw denied();
            if (Files.isSymbolicLink(normalized.getParent())) throw denied();
            Path realRoot=realOrFuture(normalized);
            Path ancestor=candidate;
            while (!Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS) && ancestor.getParent()!=null) ancestor=ancestor.getParent();
            if (!realOrFuture(candidate).startsWith(realRoot)) throw denied();
            return candidate;
        } catch (IOException ex) { throw denied(); }
    }

    private BusinessException denied() { return new BusinessException(403,"该目录不在允许访问范围"); }

    private Path realOrFuture(Path path) throws IOException {
        Path ancestor=path;
        while (!Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS)) ancestor=ancestor.getParent();
        return ancestor.toRealPath().resolve(ancestor.relativize(path)).normalize();
    }
}
