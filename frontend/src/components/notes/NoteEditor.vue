<template>
  <div class="kb-input-surface note-editor flex flex-col min-w-0">
    <div class="flex flex-wrap items-center gap-2 p-2 border-b">
      <button
        type="button"
        class="kb-btn"
        :aria-pressed="mode === 'source'"
        @click="showSource"
      >
        Markdown 源码
      </button>
      <button
        type="button"
        class="kb-btn"
        :aria-pressed="mode === 'visual'"
        @click="showVisual"
      >
        可视化编辑
      </button>
      <button type="button" class="kb-btn" :aria-pressed="mode==='read'" @click="showRead">阅读模式</button>
      <span class="note-mode-spacer"/>
      <button type="button" class="kb-btn" title="撤销 (⌘/Ctrl+Z)" aria-label="撤销" @mousedown.prevent="undo">↶</button>
      <button type="button" class="kb-btn" title="重做 (⌘/Ctrl+Shift+Z)" aria-label="重做" @mousedown.prevent="redo">↷</button>
      <span v-if="loading" role="status">加载编辑器…</span>
    </div>
    <p v-if="failure" role="alert" class="p-3">
      {{ failure }}；可继续使用源码编辑。
    </p>
    <section
      v-if="conversion !== null"
      class="p-3 border-b"
      aria-label="格式转换确认"
    >
      <p>
        此内容包含需要转换的格式。原始正文尚未修改，请比较后选择；自定义学习块建议继续使用源码。
      </p>
      <details>
        <summary>比较原始源码与编辑器输出</summary>
        <div class="grid md:grid-cols-2 gap-3">
          <pre>{{ modelValue }}</pre>
          <pre>{{ conversion }}</pre>
        </div>
      </details>
      <button type="button" class="kb-btn" @click="acceptConversion">
        我已核对，接受格式转换</button
      ><button type="button" class="kb-btn" @click="showSource">
        保留原文，使用源码
      </button>
    </section>
    <textarea
      v-show="mode === 'source'"
      ref="textarea"
      :value="modelValue"
      name="content"
      aria-label="笔记内容"
      placeholder="开始编写笔记内容…"
      class="content-textarea"
      @select="sourceSelection"
      @keyup="sourceSelection"
      @keydown="sourceKeydown"
      @input="onInput"
      @compositionstart="composing = true"
      @compositionend="finishComposition"
    />
    <NotesReadingTutor v-if="mode==='read'&&coaching&&noteId&&revision" :note-id="noteId" :revision="revision" :content="modelValue" :allow-save="allowSave" @saved="$emit('readingSaved',$event)" @open-change="$emit('readingOpen',$event)"><div class="note-read-view" @click="handleCodeCopyClick" v-html="renderMarkdown(modelValue)"/></NotesReadingTutor>
    <div v-show="mode==='read'&&!(coaching&&noteId&&revision)" ref="readRoot" class="note-read-view" @click="handleCodeCopyClick" v-html="renderMarkdown(modelValue)"/>
    <!-- 阅读模式下的划词学习：只在只读正文启用，源码/可视化编辑区不弹术语 -->
    <SelectionTermLookup
      :target="readRoot"
      :enabled="mode==='read'"
      :context-domain="readDomain"
    />
    <div
      v-show="mode === 'visual'"
      ref="host"
      class="note-crepe"
      @click.capture="safeLinkClick"
      @beforeinput="userEditing = true"
      @paste="userEditing = true"
      @keydown="userEditing = true"
      @pointerdown="userEditing = true"
    />
  </div>
</template>
<script setup lang="ts">
import NotesReadingTutor from './NotesReadingTutor.vue'
import type {ServerNote} from '@/api/notes'
const coaching=import.meta.env.VITE_PRODUCT_TUTOR_COACHING==='true'
import { ref, computed, nextTick, watch, onBeforeUnmount, onMounted } from "vue";
import {commandsCtx,editorViewCtx} from '@milkdown/kit/core';
import {undoCommand,redoCommand} from '@milkdown/kit/plugin/history';
import {TextSelection} from '@milkdown/kit/prose/state';
import {insert} from '@milkdown/kit/utils';
import {renderMarkdown} from '@/utils/markdown';
import SelectionTermLookup from '@/components/tech-english/SelectionTermLookup.vue';
import {inferTermDomain} from '@/components/tech-english/term-labels';
import {handleCodeCopyClick} from '@/utils/codeCopy';
import {notesApi} from '@/api/notes';
import type {NoteSelection} from '@/notes/editor-types';
import type { Crepe } from "@milkdown/crepe";
import "@milkdown/crepe/theme/common/style.css";
import "@milkdown/crepe/theme/frame.css";
const props = defineProps<{modelValue:string;initialMode?:'source'|'visual';noteId?:number|null;revision?:number;allowSave?:boolean}>(),
 emit=defineEmits<{'update:modelValue':[string];selection:[NoteSelection|null];readingSaved:[ServerNote];readingOpen:[boolean]}>();
