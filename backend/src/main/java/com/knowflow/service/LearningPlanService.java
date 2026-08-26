package com.knowflow.service;

import com.knowflow.dto.PlanGenerateDTO;
import com.knowflow.vo.LearningPlanVO;

import java.time.LocalDate;

/**
 * 学习计划编排服务（F3 学习计划智能编排）。
 * <p>
 * 负责：
 * 1. 生成单日 / 周级计划（AI 编排 → 兜底 → 入库）；
 * 2. 懒加载「今日计划」（无计划时当日自动生成一次）；
 * 3. 实时回填完成状态；
 * 4. 导出 RFC 5545 ICS 日历文本。
 */
public interface LearningPlanService {

    /**
     * 获取今日计划。
     * <p>
     * 若当日尚无记录 → 同步调用 generateForRange(days=1) 生成一次再返回，
     * 保证首次进入 /plan/today 页面一定有数据（即使三类候选池都空，也会返回空 blocks）。
     *
     * @param userId 登录用户 ID（非空）
     * @return 今日计划 VO
     */
    LearningPlanVO getTodayPlan(Long userId);

    /**
     * 为 userId 生成 [startDate, startDate+days-1] 范围内的计划。
     * <p>
     * 默认幂等：同 userId + date 已有记录 → 跳过；force=true 先物理删除再重生成。
     *
     * @param userId    用户 ID（非空）
     * @param startDate 起始日期（null 时用下周周一或本周日后兼容）
     * @param days      天数（null → 7，上限 30）
     * @param force     true=强制覆盖；false=幂等跳过
     * @return 实际生成的天数（0 表示全部已存在）
     */
    int generateForRange(Long userId, LocalDate startDate, Integer days, Boolean force);

    /**
     * 基于 PlanGenerateDTO 生成计划（Controller 便利方法，内部委托 generateForRange）。
     *
     * @param dto    入参（可全空，默认下周一 + 7 天 + force=false）
     * @param userId 用户 ID
     * @return 实际生成天数
     */
    default int generate(PlanGenerateDTO dto, Long userId) {
        LocalDate s = (dto == null || dto.getStartDate() == null) ? defaultStartDate() : dto.getStartDate();
        Integer d = (dto == null || dto.getDays() == null) ? 7 : dto.getDays();
        Boolean f = (dto == null || dto.getForce() == null) ? Boolean.FALSE : dto.getForce();
        return generateForRange(userId, s, d, f);
    }

    /**
     * 导出指定日期范围的计划为 RFC 5545 ICS 文本。
     *
     * @param userId    用户 ID
     * @param baseDate  基准日期（null 时=今日）
     * @param rangeDays 范围天数（null → 1，上限 30，超出强制裁剪）
     * @return ICS 文件内容字符串（UTF-8）
     */
    String exportCalendarIcs(Long userId, LocalDate baseDate, Integer rangeDays);

    /** 辅助：计算「默认起始日」——下周一。 */
    static LocalDate defaultStartDate() {
        LocalDate today = LocalDate.now();
        int dayOfWeek = today.getDayOfWeek().getValue(); // 1 Mon ... 7 Sun
        int diff;
        if (dayOfWeek == 7) {
            diff = 1; // 周日 → 明天（周一）
        } else {
            diff = 8 - dayOfWeek;
        }
        return today.plusDays(diff);
    }
}
