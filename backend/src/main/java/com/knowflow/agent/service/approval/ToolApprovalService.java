package com.knowflow.agent.service.approval;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/** 管理单次 SSE 执行内、仅由发起用户决定的短期审批。 */
@Service
public class ToolApprovalService {
  private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

  /** 创建与当前认证用户绑定的执行作用域。 */
  public Scope openScope(long ownerId) {
    return new Scope(ownerId);
  }

  /** 只允许发起者提交一次决定，过期或无权访问均返回 false。 */
  public boolean decide(long ownerId, String id, boolean approved) {
    var request = pending.get(id);
    return request != null
        && request.ownerId == ownerId
        && request.remainingNanos() > 0
        && request.decision.complete(approved);
  }

  public final class Scope implements AutoCloseable {
    private final long ownerId;
    private boolean closed;
    private Pending active;

    private Scope(long ownerId) {
      this.ownerId = ownerId;
    }

    /** 同一执行同时只允许一个待审批调用。关闭后返回 null。 */
    public synchronized String request(Duration timeout) {
      if (closed || active != null) {
        return null;
      }
      String id = UUID.randomUUID().toString();
      active = new Pending(id, ownerId, System.nanoTime() + timeout.toNanos());
      pending.put(id, active);
      return id;
    }

    /** 等待该次决定，关闭、超时或中断一律拒绝。 */
    public boolean await(String id) {
      Pending request;
      synchronized (this) {
        if (closed || active == null || !Objects.equals(active.id, id)) {
          return false;
        }
        request = active;
      }
      try {
        boolean approved =
            request.decision.get(Math.max(0, request.remainingNanos()), TimeUnit.NANOSECONDS);
        synchronized (this) {
          boolean accepted =
              approved && !closed && active == request && request.remainingNanos() > 0;
          if (active == request) {
            clearActive();
          }
          return accepted;
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return false;
      } catch (ExecutionException | TimeoutException exception) {
        return false;
      } finally {
        synchronized (this) {
          if (active == request) {
            clearActive();
          }
        }
      }
    }

    /** 使待审批立即失效并唤醒等待线程。 */
    @Override
    public synchronized void close() {
      closed = true;
      clearActive();
    }

    private void clearActive() {
      if (active != null) {
        pending.remove(active.id, active);
        active.decision.complete(false);
        active = null;
      }
    }
  }

  private static final class Pending {
    private final String id;
    private final long ownerId;
    private final long deadline;
    private final CompletableFuture<Boolean> decision = new CompletableFuture<>();

    private Pending(String id, long ownerId, long deadline) {
      this.id = id;
      this.ownerId = ownerId;
      this.deadline = deadline;
    }

    private long remainingNanos() {
      return deadline - System.nanoTime();
    }
  }
}
