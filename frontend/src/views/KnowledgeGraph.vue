<template>
  <div class="animate-fade-in knowledge-graph-page">
    <!-- ===== 页面标题区 ===== -->
    <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
      <div>
        <h1 class="kb-h1">知识图谱</h1>
        <p class="kb-body-sm mt-1">可视化技术栈依赖，图解编程概念</p>
      </div>
    </div>

    <!-- ===== 视图切换 Tab ===== -->
    <div class="flex items-center gap-1 p-1 rounded-lg segmented mb-4" style="background: var(--kb-muted);">
      <button
        v-for="tab in viewTabs"
        :key="tab.value"
        type="button"
        class="flex items-center gap-1.5 h-8 px-4 rounded-md text-sm font-medium seg-btn"
        :class="{ active: currentView === tab.value }"
        @click="switchView(tab.value)"
      >
        <Icon :name="tab.icon" :size="14" />
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <!-- ===== Tab1: 分类-文档图谱（原功能） ===== -->
    <template v-if="currentView === 'category'">
      <!-- 工具栏 -->
      <div class="rounded-lg p-4 mb-4 border toolbar-card" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-3 flex-wrap">
            <select v-model="selectedKb" class="h-9 px-3 rounded-lg text-sm border outline-none cursor-pointer kb-select">
              <option value="all">全部知识库</option>
              <option v-for="kb in kbList" :key="kb" :value="kb">{{ kb }}</option>
            </select>
            <div class="relative w-72">
              <Icon name="search" :size="16" class="search-icon" />
              <input v-model="searchKeyword" type="text" placeholder="搜索节点…" class="w-full h-9 pl-9 pr-3 rounded-lg text-sm border outline-none kb-input" />
            </div>
          </div>
          <div class="flex items-center gap-3">
            <div class="flex items-center gap-1 p-1 rounded-lg border" style="border-color: var(--kb-border);">
              <button type="button" class="w-7 h-7 rounded-md" @click="zoom = Math.max(0.5, zoom - 0.1)">
                <Icon name="zoom-out" :size="14" />
              </button>
              <span class="text-sm font-medium px-1 min-w-[36px] text-center tabular-nums">{{ Math.round(zoom * 100) }}%</span>
              <button type="button" class="w-7 h-7 rounded-md" @click="zoom = Math.min(2, zoom + 0.1)">
                <Icon name="zoom-in" :size="14" />
              </button>
            </div>
            <button type="button" class="h-9 px-4 rounded-lg text-sm font-medium border header-btn" @click="handleExport">
              <Icon name="download" :size="14" />
              <span>导出</span>
            </button>
            <button type="button" class="h-9 px-4 rounded-lg text-sm font-medium border header-btn" @click="loadGraph">
              <Icon name="refresh-cw" :size="14" />
              <span>刷新</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="rounded-lg border p-6 flex items-center justify-center mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <span class="inline-block w-5 h-5 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
        <span class="ml-2 text-sm" style="color: var(--kb-muted-foreground);">图谱加载中…</span>
      </div>

      <template v-else>
        <div class="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
          <div
            class="rounded-lg border relative"
            style="background: var(--kb-card); border-color: var(--kb-border); min-height: 560px; touch-action: none;"
            @touchstart="onTouchStart"
            @touchmove="onTouchMove"
            @touchend="onTouchEnd"
          >
            <button
              type="button"
              class="absolute top-2 right-2 z-10 px-2 py-1 rounded-md text-xs border"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-muted-foreground);"
              @click="resetView"
            >重置视图</button>
            <svg width="100%" height="520" viewBox="0 0 800 520" xmlns="http://www.w3.org/2000/svg" :style="{ transform: `translate(${panX}px, ${panY}px) scale(${zoom})`, transformOrigin: 'center center' }" class="transition-transform duration-200">
              <defs>
                <pattern id="kb-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                  <path d="M 20 0 L 0 0 0 20" fill="none" stroke="var(--kb-border)" stroke-width="0.5" opacity="0.5" />
                </pattern>
              </defs>
              <rect width="800" height="520" fill="url(#kb-grid)" />
              <g v-for="(edge, i) in renderEdges" :key="'e' + i">
                <line :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" stroke="var(--kb-border)" stroke-width="1.5" :opacity="selectedNode && !isEdgeConnected(edge) ? 0.3 : 1" />
              </g>
              <g v-for="node in level2Nodes" :key="node.id" class="cursor-pointer" @click="selectNode(node)">
                <circle :cx="node.x" :cy="node.y" r="18" :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted)' : 'var(--kb-card)'" stroke="var(--kb-border)" stroke-width="1.5" :opacity="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 0.4 : 1" />
                <text :x="node.x" :y="node.y" text-anchor="middle" dominant-baseline="central" font-size="11" :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted-foreground)' : 'var(--kb-card-foreground)'" >{{ node.label.length > 6 ? node.label.slice(0, 6) + '…' : node.label }}</text>
              </g>
              <g v-for="node in level1Nodes" :key="node.id" class="cursor-pointer" @click="selectNode(node)">
                <circle :cx="node.x" :cy="node.y" r="24" fill="rgba(59,111,224,0.12)" stroke="var(--kb-primary)" stroke-width="1.5" :opacity="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 0.4 : 1" />
                <text :x="node.x" :y="node.y" text-anchor="middle" dominant-baseline="central" font-size="12" font-weight="600" :fill="selectedNode && selectedNode.id !== node.id && !isNodeConnected(node.id) ? 'var(--kb-muted-foreground)' : 'var(--kb-primary)'" >{{ node.label.length > 4 ? node.label.slice(0, 4) : node.label }}</text>
              </g>
              <g v-for="node in centerNodes" :key="node.id" class="cursor-pointer" @click="selectNode(node)">
                <circle :cx="node.x" :cy="node.y" r="36" fill="var(--kb-primary)" :opacity="selectedNode && selectedNode.id !== node.id ? 0.5 : 1" />
                <text :x="node.x" :y="node.y" text-anchor="middle" dominant-baseline="central" font-size="14" font-weight="600" fill="var(--kb-primary-foreground)" >{{ node.label }}</text>
              </g>
              <g transform="translate(610, 478)">
                <rect x="0" y="0" width="180" height="32" rx="6" fill="var(--kb-card)" stroke="var(--kb-border)" stroke-width="1" />
                <circle cx="14" cy="16" r="5" fill="var(--kb-primary)" />
                <text x="22" y="16" dominant-baseline="central" font-size="10" fill="var(--kb-muted-foreground)">核心</text>
                <circle cx="54" cy="16" r="5" fill="rgba(59,111,224,0.12)" stroke="var(--kb-primary)" stroke-width="1" />
                <text x="62" y="16" dominant-baseline="central" font-size="10" fill="var(--kb-muted-foreground)">分类</text>
                <circle cx="98" cy="16" r="5" fill="var(--kb-card)" stroke="var(--kb-border)" stroke-width="1" />
                <text x="106" y="16" dominant-baseline="central" font-size="10" fill="var(--kb-muted-foreground)">文档</text>
              </g>
            </svg>
            <div v-if="!centerNodes.length" class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <Icon name="share-2" :size="40" style="color: var(--kb-muted-foreground);" />
              <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">暂无图谱数据</p>
            </div>
          </div>
          <!-- 详情面板 -->
          <div class="rounded-lg border p-5 detail-panel" style="background: var(--kb-card); border-color: var(--kb-border); align-self: start;">
            <template v-if="selectedNode">
              <h3 class="kb-h3">{{ selectedNode.label }}</h3>
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
                  <Icon name="file-text" :size="14" style="color: var(--kb-muted-foreground);" />
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
    </template>

    <!-- ===== Tab2: 技术栈依赖图谱 ===== -->
    <template v-else-if="currentView === 'tech'">
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 flex-wrap">
          <input v-model="techTopic" type="text" placeholder="输入技术主题，如：Spring Boot、Python AI、前端开发" class="flex-1 min-w-[260px] h-10 px-3 rounded-lg text-sm border outline-none kb-input" @keyup.enter="loadTechGraph" />
          <select v-model="techCategoryId" class="h-10 px-3 rounded-lg text-sm border outline-none cursor-pointer kb-select">
            <option :value="null">通用</option>
            <option v-for="cat in kbCategoryList" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
          <button type="button" class="h-10 px-5 rounded-lg text-sm font-medium" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="loadTechGraph" :disabled="techLoading">
            <Icon v-if="!techLoading" name="sparkles" :size="14" />
            <span>{{ techLoading ? '生成中…' : 'AI 生成技术图谱' }}</span>
          </button>
          <button type="button" class="h-10 px-4 rounded-lg text-sm font-medium border header-btn" @click="resetTechGraph">
            <Icon name="rotate-ccw" :size="14" />
            <span>重置</span>
          </button>
        </div>
        <p v-if="techGraph?.generatedAt" class="text-xs mt-2" style="color: var(--kb-muted-foreground);">
          生成于 {{ techGraph.generatedAt }} · 共 {{ techGraph.nodes.length }} 个技术 · {{ techGraph.edges.length }} 条依赖
        </p>
      </div>

      <div v-if="techLoading" class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
        <span class="inline-block w-6 h-6 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
        <p class="text-sm" style="color: var(--kb-muted-foreground);">AI 正在分析技术栈依赖关系…</p>
      </div>

      <template v-else-if="techGraph && techGraph.nodes.length">
        <!-- 技术图谱 SVG -->
        <div class="rounded-lg border p-4 mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <svg width="100%" height="520" :viewBox="techViewBox" xmlns="http://www.w3.org/2000/svg" class="tech-svg">
            <!-- 连线 -->
            <g v-for="(edge, i) in techEdges" :key="'te' + i">
              <line :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
                :stroke="edge.color"
                :stroke-width="edge.width"
                :stroke-dasharray="edge.relation === 'PREREQUISITE' ? 'none' : '6,3'"
                :opacity="edge.opacity"
                class="transition-opacity duration-300"
              />
              <!-- 箭头 -->
              <polygon
                v-if="edge.relation === 'PREREQUISITE'"
                :points="edge.arrowPoints"
                :fill="edge.color"
              />
            </g>
            <!-- 节点 -->
            <g v-for="node in techRenderNodes" :key="node.id" class="cursor-pointer" @click="selectTechNode(node)">
              <circle :cx="node.x" :cy="node.y" :r="node.r"
                :fill="node.bgColor"
                :stroke="node.strokeColor"
                stroke-width="2"
                :opacity="techSelectedNode && techSelectedNode.id !== node.id && !isTechNodeConnected(node.id) ? 0.25 : 1"
                class="transition-opacity duration-300"
              />
              <text :x="node.x" :y="node.y" text-anchor="middle" dominant-baseline="central"
                :font-size="node.fontSize"
                :font-weight="node.difficulty === 3 ? '700' : '500'"
                :fill="node.textColor"
              >{{ node.name }}</text>
              <text :x="node.x" :y="node.y + node.r + 14" text-anchor="middle" font-size="10" :fill="node.strokeColor">{{ node.categoryLabel }}</text>
            </g>
          </svg>
          <!-- 图例 -->
          <div class="flex items-center gap-4 mt-3 flex-wrap text-xs" style="color: var(--kb-muted-foreground);">
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #3b6fe0;"/> 编程语言</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #10b981;"/> 框架</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #8b5cf6;"/> 工具</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #f59e0b;"/> 数据库</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #ef4444;"/> 算法</span>
            <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded-full" style="background: #06b6d4;"/> 平台</span>
            <span class="flex items-center gap-1 ml-2">— 前置依赖</span>
            <span class="flex items-center gap-1">╌ 组件/包含</span>
          </div>
        </div>

        <!-- 技术节点详情 -->
        <div v-if="techSelectedNode" class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
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

      <template v-else>
        <div class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="search" :size="32" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm" style="color: var(--kb-muted-foreground);">输入技术主题，AI 将生成技术栈依赖图谱</p>
        </div>
      </template>
    </template>

    <!-- ===== Tab3: 概念可视化图解 ===== -->
    <template v-else-if="currentView === 'concept'">
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 flex-wrap">
          <input v-model="conceptInput" type="text" placeholder="输入编程/AI 概念，如：变量、面向对象、RAG、快速排序" class="flex-1 min-w-[260px] h-10 px-3 rounded-lg text-sm border outline-none kb-input" @keyup.enter="loadConceptDiagram" />
          <button type="button" class="h-10 px-5 rounded-lg text-sm font-medium" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="loadConceptDiagram" :disabled="conceptLoading">
            <Icon v-if="!conceptLoading" name="sparkles" :size="14" />
            <span>{{ conceptLoading ? '生成中…' : 'AI 生成图解' }}</span>
          </button>
        </div>
        <div class="flex items-center gap-2 mt-3 flex-wrap">
          <span class="text-xs" style="color: var(--kb-muted-foreground);">快速尝试：</span>
          <button v-for="c in quickConcepts" :key="c" type="button" class="px-2 py-0.5 rounded text-xs border hover:opacity-80" style="border-color: var(--kb-border);" @click="conceptInput = c; loadConceptDiagram()">{{ c }}</button>
        </div>
        <!-- 最近浏览 -->
        <div v-if="conceptHistory.length" class="flex items-center gap-2 mt-2 flex-wrap">
          <span class="text-xs" style="color: var(--kb-muted-foreground);">最近浏览：</span>
          <button v-for="h in conceptHistory" :key="h" type="button" class="px-2 py-0.5 rounded text-xs" style="background: var(--kb-muted); color: var(--kb-foreground);" @click="conceptInput = h; loadConceptDiagram()">{{ h }}</button>
        </div>
      </div>

      <div v-if="conceptLoading" class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
        <span class="inline-block w-6 h-6 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
        <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ conceptRegenerating ? 'AI 正在重新生成图解…' : 'AI 正在绘制概念图解…' }}</p>
      </div>

      <template v-else-if="conceptDiagram">
        <div class="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-4">
          <!-- 左栏：图解 + 代码示例 -->
          <div class="space-y-4">
            <div class="rounded-lg border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-2">
                  <h3 class="kb-h3">{{ conceptDiagram.concept }}</h3>
                  <span v-if="conceptDiagram.difficulty" class="px-2 py-0.5 rounded text-xs font-medium" :style="{ background: difficultyBg(conceptDiagram.difficulty), color: difficultyColor(conceptDiagram.difficulty) }">
                    {{ difficultyLabel(conceptDiagram.difficulty) }}
                  </span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="px-2 py-0.5 rounded text-xs" style="background: var(--kb-muted); color: var(--kb-muted-foreground);">{{ conceptDiagram.diagramType }}</span>
                  <button type="button" class="flex items-center gap-1 px-2 py-1 rounded-lg text-xs border transition-colors hover:opacity-80" style="border-color: var(--kb-border); color: var(--kb-muted-foreground);" :disabled="conceptRegenerating" @click="regenerateConceptDiagram">
                    <Icon name="rotate-ccw" :size="12" :class="conceptRegenerating ? 'animate-spin' : ''" />
                    <span>{{ conceptRegenerating ? '生成中…' : '重新生成' }}</span>
                  </button>
                </div>
              </div>
              <MermaidDiagram :code="conceptDiagram.mermaidCode" :id="'concept-' + conceptDiagram.concept" />
            </div>
            <!-- 代码示例 -->
            <div v-if="conceptDiagram.codeExample" class="rounded-lg border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
              <div class="flex items-center gap-2 mb-2">
                <Icon name="code" :size="14" style="color: var(--kb-primary);" />
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

      <template v-else>
        <div class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="image" :size="32" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm" style="color: var(--kb-muted-foreground);">输入概念名称，AI 将生成可视化图解</p>
        </div>
      </template>
    </template>

    <!-- ===== Tab4: 实体关系知识图谱（A-RAG-04：AI 从文档抽取实体+关系） ===== -->
    <template v-else>
      <div class="rounded-lg p-4 mb-4 border" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 flex-wrap">
          <select v-model="entityCategoryId" class="h-10 px-3 rounded-lg text-sm border kb-select">
            <option :value="null">全部分类</option>
            <option v-for="cat in graphCategories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
          <button type="button" class="h-10 px-5 rounded-lg text-sm font-medium flex items-center gap-1.5" style="background: var(--kb-primary); color: var(--kb-primary-foreground);" @click="buildEntityGraph" :disabled="entityBuilding">
            <Icon v-if="!entityBuilding" name="sparkles" :size="14" />
            <span>{{ entityBuilding ? '构建中…' : '构建知识图谱' }}</span>
          </button>
          <button type="button" class="h-10 px-4 rounded-lg text-sm border flex items-center gap-1.5 header-btn" @click="fetchEntityGraph" :disabled="entityLoading">
            <Icon name="refresh-cw" :size="14" :class="entityLoading ? 'animate-spin' : ''" />
            <span>刷新</span>
          </button>
          <span class="text-xs" style="color: var(--kb-muted-foreground);">AI 从文档抽取实体与关系，构建真正知识图谱</span>
        </div>
        <p v-if="entityGraph?.generatedAt" class="text-xs mt-2" style="color: var(--kb-muted-foreground);">
          生成于 {{ entityGraph.generatedAt }} · 共 {{ entityGraph.nodes.length }} 个实体 · {{ entityGraph.edges.length }} 条关系
        </p>
      </div>

      <div v-if="entityLoading" class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
        <span class="inline-block w-6 h-6 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
        <p class="text-sm" style="color: var(--kb-muted-foreground);">正在加载实体关系知识图谱…</p>
      </div>

      <template v-else-if="entityGraph && entityGraph.nodes.length">
        <div class="rounded-lg border p-4 mb-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <svg width="100%" height="520" :viewBox="entityViewBox" xmlns="http://www.w3.org/2000/svg" class="tech-svg">
            <g v-for="(edge, i) in entityEdges" :key="'ee' + i">
              <line :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" :stroke="edge.color" stroke-width="1.5" :opacity="edge.opacity" class="transition-opacity duration-300" />
              <text :x="edge.mx" :y="edge.my" text-anchor="middle" font-size="9" :fill="edge.color">{{ relationLabel(edge.relation) }}</text>
            </g>
            <g v-for="node in entityRenderNodes" :key="node.id" class="cursor-pointer" @click="selectEntityNode(node)">
              <circle :cx="node.x" :cy="node.y" :r="node.r" :fill="node.bgColor" :stroke="node.masteryColor || node.strokeColor" stroke-width="node.masteryColor ? 3 : 2" :opacity="entitySelectedNode && entitySelectedNode.id !== node.id && !isEntityNodeConnected(node.id) ? 0.25 : 1" class="transition-opacity duration-300" />
              <text :x="node.x" :y="node.y" text-anchor="middle" dominant-baseline="central" font-size="11" font-weight="600" :fill="node.textColor">{{ node.name }}</text>
              <!-- Phase 2-B：掌握状态指示点 -->
              <g v-if="node.masteryColor">
                <circle :cx="node.x + node.r * 0.72" :cy="node.y - node.r * 0.72" :r="5" :fill="node.masteryColor" stroke="#fff" stroke-width="1.5" />
                <text :x="node.x" :y="node.y + node.r + 11" text-anchor="middle" font-size="9" font-weight="700" :fill="node.masteryColor">{{ node.masteryScore }}</text>
              </g>
            </g>
          </svg>
          <div class="flex items-center gap-3 mt-3 flex-wrap text-xs" style="color: var(--kb-muted-foreground);">
            <span v-for="(color, type) in entityTypeLegend" :key="type" class="flex items-center gap-1">
              <span class="inline-block w-3 h-3 rounded-full" :style="{ background: color.stroke }" /> {{ entityTypeName(type) }}
            </span>
          </div>
        </div>

        <div v-if="entitySelectedNode" class="rounded-lg border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center gap-3 mb-3">
            <span class="inline-block w-10 h-10 rounded-full flex items-center justify-center text-xs font-bold text-white" :style="{ background: entityTypeColor(entitySelectedNode.type).stroke }">{{ entitySelectedNode.name.slice(0, 2) }}</span>
            <div>
              <h3 class="kb-h3">{{ entitySelectedNode.name }}</h3>
              <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ entityTypeName(entitySelectedNode.type) }} · {{ entitySelectedNode.categoryName || '未分类' }}</span>
            </div>
          </div>
          <p v-if="entitySelectedNode.description" class="text-sm mb-3" style="color: var(--kb-foreground);">{{ entitySelectedNode.description }}</p>
          <!-- Phase 2-B：掌握度概览 -->
          <div v-if="selectedNodeMastery" class="rounded p-3 mb-3 text-sm" style="background: var(--kb-muted);">
            <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">我的掌握度</div>
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-semibold" :style="{ color: masteryStatusColor(selectedNodeMastery.learningStatus) }">
                {{ selectedNodeMastery.masteryScore }} 分 · {{ masteryStatusLabel(selectedNodeMastery.learningStatus) }}
              </span>
              <span class="text-xs" style="color: var(--kb-muted-foreground);">置信 {{ selectedNodeMastery.confidenceScore }}% · 遗忘风险 {{ selectedNodeMastery.forgettingRisk }}</span>
              <button type="button" class="ml-auto px-2 py-0.5 rounded text-xs font-medium" style="border:1px solid var(--kb-primary); color: var(--kb-primary); background: rgba(59,111,224,.05);" @click="openMasteryDetail(entitySelectedNode.id)">查看详情</button>
            </div>
          </div>
          <div class="rounded p-3 mb-3 text-sm" style="background: var(--kb-muted);">
            <div class="text-xs mb-1" style="color: var(--kb-muted-foreground);">关联关系</div>
            <div class="font-semibold">{{ countEntityRelations(entitySelectedNode.id) }} 条 · 重要度 {{ entitySelectedNode.weight || 1 }}</div>
          </div>
          <div v-if="getEntityNeighbors(entitySelectedNode.id).length" class="mb-2">
            <h4 class="text-xs font-semibold mb-1" style="color: var(--kb-muted-foreground);">关联实体</h4>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="nb in getEntityNeighbors(entitySelectedNode.id)" :key="nb.id" type="button" class="px-2 py-0.5 rounded text-xs border transition-colors hover:opacity-80" style="border-color: var(--kb-border);" @click="selectEntityById(nb.id)">{{ nb.name }} <span class="opacity-60">· {{ relationLabel(nb.relation) }}</span></button>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="rounded-lg border p-12 flex flex-col items-center justify-center gap-3" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="link" :size="32" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm" style="color: var(--kb-muted-foreground);">知识图谱尚未构建，点击「构建知识图谱」让 AI 从文档抽取实体与关系</p>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import MermaidDiagram from '@/components/Knowledge/MermaidDiagram.vue'
