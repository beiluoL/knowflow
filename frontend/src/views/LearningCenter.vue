<template>
  <div class="space-y-6 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">学习中心</h1>
        <p class="text-gray-500 mt-1">今天也要努力学习哦~</p>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 space-y-6">
        <Card hoverable>
          <div class="flex items-center gap-8">
            <div class="relative w-32 h-32 flex-shrink-0">
              <svg class="w-32 h-32 transform -rotate-90" viewBox="0 0 120 120">
                <circle
                  cx="60"
                  cy="60"
                  r="52"
                  stroke="#E5E7EB"
                  stroke-width="8"
                  fill="none"
                />
                <circle
                  cx="60"
                  cy="60"
                  r="52"
                  stroke="url(#progressGradient)"
                  stroke-width="8"
                  fill="none"
                  stroke-linecap="round"
                  :stroke-dasharray="circumference"
                  :stroke-dashoffset="strokeDashoffset"
                  class="transition-all duration-1000 ease-out"
                />
                <defs>
                  <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" style="stop-color:#3B6FE0" />
                    <stop offset="100%" style="stop-color:#6F9AF2" />
                  </linearGradient>
                </defs>
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-3xl font-bold text-primary-600">{{ todayStudyData.goalProgress }}%</span>
                <span class="text-xs text-gray-500">今日目标</span>
              </div>
            </div>

            <div class="flex-1 grid grid-cols-3 gap-4">
              <div class="text-center">
                <div class="flex items-center justify-center gap-1 mb-1">
                  <Icon name="check" :size="20" Circle />
                  <span class="text-2xl font-bold text-gray-800">{{ todayStudyData.completedTasks }}</span>
                  <span class="text-gray-400">/{{ todayStudyData.totalTasks }}</span>
                </div>
                <p class="text-sm text-gray-500">已完成任务</p>
              </div>
              <div class="text-center">
                <div class="flex items-center justify-center gap-1 mb-1">
                  <Icon name="clock" :size="20" />
                  <span class="text-2xl font-bold text-gray-800">{{ todayStudyData.studyMinutes }}</span>
                </div>
                <p class="text-sm text-gray-500">学习时长(分)</p>
              </div>
              <div class="text-center">
                <div class="flex items-center justify-center gap-1 mb-1">
                  <Icon name="flame" :size="20" />
                  <span class="text-2xl font-bold text-gray-800">{{ todayStudyData.streakDays }}</span>
                </div>
                <p class="text-sm text-gray-500">连续天数</p>
              </div>
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="timer" :size="20" />
                <h2 class="font-semibold text-gray-800">番茄钟</h2>
              </div>
              <div class="flex gap-1">
                <button
                  v-for="mode in pomodoroModes" :key="mode.value"
                  @click="switchMode(mode.value)"
                  :class="[
                    'px-3 py-1 text-xs rounded-full transition-all duration-200',
                    currentMode === mode.value
                      ? 'bg-primary-500 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
                  ]"
                >
                  {{ mode.label }}
                </button>
              </div>
            </div>
          </template>

          <div class="flex flex-col items-center py-6">
            <div
              :class="[
                'relative w-48 h-48 rounded-full flex items-center justify-center mb-6 transition-all duration-500',
                modeColors[currentMode].bg,
              ]"
            >
              <div class="absolute inset-2 rounded-full bg-white shadow-inner" />
              <div class="relative z-10 text-center">
                <div class="text-5xl font-bold text-gray-800 font-mono">
                  {{ formatTime(timeLeft) }}
                </div>
                <div class="text-sm text-gray-500 mt-1">
                  {{ modeLabels[currentMode] }}
                </div>
              </div>
              <svg class="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 200 200">
                <circle
                  cx="100"
                  cy="100"
                  r="94"
                  stroke="#F3F4F6"
                  stroke-width="6"
                  fill="none"
                />
                <circle
                  cx="100"
                  cy="100"
                  r="94"
                  :stroke="modeColors[currentMode].stroke"
                  stroke-width="6"
                  fill="none"
                  stroke-linecap="round"
                  :stroke-dasharray="pomodoroCircumference"
                  :stroke-dashoffset="pomodoroDashoffset"
                  class="transition-all duration-1000 ease-linear"
                />
              </svg>
            </div>

            <div class="flex items-center gap-3 mb-4">
              <button
                @click="resetTimer"
                class="w-12 h-12 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
              >
                <Icon name="rotate-ccw" :size="20" />
              </button>
              <button
                @click="toggleTimer"
                :class="[
                  'w-16 h-16 rounded-full flex items-center justify-center transition-all duration-200 shadow-lg',
                  isRunning
                    ? 'bg-warning-500 hover:bg-warning-600 text-white'
                    : 'bg-primary-500 hover:bg-primary-600 text-white',
                ]"
              >
                <Icon name="pause" :size="28" v-if="isRunning" />
                <Icon name="play" :size="28" v-else />
              </button>
              <button
                @click="skipTimer"
                class="w-12 h-12 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
              >
                <Icon name="skip-forward" :size="20" />
              </button>
            </div>

            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-500">今日番茄数：</span>
              <div class="flex gap-1">
                <div
                  v-for="i in 6" :key="i"
                  :class="[
                    'w-3 h-3 rounded-full transition-all duration-300',
                    i <= todayStudyData.pomodorosCompleted
                      ? 'bg-red-400'
                      : 'bg-gray-200',
                  ]"
                />
              </div>
              <span class="text-sm font-medium text-gray-700">{{ todayStudyData.pomodorosCompleted }}/6</span>
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Icon name="list" :size="20" Todo />
                <h2 class="font-semibold text-gray-800">学习任务</h2>
              </div>
              <span class="text-sm text-gray-500">
                {{ completedTasksCount }}/{{ tasks.length }} 已完成
              </span>
            </div>
          </template>

          <div class="space-y-3">
            <div
              v-for="task in tasks" :key="task.id"
              :class="[
                'flex items-center gap-3 p-3 rounded-lg transition-all duration-200 group',
                task.completed ? 'bg-gray-50' : 'hover:bg-gray-50',
              ]"
            >
              <button
                @click="toggleTask(task.id)"
                :class="[
                  'w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 transition-all duration-200',
                  task.completed
                    ? 'bg-primary-500 border-primary-500'
                    : 'border-gray-300 hover:border-primary-400',
                ]"
              >
                <Icon name="check" :size="12" v-if="task.completed" />
              </button>
              <div class="flex-1 min-w-0">
                <p
                  :class="[
                    'text-sm transition-all duration-200',
                    task.completed ? 'text-gray-400 line-through' : 'text-gray-700',
                  ]"
                >
                  {{ task.title }}
                </p>
              </div>
              <div class="flex items-center gap-2 flex-shrink-0">
                <div class="flex items-center gap-1 text-xs text-gray-400">
                  <Icon name="clock" :size="20" />
                  <span>{{ task.duration }}分钟</span>
                </div>
                <button
                  @click="deleteTask(task.id)"
                  class="opacity-0 group-hover:opacity-100 p-1.5 rounded hover:bg-red-50 transition-all"
                >
                  <Icon name="trash-2" :size="16" />
                </button>
              </div>
            </div>

            <div class="flex gap-2 pt-2">
              <Input
                v-model="newTaskTitle"
                placeholder="添加新任务..."
                class="flex-1"
                @keyup.enter="addTask"
              />
              <Button @click="addTask" :disabled="!newTaskTitle.trim()">
                <Icon name="plus" :size="16" />
                添加
              </Button>
            </div>
          </div>
        </Card>
      </div>

      <div class="space-y-6">
        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="heart" :size="20" />
              <h2 class="font-semibold text-gray-800">学习伙伴</h2>
            </div>
          </template>

          <div class="flex flex-col items-center">
            <div class="relative mb-4">
              <div class="owl-container">
                <div class="owl-body">
                  <div class="owl-belly" />
                  <div class="owl-eyes">
                    <div class="owl-eye">
                      <div class="owl-pupil" />
                    </div>
                    <div class="owl-eye">
                      <div class="owl-pupil" />
                    </div>
                  </div>
                  <div class="owl-beak" />
                  <div class="owl-wings">
                    <div class="owl-wing left" />
                    <div class="owl-wing right" />
                  </div>
                </div>
                <div class="owl-feet">
                  <div class="owl-foot" />
                  <div class="owl-foot" />
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2 mb-3">
              <span class="font-semibold text-gray-800">{{ pet.name }}</span>
              <Badge variant="primary">Lv.{{ pet.level }}</Badge>
              <Badge variant="success">{{ pet.mood }}</Badge>
            </div>

            <div class="w-full space-y-3">
              <div>
                <div class="flex justify-between text-xs mb-1">
                  <span class="text-gray-500">体力值</span>
                  <span class="text-gray-700 font-medium">{{ pet.energy }}%</span>
                </div>
                <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-gradient-to-r from-green-400 to-emerald-500 rounded-full transition-all duration-500"
                    :style="{ width: `${pet.energy}%` }"
                  />
                </div>
              </div>
              <div>
                <div class="flex justify-between text-xs mb-1">
                  <span class="text-gray-500">经验值</span>
                  <span class="text-gray-700 font-medium">{{ pet.exp }}/{{ pet.maxExp }}</span>
                </div>
                <div class="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-gradient-to-r from-blue-400 to-indigo-500 rounded-full transition-all duration-500"
                    :style="{ width: `${(pet.exp / pet.maxExp) * 100}%` }"
                  />
                </div>
              </div>
            </div>

            <div class="flex gap-2 mt-4 w-full">
              <Button variant="secondary" size="sm" class="flex-1" @click="feedPet">
                <Icon name="cookie" :size="16" />
                喂食
              </Button>
              <Button variant="secondary" size="sm" class="flex-1" @click="playWithPet">
                <Icon name="gamepad-2" :size="16" />
                玩耍
              </Button>
            </div>
          </div>
        </Card>

        <Card hoverable>
          <template #header>
            <div class="flex items-center gap-2">
              <Icon name="trophy" :size="20" />
              <h2 class="font-semibold text-gray-800">本周排行榜</h2>
            </div>
          </template>

          <div class="space-y-3">
            <div
              v-for="item in rankList" :key="item.id"
              :class="[
                'flex items-center gap-3 p-2 rounded-lg transition-all duration-200',
                item.isCurrentUser ? 'bg-primary-50 ring-1 ring-primary-200' : 'hover:bg-gray-50',
              ]"
            >
              <div
                :class="[
                  'w-7 h-7 rounded-full flex items-center justify-center font-bold text-sm flex-shrink-0',
                  item.rank === 1
                    ? 'bg-gradient-to-br from-yellow-300 to-yellow-500 text-white'
                    : item.rank === 2
                    ? 'bg-gradient-to-br from-gray-300 to-gray-400 text-white'
                    : item.rank === 3
                    ? 'bg-gradient-to-br from-orange-300 to-orange-500 text-white'
                    : 'bg-gray-100 text-gray-500',
                ]"
              >
                {{ item.rank }}
              </div>
              <Avatar :name="item.name" size="sm" />
              <div class="flex-1 min-w-0">
                <p
                  :class="[
                    'text-sm font-medium truncate',
                    item.isCurrentUser ? 'text-primary-600' : 'text-gray-700',
                  ]"
                >
                  {{ item.name }}
                  <span v-if="item.isCurrentUser" class="text-xs text-primary-500">(我)</span>
                </p>
              </div>
              <div class="flex items-center gap-1 text-sm">
                <Icon name="clock" :size="20" />
                <span class="font-medium text-gray-700">{{ item.studyHours }}h</span>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Input from '@/components/ui/Input.vue'
