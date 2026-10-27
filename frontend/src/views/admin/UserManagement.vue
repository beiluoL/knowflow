<template>
  <div class="admin-users">
    <PageHeader title="用户管理" description="检索账号、维护用户信息与角色。数据来自管理员接口。"><template #actions><Button icon-name="user-plus" @click="openCreate">新增用户</Button></template></PageHeader>
    <section class="user-panel" aria-label="用户列表">
      <form class="user-toolbar" role="search" @submit.prevent="applyFilters">
        <label class="user-search"><Icon name="search" :size="18" aria-hidden="true" /><input class="kb-input kb-form-control" v-model="searchQuery" type="search" aria-label="搜索用户" placeholder="用户名、昵称或邮箱" @keydown.enter="onSearchEnter" /></label>
        <Select v-model="selectedRole" aria-label="角色筛选" :options="roleFilterOptions" @change="applyFilters" />
        <Button type="submit" :loading="loading">搜索</Button><Button variant="ghost" @click="resetFilters">重置</Button>
      </form>
      <p class="user-result-count" role="status" aria-live="polite">{{ loading ? '正在加载用户…' : error ? '列表尚未加载' : `共 ${total} 个匹配账号 · 第 ${pageNum} 页` }}</p>
      <StateView :status="status" :error-title="problem?.title || '用户加载失败'" :error-description="problem?.scope || ''" :error-hint="problem?.hint" :show-retry="problem?.retryable ?? true" empty-title="没有匹配的用户" empty-description="调整搜索词或角色条件后重试。" @retry="loadUsers">
        <div class="user-table-scroll" tabindex="0" aria-label="用户表格，可横向滚动"><table><caption class="sr-only">匹配的用户账号</caption><thead><tr><th>用户</th><th>邮箱</th><th>角色</th><th>等级</th><th>累计学习时长</th><th>注册日期</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td><div class="user-identity"><Avatar :src="user.avatar" :name="user.username" size="sm" /><span><strong>{{ user.nickname || user.username }}</strong><small>{{ user.username }} · #{{ user.id }}</small></span></div></td><td>{{ user.email || '—' }}</td><td><Badge :variant="user.role === 'ADMIN' ? 'primary' : 'default'">{{ user.role === 'ADMIN' ? '管理员' : user.role === 'USER' ? '普通用户' : user.role || '未提供' }}</Badge></td><td>{{ user.level == null ? '—' : `Lv.${user.level}` }}</td><td>{{ user.totalStudyHours == null ? '未提供' : `${user.totalStudyHours} 小时` }}</td><td>{{ user.createTime?.slice(0,10) || '未提供' }}</td><td><div class="user-row-actions"><Button variant="text" size="sm" :aria-label="`编辑用户 ${user.username}`" @click="openEdit(user)">编辑</Button><Button variant="text" size="sm" :disabled="deletingId != null" :aria-label="`删除用户 ${user.username}`" @click="removeUser(user)">删除</Button></div></td></tr></tbody></table></div>
      </StateView>
      <Pagination v-if="status === 'ready'" :page-num="pageNum" :page-size="pageSize" :total="total" @change="goPage" />
      <p class="user-source-note">学习时长为服务端累计字段；等级不换算为学习进度或掌握度。</p>
    </section>
    <div v-if="showModal" class="user-modal-mask" @click.self="requestClose">
      <section ref="panel" class="user-modal" role="dialog" aria-modal="true" aria-labelledby="user-form-title" tabindex="-1" @keydown="onFormKey">
        <header><div><h2 id="user-form-title">{{ editingId == null ? '新增用户' : '编辑用户' }}</h2><p>必填信息与角色由原有服务端规则校验。</p></div><Button variant="ghost" icon-name="x" icon-only aria-label="关闭用户表单" :disabled="saving || confirming" @click="requestClose" /></header>
        <form aria-label="用户信息表单" @submit.prevent="save">
          <fieldset :disabled="saving || confirming"><label>用户名 <span>*</span><input class="kb-input kb-form-control" v-model="form.username" autocomplete="username" required :disabled="editingId != null" maxlength="50" /></label><label>邮箱<input class="kb-input kb-form-control" v-model="form.email" type="email" autocomplete="email" /></label><label>昵称<input class="kb-input kb-form-control" v-model="form.nickname" autocomplete="nickname" /></label><label>角色<Select v-model="form.role" aria-label="用户角色" :options="roleOptions" :disabled="saving || confirming" /></label><label v-if="editingId == null">初始密码 <span>*</span><input class="kb-input kb-form-control" v-model="form.password" type="password" autocomplete="new-password" required /></label></fieldset>
          <p v-if="formError" class="user-form-error" role="alert">{{ formError }}</p>
          <footer><span role="status" aria-live="polite">{{ saving ? '正在保存，请等待服务端确认…' : dirty ? '有未保存修改' : '' }}</span><Button variant="secondary" :disabled="saving || confirming" @click="requestClose">取消</Button><Button type="submit" :loading="saving" :disabled="confirming">保存</Button></footer>
        </form>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import Button from '@/components/ui/Button.vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import Badge from '@/components/ui/Badge.vue'