import { notify, getApiError } from '@/utils/toast'
import { knowledgeApi, categoriesApi, learningApi } from '@/api'
import type {
  KnowledgeGraphVO, GraphNodeVO,
  TechGraphVO, TechNodeVO, TechEdgeVO,
  ConceptDiagramVO,
  EntityNodeVO, EntityEdgeVO, EntityGraphVO, CategoryVO,
  KnowledgeMasteryVO,
} from '@/api/types'

const router = useRouter()

// ===== 视图切换 =====
type ViewTab = 'category' | 'tech' | 'concept' | 'entity'
const currentView = ref<ViewTab>('category')
const viewTabs = [
  { value: 'category' as const, label: '分类图谱', icon: 'share-2' },
  { value: 'tech' as const, label: '技术依赖图', icon: 'git-branch' },
  { value: 'concept' as const, label: '概念图解', icon: 'image' },
  { value: 'entity' as const, label: '知识实体图', icon: 'link' },
]

// 提前声明 fetchEntityGraph / buildEntityGraph，供 switchView 在切换 Tab 时调用
async function fetchEntityGraph() {
  entityLoading.value = true
  entitySelectedNode.value = null
  try {
    entityGraph.value = await knowledgeApi.entityGraph(entityCategoryId.value || undefined)
    // Phase 2-B：并行加载知识点掌握度，叠加到实体节点（失败仅告警，不阻断图谱）
    void loadEntityMastery()
  } catch (e: unknown) {
    notify(getApiError(e, '实体图谱加载失败'), 'error')
  } finally {
    entityLoading.value = false
  }
}

