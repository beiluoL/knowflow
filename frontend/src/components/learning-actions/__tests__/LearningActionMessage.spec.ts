import { describe, it, expect } from 'vitest'
import { parseLearningActionMessage } from '@/api/learning-actions'
describe('persisted learning action messages',()=>{
 it('restores plan references and real task IDs after history reload',()=>{expect(parseLearningActionMessage(JSON.stringify({kind:'KNOWFLOW_LEARNING_ACTION',text:'未保存草案',planId:'a25a8491-e7b6-4c58-9a09-fbd90f676724',tasks:[{id:4,source:'TASK',title:'Java',status:0}],choices:[]}))).toMatchObject({text:'未保存草案',tasks:[{id:4}]})})
 it('normal chat, malformed JSON and injected routes are not structured action cards',()=>{for(const text of ['普通回答','{bad',JSON.stringify({kind:'KNOWFLOW_LEARNING_ACTION',text:'fake',planId:'javascript:alert(1)',tasks:[],choices:[]}),JSON.stringify({kind:'KNOWFLOW_LEARNING_ACTION',text:'fake',tasks:[{id:-1,source:'FOREIGN',title:'bad'}],choices:[]})])expect(parseLearningActionMessage(text)).toBeNull()})
})
