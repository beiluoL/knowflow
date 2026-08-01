<template>
  <div class="achievement-toast-host">
    <transition-group name="ach-fly" tag="div" class="toast-stack">
      <div
        v-for="item in visibleEvents"
        :key="`${item.id}_${item.at}`"
        class="ach-toast"
      >
        <div class="ach-icon-wrap" :style="{ background: iconBgMap[item.id] ?? 'var(--kb-primary)' }">
          <Icon :name="getIcon(item.id)" :size="28" />
        </div>
        <div class="ach-body">
          <div class="ach-name">{{ getName(item.id) }}</div>
          <div class="ach-tag">微成就已解锁！</div>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import type { MicroAchievementId, MicroAchievementDef } from '@/composables/useMicroAchievements';

interface UnlockEvent {
  id: MicroAchievementId;
  at: number;
}

interface Props {
  events: UnlockEvent[];
  allDefs?: MicroAchievementDef[];
}

const props = defineProps<Props>();

type InternalEvent = UnlockEvent;

const shown = new Set<string>();
const visibleEvents = ref<InternalEvent[]>([]);
const defsMap = new Map<MicroAchievementId, MicroAchievementDef>();

const iconBgMap: Partial<Record<MicroAchievementId, string>> = {
  FIRST_FOCUS: '#F59E0B',
  POMODORO_X3: '#10B981',
  POMODORO_X5: '#EF4444',
  FLOW_45MIN: '#0EA5E9',
  FLOW_90MIN: '#8B5CF6',
  NO_DISTRACT: '#14B8A6',
  REVIEW_10: '#3B82F6',
  REVIEW_20: '#6366F1',
  PERFECT_QUALITY: '#F97316',
  STREAK_3DAYS: '#EC4899',
};

function syncDefs() {
  if (props.allDefs) {
    props.allDefs.forEach((d) => defsMap.set(d.id, d));
  }
}

function getIcon(id: MicroAchievementId): string {
  return defsMap.get(id)?.icon ?? 'trophy';
}

function getName(id: MicroAchievementId): string {
  return defsMap.get(id)?.name ?? '新成就';
}

function processEvents() {
  syncDefs();
  const list = props.events;
  if (!list || list.length === 0) return;
  list.forEach((ev) => {
    const key = `${ev.id}_${ev.at}`;
    if (!shown.has(key)) {
      shown.add(key);
      visibleEvents.value.push({ id: ev.id, at: ev.at });
      window.setTimeout(() => {
        const idx = visibleEvents.value.findIndex((v) => v.id === ev.id && v.at === ev.at);
        if (idx !== -1) visibleEvents.value.splice(idx, 1);
      }, 5000);
    }
  });
}

onMounted(() => {
  processEvents();
});

watch(
  () => props.events?.length ?? 0,
  () => {
    processEvents();
  },
);
</script>

<style scoped>
.achievement-toast-host {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 90;
  pointer-events: none;
}
.toast-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-end;
}
.ach-toast {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px 14px 14px;
  min-width: 260px;
  max-width: 360px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(18px) saturate(180%);
  -webkit-backdrop-filter: blur(18px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow:
    0 12px 36px rgba(0, 0, 0, 0.38),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset;
  color: var(--kb-foreground);
}
.ach-icon-wrap {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.28);
  flex-shrink: 0;
}
.ach-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.ach-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.01em;
  line-height: 1.2;
}
.ach-tag {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

.ach-fly-enter-active {
  transition:
    opacity 0.5s cubic-bezier(0.16, 1, 0.3, 1),
    transform 1.5s cubic-bezier(0.16, 1, 0.3, 1);
}
.ach-fly-leave-active {
  transition:
    opacity 1s ease,
    transform 1s ease;
}
.ach-fly-enter-from {
  opacity: 0;
  transform: translateX(120%) translateY(-12px);
}
.ach-fly-leave-to {
  opacity: 0;
  transform: translateX(32px) scale(0.92);
}

@media (max-width: 640px) {
  .achievement-toast-host {
    left: 12px;
    right: 12px;
    top: 72px;
  }
  .toast-stack {
    align-items: stretch;
  }
  .ach-toast {
    min-width: 0;
    max-width: none;
  }
}
</style>
