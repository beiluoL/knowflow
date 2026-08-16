<template>
  <div class="space-y-6 animate-fade-in">
    <nav class="flex items-center gap-2 text-sm text-gray-500">
      <span class="text-primary-500 font-medium">知识库</span>
      <Icon name="chevron-right" :size="14" />
      <span class="text-gray-700 font-medium">管理</span>
    </nav>

    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">知识库管理</h1>
        <p class="text-gray-500 text-sm mt-1">创建、编辑和管理你的知识库集合</p>
      </div>
      <div class="flex items-center gap-3">
        <Button variant="secondary" icon-name="upload" @click="openImport()">批量导入</Button>
        <Button icon-name="plus" @click="openCreate">新建知识库</Button>
      </div>
    </div>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-primary-50">
            <Icon name="database" :size="20" class="text-primary-500" />
          </div>
          <span class="text-sm text-gray-600">知识库总数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ totalKbCount }} <span class="text-sm font-normal text-gray-500">个</span></p>
      </div>

      <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-blue-50">
            <Icon name="file-text" :size="20" class="text-blue-500" />
          </div>
          <span class="text-sm text-gray-600">文档总数</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ totalDocCount }} <span class="text-sm font-normal text-gray-500">篇</span></p>
      </div>

      <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-purple-50">
            <Icon name="hard-drive" :size="20" class="text-purple-500" />
          </div>
          <span class="text-sm text-gray-600">总存储量</span>
        </div>
        <p class="text-2xl font-bold text-gray-800 mb-2">{{ totalStorage }} <span class="text-sm font-normal text-gray-500">/ 10 GB</span></p>
        <div class="h-2 bg-gray-100 rounded-full overflow-hidden">
          <div class="h-full rounded-full bg-primary-500" :style="{ width: storagePercent + '%' }"></div>
        </div>
      </div>

      <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center bg-green-50">
            <Icon name="users" :size="20" class="text-green-500" />
          </div>
          <span class="text-sm text-gray-600">团队成员</span>
        </div>
        <p class="text-2xl font-bold text-gray-800">{{ realTotalUsers }} <span class="text-sm font-normal text-gray-500">人</span></p>
      </div>
    </div>

    <!-- 搜索 + 视图切换 -->
    <div class="bg-white border border-[var(--kb-border)] rounded-xl px-6 py-4 flex flex-wrap gap-4 items-center">
      <div class="flex-1 min-w-[220px] max-w-md">
        <Input v-model="searchKeyword" placeholder="搜索知识库名称 / 描述..." icon-name="search" />
      </div>
      <div class="flex items-center gap-2">
        <button
          @click="viewMode = 'list'"
          :class="[
            'px-3 h-9 rounded-md text-sm border transition-colors flex items-center gap-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
            viewMode === 'list'
              ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
              : 'border-[var(--kb-border)] text-gray-500 hover:border-gray-300 hover:text-gray-700',
          ]"
        >
          <Icon name="list" :size="16" /> 列表
        </button>
        <button
          @click="viewMode = 'card'"
          :class="[
            'px-3 h-9 rounded-md text-sm border transition-colors flex items-center gap-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
            viewMode === 'card'
              ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
              : 'border-[var(--kb-border)] text-gray-500 hover:border-gray-300 hover:text-gray-700',
          ]"
        >
          <Icon name="grid" :size="16" /> 卡片
        </button>
      </div>
    </div>

    <!-- ========= 列表视图 ========= -->
    <div v-if="viewMode === 'list'" class="bg-white border border-[var(--kb-border)] rounded-xl overflow-hidden">
      <div class="px-6 py-4 border-b border-[var(--kb-border)]">
        <div class="grid grid-cols-12 gap-4 text-xs text-gray-500 font-medium">
          <div class="col-span-4">知识库名称</div>
          <div class="col-span-2 text-center">文档数量</div>
          <div class="col-span-2 text-center">存储占用</div>
          <div class="col-span-2 text-center">创建时间</div>
          <div class="col-span-1 text-center">成员数</div>
          <div class="col-span-1 text-center">操作</div>
        </div>
      </div>

      <div class="divide-y divide-[#E2E6EC]/50">
        <div
          v-for="node in flatTreeNodes" :key="node.kb.id"
          class="px-6 py-4 hover:bg-gray-50 transition-colors group"
        >
          <div class="grid grid-cols-12 gap-4 items-center">
            <div class="col-span-4 flex items-center gap-3">
              <!-- 展开/折叠按钮 -->
              <button
                v-if="node.hasChildren"
                class="w-5 h-5 flex items-center justify-center text-gray-400 hover:text-gray-700 flex-shrink-0 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                :style="{ marginLeft: node.level * 24 + 'px' }"
                @click="toggleExpand(node.kb.id)"
                :title="expandedIds.has(node.kb.id) ? '折叠' : '展开'"
              >
                <Icon :name="expandedIds.has(node.kb.id) ? 'chevron-down' : 'chevron-right'" :size="14" aria-hidden="true" />
              </button>
              <div v-else :style="{ marginLeft: node.level * 24 + 20 + 'px' }"></div>
              <div
                class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 cursor-pointer hover:opacity-90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                :style="{ backgroundColor: getKbColor(node.kb.icon) + '15' }"
                role="button"
                tabindex="0"
                @click="goToKbDetail(node.kb)"
                @keydown.enter.prevent="($event.target as HTMLElement).click()"
              >
                <Icon
                  :name="getCategoryIconName(node.kb.icon)"
                  :size="20"
                  :color="getKbColor(node.kb.icon)"
                  aria-hidden="true"
                />
              </div>
              <div class="min-w-0 cursor-pointer flex-1 hover:bg-gray-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" role="button" tabindex="0" @click="goToKbDetail(node.kb)" @keydown.enter.prevent="($event.target as HTMLElement).click()">
                <p class="font-medium text-gray-800 truncate hover:text-primary-500 transition-colors">
                  {{ node.kb.name }}
                  <span v-if="node.level > 0" class="ml-1.5 text-[10px] text-gray-400 font-normal">L{{ node.level + 1 }}</span>
                </p>
                <p class="text-xs text-gray-400 truncate">{{ node.kb.description || '暂无描述' }}</p>
              </div>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-700 font-medium">{{ getDocCount(node.kb) }} 篇</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-700">{{ formatStorage(getDocCount(node.kb), node.kb.id) }}</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-sm text-gray-500">{{ formatDate(node.kb.createTime) }}</span>
            </div>
            <div class="col-span-1 text-center flex justify-center">
              <button
                class="px-2 py-1 text-xs rounded-md bg-green-50 text-green-600 hover:bg-green-100 flex items-center gap-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openMembers(node.kb)"
              >
                <Icon name="users" :size="12" aria-hidden="true" /> {{ getMemberCount(node.kb.id) }}
              </button>
            </div>
            <div class="col-span-1 flex items-center justify-center gap-1">
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openMembers(node.kb)"
                title="成员管理"
              >
                <Icon name="users" :size="16" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-purple-500 hover:bg-purple-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="openExport(node.kb)"
                title="导出"
              >
                <Icon name="download" :size="16" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-primary-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="editKb(node.kb)"
                title="编辑"
              >
                <Icon name="edit-2" :size="16" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="deleteKb(node.kb)"
                title="删除"
              >
                <Icon name="trash-2" :size="16" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="px-6 py-3 text-center border-t border-[var(--kb-border)]/50">
        <span class="text-sm text-gray-400">显示 {{ flatTreeNodes.length }} / {{ allFlatKbs.length }} 个分类</span>
      </div>
    </div>

    <!-- ========= 卡片视图 ========= -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
      <div
        v-for="kb in filteredKbs" :key="kb.id"
        class="bg-white border border-[var(--kb-border)] rounded-2xl overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-shadow transition-transform duration-200 group"
      >
        <!-- 卡片顶部渐变条 + 图标 -->
        <div
          class="h-24 relative flex items-end p-5"
          :style="{ background: `linear-gradient(135deg, ${getKbColor(kb.icon)}22 0%, ${getKbColor(kb.icon)}08 100%)` }"
        >
          <div
            class="w-14 h-14 rounded-2xl flex items-center justify-center bg-white shadow-sm border border-[var(--kb-border)] absolute bottom-0 translate-y-1/2 left-5"
          >
            <Icon
              :name="getCategoryIconName(kb.icon)"
              :size="28"
              :color="getKbColor(kb.icon)"
            />
          </div>
          <div class="ml-auto flex items-center gap-1 absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                class="p-1.5 bg-white/90 backdrop-blur rounded-md text-gray-500 hover:text-primary-500 shadow-sm border border-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click.stop="editKb(kb)"
                title="编辑"
              >
                <Icon name="edit-2" :size="14" aria-hidden="true" />
              </button>
              <button
                class="p-1.5 bg-white/90 backdrop-blur rounded-md text-gray-500 hover:text-purple-500 shadow-sm border border-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click.stop="openExport(kb)"
                title="导出"
              >
                <Icon name="download" :size="14" aria-hidden="true" />
              </button>
          </div>
        </div>

        <!-- 卡片主体 -->
        <div class="pt-10 px-5 pb-5 cursor-pointer hover:bg-gray-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" role="button" tabindex="0" @click="goToKbDetail(kb)" @keydown.enter.self.prevent="($event.target as HTMLElement).click()">
          <div class="flex items-start justify-between mb-1">
            <h3 class="font-semibold text-gray-800 text-[15px] leading-tight line-clamp-1 pr-2 group-hover:text-primary-600 transition-colors">
              {{ kb.name }}
            </h3>
          </div>
          <p class="text-xs text-gray-400 line-clamp-2 h-8 mb-4">{{ kb.description || '暂无描述' }}</p>

          <div class="flex items-center justify-between text-xs text-gray-500">
            <div class="flex items-center gap-3">
              <span class="inline-flex items-center gap-1">
                <Icon name="file-text" :size="12" class="text-blue-500" aria-hidden="true" />
                {{ getDocCount(kb) }} 篇
              </span>
              <span class="inline-flex items-center gap-1">
                <Icon name="hard-drive" :size="12" class="text-purple-500" aria-hidden="true" />
                {{ formatStorage(getDocCount(kb), kb.id) }}
              </span>
            </div>
          </div>

          <div class="mt-4 pt-4 border-t border-[var(--kb-border)]/70 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-400">{{ formatDate(kb.createTime) }}</span>
            </div>
            <div class="flex items-center gap-1">
              <button
                class="inline-flex items-center gap-1 px-2.5 h-7 rounded-md text-xs bg-green-50 text-green-600 hover:bg-green-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click.stop="openMembers(kb)"
              >
                <Icon name="users" :size="12" aria-hidden="true" />
                {{ getMemberCount(kb.id) }}
              </button>
              <button
                class="inline-flex items-center gap-1 px-2.5 h-7 rounded-md text-xs bg-primary-50 text-primary-600 hover:bg-primary-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click.stop="goToKbDetail(kb)"
              >
                查看
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div>
      <h3 class="text-base font-semibold text-gray-800 mb-4">快捷操作</h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-colors transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" role="button" tabindex="0" @click="openImport()" @keydown.enter.prevent="($event.target as HTMLElement).click()">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-primary-50 flex-shrink-0">
              <Icon name="upload" :size="22" class="text-primary-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">批量导入文档</h4>
              <p class="text-sm text-gray-500">支持 ZIP（内含 MD/TXT/JSON）、单文件上传</p>
            </div>
          </div>
        </div>

        <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-colors transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" role="button" tabindex="0" @click="firstKb && openMembers(firstKb)" @keydown.enter.prevent="($event.target as HTMLElement).click()">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-blue-50 flex-shrink-0">
              <Icon name="shield" :size="22" class="text-blue-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">设置权限管理</h4>
              <p class="text-sm text-gray-500">管理成员角色与访问权限（OWNER / EDITOR / READER）</p>
            </div>
          </div>
        </div>

        <div class="bg-white border border-[var(--kb-border)] rounded-xl p-5 cursor-pointer hover:border-primary-500/30 hover:shadow-md transition-colors transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2" role="button" tabindex="0" @click="firstKb && openExport(firstKb)" @keydown.enter.prevent="($event.target as HTMLElement).click()">
          <div class="flex items-start gap-4">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center bg-purple-50 flex-shrink-0">
              <Icon name="download" :size="22" class="text-purple-500" />
            </div>
            <div>
              <h4 class="font-medium text-gray-800 mb-1">导出知识库</h4>
              <p class="text-sm text-gray-500">将知识库导出为 ZIP（meta.json + docs/*.md）</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========= 新建 / 编辑弹窗 ========= -->
    <div
      v-if="showModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl w-full max-w-md p-6 animate-scale-in max-h-[90vh] overflow-y-auto">
        <h3 class="text-lg font-semibold text-gray-800 mb-4">
          {{ editingKb ? '编辑知识库' : '新建知识库' }}
        </h3>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">知识库名称</label>
            <Input v-model="kbForm.name" placeholder="请输入知识库名称" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">
              父分类
              <span class="text-xs text-gray-400 font-normal ml-1">（不选则为顶级分类，最多 3 级）</span>
            </label>
            <select
              v-model="kbForm.parentId"
              class="w-full h-10 px-3 rounded-lg border border-[var(--kb-border)] text-sm text-gray-700 focus:border-primary-500 focus:outline-none bg-white hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <option :value="undefined">作为顶级分类</option>
              <option
                v-for="cat in parentOptions"
                :key="cat.id"
                :value="cat.id"
                :disabled="editingKb?.id === cat.id"
              >
                {{ cat.name }}{{ editingKb?.id === cat.id ? '（当前分类，不可选）' : '' }}
              </option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">描述</label>
            <Input v-model="kbForm.description" placeholder="请输入知识库描述" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">图标</label>
            <div class="flex flex-wrap gap-1.5 mb-3">
              <button
                v-for="cat in presetIconCategories"
                :key="cat.key"
                @click="activeIconCategory = cat.key"
                :class="[
                  'px-2.5 py-1 text-xs rounded-md border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
                  activeIconCategory === cat.key
                    ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
                    : 'border-[var(--kb-border)] text-gray-500 hover:border-gray-300',
                ]"
              >
                {{ cat.label }}
              </button>
            </div>
            <div class="grid grid-cols-8 gap-2 max-h-44 overflow-y-auto p-1 border border-[var(--kb-border)] rounded-lg">
              <button
                v-for="icon in filteredPresetIcons"
                :key="icon.key"
                @click="selectPresetIcon(icon.key)"
                :title="`${icon.name}（${icon.key}）`"
                :class="[
                  'aspect-square rounded-md flex items-center justify-center border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
                  selectedIconKey === icon.key
                    ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-500'
                    : 'border-transparent hover:border-gray-300 hover:bg-gray-50',
                ]"
              >
                <Icon :name="icon.svg" :size="22" aria-hidden="true" />
              </button>
            </div>
            <div v-if="selectedIconKey" class="mt-3 flex items-center gap-2 flex-wrap">
              <span class="text-xs text-gray-500">颜色：</span>
              <button
                v-for="c in iconColorPresets"
                :key="c"
                type="button"
                @click="selectIconColor(c)"
                :class="[
                  'w-6 h-6 rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2',
                  selectedIconColor === c ? 'border-gray-700 scale-110' : 'border-white shadow-sm hover:scale-110',
                ]"
                :style="{ backgroundColor: c }"
                :title="c"
              />
              <label class="flex items-center gap-1 cursor-pointer text-xs text-gray-500" title="自定义颜色">
                <input
                  :value="selectedIconColor"
                  type="color"
                  class="w-6 h-6 rounded cursor-pointer border border-gray-200 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @input="selectIconColor(($event.target as HTMLInputElement).value)"
                />
                <span>自定义</span>
              </label>
              <button
                v-if="selectedIconColor"
                type="button"
                class="text-xs text-gray-400 hover:text-red-500 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="selectIconColor('')"
              >清除</button>
              <div class="ml-auto flex items-center gap-2">
                <span class="text-xs text-gray-400">预览：</span>
                <div
                  class="w-9 h-9 rounded-lg flex items-center justify-center"
                  :style="{ backgroundColor: (selectedIconColor || currentPresetDefaultColor) + '15' }"
                >
                  <Icon :name="currentRenderIconName" :size="20" :color="selectedIconColor || currentPresetDefaultColor" />
                </div>
              </div>
            </div>
            <p class="text-xs text-gray-400 mt-1.5">
              已选择：{{ selectedIconKey || '未选择' }}{{ selectedIconColor ? `（${selectedIconColor}）` : '' }}
            </p>
          </div>
        </div>
        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="saveKb">{{ saving ? '保存中...' : '确定' }}</Button>
        </div>
      </div>
    </div>

    <!-- ========= 成员管理弹窗 ========= -->
    <div
      v-if="showMemberModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      @click.self="closeMemberModal"
    >
      <div class="bg-white rounded-xl w-full max-w-3xl p-6 animate-scale-in max-h-[90vh] overflow-hidden flex flex-col">
        <div class="flex items-start justify-between mb-4">
          <div>
            <h3 class="text-lg font-semibold text-gray-800">成员管理</h3>
            <p class="text-sm text-gray-500 mt-0.5">
              知识库：<span class="text-gray-700 font-medium">{{ currentKb?.name }}</span>
              <span class="ml-3">共 {{ members.length }} 位成员</span>
            </p>
          </div>
          <button @click="closeMemberModal" class="text-gray-400 hover:text-gray-600 p-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2">
            <Icon name="x" :size="20" aria-hidden="true" />
          </button>
        </div>

        <!-- 搜索 + 新增行 -->
        <div class="flex flex-wrap gap-3 items-center mb-4 pb-4 border-b border-[var(--kb-border)]">
          <div class="flex-1 min-w-[200px]">
            <Input
              v-model="memberSearch"
              placeholder="搜索用户名 / 昵称 / 邮箱（用于添加新成员）..."
              icon-name="search"
              @enter="doSearchAddUser"
            />
          </div>
          <div class="w-[130px]">
            <select
              v-model="newMemberRole"
              class="w-full h-10 px-3 rounded-lg border border-[var(--kb-border)] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <option value="READER">阅读者</option>
              <option value="EDITOR">编辑者</option>
              <option value="OWNER">所有者</option>
            </select>
          </div>
          <Button @click="doSearchAddUser" :disabled="!memberSearch.trim() || addingMember">
            {{ addingMember ? '邀请中...' : '搜索并邀请' }}
          </Button>
          <Button variant="secondary" @click="showInviteByEmail = !showInviteByEmail">
            {{ showInviteByEmail ? '取消邮箱邀请' : '邮箱邀请' }}
          </Button>
        </div>

        <!-- 邮箱邀请行 -->
        <div v-if="showInviteByEmail" class="flex flex-wrap gap-3 items-center mb-4 pb-4 border-b border-[var(--kb-border)] bg-gray-50/60 -mx-6 px-6 py-3">
          <div class="flex-1 min-w-[220px]">
            <Input v-model="inviteEmail" placeholder="输入邀请邮箱（例如 user@example.com）" />
          </div>
          <div class="w-[130px]">
            <select
              v-model="inviteRole"
              class="w-full h-10 px-3 rounded-lg border border-[var(--kb-border)] text-sm text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            >
              <option value="READER">阅读者</option>
              <option value="EDITOR">编辑者</option>
              <option value="OWNER">所有者</option>
            </select>
          </div>
          <Button variant="primary" @click="doInviteByEmail" :disabled="!inviteEmail.trim() || addingMember">
            发送邀请
          </Button>
        </div>

        <!-- 候选用户列表（搜索后显示） -->
        <div v-if="candidateUsers.length" class="mb-4 pb-4 border-b border-[var(--kb-border)]">
          <p class="text-xs text-gray-500 mb-2">搜索结果：</p>
          <div class="space-y-2 max-h-40 overflow-y-auto">
            <div
              v-for="u in candidateUsers"
              :key="u.id"
              class="flex items-center justify-between px-3 py-2 rounded-lg bg-gray-50 hover:bg-gray-100 transition-colors"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-full bg-primary-50 text-primary-600 flex items-center justify-center text-xs font-medium flex-shrink-0">
                  {{ (u.nickname || u.username || '?').slice(0, 1) }}
                </div>
                <div class="min-w-0">
                  <p class="text-sm text-gray-800 font-medium truncate">
                    {{ u.nickname || u.username }}
                    <span v-if="u.username" class="text-xs text-gray-400 font-normal ml-1">@{{ u.username }}</span>
                  </p>
                  <p v-if="u.email" class="text-xs text-gray-400 truncate">{{ u.email }}</p>
                </div>
              </div>
              <Button size="sm" variant="secondary" @click="doAddUser(u)" :disabled="addingMember">
                加入
              </Button>
            </div>
          </div>
        </div>

        <!-- 成员列表 -->
        <div class="flex-1 overflow-y-auto -mx-6 px-6">
          <div v-if="!members.length" class="py-12 text-center text-gray-400 text-sm">
            <Icon name="users" :size="32" class="mx-auto mb-2 opacity-50" />
            暂无成员
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="m in members"
              :key="m.id"
              class="flex items-center justify-between px-4 py-3 rounded-lg border border-[var(--kb-border)] hover:border-primary-500/30 transition-colors"
            >
              <div class="flex items-center gap-3 min-w-0 flex-1">
                <div class="w-10 h-10 rounded-full bg-primary-50 text-primary-600 flex items-center justify-center font-medium flex-shrink-0">
                  {{ displayName(m).slice(0, 1) }}
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-gray-800 truncate">{{ displayName(m) }}</span>
                    <span :class="[
                      'text-[10px] px-2 py-0.5 rounded-full font-medium',
                      m.role === 'OWNER' ? 'bg-orange-50 text-orange-600' :
                      m.role === 'EDITOR' ? 'bg-primary-50 text-primary-600' :
                      'bg-gray-100 text-gray-500',
                    ]">
                      {{ roleLabel(m.role) }}
                    </span>
                  </div>
                  <div class="text-xs text-gray-400 truncate">
                    <span v-if="m.email">{{ m.email }}</span>
                    <span v-else-if="m.inviteEmail" class="text-amber-500">邀请中：{{ m.inviteEmail }}</span>
                    <span v-if="m.joinTime"> · 加入于 {{ formatDate(m.joinTime) }}</span>
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2 flex-shrink-0">
                <select
                  v-if="m.role !== 'OWNER' || canTransferOwner(m)"
                  :value="m.role"
                  @change="onChangeRole(m, ($event.target as HTMLSelectElement).value)"
                  class="h-8 px-2 rounded-md border border-[var(--kb-border)] text-xs text-gray-700 focus:border-primary-500 focus:outline-none hover:border-gray-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                >
                  <option value="OWNER">所有者</option>
                  <option value="EDITOR">编辑者</option>
                  <option value="READER">阅读者</option>
                </select>
                <span v-else class="text-xs text-gray-400 px-2">唯一所有者</span>
                <button
                  v-if="m.role !== 'OWNER'"
                  class="p-1.5 text-gray-400 hover:text-danger-500 hover:bg-danger-50 rounded transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                  @click="doRemoveMember(m)"
                  title="移除成员"
                >
                  <Icon name="trash-2" :size="14" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-4 mt-4 border-t border-[var(--kb-border)]">
          <Button variant="secondary" @click="closeMemberModal">完成</Button>
        </div>
      </div>
    </div>

    <!-- ========= 批量导入弹窗 ========= -->
    <div
      v-if="showImportModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      @click.self="closeImport"
    >
      <div class="bg-white rounded-xl w-full max-w-lg p-6 animate-scale-in">
        <div class="flex items-start justify-between mb-4">
          <div>
            <h3 class="text-lg font-semibold text-gray-800">批量导入文档</h3>
            <p class="text-sm text-gray-500 mt-0.5">将文档导入到指定知识库</p>
          </div>
          <button @click="closeImport" class="text-gray-400 hover:text-gray-600 p-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2">
            <Icon name="x" :size="20" aria-hidden="true" />
          </button>
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">目标知识库</label>
            <CategoryTreeSelect
              v-model="importForm.categoryId"
              :categories="knowledgeBases"
              placeholder="请选择知识库"
              empty-label="请选择知识库"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1.5">选择文件</label>
            <div
              class="border-2 border-dashed border-[var(--kb-border)] rounded-xl p-8 text-center hover:border-primary-400 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
              role="button"
              tabindex="0"
              @click="triggerImportFile"
              @keydown.enter.self.prevent="($event.target as HTMLElement).click()"
              @dragover.prevent
              @drop.prevent="onImportDrop"
            >
              <input
                ref="importFileRef"
                type="file"
                class="hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                :accept="'.zip,.md,.markdown,.txt,.json'"
                @change="onImportFileChange"
              />
              <Icon name="upload" :size="36" class="text-gray-300 mx-auto mb-2" aria-hidden="true" />
              <p class="text-sm text-gray-600 font-medium">{{ importForm.file ? importForm.file.name : '点击上传或拖拽文件到此处' }}</p>
              <p class="text-xs text-gray-400 mt-1">支持 ZIP / MD / TXT / JSON，单个文件最大 50MB</p>
            </div>
          </div>

          <div v-if="importResult" class="bg-gray-50 rounded-lg p-4 text-sm space-y-2">
            <p class="flex items-center gap-2 font-medium text-gray-800">
              <Icon name="check-circle" :size="16" class="text-green-500" /> 导入完成
            </p>
            <div class="grid grid-cols-3 gap-2 text-center">
              <div><span class="text-xs text-gray-500">总数</span><p class="font-bold text-gray-800">{{ importResult.total }}</p></div>
              <div><span class="text-xs text-green-600">成功</span><p class="font-bold text-green-600">{{ importResult.success }}</p></div>
              <div><span class="text-xs text-red-500">失败</span><p class="font-bold text-red-500">{{ importResult.failed }}</p></div>
            </div>
            <div v-if="importResult.failedNames?.length" class="text-xs text-red-500">
              失败：{{ importResult.failedNames.join('、') }}
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6">
          <Button variant="secondary" @click="closeImport">关闭</Button>
          <Button
            :disabled="!importForm.categoryId || !importForm.file || importing"
            @click="doImport"
          >
            {{ importing ? '导入中...' : '开始导入' }}
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-知识库管理：维护知识库（分类）的增删改查、成员权限、批量导入 / 导出。
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue'
import { categoriesApi, adminApi } from '@/api'
import type { CategoryVO } from '@/api/types'
import type { KbMemberVO, ImportResultVO } from '@/api/admin'
import {
  presetIcons, presetIconCategories, getIconByKey, iconColorPresets,
  parseIconValue, buildIconValue, resolveIconForRender,
  type PresetIcon,
} from '@/utils/presetIcons'

const router = useRouter()

// ========== 基本 CRUD & 状态 ==========
const goToKbDetail = (kb: CategoryVO) => router.push(`/admin/docs?categoryId=${kb.id}`)

const knowledgeBases = ref<CategoryVO[]>([])
const showModal = ref(false)
const editingKb = ref<CategoryVO | null>(null)
const saving = ref(false)

const realTotalDocs = ref(0)
const realTotalUsers = ref(0)
const totalWordCount = ref(0)
const categoryWordCounts = ref<Map<number, number>>(new Map())
const categoryDocCounts = ref<Map<number, number>>(new Map())

// 视图模式：列表 / 卡片
const viewMode = ref<'list' | 'card'>('list')
const searchKeyword = ref('')

// ========== 图标选择 ==========
const activeIconCategory = ref<PresetIcon['category']>('language')
const filteredPresetIcons = computed(() =>
  presetIcons.filter(icon => icon.category === activeIconCategory.value)
)
const legacyIconColors: Record<string, string> = {
  code: '#3B6FE0', server: '#10B981', database: '#F59E0B', brain: '#8B5CF6',
  layout: '#EC4899', settings: '#06B6D4', 'book-open': '#3B6FE0', folder: '#6B7280',
  layers: '#F97316', 'message-circle': '#84CC16',
}
const kbForm = reactive({ name: '', description: '', icon: 'lang-java', parentId: undefined as number | undefined })
const selectedIconKey = computed(() => parseIconValue(kbForm.icon).key)
const selectedIconColor = computed(() => parseIconValue(kbForm.icon).color)
const currentPresetDefaultColor = computed(() => getIconByKey(selectedIconKey.value)?.color || '#6B7280')
const currentRenderIconName = computed(() => {
  const preset = getIconByKey(selectedIconKey.value)
  return preset?.svg || selectedIconKey.value || 'folder'
})
const selectPresetIcon = (key: string) => {
  const keepColor = selectedIconColor.value
  kbForm.icon = buildIconValue(key, keepColor)
}
const selectIconColor = (color: string) => {
  kbForm.icon = buildIconValue(selectedIconKey.value, color)
}
const getCategoryIconName = (raw?: string): string => {
  if (!raw) return 'folder'
  const { name } = resolveIconForRender(raw)
  return name || 'folder'
}
const getKbColor = (raw?: string): string => {
  if (!raw) return '#6B7280'
  const { color } = resolveIconForRender(raw)
  return color || legacyIconColors[raw] || '#6B7280'
}

// ========== 统计派生 ==========
const totalKbCount = computed(() => knowledgeBases.value.length)
const totalDocCount = computed(() => realTotalDocs.value)
const totalStorage = computed(() => {
  const bytes = totalWordCount.value * 2
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  const mb = kb / 1024
  if (mb < 1024) return mb.toFixed(1) + ' MB'
  return (mb / 1024).toFixed(1) + ' GB'
})
const storagePercent = computed(() => {
  const bytes = totalWordCount.value * 2
  const mb = bytes / (1024 * 1024)
  return Math.min(100, Math.round((mb / (10 * 1024)) * 100))
})

// ========== 过滤 + 第一个 KB 快捷入口 ==========
const filteredKbs = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return knowledgeBases.value
  return knowledgeBases.value.filter(kb =>
    kb.name.toLowerCase().includes(kw) || (kb.description || '').toLowerCase().includes(kw)
  )
})
const firstKb = computed(() => knowledgeBases.value[0] || null)

// ========== 树型展开 ==========
const expandedIds = ref<Set<number>>(new Set())
interface FlatNode {
  kb: CategoryVO
  level: number
  hasChildren: boolean
}
/** 搜索时用的全量扁平列表（含子分类） */
const allFlatKbs = computed<CategoryVO[]>(() => {
  const result: CategoryVO[] = []
  const walk = (nodes: CategoryVO[]) => {
    for (const node of nodes) {
      result.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(knowledgeBases.value)
  return result
})
/** 列表视图用的扁平树（带层级 + 展开状态） */
const flatTreeNodes = computed<FlatNode[]>(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (kw) {
    // 搜索模式：扁平过滤，不显示层级
    return allFlatKbs.value
      .filter(kb => kb.name.toLowerCase().includes(kw) || (kb.description || '').toLowerCase().includes(kw))
      .map(kb => ({ kb, level: 0, hasChildren: false }))
  }
  // 树模式：按展开状态递归扁平化
  const result: FlatNode[] = []
  const walk = (nodes: CategoryVO[], level: number) => {
    for (const node of nodes) {
      const hasChildren = (node.children?.length ?? 0) > 0
      result.push({ kb: node, level, hasChildren })
      if (hasChildren && expandedIds.value.has(node.id)) {
        walk(node.children!, level + 1)
      }
    }
  }
  walk(knowledgeBases.value, 0)
  return result
})
const toggleExpand = (id: number) => {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}
/** 父分类候选（扁平，用于表单下拉） */
const parentOptions = computed<CategoryVO[]>(() => allFlatKbs.value)

// ========== 成员数（按分类缓存） ==========
const categoryMemberCounts = ref<Map<number, number>>(new Map())
const getMemberCount = (id: number): number => categoryMemberCounts.value.get(id) ?? 0

const getDocCount = (kb: CategoryVO): number => {
  const realCount = categoryDocCounts.value.get(kb.id) ?? 0
  return realCount > 0 ? realCount : (kb.docCount ?? 0)
}
const formatStorage = (docCount: number, categoryId?: number): string => {
  const wordCount = categoryId ? (categoryWordCounts.value.get(categoryId) ?? 0) : 0
  const bytes = wordCount > 0 ? wordCount * 2 : docCount * 2000
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  const mb = kb / 1024
  if (mb < 1024) return mb.toFixed(1) + ' MB'
  return (mb / 1024).toFixed(1) + ' GB'
}
const formatDate = (dateStr?: string): string => {
  if (!dateStr) return '—'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
}

// ========== 新建 / 编辑 ==========
const openCreate = () => {
  editingKb.value = null
  kbForm.name = ''
  kbForm.description = ''
  kbForm.icon = 'lang-java'
  kbForm.parentId = undefined
  activeIconCategory.value = 'language'
  showModal.value = true
}
const editKb = (kb: CategoryVO) => {
  editingKb.value = kb
  kbForm.name = kb.name
  kbForm.description = kb.description || ''
  kbForm.icon = kb.icon || 'lang-java'
  kbForm.parentId = kb.parentId && kb.parentId !== 0 ? kb.parentId : undefined
  const key = parseIconValue(kb.icon).key
  const preset = getIconByKey(key)
  if (preset) activeIconCategory.value = preset.category
  showModal.value = true
}
const closeModal = () => {
  showModal.value = false
  editingKb.value = null
}
const saveKb = async () => {
  if (!kbForm.name.trim()) {
    notify('请填写知识库名称', 'warning'); return
  }
  // 编辑时不能选自己作为父分类
  if (editingKb.value?.id && editingKb.value.id === kbForm.parentId) {
    notify('父分类不能是自身', 'warning'); return
  }
  saving.value = true
  try {
    const payload = {
      name: kbForm.name,
      description: kbForm.description,
      icon: kbForm.icon,
      sortOrder: 0,
      parentId: kbForm.parentId ?? 0,
    }
    if (editingKb.value?.id) {
      await adminApi.updateCategory(editingKb.value.id, payload)
      notify('知识库已更新', 'success')
    } else {
      const created = await adminApi.createCategory(payload)
      // 创建后刷新成员数（owner 会自动加入）
      if (created?.id) {
        try {
          const list = await adminApi.kbMembers(created.id)
          categoryMemberCounts.value.set(created.id, list.length)
        } catch {}
      }
      notify('知识库已创建', 'success')
    }
    await loadKbs()
    closeModal()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}
const deleteKb = async (kb: CategoryVO) => {
  if (!(await confirmDialog(`确定删除知识库「${kb.name}」吗？其成员关系也会一并失效。`))) return
  try {
    await adminApi.removeCategory(kb.id)
    notify('删除成功', 'success')
    categoryMemberCounts.value.delete(kb.id)
    await loadKbs()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

// ========== 主数据加载 ==========
const loadKbs = async () => {
  try {
    knowledgeBases.value = await categoriesApi.adminTree()
    // 默认展开所有有子分类的节点，便于直观看到树结构
    const initExpanded = new Set<number>()
    const walk = (nodes: CategoryVO[]) => {
      for (const node of nodes) {
        if (node.children?.length) {
          initExpanded.add(node.id)
          walk(node.children)
        }
      }
    }
    walk(knowledgeBases.value)
    expandedIds.value = initExpanded
  } catch (e: unknown) {
    notify('加载失败：' + getApiError(e), 'error')
  }
}
const loadStats = async () => {
  try {
    const [overview, users, docPage] = await Promise.all([
      adminApi.overview(),
      adminApi.users({ pageSize: 1 }),
      adminApi.docs({ pageSize: 1000 }),
    ])
    realTotalDocs.value = overview.totalDocs ?? 0
    realTotalUsers.value = users.total ?? 0
    const records = (docPage.records ?? []) as Array<{ wordCount?: number; categoryId?: number }>
    let totalWc = 0
    const catWcMap = new Map<number, number>()
    const catDocMap = new Map<number, number>()
    records.forEach((d) => {
      const wc = d.wordCount ?? 0
      totalWc += wc
      if (d.categoryId) {
        catWcMap.set(d.categoryId, (catWcMap.get(d.categoryId) ?? 0) + wc)
        catDocMap.set(d.categoryId, (catDocMap.get(d.categoryId) ?? 0) + 1)
      }
    })
    totalWordCount.value = totalWc
    categoryWordCounts.value = catWcMap
    categoryDocCounts.value = catDocMap
  } catch (e: unknown) {
    console.error('加载统计数据失败:', e)
  }
}

// ========== 成员管理弹窗 ==========
const showMemberModal = ref(false)
const currentKb = ref<CategoryVO | null>(null)
const members = ref<KbMemberVO[]>([])
const memberSearch = ref('')
const newMemberRole = ref<'OWNER' | 'EDITOR' | 'READER'>('READER')
const showInviteByEmail = ref(false)
const inviteEmail = ref('')
const inviteRole = ref<'OWNER' | 'EDITOR' | 'READER'>('READER')
const candidateUsers = ref<Array<{ id: number; username?: string; nickname?: string; email?: string }>>([])
const addingMember = ref(false)

const displayName = (m: KbMemberVO): string => m.nickname || m.username || m.inviteEmail || '未知用户'
const roleLabel = (role: string): string => role === 'OWNER' ? '所有者' : role === 'EDITOR' ? '编辑者' : '阅读者'

// 唯一 OWNER 不能被删 / 改角色为非 OWNER（需先转让）
const ownerCount = computed(() => members.value.filter(m => m.role === 'OWNER').length)
const canTransferOwner = (m: KbMemberVO): boolean => m.role === 'OWNER' && ownerCount.value > 1

const openMembers = async (kb: CategoryVO) => {
  currentKb.value = kb
  memberSearch.value = ''
  newMemberRole.value = 'READER'
  candidateUsers.value = []
  showInviteByEmail.value = false
  inviteEmail.value = ''
  showMemberModal.value = true
  await refreshMembers()
}
const refreshMembers = async () => {
  if (!currentKb.value) return
  try {
    members.value = await adminApi.kbMembers(currentKb.value.id)
    categoryMemberCounts.value.set(currentKb.value.id, members.value.length)
  } catch (e: unknown) {
    notify('加载成员失败：' + getApiError(e), 'error')
  }
}
const closeMemberModal = () => {
  showMemberModal.value = false
  currentKb.value = null
  members.value = []
  candidateUsers.value = []
}

const doSearchAddUser = async () => {
  if (!currentKb.value || !memberSearch.value.trim()) return
  try {
    const list = await adminApi.searchKbMembers(currentKb.value.id, memberSearch.value.trim())
    // 搜索结果可能是 MemberVO 或简版用户对象：统一取 userId/email 判定是否已在库
    const existingUserIds = new Set(members.value.map(m => m.userId).filter(Boolean) as number[])
    const existingEmails = new Set(members.value.map(m => m.email || m.inviteEmail).filter(Boolean) as string[])
    candidateUsers.value = (list as unknown as Array<{ id?: number; userId?: number; username?: string; nickname?: string; email?: string; inviteEmail?: string }>).map(x => ({
      id: (x.userId || x.id) as number,
      username: x.username,
      nickname: x.nickname,
      email: x.email || x.inviteEmail,
    })).filter(u => {
      if (u.id && existingUserIds.has(u.id)) return false
      if (u.email && existingEmails.has(u.email)) return false
      return true
    }).slice(0, 10)
    if (!candidateUsers.value.length) {
      notify('未搜索到可添加的用户', 'warning')
    }
  } catch (e: unknown) {
    notify('搜索失败：' + getApiError(e), 'error')
  }
}

const doAddUser = async (u: { id: number; username?: string; nickname?: string; email?: string }) => {
  if (!currentKb.value) return
  addingMember.value = true
  try {
    await adminApi.addKbMember({
      categoryId: currentKb.value.id,
      userId: u.id,
      email: u.email,
      role: newMemberRole.value,
    })
    notify('已加入知识库', 'success')
    candidateUsers.value = []
    memberSearch.value = ''
    await refreshMembers()
  } catch (e: unknown) {
    notify('添加失败：' + getApiError(e), 'error')
  } finally {
    addingMember.value = false
  }
}

const doInviteByEmail = async () => {
  if (!currentKb.value || !inviteEmail.value.trim()) return
  addingMember.value = true
  try {
    await adminApi.addKbMember({
      categoryId: currentKb.value.id,
      email: inviteEmail.value.trim(),
      role: inviteRole.value,
    })
    notify('邀请已提交，用户注册后将自动加入', 'success')
    inviteEmail.value = ''
    showInviteByEmail.value = false
    await refreshMembers()
  } catch (e: unknown) {
    notify('邀请失败：' + getApiError(e), 'error')
  } finally {
    addingMember.value = false
  }
}

const onChangeRole = async (m: KbMemberVO, newRole: string) => {
  if (!['OWNER', 'EDITOR', 'READER'].includes(newRole)) return
  if (m.role === newRole) return
  if (m.role === 'OWNER' && ownerCount.value <= 1 && newRole !== 'OWNER') {
    notify('至少保留一位所有者，请先转让角色后再修改', 'warning')
    await refreshMembers(); return
  }
  const label = `${displayName(m)} 变更为「${roleLabel(newRole)}」`
  if (!(await confirmDialog(`确定将${label}吗？`))) {
    await refreshMembers(); return
  }
  try {
    await adminApi.changeKbMemberRole(m.id, newRole)
    notify(label + ' 成功', 'success')
    await refreshMembers()
  } catch (e: unknown) {
    notify('角色变更失败：' + getApiError(e), 'error')
    await refreshMembers()
  }
}

const doRemoveMember = async (m: KbMemberVO) => {
  if (!(await confirmDialog(`确定移除成员「${displayName(m)}」吗？`))) return
  try {
    await adminApi.removeKbMember(m.id)
    notify('已移除', 'success')
    await refreshMembers()
  } catch (e: unknown) {
    notify('移除失败：' + getApiError(e), 'error')
  }
}

// ========== 批量导入 ==========
const showImportModal = ref(false)
const importFileRef = ref<HTMLInputElement | null>(null)
const importForm = reactive<{ categoryId: number | null; file: File | null }>({ categoryId: null, file: null })
const importing = ref(false)
const importResult = ref<ImportResultVO | null>(null)

const openImport = (kb?: CategoryVO) => {
  importForm.categoryId = kb?.id ?? (knowledgeBases.value[0]?.id ?? null)
  importForm.file = null
  importResult.value = null
  showImportModal.value = true
}
const closeImport = () => {
  showImportModal.value = false
  importForm.file = null
  importResult.value = null
  if (importFileRef.value) importFileRef.value.value = ''
}
const triggerImportFile = () => importFileRef.value?.click()
const onImportFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files?.[0]) importForm.file = input.files[0]
}
const onImportDrop = (e: DragEvent) => {
  const f = e.dataTransfer?.files?.[0]
  if (f) importForm.file = f
}
const doImport = async () => {
  if (!importForm.categoryId || !importForm.file) return
  importing.value = true
  importResult.value = null
  try {
    const res = await adminApi.importKbDocs(importForm.categoryId, importForm.file)
    importResult.value = res
    notify(`导入完成：成功 ${res.success}，失败 ${res.failed}`, res.failed > 0 ? 'warning' : 'success')
    await loadStats()
  } catch (e: unknown) {
    notify('导入失败：' + getApiError(e), 'error')
  } finally {
    importing.value = false
  }
}

// ========== 导出 ==========
const openExport = async (kb: CategoryVO) => {
  if (!(await confirmDialog(`确定导出知识库「${kb.name}」为 ZIP 格式吗？`))) return
  try {
    const resp = await adminApi.exportKb(kb.id)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const blob = await resp.blob()
    const cd = resp.headers.get('Content-Disposition') || ''
    const match = cd.match(/filename\*?=([^;]+)/i)
    let filename = match ? match[1].replace(/^UTF-8''/, '').replace(/"/g, '').trim() : `${kb.name}.zip`
    try { filename = decodeURIComponent(filename) } catch {}
    const a = document.createElement('a')
    const url = URL.createObjectURL(blob)
    a.href = url
    a.download = filename
    document.body.appendChild(a); a.click()
    setTimeout(() => { URL.revokeObjectURL(url); a.remove() }, 1000)
    notify('导出成功', 'success')
  } catch (e: unknown) {
    notify('导出失败：' + getApiError(e), 'error')
  }
}

onMounted(async () => {
  await loadKbs()
  await loadStats()
  // 并行加载每个知识库的成员数（并发限制 5，避免冲击后端）
  const kbs = knowledgeBases.value.slice()
  const counts = new Map<number, number>()
  const CONCURRENCY = 5
  for (let i = 0; i < kbs.length; i += CONCURRENCY) {
    const batch = kbs.slice(i, i + CONCURRENCY)
    const results = await Promise.allSettled(
      batch.map(kb => adminApi.kbMembers(kb.id))
    )
    batch.forEach((kb, idx) => {
      const r = results[idx]
      if (r.status === 'fulfilled') {
        counts.set(kb.id, r.value.length)
      }
    })
  }
  categoryMemberCounts.value = counts
})
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.5s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.animate-scale-in { animation: scaleIn 0.2s ease-out; }
@keyframes scaleIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
.line-clamp-1 { display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }
.line-clamp-2 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
</style>
