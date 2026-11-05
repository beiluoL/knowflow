<template>
  <Card v-if="auth.isAdmin">
    <template #header>
      <h2 class="font-semibold">RAG 离线评测</h2>
    </template>
    <div class="space-y-3 text-sm">
      <p class="text-muted-foreground">在固定公开候选上比较重排前后。结果仅代表此样例集。</p>
      <Button :disabled="loading" @click="run">运行评测</Button>
      <StateView :status="loading ? 'loading' : failed ? 'error' : 'ready'"
        error-title="评测运行失败" @retry="run">
        <div v-if="report" class="space-y-2 overflow-x-auto">
          <p>数据集 {{ report.datasetVersion }} · K={{ report.k }}</p>
          <table class="w-full text-left">
            <caption class="sr-only">重排前后评测指标</caption>
            <thead>
              <tr>
                <th scope="col">案例</th>
                <th scope="col">Recall 前 / 后</th>
                <th scope="col">MRR 前 / 后</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in report.cases" :key="entry.id">
                <td>{{ entry.id }}</td>
                <td>{{ entry.baseline.recall.toFixed(2) }} / {{ entry.reranked.recall.toFixed(2) }}</td>
                <td>{{ entry.baseline.reciprocalRank.toFixed(2) }} / {{ entry.reranked.reciprocalRank.toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else>尚未运行评测。</p>
      </StateView>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { apiGet } from '@/api/request'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAuthStore } from '@/stores/auth'

interface Metrics {
  recall: number
  reciprocalRank: number
}

interface Report {
  datasetVersion: string
  k: number
  cases: Array<{ id: string; baseline: Metrics; reranked: Metrics }>
}

const auth = useAuthStore()
const report = ref<Report | null>(null)
const loading = ref(false)
const failed = ref(false)

async function run() {
  if (loading.value) return
  loading.value = true
  failed.value = false
  try {
    report.value = await apiGet<Report>('/admin/rag/evaluation')
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
</script>