// Phase 2-B：知识点掌握度映射（kg_entity.id -> mastery），用于实体图谱叠加掌握状态
const masteryMap = ref<Record<number, KnowledgeMasteryVO>>({})
async function loadEntityMastery() {
  try {
    const list = await learningApi.knowledgeMastery().catch(() => [] as KnowledgeMasteryVO[])
    const map: Record<number, KnowledgeMasteryVO> = {}
    for (const m of list) map[m.knowledgeId] = m
    masteryMap.value = map
  } catch {
    // 掌握度缺失不影响图谱渲染
  }
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
const loading = ref(false)
const graph = ref<KnowledgeGraphVO>({ nodes: [], edges: [] })
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)

// 触摸手势状态
let touchState: { mode: 'none' | 'pan' | 'pinch'; startX: number; startY: number; startPanX: number; startPanY: number; startDist: number; startZoom: number } = {
  mode: 'none', startX: 0, startY: 0, startPanX: 0, startPanY: 0, startDist: 0, startZoom: 1,
}

function onTouchStart(e: TouchEvent) {
  if (e.touches.length === 1) {
    touchState = { mode: 'pan', startX: e.touches[0].clientX, startY: e.touches[0].clientY, startPanX: panX.value, startPanY: panY.value, startDist: 0, startZoom: 1 }
  } else if (e.touches.length === 2) {
    const dx = e.touches[0].clientX - e.touches[1].clientX
    const dy = e.touches[0].clientY - e.touches[1].clientY
    touchState = { mode: 'pinch', startX: 0, startY: 0, startPanX: 0, startPanY: 0, startDist: Math.hypot(dx, dy), startZoom: zoom.value }
  }
}

