"""Real macOS processes, sockets, lsof and ps; no application data is touched."""
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
SERVICE = '''import os,sys,time
from pathlib import Path
from http.server import BaseHTTPRequestHandler,HTTPServer
role=sys.argv[1]
Path('unhealthy').unlink(missing_ok=True)
port=int(os.environ['KNOWFLOW_BACKEND_PORT']) if role=='backend' else int(sys.argv[sys.argv.index('--port')+1])
if os.environ.get('FAIL_ROLE')==role:sys.exit(7)
if os.environ.get('HANG_ROLE')==role:time.sleep(30)
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  self.send_response(503 if Path('unhealthy').exists() else 401);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{"code":401}')
 def log_message(self,*args):pass
print('ACCEPTING_TRAFFIC',flush=True)
HTTPServer(('127.0.0.1',port),Handler).serve_forever()
'''

def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


class RealProcessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='knowflow-launcher-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        for d in ['backend/config', 'frontend/node_modules/vite/bin', 'bin', 'scripts']:
            (self.root / d).mkdir(parents=True)
        for name in ['knowflow-dev.sh', 'scripts/launcher_process.py']:
            shutil.copy(ROOT / name, self.root / name)
        backend = self.root / 'backend/com.knowflow.KnowFlowApplication.py'
        front = self.root / 'frontend/node_modules/vite/bin/vite.py'
        backend.write_text(SERVICE); front.write_text(SERVICE)
        for name, file, role in [('mvn', backend, 'backend'), ('npm', front, 'frontend')]:
            script = self.root / 'bin' / name
            script.write_text('#!/bin/bash\nexec python3 "' + str(file) + '" ' + role + ' "$@"\n')
            script.chmod(0o755)
        (self.root / 'backend/pom.xml').touch(); (self.root / 'frontend/package.json').write_text('{}')
        self.env = {**os.environ, 'KNOWFLOW_PROJECT_ROOT': str(self.root),
                    'KNOWFLOW_BACKEND_PORT': str(free_port()), 'KNOWFLOW_FRONTEND_PORT': str(free_port()),
                    'KNOWFLOW_START_TIMEOUT': '3', 'JAVA_HOME': str(self.root / 'unused'),
                    'MAVEN_HOME': str(self.root / 'unused'),
                    'PATH': str(self.root / 'bin') + ':' + os.environ['PATH']}
        # Test copy reserves random ports, never the user's real 8080/5173.
        launcher = self.root / 'knowflow-dev.sh'
        launcher.write_text(launcher.read_text().replace('PORT_B=8080; PORT_F=5173',
            'PORT_B=' + self.env['KNOWFLOW_BACKEND_PORT'] + '; PORT_F=' + self.env['KNOWFLOW_FRONTEND_PORT']))
        self.addCleanup(self.cleanup_services)

    def cleanup_services(self):
        for record in (self.root / '.dev-logs').glob('*.session.json'):
            data = json.loads(record.read_text())
            try:
                # Test cleanup owns these uniquely created sessions.
                os.killpg(data['pid'], signal.SIGTERM)
            except ProcessLookupError:
                pass

    def run_launcher(self, action='start'):
        r = subprocess.run(['/bin/bash', str(self.root / 'knowflow-dev.sh'), action],
                           env=self.env, text=True, capture_output=True, timeout=35)
        return r

    def assert_stopped(self, port):
        with socket.socket() as s:
            s.settimeout(.3)
            self.assertNotEqual(s.connect_ex(('127.0.0.1', int(port))), 0)

    def test_start_reclaims_conflict_then_repeat_restart_stop(self):
        port = self.env['KNOWFLOW_FRONTEND_PORT']
        foreign = subprocess.Popen(['python3', '-m', 'http.server', port, '--bind', '127.0.0.1'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.addCleanup(lambda: (foreign.terminate(), foreign.wait()) if foreign.poll() is None else None)
        for _ in range(100):
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', int(port))) == 0: break
            time.sleep(.03)
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        foreign.wait(timeout=2)
        ports = (self.root / '.dev-logs/ports').read_text().split()
        self.assertEqual(ports, [self.env['KNOWFLOW_BACKEND_PORT'], port])
        pids = [(self.root / ('.dev-logs/' + role + '.pid')).read_text() for role in ['backend', 'frontend']]
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(pids, [(self.root / ('.dev-logs/' + role + '.pid')).read_text() for role in ['backend', 'frontend']])
        r = self.run_launcher('restart'); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertNotEqual(pids[0], (self.root / '.dev-logs/backend.pid').read_text())
        r = self.run_launcher('stop'); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        for port in ports: self.assert_stopped(port)
        r = self.run_launcher('stop'); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_backend_conflict_is_reclaimed_without_touching_other_ports(self):
        port = self.env['KNOWFLOW_BACKEND_PORT']
        foreign = subprocess.Popen(['python3', '-m', 'http.server', port, '--bind', '127.0.0.1'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.addCleanup(lambda: (foreign.terminate(), foreign.wait()) if foreign.poll() is None else None)
        for _ in range(100):
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', int(port))) == 0: break
            time.sleep(.03)
        with socket.socket() as unrelated:
            unrelated.bind(('127.0.0.1', 0)); unrelated.listen()
            r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            foreign.wait(timeout=2)
            with socket.socket() as sock:
                self.assertEqual(sock.connect_ex(unrelated.getsockname()), 0)

    def test_term_resistant_conflict_is_killed_only_on_reserved_port(self):
        port = self.env['KNOWFLOW_FRONTEND_PORT']
        code = 'import signal,socket,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); s=socket.socket(); s.bind(("127.0.0.1",int(__import__("sys").argv[1]))); s.listen(); time.sleep(60)'
        proc = subprocess.Popen(['python3', '-c', code, port])
        self.addCleanup(lambda: (proc.kill(), proc.wait()) if proc.poll() is None else None)
        for _ in range(100):
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', int(port))) == 0: break
            time.sleep(.03)
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(proc.wait(timeout=2), -signal.SIGKILL)
        self.assertEqual((self.root / '.dev-logs/ports').read_text().split()[1], port)

    def test_saved_alternate_frontend_session_is_stopped_before_fixed_start(self):
        old_port = str(free_port())
        log = self.root / '.dev-logs'; log.mkdir()
        subprocess.run(['python3', str(self.root / 'scripts/launcher_process.py'), 'spawn',
                        str(self.root / 'frontend'), str(log / 'frontend.log'), str(log / 'frontend.session.json'),
                        'python3', str(self.root / 'frontend/node_modules/vite/bin/vite.py'),
                        'frontend', '--port', old_port], env=self.env, check=True, capture_output=True)
        old_pid = json.loads((log / 'frontend.session.json').read_text())['pid']
        (log / 'ports').write_text(self.env['KNOWFLOW_BACKEND_PORT'] + ' ' + old_port + '\n')
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assert_stopped(old_port)
        self.assertNotEqual(old_pid, int((log / 'frontend.pid').read_text()))
        self.assertEqual((log / 'ports').read_text().split()[1], self.env['KNOWFLOW_FRONTEND_PORT'])

    def test_frontend_failure_rolls_back_new_backend(self):
        self.env['FAIL_ROLE'] = 'frontend'
        r = self.run_launcher(); self.assertNotEqual(r.returncode, 0)
        self.assert_stopped(self.env['KNOWFLOW_BACKEND_PORT'])
        self.assertFalse((self.root / '.dev-logs/backend.pid').exists())
        self.env.pop('FAIL_ROLE')
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_timeout_cleans_unbound_session(self):
        self.env['HANG_ROLE'] = 'backend'
        r = self.run_launcher(); self.assertNotEqual(r.returncode, 0)
        self.assertFalse((self.root / '.dev-logs/backend.session.json').exists(), r.stdout + r.stderr)
        self.env.pop('HANG_ROLE')
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_unhealthy_owned_backend_is_gracefully_restarted(self):
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        old = (self.root / '.dev-logs/backend.pid').read_text()
        (self.root / 'backend/unhealthy').touch()
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertNotEqual(old, (self.root / '.dev-logs/backend.pid').read_text())

    def test_frontend_failure_preserves_preexisting_backend(self):
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        old = (self.root / '.dev-logs/backend.pid').read_text()
        subprocess.run(['python3', str(self.root / 'scripts/launcher_process.py'), 'stop',
                        str(self.root / '.dev-logs/frontend.session.json')], check=True)
        self.env['FAIL_ROLE'] = 'frontend'
        r = self.run_launcher(); self.assertNotEqual(r.returncode, 0)
        self.assertEqual(old, (self.root / '.dev-logs/backend.pid').read_text())
        with socket.socket() as s:
            self.assertEqual(s.connect_ex(('127.0.0.1', int(self.env['KNOWFLOW_BACKEND_PORT']))), 0)

    def test_reused_pid_record_never_signals_unrelated_process(self):
        log = self.root / '.dev-logs'; log.mkdir()
        proc = subprocess.Popen(['sleep', '30'], start_new_session=True)
        self.addCleanup(lambda: (proc.terminate(), proc.wait()) if proc.poll() is None else None)
        (log / 'backend.session.json').write_text(json.dumps({'pid': proc.pid, 'cwd': str(self.root / 'backend'), 'identity': 'old pid identity'}))
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIsNone(proc.poll())

    def test_lock_released_after_launcher_is_killed(self):
        self.env['HANG_ROLE'] = 'backend'
        proc = subprocess.Popen(['/bin/bash', str(self.root / 'knowflow-dev.sh'), 'start'], env=self.env,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.addCleanup(lambda: (proc.terminate(), proc.wait()) if proc.poll() is None else None)
        for _ in range(100):
            if (self.root / '.dev-logs/backend.pid').exists(): break
            time.sleep(.05)
        proc.kill(); proc.wait()
        self.env.pop('HANG_ROLE')
        r = self.run_launcher(); self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
