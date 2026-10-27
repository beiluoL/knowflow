<template>
  <MiniAppShell title="知识图谱" subtitle="从分类与文档关系浏览知识，选择节点查看上下文。" back-label="返回" @back="goBack">
    <div class="animate-fade-in knowledge-graph-page">

    <!-- ===== 视图切换 Tab ===== -->
    <div class="flex items-center gap-1 p-1 rounded-lg segmented mb-4" role="group" aria-label="图谱视图" style="background: var(--kb-muted);">
      <button
        v-for="tab in viewTabs"
        :key="tab.value"
        type="button"
        class="flex items-center gap-1.5 h-8 px-4 rounded-md text-sm font-medium seg-btn"
        :class="{ active: currentView === tab.value }"
        :aria-pressed="currentView === tab.value"
        @click="switchView(tab.value)"
      >
        <Icon :name="tab.icon" :size="16" />
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <StateView v-if="categoriesStatus === 'error' || categoriesStatus === 'retrying'" :status="categoriesStatus" :error-title="categoriesError?.title" :error-description="categoriesError?.hint" error-scope="分类筛选暂不可用，不影响已加载的图谱。" :error-hint="categoriesError?.hint" :show-retry="categoriesError?.retryable ?? true" @retry="loadCategories(true)" />
    <!-- ===== Tab1: 分类-文档图谱（原功能） ===== -->
    <template v-if="currentView === 'category'">
      <!-- 工具栏 -->
      <div class="rounded-lg p-4 mb-4 border toolbar-card" style="background: var(--kb-card); border-color: var(--kb-border);">
        <GraphToolbar label="分类图谱操作">
          <template #search><div class="relative kb-field graph-search"><Icon name="search" :size="16" class="search-icon" /><input aria-label="搜索图谱节点" v-model="searchKeyword" type="search" placeholder="搜索节点…" class="kb-input graph-search-input kb-form-control" /></div></template>
          <template #filters><Select v-model="selectedKb" aria-label="筛选知识分类" :options="kbOptions" variant="pill" :panel-width="340" /></template>
          <template #actions><Button variant="secondary" icon-name="download" title="导出当前图谱数据" @click="handleExport">导出</Button><Button variant="secondary" icon-name="refresh-cw" title="刷新分类图谱" :loading="loading" :aria-busy="loading" @click="loadGraph(true)">刷新</Button></template>
        </GraphToolbar>
      </div>

      <StateView :status="graphStatus" :error-title="graphError?.title" :error-description="graphError?.hint" :error-scope="graphError?.scope" :error-hint="graphError?.hint" :show-retry="graphError?.retryable ?? true" empty-title="还没有知识图谱" empty-description="先创建知识分类或导入资料，分类与文档关系会在这里呈现。" type="card" :rows="3" @retry="loadGraph(true)">
      <template #empty-action><button type="button" class="kb-btn kb-btn-primary" @click="router.push('/knowledge')">去知识库添加资料</button></template>
      <template #default>
        <div class="category-graph-layout">
          <KnowledgeGraphCanvas mode="hierarchy" title="分类与文档结构图" :nodes="categoryCanvasNodes" :edges="categoryCanvasEdges" :selected-id="selectedNode?.id" :keyword="searchKeyword" :searchable="false" @select="selectCategoryId" />
          <!-- 详情面板 -->
          <div class="rounded-lg border p-5 detail-panel" aria-live="polite" style="background: var(--kb-card); border-color: var(--kb-border); align-self: start;">
            <template v-if="selectedNode">
              <button type="button" class="kb-btn kb-btn-ghost" aria-label="关闭节点详情" @click="selectedNode=null">关闭详情</button><h3 class="kb-h3">{{ selectedNode.label }}</h3>
              <div class="my-3 h-px" style="background: var(--kb-border);" />
              <div class="space-y-2 text-sm">
                <div class="flex justify-between"><span style="color: var(--kb-muted-foreground);">类型</span><span>{{ levelLabel(selectedNode.level) }}</span></div>
                <div class="flex justify-between"><span style="color: var(--kb-muted-foreground);">关联文档</span><span class="font-semibold">{{ selectedNode.docCount }}</span></div>
                <div class="flex justify-between"><span style="color: var(--kb-muted-foreground);">关联节点</span><span class="font-semibold">{{ selectedNode.relationCount }}</span></div>
              </div>
              <div class="my-3 h-px" style="background: var(--kb-border);" />
              <h4 class="kb-h4 mb-2 text-sm">关联文档</h4>
              <div class="space-y-1 max-h-48 overflow-y-auto">
                <a v-for="doc in selectedNode.docs" :key="doc.id" href="#" class="flex items-center gap-2 p-2 rounded-lg transition-colors" style="text-decoration: none;" @click.prevent="goToDoc(doc.id)">
                  <Icon name="file-text" :size="16" style="color: var(--kb-muted-foreground);" />
                  <span class="text-xs flex-1 truncate">{{ doc.title }}</span>
                </a>
              </div>
            </template>
            <template v-else>
              <div class="text-xs" style="color: var(--kb-muted-foreground);">点击图谱节点查看详情</div>
            </template>
          </div>
        </div>
      </template>
      </StateView>
    </template>

    <!-- ===== Tab2: 技术栈依赖图谱 ===== -->
    <template v-else-if="currentView === 'tech'">
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <GraphToolbar label="技术依赖图操作">
          <template #search><div class="kb-field"><input v-model="techTopic" :disabled="techLoading" aria-label="技术主题" type="search" placeholder="技术主题，如 Spring Boot" class="kb-input kb-form-control" @keydown.enter="submitTech" /></div></template>
          <template #filters><Select v-model="techCategoryId" aria-label="技术参考分类" :options="referenceOptions" variant="pill" :panel-width="240" :disabled="techLoading" /></template>
          <template #actions><Button icon-name="sparkles" title="根据技术主题生成依赖图" :loading="techLoading" :aria-busy="techLoading" :disabled="!techTopic.trim()" @click="loadTechGraph">AI 生成技术图谱</Button><Button variant="secondary" icon-name="rotate-ccw" title="清空已生成的技术图谱" :disabled="techLoading || !techGraph" @click="resetTechGraph">重置</Button></template>
        </GraphToolbar>
        <p v-if="techGraph?.generatedAt" class="text-xs mt-2" style="color: var(--kb-muted-foreground);">
          生成于 {{ techGraph.generatedAt }} · 共 {{ techGraph.nodes.length }} 个技术 · {{ techGraph.edges.length }} 条依赖
        </p>
      </div>

      <StateView :status="techStatus" :error-title="techError?.title" :error-scope="techError?.scope" :error-hint="techError?.hint" :show-retry="techError?.retryable ?? true" empty-title="还没有技术依赖图" empty-description="输入技术主题后手动生成。生成与重试会调用你配置的 AI 服务。" type="card" :rows="3" @retry="loadTechGraph">
      <template #empty-action><button type="button" class="kb-btn kb-btn-primary" @click="loadTechGraph">生成技术图谱</button></template>
      <template #default>
        <!-- 技术图谱 SVG -->
        <div class="rounded-lg border p-4 mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <KnowledgeGraphCanvas mode="dependency" title="技术依赖图" :nodes="techCanvasNodes" :edges="techCanvasEdges" :selected-id="techSelectedNode?.id" @select="selectTechId" />
          <!-- 图例 -->
          <div class="flex items-center gap-4 mt-3 flex-wrap text-xs" style="color: var(--kb-muted-foreground);">
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: var(--kb-primary);"/> 编程语言</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: var(--kb-success);"/> 框架</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #8b5cf6;"/> 工具</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: var(--kb-warning);"/> 数据库</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: var(--kb-danger);"/> 算法</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #06b6d4;"/> 平台</span>
            <span class="flex items-center gap-1 ml-2">— 前置依赖</span>
            <span class="flex items-center gap-1">╌ 组件/包含</span>
          </div>
        </div>

        <!-- 技术节点详情 -->
        <div v-if="techSelectedNode" class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
          <button type="button" class="kb-btn kb-btn-ghost" aria-label="关闭节点详情" @click="techSelectedNode=null">关闭详情</button>
          <div class="flex items-center gap-3 mb-3">
            <span class="inline-block w-10 h-10 rounded-full flex items-center justify-center text-xs font-bold text-white" :style="{ background: getTechCategoryColor(techSelectedNode.category).bg }">{{ techSelectedNode.name.slice(0, 2) }}</span>
            <div>
              <h3 class="kb-h3">{{ techSelectedNode.name }}</h3>
              <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ techSelectedNode.categoryLabel }} · 难度 {{ '★'.repeat(techSelectedNode.difficulty || 1) }}</span>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3 mb-3 text-sm">
            <div class="rounded p-3" style="background: var(--kb-muted);">
              <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">知识库文档</div>
              <div class="font-semibold">{{ techSelectedNode.docCount || 0 }} 篇</div>
            </div>
            <div class="rounded p-3" style="background: var(--kb-muted);">
              <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">依赖关系</div>
              <div class="font-semibold">{{ countTechRelations(techSelectedNode.id) }} 条</div>
            </div>
          </div>
          <p v-if="techSelectedNode.description" class="text-sm mb-3" style="color: var(--kb-foreground);">{{ techSelectedNode.description }}</p>
          <div v-if="getTechPrerequisites(techSelectedNode.id).length" class="mb-3">
            <h4 class="text-xs font-semibold mb-1" style="color: var(--kb-muted-foreground);">前置知识</h4>
            <div class="flex flex-wrap gap-1">
              <span v-for="pre in getTechPrerequisites(techSelectedNode.id)" :key="pre" class="px-2 py-0.5 rounded text-xs" style="background: var(--kb-muted); color: var(--kb-foreground);">{{ pre }}</span>
            </div>
          </div>
        </div>
      </template>

      </StateView>
    </template>

    <!-- ===== Tab3: 概念可视化图解 ===== -->
    <template v-else-if="currentView === 'concept'">
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <GraphToolbar label="概念图解操作">
          <template #search><input v-model="conceptInput" :disabled="conceptLoading" aria-label="图解概念" type="search" placeholder="输入概念，如变量、递归、RAG" class="kb-input kb-form-control" @keydown.enter="submitConcept" /></template>
          <template #actions><Button icon-name="sparkles" title="生成当前概念图解" :loading="conceptLoading" :aria-busy="conceptLoading" :disabled="!conceptInput.trim()" @click="loadConceptDiagram">AI 生成图解</Button></template>
        </GraphToolbar>
        <div class="flex items-center gap-2 mt-3 flex-wrap">
          <span class="text-xs" style="color: var(--kb-muted-foreground);">快速尝试：</span>
          <button v-for="c in quickConcepts" :key="c" type="button" :disabled="conceptLoading" class="px-2 py-0.5 rounded text-xs border hover:opacity-80" style="border-color: var(--kb-border);" @click="conceptInput = c; loadConceptDiagram()">{{ c }}</button>
        </div>
        <!-- 最近浏览 -->
        <div v-if="conceptHistory.length" class="flex items-center gap-2 mt-2 flex-wrap">
          <span class="text-xs" style="color: var(--kb-muted-foreground);">最近浏览：</span>
          <button v-for="h in conceptHistory" :key="h" type="button" :disabled="conceptLoading" class="px-2 py-0.5 rounded text-xs" style="background: var(--kb-muted); color: var(--kb-foreground);" @click="conceptInput = h; loadConceptDiagram()">{{ h }}</button>
        </div>
      </div>

      <StateView :status="conceptStatus" :error-title="conceptError?.title" :error-scope="conceptError?.scope" :error-hint="conceptError?.hint" :show-retry="conceptError?.retryable ?? true" empty-title="还没有概念图解" empty-description="输入一个概念后手动生成图解。生成与重试会调用你配置的 AI 服务。" type="card" :rows="3" @retry="retryConceptDiagram">
      <template #empty-action><button type="button" class="kb-btn kb-btn-primary" @click="loadConceptDiagram">生成概念图解</button></template>
      <template #default>
        <div v-if="conceptDiagram" class="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-4">
          <!-- 左栏：图解 + 代码示例 -->
          <div class="space-y-4">
            <div class="rounded-lg border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
              <GraphToolbar label="概念图解画布操作" class="mb-3">
                <template #filters><h3 class="kb-h3">{{ conceptDiagram.concept }}</h3><span v-if="conceptDiagram.difficulty" class="px-2 py-0.5 rounded text-xs font-medium" :style="{ background: difficultyBg(conceptDiagram.difficulty), color: difficultyColor(conceptDiagram.difficulty) }">{{ difficultyLabel(conceptDiagram.difficulty) }}</span><span class="px-2 py-0.5 rounded text-xs" style="background:var(--kb-muted);color:var(--kb-muted-foreground)">{{ conceptDiagram.diagramType }}</span></template>
                <template #actions><Button variant="secondary" icon-name="rotate-ccw" title="重新生成会替换当前缓存图解" :loading="conceptRegenerating" :aria-busy="conceptRegenerating" :disabled="conceptLoading" @click="regenerateConceptDiagram">重新生成</Button></template>
              </GraphToolbar>
              <MermaidDiagram :code="conceptDiagram.mermaidCode" :id="'concept-' + conceptDiagram.concept" />
            </div>
            <!-- 代码示例 -->
            <div v-if="conceptDiagram.codeExample" class="rounded-lg border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div class="flex items-center gap-2 mb-2">
                <Icon name="code" :size="16" style="color: var(--kb-primary);" />
                <h4 class="kb-h4 text-sm">代码示例</h4>
              </div>
              <pre class="rounded-lg p-3 text-xs overflow-x-auto" style="background: #1e293b; color: #e2e8f0;"><code>{{ conceptDiagram.codeExample }}</code></pre>
            </div>
          </div>
          <!-- 右栏：学习信息 -->
          <div class="rounded-lg border p-5 space-y-4" style="background: var(--kb-card); border-color: var(--kb-border); align-self: start;">
            <!-- 概念说明 -->
            <div>
              <h4 class="kb-h4 mb-2 text-sm">概念说明</h4>
              <p class="text-sm" style="color: var(--kb-foreground);">{{ conceptDiagram.description }}</p>
            </div>
            <!-- 关键知识点 -->
            <div v-if="conceptDiagram.keyPoints?.length">
              <div class="my-3 h-px" style="background: var(--kb-border);" />
              <h4 class="kb-h4 mb-2 text-sm">关键知识点</h4>
              <ul class="space-y-1.5">
                <li v-for="(point, i) in conceptDiagram.keyPoints" :key="i" class="flex items-start gap-2 text-sm">
                  <span class="inline-block w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0" style="background: rgba(59,111,224,0.12); color: var(--kb-primary);">{{ i + 1 }}</span>
                  <span style="color: var(--kb-foreground);">{{ point }}</span>
                </li>
              </ul>
            </div>
            <!-- 关联概念 -->
            <div v-if="conceptDiagram.relatedConcepts?.length">
              <div class="my-3 h-px" style="background: var(--kb-border);" />
              <h4 class="kb-h4 mb-2 text-sm">关联概念</h4>
              <div class="flex flex-wrap gap-1.5">
                <button v-for="rc in conceptDiagram.relatedConcepts" :key="rc" type="button" class="px-2.5 py-1 rounded-lg text-xs border transition-colors hover:opacity-80" style="border-color: var(--kb-primary); color: var(--kb-primary); background: rgba(59,111,224,0.05);" @click="conceptInput = rc; loadConceptDiagram()">{{ rc }}</button>
              </div>
            </div>
            <!-- AI 详细解释 -->
            <div>
              <div class="my-3 h-px" style="background: var(--kb-border);" />
              <h4 class="kb-h4 mb-2 text-sm">AI 详细解释</h4>
              <p class="text-sm leading-relaxed" style="color: var(--kb-muted-foreground);">{{ conceptDiagram.explanation }}</p>
            </div>
          </div>
        </div>
      </template>

      </StateView>
    </template>

    <!-- ===== Tab4: 实体关系知识图谱（A-RAG-04：AI 从文档抽取实体+关系） ===== -->
    <template v-else>
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <GraphToolbar label="知识实体图操作">
          <template #filters><Button v-if="adaptiveEnabled" variant="secondary" :aria-pressed="masteryView" @click="changeMasteryView">{{masteryView?'掌握度视图':'实体类型视图'}} · 切换</Button><Select v-model="entityCategoryId" aria-label="筛选实体所属分类" :options="entityCategoryOptions" variant="pill" :panel-width="240" :disabled="entityBuilding" /></template>
          <template #actions><Button icon-name="sparkles" title="调用已配置的 AI 抽取并构建图谱" :loading="entityBuilding" :aria-busy="entityBuilding" :disabled="entityLoading" @click="buildEntityGraph">构建知识图谱</Button><Button variant="secondary" icon-name="refresh-cw" title="重新读取已保存的实体与关系" :loading="entityLoading" :aria-busy="entityLoading" :disabled="entityBuilding" @click="fetchEntityGraph(true)">刷新</Button></template>
        </GraphToolbar>
        <p class="text-xs mt-2" style="color:var(--kb-muted-foreground)">构建会调用 AI 抽取；刷新只读取已有实体与关系。</p>
        <p v-if="entityGraph?.generatedAt" class="text-xs mt-2" style="color: var(--kb-muted-foreground);">
          生成于 {{ entityGraph.generatedAt }} · 共 {{ entityGraph.nodes.length }} 个实体 · {{ entityGraph.edges.length }} 条关系
        </p>
      </div>

      <StateView :status="entityStatus" :error-title="entityError?.title" :error-description="entityError?.hint" :error-scope="entityError?.scope" :error-hint="entityError?.hint" :show-retry="entityError?.retryable ?? true" empty-title="还没有知识实体" empty-description="添加文档后，点击构建知识图谱提取实体与关系。构建会调用你配置的 AI 服务。" type="card" :rows="3" @retry="fetchEntityGraph(true)">
      <template #empty-action><button type="button" class="kb-btn kb-btn-primary" :disabled="entityBuilding" @click="buildEntityGraph">{{ entityBuilding ? '构建中…' : '构建知识图谱' }}</button></template>
      <template #default>
        <StateView v-if="masteryStatus !== 'ready' && masteryStatus !== 'empty'" :status="masteryStatus" :error-title="masteryError?.title" :error-description="masteryError?.hint" error-scope="仅掌握度标记不可用，实体和关系仍可浏览。" :error-hint="masteryError?.hint" :show-retry="masteryError?.retryable ?? true" @retry="loadEntityMastery(true)" />
        <div class="rounded-lg border p-4 mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <KnowledgeGraphCanvas mode="relations" title="知识实体关系图" :nodes="entityCanvasNodes" :edges="entityCanvasEdges" :selected-id="entitySelectedNode ? String(entitySelectedNode.id) : null" @select="id => id ? selectEntityById(Number(id)) : entitySelectedNode = null" />
          <div class="flex items-center gap-3 mt-3 flex-wrap text-xs" style="color: var(--kb-muted-foreground);">
            <span v-if="adaptiveEnabled&&masteryView">状态采用原掌握度引擎：已掌握 / 学习中 / 薄弱 / 需复习 / 证据不足。颜色同时保留节点文字。</span><template v-if="masteryView"><span v-for="(color,status) in MASTERY_STATUS_COLORS" :key="status"><span aria-hidden="true" :style="{color}">●</span> {{status==='NOT_STARTED'?'证据不足':masteryStatusLabel(status)}}</span></template><span v-for="(color, type) in (masteryView?{}:entityTypeLegend)" :key="type" class="flex items-center gap-1">
              <span class="inline-block w-3 h-3 rounded-full" :style="{ background: color.stroke }" /> {{ entityTypeName(type) }}
            </span>
          </div>
        </div>

        <div v-if="entitySelectedNode" class="rounded-lg border p-5 graph-selected-details" style="background: var(--kb-card); border-color: var(--kb-border);">
          <button type="button" class="kb-btn kb-btn-ghost" aria-label="关闭节点详情" @click="entitySelectedNode=null">关闭详情</button>
          <div class="flex items-center gap-3 mb-3">
            <span class="inline-block w-10 h-10 rounded-full flex items-center justify-center text-xs font-bold text-white" :style="{ background: entityTypeColor(entitySelectedNode.type).stroke }">{{ entitySelectedNode.name.slice(0, 2) }}</span>
            <div>
              <h3 class="kb-h3">{{ entitySelectedNode.name }}</h3>
              <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ entityTypeName(entitySelectedNode.type) }} · {{ entitySelectedNode.categoryName || '未分类' }}</span>
            </div>
          </div>
          <p v-if="entitySelectedNode.description" class="text-sm mb-3" style="color: var(--kb-foreground);">{{ entitySelectedNode.description }}</p>
          <GraphKnowledgeEvidence v-if="adaptiveEnabled" :knowledge-id="entitySelectedNode.id"/>
          <!-- Phase 2-B：掌握度概览 -->
          <div v-if="selectedNodeMastery" class="rounded p-3 mb-3 text-sm" style="background: var(--kb-muted);">
            <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">我的掌握度</div>
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-semibold" :style="{ color: masteryStatusColor(selectedNodeMastery.learningStatus) }">
                {{ selectedNodeMastery.masteryScore }} 分 · {{ masteryStatusLabel(selectedNodeMastery.learningStatus) }}
              </span>
              <span class="text-xs" style="color: var(--kb-muted-foreground);">置信 {{ selectedNodeMastery.confidenceScore }}% · 遗忘风险 {{ selectedNodeMastery.forgettingRisk }}</span>

            </div>
          </div>
              <button type="button" class="ml-auto px-2 py-0.5 rounded text-xs font-medium" style="border:1px solid var(--kb-primary); color: var(--kb-primary); background: rgba(59,111,224,.05);" @click="openMasteryDetail(entitySelectedNode.id)">查看详情</button>
          <div class="rounded p-3 mb-3 text-sm" style="background: var(--kb-muted);">
            <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">关联关系</div>
            <div class="font-semibold">{{ countEntityRelations(entitySelectedNode.id) }} 条 · 重要度 {{ entitySelectedNode.weight || 1 }}</div>
          </div>
          <div v-if="entitySourceDocs.length" class="mb-3"><h4 class="text-sm">关系来源文档</h4><button v-for="id in entitySourceDocs" :key="id" type="button" class="kb-btn kb-btn-ghost" @click="goToDoc('doc_'+id)">文档 #{{ id }}</button></div>
          <div v-if="getEntityNeighbors(entitySelectedNode.id).length" class="mb-2">
            <h4 class="text-xs font-semibold mb-1" style="color: var(--kb-muted-foreground);">关联实体</h4>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="nb in getEntityNeighbors(entitySelectedNode.id)" :key="nb.id" type="button" class="px-2 py-0.5 rounded text-xs border transition-colors hover:opacity-80" style="border-color: var(--kb-border);" @click="selectEntityById(nb.id)">{{ nb.name }} <span class="opacity-60">· {{ relationLabel(nb.relation) }}</span></button>
            </div>
          </div>
        </div>
      </template>

      </StateView>
    </template>
  </div>
