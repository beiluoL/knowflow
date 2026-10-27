// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
const api = vi.hoisted(() => ({ chapterDetail: vi.fn(), chapters: vi.fn(), pathDetail: vi.fn(), chapterKnowledge: vi.fn(), favorite: vi.fn(), toggleChapterFavorite: vi.fn(), createChapterNote: vi.fn(), saveReadingPosition: vi.fn(), updateVideoProgress: vi.fn() }))
vi.mock('@/api', () => ({ learningApi: api, docsApi: { list: vi.fn().mockResolvedValue({ records: [] }) } }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: unknown) => String(e), confirmDialog: vi.fn() }))
vi.mock('@/utils/celebrate', () => ({ celebrateCertificate: vi.fn() }))
import ChapterLearn from '../ChapterLearn.vue'
const chapter = (id: number) => ({ id, pathId: 3, title: `Chapter ${id}`, contentHash:'a'.repeat(64), content: '## Read **first**\n[Official](https://example.com)\n\n```java\n## not a heading\n```\n\n## Summary', sortOrder: id, duration: 35, completed: false, locked: false })
beforeEach(() => { vi.stubGlobal('crypto',{subtle:{digest:vi.fn(async (_algorithm:string,input:ArrayBuffer)=>new Uint8Array(32).fill(new TextDecoder().decode(input).includes('正文已经修订')?2:1).buffer)}}); vi.resetAllMocks(); api.chapterDetail.mockImplementation(async id => chapter(id)); api.chapters.mockResolvedValue([chapter(1), chapter(2)]); api.pathDetail.mockResolvedValue({ id: 3, title: 'Path', level: '进阶' }); api.chapterKnowledge.mockResolvedValue({ knowledge: [] }); api.favorite.mockResolvedValue({ favorited: false }); api.toggleChapterFavorite.mockResolvedValue({ favorited: true }); api.createChapterNote.mockResolvedValue({ id: 10 }) })
it('navigates actual timeline nodes and does not unlock unfinished prerequisites',async()=>{
 api.chapters.mockResolvedValue([chapter(1),{...chapter(2),locked:true}]);const {w,router}=await setup();
 expect(w.find('button[aria-label="第 2 章：Chapter 2"]').attributes('disabled')).toBeDefined();
 expect(router.currentRoute.value.path).toBe('/learning/chapter/1');w.unmount();
})
it('collapses only children of the selected real heading',async()=>{
 api.chapterDetail.mockResolvedValue({...chapter(1),content:'## Parent\nText\n### Child\nText\n## Other\nText'});const {w}=await setup();
 await w.find('button[aria-label="收起 Parent 的子目录"]').trigger('click');
 expect(w.find('a[href="#heading-child"]').exists()).toBe(false);expect(w.find('a[href="#heading-other"]').exists()).toBe(true);w.unmount();
})
it('does not offer unavailable training resources as enabled actions',async()=>{
 const {w}=await setup();expect(w.find('.quiz-tool').attributes('disabled')).toBeDefined();expect(w.find('.code-tool').attributes('disabled')).toBeDefined();w.unmount();
})
async function setup(attachTo?:HTMLElement) { const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/learning/chapter/:id', component: ChapterLearn }] }); await router.push('/learning/chapter/1'); await router.isReady(); const w = mount(ChapterLearn, { attachTo, global: { plugins: [router], stubs: { InterviewSeedChapter: true } } }); await flushPromises(); return { w, router } }
it('shows server completion progress rather than visited dots and keeps objectives uncompleted',async()=>{
 api.chapters.mockResolvedValue([{...chapter(1),completed:false},{...chapter(2),completed:true}])
 api.pathDetail.mockResolvedValue({id:3,title:'Path',level:'进阶',progress:50,completedChapters:1,chapterCount:2})
 api.chapterDetail.mockResolvedValue({...chapter(1),content:'## 学习目标\n- Explain\n\n难度：2；预计 35 分钟；面试重要度：S\n\nSummary\n\n## Read\nText'})
 const {w}=await setup()
 expect(w.find('progress').attributes('value')).toBe('50')
 expect(w.text()).toContain('已完成 1 / 2 章')
 expect(w.find('.learning-objectives').text()).toContain('Explain')
 expect(w.find('.learning-objectives input[type=checkbox]').exists()).toBe(false)
 expect(w.find('.progress-dot').exists()).toBe(false)
 expect(w.find('.chapter-tool-link').attributes('href')).toContain('chapterId=1')
 w.unmount()
})
it('initializes declarative recall after the chapter finishes loading', async () => {
  api.chapterDetail.mockResolvedValue({...chapter(1),content:'## Recall\n\n```quiz-run\nQ: Choose A\nA. Yes\nB. No\n答案: A\n解析: Because\n```'})
  const {w}=await setup(document.body)
  try {expect(w.find('.quiz-card').exists()).toBe(true); await w.find('.quiz-opt').trigger('click'); expect(w.find('.quiz-feedback').text()).toContain('回答正确')} finally {w.unmount()}
})
it('never records idle page time as video playback', async () => {
  api.chapterDetail.mockResolvedValue({...chapter(1),content:'## Video\n\n[video](https://www.youtube.com/watch?v=example)'})
  api.updateVideoProgress.mockResolvedValue(100)
  vi.useFakeTimers({toFake:['setInterval','clearInterval']})
  let w:Awaited<ReturnType<typeof setup>>['w']|undefined
  try {
    ({w}=await setup())
    expect(w.find('iframe[title="章节参考视频"]').exists()).toBe(true)
    expect(w.find('iframe').attributes('sandbox')).toBe('allow-scripts allow-presentation')
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.updateVideoProgress).not.toHaveBeenCalled()
    expect(w.text()).toContain('不自动记录观看进度')
  } finally {w?.unmount(); vi.useRealTimers()}
})
it('uses real markdown links, copy controls and numbered headings excluding fenced code', async () => { const { w } = await setup(); expect(w.find('a[href="https://example.com"]').exists()).toBe(true); expect(w.find('.code-copy-btn').exists()).toBe(true); expect(w.findAll('.outline-item').map(x => x.text())).toEqual(['01. Read first', '02. Summary']); expect(w.find('.chapter-article h2').text()).toBe('01. Read first'); w.unmount() })
it('offers a collapsible outline for narrow PC viewports', async () => { const { w } = await setup(); const toggle = w.find('.outline-toggle'); expect(toggle.exists()).toBe(true); expect(toggle.attributes('aria-expanded')).toBe('false'); await toggle.trigger('click'); expect(toggle.attributes('aria-expanded')).toBe('true'); w.unmount() })
it('keeps the page title single when the document starts with a title', async () => { api.chapterDetail.mockResolvedValue({...chapter(1),content:'# Document title\n\n## Content\nText'}); const {w}=await setup(); expect(w.findAll('h1')).toHaveLength(1); w.unmount() })
it('treats runnable fence content as literal code, never HTML or a same-origin execution control', async () => {
  const literal = '</textarea><img data-unsafe-code="yes" src="invalid" onerror="void 0">'
  api.chapterDetail.mockResolvedValue({...chapter(1),pathId:null,content:`## Example\n\n\`\`\`js-run\n${literal}\n\`\`\``})
  const {w}=await setup(document.body)
  try {
    expect(w.find('[data-unsafe-code]').exists()).toBe(false)
    expect(w.find('code').text()).toContain(literal)
    expect(w.find('.code-copy-btn').exists()).toBe(true)
    expect(w.find('.code-run-btn').exists()).toBe(false)
  } finally {w.unmount()}
})
it('route change loads the new chapter and errors never retain the previous title', async () => { const { w, router } = await setup(); api.chapterDetail.mockRejectedValueOnce(new Error('offline')); await router.push('/learning/chapter/2'); await flushPromises(); expect(w.text()).not.toContain('Chapter 1'); expect(w.text()).toContain('章节加载失败'); expect(w.findAll('button').some(x => x.text() === '重试')).toBe(true); w.unmount() })
it('favorite success comes from the server rather than a local toggle', async () => { const { w } = await setup(); await w.findAll('button').find(x => x.text() === '收藏')!.trigger('click'); await flushPromises(); expect(api.toggleChapterFavorite).toHaveBeenCalledWith(1); expect(w.text()).toContain('已收藏'); w.unmount() })
it('a late old response cannot replace the new chapter', async () => {
  let resolveOld!: (c: ReturnType<typeof chapter>) => void
  api.chapterDetail.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/learning/chapter/:id', component: ChapterLearn }] })
  await router.push('/learning/chapter/1'); await router.isReady()
  const w = mount(ChapterLearn, { global: { plugins: [router], stubs: { InterviewSeedChapter: true } } })
  await router.push('/learning/chapter/2'); await flushPromises(); resolveOld(chapter(1)); await flushPromises()
  expect(w.text()).toContain('Chapter 2'); expect(w.text()).not.toContain('Chapter 1Read'); w.unmount()
})
it('saving a note binds the displayed chapter and preserves content on failure', async () => {
  const { w } = await setup(); await w.findAll('button').find(x => x.text() === '写本章笔记')!.trigger('click')
  await w.find('textarea').setValue('My own explanation')
  api.createChapterNote.mockRejectedValueOnce(new Error('note offline'))
  await w.find('.note-save').trigger('click'); await flushPromises()
  expect((w.find('textarea').element as HTMLTextAreaElement).value).toBe('My own explanation')
  expect(w.text()).toContain('note offline')
  await w.find('.note-save').trigger('click'); await flushPromises()
  expect(api.createChapterNote).toHaveBeenLastCalledWith({chapterId:1,pathId:3,title:'Chapter 1 · 学习笔记',content:'My own explanation'})
  expect(w.find('textarea').exists()).toBe(false); w.unmount()
})

