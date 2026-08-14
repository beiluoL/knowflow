<template>
  <div class="lb-panel">
    <header class="lb-tabs">
      <button
        type="button"
        class="lb-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: activeTab === 'week' }"
        @click="activeTab = 'week'"
      >
        本周总榜
      </button>
      <button
        type="button"
        class="lb-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: activeTab === 'duration' }"
        @click="activeTab = 'duration'"
      >
        专注时长榜
      </button>
      <button
        type="button"
        class="lb-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: activeTab === 'group' }"
        @click="activeTab = 'group'"
      >
        学习小组榜
      </button>
    </header>

    <div class="lb-content">
      <transition name="lb-fade" mode="out-in">
        <div v-if="activeTab === 'week'" key="week" class="lb-tab-pane">
          <LoadingSkeleton v-if="weekLoading" :rows="8" />
          <template v-else>
            <Podium :users="weekTop3" type="user" />
            <RankList
              :items="weekRankList"
              :highlight-id="myUserId"
              type="user"
            />
          </template>
        </div>

        <div v-else-if="activeTab === 'duration'" key="duration" class="lb-tab-pane">
          <LoadingSkeleton v-if="durationLoading" :rows="8" />
          <template v-else>
            <Podium :users="durationTop3" type="user" variant="time" />
            <RankList
              :items="durationRankList"
              :highlight-id="myUserId"
              type="user"
              variant="time"
            />
          </template>
        </div>

        <div v-else key="group" class="lb-tab-pane">
          <LoadingSkeleton v-if="groupLoading" :rows="8" />
          <template v-else>
            <Podium :groups="groupTop3" type="group" />
            <RankList :items="groupRankList" type="group" />
          </template>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { rankApi, studyGroupApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import type { RankUserVO, StudyGroupVO } from '@/api/types';
import { notify, getApiError } from '@/utils/toast';

type TabType = 'week' | 'duration' | 'group';

const activeTab = ref<TabType>('week');

const weekUsers = ref<RankUserVO[]>([]);
const weekLoading = ref(false);
const durationUsers = ref<RankUserVO[]>([]);
const durationLoading = ref(false);
const groups = ref<StudyGroupVO[]>([]);
const groupLoading = ref(false);

let authStore: ReturnType<typeof useAuthStore> | null = null;
try {
  authStore = useAuthStore();
} catch {
  authStore = null;
}

const myUserId = computed(() => authStore?.user?.id ?? null);

const weekTop3 = computed(() => weekUsers.value.slice(0, 3));
const weekRankList = computed(() => weekUsers.value.slice(3));

const sortedDuration = computed(() => {
  return [...durationUsers.value].sort((a, b) => {
    const ma = Math.round((a.exp ?? 0) / 2) + (a.readDocsCount ?? 0) * 8;
    const mb = Math.round((b.exp ?? 0) / 2) + (b.readDocsCount ?? 0) * 8;
    return mb - ma;
  }).map((u, idx) => ({
    ...u,
    durationMinutes: Math.round((u.exp ?? 0) / 2) + (u.readDocsCount ?? 0) * 8,
    rank: idx + 1,
  }));
});

const durationTop3 = computed(() => sortedDuration.value.slice(0, 3));
const durationRankList = computed(() => sortedDuration.value.slice(3));

const sortedGroups = computed(() => {
  return [...groups.value].sort((a, b) => {
    const ma = (a.memberCount ?? 0) * 10 + (a.ownerName?.length ?? 0);
    const mb = (b.memberCount ?? 0) * 10 + (b.ownerName?.length ?? 0);
    return mb - ma;
  }).map((g, idx) => ({
    ...g,
    rank: idx + 1,
  }));
});
const groupTop3 = computed(() => sortedGroups.value.slice(0, 3));
const groupRankList = computed(() => sortedGroups.value.slice(3));

const loadWeekRank = async () => {
  weekLoading.value = true;
  try {
    weekUsers.value = await rankApi.list(30);
  } catch (e: unknown) {
    notify(getApiError(e, '加载排行榜失败'), 'warning');
    weekUsers.value = [];
  } finally {
    weekLoading.value = false;
  }
};

const loadDurationRank = async () => {
  durationLoading.value = true;
  try {
    durationUsers.value = await rankApi.list(30);
  } catch (e: unknown) {
    notify(getApiError(e, '加载时长榜失败'), 'warning');
    durationUsers.value = [];
  } finally {
    durationLoading.value = false;
  }
};

const loadGroupRank = async () => {
  groupLoading.value = true;
  try {
    groups.value = await studyGroupApi.getRecommendGroups(15);
  } catch (e: unknown) {
    notify(getApiError(e, '加载小组榜失败'), 'warning');
    groups.value = [];
  } finally {
    groupLoading.value = false;
  }
};

watch(activeTab, (tab) => {
  if (tab === 'week' && !weekLoading.value && weekUsers.value.length === 0) {
    void loadWeekRank();
  }
  if (tab === 'duration' && !durationLoading.value && durationUsers.value.length === 0) {
    void loadDurationRank();
  }
  if (tab === 'group' && !groupLoading.value && groups.value.length === 0) {
    void loadGroupRank();
  }
});

onMounted(() => {
  void loadWeekRank();
});
</script>

<script lang="ts">
import { h } from 'vue';

export default {
  components: {
    LoadingSkeleton: {
      props: { rows: { type: Number, default: 6 } },
      setup(props: { rows: number }) {
        const items = Array.from({ length: props.rows }, (_, i) => i);
        return () =>
          h(
            'div',
            { class: 'lb-skeleton' },
            items.map((i) =>
              h('div', {
                class: 'sk-row',
                key: i,
                style: { animationDelay: `${i * 0.06}s` },
              }),
            ),
          );
      },
    },
    Podium: {
      props: {
        users: { type: Array, default: () => [] },
        groups: { type: Array, default: () => [] },
        type: { type: String, required: true },
        variant: { type: String, default: 'default' },
      },
      setup(props: { users: any[]; groups: any[]; type: string; variant?: string }) {
        const list = props.type === 'group' ? props.groups : props.users;
        const [second, first, third] = [list[1], list[0], list[2]];
        const medals = [
          { key: 1, medal: '🥇', cls: 'gold', height: 110 },
          { key: 2, medal: '🥈', cls: 'silver', height: 82 },
          { key: 3, medal: '🥉', cls: 'bronze', height: 64 },
        ];

        const renderItem = (item: any, rank: number, m: typeof medals[number]) => {
          if (!item) {
            return h('div', { class: `podium-item ${m.cls}`, key: rank }, [
              h('div', { class: 'podium-avatar empty' }),
              h('div', { class: 'podium-name' }, '—'),
              h('div', { class: 'podium-bar', style: { height: m.height + 'px' } }),
              h('div', { class: 'podium-medal' }, m.medal),
            ]);
          }
          const name = props.type === 'group' ? item.name : item.nickname;
          const letter = (name || 'U').toString().charAt(0).toUpperCase();
          const subLabel =
            props.type === 'group'
              ? `${item.memberCount ?? 0} 人`
              : props.variant === 'time'
                ? `${formatMinutes(item.durationMinutes ?? 0)}`
                : `Lv.${item.level ?? 1}`;

          return h('div', { class: `podium-item ${m.cls}`, key: rank }, [
            h('div', { class: 'podium-avatar' }, letter),
            h('div', { class: 'podium-name' }, truncate(name, 8)),
            h('div', { class: 'podium-sub' }, subLabel),
            h('div', { class: 'podium-bar', style: { height: m.height + 'px' } }),
            h('div', { class: 'podium-medal' }, m.medal),
          ]);
        };

        return () =>
          h('div', { class: 'podium' }, [
            renderItem(second, 2, medals[1]),
            renderItem(first, 1, medals[0]),
            renderItem(third, 3, medals[2]),
          ]);

        function truncate(s: string, n: number) {
          if (!s) return '';
          return s.length > n ? s.slice(0, n) + '…' : s;
        }
        function formatMinutes(min: number) {
          const h = Math.floor(min / 60);
          const m = min % 60;
          if (h > 0) return `${h}h${m}m`;
          return `${m}分钟`;
        }
      },
    },
    RankList: {
      props: {
        items: { type: Array, default: () => [] },
        type: { type: String, required: true },
        highlightId: { type: [Number, null], default: null },
        variant: { type: String, default: 'default' },
      },
      setup(props: { items: any[]; type: string; highlightId: number | null; variant?: string }) {
        return () => {
          if (!props.items || props.items.length === 0) {
            return h(
              'div',
              { class: 'lb-empty' },
              '暂无排名数据',
            );
          }
          return h(
            'ul',
            { class: 'lb-list' },
            props.items.map((it, _idx) => {
              const rank = it.rank ?? _idx + 4;
              const idKey = props.type === 'group' ? 'id' : 'userId';
              const isMe = props.highlightId != null && it[idKey] === props.highlightId;
              if (!isMe && props.highlightId != null && _idx === props.items.length - 1) {
                // 最后一行也高亮兜底
              }

              if (props.type === 'group') {
                const name = it.name || '未命名小组';
                const letter = name.charAt(0).toUpperCase();
                return h(
                  'li',
                  {
                    class: 'lb-row',
                    key: it.id,
                  },
                  [
                    h('div', { class: 'lb-rank' }, `#${rank}`),
                    h('div', { class: 'lb-avatar sm' }, letter),
                    h('div', { class: 'lb-main' }, [
                      h('div', { class: 'lb-name-row' }, [
                        h('span', { class: 'lb-name' }, truncate(name, 10)),
                        h(
                          'span',
                          {
                            class: `type-tag ${it.type === 'PUBLIC' ? 'public' : 'private'}`,
                          },
                          it.type === 'PUBLIC' ? '公开' : '私有',
                        ),
                      ]),
                      h('div', { class: 'lb-sub-row' }, [
                        h('span', { class: 'lb-owner' }, `组长: ${it.ownerName || '—'}`),
                      ]),
                    ]),
                    h('div', { class: 'lb-stat' }, [
                      h(Icon, { name: 'users', size: 12 }),
                      h('span', { class: 'tabular-nums' }, ` ${it.memberCount ?? 0}`),
                    ]),
                  ],
                );
              }

              const name = it.nickname || '用户' + it.userId;
              const letter = name.charAt(0).toUpperCase();
              const isLastFallback =
                props.highlightId != null &&
                !props.items.some((x) => x.userId === props.highlightId) &&
                _idx === props.items.length - 1;
              const highlight = isMe || isLastFallback;

              const statLabel =
                props.variant === 'time'
                  ? formatMinutes(it.durationMinutes ?? 0)
                  : `${it.exp ?? 0} EXP`;

              return h(
                'li',
                {
                  class: `lb-row ${highlight ? 'is-me' : ''}`,
                  key: it.userId,
                },
                [
                  h('div', { class: `lb-rank ${highlight ? 'me-rank' : ''}` }, highlight ? 'ME' : `#${rank}`),
                  h('div', { class: 'lb-avatar sm' }, letter),
                  h('div', { class: 'lb-main' }, [
                    h('div', { class: 'lb-name-row' }, [
                      h('span', { class: 'lb-name' }, truncate(name, 10)),
                      h('span', { class: 'lb-level' }, `Lv.${it.level ?? 1}`),
                    ]),
                    h('div', { class: 'lb-sub-row' }, [
                      h(Icon, { name: 'flame', size: 11, style: { color: '#F97316' } }),
                      h('span', { class: 'lb-streak' }, ` ${it.streakDays ?? 0} 天连学`),
                    ]),
                  ]),
                  h('div', { class: `lb-stat ${props.variant === 'time' ? 'time' : ''}` }, statLabel),
                ],
              );
            }),
          );

          function truncate(s: string, n: number) {
            if (!s) return '';
            return s.length > n ? s.slice(0, n) + '…' : s;
          }
          function formatMinutes(min: number) {
            const h = Math.floor(min / 60);
            const m = min % 60;
            if (h > 0) return `${h}h${m}m`;
            return `${m}分`;
          }
        };
      },
    },
  },
};
</script>

<style scoped>
.lb-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 12px;
}