</MiniAppShell>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import Button from '@/components/ui/Button.vue'
import Select from '@/components/ui/Select.vue'
import GraphToolbar from '@/components/Knowledge/GraphToolbar.vue'
import GraphKnowledgeEvidence from '@/components/Knowledge/GraphKnowledgeEvidence.vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import MermaidDiagram from '@/components/Knowledge/MermaidDiagram.vue'
import { notify, getApiError } from '@/utils/toast'
import { knowledgeApi, categoriesApi, learningApi } from '@/api'
import type {
  KnowledgeGraphVO,
  TechGraphVO, TechNodeVO,
  ConceptDiagramVO,
  EntityNodeVO, EntityGraphVO, CategoryVO,
  KnowledgeMasteryVO,
} from '@/api/types'

import KnowledgeGraphCanvas from '@/components/Knowledge/KnowledgeGraphCanvas.vue'
import type { CanvasGraphNode, CanvasGraphEdge } from '@/utils/knowledgeGraphLayout'
import { filterKnowledgeGraph, graphNodeContext } from '@/utils/knowledgeGraphView'

const router = useRouter()
const graphQuery=()=>router.currentRoute?.value?.query||{}
const adaptiveEnabled=import.meta.env.VITE_PRODUCT_ADAPTIVE_LEARNING==='true'
const masteryView=ref(adaptiveEnabled&&graphQuery().graphColor==='mastery')
async function changeMasteryView(){masteryView.value=!masteryView.value;await router.replace({query:{...graphQuery(),graphColor:masteryView.value?'mastery':undefined,view:'entity'}})}
let pageAlive=true
onBeforeUnmount(()=>{pageAlive=false})

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

