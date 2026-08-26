<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--kb-foreground);">AI 设置</h1>
        <p class="text-sm mt-1" style="color: var(--kb-muted-foreground);">配置大模型 API，使用自己的 Key 或平台提供的模型</p>
      </div>
      <div v-if="userConfig" class="flex items-center gap-2">
        <span class="px-3 py-1 rounded-full text-xs font-medium" style="background: rgba(59,111,224,0.1); color: var(--kb-primary);">
          已启用自定义配置
        </span>
      </div>
    </div>

    <!-- Section 1: 自定义 AI 配置 -->
    <Card>
      <template #header>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
            <Icon name="key" :size="20" style="color: var(--kb-primary);" />
          </div>
          <div>
            <h3 class="font-semibold" style="color: var(--kb-foreground);">自定义 AI 模型</h3>
            <p class="text-xs" style="color: var(--kb-muted-foreground);">输入自己的 API Key，直接调用大模型服务</p>
          </div>
        </div>
      </template>
      <div class="space-y-6">
        <!-- 选择提供商 -->
        <div>
          <label class="block text-sm font-medium mb-3" style="color: var(--kb-foreground);">选择模型提供商</label>
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            <button
              v-for="p in platformModels"
              :key="p.provider"
              type="button"
              class="provider-btn p-3 rounded-lg border text-center transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 relative"
              :style="{
                borderColor: form.provider === p.provider ? 'var(--kb-primary)' : 'var(--kb-border)',
                background: form.provider === p.provider ? 'rgba(59,111,224,0.06)' : 'var(--kb-card)',
              }"
              @click="selectProvider(p.provider)"
            >
              <div class="text-sm font-medium" :style="{ color: form.provider === p.provider ? 'var(--kb-primary)' : 'var(--kb-foreground)' }">{{ p.label }}</div>
              <div v-if="p.providerType === 'LOCAL'" class="text-xs mt-0.5" style="color: var(--kb-muted-foreground);">本地</div>
            </button>
          </div>
        </div>

        <!-- 当前选中提供商信息栏 -->
        <div v-if="currentProviderInfo" class="p-4 rounded-lg border" style="border-color: var(--kb-border); background: var(--kb-muted);">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-4">
              <div>
                <span class="text-sm font-medium" style="color: var(--kb-foreground);">{{ currentProviderInfo.label }}</span>
                <span v-if="currentProviderInfo.priceInfo" class="ml-2 text-xs" style="color: var(--kb-muted-foreground);">{{ currentProviderInfo.priceInfo }}</span>
              </div>
              <a
                v-if="currentProviderInfo.websiteUrl"
                :href="currentProviderInfo.websiteUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="inline-flex items-center gap-1 text-xs font-medium transition-colors hover:opacity-80"
                style="color: var(--kb-primary);"
              >
                <Icon name="external-link" :size="14" />
                访问官网
              </a>
            </div>
            <button
              v-if="currentProviderInfo.keyGuide && currentProviderInfo.keyGuide.length > 0"
              type="button"
              class="inline-flex items-center gap-1 text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors hover:bg-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="border-color: var(--kb-border); color: var(--kb-primary);"
              @click="showKeyGuide = !showKeyGuide"
            >
              <Icon name="help-circle" :size="14" />
              {{ showKeyGuide ? '收起引导' : '如何获取 API Key？' }}
            </button>
          </div>

          <!-- 推荐模型快速选择 -->
          <div v-if="currentProviderInfo.popularModels && currentProviderInfo.popularModels.length > 0" class="mt-3 flex items-center gap-2 flex-wrap">
            <span class="text-xs" style="color: var(--kb-muted-foreground);">推荐模型：</span>
            <button
              v-for="m in currentProviderInfo.popularModels"
              :key="m"
              type="button"
              class="px-2 py-0.5 rounded text-xs border transition-colors hover:opacity-80"
              :style="{
                borderColor: form.model === m ? 'var(--kb-primary)' : 'var(--kb-border)',
                color: form.model === m ? 'var(--kb-primary)' : 'var(--kb-foreground)',
                background: form.model === m ? 'rgba(59,111,224,0.06)' : 'transparent',
              }"
              @click="form.model = m"
            >{{ m }}</button>
          </div>
        </div>

        <!-- API Key 获取引导 -->
        <div v-if="showKeyGuide && currentProviderInfo?.keyGuide" class="p-4 rounded-lg border" style="border-color: var(--kb-primary); background: rgba(59,111,224,0.04);">
          <div class="flex items-center gap-2 mb-3">
            <Icon name="book-open" :size="16" style="color: var(--kb-primary);" />
            <span class="text-sm font-medium" style="color: var(--kb-primary);">API Key 获取步骤</span>
          </div>
          <ol class="space-y-2">
            <li v-for="(step, idx) in currentProviderInfo.keyGuide" :key="idx" class="flex items-start gap-3">
              <span class="flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold" style="background: var(--kb-primary); color: var(--kb-primary-foreground);">{{ idx + 1 }}</span>
              <span class="text-sm pt-0.5" style="color: var(--kb-foreground);">{{ step }}</span>
            </li>
          </ol>
          <a
            v-if="currentProviderInfo.websiteUrl"
            :href="currentProviderInfo.websiteUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1 mt-3 text-xs font-medium hover:opacity-80"
            style="color: var(--kb-primary);"
          >
            <Icon name="external-link" :size="14" />
            前往 {{ currentProviderInfo.label }} 官网注册 →
          </a>
        </div>

        <!-- 表单网格：API Key + Base URL + Model -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <!-- API Key -->
          <div class="md:col-span-1">
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">
              API Key
              <span v-if="userConfig?.apiKeyMasked" class="ml-2 text-xs" style="color: var(--kb-muted-foreground);">当前: {{ userConfig.apiKeyMasked }}</span>
            </label>
            <div class="relative">
              <input
                v-model="form.apiKey"
                :type="showKey ? 'text' : 'password'"
                placeholder="输入你的 API Key"
                class="w-full h-10 px-3 pr-10 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                autocomplete="off"
              />
              <button
                type="button"
                class="absolute right-2 top-1/2 -translate-y-1/2 p-1 transition-colors hover:bg-gray-100"
                @click="showKey = !showKey"
                :aria-label="showKey ? '隐藏 Key' : '显示 Key'"
              >
                <Icon :name="showKey ? 'eye-off' : 'eye'" :size="16" style="color: var(--kb-muted-foreground);" aria-hidden="true" />
              </button>
            </div>
            <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">Key 仅保存在服务器</p>
          </div>

          <!-- Base URL -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">API 地址</label>
            <input
              v-model="form.baseUrl"
              type="text"
              placeholder="https://api.deepseek.com/v1"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>

          <!-- Model -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">模型名称</label>
            <input
              v-model="form.model"
              type="text"
              placeholder="deepseek-chat"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>
        </div>

        <!-- 启用开关 -->
        <div class="flex items-center justify-between p-4 rounded-lg border" style="border-color: var(--kb-border);">
          <div>
            <h4 class="text-sm font-medium" style="color: var(--kb-foreground);">启用自定义配置</h4>
            <p class="text-xs mt-0.5" style="color: var(--kb-muted-foreground);">开启后将使用你自己的 API Key 调用大模型</p>
          </div>
          <button
            type="button"
            :class="[
              'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 hover:opacity-90',
              form.isActive ? 'bg-[var(--kb-primary)]' : 'bg-gray-200'
            ]"
            @click="form.isActive = form.isActive ? 0 : 1"
          >
            <span
              :class="[
                'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-card shadow transition duration-200',
                form.isActive ? 'translate-x-5' : 'translate-x-0'
              ]"
            ></span>
          </button>
        </div>

        <!-- 连通性测试结果 -->
        <div v-if="testResult" class="p-4 rounded-lg border" :style="{
          borderColor: testResult.success ? 'var(--kb-accent)' : 'var(--kb-danger, #ef4444)',
          background: testResult.success ? 'rgba(16,185,129,0.06)' : 'rgba(239,68,68,0.06)',
        }">
          <div class="flex items-start gap-3">
            <Icon
              :name="testResult.success ? 'check-circle' : 'x-circle'"
              :size="20"
              :style="{ color: testResult.success ? 'var(--kb-accent)' : 'var(--kb-danger, #ef4444)' }"
            />
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium" :style="{ color: testResult.success ? 'var(--kb-accent)' : 'var(--kb-danger, #ef4444)' }">
                  {{ testResult.success ? '连通成功' : '连通失败' }}
                </span>
                <span class="text-xs" style="color: var(--kb-muted-foreground);">耗时 {{ testResult.elapsedMs }}ms</span>
              </div>
              <p class="text-sm mt-1" style="color: var(--kb-foreground);">{{ testResult.message }}</p>
              <p v-if="testResult.reply" class="text-xs mt-1" style="color: var(--kb-muted-foreground);">模型回复：{{ testResult.reply }}</p>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center justify-between gap-3">
          <button
            type="button"
            :disabled="testing"
            class="inline-flex items-center gap-2 px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="border-color: var(--kb-border); color: var(--kb-foreground);"
            @click="handleTest"
          >
            <Icon :name="testing ? 'loader' : 'zap'" :size="16" :class="testing ? 'animate-spin' : ''" style="color: var(--kb-primary);" />
            {{ testing ? '测试中...' : '测试连通性' }}
          </button>
          <div class="flex items-center gap-3">
            <button
              v-if="userConfig"
              type="button"
              class="px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="border-color: var(--kb-border); color: var(--kb-foreground);"
              @click="handleDelete"
            >
              删除配置
            </button>
            <button
              type="button"
              class="px-4 h-9 rounded-lg text-sm font-medium transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
              @click="handleSave"
            >
              保存配置
            </button>
          </div>
        </div>
      </div>
    </Card>

    <!-- Section 2: 平台模型 -->
    <Card>
      <template #header>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(16,185,129,0.08);">
            <Icon name="sparkles" :size="20" style="color: var(--kb-accent);" />
          </div>
          <div>
            <h3 class="font-semibold" style="color: var(--kb-foreground);">平台模型</h3>
            <p class="text-xs" style="color: var(--kb-muted-foreground);">使用平台提供的 AI 能力，无需配置 Key</p>
          </div>
        </div>
      </template>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="pm in platformModels"
          :key="pm.provider"
          class="p-4 rounded-lg border transition-colors"
          style="border-color: var(--kb-border);"
        >
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-3">
              <div class="w-9 h-9 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
                <Icon name="bot" :size="18" style="color: var(--kb-primary);" />
              </div>
              <div>
                <h4 class="text-sm font-medium" style="color: var(--kb-foreground);">{{ pm.label }}</h4>
                <p class="text-xs" style="color: var(--kb-muted-foreground);">{{ pm.model }}</p>
              </div>
            </div>
            <span
              v-if="pm.subscriptionRequired"
              class="px-2 py-0.5 rounded text-xs font-medium"
              style="background: rgba(245,158,11,0.08); color: var(--kb-warning);"
            >订阅</span>
            <span
              v-else
              class="px-2 py-0.5 rounded text-xs font-medium"
              style="background: rgba(16,185,129,0.08); color: var(--kb-accent);"
            >免费额度</span>
          </div>
          <p class="text-xs mb-2" style="color: var(--kb-muted-foreground);">{{ pm.priceInfo }}</p>
          <div class="flex items-center justify-between">
            <p class="text-xs" style="color: var(--kb-muted-foreground);">{{ pm.baseUrl || '自定义地址' }}</p>
            <a
              v-if="pm.websiteUrl"
              :href="pm.websiteUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1 text-xs font-medium hover:opacity-80"
              style="color: var(--kb-primary);"
            >
              <Icon name="external-link" :size="12" />
              官网
            </a>
          </div>
        </div>
      </div>
      <div class="mt-4 p-3 rounded-lg" style="background: rgba(59,111,224,0.04);">
        <div class="flex items-start gap-2">
          <Icon name="info" :size="16" style="color: var(--kb-primary); margin-top: 2px;" />
          <div>
            <p class="text-xs" style="color: var(--kb-muted-foreground);">平台模型由系统统一管理，免费额度用完后需订阅才能继续使用。如需无限制使用，请配置自己的 API Key。</p>
          </div>
        </div>
      </div>
    </Card>

    <!-- Section 3: 模型参数 -->
    <Card>
      <template #header>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(245,158,11,0.08);">
            <Icon name="settings" :size="20" style="color: var(--kb-warning);" />
          </div>
          <div>
            <h3 class="font-semibold" style="color: var(--kb-foreground);">模型参数</h3>
            <p class="text-xs" style="color: var(--kb-muted-foreground);">微调 AI 回复的质量和风格</p>
          </div>
        </div>
      </template>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">温度 (Temperature)</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.temperature }}</span>
          </div>
          <input type="range" v-model.number="modelConfig.temperature" min="0" max="2" step="0.1" class="w-full h-2 rounded-lg appearance-none cursor-pointer" style="background: var(--kb-muted);" />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">值越高越随机</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">最大上下文长度</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.maxContext }} tokens</span>
          </div>
          <input type="range" v-model.number="modelConfig.maxContext" min="1024" max="32768" step="1024" class="w-full h-2 rounded-lg appearance-none cursor-pointer" style="background: var(--kb-muted);" />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">最大 token 数量</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">Top P</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.topP }}</span>
          </div>
          <input type="range" v-model.number="modelConfig.topP" min="0" max="1" step="0.05" class="w-full h-2 rounded-lg appearance-none cursor-pointer" style="background: var(--kb-muted);" />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">核采样参数</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">最大生成长度</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.maxTokens }} tokens</span>
          </div>
          <input type="range" v-model.number="modelConfig.maxTokens" min="256" max="8192" step="256" class="w-full h-2 rounded-lg appearance-none cursor-pointer" style="background: var(--kb-muted);" />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">单次回复最大 token</p>
        </div>
      </div>
      <div class="flex items-center justify-end gap-3 mt-6 pt-4 border-t" style="border-color: var(--kb-border);">
        <button type="button" class="px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-muted" style="border-color: var(--kb-border); color: var(--kb-foreground);" @click="resetParams">重置参数</button>
        <button type="button" class="px-4 h-9 rounded-lg text-sm font-medium transition-colors hover:opacity-90" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="saveParams">保存参数</button>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import { aiConfigApi } from '@/api/aiConfig'
