<template>
  <div class="db-settings">
    <header class="db-settings__header">
      <div>
        <h1 class="db-settings__title">
          <Icon name="database" size="lg" />
          数据库设置
        </h1>
        <p class="db-settings__desc">
          切换 H2（开发测试）与 MySQL（生产）数据源。切换前会自动校验目标库连通性，失败则保持当前数据库不变。
        </p>
      </div>
      <button class="db-btn db-btn--ghost" :disabled="loading" @click="loadStatus">
        <Icon name="refresh-cw" size="sm" />
        刷新
      </button>
    </header>

    <!-- 当前运行状态 -->
    <section class="db-card">
      <h2 class="db-card__title">当前运行状态</h2>
      <div v-if="loading && !status" class="db-empty">加载中…</div>
      <div v-else-if="status" class="db-status">
        <div class="db-status__badge" :class="status.healthy ? 'is-healthy' : 'is-error'">
          <Icon :name="status.healthy ? 'check-circle' : 'alert-circle'" size="sm" />
          {{ status.healthy ? '连接正常' : '连接异常' }}
        </div>
        <dl class="db-grid">
          <div class="db-grid__item">
            <dt>数据库类型</dt>
            <dd>{{ status.displayName }}</dd>
          </div>
          <div class="db-grid__item">
            <dt>产品版本</dt>
            <dd>{{ status.productName || '-' }} {{ status.productVersion || '' }}</dd>
          </div>
          <div class="db-grid__item">
            <dt>业务表数量</dt>
            <dd>{{ status.tableCount ?? '-' }}</dd>
          </div>
          <div class="db-grid__item">
            <dt>连接池（活跃/空闲/总数）</dt>
            <dd>
              {{ status.activeConnections ?? '-' }} / {{ status.idleConnections ?? '-' }} /
              {{ status.totalConnections ?? '-' }}
            </dd>
          </div>
          <div class="db-grid__item db-grid__item--wide">
            <dt>连接地址</dt>
            <dd class="db-mono">{{ status.url }}</dd>
          </div>
        </dl>
        <p v-if="!status.healthy && status.message" class="db-alert db-alert--error">
          {{ status.message }}
        </p>
        <p v-if="status.allowRuntimeSwitch === false" class="db-alert db-alert--warn">
          当前已禁用运行时切换（allow-runtime-switch=false），如需切换请修改配置后重启服务。
        </p>
      </div>
    </section>

    <!-- 切换数据库 -->
    <section v-if="status" class="db-card">
      <h2 class="db-card__title">切换数据库</h2>

      <div class="db-options">
        <button
          v-for="opt in status.options"
          :key="opt.code"
          class="db-option"
          :class="{ 'is-selected': form.type === opt.code, 'is-active': opt.active }"
          type="button"
          @click="selectType(opt)"
        >
          <Icon :name="opt.code === 'mysql' ? 'server' : 'zap'" size="md" />
          <span class="db-option__name">{{ opt.displayName }}</span>
          <span v-if="opt.active" class="db-option__tag">运行中</span>
        </button>
      </div>

      <div class="db-form">
        <label class="db-field">
          <span class="db-field__label">连接地址（JDBC URL）</span>
          <input v-model.trim="form.url" class="db-input" type="text" placeholder="留空则使用服务端配置" />
        </label>
        <div class="db-field-row">
          <label class="db-field">
            <span class="db-field__label">用户名</span>
            <input v-model.trim="form.username" class="db-input" type="text" placeholder="留空沿用服务端配置" />
          </label>
          <label class="db-field">
            <span class="db-field__label">密码</span>
            <input v-model="form.password" class="db-input" type="password" placeholder="留空沿用服务端配置" />
          </label>
        </div>
        <label class="db-field db-field--check">
          <input v-model="form.initSchema" type="checkbox" />
          <span>
            切换后执行初始化脚本（建表 + 演示数据）
            <em class="db-hint">目标库为空时首次切换请勾选；已有数据时勾选会重复写入</em>
          </span>
        </label>
      </div>

      <div class="db-actions">
        <button class="db-btn db-btn--ghost" :disabled="testing || switching" @click="handleTest">
          <Icon name="link" size="sm" />
          {{ testing ? '测试中…' : '测试连接' }}
        </button>
        <button
          class="db-btn db-btn--primary"
          :disabled="switching || testing || !canSwitch"
          @click="handleSwitch"
        >
          <Icon name="save" size="sm" />
          {{ switching ? '切换中…' : '切换并保存' }}
        </button>
      </div>

      <p v-if="testResult" class="db-alert" :class="testResult.success ? 'db-alert--ok' : 'db-alert--error'">
        <template v-if="testResult.success">
          连接成功：{{ testResult.productName }} {{ testResult.productVersion }}，
          现有业务表 {{ testResult.tableCount }} 张，耗时 {{ testResult.costMs }}ms
        </template>
        <template v-else>连接失败：{{ testResult.message }}（耗时 {{ testResult.costMs }}ms）</template>
      </p>
      <p v-if="switchMessage" class="db-alert" :class="switchOk ? 'db-alert--ok' : 'db-alert--error'">
        {{ switchMessage }}
      </p>
    </section>

    <!-- 初始化脚本 -->
    <section v-if="status" class="db-card">
      <h2 class="db-card__title">初始化当前数据库</h2>
      <p class="db-card__desc">
        对当前生效的数据库强制执行方言脚本（db/{{ status.currentType }}/schema.sql 与 data.sql）。
        用于新库建表或重建演示数据，请谨慎在生产环境使用。
      </p>
      <button class="db-btn db-btn--ghost" :disabled="initializing" @click="handleInit">
        <Icon name="cpu" size="sm" />
        {{ initializing ? '执行中…' : '执行初始化脚本' }}
      </button>
      <p v-if="initMessage" class="db-alert" :class="initOk ? 'db-alert--ok' : 'db-alert--error'">
        {{ initMessage }}
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { databaseApi } from '@/api/database';
import type { DbOptionVO, DbStatusVO, DbTestResult } from '@/api/database';

