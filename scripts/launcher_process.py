"""Detached sessions and identity checks; fixed-port conflicts are explicitly reclaimed."""
import fcntl
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def identity(pid):
    return subprocess.check_output(['ps', '-p', str(pid), '-o', 'lstart='], text=True).strip()


def cwd(pid):
    out = subprocess.check_output(['lsof', '-a', '-p', str(pid), '-d', 'cwd', '-Fn'], text=True)
    return next(line[1:] for line in out.splitlines() if line.startswith('n'))


def terminate(record):
    path = Path(record)
    if not path.exists():
        return 0
    data = json.loads(path.read_text())
    pid = data['pid']
    try:
        if identity(pid) != data['identity']:
            print('PID 已被复用；仅清除失效记录，保留该进程。', file=sys.stderr)
            path.unlink(missing_ok=True)
            return 0
        if os.getpgid(pid) != pid or cwd(pid) != data['cwd']:
            print('进程组或目录无法核实；保留进程与记录：' + str(path), file=sys.stderr)
            return 1
    except subprocess.CalledProcessError:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            path.unlink(missing_ok=True)
            return 0
        print('无法读取存活进程身份；保留进程与记录：' + str(path), file=sys.stderr)
        return 1
    except ProcessLookupError:
        path.unlink(missing_ok=True)
        return 0
    listing = subprocess.check_output(['ps', '-axo', 'pid=,pgid='], text=True)
    members = []
    for line in listing.splitlines():
        p, group = map(int, line.split())
        if group != pid:
            continue
        try:
            if cwd(p) != data['cwd']:
                print('进程组含无法确认的成员，保留进程。', file=sys.stderr)
                return 1
            members.append((p, identity(p)))
        except (subprocess.CalledProcessError, StopIteration):
            if not Path('/proc').exists():
                # macOS processes may disappear during inspection; skip only dead PIDs.
                try:
                    os.kill(p, 0)
                except ProcessLookupError:
                    continue
            return 1
    # Signal individually, rechecking PID identity and membership immediately before TERM.
    for p, stamp in reversed(members):
        try:
            if identity(p) == stamp and os.getpgid(p) == pid and cwd(p) == data['cwd']:
                os.kill(p, signal.SIGTERM)
        except (ProcessLookupError, subprocess.CalledProcessError):
            pass
    for _ in range(150):
        alive = False
        for p, stamp in members:
            try:
                state = subprocess.check_output(['ps', '-p', str(p), '-o', 'stat='], text=True).strip()
                if state and not state.startswith('Z') and identity(p) == stamp:
                    alive = True
            except subprocess.CalledProcessError:
                pass
        if not alive:
            path.unlink(missing_ok=True)
            return 0
        time.sleep(.1)
    print('进程未在 15 秒内退出；保留记录，请检查日志。不强制杀进程。', file=sys.stderr)
    return 1


def clear_port(port, pid):
    # Only a listener on the explicitly reserved port may be reclaimed.
    def listening():
        result = subprocess.run(['lsof', '-nP', '-a', '-p', str(pid),
                                 '-iTCP:' + str(port), '-sTCP:LISTEN', '-t'],
                                capture_output=True, text=True)
        return str(pid) in result.stdout.split()

    if not listening():
        return 0
    try:
        stamp = identity(pid)
        if not stamp:
            raise ValueError('无法核实占用进程身份')
        print('释放端口 ' + str(port) + '：' + subprocess.check_output(
            ['ps', '-p', str(pid), '-o', 'command='], text=True).strip(), flush=True)
        if not listening() or identity(pid) != stamp:
            return 1
        os.kill(pid, signal.SIGTERM)
        for _ in range(100):
            if not listening():
                return 0
            time.sleep(.1)
        # A TERM-resistant listener is killed only after identity and port rechecks.
        if identity(pid) != stamp or not listening():
            return 1
        os.kill(pid, signal.SIGKILL)
        for _ in range(50):
            if not listening():
                return 0
            time.sleep(.1)
    except ProcessLookupError:
        return 0
    except (subprocess.CalledProcessError, ValueError):
        print('无法核实占用进程身份；启动中止，不使用其他端口。', file=sys.stderr)
        return 1
    print('固定端口无法释放；启动中止，不使用其他端口。', file=sys.stderr)
    return 1


if __name__ == '__main__':
    try:
        if sys.argv[1] == 'lock':
            lock, script, root = sys.argv[2:5]
            fd = os.open(lock, os.O_CREAT | os.O_RDWR, 0o600)
            for attempt in range(21):
                try:
                    fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except BlockingIOError:
                    if attempt == 20:
                        print('已有启动器正在操作，请稍候。')
                        sys.exit(1)
                    time.sleep(.1)
            os.set_inheritable(fd, True)
            os.execvpe('/bin/bash', ['bash', script, *sys.argv[5:]],
                      {**os.environ, 'KNOWFLOW_LAUNCHER_LOCKED': root})
        if sys.argv[1] == 'clear-port':
            sys.exit(clear_port(int(sys.argv[2]), int(sys.argv[3])))
        if sys.argv[1] == 'stop':
            sys.exit(terminate(sys.argv[2]))
        folder, log, record = sys.argv[2:5]
        with open(log, 'w') as output:
            proc = subprocess.Popen(sys.argv[5:], cwd=folder, stdin=subprocess.DEVNULL,
                                    stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            data = {'pid': proc.pid, 'cwd': folder, 'identity': identity(proc.pid)}
            path = Path(record)
            temp = path.with_suffix('.tmp')
            temp.write_text(json.dumps(data))
            temp.replace(path)
        except Exception:
            # The child is ours by construction, even when recording failed.
            proc.terminate()
            proc.wait(timeout=15)
            raise
        print(proc.pid)
    except (OSError, ValueError, subprocess.CalledProcessError, StopIteration) as exc:
        print('进程管理失败：' + str(exc), file=sys.stderr)
        sys.exit(1)