import { notify, confirmDialog } from '@/utils/toast'
import type { UserAiConfigVO, PlatformModelVO, AiTestResult } from '@/api/types'

const userConfig = ref<UserAiConfigVO | null>(null)
const platformModels = ref<PlatformModelVO[]>([])
const showKey = ref(false)
const showKeyGuide = ref(false)
const testing = ref(false)
const testResult = ref<AiTestResult | null>(null)

const form = reactive({
  provider: 'deepseek',
  apiKey: '',
  baseUrl: 'https://api.deepseek.com/v1',
  model: 'deepseek-chat',
  isActive: 1,
})

const modelConfig = reactive({
  temperature: 0.7,
  maxContext: 8192,
  topP: 0.9,
  maxTokens: 2048,
})

const PARAMS_KEY = 'knowflow_model_params'

const currentProviderInfo = computed(() =>
  platformModels.value.find(p => p.provider === form.provider)
)

function selectProvider(id: string) {
  form.provider = id
  const p = platformModels.value.find(m => m.provider === id)
  if (p) {
    form.baseUrl = p.baseUrl || ''
    form.model = p.defaultModel || p.model || ''
  }
  testResult.value = null
  showKeyGuide.value = false
}

async function loadConfig() {
  try {
    const [config, models] = await Promise.all([
      aiConfigApi.getConfig(),
      aiConfigApi.platformModels(),
    ])
    userConfig.value = config
    platformModels.value = models
    if (config) {
      form.provider = config.provider || 'deepseek'
      form.baseUrl = config.baseUrl || ''
      form.model = config.model || ''
      form.isActive = config.isActive ?? 1
    } else if (models.length > 0) {
      selectProvider(models[0].provider)
    }
  } catch {
    // 静默处理
  }
}

