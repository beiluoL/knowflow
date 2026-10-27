/** Logs only bounded local demo values; DOM events, functions and secret fields are never dumped. */
export function safeFixtureLog(value:unknown):string{
 const seen=new WeakSet<object>()
 try{return JSON.stringify(value,(key,item)=>{
  if(/token|password|secret|authorization|api.?key/i.test(key))return '[已隐藏]'
  if(typeof item==='function')return '[函数]'
  if(typeof item==='string')return item.slice(0,500)
  if(item&&typeof item==='object'){if(item instanceof Event)return {type:item.type};if(seen.has(item))return '[循环引用]';seen.add(item)}
  return item
 })?.slice(0,1000)||'null'}catch{return '[不可序列化]'}
}
