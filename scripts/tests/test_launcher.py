"""Exercise the launcher as a subprocess using isolated service/port boundaries.
A regression that reports success without a backend, restarts healthy services,
or switches to alternate ports must fail these tests. No real service is stopped.
"""
import fcntl, json, os, pathlib, shutil, signal, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
FAKE=r'''#!/usr/bin/env python3
import os,sys,json,pathlib,time
name=pathlib.Path(sys.argv[0]).name;args=sys.argv[1:];root=pathlib.Path(os.environ['KNOWFLOW_PROJECT_ROOT']);scenario=os.environ['FIXTURE_SCENARIO'];trace=root/'trace'
def record(s):
 with trace.open('a') as f:f.write(s+'\n')
if name=='sleep':time.sleep(.02)
elif name=='java':print('openjdk version "17.0.14"',file=sys.stderr)
elif name=='mvn':
 if any(x in args for x in ['--version','-v','-version']):print('Apache Maven 3.8.3')
 else:
  record('mvn '+' '.join(args))
  if scenario=='backend_failure':print('APPLICATION FAILED TO START',flush=True);sys.exit(1)
  (root/'backend-ready').touch();print('Started KnowFlowApplication' if scenario=='not_ready' else 'Application availability state ReadinessState changed to ACCEPTING_TRAFFIC',flush=True);time.sleep(30)
elif name=='npm':
 if '--version' in args:print('10.0.0')
 else:
  record('npm '+' '.join(args)+' backend='+os.environ.get('SERVER__PORT',''))
  if scenario=='frontend_failure':print('Port 5173 is already in use',flush=True);sys.exit(1)
  (root/'frontend-ready').touch();print('VITE ready',flush=True);time.sleep(30)
elif name=='lsof':
 if '-d' in args:
  pid=args[args.index('-p')+1];folder='backend' if pid=='99999998' else 'frontend'
  if scenario=='foreign':print('n/tmp/unrelated-service')
  else:print('n'+str(root/folder))
 else:
  port=next(a.split(':')[1] for a in args if a.startswith('-iTCP:'))
  if scenario=='preview' and port=='5173':print('99999997');sys.exit(0)
  if port not in ['8080','5173'] and not (root/'frontend-ready').exists():sys.exit(0)
  ready=(scenario=='healthy' or (root/('backend-ready'if port=='8080'else'frontend-ready')).exists() or (scenario=='foreign' and port=='8080'))
  if ready:print('99999998'if port=='8080'else'99999999')
elif name=='ps':
 pid=args[args.index('-p')+1]
 if scenario=='ps_denied':sys.exit(1)
 if pid=='99999997':print('node '+str(root/'scripts/tests/flashcard_visual_preview.cjs'));sys.exit(0)
 print('java com.knowflow.KnowFlowApplication'if pid=='99999998'else str(root/'frontend/node_modules/vite/bin/vite.js'))
elif name=='curl':
 url=next((a for a in args if a.startswith('http')), '')
 front=':8080/' not in url;ready=scenario=='healthy' or (root/('frontend-ready'if front else'backend-ready')).exists()
 if not ready:sys.exit(7)
 body='{"code":401}'if '/api/auth/me' in url else '<html>KnowFlow</html>'
 if '-o'in args:pathlib.Path(args[args.index('-o')+1]).write_text(body)
 elif '--output'in args:pathlib.Path(args[args.index('--output')+1]).write_text(body)
 print('401'if '/api/auth/me'in url else'200',end='')
'''
class LauncherTests(unittest.TestCase):
 def fixture(self,scenario):
  tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup);root=pathlib.Path(tmp.name).resolve()
  for d in ['backend/config','frontend','fake/bin']: (root/d).mkdir(parents=True,exist_ok=True)
  (root/'backend/pom.xml').write_text('<project/>');(root/'frontend/package.json').write_text('{}')
  script=(ROOT/'knowflow-dev.sh').read_text().replace('ROOT="/Users/beiluo/Documents/alProject/qoderProject/knowflow"','ROOT="'+str(root)+'"').replace('MAVEN_HOME="/Users/beiluo/Documents/Development/apache-maven-3.8.3"','MAVEN_HOME="'+str(root/'fake')+'"').replace('JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"','JAVA_HOME="'+str(root/'fake')+'"')
  (root/'scripts').mkdir();shutil.copy(ROOT/'scripts/launcher_process.py',root/'scripts/launcher_process.py')
  (root/'knowflow-dev.sh').write_text(script);(root/'knowflow-dev.sh').chmod(0o755)
  for tool in ['mvn','npm','java','lsof','ps','curl','sleep']:
   p=root/'fake/bin'/tool;p.write_text(FAKE);p.chmod(0o755)
  env={**os.environ,'KNOWFLOW_PROJECT_ROOT':str(root),'KNOWFLOW_START_TIMEOUT':'2','FIXTURE_SCENARIO':scenario,'JAVA_HOME':str(root/'fake'),'MAVEN_HOME':str(root/'fake'),'PATH':str(root/'fake/bin')+':'+os.environ['PATH'],'TERM':'dumb'}
  def cleanup():
   for f in (root/'.dev-logs').glob('*.pid') if (root/'.dev-logs').exists() else []:
    try:
     pid=int(f.read_text());
     if 0<pid<99999998:os.kill(pid,signal.SIGTERM)
    except (ValueError,ProcessLookupError):pass
  self.addCleanup(cleanup)
  return root,env
 def run_launcher(self,root,env,*args):return subprocess.run(['bash',str(root/'knowflow-dev.sh'),*args],env=env,capture_output=True,text=True,timeout=12)
 def test_backend_failure_is_not_success_and_frontend_is_not_launched(self):
  root,env=self.fixture('backend_failure');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertNotIn('npm run', (root/'trace').read_text())
 def test_start_reuses_healthy_owned_services_without_restarting_them(self):
  root,env=self.fixture('healthy');r=self.run_launcher(root,env,'start')
  self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertFalse((root/'trace').exists())
 def test_unreleasable_foreign_port_fails_without_starting_any_service(self):
  root,env=self.fixture('foreign');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertFalse((root/'trace').exists())
 def test_persistent_database_disables_memory_seed_and_frontend_uses_strict_port(self):
  root,env=self.fixture('start');(root/'backend/config/datasource.json').write_text(json.dumps({'type':'h2','h2':{'url':'jdbc:h2:file:/existing/knowledge','initMode':'never'}}))
  r=self.run_launcher(root,env,'start');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  trace=(root/'trace').read_text();self.assertIn('development-account.enabled=false',trace);self.assertIn('--port 5173 --strictPort',trace)
 def test_command_preserves_failure_and_does_not_print_success(self):
  root,env=self.fixture('backend_failure');(root/'knowflow-dev.sh').write_text('#!/bin/bash\necho failed\nexit 7\n')
  wrapper=(ROOT/'▶ 启动项目.command').read_text().replace('cd /Users/beiluo/Documents/alProject/qoderProject/knowflow','cd "'+str(root)+'"')
  (root/'▶ 启动项目.command').write_text(wrapper)
  r=subprocess.run(['bash',str(root/'▶ 启动项目.command')],env=env,capture_output=True,text=True,timeout=5)
  self.assertEqual(r.returncode,7);self.assertNotIn('项目已在后台运行',r.stdout)
 def test_http_listener_before_startup_runners_is_not_ready(self):
  root,env=self.fixture('not_ready');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertNotIn('npm run',(root/'trace').read_text())
 def test_repeated_start_cannot_accept_incomplete_backend(self):
  root,env=self.fixture('not_ready');first=self.run_launcher(root,env,'start');second=self.run_launcher(root,env,'start')
  self.assertNotEqual(first.returncode,0);self.assertNotEqual(second.returncode,0)
  self.assertNotIn('npm run',(root/'trace').read_text())
 def test_frontend_failure_is_not_reported_as_success(self):
  root,env=self.fixture('frontend_failure');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertNotIn('均已就绪',r.stdout)
 def test_stop_refuses_foreign_listener(self):
  root,env=self.fixture('foreign');r=self.run_launcher(root,env,'stop')
  self.assertNotEqual(r.returncode,0);self.assertFalse((root/'trace').exists())
 def test_simultaneous_launcher_is_refused(self):
  root,env=self.fixture('start');lock=root/'.dev-logs/launcher.flock';lock.parent.mkdir(parents=True)
  with lock.open('w') as held:
   fcntl.flock(held,fcntl.LOCK_EX|fcntl.LOCK_NB);r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertFalse((root/'trace').exists())
 def test_unstoppable_preview_fails_without_falling_back(self):
  root,env=self.fixture('preview');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0)
  self.assertNotIn('http://localhost:5174/',r.stdout)
 def test_unknown_process_reports_inspection_failure_not_foreign_certainty(self):
  root,env=self.fixture('ps_denied');(root/'backend-ready').touch()
  r=self.run_launcher(root,env,'start');self.assertNotEqual(r.returncode,0)
  self.assertIn('无法核实',r.stdout+r.stderr)
 def test_environment_cannot_redirect_fixed_ports(self):
  root,env=self.fixture('start');env['KNOWFLOW_FRONTEND_PORT']='5199';env['KNOWFLOW_BACKEND_PORT']='8099'
  r=self.run_launcher(root,env,'start');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  trace=(root/'trace').read_text();self.assertIn('--server.port=8080',trace);self.assertIn('--port 5173 --strictPort',trace)
 def test_stale_alternate_record_does_not_redirect_start(self):
  root,env=self.fixture('healthy');log=root/'.dev-logs';log.mkdir();(log/'ports').write_text('8080 5174\n')
  r=self.run_launcher(root,env,'start');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  self.assertIn('http://localhost:5173/',r.stdout);self.assertEqual((log/'ports').read_text(),'8080 5173\n')
 def test_foreign_backend_failure_does_not_offer_alternate_ports(self):
  root,env=self.fixture('foreign');r=self.run_launcher(root,env,'start')
  self.assertNotEqual(r.returncode,0);self.assertNotIn('KNOWFLOW_BACKEND_PORT=',r.stdout)
if __name__=='__main__':unittest.main(verbosity=2)