// ===== 视图切换 =====
type ViewTab = 'category' | 'tech' | 'concept' | 'entity'
const currentView = ref<ViewTab>(adaptiveEnabled&&graphQuery().view==='entity'?'entity':'category')
const viewTabs = [
  { value: 'category' as const, label: '分类图谱', icon: 'share-2' },
  { value: 'tech' as const, label: '技术依赖图', icon: 'git-branch' },
  { value: 'concept' as const, label: '概念图解', icon: 'image' },
  { value: 'entity' as const, label: '知识实体图', icon: 'link' },
]

// 提前声明 fetchEntityGraph / buildEntityGraph，供 switchView 在切换 Tab 时调用
async function fetchEntityGraph(retry = false) {
  entitySelectedNode.value = null
  await entityResource.run(retry)
  if (pageAlive && entityStatus.value === 'ready') {
    // Phase 2-B：并行加载知识点掌握度，叠加到实体节点（失败仅告警，不阻断图谱）
    void loadEntityMastery()
  }
}

// Phase 2-B：知识点掌握度映射（kg_entity.id -> mastery），用于实体图谱叠加掌握状态
const masteryMap = ref<Record<number, KnowledgeMasteryVO>>({})
const masteryResource = useAsyncResource(() => learningApi.knowledgeMastery(), list => list.length === 0, '掌握度加载失败', 'section')
const masteryStatus = masteryResource.status
const masteryError = masteryResource.error
async function loadEntityMastery(retry = false) {
    masteryMap.value = {}
    await masteryResource.run(retry)
    if (masteryStatus.value === 'error' || !masteryResource.data.value) return
    const list = masteryResource.data.value
    const map: Record<number, KnowledgeMasteryVO> = {}
    for (const m of list) map[m.knowledgeId] = m
    masteryMap.value = map
}

