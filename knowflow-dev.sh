#!/usr/bin/env bash
# 固定 8080/5173；按用户要求释放固定端口的冲突进程，不自动换端口。
set -u
ROOT="$(cd "${KNOWFLOW_PROJECT_ROOT:-$(dirname "${BASH_SOURCE[0]}")}" && pwd -P)" || exit 1
HELPER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/launcher_process.py"
BACKEND="${ROOT}/backend"; FRONTEND="${ROOT}/frontend"; LOG_DIR="${ROOT}/.dev-logs"
mkdir -p "${LOG_DIR}" || exit 1
JAVA_HOME="${JAVA_HOME:-${HOME}/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home}"
MAVEN_HOME="${MAVEN_HOME:-${HOME}/Documents/Development/apache-maven-3.8.3}"
export JAVA_HOME
export PATH="${MAVEN_HOME}/bin:${JAVA_HOME}/bin:${PATH}"
# Finder 不继承交互 shell 的 nvm PATH。
if ! command -v npm >/dev/null; then
  for node_bin in "${HOME}"/.nvm/versions/node/*/bin; do
    [ -x "${node_bin}/npm" ] && export PATH="${node_bin}:${PATH}"
  done
fi
TIMEOUT="${KNOWFLOW_START_TIMEOUT:-180}"
case "${TIMEOUT}" in ''|*[!0-9]*) echo '启动超时设置必须为整数'; exit 1;; esac
SAVED_B=8080; SAVED_F=5173
if [ -f "${LOG_DIR}/ports" ]; then
  read -r SAVED_B SAVED_F < "${LOG_DIR}/ports"
fi
PORT_B=8080; PORT_F=5173
listeners() { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | sort -u; }
owned() {
  local folder cmd cwd
  folder="${BACKEND}"; [ "$2" = "${PORT_F}" ] && folder="${FRONTEND}"
  cwd=$(lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p')
  cmd=$(ps -p "$1" -o command= 2>/dev/null) || return 2
  [ -n "${cwd}" ] && [ -n "${cmd}" ] || return 2
  [ "${cwd}" = "${folder}" ] || return 1
  if [ "$2" = "${PORT_B}" ]; then
    case "${cmd}" in *com.knowflow.KnowFlowApplication*|*spring-boot-maven-plugin*|*spring-boot:run*) return 0;; esac
  else
    case "${cmd}" in *"${FRONTEND}/node_modules/vite/"*|*"node_modules/.bin/vite"*) return 0;; esac
  fi
  return 1
}
port_owned() {
  local pid result
  for pid in $(listeners "$1"); do
    owned "${pid}" "$1"; result=$?
    if [ "${result}" -ne 0 ]; then
      echo "端口 $1 的 PID ${pid} 无法核实为日常服务；保留该进程。" >&2
      return 1
    fi
  done
}
check_ports() {
  port_owned "${PORT_B}" && port_owned "${PORT_F}"
}
select_ports() {
  local port pid
  # 清理本启动器先前在其他端口启动的会话；不按旧记录改端口。
  if [ "${SAVED_F}" != "${PORT_F}" ] || [ "${SAVED_B}" != "${PORT_B}" ]; then stop_managed frontend || return 1; fi
  if [ "${SAVED_B}" != "${PORT_B}" ]; then stop_managed backend || return 1; fi
  for port in "${PORT_B}" "${PORT_F}"; do
    for pid in $(listeners "${port}"); do
      if owned "${pid}" "${port}"; then continue; fi
      echo "固定端口 ${port} 被 PID ${pid} 占用，正在停止占用进程…"
      python3 "${HELPER}" clear-port "${port}" "${pid}" || return 1
    done
    port_owned "${port}" || return 1
  done
}
probe() {
  local status
  status=$(curl -s --connect-timeout 2 --max-time 3 -o "${LOG_DIR}/probe-$1.json" -w '%{http_code}' "http://127.0.0.1:$1/api/auth/me") || return 1
  [ "${status}" = 401 ] && grep -Eq '"code"[[:space:]]*:[[:space:]]*401' "${LOG_DIR}/probe-$1.json"
}
wait_service() {
  local port="$1" pid="$2" label="$3" started=${SECONDS}
  while [ "$((SECONDS-started))" -lt "${TIMEOUT}" ]; do
    if [ "${port}" = "${PORT_B}" ]; then
      if port_owned "${port}" && grep -q 'ACCEPTING_TRAFFIC' "${LOG_DIR}/backend.log" && probe "${PORT_B}"; then return 0; fi
    elif port_owned "${port}" && probe "${PORT_F}"; then return 0
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "${label} 启动失败。日志：${LOG_DIR}/${label}.log"; return 1
    fi
    sleep 1
  done
  echo "${label} 未在 ${TIMEOUT} 秒内就绪。日志：${LOG_DIR}/${label}.log"; return 1
}
spawn() {
  local folder="$1" log="$2"; shift 2
  python3 "${HELPER}" spawn "${folder}" "${log}" "${log%.log}.session.json" "$@"
}
stop_managed() {
  python3 "${HELPER}" stop "${LOG_DIR}/$1.session.json"
}
failed() {
  echo "启动失败；检查 ${LOG_DIR}/backend.log 和 ${LOG_DIR}/frontend.log。"
  # Only sessions created by this attempt are rolled back. Reused services remain intact.
  [ "${NEW_F}" = 0 ] || { stop_managed frontend && rm -f "${LOG_DIR}/frontend.pid"; }
  [ "${NEW_B}" = 0 ] || { stop_managed backend && rm -f "${LOG_DIR}/backend.pid"; }
  NEW_F=0; NEW_B=0
  return 1
}
NEW_B=0; NEW_F=0
start() {
  local tool seed pid
  [ -d "${BACKEND}" ] && [ -d "${FRONTEND}" ] || { echo '项目目录不完整'; return 1; }
  for tool in java mvn npm lsof curl python3; do
    command -v "${tool}" >/dev/null || { echo "缺少运行工具：${tool}"; return 1; }
  done
  select_ports || return 1
  printf '%s %s\n' "${PORT_B}" "${PORT_F}" > "${LOG_DIR}/ports.tmp" && mv "${LOG_DIR}/ports.tmp" "${LOG_DIR}/ports" || return 1
  seed=$(python3 - "${BACKEND}/config/datasource.json" <<'PY'
import json,sys,os
p=os.environ.get('DB_CONFIG_FILE',sys.argv[1])
try:
 c=json.load(open(p)) if os.path.exists(p) else {}
 t=c.get('type',os.environ.get('DB_TYPE','h2'))
 url=c.get('h2',{}).get('url',os.environ.get('H2_URL','jdbc:h2:mem:knowledge'))
 print('true' if t=='h2' and url.startswith('jdbc:h2:mem:') else 'false')
except Exception:
 print('数据库配置无法读取，请检查配置文件。',file=sys.stderr);sys.exit(1)
PY
  ) || return 1
  if [ -n "$(listeners "${PORT_B}")" ]; then
    pid=$(listeners "${PORT_B}" | head -1)
    if ! probe "${PORT_B}" || { [ -f "${LOG_DIR}/backend.pid" ] && ! grep -q 'ACCEPTING_TRAFFIC' "${LOG_DIR}/backend.log" 2>/dev/null; }; then
      echo '本项目后端尚未就绪，等待初始化；超时后优雅重启。'
      wait_service "${PORT_B}" "${pid}" backend || { stop_service "${PORT_B}" backend || return 1; }
    fi
  else
    # No listener, but an earlier launch may still be compiling or stuck.
    stop_managed backend || return 1
    rm -f "${LOG_DIR}/backend.pid"
  fi
  if [ -n "$(listeners "${PORT_B}")" ]; then
    echo '后端已就绪，复用当前服务。'
  else
    [ ! -f "${LOG_DIR}/backend.log" ] || cp "${LOG_DIR}/backend.log" "${LOG_DIR}/backend.previous.log"
    echo '正在启动后端，等待数据库初始化完成…'
    pid=$(spawn "${BACKEND}" "${LOG_DIR}/backend.log" mvn org.springframework.boot:spring-boot-maven-plugin:3.2.5:run \
      -Dspring-boot.run.arguments="--server.port=${PORT_B} --server.address=127.0.0.1 --knowflow.seed.development-account.enabled=${seed} --logging.level.org.springframework.boot.availability.ApplicationAvailabilityBean=DEBUG") || return 1
    NEW_B=1
    echo "${pid}" > "${LOG_DIR}/backend.pid"
    wait_service "${PORT_B}" "${pid}" backend || { failed; return 1; }
  fi
  if [ -n "$(listeners "${PORT_F}")" ]; then
    if ! probe "${PORT_F}"; then
      echo '本项目前端代理未就绪，尝试优雅停止后重启。'
      stop_service "${PORT_F}" frontend || return 1
    fi
  else
    stop_managed frontend || return 1
    rm -f "${LOG_DIR}/frontend.pid"
  fi
  if [ -n "$(listeners "${PORT_F}")" ]; then
    echo '前端已就绪，复用当前服务。'
  else
    [ ! -f "${LOG_DIR}/frontend.log" ] || cp "${LOG_DIR}/frontend.log" "${LOG_DIR}/frontend.previous.log"
    pid=$(spawn "${FRONTEND}" "${LOG_DIR}/frontend.log" env SERVER__PORT="${PORT_B}" npm run dev -- --host 127.0.0.1 --port "${PORT_F}" --strictPort) || return 1
    NEW_F=1
    echo "${pid}" > "${LOG_DIR}/frontend.pid"
    wait_service "${PORT_F}" "${pid}" frontend || { failed; return 1; }
  fi
  printf '%s %s\n' "${PORT_B}" "${PORT_F}" > "${LOG_DIR}/ports.tmp" && mv "${LOG_DIR}/ports.tmp" "${LOG_DIR}/ports" || return 1
  NEW_B=0; NEW_F=0
  echo "前后端及代理均已就绪：http://localhost:${PORT_F}/ （后端 ${PORT_B}）"
}
stop_service() {
  local port="$1" label="$2" pid i
  port_owned "${port}" || return 1
  stop_managed "${label}" || return 1
  for pid in $(listeners "${port}"); do
    # Legacy / manually started service: validate cwd and command again before TERM.
    owned "${pid}" "${port}" || return 1
    kill -TERM "${pid}" 2>/dev/null || return 1
  done
  i=0
  while [ -n "$(listeners "${port}")" ] && [ "${i}" -lt 15 ]; do sleep 1; i=$((i+1)); done
  [ -z "$(listeners "${port}")" ] || { echo "端口 ${port} 尚未释放；不强杀。"; return 1; }
  rm -f "${LOG_DIR}/${label}.pid"
}
stop() {
  check_ports || return 1
  stop_service "${PORT_F}" frontend && stop_service "${PORT_B}" backend || return 1
  echo '本项目服务已停止。'
}
# Advisory lock is released by the OS even after a crash; never unlink its inode.
if [ "${KNOWFLOW_LAUNCHER_LOCKED:-}" != "${ROOT}" ]; then
  exec python3 "${HELPER}" lock "${LOG_DIR}/launcher.flock" "${BASH_SOURCE[0]}" "${ROOT}" "$@"
fi
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'result=$?; if [ "${NEW_F}" = 1 ] || [ "${NEW_B}" = 1 ]; then failed; fi; exit "${result}"' EXIT
case "${1:-start}" in
  start) start;;
  stop) stop;;
  restart) select_ports && stop && start;;
  status) check_ports && probe "${PORT_B}" && probe "${PORT_F}" && echo "前后端及代理均已就绪：http://localhost:${PORT_F}/ （后端 ${PORT_B}）";;
  *) echo "用法：$0 {start|stop|restart|status}"; exit 1;;
esac
