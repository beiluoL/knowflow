<template>
  <button :type="type" :class="['kb-button', `kb-button--${variant}`, `kb-button--${size}`, {'kb-button--icon-only':iconOnly,'w-full':block,'kb-button--loading-text':loading && !iconName}]"
    :disabled="disabled || loading" :aria-busy="loading || undefined" :aria-label="ariaLabel" :title="title"
    @click="$emit('click',$event)">
    <span v-if="iconName || loading" class="kb-button__icon" :class="{'kb-button__icon--trailing':iconPosition==='right'}">
      <Icon :name="loading ? 'loader' : iconName!" :size="size==='lg'?18:16" :class="{'kb-button__spinner':loading}" />
    </span>
    <span v-if="!iconOnly" class="kb-button__label"><slot /></span>
  </button>
</template>
<script setup lang="ts">
import Icon from './Icon.vue'
withDefaults(defineProps<{
 variant?:'primary'|'secondary'|'outline'|'ghost'|'text'|'danger';size?:'sm'|'md'|'lg';type?:'button'|'submit'|'reset';
 disabled?:boolean;loading?:boolean;block?:boolean;iconName?:string;iconOnly?:boolean;ariaLabel?:string;title?:string;iconPosition?:'left'|'right'
}>(),{variant:'primary',size:'md',type:'button',disabled:false,loading:false,block:false,iconOnly:false,iconPosition:'left'})
defineEmits<{click:[event:MouseEvent]}>()
</script>
<style scoped>
.kb-button{position:relative;display:inline-flex;align-items:center;justify-content:center;gap:var(--kb-button-gap);height:var(--kb-btn-height-md);padding:0 var(--kb-btn-padding-md);border:1px solid transparent;border-radius:var(--kb-btn-radius);font-family:var(--font-sans);font-size:var(--kb-fs-body-md);line-height:var(--kb-button-line-height);font-weight:var(--kb-fw-label);white-space:nowrap;box-shadow:none;cursor:pointer;transition:background-color .16s,color .16s,border-color .16s}
.kb-button--sm{height:var(--kb-btn-height-sm);padding-inline:var(--kb-btn-padding-sm)}.kb-button--lg{height:var(--kb-btn-height-lg)}
.kb-button__icon{display:inline-flex;align-items:center;justify-content:center;width:var(--kb-button-icon-slot);flex:0 0 var(--kb-button-icon-slot)}.kb-button__icon--trailing{order:1}.kb-button__label{min-width:0;white-space:nowrap}
/* Loading without an icon overlays the preserved label box. */
.kb-button--loading-text .kb-button__icon{position:absolute;inset:0;width:auto}.kb-button--loading-text .kb-button__label{color:transparent}
.kb-button[aria-busy=true]:not(:has(.kb-button__icon + .kb-button__label)){cursor:wait}
.kb-button--icon-only{width:var(--kb-btn-height-md);padding:0}.kb-button--icon-only.kb-button--sm{width:var(--kb-btn-height-sm)}.kb-button--icon-only.kb-button--lg{width:var(--kb-btn-height-lg)}
.kb-button--primary{background:var(--kb-primary);color:var(--kb-primary-foreground)}.kb-button--danger{background:var(--kb-danger-text);color:var(--kb-destructive-foreground)}
.kb-button--secondary{background:var(--kb-card);color:var(--kb-foreground);border-color:var(--kb-border)}
/* ghost retains its historical outlined contract; outline is the explicit alias. */
.kb-button--outline,.kb-button--ghost{background:transparent;color:var(--kb-primary);border-color:var(--kb-primary)}.kb-button--text{background:transparent;color:var(--kb-primary)}
.kb-button:hover:not(:disabled){filter:brightness(.96)}.kb-button--text:hover:not(:disabled){background:var(--kb-primary-soft)}.kb-button:active:not(:disabled){filter:brightness(.9)}
.kb-button:focus-visible{outline:2px solid var(--kb-ring);outline-offset:var(--kb-space-1)}.kb-button:disabled{opacity:.5;cursor:not-allowed}.kb-button__spinner{animation:kb-button-spin 1s linear infinite}
@keyframes kb-button-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.kb-button{transition:none}.kb-button__spinner{animation:none}}
@media(forced-colors:active){.kb-button{border-color:ButtonText}.kb-button:focus-visible{outline-color:Highlight}.kb-button:disabled{color:GrayText}}
</style>
