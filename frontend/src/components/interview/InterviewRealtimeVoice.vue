<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue';
import type { V2Session } from '@/api/interviewV2';
import Button from '@/components/ui/Button.vue';
import Checkbox from '@/components/ui/Checkbox.vue';
import Select from '@/components/ui/Select.vue';
import Input from '@/components/ui/Input.vue';
import Textarea from '@/components/ui/Textarea.vue';
import { confirmDialog } from '@/utils/toast';
import { useInterviewRealtimeVoice } from '@/composables/useInterviewRealtimeVoice';
const props = defineProps<{ session: V2Session; disabled?: boolean }>();
const emit = defineEmits<{ updated: [session: V2Session]; active: [value: boolean]; pending: [value: boolean] }>();
const voice = useInterviewRealtimeVoice({ session: () => props.session, blocked: () => !!props.disabled, updated: (value) => emit('updated', value) });
const { capability, loading, error, status, active, pending, draft, partial, provisionalQuestion, finalReady, voiceOutput, allowBargeIn, playing, volume, deviceId, devices } = voice;
const labels = { offline: '未连接', connecting: '正在连接', ready: '实时连接就绪', preparing: '正在请求麦克风权限（最多等待20秒）；请检查浏览器提示，可随时取消', recording: '正在收音', finalizing: '等待最终转写', draft: '请校对转写并确认', committing: '正在提交回答', thinking: '正在评估并准备经过校验的下一题', reconnecting: '正在重连，草稿保留' };
watch(active, (value) => emit('active', value), { immediate: true });
watch(pending, (value) => emit('pending', value), { immediate: true });
onBeforeUnmount(() => { emit('active', false); emit('pending', false); });
async function endSession(): Promise<void> {
  if (await confirmDialog('结束面试并生成报告？未提交的语音草稿不会进入报告。', {title:'结束面试',confirmText:'结束并生成报告'})) { voice.control('end'); }
}
</script>
<template>
  <section class="kf-realtime-voice" aria-label="实时语音回答">
    <h4>实时语音回答</h4>
    <p>转写不会自动提交；请停止录音、校对最终文字后确认。{{ capability?.llmStreaming ? 'AI候选问题逐步显示，校验通过后才成为正式问题。' : '追问经过校验后完整显示，当前不提供模型逐字输出。' }}</p>
    <p v-if="loading" role="status">正在检查实时语音能力…</p>
    <p v-else-if="!capability?.enabled" role="status">{{ capability?.reason || '实时语音不可用，仍可使用普通录音或文字作答。' }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <Button v-if="!active" variant="secondary" :disabled="loading" @click="voice.loadCapability">重新检查能力</Button>
    <template v-if="capability?.enabled">
      <p role="status">{{ labels[status] }}</p>
      <Select v-model="deviceId" label="输入麦克风" aria-label="输入麦克风" :disabled="active" :options="[{value:'',label:'系统默认麦克风'},...devices.map((device,index)=>({value:device.deviceId,label:device.label || `麦克风 ${index+1}`}))]" />
      <Button variant="secondary" :disabled="active" @click="voice.loadDevices">刷新麦克风列表</Button>
      <label class="kf-realtime-option"><Checkbox v-model="voiceOutput" :disabled="!capability.tts.available" />朗读面试问题（需明确开启）</label>
      <label class="kf-realtime-option"><Checkbox v-model="allowBargeIn" :disabled="status==='recording'||status==='finalizing'" />收音时允许语音打断朗读</label>
      <p v-if="allowBargeIn">仅在你点击开始收音后启用服务端语音检测；朗读开始后的前 300ms 不自动打断。仍可随时手动打断。</p>
      <p v-if="playing" role="status">正在朗读面试问题</p>
      <p v-if="!capability.tts.available">语音输出未配置，问题文字完整保留。</p>
      <Input :model-value="volume" @update:model-value="value=>{const number=Number(value);if(Number.isFinite(number)&&number>=0&&number<=1)volume=number}" label="语音输出音量（0–1，0为静音）" type="number" :min="0" :max="1" :step="0.1" />
      <Button v-if="status==='preparing'" variant="secondary" @click="voice.disconnect(false)">取消麦克风请求</Button>
      <div class="kf-realtime-actions">
        <Button v-if="!active" :disabled="disabled" @click="voice.open">连接实时语音</Button>
        <Button v-if="status==='ready'" :disabled="disabled" @click="voice.startCapture">开始收音</Button>
        <Button v-if="status==='recording'" @click="voice.stopCapture">停止并转写</Button>
        <Button v-if="status==='recording'" variant="secondary" @click="voice.capture.state.value==='paused'?voice.capture.resume():voice.capture.pause()">{{voice.capture.state.value==='paused'?'继续收音':'暂停收音'}}</Button>
        <Button v-if="active" variant="secondary" @click="voice.interrupt">打断朗读</Button>
        <Button v-if="active" variant="secondary" @click="voice.disconnect(false)">断开并保留草稿</Button>
        <Button v-if="active" variant="secondary" @click="voice.control('pause')">暂停面试</Button>
        <Button v-if="active" variant="secondary" @click="endSession">结束并查看报告</Button>
      </div>
      <label v-if="status==='recording'">输入音量<meter min="0" max="1" :value="voice.capture.volume.value" />已收音 {{voice.capture.seconds.value.toFixed(1)}} 秒</label>
      <p v-if="provisionalQuestion" aria-live="polite">AI候选问题（正在生成，尚未校验）：{{ provisionalQuestion }}</p>
      <p v-if="partial" aria-live="polite">实时草稿：{{ partial }}</p>
      <Textarea v-if="draft || finalReady" v-model="draft" label="最终转写（可编辑）" :maxlength="8000" :disabled="status==='committing'" />
      <p v-if="draft && !finalReady">草稿已保留；重新连接以恢复服务端最终转写，或复制文字后断开使用文字作答。</p>
      <Button v-if="finalReady" :disabled="status!=='draft'||!draft.trim()||disabled" @click="voice.commit">确认最终文字并提交回答</Button>
      <Button v-if="draft || partial" variant="secondary" @click="voice.disconnect(true)">丢弃实时草稿</Button>
    </template>
  </section>
</template>
<style scoped>
.kf-realtime-voice{display:grid;gap:var(--kf-space-3);padding:var(--kf-space-4);background:var(--kf-muted);border-radius:var(--kf-radius-md)}
.kf-realtime-actions,.kf-realtime-option{display:flex;align-items:center;flex-wrap:wrap;gap:var(--kf-space-2)}
</style>
