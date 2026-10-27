// Capability gaps documented in the playground; production components remain untouched.
export const capabilityNotes:Record<string,string[]>={
 Input:['前置/后置图标可用；suffixIconName不是后置按钮。','Clearable与后置操作按钮：Not Supported（真实Input没有这些Props）。','readonly为原生属性透传，不等同disabled。'],
 DatePicker:['选中日期Fixture固定2026-10-15，min/max固定十月；今天标记仍由真实组件按本机日期计算，无法通过公开Prop冻结。','禁用范围通过min/max；任意禁用日期函数：Not Supported。'],
 Badge:['支持真实variant与默认插槽；数量可由插槽文案表达。','独立dot/size/count参数：Not Supported。'],
 Pagination:['pageNum/pageSize/total与change可用；边界自动禁用上一页/下一页。','外部disabled参数：Not Supported。'],
 Drawer:['复用真实AppDialog placement=right；Left：Not Supported。'],
 Combobox:['Demo-only参考，支持本地建议/键盘/清空；远程异步服务：Not Supported。'],
 Skeleton:['复用SkeletonList的list/card/line；table类型：Not Supported。'],
 Dialog:['复用AppDialog；表单、错误、loading和长内容属于本地预览。','危险确认沿用真实variant，不执行删除。'],
 Toast:['复用真实ToastHost和notify/dismiss，自己的通知ID及计时器卸载清理。','不会移除展示页之外创建的通知。'],
}
