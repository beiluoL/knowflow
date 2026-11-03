import { apiDelete, apiGet, apiPost, apiPut } from '@/api/request'
import type { ResumeDesign, ResumeTemplateMeta, ResumeTheme } from './types'

/** 模板目录由后端白名单返回，保证前端渲染 key 与持久化校验一致。 */
export const listTemplates = () => apiGet<ResumeTemplateMeta[]>('/interview/career/templates')

export const listDesigns = (resumeId: number) =>
  apiGet<ResumeDesign[]>(`/interview/career/records/${resumeId}/designs`)

export const createDesign = (resumeId: number, theme: ResumeTheme, name?: string) =>
  apiPost<ResumeDesign>(`/interview/career/records/${resumeId}/designs`, { name, theme })

export const updateDesign = (resumeId: number, designId: number, theme: ResumeTheme, name?: string) =>
  apiPut<ResumeDesign>(`/interview/career/records/${resumeId}/designs/${designId}`, { name, theme })

export const setDefaultDesign = (resumeId: number, designId: number) =>
  apiPost<void>(`/interview/career/records/${resumeId}/designs/${designId}/default`, {})

export const deleteDesign = (resumeId: number, designId: number) =>
  apiDelete<void>(`/interview/career/records/${resumeId}/designs/${designId}`)