import Button from '@/components/ui/Button.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { todayStudyData, learningPet, weeklyRank } from '@/data/learning'
import { learningApi } from '@/api'

interface StudyTask {
  id: number | string
  title: string
  duration: number
  completed: boolean
}

const radius = 52
const circumference = 2 * Math.PI * radius
const strokeDashoffset = computed(() => {
  return circumference - (todayStudyData.goalProgress / 100) * circumference
})

const pomodoroRadius = 94
const pomodoroCircumference = 2 * Math.PI * pomodoroRadius

type PomodoroMode = 'focus' | 'shortBreak' | 'longBreak'

const pomodoroModes = [
  { value: 'focus' as const, label: '专注', duration: 25 * 60 },
  { value: 'shortBreak' as const, label: '短休', duration: 5 * 60 },
  { value: 'longBreak' as const, label: '长休', duration: 15 * 60 },
]

const modeLabels: Record<PomodoroMode, string> = {
  focus: '专注时间',
  shortBreak: '短休息',
  longBreak: '长休息',
}

const modeColors: Record<PomodoroMode, { bg: string; stroke: string }> = {
  focus: { bg: 'bg-red-50', stroke: '#EF4444' },
  shortBreak: { bg: 'bg-green-50', stroke: '#10B981' },
  longBreak: { bg: 'bg-blue-50', stroke: '#3B6FE0' },
}

