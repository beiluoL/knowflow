<template>
  <Card>
    <template #header>
      <h2 class="font-semibold">持久化 AI 账单</h2>
    </template>
    <StateView :status="loading ? 'loading' : failed ? 'error' : 'ready'"
      error-title="账单读取失败" @retry="load(currentPage)">
      <p v-if="!billing?.records.length" class="text-sm">暂无账单记录。</p>
      <div v-else class="space-y-3">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <caption class="sr-only">按 UTC 日期记录的 AI 调用账单</caption>
            <thead>
              <tr>
                <th scope="col">日期（UTC）</th>
                <th scope="col">用户</th>
                <th scope="col">模型 / 场景</th>
                <th scope="col">状态</th>
                <th scope="col">预留 Token</th>
                <th scope="col">输入 / 输出 Token</th>
                <th scope="col">成本（USD）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in billing.records" :key="entry.id">
                <td>{{ entry.day }}</td>
                <td>{{ entry.userId ?? '系统' }}</td>
                <td>{{ entry.modelScope }} / {{ entry.scene }}</td>
                <td>{{ entry.status }}</td>
                <td>{{ entry.reservedTokens }}</td>
                <td>{{ entry.inputTokens ?? '未知' }} / {{ entry.outputTokens ?? '未知' }}</td>
                <td>{{ entry.costUsd ?? '未知' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav aria-label="账单分页" class="flex items-center gap-3">
          <Button :disabled="currentPage <= 1" @click="load(currentPage - 1)">上一页</Button>
          <span>第 {{ currentPage }} / {{ billing.pages }} 页</span>
          <Button :disabled="currentPage >= billing.pages" @click="load(currentPage + 1)">下一页</Button>
        </nav>
      </div>
    </StateView>
  </Card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { aiConfigApi, type AiBillingPage } from '@/api/aiConfig'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import StateView from '@/components/ui/StateView.vue'

const billing = ref<AiBillingPage | null>(null)
const currentPage = ref(1)
const loading = ref(true)
const failed = ref(false)

async function load(page: number) {
  currentPage.value = page
  loading.value = true
  failed.value = false
  try {
    billing.value = await aiConfigApi.billing(page)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => { void load(1) })
</script>
