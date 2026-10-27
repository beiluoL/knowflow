package com.knowflow.exam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 考试超时自动交卷调度器。
 *
 * <p>扫描已过服务端截止时间（exam_attempt.server_deadline）仍处于 IN_PROGRESS 的尝试，
 * 以截止前自动保存的草稿为依据完成判分；实际认领在 ExamServiceImpl 内通过条件 UPDATE 完成，
 * 因此本调度器在多实例同时运行、或与用户手动交卷并发时，每场考试只会判分一次。
 *
 * <p>可用 {@code knowflow.exam.timeout-scheduler.enabled=false} 关闭（如离线批处理节点）。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "knowflow.exam.timeout-scheduler.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ExamTimeoutScheduler {

    private final ExamService examService;

    /** 启动 30 秒后首轮扫描，之后每 60 秒一批，每批最多 100 场。 */
    @Scheduled(initialDelayString = "${knowflow.exam.timeout-scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${knowflow.exam.timeout-scheduler.delay-ms:60000}")
    public void autoSubmitExpiredAttempts() {
        int done = examService.autoSubmitExpiredAttempts(100);
        if (done > 0) {
            log.info("auto-submitted {} expired exam attempt(s)", done);
        }
    }
}
