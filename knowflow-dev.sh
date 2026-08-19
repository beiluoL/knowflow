#!/usr/bin/env bash
# ==========================================================================
#  KnowFlow 开发环境一键启停脚本
#  用法:  ./knowflow-dev.sh {start|stop|restart|status}
#  - start   启动后端(Spring Boot:8080) + 前端(Vite:5173)，若已运行则先重启
#  - stop    停止前后端
#  - restart 先停止再启动
#  - status  查看运行状态
#  日常使用：直接双击 『▶ 启动项目.command』 / 『■ 停止项目.command』
# ==========================================================================

ROOT="/Users/beiluo/Documents/alProject/qoderProject/knowflow"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
LOG_DIR="$ROOT/.dev-logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

# ---- JDK 17 (Corretto) + Maven 3.8.3 ----
JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
MAVEN_HOME="/Users/beiluo/Documents/Development/apache-maven-3.8.3"
export JAVA_HOME
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

PORT_B=8080   # 后端
PORT_F=5173   # 前端

mkdir -p "$LOG_DIR"

# 返回占用某端口的 PID（没有则空）
pid_on_port() { lsof -ti tcp:"$1" 2>/dev/null; }

# 释放某端口（先优雅 kill，1 秒后还占用再强杀）
kill_port() {
  local pids
  pids=$(pid_on_port "$1")
  if [ -n "$pids" ]; then
    echo "  • 释放端口 $1 的进程: $pids"
    kill $pids 2>/dev/null
    sleep 1
    pids=$(pid_on_port "$1")
    if [ -n "$pids" ]; then
      echo "  • 端口 $1 仍未释放，强制结束: $pids"
      kill -9 $pids 2>/dev/null
      sleep 1
    fi
  fi
}

start_backend() {
  cd "$BACKEND" || exit 1
  nohup mvn org.springframework.boot:spring-boot-maven-plugin:3.2.5:run \
    -Dspring-boot.run.arguments="--server.port=$PORT_B" \
    > "$BACKEND_LOG" 2>&1 &
}

start_frontend() {
  cd "$FRONTEND" || exit 1
  nohup env SERVER__PORT="$PORT_B" npm run dev -- --host \
    > "$FRONTEND_LOG" 2>&1 &
}

do_start() {
  echo "=========================================="
  echo "   启动 KnowFlow 开发环境"
  echo "=========================================="
  # 若已运行则先清理，实现“双击即重启”
  kill_port "$PORT_B"
  kill_port "$PORT_F"
  sleep 1

  echo "  • 启动后端 Spring Boot  → 日志: $BACKEND_LOG"
  start_backend
  echo "  • 启动前端 Vite         → 日志: $FRONTEND_LOG"
  start_frontend

  echo ""
  echo "  等待前端就绪..."
  local i
  for i in $(seq 1 60); do
    if [ -n "$(pid_on_port "$PORT_F")" ]; then
      echo "  ✅ 前端已就绪: http://localhost:$PORT_F/"
      break
    fi
    sleep 1
  done

  echo ""
  echo "  🌐 前端地址: http://localhost:$PORT_F/"
  echo "  🔧 后端地址: http://localhost:$PORT_B/   (首次启动较慢，约 20~40 秒)"
  echo ""
  echo "  📋 查看后端日志: tail -f $BACKEND_LOG"
  echo "  📋 查看前端日志: tail -f $FRONTEND_LOG"
  echo "  ⏹  停止项目: 双击『■ 停止项目.command』"
  echo "  🔄 重启项目: 再次双击『▶ 启动项目.command』即可"
}

do_stop() {
  echo "=========================================="
  echo "   停止 KnowFlow 开发环境"
  echo "=========================================="
  kill_port "$PORT_B"
  kill_port "$PORT_F"
  echo "  ✅ 已停止"
}

do_status() {
  local pb pf
  pb=$(pid_on_port "$PORT_B")
  pf=$(pid_on_port "$PORT_F")
  echo "  后端 (8080): ${pb:-未运行}"
  echo "  前端 (5173): ${pf:-未运行}"
}

case "${1:-start}" in
  start)   do_start ;;
  stop)    do_stop ;;
  restart) do_stop; sleep 2; do_start ;;
  status)  do_status ;;
  *) echo "用法: $0 {start|stop|restart|status}"; exit 1 ;;
esac
