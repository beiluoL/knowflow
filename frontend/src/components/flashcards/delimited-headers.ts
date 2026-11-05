/** 仅读取首条记录用于映射，后端仍是完整文件解析的权威来源。 */
export function delimitedHeaders(content: string, separator: ',' | '\t'): string[] {
 const headers:string[]=[];let field='',quoted=false,closed=false
 const source=content.replace(/^\uFEFF/,'')
 for(let i=0;i<source.length;i++){
  const c=source[i]
  if(quoted){if(c==='"'){if(source[i+1]==='"'){field+='"';i++}else{quoted=false;closed=true}}else field+=c}
  else if(c===separator){headers.push(field);field='';closed=false}
  else if(c==='\r'||c==='\n'){headers.push(field);return headers.slice(0,32)}
  else if(c==='"'&&!field&&!closed)quoted=true
  else {if(closed||c==='"')return [];field+=c}
  if(field.length>50000||headers.length>=32)return []
 }
 if(quoted)return [];headers.push(field);return headers
}