const currentMode = ref<PomodoroMode>('focus')
const isRunning = ref(false)
const timeLeft = ref(25 * 60)
let timerInterval: number | null = null

const pomodoroDashoffset = computed(() => {
  const totalTime = pomodoroModes.find((m) => m.value === currentMode.value)?.duration || 25 * 60
  const progress = timeLeft.value / totalTime
  return pomodoroCircumference * (1 - progress)
})

const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const switchMode = (mode: PomodoroMode) => {
  currentMode.value = mode
  isRunning.value = false
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
  const modeData = pomodoroModes.find((m) => m.value === mode)
  timeLeft.value = modeData?.duration || 25 * 60
}

const toggleTimer = () => {
  if (isRunning.value) {
    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
  } else {
    timerInterval = window.setInterval(() => {
      if (timeLeft.value > 0) {
        timeLeft.value--
      } else {
        if (timerInterval) {
          clearInterval(timerInterval)
          timerInterval = null
        }
        isRunning.value = false
      }
    }, 1000)
  }
  isRunning.value = !isRunning.value
}

const resetTimer = () => {
  isRunning.value = false
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
  const modeData = pomodoroModes.find((m) => m.value === currentMode.value)
  timeLeft.value = modeData?.duration || 25 * 60
}

const skipTimer = () => {
  const currentIndex = pomodoroModes.findIndex((m) => m.value === currentMode.value)
  const nextIndex = (currentIndex + 1) % pomodoroModes.length
  switchMode(pomodoroModes[nextIndex].value)
}