function onTouchMove(e: TouchEvent) {
  e.preventDefault()
  if (touchState.mode === 'pan' && e.touches.length === 1) {
    panX.value = touchState.startPanX + (e.touches[0].clientX - touchState.startX)
    panY.value = touchState.startPanY + (e.touches[0].clientY - touchState.startY)
  } else if (touchState.mode === 'pinch' && e.touches.length === 2) {
    const dx = e.touches[0].clientX - e.touches[1].clientX
    const dy = e.touches[0].clientY - e.touches[1].clientY
    const dist = Math.hypot(dx, dy)
    if (touchState.startDist > 0) {
      const ratio = dist / touchState.startDist
      zoom.value = Math.max(0.5, Math.min(2, touchState.startZoom * ratio))
    }
  }
}

function onTouchEnd() {
  touchState.mode = 'none'
}

function resetView() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}
const selectedNode = ref<RenderNode | null>(null)
const selectedKb = ref('all')
const searchKeyword = ref('')

interface RenderNode {
  id: string; label: string; level: string; type: string;
  x: number; y: number; docCount: number; relationCount: number;
  docs: { id: string; title: string; tag: string }[];
}

const kbList = computed(() => {
  const list = new Set<string>()
  graph.value.nodes.forEach((n) => { if (n.type === 'category' && n.label) list.add(n.label) })
  return Array.from(list).slice(0, 10)
})