const status = ref<DbStatusVO | null>(null);
const loading = ref(false);
const testing = ref(false);
const switching = ref(false);
const initializing = ref(false);

const testResult = ref<DbTestResult | null>(null);
const switchMessage = ref('');
const switchOk = ref(false);
const initMessage = ref('');
const initOk = ref(false);

const form = reactive({
  type: '',
  url: '',
  username: '',
  password: '',
  initSchema: false,
});

// 仅当选择了类型且未禁用运行时切换时才允许提交
const canSwitch = computed(
  () => Boolean(form.type) && status.value?.allowRuntimeSwitch !== false,
);

/** 拉取数据库状态并同步表单默认值 */
async function loadStatus(): Promise<void> {
  loading.value = true;
  try {
    // apiGet/apiPost 已由响应拦截器解包，直接返回业务数据本身
    const data = await databaseApi.status();
    status.value = data;
    if (!form.type) {
      const active = data.options.find((o) => o.active) ?? data.options[0];
      if (active) selectType(active);
    }
  } catch (e) {
    switchOk.value = false;
    switchMessage.value = `加载状态失败：${(e as Error).message}`;
  } finally {
    loading.value = false;
  }
}

/** 选中某个数据库类型，回填其已配置的连接信息（密码不回传，保持为空） */
function selectType(opt: DbOptionVO): void {
  form.type = opt.code;
  form.url = opt.url ?? '';
  form.username = opt.username ?? '';
  form.password = '';
  testResult.value = null;
  switchMessage.value = '';
}

/** 构造请求体：空字符串视为「沿用服务端配置」，避免误清空 */
function buildPayload(withInit = false) {
  return {
    type: form.type,
    url: form.url || undefined,
    username: form.username || undefined,
    password: form.password || undefined,
    initSchema: withInit ? form.initSchema : undefined,
  };
}

async function handleTest(): Promise<void> {
  testing.value = true;
  testResult.value = null;
  try {
    testResult.value = await databaseApi.test(buildPayload());
  } catch (e) {
    testResult.value = {
      success: false,
      type: form.type,
      url: form.url,
      costMs: 0,
      message: (e as Error).message,
    };
  } finally {
    testing.value = false;
  }
}

async function handleSwitch(): Promise<void> {
  const target = status.value?.options.find((o) => o.code === form.type);
  const confirmed = window.confirm(
    `确认将数据库切换为「${target?.displayName ?? form.type}」？\n` +
      '切换后新请求将立即使用目标库，旧连接池会在 30 秒后释放。',
  );
  if (!confirmed) return;

  switching.value = true;
  switchMessage.value = '';
  try {
    const data = await databaseApi.switchDb(buildPayload(true));
    status.value = data;
    switchOk.value = true;
    switchMessage.value = `已切换为 ${data.displayName}`;
    form.password = '';
  } catch (e) {
    switchOk.value = false;
    switchMessage.value = `切换失败：${(e as Error).message}`;
  } finally {
    switching.value = false;
  }
}

