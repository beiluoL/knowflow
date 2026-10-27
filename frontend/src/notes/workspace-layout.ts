import type {InjectionKey,Ref} from 'vue'
export type NotesLayoutMode='normal'|'fullscreen'|'immersive'
export const NOTES_LAYOUT_KEY:InjectionKey<Ref<NotesLayoutMode>>=Symbol('notes-layout')
