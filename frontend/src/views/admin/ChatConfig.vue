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
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
            <button
              v-for="p in providerList"
              :key="p.id"
              type="button"
              class="provider-btn p-3 rounded-lg border text-center transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              :style="{
                borderColor: form.provider === p.id ? 'var(--kb-primary)' : 'var(--kb-border)',
                background: form.provider === p.id ? 'rgba(59,111,224,0.06)' : 'var(--kb-card)',
              }"
              @click="selectProvider(p.id)"
            >
              <div class="text-sm font-medium" :style="{ color: form.provider === p.id ? 'var(--kb-primary)' : 'var(--kb-foreground)' }">{{ p.label }}</div>
            </button>
          </div>
        </div>

        <!-- API Key -->
        <div>
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
              class="absolute right-2 top-1/2 -translate-y-1/2 p-1 transition-colors hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              @click="showKey = !showKey"
              :aria-label="showKey ? '隐藏 Key' : '显示 Key'"
            >
              <Icon :name="showKey ? 'eye-off' : 'eye'" :size="16" style="color: var(--kb-muted-foreground);" aria-hidden="true" />
            </button>
          </div>
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">Key 仅保存在服务器，不会泄露给第三方</p>
        </div>

        <!-- Base URL -->
        <div>
          <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">API 地址 (Base URL)</label>
          <input
            v-model="form.baseUrl"
            type="text"
            placeholder="https://api.deepseek.com/v1"
            class="w-full h-10 px-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
          />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">选择提供商后自动填充，也可自定义</p>
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

        <!-- 启用开关 -->
        <div class="flex items-center justify-between p-4 rounded-lg border" style="border-color: var(--kb-border);">
          <div>
            <h4 class="text-sm font-medium" style="color: var(--kb-foreground);">启用自定义配置</h4>
            <p class="text-xs mt-0.5" style="color: var(--kb-muted-foreground);">开启后将使用你自己的 API Key 调用大模型</p>
          </div>
          <button
            type="button"
            :class="[
              'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
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

        <!-- 操作按钮 -->
        <div class="flex items-center justify-end gap-3">
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
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
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
          <p class="text-xs mb-3" style="color: var(--kb-muted-foreground);">{{ pm.priceInfo }}</p>
          <p class="text-xs" style="color: var(--kb-muted-foreground);">API: {{ pm.baseUrl }}</p>
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
          <input
            type="range"
            v-model.number="modelConfig.temperature"
            min="0"
            max="2"
            step="0.1"
            class="w-full h-2 rounded-lg appearance-none cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="background: var(--kb-muted); color: var(--kb-primary);"
          />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">控制输出的随机性，值越高越随机</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">最大上下文长度</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.maxContext }} tokens</span>
          </div>
          <input
            type="range"
            v-model.number="modelConfig.maxContext"
            min="1024"
            max="32768"
            step="1024"
            class="w-full h-2 rounded-lg appearance-none cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="background: var(--kb-muted); color: var(--kb-primary);"
          />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">模型能处理的最大 token 数量</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">Top P</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.topP }}</span>
          </div>
          <input
            type="range"
            v-model.number="modelConfig.topP"
            min="0"
            max="1"
            step="0.05"
            class="w-full h-2 rounded-lg appearance-none cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="background: var(--kb-muted); color: var(--kb-primary);"
          />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">核采样参数，控制输出的多样性</p>
        </div>
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">最大生成长度</label>
            <span class="text-sm font-medium" style="color: var(--kb-primary);">{{ modelConfig.maxTokens }} tokens</span>
          </div>
          <input
            type="range"
            v-model.number="modelConfig.maxTokens"
            min="256"
            max="8192"
            step="256"
            class="w-full h-2 rounded-lg appearance-none cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            style="background: var(--kb-muted); color: var(--kb-primary);"
          />
          <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">单次回复的最大 token 数量</p>
        </div>
      </div>
      <div class="flex items-center justify-end gap-3 mt-6 pt-4 border-t" style="border-color: var(--kb-border);">
        <button
          type="button"
          class="px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          style="border-color: var(--kb-border); color: var(--kb-foreground);"
          @click="resetParams"
        >
          重置参数
        </button>
        <button
          type="button"
          class="px-4 h-9 rounded-lg text-sm font-medium transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
          @click="saveParams"
        >
          保存参数
        </button>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
// 管理后台-AI 设置：用户自定义 API Key + 平台模型 + 参数微调。
import { ref, reactive, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import { aiConfigApi } from '@/api/aiConfig'
import { notify, confirmDialog } from '@/utils/toast'
import type { UserAiConfigVO, PlatformModelVO } from '@/api/types'

// ===== 提供商预设 =====
const providerList = [
  { id: 'deepseek', label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { id: 'siliconflow', label: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', model: 'Qwen/Qwen2.5-7B-Instruct' },
  { id: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o' },
  { id: 'qwen', label: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { id: 'custom', label: '自定义', baseUrl: '', model: '' },
]

const userConfig = ref<UserAiConfigVO | null>(null)
const platformModels = ref<PlatformModelVO[]>([])
const showKey = ref(false)

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

function selectProvider(id: string) {
  form.provider = id
  const preset = providerList.find(p => p.id === id)
  if (preset) {
    form.baseUrl = preset.baseUrl
    form.model = preset.model
  }
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
    }
  } catch {
    // 未配置时静默处理
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
      apiKey: form.apiKey || '****', // 如果未修改 key，传占位符让后端忽略
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

async function handleDelete() {
  const confirmed = await confirmDialog('确定要删除自定义 AI 配置吗？将回退到使用平台模型。')
  if (!confirmed) return
  try {
    await aiConfigApi.deleteConfig()
    userConfig.value = null
    form.apiKey = ''
    form.isActive = 0
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
