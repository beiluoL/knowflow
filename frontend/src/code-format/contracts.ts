export interface FormatResult { formatted: string; start: number; end: number }
export const MAX_FORMAT_LENGTH = 100_000
export function supportsFormatting(language: string) { return language === 'javascript' || language === 'typescript' }