const renderNodes = computed<RenderNode[]>(() => {
  const categories = graph.value.nodes.filter((n) => n.type === 'category')
  const docs = graph.value.nodes.filter((n) => n.type === 'doc')
  const kw = searchKeyword.value.trim().toLowerCase()
  const filterFn = (n: GraphNodeVO) => !kw || n.label.toLowerCase().includes(kw)
  const center = categories[0]
  const centerId = center?.id || 'center'
  const centerLabel = center?.label || '知识库'
  const nodes: RenderNode[] = []

  nodes.push({ id: centerId, label: centerLabel, level: 'center', type: 'category', x: 400, y: 260, docCount: docs.length, relationCount: categories.length + docs.length, docs: docs.slice(0, 4).map(d => ({ id: d.id, title: d.label, tag: '文档' })) })

  const l1 = categories.slice(1).filter(filterFn)
  l1.forEach((n, i) => {
    const angle = (i / Math.max(l1.length, 1)) * Math.PI * 2 - Math.PI / 2
    nodes.push({ id: n.id, label: n.label, level: 'level1', type: n.type, x: 400 + Math.cos(angle) * 160, y: 260 + Math.sin(angle) * 160, docCount: 0, relationCount: 0, docs: [] })
  })

  const l2 = docs.filter(filterFn)
  l2.forEach((n, i) => {
    const angle = (i / Math.max(l2.length, 1)) * Math.PI * 2 - Math.PI / 2
    nodes.push({ id: n.id, label: n.label, level: 'level2', type: n.type, x: 400 + Math.cos(angle) * 240, y: 260 + Math.sin(angle) * 240, docCount: 0, relationCount: 1, docs: [{ id: n.id, title: n.label, tag: '文档' }] })
  })

  if (kw) return nodes.filter(n => n.label.toLowerCase().includes(kw) || n.level === 'center')
  return nodes
})

