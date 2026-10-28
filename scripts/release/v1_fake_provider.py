"""Loopback-only deterministic OpenAI-compatible provider for synthetic V1 smoke tests.
No external requests, credentials, or model inference. Not a quality evaluation.
"""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
NOTE = {'title': '合成Java课程', 'content': '# Java与Spring\n\n## JVM\nJVM提供GC。\n\n## IoC\nSpring管理Bean。', 'chapters': [{'title': 'JVM', 'knowledgePoints': ['垃圾回收']}, {'title': 'IoC', 'knowledgePoints': ['依赖注入']}]}
CARDS = {'flashcards': [{'front': f'{x}是什么', 'back': y} for x,y in [('GC','垃圾回收'),('JVM','Java虚拟机'),('IoC','控制反转'),('Bean','Spring管理对象'),('事务','保证原子性')]]}
QUIZ = [{'type': 'TRUE_FALSE', 'content': x, 'options': [], 'answer': a, 'explanation': '合成资料原文说明'} for x,a in [('JVM提供GC','true'),('IoC管理Bean','true'),('事务没有原子性','false')]]
class Handler(BaseHTTPRequestHandler):
 def do_POST(self):
  if self.path != '/v1/chat/completions':
   self.send_error(404); return
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  prompt=json.dumps(data.get('messages', []), ensure_ascii=False)
  content=json.dumps(NOTE if 'SOURCE_NOTE_V1' in prompt else CARDS if 'SOURCE_CARDS_V1' in prompt else QUIZ, ensure_ascii=False)
  if 'MICRO_LEARN_V1' in prompt:
   content=json.dumps({'analogy':'像清理闲置物品','concept':'JVM提供GC','sourceQuote':'JVM提供GC。'},ensure_ascii=False)
  if 'ERROR_ANNOTATION_V1' in prompt:
   payload=json.loads(data['messages'][-1]['content'])
   content=json.dumps({'labels':['UNKNOWN'],'findings':[{'label':'UNKNOWN','answerQuote':payload['answerQuoteCandidates'][0],'basisQuote':payload['basisQuoteCandidates'][0],'issue':'选择与题版依据冲突','missingKnowledge':'垃圾回收','practice':'对照原文完成单题补练'}],'uncertainty':'仅选项无法推定真实认知原因','confidence':'LOW'},ensure_ascii=False)
  if data.get('stream'):
   self.send_response(200); self.send_header('Content-Type','text/event-stream'); self.end_headers()
   for value in [{'choices':[{'delta':{'content':'合成导师：JVM提供GC。你能用自己的话解释垃圾回收吗？'},'index':0}]}, '[DONE]']:
    self.wfile.write(('data: '+(value if isinstance(value,str) else json.dumps(value,ensure_ascii=False))+'\n\n').encode()); self.wfile.flush()
  else:
   body=json.dumps({'id':'synthetic-v1','object':'chat.completion','model':'deterministic-local','choices':[{'message':{'role':'assistant','content':content},'finish_reason':'stop','index':0}],'usage':{'prompt_tokens':1,'completion_tokens':1,'total_tokens':2}},ensure_ascii=False).encode()
   self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
if __name__ == '__main__':
 HTTPServer(('127.0.0.1',19091),Handler).serve_forever()