async function buildEntityGraph() {
  if (entityBuilding.value) return
  entityBuilding.value = true
  try {
    const res = await knowledgeApi.buildEntityGraph(entityCategoryId.value || undefined)
    notify(res.message || '知识图谱构建完成', 'success')
    await fetchEntityGraph()
  } catch (e: unknown) {
    notify(getApiError(e, '构建失败'), 'error')
  } finally {
    entityBuilding.value = false
  }
}

const switchView = (v: ViewTab) => {
  currentView.value = v
  if (v === 'entity') void fetchEntityGraph()
}

// ===== Tab1: 分类图谱（原逻辑保留） =====
const graphResource = useAsyncResource(() => knowledgeApi.graph(), value => value.nodes.length === 0, '图谱加载失败')
const graphStatus = graphResource.status
const graphError = graphResource.error
const loading = computed(() => ['loading', 'retrying'].includes(graphStatus.value))
const graph = computed<KnowledgeGraphVO>(() => graphResource.data.value ?? { nodes: [], edges: [] })
const selectedNode = ref<RenderNode | null>(null)
const selectedKb = ref('all')
const searchKeyword = ref('')

interface RenderNode {
  id: string; label: string; level: string; type: string;
  x: number; y: number; docCount: number; relationCount: number;
  docs: { id: string; title: string; tag: string }[];
}

