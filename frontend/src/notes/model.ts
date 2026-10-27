/** 兼容旧CSV标签；新笔记使用JSON数组保留标签里的标点。 */
export function noteTags(raw?: string|null):string[]{if(!raw)return[];try{const v:unknown=JSON.parse(raw);if(Array.isArray(v))return v.filter((x):x is string=>typeof x==='string')}catch{/* 旧数据为CSV */}return raw.split(',').map(x=>x.trim()).filter(Boolean)}
export function serializeTags(tags:string[]){return JSON.stringify([...new Set(tags.map(x=>x.trim()).filter(Boolean))])}
export function noteSummary(raw:string){return raw.replace(/!\[[^\]]*\]\([^)]*\)/g,'[图片]').replace(/\[([^\]]+)\]\([^)]*\)/g,'$1').replace(/```[\s\S]*?```/g,'[代码]').replace(/[#*>`~_[\]]/g,'').replace(/\s+/g,' ').trim().slice(0,90)}
export function noteTime(raw:string){const d=new Date(raw);if(Number.isNaN(d.getTime()))return '时间未知';return new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(d)}
export const NOTE_ROUTE_NAMES=new Set(['NotesManage','NoteCreate','NoteEdit']);
