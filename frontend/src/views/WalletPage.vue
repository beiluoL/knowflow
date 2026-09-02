<template>
  <div class="wallet-page">
    <PageHeader
      title="钱包"
      description="你的学习成长与金币。XP 来自学习行为，Coin 可在应用中心兑换学习应用。"
      :crumbs="[{ label: '我的' }, { label: '钱包' }]"
    />
    <div class="wallet-grid">
      <WalletCard :wallet="store.wallet" />
      <div class="wallet-tip" :style="{ borderColor: 'var(--kb-border)' }">
        <Icon name="sparkles" :size="16" />
        <p>完成章节、答题、复习、打卡都能获得 XP 与 Coin。Coin 不可提现、不可转账，只能在学习平台内使用。</p>
      </div>
    </div>
    <section class="ledger-section">
      <div class="section-head">
        <h2 class="section-title">最近流水</h2>
        <button class="refresh-btn" @click="loadLedger" :disabled="store.ledgerLoading">刷新</button>
      </div>
      <LedgerList :items="store.ledger" :loading="store.ledgerLoading" />
    </section>
  </div>
</template>

<script setup lang="ts">
// Architecture-2：钱包页（/wallet，§97/§115：余额 + 等级 + XP + 流水，不做复杂财务系统）。
import { onMounted } from 'vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Icon from '@/components/ui/Icon.vue'
import WalletCard from '@/components/economy/WalletCard.vue'
import LedgerList from '@/components/economy/LedgerList.vue'
import { useWalletStore } from '@/stores/wallet'

const store = useWalletStore()

function loadLedger(): void {
  void store.fetchLedger()
}

onMounted(() => {
  void store.fetchWallet(true)
  loadLedger()
})
</script>

<style scoped>
.wallet-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.wallet-grid {
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: 14px;
}
.wallet-tip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  line-height: 1.6;
}
.wallet-tip p {
  margin: 0;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.refresh-btn {
  border: none;
  background: transparent;
  color: var(--kb-primary);
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
@media (max-width: 768px) {
  .wallet-grid {
    grid-template-columns: 1fr;
  }
}
</style>
