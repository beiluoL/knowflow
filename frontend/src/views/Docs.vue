<template>
  <div class="documents-workspace">
    <PageHeader title="文档库" description="查找、筛选和阅读你有权访问的知识资料。">
      <template #actions><router-link to="/knowledge" class="kb-btn">知识中心</router-link></template>
    </PageHeader>
    <form class="document-filters" role="search" @submit.prevent="submitSearch">
      <div class="document-search">
        <Icon name="search" :size="18" />
        <input class="kb-input kb-form-control" v-model="keyword" type="search" aria-label="搜索文档" placeholder="搜索标题、正文或标签" @compositionstart="composing = true" @compositionend="composing = false" />
        <button type="submit" class="kb-btn kb-btn-primary">搜索</button>
      </div>
      <div class="document-filter-row">
        <Select :model-value="categoryId" label="文档分类" :options="[{ value: '', label: '全部分类' }, ...categoryOptions.map(cat => ({ value: String(cat.id), label: cat.label }))]" searchable @update:modelValue="value => { categoryId = value; changeFilter() }" />
        <Select :model-value="type" label="文档类型" :options="[{ value: '', label: '全部类型' }, { value: 'doc', label: '文档' }, { value: 'pdf', label: 'PDF' }, { value: 'markdown', label: 'Markdown' }, { value: 'note', label: '笔记' }]" @update:modelValue="value => { type = value; changeFilter() }" />
        <Select :model-value="sort" label="文档排序" :options="[{ value: 'time', label: '最新发布' }, { value: 'relevance', label: '相关性' }, { value: 'view', label: '最多阅读' }]" @update:modelValue="value => { sort = value; changeFilter() }" />
        <button v-if="hasFilters" type="button" class="kb-btn" @click="resetFilters">重置筛选</button>
      </div>
    </form>
    <StateView :status="documentsStatus" :error-title="documentError?.title" :error-scope="documentError?.scope" :error-hint="documentError?.hint" :show-retry="documentError?.retryable" empty-title="还没有可浏览的分类资料" empty-description="当前条件下没有可访问的文档，试试其他关键词或重置筛选。" @retry="loadData(true)">
      <template #empty-action><button v-if="hasFilters" type="button" class="kb-state-retry" @click="resetFilters">重置筛选</button><router-link v-else to="/knowledge" class="kb-btn">前往知识中心</router-link></template>
      <div class="document-results">
        <p class="document-result-count" aria-live="polite">共 {{ total }} 篇文档</p>
        <ul class="document-list">
          <li v-for="doc in docs" :key="doc.id">
            <router-link :to="`/doc/${doc.id}`" class="document-row">
              <DocTypeBadge :file-url="doc.fileUrl" :content="doc.content" :size="32" />
              <div class="document-row-content">
                <h2>{{ doc.title }}</h2>
                <p v-if="doc.summary || doc.content" class="document-excerpt">{{ searchSummary(doc.summary || doc.content || '') }}</p>
                <div class="document-meta"><span>{{ doc.categoryName || '未分类' }}</span><time v-if="doc.createTime">{{ formatDate(doc.createTime) }}</time><span v-if="doc.viewCount != null">{{ doc.viewCount }} 次阅读</span></div>
              </div>
              <Icon name="chevron-right" :size="18" class="document-arrow" />
            </router-link>
          </li>
        </ul>
        <Pagination v-if="total > pageSize" :page-num="pageNum" :page-size="pageSize" :total="total" @change="changePage" />
      </div>
    </StateView>
    <StateView v-if="auth.isLoggedIn" :status="recentStatus" :error-title="recentError?.title" :error-scope="recentError?.scope" :error-hint="recentError?.hint" :show-retry="recentError?.retryable" empty-title="还没有阅读记录" empty-description="打开一篇文档后，可以在这里继续阅读。" @retry="recentResource.run(true)">
      <section class="document-recent"><h2>继续阅读</h2><div class="document-recent-grid"><router-link v-for="doc in recentDocs" :key="doc.id" :to="`/doc/${doc.id}`" class="document-recent-link"><span>{{ doc.title }}</span></router-link></div></section>
    </StateView>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import Pagination from '@/components/ui/Pagination.vue'