const kbList = computed(() => graph.value.nodes.filter(n => n.type === 'category'))
const kbOptions = computed(() => [{ value: 'all', label: '全部知识库' }, ...kbList.value.map(kb => ({ value: kb.id, label: kb.label }))])
const visibleGraph = computed(() => filterKnowledgeGraph(graph.value, selectedKb.value, searchKeyword.value))
const categoryCanvasNodes = computed<CanvasGraphNode[]>(() => visibleGraph.value.nodes.map(n=>({id:n.id,label:n.label,type:n.type,typeLabel:n.type==='category'?'分类':'文档',color:n.type==='category'?'var(--kb-primary)':'var(--kb-muted-foreground)'})))
const categoryCanvasEdges = computed<CanvasGraphEdge[]>(() => visibleGraph.value.edges.map(e=>({...e,label:e.relation==='parent'?'子分类':'包含文档',directed:true})))
function selectCategoryId(id:string|null) {
  const n=graph.value.nodes.find(node=>node.id===id)
  if(!n){selectedNode.value=null;return}
  const context=graphNodeContext(graph.value,n.id)
  selectedNode.value={...n,level:n.type==='category'?'level1':'level2',x:0,y:0,docCount:context.docs.length,relationCount:context.relationCount,docs:context.docs.map(d=>({id:d.id,title:d.label,tag:'文档'}))}
}
watch(visibleGraph, value=>{if(selectedNode.value&&!value.nodes.some(n=>n.id===selectedNode.value!.id))selectedNode.value=null})
const levelLabel = (level: string) => level === 'center' ? '核心主题' : level === 'level1' ? '分类' : '文档'
const goToDoc = (nodeId: string) => { const match = /^(?:doc[-_])?(\d+)$/.exec(nodeId); if (match) router.push(`/doc/${match[1]}`) }

