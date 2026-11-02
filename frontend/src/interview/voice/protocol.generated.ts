// 由 scripts/generate-interview-voice.mjs 生成，请修改后端 voice-v1.json 后重新生成。
export const VOICE_PROTOCOL_VERSION = 1 as const
export const VOICE_INPUT_MAGIC = 1262900785
export const VOICE_OUTPUT_MAGIC = 1262900273
export const VOICE_BINARY_HEADER_BYTES = 12
export const VOICE_CLIENT_EVENTS = ["session.start","audio.start","audio.stop","audio.frame","audio.commit","control.pause","control.resume","control.interrupt","control.end","heartbeat","client.ack"] as const
export const VOICE_SERVER_EVENTS = ["session.ready","session.state","audio.accepted","asr.partial","asr.final","speech.started","speech.ended","turn.accepted","llm.delta","llm.completed","tts.started","tts.chunk","tts.completed","control.ack","session.paused","session.resumed","session.completed","error","heartbeat.ack"] as const
export type VoiceClientEventType = typeof VOICE_CLIENT_EVENTS[number]
export type VoiceServerEventType = typeof VOICE_SERVER_EVENTS[number]
