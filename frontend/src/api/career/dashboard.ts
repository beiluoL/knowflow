import { apiGet } from '@/api/request'
import type { CareerDashboard, CareerOverview } from './types'

export const getCareerDashboard = () => apiGet<CareerDashboard>('/interview/career/dashboard')

/** 求职总览：聚合读取，数字全部来自本人真实记录。 */
export const getCareerOverview = () => apiGet<CareerOverview>('/interview/career/overview')
