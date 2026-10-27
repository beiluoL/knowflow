/**
 * 日期时间组件的公共精度与范围类型。
 *
 * 显示层与数据层分离：类型描述的是**数据合同**，用户可见格式由
 * `date-time-format.ts` 的 format*ForDisplay 负责。
 */

/** 时间精度：分钟或秒。唯一来源是 time-picker-utils。 */
export type { TimePrecision } from './time-picker-utils'

/** 日期时间精度：纯日期、分钟或秒。 */
export type DateTimePrecision = 'date' | 'minute' | 'second'

/** 年份合同：YYYY。 */
export type YearValue = string

/** 年月合同：YYYY-MM。 */
export type YearMonthValue = string

/** 纯日期合同：YYYY-MM-DD。 */
export type DateValue = string

/** 纯时间合同：HH:mm 或 HH:mm:ss。 */
export type TimeValue = string

/** 本地日期时间合同：YYYY-MM-DDTHH:mm(:ss)。 */
export type LocalDateTimeValue = string

/** 通用范围形状；空字符串表示该端点未选择。 */
export interface RangeValue {
  start: string
  end: string
}

/** 日期范围：两个纯日期。 */
export type DateRangeValue = RangeValue

/** 时间范围：两个纯时间；跨午夜语义由调用方显式开启。 */
export type TimeRangeValue = RangeValue

/** 日期时间范围：两个本地日期时间。 */
export type DateTimeRangeValue = RangeValue

/** 统一的空范围常量，避免调用方各自复制。 */
export const EMPTY_RANGE: RangeValue = { start: '', end: '' }
