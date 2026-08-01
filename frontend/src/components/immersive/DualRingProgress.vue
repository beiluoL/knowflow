<template>
  <div class="dual-ring" :style="{ width: size + 'px', height: size + 'px' }">
    <svg
      class="ring-svg"
      :viewBox="`0 0 ${size + 20} ${size + 20}`"
      :width="size + 20"
      :height="size + 20"
      aria-hidden="true"
    >
      <circle
        :cx="(size + 20) / 2"
        :cy="(size + 20) / 2"
        :r="outerR"
        fill="none"
        stroke="rgba(255,255,255,0.08)"
        stroke-width="8"
      />
      <circle
        :cx="(size + 20) / 2"
        :cy="(size + 20) / 2"
        :r="outerR"
        fill="none"
        :stroke="outerColor"
        stroke-width="8"
        stroke-linecap="round"
        :stroke-dasharray="outerCircumference"
        :stroke-dashoffset="outerDashoffset"
        class="ring-progress outer"
      />
      <circle
        :cx="(size + 20) / 2"
        :cy="(size + 20) / 2"
        :r="innerR"
        fill="none"
        stroke="rgba(255,255,255,0.08)"
        stroke-width="6"
      />
      <circle
        :cx="(size + 20) / 2"
        :cy="(size + 20) / 2"
        :r="innerR"
        fill="none"
        :stroke="innerColor"
        stroke-width="6"
        stroke-linecap="round"
        :stroke-dasharray="innerCircumference"
        :stroke-dashoffset="innerDashoffset"
        class="ring-progress inner"
      />
    </svg>
    <div class="ring-center">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  innerProgress: number;
  outerProgress?: number;
  innerColor?: string;
  outerColor?: string;
  size?: number;
}

const props = withDefaults(defineProps<Props>(), {
  outerProgress: undefined,
  innerColor: 'var(--kb-primary)',
  outerColor: '#EF4444',
  size: 260,
});

const outerR = computed(() => props.size / 2 - 10);
const innerR = computed(() => props.size / 2 - 26);
const outerCircumference = computed(() => 2 * Math.PI * outerR.value);
const innerCircumference = computed(() => 2 * Math.PI * innerR.value);
const resolvedOuterProgress = computed(() =>
  props.outerProgress !== undefined ? props.outerProgress : props.innerProgress,
);
const outerDashoffset = computed(() =>
  outerCircumference.value * (1 - Math.min(1, Math.max(0, resolvedOuterProgress.value))),
);
const innerDashoffset = computed(() =>
  innerCircumference.value * (1 - Math.min(1, Math.max(0, props.innerProgress))),
);
</script>

<style scoped>
.dual-ring {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.ring-svg {
  position: absolute;
  inset: 0;
  transform: rotate(-90deg);
}
.ring-progress {
  transition: stroke-dashoffset 0.6s ease, stroke 0.3s ease;
}
.ring-center {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
</style>