const textarea = ref<HTMLTextAreaElement | null>(null),
  host = ref<HTMLDivElement | null>(null),
  mode = ref<"source" | "visual" | "read">("source"),
  loading = ref(false),
  failure = ref(""),
  conversion = ref<string | null>(null);
let editor: Crepe | null = null,
  dead = false,
  generation = 0,
  lastOutput = "";
const readRoot=ref<HTMLElement|null>(null);
/** 笔记正文的技术领域提示（仅用于术语多义项排序，不改变笔记归属或权限） */
const readDomain = computed(() => inferTermDomain(props.modelValue.slice(0, 2000)));
const sourcePast:string[]=[],sourceFuture:string[]=[];
const userEditing = ref(false),
  composing = ref(false);
function onInput(event: Event) {
  if (!composing.value){sourcePast.push(props.modelValue);sourceFuture.length=0;emit("update:modelValue", (event.target as HTMLTextAreaElement).value);}
}
function finishComposition(event: CompositionEvent) {
  composing.value = false;sourcePast.push(props.modelValue);sourceFuture.length=0;
  emit("update:modelValue", (event.target as HTMLTextAreaElement).value);
}
async function destroy() {
  const old = editor;
  editor = null;
  if (old) await old.destroy();
}
// 生命周期串行化：加载尚未完成时的模式切换，也必须等待创建结束再销毁。
let lifecycle: Promise<void> = Promise.resolve();
async function showSource() {
  mode.value = "source";
  conversion.value = null;
  loading.value = false;
  generation++;
  lifecycle = lifecycle.catch(() => {}).then(() => destroy());
  await lifecycle;
  await nextTick();
  textarea.value?.focus();
}
async function showVisual() {
 if(editor && lastOutput===props.modelValue && !loading.value){mode.value='visual';return;}
  const token = ++generation;
  loading.value = true;
  failure.value = "";
  conversion.value = null;
  mode.value = "visual";
  userEditing.value = false;
  lifecycle = lifecycle
    .catch(() => {})
    .then(async () => {
      try {
        await destroy();
        await nextTick();
        const { Crepe } = await import("@milkdown/crepe");
        if (dead || token !== generation || !host.value) return;
        const instance = new Crepe({
          root: host.value,
          defaultValue: props.modelValue,
          features:{[Crepe.Feature.TopBar]:true},
          featureConfigs:{
           [Crepe.Feature.TopBar]:{headingOptions:[{label:'正文',level:null},{label:'一级标题',level:1},{label:'二级标题',level:2},{label:'三级标题',level:3}]},
           [Crepe.Feature.ImageBlock]:{onUpload:notesApi.uploadImage,inlineUploadButton:'上传图片',blockUploadButton:'上传图片（≤512KB）',blockConfirmButton:'插入',inlineUploadPlaceholderText:'或粘贴图片链接',blockUploadPlaceholderText:'或粘贴图片链接',blockCaptionPlaceholderText:'图片说明'},
          },
        });
        editor = instance;
        instance.on((listener) => {
         listener.selectionUpdated((ctx,selected)=>{const view=ctx.get(editorViewCtx);const text=selected.empty?'':view.state.doc.textBetween(selected.from,selected.to,'\n');emit('selection',text.trim()?{text,from:selected.from,to:selected.to,markdown:props.modelValue,generation:token,source:'visual'}:null)});
         listener.markdownUpdated((_ctx, markdown) => {
            if (
              editor !== instance ||
              mode.value !== "visual" ||
              loading.value ||
              conversion.value !== null ||
              !userEditing.value
            )
              return;
            lastOutput = markdown;
            emit("update:modelValue", markdown);
          });
        });
        await instance.create();
        if (dead || token !== generation) return;
        const serialized = instance.getMarkdown();
        lastOutput = props.modelValue;
        // 不在未编辑时把解析器规范化结果写回业务正文。
        if (serialized !== props.modelValue && renderMarkdown(serialized)!==renderMarkdown(props.modelValue)) {
          conversion.value = serialized;
          instance.setReadonly(true);
        }
      } catch (e: unknown) {
        failure.value = e instanceof Error ? e.message : "编辑器初始化失败";
        mode.value = "source";
        await destroy();
      } finally {
        if (token === generation) loading.value = false;
      }
    });
  await lifecycle;
}
function safeLinkClick(event: MouseEvent) {
  const link = (event.target as Element).closest("a[href]");
  if (link) {
    try {
      const url = new URL(link.getAttribute("href") || "", location.href);
      if (!["http:", "https:", "mailto:"].includes(url.protocol))
        event.preventDefault();
    } catch {
      event.preventDefault();
    }
  }
}
function acceptConversion() {
  if (conversion.value === null) return;
  lastOutput = conversion.value;
  emit("update:modelValue", conversion.value);
  conversion.value = null;
  editor?.setReadonly(false);
}
watch(
  () => props.modelValue,
  (next) => {
    if (mode.value === "visual" && next !== lastOutput) void showVisual();
  },
);
onBeforeUnmount(() => {
  dead = true;
  generation++;
  lifecycle = lifecycle.catch(() => {}).then(() => destroy());
});
function showRead(){mode.value='read';}
function sourceSelection(){const el=textarea.value;if(!el||composing.value)return;const text=el.value.slice(el.selectionStart,el.selectionEnd);emit('selection',text.trim()?{text,from:el.selectionStart,to:el.selectionEnd,markdown:props.modelValue,generation,source:'source'}:null)}
function sourceKeydown(event:KeyboardEvent){if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==='z'){event.preventDefault();event.shiftKey?redo():undo()}}
function undo(){if(mode.value==='visual'&&editor){userEditing.value=true;editor.editor.action(ctx=>ctx.get(commandsCtx).call(undoCommand.key));}else if(mode.value==='source'&&sourcePast.length){sourceFuture.push(props.modelValue);emit('update:modelValue',sourcePast.pop()!)}}
function redo(){if(mode.value==='visual'&&editor){userEditing.value=true;editor.editor.action(ctx=>ctx.get(commandsCtx).call(redoCommand.key));}else if(mode.value==='source'&&sourceFuture.length){sourcePast.push(props.modelValue);emit('update:modelValue',sourceFuture.pop()!)}}
function jumpToHeading(index:number){if(mode.value!=='source'){const root=mode.value==='read'?readRoot.value:host.value;root?.querySelectorAll<HTMLElement>('h1,h2,h3')[index]?.scrollIntoView({behavior:'smooth',block:'start'});return;}const lines=props.modelValue.split('\n');let code=false,count=0,offset=0;for(const line of lines){if(/^\s*(```|~~~)/.test(line))code=!code;if(!code&&/^#{1,3}\s/.test(line)){if(count++===index){textarea.value?.focus();textarea.value?.setSelectionRange(offset,offset);if(textarea.value)textarea.value.scrollTop=offset/Math.max(1,props.modelValue.length)*textarea.value.scrollHeight;return;}}offset+=line.length+1}}
function applySelection(text:string,selection:NoteSelection){if(selection.markdown!==props.modelValue||selection.generation!==generation||conversion.value!==null)return false;if(selection.source==='source'){sourcePast.push(props.modelValue);emit('update:modelValue',props.modelValue.slice(0,selection.from)+text+props.modelValue.slice(selection.to));return true;}if(!editor)return false;let applied=false;editor.editor.action(ctx=>{const view=ctx.get(editorViewCtx);if(view.state.doc.textBetween(selection.from,selection.to,'\n')!==selection.text)return;userEditing.value=true;view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc,selection.from,selection.to)));insert(text)(ctx);applied=true;});return applied;}
onMounted(()=>{if(props.initialMode==='visual')void showVisual()});
defineExpose({textarea,showSource,showVisual,showRead,jumpToHeading,applySelection,mode});

</script>
<style scoped>
.note-mode-spacer{flex:1}
.note-read-view {max-width:var(--kb-notes-reading-width);width:100%;margin:auto;padding:var(--kb-space-5);line-height:var(--kb-notes-leading);font-size:var(--kb-notes-body-size);overflow-wrap:anywhere}
.note-editor :deep(.milkdown-top-bar){position:sticky;top:0;z-index:5;background:var(--kb-card);border-bottom:1px solid var(--kb-border);padding:var(--kb-space-2);font-family:inherit}
.note-editor :deep(.milkdown-top-bar .top-bar-content){flex-wrap:wrap}
.note-editor .kb-btn {
  padding: var(--kb-space-2) var(--kb-space-3);
  border-radius: var(--kb-radius-md);
  color: var(--kb-muted-foreground);
}
.note-editor .kb-btn[aria-pressed="true"] {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
}
.note-editor > div:first-child {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kb-space-2);
  padding: var(--kb-space-2);
}
.note-editor {
  flex: 1;
  min-height: calc(var(--kb-space-8) * 8);
}
.content-textarea:focus {
  border-color: transparent !important;
  box-shadow: none !important;
  outline: none !important;
}
.content-textarea {
  flex: 1;
  resize: vertical;
  width: 100%;
  min-height: calc(var(--kb-space-8) * 14);
  padding: var(--kb-space-5);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  font-family: var(--kb-font-mono, monospace);
  line-height: 1.8;
}
.note-crepe {
  min-width: 0;
  overflow-wrap: anywhere;
  --crepe-font-title: var(--kb-font-sans, inherit);
  --crepe-font-default: var(--kb-font-sans, inherit);
  --crepe-color-background: var(--kb-card);
  --crepe-color-on-background: var(--kb-foreground);
  --crepe-color-primary: var(--kb-primary);
  --crepe-color-surface: var(--kb-muted);
}
.note-crepe :deep(.milkdown h1),
.note-crepe :deep(.milkdown h2),
.note-crepe :deep(.milkdown h3) {
  font-family: inherit;
}
.note-crepe :deep(.milkdown) {
  max-width: var(--kb-notes-reading-width);
  margin: auto;
}
.note-crepe :deep(.ProseMirror) {
  padding: var(--kb-space-5);
  min-height: calc(var(--kb-space-8) * 14);
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 40vh;
  overflow: auto;
}
</style>

<style scoped>
.note-editor :deep(.milkdown .ProseMirror),.note-read-view{font-family:inherit;font-size:var(--kb-notes-body-size);line-height:var(--kb-notes-leading)}
.note-editor :deep(.ProseMirror h1),.note-read-view :deep(h1){font-size:var(--kb-notes-h1-size);line-height:1.4}
.note-editor :deep(.ProseMirror h2),.note-read-view :deep(h2){font-size:var(--kb-notes-h2-size);line-height:1.45}
.note-editor :deep(.ProseMirror h3),.note-read-view :deep(h3){font-size:var(--kb-notes-h3-size);line-height:1.5}
.note-editor :deep(.ProseMirror h1),.note-editor :deep(.ProseMirror h2),.note-editor :deep(.ProseMirror h3),.note-read-view :deep(h1),.note-read-view :deep(h2),.note-read-view :deep(h3){font-weight:650;scroll-margin-top:calc(var(--kb-notes-toolbar-height) + var(--kb-space-6));margin:var(--kb-space-6) 0 var(--kb-space-3)}
.note-editor :deep(.cm-editor),.note-read-view :deep(pre){font-size:var(--kb-notes-code-size);max-width:100%;overflow-x:auto}
.note-editor :deep(table),.note-read-view :deep(table){max-width:100%;border-collapse:collapse}
.note-read-view :deep(td),.note-read-view :deep(th){padding:var(--kb-space-2) var(--kb-space-3);border:1px solid var(--kb-border)}
.note-read-view :deep(img){max-width:100%;height:auto}
.note-read-view :deep(blockquote){border-left:var(--kb-space-1) solid var(--kb-primary);background:var(--kb-primary-soft);padding:var(--kb-space-3) var(--kb-space-4);margin:var(--kb-space-4) 0;border-radius:var(--kb-radius-sm)}
</style>