const centerNodes = computed(() => renderNodes.value.filter(n => n.level === 'center'))
const level1Nodes = computed(() => renderNodes.value.filter(n => n.level === 'level1'))
const level2Nodes = computed(() => renderNodes.value.filter(n => n.level === 'level2'))

const renderEdges = computed(() => {
  const map = new Map<string, { x: number; y: number }>()
  renderNodes.value.forEach(n => map.set(n.id, { x: n.x, y: n.y }))
  const result: { x1: number; y1: number; x2: number; y2: number; source: string; target: string }[] = []
  for (const e of graph.value.edges) {
    const s = map.get(e.source); const t = map.get(e.target)
    if (s && t) result.push({ x1: s.x, y1: s.y, x2: t.x, y2: t.y, source: e.source, target: e.target })
  }
  if (result.length === 0 && renderNodes.value.length > 1) {
    const center = centerNodes.value[0]
    if (center) {
      level1Nodes.value.forEach(n => result.push({ x1: center.x, y1: center.y, x2: n.x, y2: n.y, source: center.id, target: n.id }))
      level2Nodes.value.forEach(n => {
        const nearest = level1Nodes.value[0]
        if (nearest) result.push({ x1: nearest.x, y1: nearest.y, x2: n.x, y2: n.y, source: nearest.id, target: n.id })
        else result.push({ x1: center.x, y1: center.y, x2: n.x, y2: n.y, source: center.id, target: n.id })
      })
    }
  }
  return result
})

const isNodeConnected = (nodeId: string) => {
  if (!selectedNode.value) return true
  return graph.value.edges.some(e => (e.source === selectedNode.value!.id && e.target === nodeId) || (e.target === selectedNode.value!.id && e.source === nodeId))
}
const isEdgeConnected = (edge: { source: string; target: string }) => {
  if (!selectedNode.value) return true
  return edge.source === selectedNode.value.id || edge.target === selectedNode.value.id
}
const selectNode = (node: RenderNode) => { selectedNode.value = selectedNode.value?.id === node.id ? null : node }
const levelLabel = (level: string) => level === 'center' ? '核心主题' : level === 'level1' ? '分类' : '文档'
const goToDoc = (nodeId: string) => { const docId = parseInt(nodeId.split('_')[1] || nodeId); if (!isNaN(docId)) router.push(`/doc/${docId}`) }

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

async function loadGraph() {
  loading.value = true
  try { graph.value = await knowledgeApi.graph() }
  catch (e: unknown) { notify(getApiError(e, '图谱加载失败'), 'error') }
  finally { loading.value = false }
}

// ===== Tab2: 技术栈依赖图谱 =====
const techLoading = ref(false)
const techGraph = ref<TechGraphVO | null>(null)
const techTopic = ref('Spring Boot')
const techCategoryId = ref<number | null>(null)
const techSelectedNode = ref<TechNodeVO | null>(null)

const kbCategoryList = computed(() => {
  const cats = new Set<string>()
  graph.value.nodes.forEach(n => { if (n.type === 'category') cats.add(n.id) })
  return graph.value.nodes.filter(n => n.type === 'category').map(n => ({ id: n.id, name: n.label }))
})

interface TechRenderNode extends TechNodeVO {
  x: number; y: number; r: number; bgColor: string; strokeColor: string; textColor: string; fontSize: number
}
interface TechRenderEdge extends TechEdgeVO {
  x1: number; y1: number; x2: number; y2: number; color: string; width: number; opacity: number; arrowPoints: string
}

const CATEGORY_COLORS: Record<string, { bg: string; stroke: string; text: string }> = {
  LANGUAGE:   { bg: 'rgba(59,111,224,0.15)', stroke: '#3b6fe0', text: '#3b6fe0' },
  FRAMEWORK:  { bg: 'rgba(16,185,129,0.15)', stroke: '#10b981', text: '#10b981' },
  TOOL:       { bg: 'rgba(139,92,246,0.15)', stroke: '#8b5cf6', text: '#8b5cf6' },
  DATABASE:   { bg: 'rgba(245,158,11,0.15)', stroke: '#f59e0b', text: '#f59e0b' },
  ALGORITHM:  { bg: 'rgba(239,68,68,0.15)', stroke: '#ef4444', text: '#ef4444' },
  PLATFORM:   { bg: 'rgba(6,182,212,0.15)', stroke: '#06b6d4', text: '#06b6d4' },
}

const getTechCategoryColor = (cat: string) => CATEGORY_COLORS[cat] || CATEGORY_COLORS.TOOL

const techViewBox = computed(() => {
  const count = techGraph.value?.nodes.length || 0
  const cols = Math.max(3, Math.ceil(Math.sqrt(count)))
  const width = Math.max(600, cols * 180)
  return `0 0 ${width} 520`
})

const techRenderNodes = computed<TechRenderNode[]>(() => {
  if (!techGraph.value || !techGraph.value.nodes.length) return []
  const nodes = techGraph.value.nodes
  const count = nodes.length
  const cols = Math.max(3, Math.ceil(Math.sqrt(count)))
  const rows = Math.ceil(count / cols)
  const colWidth = Math.max(140, 600 / cols)
  const rowHeight = Math.max(80, 480 / rows)
  const offsetX = Math.max(30, (600 - cols * colWidth) / 2)
  const offsetY = Math.max(40, (500 - rows * rowHeight) / 2)

  return nodes.map((n, i) => {
    const col = i % cols
    const row = Math.floor(i / cols)
    const x = offsetX + col * colWidth + colWidth / 2
    const y = offsetY + row * rowHeight + rowHeight / 2
    const colors = CATEGORY_COLORS[n.category] || CATEGORY_COLORS.TOOL
    const r = n.difficulty === 3 ? 28 : n.difficulty === 2 ? 24 : 20
    const fontSize = n.difficulty === 3 ? 12 : 11
    return { ...n, x, y, r, bgColor: colors.bg, strokeColor: colors.stroke, textColor: colors.text, fontSize }
  })
})

