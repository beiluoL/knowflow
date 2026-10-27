/** 普通和章节 Markdown 笔记共用服务端主库；旧本地数据仅供显式迁移。 */
import { apiGet, apiPost, apiPut, apiDelete, apiUpload } from "./request";
export interface NotePayload {
  title: string;
  content: string;
  tags?: string;
  sourceTitle?: string;
  status?: "SAVED" | "DRAFT";
  revision?: number;
  chapterId?: number;
  pathId?: number;
  folderId?: number|null;
  favorite?: boolean;
  sourceDocId?: number|null;
}
export interface ServerNote extends NotePayload {
  id: number;
  userId: number;
  revision: number;
  deleted: number;
  sourceType: "GENERAL" | "CHAPTER";
  contentFormat: "MARKDOWN";
  createTime: string;
  updateTime: string;
}
export interface NotePage {
  records: ServerNote[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}
export interface NoteVersion extends NotePayload {
  id: number;
  revision: number;
  reason: string;
  createTime: string;
}
function id(value: number) {
  if (!Number.isSafeInteger(value) || value < 1) throw Error("笔记ID不正确");
  return value;
}
export const notesApi = {
  list: (
    params: {
      pageNum?: number;
      pageSize?: number;
      keyword?: string;
      trash?: boolean;
      view?:NoteView;folderId?:number;tagIds?:number[];sort?:NoteSort;
    } = {},
  ) => apiGet<NotePage>("/notes", params),
  stats:()=>apiGet<NoteStats>('/notes/stats'),
  folders:()=>apiGet<NoteFolder[]>('/notes/folders'),
  createFolder:(payload:{name:string;parentId?:number|null})=>apiPost<NoteFolder>('/notes/folders',payload),
  updateFolder:(folderId:number,payload:{name:string;parentId:number|null;revision:number})=>apiPut<NoteFolder>(`/notes/folders/${id(folderId)}`,payload),
  deleteFolder:(folderId:number,revision:number)=>apiDelete<void>(`/notes/folders/${id(folderId)}`,{revision}),
  tags:()=>apiGet<NoteTag[]>('/notes/tags'),
  createTag:(name:string)=>apiPost<NoteTag>('/notes/tags',{name}),
  renameTag:(tagId:number,name:string)=>apiPut<void>(`/notes/tags/${id(tagId)}`,{name}),
  deleteTag:(tagId:number)=>apiDelete<void>(`/notes/tags/${id(tagId)}`),
  relations:(noteId:number)=>apiGet<NoteRelations>(`/notes/${id(noteId)}/relations`),
  addRelation:(noteId:number,type:'DOC'|'NOTE',targetId:number)=>apiPost<NoteRelations>(`/notes/${id(noteId)}/relations`,{type,targetId}),
  removeRelation:(noteId:number,relationId:number)=>apiDelete<void>(`/notes/${id(noteId)}/relations/${id(relationId)}`),
  uploadImage:(file:File)=>apiUpload<string>('/notes/images',file,{}),
  detail: (noteId: number) => apiGet<ServerNote>(`/notes/${id(noteId)}`),
  create: (payload: NotePayload) => apiPost<ServerNote>("/notes", payload),
  update: (noteId: number, payload: NotePayload) =>
    apiPut<ServerNote>(`/notes/${id(noteId)}`, payload),
  delete: (noteId: number, revision: number) =>
    apiDelete<ServerNote>(`/notes/${id(noteId)}`, { revision }),
  restore: (noteId: number, revision: number) =>
    apiPost<ServerNote>(`/notes/${id(noteId)}/restore`, { revision }),
  versions: (noteId: number) =>
    apiGet<NoteVersion[]>(`/notes/${id(noteId)}/versions`),
  restoreVersion: (noteId: number, version: number, revision: number) =>
    apiPost<ServerNote>(
      `/notes/${id(noteId)}/versions/${id(version)}/restore`,
      { revision },
    ),
  importLegacy: (payload: NotePayload & { legacyKey: string }) =>
    apiPost<ServerNote>("/notes/import", payload),
};

export type NoteView='ALL'|'RECENT'|'FAVORITES'|'DRAFT'|'TRASH';
export type NoteSort='UPDATED'|'CREATED'|'TITLE';
export interface NoteFolder{id:number;name:string;parentId:number|null;revision:number;count:number}
export interface NoteTag{id:number;name:string;count:number}
export interface NoteStats{total:number;recent:number;favorites:number;drafts:number;trash:number}
export interface NoteRelation{id:number;type:'DOC'|'NOTE';targetId:number;title:string;accessible:boolean;href:string}
export interface NoteRelations{outgoing:NoteRelation[];backlinks:NoteRelation[]}
