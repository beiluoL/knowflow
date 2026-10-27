<template>
  <div class="ai-composer kb-input-surface" :aria-busy="busy || undefined" :class="{'ai-composer--disabled':disabled,'ai-composer--error':!!error}" :style="maxHeight?{'--ai-composer-max-height':maxHeight+'px'}:undefined" ref="container">
    <div v-if="attachments.length" class="ai-composer__attachments" aria-label="待发送附件">
      <div v-for="attachment in attachments" :key="attachment.id" class="ai-composer__attachment">
        <img v-if="attachment.previewUrl" :src="attachment.previewUrl" :alt="attachment.name" /><span v-else>{{attachment.name}}</span>
        <button type="button" :aria-label="attachment.previewUrl ? '移除图片' : '移除附件 ' + attachment.name" :disabled="disabled" @click="emit('removeAttachment',attachment.id)"><Icon name="x" :size="12" /></button>
      </div>
    </div>
    <textarea ref="input" :value="modelValue" :aria-label="inputLabel" :placeholder="placeholder" :disabled="disabled" :aria-invalid="error?true:undefined" :aria-describedby="error?errorId:undefined" rows="1" class="ai-composer__input" @input="onInput" @keydown="onKey" @compositionstart="composing=true" @compositionend="composing=false" />
    <div class="ai-composer__toolbar">
      <div class="ai-composer__tools">
        <slot name="tools">
          <button v-if="showAttachment" type="button" aria-label="发送图片" title="添加图片（最多4张，每张5MB；暂不支持文档解析）" :disabled="disabled" @click="emit('attach')"><Icon name="plus" :size="18" /></button>
        </slot>
        <button v-if="showKnowledge" type="button" role="switch" aria-label="关联知识库" :aria-checked="useKnowledgeBase" class="ai-composer__knowledge" :disabled="disabled" @click="emit('update:useKnowledgeBase',!useKnowledgeBase)"><Icon name="database" :size="14" /><span>知识库{{useKnowledgeBase?'已关联':'未关联'}}</span></button>
        <button v-if="showThinking" type="button" role="switch" aria-label="深度思考" :aria-checked="thinkingEnabled" class="ai-composer__thinking" :disabled="disabled||!thinkingAvailable" :title="thinkingTitle" @click="emit('update:thinkingEnabled',!thinkingEnabled)"><Icon name="brain" :size="14" /><span>深度思考{{thinkingEnabled?'已开启':'已关闭'}}</span></button>
      </div>
      <div class="ai-composer__actions">
        <button v-if="modelLabel" type="button" class="ai-composer__model" aria-label="模型设置" :title="modelLabel+' · 打开已有模型配置'" :disabled="disabled" @click="emit('modelSettings')"><span>{{modelLabel}}</span><Icon name="chevron-down" :size="12" /></button>
        <label v-if="modelOptions && modelOptions.length>1" class="ai-composer__model-picker">
          <span class="ai-composer__sr">选择模型</span>
          <select class="kb-select kb-select-sm" :value="selectedModel" :disabled="disabled" aria-label="选择对话模型" @change="onModelChange">
            <option v-for="m in modelOptions" :key="m.value" :value="m.value">{{m.label}}</option>
          </select>
        </label>
        <button v-if="showVoice" type="button" aria-label="语音输入" :aria-pressed="recording" :title="recording?'停止语音输入':'语音输入'" :disabled="disabled" @click="emit('voice')"><Icon name="mic" :size="17" /></button>
        <button v-if="busy&&stoppable" type="button" class="ai-composer__send" aria-label="停止接收" title="停止接收回复" @click="emit('stop')"><Icon name="square" :size="16" /><span class="ai-composer__sr">停止接收</span></button>
        <button v-else type="button" class="ai-composer__send" aria-label="发送消息" :disabled="!canSubmit" :title="busy?'正在处理…':'发送 (Enter)'" @click="submit"><Icon :name="busy?'loader':'arrow-up'" :size="20" :class="busy?'animate-spin':''" /></button>
      </div>
    </div>
    <p v-if="error" :id="errorId" role="alert" class="ai-composer__error">{{error}}</p>
    <slot name="footer" />
  </div>
