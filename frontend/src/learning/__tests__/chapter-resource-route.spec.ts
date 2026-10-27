import { describe, it, expect } from 'vitest'
import { resolveChapterResourceRoute, readChapterContext, learningEntryViewKey } from '../action-contract'
import { resolveActionRoute } from '../route'
describe('chapter resource context', () => {
 it('preserves a real chapter across document, knowledge and editor URLs', () => {
  expect(resolveChapterResourceRoute('document',38,24)).toBe('/doc/38?chapterId=24')
  expect(resolveChapterResourceRoute('knowledge',1,24)).toBe('/learning/knowledge-mastery/1?chapterId=24')
  expect(resolveChapterResourceRoute('code',48,24)).toBe('/learning/code-practice/48?chapterId=24')
  expect(resolveChapterResourceRoute('chapter',24)).toBe('/learning/chapter/24')
  expect(readChapterContext({chapterId:'24'})).toBe(24)
 })
 it('remounts flashcard sessions when their actual filter changes',()=>{expect(learningEntryViewKey({path:'/learning/flashcards',query:{chapterId:'24'}})).not.toBe(learningEntryViewKey({path:'/learning/flashcards',query:{chapterId:'25'}}));expect(learningEntryViewKey({path:'/doc/38',query:{chapterId:'24'}})).toBe('/doc/38')})
 it('rejects malformed IDs and arbitrary resource paths', () => {
  for(const value of ['-1','01','1.5','9007199254740992',['24','25']]) expect(readChapterContext({chapterId:value})).toBeNull()
  expect(resolveChapterResourceRoute('code',-1,24)).toBe('/learning/center')
  for(const route of ['https://x/doc/1','javascript:alert(1)','/doc/01','/doc/1/extra','/doc/9007199254740992']) expect(resolveActionRoute({type:'EVIDENCE_PRACTICE',route,title:'test',reason:'user navigation'})).toBe('/learning/center')
 })
})
