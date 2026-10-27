export interface FlashcardImportRequest {
 format: 'csv' | 'tsv'
 content: string
 source: string
 mapping: Record<string, number>
}
export interface FlashcardImportPreview {
 headers: string[]
 warnings: string[]
 counts: Record<'NEW'|'DUPLICATE'|'CONFLICT'|'INVALID', number>
 cardCount: number
 token: string
 rows: { line:number; status:'NEW'|'DUPLICATE'|'CONFLICT'|'INVALID'; message:string; cards:number; note:{type:string; front:string;back:string} }[]
}
export interface FlashcardImportOutcome { insertedNotes:number;insertedCards:number;skipped:number;noteIds:number[] }
export interface FlashcardContentExport { format:'csv'|'tsv'; content:string; notes:number; warning:string }
