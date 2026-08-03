// 用户自定义背景预设请求层：封装预设查询/保存/删除接口调用。
import { apiGet, apiPost, apiDelete } from './request'

/** 用户自定义背景预设 */
export interface UserBackgroundPresetVO {
  id: number
  userId: number
  name: string
  bgType: string
  bgValue: string
  thumbnail: string
  createTime: string
  updateTime: string
}

/** 保存预设的请求体 */
export interface PresetSaveDTO {
  name: string
  bgType: string
  bgValue: string
  thumbnail: string
}

/** 获取当前用户的所有自定义预设 */
export function listPresets() {
  return apiGet<UserBackgroundPresetVO[]>('/settings/background-presets')
}

/** 保存（新增或更新同名）自定义预设 */
export function savePreset(dto: PresetSaveDTO) {
  return apiPost<UserBackgroundPresetVO>('/settings/background-presets', dto)
}

/** 删除自定义预设 */
export function deletePreset(id: number) {
  return apiDelete<void>(`/settings/background-presets/${id}`, undefined)
}
