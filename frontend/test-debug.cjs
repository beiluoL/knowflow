const MarkdownIt = require('markdown-it');
const taskLists = require('markdown-it-task-lists');

const md = new MarkdownIt({ html: false });
md.use(taskLists);

// 最终版变量插件：简化的上下文检查
function variablePlugin(md) {
  md.inline.ruler.before('link', 'variable', (state, silent) => {
    const pos = state.pos;
    const ch = state.src.charCodeAt(pos);
    if (ch !== 0x5B) return false;
    
    const beforeText = state.src.substring(Math.max(0, pos - 30), pos);
    const isAtLineStart = /^[\r\n\s]*$/.test(beforeText) || /[\r\n][\s]*$/.test(beforeText);

    let end = state.src.indexOf(']', pos + 1);
    if (end === -1) return false;
    const content = state.src.substring(pos + 1, end);
    
    const nextChar = state.src.charCodeAt(end + 1);
    const isTaskListMarker = (content === 'x' || content === ' ') && nextChar === 0x20;
    
    // 关键检查：如果在行首，且是任务列表标记，则跳过
    if (isAtLineStart && isTaskListMarker) {
      return false;
    }

    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(content)) return false;
    if (nextChar === 0x28) return false;

    if (!silent) {
      const token = state.push('variable', '', 0);
      token.content = content;
      token.map = [pos, end + 1];
    }
    state.pos = end + 1;
    return true;
  });
  
  md.renderer.rules.variable = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    return `<span class="template-variable" data-variable="${token.content}">${token.content}</span>`;
  };
}
md.use(variablePlugin);

// 所有测试用例
const testCases = [
  // 1. 任务列表 (之前失败的案例)
  "# 标题\n- [ ] 未完成任务\n- [x] 已完成任务",
  // 2. 变量
  '这是一个 [user_name] 变量',
  // 3. 链接
  '[点击这里](https://example.com)',
  // 4. 冲突场景
  '[text](url)',
  '[my_var](https://a.com)',
  // 5. 嵌套
  '**[bold_var]**',
  '*[italic_var]*',
  // 6. 非法变量名
  '[123_invalid]',
  '[var with space]',
  '[普通文本]',
  // 7. 混合
  '[var] 和 [link](url) 混合',
  // 8. 任务列表中包含变量
  '- [ ] 任务 with [sub_task]\n- [x] 完成 with [done_var]',
  // 9. 独立的 [x] 应该是变量
  '变量 [x] 单独出现',
  '变量 [x] 在中间',
  // 10. 数字列表
  '1. [x] 有序任务',
  '2. [ ] 有序未完成',
  // 11. 强调与变量混合
  '这是 **[bold_var]** 和 *[italic_var]* 的测试',
];

testCases.forEach((input, i) => {
  console.log(`\n=== Test ${i + 1} ===`);
  console.log('Input:', JSON.stringify(input));
  const output = md.render(input);
  console.log('Output:', output.replace(/\n/g, '\\n'));
});