import Avatar from '@/components/ui/Avatar.vue'
import StateView from '@/components/ui/StateView.vue'
import Pagination from '@/components/ui/Pagination.vue'
import { adminApi } from '@/api'
import type { UserVO } from '@/api/types'
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { describePageError } from '@/utils/error-message'
const roleOptions = [{ value: 'USER', label: '普通用户' }, { value: 'ADMIN', label: '管理员' }]
const roleFilterOptions = [{ value: '', label: '全部角色' }, ...roleOptions]
const users = ref<UserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const searchQuery = ref('')
const selectedRole = ref('')
const appliedKeyword = ref('')
const appliedRole = ref('')
const loading = ref(true)
const error = ref<unknown>(null)
const problem = computed(() => error.value ? describePageError(error.value, { subject: '用户加载失败' }) : null)
const status = computed(() => loading.value ? 'loading' : error.value ? 'error' : users.value.length ? 'ready' : 'empty')
let sequence = 0
async function loadUsers() {
  const current = ++sequence
  loading.value = true; error.value = null
  try {
    // 分页、关键词和角色均由后端处理，避免只下载前100条再切页导致漏数据。
    const params: { pageNum: number; pageSize: number; keyword?: string; role?: string } = { pageNum: pageNum.value, pageSize }
    if (appliedKeyword.value) params.keyword = appliedKeyword.value
    if (appliedRole.value) params.role = appliedRole.value
    const result = await adminApi.users(params)
    if (current !== sequence) return
    users.value = result.records; total.value = result.total
    if (!users.value.length && pageNum.value > 1 && total.value > 0) { pageNum.value = Math.max(1, Math.ceil(total.value / pageSize)); await loadUsers() }
  } catch (e) { if (current === sequence) error.value = e }
  finally { if (current === sequence) loading.value = false }
}
function applyFilters() { appliedKeyword.value = searchQuery.value.trim(); appliedRole.value = selectedRole.value; pageNum.value = 1; void loadUsers() }
function resetFilters() { searchQuery.value = ''; selectedRole.value = ''; applyFilters() }
function goPage(page: number) { if (loading.value) return; pageNum.value = page; void loadUsers() }
function onSearchEnter(event: KeyboardEvent) { if (event.isComposing || event.keyCode === 229) event.preventDefault() }
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const confirming = ref(false)
const deletingId = ref<number | null>(null)
const formError = ref('')
const blank = () => ({ username: '', email: '', nickname: '', role: 'USER', password: '' })
const form = ref(blank())
const baseline = ref('')
const dirty = computed(() => JSON.stringify(form.value) !== baseline.value)
const panel = ref<HTMLElement | null>(null)
let trigger: HTMLElement | null = null
let closing = false
async function showForm() { trigger = document.activeElement as HTMLElement; baseline.value = JSON.stringify(form.value); formError.value = ''; showModal.value = true; await nextTick(); panel.value?.querySelector<HTMLElement>('input:not(:disabled)')?.focus() }
function openCreate() { editingId.value = null; form.value = blank(); void showForm() }
function openEdit(user: UserVO) { editingId.value = user.id; form.value = { username: user.username, email: user.email || '', nickname: user.nickname || '', role: user.role || 'USER', password: '' }; void showForm() }
async function confirmUserAction(message: string) {
  if (confirming.value) return false
  confirming.value = true
  try {
    return await confirmDialog(message, {title:'放弃未保存的修改？',confirmText:'放弃修改',cancelText:'返回编辑',icon:'log-out'})
  } finally { confirming.value = false }
}
async function canDiscard() {
  if (saving.value || closing || confirming.value || deletingId.value != null) return false
  if (!showModal.value || !dirty.value) return true
  closing = true
  try { return await confirmUserAction('表单有未保存修改，确定放弃？') }
  finally { closing = false }
}
async function closeForm() { showModal.value = false; formError.value = ''; form.value = blank(); await nextTick(); if (trigger?.isConnected) trigger.focus() }
async function requestClose() { if (await canDiscard()) await closeForm() }
function onFormKey(event: KeyboardEvent) {
  if (document.querySelector('.dlg-card--confirmation')) return
  if (panel.value?.querySelector('[aria-haspopup="listbox"][aria-expanded="true"]')) return
  if (event.key === 'Escape') { event.preventDefault(); void requestClose(); return }
  if (event.key !== 'Tab') return
  const nodes = Array.from(panel.value?.querySelectorAll<HTMLElement>('input:not(:disabled),select:not(:disabled),button:not(:disabled)') || [])
  const first = nodes[0], last = nodes[nodes.length-1]
  if (!nodes.length) { event.preventDefault(); panel.value?.focus(); return }
  if (!nodes.includes(document.activeElement as HTMLElement)) { event.preventDefault(); (event.shiftKey ? last : first)?.focus(); return }
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
async function save() {
  if (saving.value || confirming.value || !showModal.value) return
  formError.value = ''
  if (!form.value.username.trim()) { formError.value = '请填写用户名'; return }
  if (editingId.value == null && !form.value.password.trim()) { formError.value = '请填写初始密码'; return }
  saving.value = true
  await nextTick(); panel.value?.focus()
  try {
    if (editingId.value != null) await adminApi.updateUser(editingId.value, { email: form.value.email, nickname: form.value.nickname, role: form.value.role })
    else await adminApi.createUser({ ...form.value, username: form.value.username.trim() })
    // 只有服务成功才关闭；失败时字段与草稿都保留，取消不能中断未确认的写请求。
    notify(editingId.value == null ? '用户已创建' : '用户信息已保存', 'success')
    await closeForm(); await loadUsers()
  } catch (e) { formError.value = '保存失败：' + getApiError(e) }
  finally { saving.value = false }
}
async function removeUser(user: UserVO) {
  if (deletingId.value != null) return
  deletingId.value = user.id
  try {
    if (!(await confirmUserAction(`确定删除用户「${user.username}」？此操作不可恢复。`))) return
    await adminApi.removeUser(user.id); notify('用户已删除', 'success'); await loadUsers()
  } catch (e) { notify('删除失败：' + getApiError(e), 'error') }
  finally { deletingId.value = null }
}
function beforeUnload(event: BeforeUnloadEvent) { if (showModal.value && (dirty.value || saving.value)) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(canDiscard)
onMounted(() => { void loadUsers(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { sequence++; window.removeEventListener('beforeunload', beforeUnload) })
</script>
<style scoped>
.admin-users { min-width:0; color:var(--kb-foreground); display:grid; gap:var(--kb-space-5); }
.user-panel { min-width:0; background:var(--kb-card); border:1px solid var(--kb-border); border-radius:var(--kb-radius-md); padding:var(--kb-space-4); }
.user-toolbar { display:flex; gap:var(--kb-space-3); align-items:center; flex-wrap:wrap; }
.user-search { display:flex; align-items:center; gap:var(--kb-space-2); border-radius:var(--kb-radius-md); padding:var(--kb-space-3); flex:1; min-width:12rem; border:0; }
.user-search:focus-within { background:var(--kb-field-active); }
.user-search input { flex:1; min-width:0; border:0; outline:0; box-shadow:none; padding:0; background:transparent; color:var(--kb-foreground); }
.user-toolbar select, .user-modal :is(input,select) { padding:var(--kb-space-3); background:var(--kb-card); border:1px solid var(--kb-border); border-radius:var(--kb-radius-sm); color:var(--kb-foreground); }
.user-result-count, .user-source-note { margin-block:var(--kb-space-3); color:var(--kb-muted-foreground); font-size:var(--kb-fs-body-sm); }
.user-table-scroll { overflow-x:auto; max-width:100%; margin-bottom:var(--kb-space-4); }
table { width:100%; min-width:48rem; border-collapse:collapse; font-size:var(--kb-fs-body-sm); }
th,td { padding:var(--kb-space-3); text-align:left; border-bottom:1px solid var(--kb-border); }
th { background:var(--kb-muted); color:var(--kb-muted-foreground); font-weight:500; }
tbody tr:hover { background:var(--kb-muted); }
.user-identity { display:flex; gap:var(--kb-space-2); align-items:center; max-width:18rem; overflow-wrap:anywhere; }.user-identity small { display:block; color:var(--kb-muted-foreground); margin-top:var(--kb-space-1); }
.user-row-actions { display:flex; gap:var(--kb-space-2); }
.user-modal-mask { position:fixed; inset:0; z-index:100; background:color-mix(in srgb,var(--kb-foreground) 30%,transparent); display:flex; align-items:center; justify-content:center; padding:var(--kb-space-4); }
.user-modal { width:min(100%,30rem); max-height:90dvh; overflow-y:auto; background:var(--kb-card); border:1px solid var(--kb-border); border-radius:var(--kb-radius-lg); box-shadow:var(--kb-shadow-lg); }
.user-modal header { display:flex; align-items:center; justify-content:space-between; gap:var(--kb-space-3); padding:var(--kb-space-5); border-bottom:1px solid var(--kb-border); }.user-modal h2 { font-size:var(--kb-fs-h3); font-weight:600; }.user-modal header p { color:var(--kb-muted-foreground); font-size:var(--kb-fs-body-sm); margin-top:var(--kb-space-2); }
.user-modal fieldset { display:grid; gap:var(--kb-space-4); padding:var(--kb-space-5); min-width:0; }.user-modal label { display:grid; gap:var(--kb-space-2); font-size:var(--kb-fs-body-sm); }.user-modal label span { color:var(--kb-danger); }.user-modal input { min-width:0; width:100%; }
.user-modal footer { display:flex; justify-content:flex-end; flex-wrap:wrap; gap:var(--kb-space-3); padding:var(--kb-space-4); border-top:1px solid var(--kb-border); align-items:center; }.user-modal footer span { color:var(--kb-muted-foreground); font-size:var(--kb-fs-body-sm); margin-right:auto; }.user-form-error { color:var(--kb-danger); font-size:var(--kb-fs-body-sm); padding-inline:var(--kb-space-5); }
.admin-users :is(button,a,input,select,summary,[tabindex]):focus-visible:not(.kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:var(--kb-field-focus-width) solid var(--kb-ring); outline-offset:var(--kb-space-1); }
@media(max-width:600px) { .user-search { flex-basis:100%; border:0; }.user-panel { padding:var(--kb-space-3); } }
</style>

<style scoped>
.user-search:has(input:focus-visible){outline:0}.user-search input.kb-form-control,.user-search input.kb-form-control:focus,.user-search input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
