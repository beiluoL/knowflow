/**
 * Web Speech API 类型声明。
 * TypeScript 内置 lib.dom.d.ts 尚未包含 SpeechRecognition 相关类型，
 * 此处补齐以便业务代码不再使用 any。
 * 规格：https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API
 */

/** 单条识别结果的候选文本。 */
interface SpeechRecognitionAlternative {
  readonly transcript: string
  readonly confidence: number
}

/** 一条识别结果（可能含多个候选）。 */
interface SpeechRecognitionResult {
  readonly isFinal: boolean
  readonly length: number
  item(index: number): SpeechRecognitionAlternative
  [index: number]: SpeechRecognitionAlternative
}

/** 识别结果集合（类数组）。 */
interface SpeechRecognitionResultList {
  readonly length: number
  item(index: number): SpeechRecognitionResult
  [index: number]: SpeechRecognitionResult
}

/** onresult 事件对象。 */
interface SpeechRecognitionEvent extends Event {
  readonly resultIndex: number
  readonly results: SpeechRecognitionResultList
}

/** onerror 事件对象。 */
interface SpeechRecognitionErrorEvent extends Event {
  readonly error: string
  readonly message: string
}

/** SpeechRecognition 实例接口。 */
interface SpeechRecognitionInstance extends EventTarget {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  start(): void
  stop(): void
  abort(): void
  onresult: ((event: SpeechRecognitionEvent) => void) | null
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null
  onend: (() => void) | null
  onstart: (() => void) | null
}

/** SpeechRecognition 构造函数。 */
interface SpeechRecognitionConstructor {
  new (): SpeechRecognitionInstance
  prototype: SpeechRecognitionInstance
}

/** 扩展 Window 接口，挂载浏览器厂商前缀的 SpeechRecognition。 */
interface Window {
  SpeechRecognition?: SpeechRecognitionConstructor
  webkitSpeechRecognition?: SpeechRecognitionConstructor
}