import DocTypeBadge from '@/components/doc/DocTypeBadge.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuthStore } from '@/stores/auth'
import { categoriesApi, docsApi } from '@/api'
import { searchSummary } from '@/utils/searchText'
import type { CategoryVO, DocQuery } from '@/api/types'
const auth=useAuthStore(),route=useRoute(),router=useRouter()
const keyword=ref(''),categoryId=ref(''),type=ref(''),sort=ref<NonNullable<DocQuery['sort']>>('time'),pageNum=ref(1),composing=ref(false),pageSize=12
const query=ref<DocQuery>({pageNum:1,pageSize,sort:'time'})
function restoreQuery(){
 const q=route.query;keyword.value=typeof q.q==='string'?q.q:'';categoryId.value=typeof q.categoryId==='string'&&/^\d+$/.test(q.categoryId)?q.categoryId:''
 type.value=typeof q.type==='string'&&['doc','pdf','markdown','note'].includes(q.type)?q.type:''
 sort.value=typeof q.sort==='string'&&['time','view','relevance'].includes(q.sort)?q.sort as NonNullable<DocQuery['sort']>:'time'
 pageNum.value=typeof q.page==='string'&&/^\d+$/.test(q.page)?Math.max(1,Number(q.page)):1
 query.value={keyword:keyword.value.trim()||undefined,categoryId:categoryId.value?Number(categoryId.value):undefined,type:type.value as DocQuery['type']||undefined,sort:sort.value,pageNum:pageNum.value,pageSize}
}
restoreQuery()
const documentsResource=useAsyncResource(async()=>{const[cats,page]=await Promise.all([categoriesApi.tree(),docsApi.list({...query.value})]);return{cats,page}},data=>data.page.records.length===0,'文档库加载失败')
const documentsStatus=documentsResource.status,documentError=documentsResource.error
const docs=computed(()=>documentsResource.data.value?.page.records||[]),total=computed(()=>documentsResource.data.value?.page.total||0)
const categoryOptions=computed(()=>{const result:{id:number;label:string}[]=[];function walk(cats:CategoryVO[],prefix=''){for(const cat of cats){result.push({id:cat.id,label:prefix+cat.name});walk(cat.children||[],prefix+'— ')}}walk(documentsResource.data.value?.cats||[]);return result})
const recentResource=useAsyncResource(()=>docsApi.recent(),data=>data.length===0,'最近阅读加载失败','section')
const recentStatus=recentResource.status,recentError=recentResource.error,recentDocs=computed(()=>recentResource.data.value?.slice(0,6)||[])
const hasFilters=computed(()=>!!(keyword.value||categoryId.value||type.value||sort.value!=='time'))
function formatDate(date?:string){return date?date.slice(0,10):''}
function loadData(retry=false){return documentsResource.run(retry)}
function navigate(){const next={...route.query,q:keyword.value.trim()||undefined,categoryId:categoryId.value||undefined,type:type.value||undefined,sort:sort.value==='time'?undefined:sort.value,page:pageNum.value===1?undefined:String(pageNum.value)};void router.replace({query:next});query.value={keyword:keyword.value.trim()||undefined,categoryId:categoryId.value?Number(categoryId.value):undefined,type:type.value as DocQuery['type']||undefined,sort:sort.value,pageNum:pageNum.value,pageSize};void loadData()}
function submitSearch(){if(composing.value)return;keyword.value=keyword.value.trim();pageNum.value=1;navigate()}
function changeFilter(){pageNum.value=1;navigate()}
function changePage(page:number){pageNum.value=page;navigate()}
function resetFilters(){keyword.value='';categoryId.value='';type.value='';sort.value='time';pageNum.value=1;navigate()}
watch(()=>route.query,()=>{const previous=JSON.stringify(query.value);restoreQuery();if(previous!==JSON.stringify(query.value))void loadData()})
onMounted(()=>{void loadData();if(auth.isLoggedIn)void recentResource.run()})
</script>
<style scoped>
.documents-workspace { display:grid; gap:var(--kb-space-6); min-width:0; }
.document-filters { display:grid; gap:var(--kb-space-4); }
.document-search { display:flex; align-items:center; gap:var(--kb-space-3); padding:var(--kb-space-2); padding-left:var(--kb-space-4); background:var(--kb-field-surface); border-radius:var(--kb-radius-lg); border:0; }
.document-search:focus-within { background:var(--kb-field-active); }
.document-search button { background:var(--kb-primary);color:var(--kb-primary-foreground);padding:var(--kb-space-2) var(--kb-space-4);border-radius:var(--kb-radius-md);min-height:var(--kb-control-hit-size); }
.document-search input { flex:1;min-width:0;border:0;outline:0;box-shadow:none;background:transparent;color:var(--kb-foreground);font-size:var(--kb-fs-body-md); }
.document-filter-row { display:flex;flex-wrap:wrap;gap:var(--kb-space-3);align-items:end; }
.document-filter-row > .select-control { flex:1; min-width:8rem; }
.document-results,.document-recent { display:grid;gap:var(--kb-space-4);min-width:0; }
.document-result-count,.document-meta,.document-excerpt { color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm); }
.document-list { margin:0;padding:0;list-style:none;border-top:1px solid var(--kb-border); }
.document-row { display:flex;align-items:center;gap:var(--kb-space-4);padding:var(--kb-space-5) var(--kb-space-2);border-bottom:1px solid var(--kb-border);color:var(--kb-foreground); }
.document-row:hover,.document-recent-link:hover { background:var(--kb-muted); }
.document-row-content { min-width:0;flex:1; }
.document-row h2,.document-recent h2 { font-family:inherit;font-size:var(--kb-fs-body-lg);font-weight:600;overflow-wrap:anywhere; }
.document-excerpt { margin-top:var(--kb-space-2);line-height:var(--kb-lh-body-md);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere; }
.document-meta { display:flex;flex-wrap:wrap;gap:var(--kb-space-3);margin-top:var(--kb-space-2);font-size:var(--kb-fs-caption); }
.document-recent-grid { display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr));gap:var(--kb-space-3); }
.document-recent-link { display:grid;gap:var(--kb-space-2);padding:var(--kb-space-4);border:1px solid var(--kb-border);border-radius:var(--kb-radius-lg);overflow-wrap:anywhere; }
.document-recent-link span+span { color:var(--kb-muted-foreground);font-size:var(--kb-fs-caption); }
.documents-workspace :is(a,button,select):focus-visible:not(.kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:2px solid var(--kb-ring);outline-offset:2px; }
.documents-workspace :deep(button.bg-primary-500.text-white) { color:var(--kb-primary-foreground); }
@media(max-width:640px){.document-arrow{display:none}.document-row{gap:var(--kb-space-3)}.document-filter-row label{min-width:calc(50% - var(--kb-space-3))}}
</style>

<style scoped>
.document-search:has(input:focus-visible){outline:0}.document-search input.kb-form-control,.document-search input.kb-form-control:focus,.document-search input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