async function handleInit(): Promise<void> {
  const confirmed = window.confirm(
    '确认对当前数据库执行初始化脚本？\n若库中已有数据，可能产生重复记录或主键冲突。',
  );
  if (!confirmed) return;

  initializing.value = true;
  initMessage.value = '';
  try {
    const data = await databaseApi.init();
    initOk.value = data.success;
    initMessage.value = data.success
      ? `初始化完成，当前业务表 ${data.tableCount} 张，耗时 ${data.costMs}ms`
      : `初始化失败：${data.message}`;
    await loadStatus();
  } catch (e) {
    initOk.value = false;
    initMessage.value = `初始化失败：${(e as Error).message}`;
  } finally {
    initializing.value = false;
  }
}

onMounted(loadStatus);
</script>

<style scoped>
.db-settings {
  padding: 24px;
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.db-settings__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.db-settings__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-text);
}

.db-settings__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-text-secondary);
}

.db-card {
  background: var(--kb-surface);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg, 12px);
  padding: 20px;
}

.db-card__title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-text);
}

.db-card__desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-text-secondary);
}

.db-empty {
  font-size: 13px;
  color: var(--kb-text-secondary);
}

.db-status__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 14px;
}

.db-status__badge.is-healthy {
  background: var(--kb-success-bg, rgba(34, 197, 94, 0.12));
  color: var(--kb-success, #16a34a);
}

.db-status__badge.is-error {
  background: var(--kb-danger-bg, rgba(239, 68, 68, 0.12));
  color: var(--kb-danger, #dc2626);
}

.db-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin: 0;
}

.db-grid__item--wide {
  grid-column: 1 / -1;
}

.db-grid dt {
  font-size: 12px;
  color: var(--kb-text-secondary);
  margin-bottom: 4px;
}

.db-grid dd {
  margin: 0;
  font-size: 14px;
  color: var(--kb-text);
  word-break: break-all;
}

.db-mono {
  font-family: var(--kb-font-mono, ui-monospace, monospace);
  font-size: 12px;
}

.db-options {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.db-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius, 8px);
  background: var(--kb-bg);
  color: var(--kb-text);
  cursor: pointer;
  transition: all 0.15s ease;
}

.db-option:hover {
  border-color: var(--kb-primary);
}

.db-option.is-selected {
  border-color: var(--kb-primary);
  background: var(--kb-primary-bg, rgba(59, 130, 246, 0.08));
}

.db-option__name {
  font-size: 14px;
  font-weight: 500;
}

.db-option__tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kb-success-bg, rgba(34, 197, 94, 0.12));
  color: var(--kb-success, #16a34a);
}

.db-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.db-field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.db-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.db-field__label {
  font-size: 12px;
  color: var(--kb-text-secondary);
}

.db-field--check {
  flex-direction: row;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-text);
}

.db-hint {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-style: normal;
  color: var(--kb-text-secondary);
}

.db-input {
  padding: 8px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius, 8px);
  background: var(--kb-bg);
  color: var(--kb-text);
  font-size: 13px;
  outline: none;
}

.db-input:focus {
  border-color: var(--kb-primary);
}

.db-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}

.db-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--kb-radius, 8px);
  border: 1px solid var(--kb-border);
  background: var(--kb-bg);
  color: var(--kb-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.db-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
}

.db-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.db-btn--primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: #fff;
}

.db-alert {
  margin: 14px 0 0;
  padding: 10px 12px;
  border-radius: var(--kb-radius, 8px);
  font-size: 13px;
  line-height: 1.6;
}

.db-alert--ok {
  background: var(--kb-success-bg, rgba(34, 197, 94, 0.12));
  color: var(--kb-success, #16a34a);
}

.db-alert--error {
  background: var(--kb-danger-bg, rgba(239, 68, 68, 0.12));
  color: var(--kb-danger, #dc2626);
}

.db-alert--warn {
  background: var(--kb-warning-bg, rgba(245, 158, 11, 0.12));
  color: var(--kb-warning, #d97706);
}

@media (max-width: 640px) {
  .db-field-row {
    grid-template-columns: 1fr;
  }
}
</style>
