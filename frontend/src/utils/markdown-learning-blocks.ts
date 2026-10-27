import type MarkdownIt from 'markdown-it'
type Markdown = InstanceType<typeof MarkdownIt>
const labels:Record<string,string>={info:'信息',tip:'学习技巧',warning:'注意事项',danger:'错误示例'}
const symbols:Record<string,string>={info:'ℹ',tip:'💡',warning:'⚠',danger:'✗'}

/** A small allowlist of authored blocks, not executable HTML or arbitrary components. */
export function learningBlocks(md:Markdown) {
 md.block.ruler.before('fence','learning_blocks',(state,start,end,silent)=>{
  if(state.sCount[start]-state.blkIndent>=4)return false
  const first=state.src.slice(state.bMarks[start]+state.tShift[start],state.eMarks[start]).trim()
  const match=/^:::(info|tip|warning|danger|details|quiz)(?:\s+(.*))?$/.exec(first)
  if(!match)return false
  if(silent)return true
  const kind=match[1],arg=match[2]||''
  if(kind==='quiz') {
   const ref=/^id="([\w./:-]{1,160})"$/.exec(arg)
   const token=state.push('learning_quiz_ref','',0);token.content=ref?.[1]||''
   state.line=start+1
   if(state.line<end&&state.src.slice(state.bMarks[state.line]+state.tShift[state.line],state.eMarks[state.line]).trim()===':::')state.line++
   return true
  }
  let close=start+1,depth=1,fence=''
  for(;close<end;close++) {
   const line=state.src.slice(state.bMarks[close]+state.tShift[close],state.eMarks[close]).trim()
   const marker=/^(`{3,}|~{3,})/.exec(line)
   if(marker){if(!fence)fence=marker[1];else if(marker[1][0]===fence[0]&&marker[1].length>=fence.length)fence='';continue}
   if(fence)continue
   if(/^:::quiz(?:\s|$)/.test(line)) {
    const next=close+1
    if(next<end&&state.src.slice(state.bMarks[next]+state.tShift[next],state.eMarks[next]).trim()===':::')close++
    continue
   }
   if(/^:::(info|tip|warning|danger|details)(?:\s|$)/.test(line))depth++
   if(line===':::'&&--depth===0)break
  }
  // An unclosed container stays readable literal Markdown.
  if(close>=end)return false
  const open=state.push('learning_block_open',kind==='details'?'details':'aside',1)
  open.meta={kind,title:arg.slice(0,160)||labels[kind]||'展开内容'}
  const oldMax=state.lineMax;state.lineMax=close
  state.md.block.tokenize(state,start+1,close)
  state.lineMax=oldMax
  state.push('learning_block_close',kind==='details'?'details':'aside',-1)
  state.line=close+1
  return true
 },{alt:['paragraph','reference','blockquote','list']})
 md.renderer.rules.learning_block_open=(tokens,i)=>{
  const kind=String(tokens[i].meta?.kind||'info'),title=String(tokens[i].meta?.title||'信息')
  const safe=md.utils.escapeHtml(title)
  if(kind==='details')return `<details class="learning-details"><summary>${safe}</summary>\n`
  return `<aside class="learning-callout learning-callout-${kind}" aria-label="${safe}"><p class="learning-callout-title"><span aria-hidden="true">${symbols[kind]}</span>${safe}</p>\n`
 }
 md.renderer.rules.learning_block_close=(tokens,i)=>`</${tokens[i].tag}>\n`
 md.renderer.rules.learning_quiz_ref=(tokens,i)=>tokens[i].content
  ? `<button type="button" class="chapter-quiz-reference" data-quiz-ref-key="${md.utils.escapeHtml(tokens[i].content)}" disabled>本章题目未就绪（${md.utils.escapeHtml(tokens[i].content)}）</button>\n`
  : '<p class="quiz-reference-invalid" role="note">无效题目引用：请使用本章真实题目ID或稳定Key。</p>\n'
}