const techEdges = computed<TechRenderEdge[]>(() => {
  if (!techGraph.value) return []
  const map = new Map<string, TechRenderNode>()
  techRenderNodes.value.forEach(n => map.set(n.id, n))
  const result: TechRenderEdge[] = []
  for (const e of techGraph.value.edges) {
    const s = map.get(e.source); const t = map.get(e.target)
    if (!s || !t) continue
    const dx = t.x - s.x, dy = t.y - s.y
    const len = Math.sqrt(dx * dx + dy * dy) || 1
    const ux = dx / len, uy = dy / len
    const x1 = s.x + ux * s.r, y1 = s.y + uy * s.r
    const x2 = t.x - ux * (t.r + 4), y2 = t.y - uy * (t.r + 4)
    const color = e.relation === 'PREREQUISITE' ? '#3b6fe0' : e.relation === 'COMPONENT' ? '#10b981' : '#8b5cf6'
    const width = (e.strength || 1) * 0.6 + 0.6
    const opacity = techSelectedNode.value ? (e.source === techSelectedNode.value.id || e.target === techSelectedNode.value.id ? 1 : 0.15) : 0.8
    // 箭头
    const arrowSize = 6
    const arrowX = t.x - ux * t.r
    const arrowY = t.y - uy * t.r
    const perpX = -uy, perpY = ux
    const arrowPoints = `${arrowX},${arrowY} ${arrowX - ux * arrowSize + perpX * arrowSize * 0.6},${arrowY - uy * arrowSize + perpY * arrowSize * 0.6} ${arrowX - ux * arrowSize - perpX * arrowSize * 0.6},${arrowY - uy * arrowSize - perpY * arrowSize * 0.6}`
    result.push({ ...e, x1, y1, x2, y2, color, width, opacity, arrowPoints })
  }
  return result
})

const selectTechNode = (node: TechNodeVO) => {
  techSelectedNode.value = techSelectedNode.value?.id === node.id ? null : node
}
const isTechNodeConnected = (nodeId: string) => {
  if (!techSelectedNode.value) return true
  return techEdges.value.some(e => (e.source === techSelectedNode.value!.id && e.target === nodeId) || (e.target === techSelectedNode.value!.id && e.source === nodeId))
}
const countTechRelations = (id: string) => techGraph.value?.edges.filter(e => e.source === id || e.target === id).length || 0
const getTechPrerequisites = (id: string) => {
  if (!techGraph.value) return []
  const prereqIds = techGraph.value.edges.filter(e => e.target === id && e.relation === 'PREREQUISITE').map(e => e.source)
  return techGraph.value.nodes.filter(n => prereqIds.includes(n.id)).map(n => n.name)
}

async function loadTechGraph() {
  if (!techTopic.value.trim()) return
  techLoading.value = true
  techSelectedNode.value = null
  try {
    techGraph.value = await knowledgeApi.techGraph(techTopic.value.trim(), techCategoryId.value || undefined)
  } catch (e: unknown) {
    notify(getApiError(e, '技术图谱生成失败'), 'error')
  } finally {
    techLoading.value = false
  }
}

function resetTechGraph() {
  techTopic.value = 'Spring Boot'
  techGraph.value = null
  techSelectedNode.value = null
}

// ===== Tab3: 概念可视化图解 =====
const conceptLoading = ref(false)
const conceptRegenerating = ref(false)
const conceptDiagram = ref<ConceptDiagramVO | null>(null)
const conceptInput = ref('变量')
const quickConcepts = ['变量', '循环', '面向对象', '递归', 'RAG', '快速排序', 'React Hooks']
const conceptHistory = ref<string[]>([])

function difficultyLabel(d: number) {
  return d === 3 ? '进阶' : d === 2 ? '中等' : '入门'
}
function difficultyColor(d: number) {
  return d === 3 ? '#ef4444' : d === 2 ? '#f59e0b' : '#10b981'
}
function difficultyBg(d: number) {
  return d === 3 ? 'rgba(239,68,68,0.12)' : d === 2 ? 'rgba(245,158,11,0.12)' : 'rgba(16,185,129,0.12)'
}

async function loadConceptDiagram() {
  if (!conceptInput.value.trim()) return
  conceptLoading.value = true
  try {
    const concept = conceptInput.value.trim()
    conceptDiagram.value = await knowledgeApi.conceptDiagram(concept)
    // 记录浏览历史
    if (!conceptHistory.value.includes(concept)) {
      conceptHistory.value.unshift(concept)
      if (conceptHistory.value.length > 6) conceptHistory.value.pop()
    }
  } catch (e: unknown) {
    notify(getApiError(e, '图解生成失败'), 'error')
  } finally {
    conceptLoading.value = false
  }
}

/** 重新生成概念图解：删除旧缓存并让 AI 重新生成 */
async function regenerateConceptDiagram() {
  if (!conceptInput.value.trim() || conceptRegenerating.value) return
  conceptRegenerating.value = true
  conceptLoading.value = true
  try {
    const concept = conceptInput.value.trim()
    conceptDiagram.value = await knowledgeApi.regenerateConceptDiagram(concept)
    notify('图解已重新生成', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '重新生成失败'), 'error')
  } finally {
    conceptRegenerating.value = false
    conceptLoading.value = false
  }
}

// ===== Tab4: 实体关系知识图谱（A-RAG-04：AI 从文档抽取实体+关系） =====
const graphCategories = ref<CategoryVO[]>([])
const entityLoading = ref(false)
const entityBuilding = ref(false)
const entityGraph = ref<EntityGraphVO | null>(null)
const entitySelectedNode = ref<EntityNodeVO | null>(null)
const entityCategoryId = ref<number | null>(null)

