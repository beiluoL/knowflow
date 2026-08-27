package com.knowflow.util;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 农历（阴历）换算工具类：公历 ↔ 农历，采用「查表法」（1900-2100 年）。
 *
 * <p>数据表 lunarInfo 编码 1900-2100 每一年的农历信息（来源：香港天文台推算数据，
 * 与 lunar-javascript / sxtwl 等主流农历库一致），算法为公开的「寿星天文历」简化版：
 * <ul>
 *   <li>lunarInfo[y] 的 4 个 bit（0x0F00）表示该年闰月月份（0 表示无闰月）；</li>
 *   <li>其余 12 个 bit（0x0FFF）从低位到高位分别表示正月 ~ 十二月是 29 天（小月）还是 30 天（大月）；</li>
 *   <li>0x8000 bit 表示该年闰月是大月（30 天）还是小月（29 天）。</li>
 * </ul>
 *
 * <p>支持范围：公历 1900-01-31 至 2100-12-31（农历庚子年正月初一至）。范围外返回 null。
 */
public final class LunarCalendar {

    private LunarCalendar() {
    }

    // =====================================================================
    // 1900-2100 农历年信息表（每项 16bit，见类注释）
    // =====================================================================
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520, // 2100
    };

    /** 1900-01-31（农历庚子年正月初一）对应的公历天数偏移基准 */
    private static final long BASE_SOLAR_EPOCH = LocalDate.of(1900, 1, 31).toEpochDay();

    private static final String[] CN_NUM = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final String[] CN_MONTH = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};

    /** 农历日期结果对象。 */
    public static final class LunarDate {
        /** 农历年（如 2026）。 */
        public final int year;
        /** 农历月（1-12，正月=1）。 */
        public final int month;
        /** 是否闰月。 */
        public final boolean leap;
        /** 农历日（1-30）。 */
        public final int day;
        /** 农历年天干地支纪年，如「丙午」。 */
        public final String ganzhiYear;
        /** 生肖，如「马」。 */
        public final String zodiac;

        LunarDate(int year, int month, boolean leap, int day) {
            this.year = year;
            this.month = month;
            this.leap = leap;
            this.day = day;
            this.ganzhiYear = calcGanzhiYear(year);
            this.zodiac = calcZodiac(year);
        }

        /** 农历月日文本，如「八月十五」「正月初一」「闰二月初十」。 */
        public String monthDayText() {
            return (leap ? "闰" : "") + CN_MONTH[month - 1] + "月" + dayText(day);
        }

        /** 农历日期完整文本，如「丙午马年 八月十五」。 */
        @Override
        public String toString() {
            return ganzhiYear + zodiac + "年 " + monthDayText();
        }
    }

    // =====================================================================
    // 基础运算
    // =====================================================================

    private static int lunarYearInfo(int year) {
        return LUNAR_INFO[year - 1900];
    }

    /** 该年闰月月份（1-12），0 表示无闰月。 */
    public static int leapMonth(int year) {
        return lunarYearInfo(year) & 0x0F;
    }

    /** 该年闰月天数（29/30），无闰月返回 0。 */
    public static int leapDays(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return (lunarYearInfo(year) & 0x10000) != 0 ? 30 : 29;
    }

    /** 该年农历某月（非闰月）天数（29/30）。 */
    public static int monthDays(int year, int month) {
        return (lunarYearInfo(year) & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    /** 该年农历总天数（含闰月）。 */
    public static int lunarYearDays(int year) {
        int sum = 348; // 12 * 29
        int info = lunarYearInfo(year);
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            sum += (info & i) != 0 ? 1 : 0;
        }
        return sum + leapDays(year);
    }

    /** 农历月日 → 公历（含闰月处理）。找不到返回 null。 */
    public static LocalDate lunarToSolar(int year, int month, int day, boolean leap) {
        if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 30) {
            return null;
        }
        int leapM = leapMonth(year);
        boolean hasLeap = leapM != 0;
        if (leap && (!hasLeap || month != leapM)) {
            return null; // 该年无此闰月
        }
        // 自 1900 年正月初一起累计：先加完整年份，再加当年月日
        int offset = 0;
        for (int y = 1900; y < year; y++) {
            offset += lunarYearDays(y);
        }
        for (int m = 1; m < month; m++) {
            offset += monthDays(year, m);
        }
        // 跨过闰月才补闰月天数；闰月本身（leap=true 且月号=闰月）需先经过同号普通月
        if (hasLeap && month > leapM) {
            offset += leapDays(year);
        }
        if (leap && month == leapM) {
            offset += monthDays(year, month);
        }
        offset += day - 1;
        return LocalDate.ofEpochDay(BASE_SOLAR_EPOCH + offset);
    }

    /**
     * 公历 → 农历。支持 1900-01-31 至 2100-12-31，范围外返回 null。
     */
    public static LunarDate solarToLunar(LocalDate solar) {
        long epoch = solar.toEpochDay();
        long offsetDays = epoch - BASE_SOLAR_EPOCH;
        if (offsetDays < 0 || offsetDays >= lunarTotalDays()) {
            return null;
        }
        // 定位农历年
        int year = 1900;
        int days = lunarYearDays(year);
        while (offsetDays >= days) {
            offsetDays -= days;
            year++;
            days = lunarYearDays(year);
        }
        // 定位农历月（考虑闰月）
        int leapM = leapMonth(year);
        int month = 1;
        boolean leap = false;
        while (true) {
            int dm = monthDays(year, month);
            if (offsetDays < dm) {
                break;
            }
            offsetDays -= dm;
            if (leapM == month && !leap) {
                // 进入闰月
                int ld = leapDays(year);
                if (offsetDays < ld) {
                    leap = true;
                    break;
                }
                offsetDays -= ld;
            }
            month++;
        }
        return new LunarDate(year, month, leap, (int) offsetDays + 1);
    }

    private static long lunarTotalDays() {
        long total = 0;
        for (int y = 1900; y <= 2100; y++) {
            total += lunarYearDays(y);
        }
        return total;
    }

    // =====================================================================
    // 干支纪年 / 生肖
    // =====================================================================

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ZODIAC = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};

    /** 干支纪年：1900 年为庚子年。 */
    public static String calcGanzhiYear(int year) {
        int idx = (year - 1900) % 60;
        return GAN[idx % 10] + ZHI[idx % 12];
    }

    /** 生肖：1900 年为鼠年。 */
    public static String calcZodiac(int year) {
        return ZODIAC[((year - 1900) % 12 + 12) % 12];
    }

    private static String dayText(int day) {
        if (day == 10) {
            return "初十";
        }
        if (day < 10) {
            return "初" + CN_NUM[day];
        }
        if (day == 20) {
            return "二十";
        }
        if (day < 20) {
            return "十" + CN_NUM[day - 10];
        }
        if (day == 30) {
            return "三十";
        }
        return "廿" + CN_NUM[day - 20];
    }

    // =====================================================================
    // 便捷：农历传统节日查询（按农历月日）
    // =====================================================================

    /** 农历月日 → 传统节日名（无则 null）。月为 0 时表示腊月（十二月）。 */
    public static String traditionalFestival(int month, boolean leap, int day) {
        if (leap) {
            return null;
        }
        if (month == 1 && day == 1) return "春节";
        if (month == 1 && day == 15) return "元宵节";
        if (month == 2 && day == 2) return "龙抬头";
        if (month == 5 && day == 5) return "端午节";
        if (month == 7 && day == 7) return "七夕";
        if (month == 7 && day == 15) return "中元节";
        if (month == 8 && day == 15) return "中秋节";
        if (month == 9 && day == 9) return "重阳节";
        if (month == 12 && day == 8) return "腊八节";
        if (month == 12 && day == 23) return "小年";
        if (month == 12 && day == 29) return "除夕";
        if (month == 12 && day == 30) return "除夕";
        return null;
    }

    /** 农历某年十二月初一之后（含）是否包含除夕（腊月廿九或三十）。 */
    public static boolean hasChuxi(int year) {
        return true; // 除夕为农历每年最后一天，必然存在
    }
}
