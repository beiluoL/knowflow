#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1
echo '=== KnowFlow 启动器 ==='
./knowflow-dev.sh "${1:-start}"
result=$?
if [ "$result" -eq 0 ]; then
  echo '项目已在后台运行。可以关闭此窗口；前端固定 5173，后端固定 8080；重复双击会复用已就绪服务，端口冲突会先清理。'
else
  echo '项目启动失败。请查看上方诊断与 .dev-logs/backend.log、.dev-logs/frontend.log。'
fi
if [ -t 0 ]; then
  echo '按 Enter 关闭窗口…'; read -r
fi
exit "$result"