const tasks = ref<StudyTask[]>([])
const newTaskTitle = ref('')

const loadTasks = async () => {
  try {
    const list = await learningApi.tasks()
    tasks.value = list.map((t) => ({
      id: t.id,
      title: t.title,
      duration: 25,
      completed: false,
    }))
  } catch {
    tasks.value = []
  }
}

const completedTasksCount = computed(() => {
  return tasks.value.filter((t) => t.completed).length
})

const toggleTask = (id: string | number) => {
  const task = tasks.value.find((t) => t.id === id)
  if (task) {
    task.completed = !task.completed
  }
}

const deleteTask = (id: string | number) => {
  tasks.value = tasks.value.filter((t) => t.id !== id)
}

const addTask = () => {
  if (!newTaskTitle.value.trim()) return
  tasks.value.push({
    id: Date.now().toString(),
    title: newTaskTitle.value.trim(),
    duration: 25,
    completed: false,
  })
  newTaskTitle.value = ''
}

const pet = ref({ ...learningPet })

const feedPet = () => {
  pet.value.energy = Math.min(100, pet.value.energy + 10)
  pet.value.exp = Math.min(pet.value.maxExp, pet.value.exp + 20)
  if (pet.value.exp >= pet.value.maxExp) {
    pet.value.level++
    pet.value.exp = 0
    pet.value.maxExp = Math.floor(pet.value.maxExp * 1.5)
  }
}

const playWithPet = () => {
  pet.value.energy = Math.max(0, pet.value.energy - 15)
  pet.value.exp = Math.min(pet.value.maxExp, pet.value.exp + 30)
  if (pet.value.exp >= pet.value.maxExp) {
    pet.value.level++
    pet.value.exp = 0
    pet.value.maxExp = Math.floor(pet.value.maxExp * 1.5)
  }
}

const rankList = weeklyRank

onMounted(loadTasks)

onUnmounted(() => {
  if (timerInterval) {
    clearInterval(timerInterval)
  }
})
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

.owl-container {
  width: 100px;
  height: 120px;
  position: relative;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-5px);
  }
}

.owl-body {
  width: 80px;
  height: 90px;
  background: linear-gradient(180deg, #8B5CF6 0%, #7C3AED 100%);
  border-radius: 40px 40px 35px 35px;
  position: absolute;
  top: 5px;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: 0 4px 15px rgba(139, 92, 246, 0.3);
}

.owl-belly {
  width: 50px;
  height: 55px;
  background: linear-gradient(180deg, #FDF2F8 0%, #FCE7F3 100%);
  border-radius: 25px 25px 20px 20px;
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
}

.owl-eyes {
  display: flex;
  gap: 12px;
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
}

.owl-eye {
  width: 22px;
  height: 22px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.owl-pupil {
  width: 10px;
  height: 10px;
  background: #1F2937;
  border-radius: 50%;
  position: relative;
  animation: lookAround 4s ease-in-out infinite;
}

.owl-pupil::after {
  content: '';
  width: 4px;
  height: 4px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 1px;
  right: 1px;
}

@keyframes lookAround {
  0%, 100% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(2px, 0);
  }
  50% {
    transform: translate(0, 2px);
  }
  75% {
    transform: translate(-2px, 0);
  }
}

.owl-beak {
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 8px solid #F59E0B;
  position: absolute;
  top: 38px;
  left: 50%;
  transform: translateX(-50%);
}

.owl-wings {
  position: absolute;
  top: 40px;
  width: 100%;
}

.owl-wing {
  width: 18px;
  height: 35px;
  background: linear-gradient(180deg, #7C3AED 0%, #6D28D9 100%);
  border-radius: 50%;
  position: absolute;
}

.owl-wing.left {
  left: -6px;
  transform: rotate(-10deg);
  animation: wingLeft 3s ease-in-out infinite;
}

.owl-wing.right {
  right: -6px;
  transform: rotate(10deg);
  animation: wingRight 3s ease-in-out infinite;
}

@keyframes wingLeft {
  0%, 100% {
    transform: rotate(-10deg);
  }
  50% {
    transform: rotate(-20deg);
  }
}

@keyframes wingRight {
  0%, 100% {
    transform: rotate(10deg);
  }
  50% {
    transform: rotate(20deg);
  }
}

.owl-feet {
  display: flex;
  gap: 16px;
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
}

.owl-foot {
  width: 14px;
  height: 8px;
  background: #F59E0B;
  border-radius: 4px;
}
</style>