</template>
<script setup lang="ts">
import {computed,ref,watch,nextTick,onMounted,onUnmounted,useId} from 'vue'
import Icon from '@/components/ui/Icon.vue'
export interface ComposerAttachment {id:string|number;name:string;previewUrl?:string}
const props=withDefaults(defineProps<{modelValue:string;attachments?:readonly ComposerAttachment[];disabled?:boolean;busy?:boolean;stoppable?:boolean;showAttachment?:boolean;showKnowledge?:boolean;showVoice?:boolean;showThinking?:boolean;useKnowledgeBase?:boolean;thinkingEnabled?:boolean;thinkingAvailable?:boolean;thinkingHint?:string;recording?:boolean;modelLabel?:string;modelOptions?:readonly {value:string;label:string}[];selectedModel?:string;placeholder?:string;inputLabel?:string;maxHeight?:number;error?:string}>(),{attachments:()=>[],disabled:false,busy:false,stoppable:false,showAttachment:false,showKnowledge:false,showVoice:false,showThinking:false,useKnowledgeBase:true,thinkingEnabled:false,thinkingAvailable:true,thinkingHint:'',recording:false,modelLabel:'',modelOptions:()=>[],selectedModel:'',placeholder:'输入你的问题…',inputLabel:'输入你的问题',error:''})
const emit=defineEmits<{'update:modelValue':[value:string];'update:useKnowledgeBase':[value:boolean];'update:thinkingEnabled':[value:boolean];'update:selectedModel':[value:string];submit:[];stop:[];attach:[];removeAttachment:[id:string|number];voice:[];modelSettings:[];input:[event:Event]}>()
const input=ref<HTMLTextAreaElement|null>(null),container=ref<HTMLElement|null>(null),composing=ref(false),errorId='composer-error-'+useId()
const canSubmit=computed(()=>!props.disabled&&!props.busy&&(!!props.modelValue.trim()||props.attachments.length>0))
const thinkingTitle=computed(()=>{
  if(!props.thinkingAvailable)return props.thinkingHint||'当前模型不支持深度思考'
  return props.thinkingEnabled?'关闭深度思考（不再展示模型推理内容）':'开启深度思考：展示模型真实返回的推理内容或摘要'
})
function submit(){if(canSubmit.value)emit('submit')}
function onKey(event:KeyboardEvent){if(event.key==='Enter'&&!event.shiftKey&&!event.ctrlKey&&!event.altKey&&!event.metaKey&&!event.isComposing&&!composing.value){event.preventDefault();submit()}}
function onInput(event:Event){emit('update:modelValue',(event.target as HTMLTextAreaElement).value);emit('input',event);resize()}
function onModelChange(event:Event){emit('update:selectedModel',(event.target as HTMLSelectElement).value)}
function resize(){const el=input.value;if(!el||disposed)return;const parsed=parseFloat(getComputedStyle(el).maxHeight);const limit=props.maxHeight??(Number.isFinite(parsed)?parsed:192);el.style.height='auto';el.style.height=Math.min(el.scrollHeight,limit)+'px';el.style.overflowY=el.scrollHeight>limit?'auto':'hidden'}
function focus(){input.value?.focus()}
let observer:ResizeObserver|undefined,disposed=false,lastWidth=-1
watch(()=>[props.modelValue,props.maxHeight],()=>void nextTick(resize))
onMounted(()=>{resize();if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(entries=>{const width=entries[0]?.contentRect.width;if(width!==undefined&&width!==lastWidth){lastWidth=width;resize()}});if(container.value)observer.observe(container.value)}else window.addEventListener('resize',resize)})
onUnmounted(()=>{disposed=true;observer?.disconnect();window.removeEventListener('resize',resize)})
defineExpose({focus,resize})
</script>
<style scoped>
.ai-composer{min-width:0;padding:var(--kb-space-3) var(--kb-space-4);border:1px solid var(--kb-border);border-radius:var(--kb-composer-radius);background:var(--kb-card);color:var(--kb-foreground);box-shadow:none;transition:border-color .16s ease}
.ai-composer:focus-within{border-color:var(--kb-field-border);box-shadow:none}
.ai-composer .ai-composer__input,.ai-composer .ai-composer__input:focus,.ai-composer .ai-composer__input:focus-visible{display:block;width:100%;min-height:2.75rem;max-height:var(--ai-composer-max-height,var(--kb-composer-max-height));resize:none;outline:none;padding:var(--kb-space-2) 0;border:0;border-radius:0;background:transparent;color:inherit;box-shadow:none;font:inherit;font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md)}
.ai-composer__toolbar,.ai-composer__tools,.ai-composer__actions{display:flex;align-items:center;gap:var(--kb-space-2);min-width:0}.ai-composer__toolbar{justify-content:space-between;flex-wrap:wrap;gap:var(--kb-space-2)}.ai-composer__tools{flex:1;flex-wrap:wrap}.ai-composer__actions{margin-inline-start:auto;flex-shrink:0}
.ai-composer button{display:inline-flex;align-items:center;justify-content:center;gap:var(--kb-space-1);min-height:2rem;padding:var(--kb-space-1) var(--kb-space-2);border-radius:var(--kb-radius-md);color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm)}.ai-composer button:hover:not(:disabled){background:var(--kb-muted)}.ai-composer button:focus-visible{outline:2px solid var(--kb-ring);outline-offset:2px}.ai-composer button:disabled{opacity:.5;cursor:not-allowed}.ai-composer button[aria-pressed=true],.ai-composer button[aria-checked=true]{color:var(--kb-primary);background:var(--kb-primary-soft)}
.ai-composer .ai-composer__send{background:var(--kb-primary);color:var(--kb-primary-foreground);border-radius:var(--kb-radius-full);width:var(--kb-composer-send-size);height:var(--kb-composer-send-size);min-height:var(--kb-composer-send-size);padding:0;flex-shrink:0}.ai-composer .ai-composer__send:hover:not(:disabled){background:color-mix(in srgb,var(--kb-primary) 88%,black);opacity:1}.ai-composer .ai-composer__send:disabled{background:var(--kb-muted);color:var(--kb-muted-foreground);opacity:1;cursor:not-allowed}.ai-composer__model span{font-family:var(--font-mono);max-width:12rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ai-composer__send :deep(svg){display:block;stroke-width:2.5}
.ai-composer__attachments{display:flex;gap:var(--kb-space-2);flex-wrap:wrap;margin-bottom:var(--kb-space-2)}.ai-composer__attachment{position:relative;max-width:100%;padding:var(--kb-space-1)}.ai-composer__attachment img{display:block;width:4rem;height:4rem;object-fit:cover;border-radius:var(--kb-radius-md)}.ai-composer__attachment button{position:absolute;right:0;top:0;background:var(--kb-card);border:1px solid var(--kb-border);min-height:1.25rem;padding:var(--kb-space-1)}.ai-composer__error{color:var(--kb-danger-text);font-size:var(--kb-fs-body-sm);margin-top:var(--kb-space-2)}.ai-composer__sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}
.ai-composer.ai-composer--error{border-color:var(--kb-destructive)}
.ai-composer.ai-composer--error:focus-within{border-color:var(--kb-destructive);box-shadow:none}
.ai-composer__input::placeholder{color:var(--kb-field-placeholder);opacity:1}
@media(max-width:639px){.ai-composer{padding:var(--kb-space-2) var(--kb-space-3)}.ai-composer__model span{font-family:var(--font-mono);max-width:6rem}.ai-composer__toolbar{gap:var(--kb-space-1)}.ai-composer__tools,.ai-composer__actions{gap:var(--kb-space-1)}}

@media(prefers-reduced-motion:reduce){.ai-composer{transition:none}}
</style>