// C② 导出当前 Tab 的图谱数据（分类/技术/实体为 nodes+edges 结构，概念为 Mermaid 图解）
const exportMeta: Record<ViewTab, { file: string; label: string }> = {
  category: { file: 'knowledge-graph-category', label: '分类图谱' },
  tech: { file: 'knowledge-graph-tech', label: '技术依赖图' },
  concept: { file: 'knowledge-graph-concept', label: '概念图解' },
  entity: { file: 'knowledge-graph-entity', label: '知识实体图' },
}

const handleExport = () => {
  const tab = currentView.value
  let payload: unknown
  let hasData = false
  if (tab === 'category') {
    payload = { nodes: graph.value.nodes, edges: graph.value.edges }
    hasData = graph.value.nodes.length > 0
  } else if (tab === 'tech') {
    payload = techGraph.value
    hasData = !!techGraph.value && techGraph.value.nodes.length > 0
  } else if (tab === 'entity') {
    payload = entityGraph.value
    hasData = !!entityGraph.value && entityGraph.value.nodes.length > 0
  } else {
    payload = conceptDiagram.value
    hasData = !!conceptDiagram.value
  }
  if (!hasData) {
    notify(`${exportMeta[tab].label}暂无数据，无法导出`, 'warning')
    return
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `${exportMeta[tab].file}-${Date.now()}.json`; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
  notify(`${exportMeta[tab].label}已导出`, 'success')
}

async function loadGraph(retry = false) {
  selectedNode.value = null
  await graphResource.run(retry)
}

// ===== Tab2: 技术栈依赖图谱 =====
const techResource = useAsyncResource(() => knowledgeApi.techGraph(techTopic.value.trim(), techCategoryId.value || undefined), value => value.nodes.length === 0, '技术图谱生成失败')
const techStatus = techResource.status
techStatus.value = 'empty'
const techError = techResource.error
const techLoading = computed(() => ['loading', 'retrying'].includes(techStatus.value))
const techGraph = computed<TechGraphVO | null>(() => techResource.data.value)
const techTopic = ref('Spring Boot')
const techCategoryId = ref<number | null>(null)
const techSelectedNode = ref<TechNodeVO | null>(null)


const CATEGORY_COLORS: Record<string, { bg: string; stroke: string; text: string }> = {
  LANGUAGE:   { bg: 'rgba(59,111,224,0.15)', stroke: 'var(--kb-primary)', text: 'var(--kb-primary)' },
  FRAMEWORK:  { bg: 'rgba(16,185,129,0.15)', stroke: 'var(--kb-success)', text: 'var(--kb-success)' },
  TOOL:       { bg: 'rgba(139,92,246,0.15)', stroke: '#8b5cf6', text: '#8b5cf6' },
  DATABASE:   { bg: 'rgba(245,158,11,0.15)', stroke: 'var(--kb-warning)', text: 'var(--kb-warning)' },
  ALGORITHM:  { bg: 'rgba(239,68,68,0.15)', stroke: 'var(--kb-danger)', text: 'var(--kb-danger)' },
  PLATFORM:   { bg: 'rgba(6,182,212,0.15)', stroke: '#06b6d4', text: '#06b6d4' },
}

const getTechCategoryColor = (cat: string) => CATEGORY_COLORS[cat] || CATEGORY_COLORS.TOOL

const techCanvasNodes = computed<CanvasGraphNode[]>(()=>(techGraph.value?.nodes||[]).map(n=>({id:n.id,label:n.name,type:n.category,typeLabel:n.categoryLabel,color:getTechCategoryColor(n.category).stroke})))
const techCanvasEdges = computed<CanvasGraphEdge[]>(()=>(techGraph.value?.edges||[]).map(e=>({...e,label:({PREREQUISITE:'前置依赖',COMPONENT:'组成',DEPENDS:'依赖',DEPENDS_ON:'依赖'} as Record<string,string>)[e.relation] || e.relation,directed:true})))
function selectTechId(id:string|null){techSelectedNode.value=techGraph.value?.nodes.find(n=>n.id===id)||null}
const countTechRelations = (id: string) => techGraph.value?.edges.filter(e => e.source === id || e.target === id).length || 0
const getTechPrerequisites = (id: string) => {
  if (!techGraph.value) return []
  const prereqIds = techGraph.value.edges.filter(e => e.target === id && e.relation === 'PREREQUISITE').map(e => e.source)
  return techGraph.value.nodes.filter(n => prereqIds.includes(n.id)).map(n => n.name)
}

function submitTech(event:KeyboardEvent){if(!event.isComposing&&event.keyCode!==229)void loadTechGraph()}
function submitConcept(event:KeyboardEvent){if(!event.isComposing&&event.keyCode!==229)void loadConceptDiagram()}
async function loadTechGraph() {
  if (!techTopic.value.trim() || techLoading.value) return
  techSelectedNode.value = null
  await techResource.run(techStatus.value === 'error')
}

function resetTechGraph() {
  if (techLoading.value) return
  techTopic.value = 'Spring Boot'
  techResource.data.value = null
  techSelectedNode.value = null
  techStatus.value = 'empty'
  techError.value = null
}

// ===== Tab3: 概念可视化图解 =====
const conceptRegenerating = ref(false)
let conceptAttempt = { topic: '变量', regenerate: false }
const conceptResource = useAsyncResource(() => conceptAttempt.regenerate ? knowledgeApi.regenerateConceptDiagram(conceptAttempt.topic) : knowledgeApi.conceptDiagram(conceptAttempt.topic), value => !value, '概念图解生成失败')
const conceptStatus = conceptResource.status
conceptStatus.value = 'empty'
const conceptError = conceptResource.error
const conceptLoading = computed(() => ['loading', 'retrying'].includes(conceptStatus.value))
const conceptDiagram = computed<ConceptDiagramVO | null>(() => conceptResource.data.value)
const conceptInput = ref('变量')
const quickConcepts = ['变量', '循环', '面向对象', '递归', 'RAG', '快速排序', 'React Hooks']
const conceptHistory = ref<string[]>([])

function difficultyLabel(d: number) {
  return d === 3 ? '进阶' : d === 2 ? '中等' : '入门'
}
function difficultyColor(d: number) {
  return d === 3 ? 'var(--kb-danger)' : d === 2 ? 'var(--kb-warning)' : 'var(--kb-success)'
}
function difficultyBg(d: number) {
  return d === 3 ? 'rgba(239,68,68,0.12)' : d === 2 ? 'rgba(245,158,11,0.12)' : 'rgba(16,185,129,0.12)'
}

async function loadConceptDiagram() {
  if (!conceptInput.value.trim() || conceptLoading.value) return
    const concept = conceptInput.value.trim()
    conceptAttempt = { topic: concept, regenerate: false }
    await conceptResource.run(conceptStatus.value === 'error')
    if (conceptStatus.value !== 'ready') return
    // 记录浏览历史
    if (!conceptHistory.value.includes(concept)) {
      conceptHistory.value.unshift(concept)
      if (conceptHistory.value.length > 6) conceptHistory.value.pop()
    }
}

/** 重新生成概念图解：删除旧缓存并让 AI 重新生成 */
async function regenerateConceptDiagram() {
  if (!conceptInput.value.trim() || conceptLoading.value) return
  conceptRegenerating.value = true
  conceptAttempt = { topic: conceptInput.value.trim(), regenerate: true }
  try {
    await conceptResource.run(true)
    if (conceptStatus.value === 'ready') notify('图解已重新生成', 'success')
  } finally {
    conceptRegenerating.value = false
  }
}

async function retryConceptDiagram() {
  if (conceptLoading.value) return
  conceptRegenerating.value = conceptAttempt.regenerate
  conceptInput.value = conceptAttempt.topic
  try { await conceptResource.run(true) } finally { conceptRegenerating.value = false }
}

// ===== Tab4: 实体关系知识图谱（A-RAG-04：AI 从文档抽取实体+关系） =====
const graphCategories = ref<CategoryVO[]>([])
const categoriesResource = useAsyncResource(() => categoriesApi.tree(), list => list.length === 0, '分类筛选加载失败', 'section')
const categoriesStatus = categoriesResource.status
const categoriesError = categoriesResource.error
async function loadCategories(retry = false) {
  await categoriesResource.run(retry)
  if (categoriesStatus.value !== 'error' && categoriesResource.data.value) {
    const flatten=(list:CategoryVO[]):CategoryVO[]=>list.flatMap(category=>[category,...flatten(category.children||[])])
    graphCategories.value = flatten(categoriesResource.data.value)
  }
}
const entityResource = useAsyncResource(() => knowledgeApi.entityGraph(entityCategoryId.value || undefined), value => value.nodes.length === 0, '实体图谱加载失败')
const entityStatus = entityResource.status
const entityError = entityResource.error
const entityLoading = computed(() => ['loading', 'retrying'].includes(entityStatus.value))
const entityBuilding = ref(false)
const entityGraph = computed<EntityGraphVO | null>(() => entityResource.data.value)
const entitySelectedNode = ref<EntityNodeVO | null>(null)
const entityCategoryId = ref<number | null>(null)
const referenceOptions = computed(() => [{ value: null, label: '通用' }, ...graphCategories.value.map(cat => ({ value: cat.id, label: cat.name }))])
const entityCategoryOptions = computed(() => [{ value: null, label: '全部分类' }, ...graphCategories.value.map(cat => ({ value: cat.id, label: cat.name }))])
watch(entityCategoryId,()=>{if(currentView.value==='entity')void fetchEntityGraph()})

const ENTITY_TYPE_COLORS: Record<string, { bg: string; stroke: string; text: string }> = {
  CONCEPT:    { bg: 'rgba(59,111,224,0.15)', stroke: 'var(--kb-primary)', text: 'var(--kb-primary)' },
  TECHNIQUE: { bg: 'rgba(16,185,129,0.15)', stroke: 'var(--kb-success)', text: 'var(--kb-success)' },
  TERM:      { bg: 'rgba(245,158,11,0.15)', stroke: 'var(--kb-warning)', text: 'var(--kb-warning)' },
  PRINCIPLE: { bg: 'rgba(139,92,246,0.15)', stroke: '#8b5cf6', text: '#8b5cf6' },
  TOOL:      { bg: 'rgba(6,182,212,0.15)', stroke: '#06b6d4', text: '#06b6d4' },
  OTHER:     { bg: 'rgba(100,116,139,0.15)', stroke: '#64748b', text: '#64748b' },
}
const ENTITY_TYPE_LABELS: Record<string, string> = {
  CONCEPT: '概念', TECHNIQUE: '技术', TERM: '术语', PRINCIPLE: '原理', TOOL: '工具', OTHER: '其他',
}
const RELATION_LABELS: Record<string, string> = {
  RELATED_TO: '相关', PREREQUISITE: '前置', IS_A: '属于', PART_OF: '组成', USES: '使用', CONTRASTS: '对比',
}
const entityTypeLegend = computed(() => ENTITY_TYPE_COLORS)

const entityTypeName = (t?: string) => ENTITY_TYPE_LABELS[t || 'OTHER'] || '其他'
const entityTypeColor = (t?: string) => ENTITY_TYPE_COLORS[t || 'OTHER'] || ENTITY_TYPE_COLORS.OTHER
const relationLabel = (r?: string) => r ? (RELATION_LABELS[r] || r) : '未标注关系'

const MASTERY_STATUS_COLORS:Record<string,string>={NOT_STARTED:'var(--kb-muted-foreground)',LEARNING:'var(--kb-primary)',WEAK:'var(--kb-danger)',MASTERED:'var(--kb-success)',REVIEW_REQUIRED:'var(--kb-warning)'}
const masteryStatusColor=(status?:string)=>MASTERY_STATUS_COLORS[status||'NOT_STARTED']||MASTERY_STATUS_COLORS.NOT_STARTED
const nodeMasteryLabel=(id:number)=>masteryStatus.value==='error'?'掌握度不可用':!masteryMap.value[id]?.confidenceScore?'证据不足':masteryStatusLabel(masteryMap.value[id]?.learningStatus)
const entityCanvasNodes = computed<CanvasGraphNode[]>(()=>(entityGraph.value?.nodes||[]).map(n=>({id:String(n.id),label:n.name,type:n.type,color:masteryView.value?masteryStatusColor(masteryStatus.value==='ready'&&masteryMap.value[n.id]?.confidenceScore?masteryMap.value[n.id]?.learningStatus:'NOT_STARTED'):entityTypeColor(n.type).stroke,typeLabel:masteryView.value?nodeMasteryLabel(n.id):entityTypeName(n.type),annotation:masteryView.value?undefined:masteryStatus.value==='ready'&&masteryMap.value[n.id]?.confidenceScore ? masteryMap.value[n.id]!.masteryScore+' 分' : undefined})))
const entityCanvasEdges = computed<CanvasGraphEdge[]>(()=>(entityGraph.value?.edges||[]).map(e=>({source:String(e.source),target:String(e.target),relation:e.relation,label:relationLabel(e.relation),directed:!['RELATED_TO','CONTRASTS'].includes(e.relation)})))
const selectEntityById = (id: number) => {
  entitySelectedNode.value = entityGraph.value?.nodes.find((n) => n.id === id) || null
}
const countEntityRelations = (id: number) => entityGraph.value?.edges.filter((e) => e.source === id || e.target === id).length || 0
interface EntityNeighbor { id: number; name: string; relation: string }
const entitySourceDocs = computed(()=>[...new Set((entityGraph.value?.edges||[]).filter(e=>entitySelectedNode.value&&(e.source===entitySelectedNode.value.id||e.target===entitySelectedNode.value.id)).map(e=>e.docId).filter((id):id is number=>typeof id==='number'&&id>0))])
const getEntityNeighbors = (id: number): EntityNeighbor[] => {
  if (!entityGraph.value) return []
  const result: EntityNeighbor[] = []
  for (const e of entityGraph.value.edges) {
    if (e.source === id) {
      const t = entityGraph.value.nodes.find((n) => n.id === e.target)
      if (t) result.push({ id: t.id, name: t.name, relation: e.relation })
    } else if (e.target === id) {
      const s = entityGraph.value.nodes.find((n) => n.id === e.source)
      if (s) result.push({ id: s.id, name: s.name, relation: e.relation })
    }
  }
  return result
}

// Phase 2-B：选中节点的掌握度 + 跳转详情
const MASTERY_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '未学习', LEARNING: '学习中', WEAK: '薄弱', MASTERED: '已掌握', REVIEW_REQUIRED: '需复习',
}
const masteryStatusLabel = (s?: string) => masteryStatus.value === 'error' ? '掌握度暂不可用' : MASTERY_STATUS_LABELS[s || 'NOT_STARTED'] || '未学习'
const selectedNodeMastery = computed(() => masteryStatus.value === 'ready' && entitySelectedNode.value ? masteryMap.value[entitySelectedNode.value.id] : undefined)
const openMasteryDetail = (id: number) => router.push(`/learning/knowledge-mastery/${id}`)

