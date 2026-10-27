import type {PlaygroundEntry} from './registry'
const definitions = [
 ['Accordion','折叠面板','展开多个本地章节；原生 details 支持键盘。'],
 ['Avatar','头像','复用真实 Avatar，展示首字母、尺寸和在线状态；不测试图片失败回退。'],
 ['ButtonGroup','按钮组','互斥视图选择，保留按钮键盘反馈。'],
 ['Slider','滑块','原生 range 的范围、步进和键盘操作。'],
 ['DataTable','数据表格','本地排序、过滤、行选择、分页与列显隐。'],
 ['InputGroup','组合输入','组合前后缀与共享输入表面。'],
 ['NumberInput','数字输入','保留空值，支持 0–100 和步进 5。'],
 ['CopyButton','复制按钮','经点击复制本地文本；明确显示剪贴板失败。'],
 ['StatusIndicator','状态指示','状态色配合可读文字，支持本地切换。'],
 ['ResizablePanels','可调面板','键盘方向键和原生范围控件调整面板比例。'],
 ['Toolbar','工具栏','本地格式切换与撤销，原生按钮导航。'],
] as const
const draftProps: Record<string, Array<[string,string,unknown,string]>> = {
 Accordion:[['multiple','boolean',true,'本地原生 details 允许多项展开']], Avatar:[['name','string','KnowFlow','真实 Avatar 首字母'],['size','enum','md','sm / md / lg / xl'],['showStatus','boolean',true,'真实在线状态点'],['iconName','string','','空值展示首字母'],['status','enum','online','真实在线状态']],
 ButtonGroup:[['modelValue','string','列表','本地互斥选择']], Slider:[['modelValue','number',40,'0–100，步进5']], DataTable:[['pageSize','number',3,'本地分页大小'],['rows','array',[],'六条本地固定数据']],
 InputGroup:[['modelValue','string','notes','组合输入值']], NumberInput:[['modelValue','number | null',null,'空值保持 null，0–100，步进5']],
 CopyButton:[['text','string','KnowFlow 本地示例','点击复制，失败提示']], StatusIndicator:[['status','enum','online','online / busy / away / offline']], ResizablePanels:[['ratio','number',50,'25–75，步进5']], Toolbar:[['bold','boolean',false,'本地格式切换'],['italic','boolean',false,'本地格式切换']],
}
export const extensionNames: string[] = definitions.map(([name])=>name)
export const extensionEntries: PlaygroundEntry[] = definitions.map(([name,title,description])=>({
 id:`v5-${name.toLowerCase()}`,name,title,category:'扩展组件',previewKey:name,
 description:`${description} ${name==='Avatar'?'真实组件适配。':'Demo-only：隔离接口草案，尚无生产公共实现。'}`,
 implementationStatus:name==='Avatar'?'已接入真实组件':'仅有内部演示',
 sourcePath:name==='Avatar'?'frontend/src/components/ui/Avatar.vue':'frontend/src/dev/ExtensionDemos.vue',
 supportedProps:(draftProps[name]||[]).map(([key])=>key),props:(draftProps[name]||[]).map(([key,type,value,description])=>({name:key,type,default:value,sourceDefault:name==='Avatar'?({name:'未指定',size:'md',showStatus:'false',iconName:'user',status:'未指定'} as Record<string,string>)[key]||'未指定':JSON.stringify(value),required:false,description:`${name==='Avatar'?'真实接口':'Demo-only 草案'}：${description}；交互由预览内部控制`,editable:name==='Avatar',options:name==='Avatar'&&key==='size'?['sm','md','lg','xl']:name==='Avatar'&&key==='status'?['online','offline','busy','away']:undefined})),variants:[],events:name==='Avatar'?[]:['event(name, value)'],slots:[],designSpecs:{},
 examples:`<ExtensionDemos name="${name}" @event="record" />`,
 accessibilityNotes:'本地状态；不接 API、Store 或持久化。键盘使用原生语义。不支持服务端数据、虚拟滚动或生产权限。',
}))