it('saves current reading anchor only after server success and preserves a retry on conflict', async () => {
 const {w}=await setup();api.saveReadingPosition.mockRejectedValueOnce(new Error('其他页面已更新'));
 await w.find('[data-reading-save]').trigger('click');await vi.waitFor(()=>expect(api.saveReadingPosition).toHaveBeenCalled());await flushPromises();
 expect(w.text()).toContain('其他页面已更新');expect(w.text()).not.toContain('阅读位置已保存');
 api.saveReadingPosition.mockClear();api.saveReadingPosition.mockResolvedValue({...chapter(1),readingAnchor:'heading-read-first',readingRevision:1});
 await w.find('[data-reading-save]').trigger('click');await vi.waitFor(()=>expect(api.saveReadingPosition).toHaveBeenCalled());await flushPromises();
 expect(api.saveReadingPosition).toHaveBeenLastCalledWith(1,'heading-read-first',0,'a'.repeat(64),expect.stringMatching(/^[a-f0-9]{64}$/));expect(w.text()).toContain('阅读位置已保存');w.unmount();
})
it('offers explicit recovery and warns when saved chapter content has changed', async () => {
 api.chapterDetail.mockResolvedValue({...chapter(1),readingAnchor:'heading-summary',readingRevision:1,readingPositionStale:true});
 const {w}=await setup();expect(w.text()).toContain('正文已更新');expect(w.find('[data-reading-resume]').exists()).toBe(false);w.unmount();
})

it('detects a revised displayed reading even when original chapter content is unchanged', async () => {
 api.chapterDetail.mockResolvedValue({...chapter(1),readingAnchor:'heading-summary',readingRevision:1,readingPresentationHash:'01'.repeat(32)});
 const {w}=await setup();
 w.findComponent({name:'InterviewSeedChapter'}).vm.$emit('revision',{content:'## Summary\n正文已经修订',knowledge:[],questions:[],exercises:[],notes:[],flashcardCount:0});
 await vi.waitFor(()=>expect(w.text()).toContain('正文已更新'));expect(w.find('[data-reading-resume]').exists()).toBe(false);w.unmount();
})
