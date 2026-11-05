import test from 'node:test'
import assert from 'node:assert/strict'
import { mapperViolations } from './mapper-boundary.mjs'
const file='backend/src/main/java/com/knowflow/techenglish/mapper/TermMapper.java'
const source='package com.knowflow.techenglish.mapper; @Mapper public interface TermMapper extends BaseMapper<Term> {}'
const registry={legacy:[],owned:{'com.knowflow.techenglish.mapper.TermMapper':{entity:'Term',callers:[]}}}
test('owned module mapper passes',()=>assert.deepEqual(mapperViolations({[file]:source},registry),[]))
test('new global and misspelled mapper packages fail',()=>{
 for(const pkg of ['com.knowflow.mapper','com.nowflow.coding.mapper'])assert.ok(mapperViolations({[file]:source.replace('com.knowflow.techenglish.mapper',pkg)},registry).length)
})
test('unregistered or generic CommonMapper fails',()=>assert.ok(mapperViolations({[file]:source.replaceAll('TermMapper','CommonMapper')},registry).length))
test('duplicate entity mapper fails',()=>assert.ok(mapperViolations({[file]:source,[file.replace('TermMapper','OtherMapper')]:source.replaceAll('TermMapper','OtherMapper')},registry).length))
test('cross module direct mapper dependency fails for governed module',()=>assert.ok(mapperViolations({[file]:source,'backend/src/main/java/com/knowflow/chat/ChatService.java':'package com.knowflow.chat; import com.knowflow.techenglish.mapper.TermMapper; class ChatService { private TermMapper terms; void write(){terms.insert(null);} }'},registry).length))
test('legacy locations remain allowed without blessing new interfaces',()=>{
 const old='package com.knowflow.mapper; @Mapper interface OldMapper extends BaseMapper<Old> {}'
 assert.deepEqual(mapperViolations({'backend/src/main/java/com/knowflow/mapper/OldMapper.java':old},{legacy:['com.knowflow.mapper.OldMapper'],owned:{}}),[])
 assert.ok(mapperViolations({[file]:source.replace('BaseMapper<Term>','Other')},registry).length)
})

test('different entities mapped to the same table fail',()=>{
 const other=file.replace('TermMapper','OtherMapper')
 assert.ok(mapperViolations({[file]:'import com.knowflow.entity.Term; '+source,[other]:'import com.knowflow.entity.Other; '+source.replaceAll('TermMapper','OtherMapper').replace('BaseMapper<Term>','BaseMapper<Other>'),'Term.java':'package com.knowflow.entity; @TableName("term") class Term {}','Other.java':'package com.knowflow.entity; @TableName("term") class Other {}'},registry).some(x=>x.includes('duplicate')))
})
test('deleting a governed mapper fails',()=>assert.ok(mapperViolations({},registry).some(x=>x.includes('missing'))))
