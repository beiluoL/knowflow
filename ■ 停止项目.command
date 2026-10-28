#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1
echo '=== KnowFlow 停止器 ==='
./knowflow-dev.sh stop
result=$?
if [ -t 0 ]; then echo '按 Enter 关闭窗口…'; read -r; fi
exit "$result"
