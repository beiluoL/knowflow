// 通知状态管理：维护通知列表、未读数与分页，提供标记已读等操作。
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { notificationsApi } from '@/api/notifications'
import type { NotificationVO } from '@/api/types'

interface FetchParams {
  type?: string
  pageNum?: number
  pageSize?: number
  isRead?: number
}

export const useNotificationStore = defineStore('notification', () => {
  const list = ref<NotificationVO[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const unreadCount = ref(0)
  const loaded = ref(false)

  const unreadList = computed(() => list.value.filter((n) => n.isRead === 0))

  async function fetchUnreadCount() {
    try {
      unreadCount.value = await notificationsApi.unreadCount()
    } catch {
      // 未读数获取失败静默处理，不影响主流程
    }
  }

  async function fetchList(params: FetchParams = {}, append = false) {
    const page = params.pageNum ?? pageNum.value
    const size = params.pageSize ?? pageSize.value
    try {
      const res = await notificationsApi.list({
        type: params.type,
        isRead: params.isRead,
        pageNum: page,
        pageSize: size,
      })
      // append=true 时累加到现有列表（用于「加载更多」），否则替换
      list.value = append ? [...list.value, ...res.records] : res.records
      total.value = res.total
      pageNum.value = page
      pageSize.value = size
      loaded.value = true
      await fetchUnreadCount()
    } catch (e) {
      // 错误向上抛由调用方决定如何提示用户
      throw e
    }
  }

  async function markAsRead(id: number) {
    try {
      await notificationsApi.markAsRead(id)
      const target = list.value.find((n) => n.id === id)
      if (target) {
        target.isRead = 1
      }
      if (unreadCount.value > 0) {
        unreadCount.value--
      }
    } catch (e) {
      // 错误向上抛由调用方决定如何提示用户
      throw e
    }
  }

  async function markAllAsRead() {
    try {
      await notificationsApi.markAllAsRead()
      list.value = list.value.map((n) => ({ ...n, isRead: 1 }))
      unreadCount.value = 0
    } catch (e) {
      // 错误向上抛由调用方决定如何提示用户
      throw e
    }
  }

  function reset() {
    list.value = []
    total.value = 0
    unreadCount.value = 0
    loaded.value = false
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    unreadCount,
    unreadList,
    loaded,
    fetchUnreadCount,
    fetchList,
    markAsRead,
    markAllAsRead,
    reset,
  }
})
