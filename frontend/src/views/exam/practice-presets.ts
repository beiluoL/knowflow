import type { ComposeRequest } from '@/api/exam'
export interface PracticePreset { key: string; label: string; description: string; config: Partial<ComposeRequest> }
/** Configuration recipes for the existing PaperComposer; no second selection engine. */
export const practicePresets: PracticePreset[] = [
  { key:'TOPIC', label:'知识点专项', description:'在下方选择真实题库知识点，并设置难度、题型和数量。', config:{strategy:'RANDOM',includeAnswered:true} },
  { key:'FOCUS', label:'重点题训练', description:'按已有公共练习频次选题，不能将练习频次当作真实招聘面试出现频率。', config:{strategy:'FREQUENCY',includeAnswered:true} },
  { key:'RANDOM', label:'随机练习', description:'固定种子与题库版本时，可复现选题结果。', config:{strategy:'RANDOM',includeAnswered:true} },
  { key:'WEAK', label:'薄弱点专项', description:'使用真实历史作答；证据不足时拒绝编造薄弱点。', config:{strategy:'WEAK',includeAnswered:true} },
  { key:'MISTAKE', label:'错题复练', description:'只选择当前账号题库关联、未掌握的错题；没有题目时明确提示。', config:{strategy:'MISTAKE',includeAnswered:true} },
  { key:'NEW', label:'未做题优先', description:'排除当前账号实际作答过的题目身份，题量不足不会重复凑数。', config:{strategy:'RANDOM',includeAnswered:false} },
  { key:'COMPREHENSIVE', label:'Java + AI 综合模拟', description:'复用已发布题库的知识点均衡策略，不包含待审核内容。', config:{strategy:'BALANCED',domain:'JAVA_AI',topics:[],includeAnswered:true} },
  { key:'PROJECT', label:'项目场景专项', description:'使用普通题库已有 PROJECT 题目；独立场景流程继续由场景应用提供。', config:{strategy:'SCENARIO',domain:'JAVA_AI',topics:['PROJECT'],includeAnswered:true} },
  { key:'SPRINT', label:'面试冲刺', description:'15分钟训练，按到期错题、重复错误、薄弱点、目标与未做题规则优先选择。', config:{strategy:'REVIEW',durationMinutes:15,includeAnswered:true} },
]
