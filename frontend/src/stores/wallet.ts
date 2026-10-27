/**
 * Architecture-2：钱包状态（§103-105）。
 * 只做缓存/状态/展示——XP/Coin 的真实来源永远是后端（§104）；前端禁止 coin+= / xp+=（§102/§176/§178）。
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { walletApi } from '@/api/wallet'
import { useAuthStore } from './auth'
import type { WalletVO, LedgerVO } from '@/economy/types'

export const useWalletStore = defineStore('wallet', () => {
  const auth = useAuthStore()
  const wallet = ref<WalletVO | null>(null)
  const ledger = ref<LedgerVO[]>([])
  const loading = ref(false)
  const ledgerLoading = ref(false)

  async function fetchWallet(force = false): Promise<void> {
    if (!auth.isLoggedIn) return
    if (!force && wallet.value) return
    loading.value = true
    try {
      wallet.value = await walletApi.getWallet()
    } catch {
      // 静默：经济层为辅助展示，失败不影响主流程（§150-152）
    } finally {
      loading.value = false
    }
  }

  async function fetchLedger(currency?: string): Promise<void> {
    if (!auth.isLoggedIn) return
    ledgerLoading.value = true
    try {
      ledger.value = await walletApi.getLedger({ page: 1, size: 20, currency })
    } catch {
      ledger.value = []
    } finally {
      ledgerLoading.value = false
    }
  }

  /** 购买成功后刷新真实余额（§175/§177） */
  function refresh(): void {
    void fetchWallet(true)
  }

  // 登出/切换账号 → 清空，防止串号（§291-292）
  watch(
    () => auth.user?.id,
    () => {
      wallet.value = null
      ledger.value = []
    }
  )

  return { wallet, ledger, loading, ledgerLoading, fetchWallet, fetchLedger, refresh }
})
