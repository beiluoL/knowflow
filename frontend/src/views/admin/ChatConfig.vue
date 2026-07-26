<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">对话配置</h1>
          <p class="text-gray-500 text-sm mt-1">配置 AI 对话的模型参数和知识库设置</p>
        </div>
      </div>

      <Card>
        <template #header>
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center">
              <Icon name="bot" :size="20" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-800">模型配置</h3>
              <p class="text-xs text-gray-500">选择和配置可用的 AI 模型</p>
            </div>
          </div>
        </template>
        <div class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-3">可用模型</label>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <div
                v-for="model in models" :key="model.id"
                class="p-4 border rounded-lg transition-all cursor-pointer model-card"
                :class="[
                  model.enabled ? 'border-primary-200 bg-primary-50/30' : 'border-gray-200 opacity-60',
                  defaultModel === model.id ? 'ring-2 ring-primary-500' : ''
                ]"
                @click="selectModel(model.id)"
              >
                <div class="flex items-start justify-between mb-3">
                  <div class="flex items-center gap-3">
                    <div
                      class="w-10 h-10 rounded-lg flex items-center justify-center"
                      :class="model.enabled ? 'bg-primary-100' : 'bg-gray-100'"
                    >
                      <Icon name="brain" :size="20" />
                    </div>
                    <div>
                      <h4 class="font-medium text-gray-800">{{ model.name }}</h4>
                      <p class="text-xs text-gray-500">{{ model.provider }}</p>
                    </div>
                  </div>
                  <button
                    @click.stop="toggleModel(model.id)"
                    :class="[
                      'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
                      model.enabled ? 'bg-primary-500' : 'bg-gray-200'
                    ]"
                  >
                    <span
                      :class="[
                        'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                        model.enabled ? 'translate-x-5' : 'translate-x-0'
                      ]"
                    ></span>
                  </button>
                </div>
                <p class="text-xs text-gray-500">{{ model.description }}</p>
                <div v-if="defaultModel === model.id" class="mt-3">
                  <Badge variant="primary">默认模型</Badge>
                </div>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6 pt-6 border-t border-gray-100">
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">温度 (Temperature)</label>
                <span class="text-sm text-primary-600 font-medium">{{ modelConfig.temperature }}</span>
              </div>
              <input
                type="range"
                v-model.number="modelConfig.temperature"
                min="0"
                max="2"
                step="0.1"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
              />
              <p class="text-xs text-gray-400 mt-1">控制输出的随机性，值越高越随机</p>
            </div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">最大上下文长度</label>
                <span class="text-sm text-primary-600 font-medium">{{ modelConfig.maxContext }} tokens</span>
              </div>
              <input
                type="range"
                v-model.number="modelConfig.maxContext"
                min="1024"
                max="32768"
                step="1024"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
              />
              <p class="text-xs text-gray-400 mt-1">模型能处理的最大 token 数量</p>
            </div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">Top P</label>
                <span class="text-sm text-primary-600 font-medium">{{ modelConfig.topP }}</span>
              </div>
              <input
                type="range"
                v-model.number="modelConfig.topP"
                min="0"
                max="1"
                step="0.05"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
              />
              <p class="text-xs text-gray-400 mt-1">核采样参数，控制输出的多样性</p>
            </div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">最大生成长度</label>
                <span class="text-sm text-primary-600 font-medium">{{ modelConfig.maxTokens }} tokens</span>
              </div>
              <input
                type="range"
                v-model.number="modelConfig.maxTokens"
                min="256"
                max="8192"
                step="256"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
              />
              <p class="text-xs text-gray-400 mt-1">单次回复的最大 token 数量</p>
            </div>
          </div>
        </div>
      </Card>

      <Card>
        <template #header>
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-success-50 flex items-center justify-center">
              <Library class="w-5 h-5 text-success-500" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-800">知识库配置</h3>
              <p class="text-xs text-gray-500">配置对话时的知识库检索参数</p>
            </div>
            <div class="ml-auto">
              <button
                @click="knowledgeConfig.enabled = !knowledgeConfig.enabled"
                :class="[
                  'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-success-500 focus:ring-offset-2',
                  knowledgeConfig.enabled ? 'bg-success-500' : 'bg-gray-200'
                ]"
              >
                <span
                  :class="[
                    'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                    knowledgeConfig.enabled ? 'translate-x-5' : 'translate-x-0'
                  ]"
                ></span>
              </button>
            </div>
          </div>
        </template>
        <div
          class="space-y-6 transition-all duration-300"
          :class="{ 'opacity-50 pointer-events-none': !knowledgeConfig.enabled }"
        >
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">相似度阈值</label>
                <span class="text-sm text-success-600 font-medium">{{ knowledgeConfig.similarityThreshold }}</span>
              </div>
              <input
                type="range"
                v-model.number="knowledgeConfig.similarityThreshold"
                min="0.5"
                max="0.95"
                step="0.05"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-success-500"
              />
              <p class="text-xs text-gray-400 mt-1">低于此阈值的文档将不被检索</p>
            </div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-sm font-medium text-gray-700">返回参考数量</label>
                <span class="text-sm text-success-600 font-medium">{{ knowledgeConfig.referenceCount }} 篇</span>
              </div>
              <input
                type="range"
                v-model.number="knowledgeConfig.referenceCount"
                min="1"
                max="10"
                step="1"
                class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-success-500"
              />
              <p class="text-xs text-gray-400 mt-1">每次检索返回的参考文档数量</p>
            </div>
          </div>
          <div class="p-4 bg-gray-50 rounded-lg">
            <div class="flex items-start gap-3">
              <Icon name="info" :size="20" />
              <div>
                <p class="text-sm text-gray-600">知识库检索提示</p>
                <p class="text-xs text-gray-500 mt-1">开启知识库后，AI 对话时会自动检索相关文档作为参考，回答将基于知识库内容。</p>
              </div>
            </div>
          </div>
        </div>
      </Card>

      <Card>
        <template #header>
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-lg bg-warning-50 flex items-center justify-center">
              <Icon name="settings" :size="20" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-800">对话设置</h3>
              <p class="text-xs text-gray-500">配置对话的基本规则和限制</p>
            </div>
          </div>
        </template>
        <div class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">每日对话上限</label>
              <Input v-model="chatConfig.dailyLimit" type="number" placeholder="请输入每日对话上限" />
              <p class="text-xs text-gray-400 mt-1">普通用户每日可发起的对话次数，0 表示不限制</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">单轮最大消息数</label>
              <Input v-model="chatConfig.maxMessages" type="number" placeholder="请输入单轮最大消息数" />
              <p class="text-xs text-gray-400 mt-1">单个对话会话中允许的最大消息数量</p>
            </div>
          </div>

          <div class="p-4 border border-gray-200 rounded-lg">
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-800">保留历史对话</h4>
                <p class="text-xs text-gray-500 mt-0.5">开启后，新对话会保留之前的对话上下文</p>
              </div>
              <button
                @click="chatConfig.keepHistory = !chatConfig.keepHistory"
                :class="[
                  'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-warning-500 focus:ring-offset-2',
                  chatConfig.keepHistory ? 'bg-warning-500' : 'bg-gray-200'
                ]"
              >
                <span
                  :class="[
                    'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                    chatConfig.keepHistory ? 'translate-x-5' : 'translate-x-0'
                  ]"
                ></span>
              </button>
            </div>
          </div>

          <div class="p-4 border border-gray-200 rounded-lg">
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-800">显示参考来源</h4>
                <p class="text-xs text-gray-500 mt-0.5">开启后，AI 回答会显示参考的知识库文档来源</p>
              </div>
              <button
                @click="chatConfig.showSources = !chatConfig.showSources"
                :class="[
                  'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-warning-500 focus:ring-offset-2',
                  chatConfig.showSources ? 'bg-warning-500' : 'bg-gray-200'
                ]"
              >
                <span
                  :class="[
                    'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                    chatConfig.showSources ? 'translate-x-5' : 'translate-x-0'
                  ]"
                ></span>
              </button>
            </div>
          </div>
        </div>
      </Card>

      <div class="flex items-center justify-end gap-3">
        <Button variant="secondary" @click="resetConfig">重置配置</Button>
        <Button @click="saveConfig">保存配置</Button>
      </div>
    </div>
