package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.MemorialDTO;
import com.knowflow.entity.CalendarMemorial;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CalendarMemorialMapper;
import com.knowflow.service.CalendarMarkService;
import com.knowflow.util.LunarCalendar;
import com.knowflow.vo.DateMarkVO;
import com.knowflow.vo.MemorialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 日历日期标记实现。
 *
 * <p>数据来源与算法说明：
 * <ul>
 *   <li><b>法定节假日（休/班）</b>：内置《国务院办公厅关于 2025/2026 年部分节假日安排的通知》
 *       官方安排（中国政府网 www.gov.cn 发布），覆盖放假区间与调休上班日；未覆盖年份按
 *       「周末休息、工作日上班」默认规则兜底。每年国务院通知发布后，在 {@link #HOLIDAY_RULES}
 *       中追加对应年份规则即可。</li>
 *   <li><b>中国传统节日</b>：由 {@link LunarCalendar} 查表法（1900-2100 香港天文台推算数据）
 *       将公历换算为农历，再匹配春节/端午/中秋/重阳等农历月日；清明/冬至两个节气按通式近似计算。</li>
 *   <li><b>现代节日</b>：固定公历日期（如 9 月 10 日教师节）或按周计算（如 5 月第 2 个周日母亲节）。</li>
 *   <li><b>纪念日</b>：用户自定义，fixed（固定日期）/ yearly（每年重复），按区间展开。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarMarkServiceImpl implements CalendarMarkService {

    private final CalendarMemorialMapper memorialMapper;

    // =====================================================================
    // 主题色（前端浅色主题下使用；与前端 api/calendar.ts 的常量保持一致）
    // =====================================================================
    private static final String COLOR_HOLIDAY_REST = "#E5484D"; // 休（红）
    private static final String COLOR_HOLIDAY_WORK = "#F59E0B"; // 班（橙）
    private static final String COLOR_LUNAR = "#E11D48";        // 传统节日（玫红）
    private static final String COLOR_MODERN = "#0EA5E9";       // 现代节日（蓝）
    private static final String COLOR_MEMORIAL = "#8B5CF6";     // 纪念日（紫）

    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$");

    // =====================================================================
    // 法定节假日规则（国务院办公厅历年安排；未列年份回退周末规则）
    // =====================================================================

    /** 一条放假/调休规则：rest=true 放假（subLabel=休），rest=false 调休上班（subLabel=班）。 */
    private record HolidayRule(LocalDate start, LocalDate end, boolean rest, String name) {
    }

    private static final Map<Integer, List<HolidayRule>> HOLIDAY_RULES = new HashMap<>();

    static {
        // ---- 2025 年：《国务院办公厅关于2025年部分节假日安排的通知》（国办发明电〔2024〕…，中国政府网 2024-11-12 发布）
        rules(2025,
                rest("2025-01-01", "元旦"),
                rest("2025-01-28", "2025-02-04", "春节"), work("2025-01-26"), work("2025-02-08"),
                rest("2025-04-04", "2025-04-06", "清明节"),
                rest("2025-05-01", "2025-05-05", "劳动节"), work("2025-04-27"),
                rest("2025-05-31", "2025-06-02", "端午节"),
                rest("2025-10-01", "2025-10-08", "国庆节·中秋节"), work("2025-09-28"), work("2025-10-11"));
        // ---- 2026 年：《国务院办公厅关于2026年部分节假日安排的通知》（中国政府网 2025-11 发布）
        rules(2026,
                rest("2026-01-01", "2026-01-03", "元旦"), work("2026-01-04"),
                rest("2026-02-15", "2026-02-23", "春节"), work("2026-02-14"), work("2026-02-28"),
                rest("2026-04-04", "2026-04-06", "清明节"),
                rest("2026-05-01", "2026-05-05", "劳动节"), work("2026-05-09"),
                rest("2026-06-19", "2026-06-21", "端午节"),
                rest("2026-09-25", "2026-09-27", "中秋节"),
                rest("2026-10-01", "2026-10-07", "国庆节"), work("2026-09-20"), work("2026-10-10"));
    }

    private static void rules(int year, HolidayRule... items) {
        HOLIDAY_RULES.put(year, List.of(items));
    }

    private static HolidayRule rest(String date, String name) {
        LocalDate d = LocalDate.parse(date);
        return new HolidayRule(d, d, true, name);
    }

    private static HolidayRule rest(String start, String end, String name) {
        return new HolidayRule(LocalDate.parse(start), LocalDate.parse(end), true, name);
    }

    private static HolidayRule work(String date) {
        LocalDate d = LocalDate.parse(date);
        return new HolidayRule(d, d, false, "调休上班");
    }

    // =====================================================================
    // 现代节日（公历固定 / 按周计算）
    // =====================================================================

    private record ModernFestival(int month, int day, String name, DayOfWeek nthDow, int nth) {
        /** 固定公历日期 */
        static ModernFestival fixed(int month, int day, String name) {
            return new ModernFestival(month, day, name, null, 0);
        }

        /** 某月第 n 个星期几 */
        static ModernFestival nth(int month, int nth, DayOfWeek dow, String name) {
            return new ModernFestival(month, 0, name, dow, nth);
        }
    }

    private static final List<ModernFestival> MODERN_FESTIVALS = List.of(
            ModernFestival.fixed(1, 1, "元旦"),
            ModernFestival.fixed(2, 14, "情人节"),
            ModernFestival.fixed(3, 8, "妇女节"),
            ModernFestival.fixed(3, 12, "植树节"),
            ModernFestival.fixed(4, 1, "愚人节"),
            ModernFestival.fixed(5, 1, "劳动节"),
            ModernFestival.fixed(5, 4, "青年节"),
            ModernFestival.nth(5, 2, DayOfWeek.SUNDAY, "母亲节"),
            ModernFestival.fixed(6, 1, "儿童节"),
            ModernFestival.nth(6, 3, DayOfWeek.SUNDAY, "父亲节"),
            ModernFestival.fixed(7, 1, "建党节"),
            ModernFestival.fixed(8, 1, "建军节"),
            ModernFestival.fixed(9, 10, "教师节"),
            ModernFestival.fixed(10, 1, "国庆节"),
            ModernFestival.fixed(10, 31, "万圣节"),
            ModernFestival.nth(11, 4, DayOfWeek.THURSDAY, "感恩节"),
            ModernFestival.fixed(12, 24, "平安夜"),
            ModernFestival.fixed(12, 25, "圣诞节"));

    // =====================================================================
    // marks 区间查询
    // =====================================================================

    @Override
    public List<DateMarkVO> listMarks(Long userId, LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessException("区间参数不合法：需 start <= end");
        }
        if (ChronoUnit.DAYS.between(start, end) > 730) {
            throw new BusinessException("查询区间过大（最长支持 2 年）");
        }

        List<DateMarkVO> marks = new ArrayList<>();
        List<CalendarMemorial> memorials = memorialMapper.selectList(
                new LambdaQueryWrapper<CalendarMemorial>()
                        .eq(CalendarMemorial::getUserId, userId)
                        .orderByAsc(CalendarMemorial::getMonthDay));

        List<HolidayRule> rules = HOLIDAY_RULES.getOrDefault(start.getYear(), List.of());
        // 跨年区间：同时并入 end 年份的规则
        if (end.getYear() != start.getYear()) {
            List<HolidayRule> endRules = HOLIDAY_RULES.getOrDefault(end.getYear(), List.of());
            if (!endRules.isEmpty()) {
                List<HolidayRule> merged = new ArrayList<>(rules);
                merged.addAll(endRules);
                rules = merged;
            }
        }

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            buildHolidayMark(marks, cursor, rules);
            buildLunarMark(marks, cursor);
            buildModernMark(marks, cursor);
            buildMemorialMarks(marks, cursor, memorials);
            cursor = cursor.plusDays(1);
        }

        marks.sort(Comparator
                .comparing(DateMarkVO::getDate)
                .thenComparing(DateMarkVO::getType)
                .thenComparing(DateMarkVO::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return marks;
    }

    /** 法定节假日：命中规则 → 休/班；未命中 → 周末默认休，工作日不生成标记。 */
    private void buildHolidayMark(List<DateMarkVO> marks, LocalDate day, List<HolidayRule> rules) {
        for (HolidayRule r : rules) {
            if (!day.isBefore(r.start()) && !day.isAfter(r.end())) {
                marks.add(mark("holiday", day, r.name(), r.rest() ? "休" : "班",
                        r.rest() ? COLOR_HOLIDAY_REST : COLOR_HOLIDAY_WORK));
                return;
            }
        }
        DayOfWeek dow = day.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            marks.add(mark("holiday", day, "周末", "休", COLOR_HOLIDAY_REST));
        }
    }

    /** 中国传统节日：公历 → 农历 → 匹配农历月日节日；清明/冬至按节气近似。 */
    private void buildLunarMark(List<DateMarkVO> marks, LocalDate day) {
        LunarCalendar.LunarDate lunar = LunarCalendar.solarToLunar(day);
        if (lunar == null) {
            return;
        }
        String festival = LunarCalendar.traditionalFestival(lunar.month, lunar.leap, lunar.day);
        if (festival != null) {
            marks.add(mark("lunar", day, festival, lunar.monthDayText(), COLOR_LUNAR));
            return;
        }
        // 节气：清明（4月4-6日）、冬至（12月21-23日）按通式计算（1900-2100 适用）
        int y = day.getYear();
        if (day.getMonthValue() == 4 && day.getDayOfMonth() == solarTermDay(y, 4.81)) {
            marks.add(mark("lunar", day, "清明节", "清明", COLOR_LUNAR));
        } else if (day.getMonthValue() == 12 && day.getDayOfMonth() == solarTermDay(y, 21.94)) {
            marks.add(mark("lunar", day, "冬至", "冬至", COLOR_LUNAR));
        }
    }

    /**
     * 节气近似日（日序公式 d = [Y*0.2422 + C] - [Y/4]，C 为世纪常数，1900-2099 适用）。
     * 清明 C=4.81；冬至 C=21.94。
     */
    private static int solarTermDay(int year, double c) {
        int y = year % 100;
        return (int) (y * 0.2422 + c) - (int) (y / 4.0);
    }

    /** 现代节日：固定公历日期 + 第 N 个星期几。 */
    private void buildModernMark(List<DateMarkVO> marks, LocalDate day) {
        for (ModernFestival f : MODERN_FESTIVALS) {
            if (f.nthDow() == null) {
                if (day.getMonthValue() == f.month() && day.getDayOfMonth() == f.day()) {
                    marks.add(mark("modern", day, f.name(), null, COLOR_MODERN));
                    return;
                }
            } else {
                LocalDate target = nthDayOfMonth(day.getYear(), f.month(), f.nth(), f.nthDow());
                if (target != null && target.equals(day)) {
                    marks.add(mark("modern", day, f.name(), null, COLOR_MODERN));
                    return;
                }
            }
        }
    }

    /** 纪念日展开：fixed 按完整日期，yearly 按 MM-dd 每年出现。 */
    private void buildMemorialMarks(List<DateMarkVO> marks, LocalDate day, List<CalendarMemorial> memorials) {
        for (CalendarMemorial m : memorials) {
            if ("fixed".equals(m.getType())) {
                if (m.getFixedDate() != null && m.getFixedDate().equals(day)) {
                    marks.add(mark("memorial", day, m.getName(), "纪念日",
                            StringUtils.hasText(m.getColor()) ? m.getColor() : COLOR_MEMORIAL, m.getId()));
                }
            } else if ("yearly".equals(m.getType())) {
                if (matchesMonthDay(day, m.getMonthDay())) {
                    marks.add(mark("memorial", day, m.getName(), "纪念日",
                            StringUtils.hasText(m.getColor()) ? m.getColor() : COLOR_MEMORIAL, m.getId()));
                }
            }
        }
    }

    private static boolean matchesMonthDay(LocalDate day, String monthDay) {
        if (!StringUtils.hasText(monthDay)) {
            return false;
        }
        String[] parts = monthDay.split("-");
        if (parts.length != 2) {
            return false;
        }
        try {
            int m = Integer.parseInt(parts[0]);
            int d = Integer.parseInt(parts[1]);
            return day.getMonthValue() == m && day.getDayOfMonth() == d;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 某年某月第 n 个星期几（不存在返回 null，如 2 月第 5 个周一）。 */
    private static LocalDate nthDayOfMonth(int year, int month, int nth, DayOfWeek dow) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate first = ym.atDay(1);
        int firstDow = first.getDayOfWeek().getValue();
        int targetDow = dow.getValue();
        int offset = (targetDow - firstDow + 7) % 7;
        int dayOfMonth = 1 + offset + (nth - 1) * 7;
        if (dayOfMonth > ym.lengthOfMonth()) {
            return null;
        }
        return ym.atDay(dayOfMonth);
    }

    // =====================================================================
    // 纪念日 CRUD
    // =====================================================================

    @Override
    public List<MemorialVO> listMemorials(Long userId) {
        return memorialMapper.selectList(new LambdaQueryWrapper<CalendarMemorial>()
                        .eq(CalendarMemorial::getUserId, userId)
                        .orderByDesc(CalendarMemorial::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMemorial(MemorialDTO dto, Long userId) {
        CalendarMemorial e = new CalendarMemorial();
        e.setUserId(userId);
        applyDto(e, dto);
        memorialMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemorial(Long id, MemorialDTO dto, Long userId) {
        CalendarMemorial e = requireOwn(memorialMapper.selectById(id), userId);
        applyDto(e, dto);
        memorialMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMemorial(Long id, Long userId) {
        CalendarMemorial e = requireOwn(memorialMapper.selectById(id), userId);
        memorialMapper.deleteById(e.getId());
    }

    /** DTO → 实体（含字段校验与 month_day 冗余派生）。 */
    private void applyDto(CalendarMemorial e, MemorialDTO dto) {
        String name = dto.getName();
        if (!StringUtils.hasText(name) || name.trim().length() > 100) {
            throw new BusinessException("纪念日名称不能为空且不超过 100 字");
        }
        String type = dto.getType();
        if (!"fixed".equals(type) && !"yearly".equals(type)) {
            throw new BusinessException("纪念日类型仅支持 fixed / yearly");
        }
        e.setName(name.trim());
        e.setType(type);
        e.setColor(StringUtils.hasText(dto.getColor()) ? dto.getColor().trim() : null);
        e.setNote(StringUtils.hasText(dto.getNote()) ? dto.getNote().trim() : null);

        if ("fixed".equals(type)) {
            if (dto.getFixedDate() == null) {
                throw new BusinessException("固定纪念日需填写日期");
            }
            e.setFixedDate(dto.getFixedDate());
            e.setMonthDay(String.format("%02d-%02d", dto.getFixedDate().getMonthValue(), dto.getFixedDate().getDayOfMonth()));
        } else {
            String md = dto.getMonthDay();
            if (!StringUtils.hasText(md) || !MONTH_DAY_PATTERN.matcher(md.trim()).matches()) {
                throw new BusinessException("每年重复纪念日需填写 MM-dd 格式日期");
            }
            e.setMonthDay(md.trim());
            e.setFixedDate(null);
        }
    }

    private MemorialVO toVO(CalendarMemorial e) {
        MemorialVO vo = new MemorialVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setType(e.getType());
        vo.setMonthDay(e.getMonthDay());
        vo.setFixedDate(e.getFixedDate());
        vo.setColor(e.getColor());
        vo.setNote(e.getNote());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private CalendarMemorial requireOwn(CalendarMemorial e, Long userId) {
        if (e == null) {
            throw new BusinessException("纪念日不存在");
        }
        if (!Objects.equals(e.getUserId(), userId)) {
            throw new BusinessException("无权操作该记录");
        }
        return e;
    }

    private static DateMarkVO mark(String type, LocalDate date, String name, String subLabel, String color) {
        return mark(type, date, name, subLabel, color, null);
    }

    private static DateMarkVO mark(String type, LocalDate date, String name, String subLabel, String color, Long memorialId) {
        DateMarkVO vo = new DateMarkVO();
        vo.setType(type);
        vo.setDate(date);
        vo.setName(name);
        vo.setSubLabel(subLabel);
        vo.setColor(color);
        vo.setMemorialId(memorialId);
        return vo;
    }
}
