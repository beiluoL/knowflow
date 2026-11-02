import { readFile, writeFile } from 'node:fs/promises'
// 单一协议定义生成前端常量；--check用于CI/测试发现漂移，不自动修改基线。
const source = new URL('../../backend/src/main/resources/interview/voice-v1.json', import.meta.url)
const target = new URL('../src/interview/voice/protocol.generated.ts', import.meta.url)
const value = JSON.parse(await readFile(source, 'utf8'))
const code = `// 由 scripts/generate-interview-voice.mjs 生成，请修改后端 voice-v1.json 后重新生成。\nexport const VOICE_PROTOCOL_VERSION = ${value.protocolVersion} as const\nexport const VOICE_INPUT_MAGIC = ${value.inputMagic}\nexport const VOICE_OUTPUT_MAGIC = ${value.outputMagic}\nexport const VOICE_BINARY_HEADER_BYTES = ${value.binaryHeaderBytes}\nexport const VOICE_CLIENT_EVENTS = ${JSON.stringify(value.clientEvents)} as const\nexport const VOICE_SERVER_EVENTS = ${JSON.stringify(value.serverEvents)} as const\nexport type VoiceClientEventType = typeof VOICE_CLIENT_EVENTS[number]\nexport type VoiceServerEventType = typeof VOICE_SERVER_EVENTS[number]\n`
if (process.argv.includes('--check')) {
  if (await readFile(target, 'utf8') !== code) { console.error('语音协议生成文件不一致，请运行生成脚本'); process.exitCode = 1 }
} else await writeFile(target, code)