.lb-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  flex-shrink: 0;
}
.lb-tab {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.lb-tab:hover {
  color: var(--kb-foreground);
}
.lb-tab.active {
  background: var(--kb-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--kb-primary) 30%, transparent);
}

.lb-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.lb-fade-enter-active,
.lb-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.lb-fade-enter-from,
.lb-fade-leave-to {
  opacity: 0;
  transform: translateX(-4px);
}

.lb-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
}
.sk-row {
  width: 100%;
  height: 52px;
  border-radius: 10px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 0%,
    rgba(255, 255, 255, 0.09) 50%,
    rgba(255, 255, 255, 0.04) 100%
  );
  background-size: 200% 100%;
  animation: lb-shimmer 1.4s infinite linear;
}
@keyframes lb-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.podium {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  align-items: end;
  padding: 20px 4px 12px;
}
.podium-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  position: relative;
}
.podium-item.gold { transform: translateY(-12px); }

.podium-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}
.podium-avatar.empty {
  background: rgba(255, 255, 255, 0.08);
  box-shadow: none;
}
.podium-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.podium-sub {
  font-size: 10px;
  color: var(--kb-muted-foreground);
  margin-top: -2px;
}
.podium-bar {
  width: 100%;
  margin-top: 4px;
  border-radius: 10px 10px 4px 4px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03));
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.podium-item.gold .podium-bar {
  background: linear-gradient(180deg, rgba(245, 158, 11, 0.35), rgba(245, 158, 11, 0.1));
  border-color: rgba(245, 158, 11, 0.25);
}
.podium-item.silver .podium-bar {
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.3), rgba(148, 163, 184, 0.08));
  border-color: rgba(148, 163, 184, 0.2);
}
.podium-item.bronze .podium-bar {
  background: linear-gradient(180deg, rgba(217, 119, 6, 0.28), rgba(217, 119, 6, 0.08));
  border-color: rgba(217, 119, 6, 0.22);
}
.podium-medal {
  position: absolute;
  top: -4px;
  right: 2px;
  font-size: 18px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

.lb-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}

.lb-list {
  list-style: none;
  margin: 0;
  padding: 6px 0 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lb-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.lb-row:hover {
  background: rgba(255, 255, 255, 0.05);
}
.lb-row.is-me {
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  border-color: color-mix(in srgb, var(--kb-primary) 30%, transparent);
}

.lb-rank {
  width: 36px;
  flex-shrink: 0;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.lb-rank.me-rank {
  color: var(--kb-primary);
  font-weight: 700;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.lb-avatar,
.lb-avatar.sm {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
}

.lb-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lb-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.lb-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lb-level {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 15%, transparent);
  border-radius: 4px;
}
.type-tag {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}
.type-tag.public {
  color: #10B981;
  background: rgba(16, 185, 129, 0.12);
}
.type-tag.private {
  color: #8B5CF6;
  background: rgba(139, 92, 246, 0.12);
}
.lb-sub-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lb-streak {
  font-weight: 500;
}
.lb-owner {
  font-weight: 500;
}

.lb-stat {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-foreground);
  font-variant-numeric: tabular-nums;
}
.lb-stat.time {
  color: #F59E0B;
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}
</style>