// ===== 初始化 =====
onMounted(() => {
  void loadGraph()
  if(currentView.value==='entity')void fetchEntityGraph()
  // 预加载分类列表，供实体图谱按分类筛选
  void loadCategories()
})
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.4s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.seg-btn { background: transparent; color: var(--kb-muted-foreground); border: none; cursor: pointer; transition: all 0.15s; }
.seg-btn:hover { color: var(--kb-foreground); }
.seg-btn.active { background: var(--kb-card); color: var(--kb-primary); box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
.header-btn { background: var(--kb-card); color: var(--kb-foreground); border-color: var(--kb-border); }
.header-btn:hover { background: var(--kb-muted); }
.kb-select, .kb-input { background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground); }

.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--kb-muted-foreground); pointer-events: none; }
.detail-panel { position: sticky; top: 5rem; }
.tech-svg { display: block; }
@media (max-width: 1024px) { .detail-panel { position: static !important; } }
/* Phase B: keep the existing SVG engine; contain canvas transforms locally. */
.knowledge-graph-page { min-width:0; }
.graph-selected-details { min-width:0; overflow-wrap:anywhere; }
.segmented { flex-wrap:wrap; }
.graph-search { width:100%;min-width:0; }.graph-search .graph-search-input{padding-inline-start:var(--kb-space-8)}
.category-graph-layout { display:grid;grid-template-columns:minmax(0,1fr) minmax(14rem,18rem);gap:var(--kb-space-4);align-items:start; }
.category-graph-canvas { overflow:hidden;min-width:0;user-select:none;cursor:grab; }
.category-graph-canvas:active { cursor:grabbing; }
.graph-node:focus-visible { outline:none; }
.graph-node:focus-visible circle { stroke:var(--kb-foreground);stroke-width:3; }
.detail-panel { min-width:0;overflow-wrap:anywhere; }
.knowledge-graph-page :is(button,a):focus-visible { outline:2px solid var(--kb-ring);outline-offset:2px; }
@media(max-width:1100px){.category-graph-layout{grid-template-columns:minmax(0,1fr)}.category-graph-layout .detail-panel{position:static}}

</style>
