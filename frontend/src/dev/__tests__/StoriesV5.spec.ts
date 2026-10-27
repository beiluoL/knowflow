// @vitest-environment happy-dom
import {describe,it,expect} from 'vitest'
import {entries} from '../registry'
import {stories,validateStories,resolveStory,storyDefaults} from '../stories'
describe('V5 stories',()=>{
 it('validates component identities and real props',()=>{expect(validateStories(entries,stories)).toEqual([]);expect(validateStories(entries,[{...stories[0]!,componentId:'missing'}])).not.toEqual([]);expect(validateStories(entries,[{...stories[0]!,initialProps:{nonexistent:true}}])).not.toEqual([])})
 it('resolves case-insensitive URL and rejects an unrelated story',()=>{expect(resolveStory(entries,stories,'button','loading').entry?.name).toBe('Button');expect(resolveStory(entries,stories,'button','loading').story?.storyId).toBe('loading');expect(resolveStory(entries,stories,'input','loading').story?.componentId).toBe('Input')})
 it('never shares mutable story defaults',()=>{const e=entries.find(e=>e.name==='Button')!,s=stories.find(s=>s.componentId===e.id&&s.storyId==='loading')!;const a=storyDefaults(e,s);a.loading=false;expect(storyDefaults(e,s).loading).toBe(true)})
})
