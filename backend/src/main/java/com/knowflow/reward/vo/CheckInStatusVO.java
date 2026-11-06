package com.knowflow.reward.vo;

import java.util.List;
import lombok.Data;

/**
 * 每日打卡状态 VO：GET /api/checkin/status 的响应，供前端渲染「今日是否已打卡、连续天数、本月日历」。
 *
 * <p>由 CheckInServiceImpl#getStatus 只读组装，不写库；自然日与自然月都以服务端 LocalDate.now() 的本地时区为准。
 *
 * <p>未打卡但昨日有记录时仍展示昨日的连续天数（streak 尚未断），因此 continuousDays 不一定代表今天已经打卡。
 */
@Data
public class CheckInStatusVO {

  /** 今日是否已有有效打卡行；查询经 BaseEntity 的 {@code @TableLogic} 自动过滤软删行。 */
  private Boolean checkedToday;

  /** 连续天数展示值：今日已打卡取今日行，否则取昨日行的值，两者都无为 0。 */
  private Integer continuousDays;

  /** 累计打卡天数，按 user_check_in 有效行数 COUNT，不含软删行。 */
  private Integer totalDays;

  /** 本月已打卡的日期号列表（如 [1,2,5]），去重升序；当月无记录返回空列表而不是 null。 */
  private List<Integer> monthCheckedDays;
}
