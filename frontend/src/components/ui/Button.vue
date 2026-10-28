<template>
  <button :type="type" :class="['kf-button', `kf-button--${variant}`, `kf-button--${size}`, {'kf-button--icon-only':iconOnly,'w-full':block,'kf-button--loading-text':loading && !iconName}]"
    :disabled="disabled || loading" :aria-busy="loading || undefined" :aria-label="ariaLabel" :title="title"
    @click="$emit('click',$event)">
    <span v-if="iconName || loading" class="kf-button__icon" :class="{'kf-button__icon--trailing':iconPosition==='right'}">
      <Icon :name="loading ? 'loader' : iconName!" :size="size==='lg'?18:16" :class="{'kf-button__spinner':loading}" />
    </span>
    <span v-if="!iconOnly" class="kf-button__label"><slot /></span>
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
.kf-button{position:relative;display:inline-flex;align-items:center;justify-content:center;gap:var(--kf-button-gap);height:var(--kf-btn-height-md);padding:0 var(--kf-btn-padding-md);border:1px solid transparent;border-radius:var(--kf-btn-radius);font-family:var(--font-sans);font-size:var(--kf-fs-body-md);line-height:var(--kf-button-line-height);font-weight:var(--kf-fw-label);white-space:nowrap;box-shadow:none;cursor:pointer;transition:background-color .16s,color .16s,border-color .16s}
.kf-button--sm{height:var(--kf-btn-height-sm);padding-inline:var(--kf-btn-padding-sm)}.kf-button--lg{height:var(--kf-btn-height-lg)}
.kf-button__icon{display:inline-flex;align-items:center;justify-content:center;width:var(--kf-button-icon-slot);flex:0 0 var(--kf-button-icon-slot)}.kf-button__icon--trailing{order:1}.kf-button__label{min-width:0;white-space:nowrap}
/* Loading without an icon overlays the preserved label box. */
.kf-button--loading-text .kf-button__icon{position:absolute;inset:0;width:auto}.kf-button--loading-text .kf-button__label{color:transparent}
.kf-button[aria-busy=true]:not(:has(.kf-button__icon + .kf-button__label)){cursor:wait}
.kf-button--icon-only{width:var(--kf-btn-height-md);padding:0}.kf-button--icon-only.kf-button--sm{width:var(--kf-btn-height-sm)}.kf-button--icon-only.kf-button--lg{width:var(--kf-btn-height-lg)}
.kf-button--primary{background:var(--kf-primary);color:var(--kf-primary-foreground)}.kf-button--danger{background:var(--kf-danger-text);color:var(--kf-destructive-foreground)}
.kf-button--secondary{background:var(--kf-card);color:var(--kf-foreground);border-color:var(--kf-border)}
/* ghost retains its historical outlined contract; outline is the explicit alias. */
.kf-button--outline,.kf-button--ghost{background:transparent;color:var(--kf-primary);border-color:var(--kf-primary)}.kf-button--text{background:transparent;color:var(--kf-primary)}
.kf-button:hover:not(:disabled){filter:brightness(.96)}.kf-button--text:hover:not(:disabled){background:var(--kf-primary-soft)}.kf-button:active:not(:disabled){filter:brightness(.9)}
.kf-button:focus-visible{outline:2px solid var(--kf-ring);outline-offset:var(--kf-space-1)}.kf-button:disabled{opacity:.5;cursor:not-allowed}.kf-button__spinner{animation:kf-button-spin 1s linear infinite}
@keyframes kf-button-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.kf-button{transition:none}.kf-button__spinner{animation:none}}
@media(forced-colors:active){.kf-button{border-color:ButtonText}.kf-button:focus-visible{outline-color:Highlight}.kf-button:disabled{color:GrayText}}
</style>