</template>

<script setup lang="ts">
// 管理后台-对话配置：管理 AI 模型开关与默认项、知识库检索参数与对话规则，配置持久化于本地。
import { notify } from '@/utils/toast'
import { ref, reactive, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'

// 后端暂无对话配置独立接口，本页配置持久化到浏览器 localStorage
const STORAGE_KEY = 'knowflow_chat_config'

interface Model {
  id: string
  name: string
  provider: string
  description: string
  enabled: boolean
}

const models = ref<Model[]>([
  { id: 'gpt-4', name: 'GPT-4', provider: 'OpenAI', description: '最强大的模型，适合复杂推理和创作', enabled: true },
  { id: 'gpt-3.5-turbo', name: 'GPT-3.5 Turbo', provider: 'OpenAI', description: '快速且经济实惠，适合日常对话', enabled: true },
  { id: 'claude-3', name: 'Claude 3', provider: 'Anthropic', description: '长文本理解能力强，安全性高', enabled: false },
  { id: 'qwen', name: '通义千问', provider: '阿里云', description: '国内模型，响应速度快', enabled: true },
  { id: 'ernie', name: '文心一言', provider: '百度', description: '中文理解能力优秀', enabled: false },
  { id: 'deepseek', name: 'DeepSeek', provider: '深度求索', description: '代码能力强，性价比高', enabled: true },
])

const defaultModel = ref('gpt-4')

const modelConfig = reactive({
  temperature: 0.7,
  maxContext: 8192,
  topP: 0.9,
  maxTokens: 2048,
})

const knowledgeConfig = reactive({
  enabled: true,
  similarityThreshold: 0.75,
  referenceCount: 5,
})

const chatConfig = reactive({
  dailyLimit: '50',
  maxMessages: '50',
  keepHistory: true,
  showSources: true,
})

const toggleModel = (id: string) => {
  const model = models.value.find(m => m.id === id)
  if (model) {
    model.enabled = !model.enabled
    if (!model.enabled && defaultModel.value === id) {
      const firstEnabled = models.value.find(m => m.enabled && m.id !== id)
      if (firstEnabled) {
        defaultModel.value = firstEnabled.id
      }
    }
  }
}

const selectModel = (id: string) => {
  const model = models.value.find(m => m.id === id)
  if (model && model.enabled) {
    defaultModel.value = id
  }
}

const loadConfig = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (Array.isArray(data.models)) {
      data.models.forEach((pm: { id: string; enabled: boolean }) => {
        const m = models.value.find((x) => x.id === pm.id)
        if (m) m.enabled = pm.enabled
      })
    }
    if (data.defaultModel) defaultModel.value = data.defaultModel
    if (data.modelConfig) Object.assign(modelConfig, data.modelConfig)
    if (data.knowledgeConfig) Object.assign(knowledgeConfig, data.knowledgeConfig)
    if (data.chatConfig) Object.assign(chatConfig, data.chatConfig)
  } catch {
    // 忽略损坏的本地配置
  }
}