function loadParams() {
  try {
    const raw = localStorage.getItem(PARAMS_KEY)
    if (raw) Object.assign(modelConfig, JSON.parse(raw))
  } catch {
    // 忽略
  }
}

async function handleSave() {
  if (!form.apiKey && !userConfig.value) {
    notify('请输入 API Key', 'error')
    return
  }
  try {
    const result = await aiConfigApi.saveConfig({
      provider: form.provider,
      apiKey: form.apiKey || '****',
      baseUrl: form.baseUrl,
      model: form.model,
      isActive: form.isActive,
    })
    userConfig.value = result
    form.apiKey = ''
    notify('AI 配置已保存', 'success')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存失败'
    notify(msg, 'error')
  }
}

async function handleTest() {
  testing.value = true
  testResult.value = null
  try {
    const result = await aiConfigApi.testConnection({
      provider: form.provider,
      apiKey: form.apiKey || (userConfig.value?.apiKeyMasked || ''),
      baseUrl: form.baseUrl,
      model: form.model,
    })
    testResult.value = result
    if (result.success) {
      notify('连通测试成功', 'success')
    } else {
      notify(result.message, 'error')
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '测试失败'
    testResult.value = { success: false, message: msg, elapsedMs: 0 }
    notify(msg, 'error')
  } finally {
    testing.value = false
  }
}

async function handleDelete() {
  const confirmed = await confirmDialog('确定要删除自定义 AI 配置吗？将回退到使用平台模型。')
  if (!confirmed) return
  try {
    await aiConfigApi.deleteAll()
    userConfig.value = null
    form.apiKey = ''
    form.isActive = 0
    testResult.value = null
    notify('已删除自定义配置，将使用平台模型', 'success')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    notify(msg, 'error')
  }
}

function resetParams() {
  modelConfig.temperature = 0.7
  modelConfig.maxContext = 8192
  modelConfig.topP = 0.9
  modelConfig.maxTokens = 2048
  localStorage.removeItem(PARAMS_KEY)
  notify('参数已重置', 'success')
}

function saveParams() {
  localStorage.setItem(PARAMS_KEY, JSON.stringify(modelConfig))
  notify('参数已保存', 'success')
}

onMounted(() => {
  loadConfig()
  loadParams()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.provider-btn:hover {
  border-color: var(--kb-primary) !important;
}
input[type="range"] {
  -webkit-appearance: none;
  appearance: none;
}
input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}
input[type="range"]::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}
</style>