const ENTITY_TYPE_COLORS: Record<string, { bg: string; stroke: string; text: string }> = {
  CONCEPT:    { bg: 'rgba(59,111,224,0.15)', stroke: '#3b6fe0', text: '#3b6fe0' },
  TECHNIQUE: { bg: 'rgba(16,185,129,0.15)', stroke: '#10b981', text: '#10b981' },
  TERM:      { bg: 'rgba(245,158,11,0.15)', stroke: '#f59e0b', text: '#f59e0b' },
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
const RELATION_COLORS: Record<string, string> = {
  RELATED_TO: '#94a3b8', PREREQUISITE: '#3b6fe0', IS_A: '#8b5cf6', PART_OF: '#10b981', USES: '#06b6d4', CONTRASTS: '#ef4444',
}
const entityTypeLegend = computed(() => ENTITY_TYPE_COLORS)

const entityTypeName = (t?: string) => ENTITY_TYPE_LABELS[t || 'OTHER'] || '其他'
const entityTypeColor = (t?: string) => ENTITY_TYPE_COLORS[t || 'OTHER'] || ENTITY_TYPE_COLORS.OTHER
const relationLabel = (r?: string) => RELATION_LABELS[r || 'RELATED_TO'] || '相关'

const entityViewBox = computed(() => {
  const count = entityGraph.value?.nodes.length || 0
  const cols = Math.max(3, Math.ceil(Math.sqrt(count)))
  const width = Math.max(700, cols * 160)
  return `0 0 ${width} 520`
})

interface EntityRenderNode extends EntityNodeVO { x: number; y: number; r: number; bgColor: string; strokeColor: string; textColor: string; masteryScore?: number; learningStatus?: string; masteryColor?: string }
interface EntityRenderEdge extends EntityEdgeVO { x1: number; y1: number; x2: number; y2: number; color: string; opacity: number; mx: number; my: number }

const MASTERY_STATUS_COLORS: Record<string, string> = {
  NOT_STARTED: '#64748b', LEARNING: '#3b6fe0', WEAK: '#EF4444', MASTERED: '#10B981', REVIEW_REQUIRED: '#F59E0B',
}
const masteryStatusColor = (s?: string) => MASTERY_STATUS_COLORS[s || 'NOT_STARTED'] || MASTERY_STATUS_COLORS.NOT_STARTED

const entityRenderNodes = computed<EntityRenderNode[]>(() => {
  if (!entityGraph.value) return []
  const nodes = entityGraph.value.nodes
  const count = nodes.length
  const cx = 400, cy = 260
  const radius = Math.min(220, 70 + count * 5)
  return nodes.map((n, i) => {
    const angle = (i / Math.max(count, 1)) * Math.PI * 2 - Math.PI / 2
    const x = cx + Math.cos(angle) * radius
    const y = cy + Math.sin(angle) * radius
    const w = n.weight || 1
    const r = Math.max(15, Math.min(34, 13 + w))
    const colors = ENTITY_TYPE_COLORS[n.type] || ENTITY_TYPE_COLORS.OTHER
    const mk = masteryMap.value[n.id]
    return {
      ...n, x, y, r, bgColor: colors.bg, strokeColor: colors.stroke, textColor: colors.text,
      masteryScore: mk?.masteryScore, learningStatus: mk?.learningStatus,
      masteryColor: mk ? masteryStatusColor(mk.learningStatus) : undefined,
    }
  })
})

const entityEdges = computed<EntityRenderEdge[]>(() => {
  if (!entityGraph.value) return []
  const map = new Map<number, EntityRenderNode>()
  entityRenderNodes.value.forEach((n) => map.set(n.id, n))
  const result: EntityRenderEdge[] = []
  for (const e of entityGraph.value.edges) {
    const s = map.get(e.source); const t = map.get(e.target)
    if (!s || !t) continue
    const dx = t.x - s.x, dy = t.y - s.y
    const len = Math.sqrt(dx * dx + dy * dy) || 1
    const ux = dx / len, uy = dy / len
    const x1 = s.x + ux * s.r, y1 = s.y + uy * s.r
    const x2 = t.x - ux * t.r, y2 = t.y - uy * t.r
    const mx = (x1 + x2) / 2, my = (y1 + y2) / 2
    const color = RELATION_COLORS[e.relation] || '#94a3b8'
    const opacity = entitySelectedNode.value ? (e.source === entitySelectedNode.value.id || e.target === entitySelectedNode.value.id ? 1 : 0.15) : 0.7
    result.push({ ...e, x1, y1, x2, y2, color, opacity, mx, my })
  }
  return result
})

const selectEntityNode = (node: EntityNodeVO) => {
  entitySelectedNode.value = entitySelectedNode.value?.id === node.id ? null : node
}
const selectEntityById = (id: number) => {
  entitySelectedNode.value = entityGraph.value?.nodes.find((n) => n.id === id) || null
}
const isEntityNodeConnected = (nodeId: number) => {
  if (!entitySelectedNode.value) return true
  return entityEdges.value.some((e) => (e.source === entitySelectedNode.value!.id && e.target === nodeId) || (e.target === entitySelectedNode.value!.id && e.source === nodeId))
}
const countEntityRelations = (id: number) => entityGraph.value?.edges.filter((e) => e.source === id || e.target === id).length || 0
interface EntityNeighbor { id: number; name: string; relation: string }
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
const masteryStatusLabel = (s?: string) => MASTERY_STATUS_LABELS[s || 'NOT_STARTED'] || '未学习'
const selectedNodeMastery = computed(() => entitySelectedNode.value ? masteryMap.value[entitySelectedNode.value.id] : undefined)
const openMasteryDetail = (id: number) => router.push(`/learning/knowledge-mastery/${id}`)

// ===== 初始化 =====
onMounted(() => {
  void loadGraph()
  // 预加载分类列表，供实体图谱按分类筛选
  categoriesApi.tree().then((list) => { graphCategories.value = list }).catch(() => {})
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
.kb-input:focus { border-color: var(--kb-primary); box-shadow: 0 0 0 3px rgba(59,111,224,0.1); }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--kb-muted-foreground); pointer-events: none; }
.detail-panel { position: sticky; top: 5rem; }
.tech-svg { display: block; }
@media (max-width: 1024px) { .detail-panel { position: static !important; } }
</style>