const resetConfig = () => {
  modelConfig.temperature = 0.7
  modelConfig.maxContext = 8192
  modelConfig.topP = 0.9
  modelConfig.maxTokens = 2048
  knowledgeConfig.enabled = true
  knowledgeConfig.similarityThreshold = 0.75
  knowledgeConfig.referenceCount = 5
  chatConfig.dailyLimit = '50'
  chatConfig.maxMessages = '50'
  chatConfig.keepHistory = true
  chatConfig.showSources = true
  models.value.forEach((m) => (m.enabled = true))
  defaultModel.value = 'gpt-4'
  localStorage.removeItem(STORAGE_KEY)
}

const saveConfig = () => {
  const payload = {
    models: models.value.map((m) => ({ id: m.id, enabled: m.enabled })),
    defaultModel: defaultModel.value,
    modelConfig,
    knowledgeConfig,
    chatConfig,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  notify('配置已保存到本地', 'success')
}

onMounted(loadConfig)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.model-card {
  animation: scaleIn 0.3s ease-out;
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
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
  background: currentColor;
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

input[type="range"]::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: currentColor;
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.accent-primary-500 {
  color: #3B6FE0;
}

.accent-success-500 {
  color: #10B981;
}

.accent-warning-500 {
  color: #F59E0B;
}
</style>
