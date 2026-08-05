#!/usr/bin/env bash
# 将本机 node 二进制复制为 Tauri 侧车所需的命名格式。
# Tauri externalBin 要求文件名带目标三元组，例如：
#   server-x86_64-apple-darwin   (Intel Mac)
#   server-aarch64-apple-darwin  (Apple Silicon Mac)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN_DIR="$ROOT/binaries"
mkdir -p "$BIN_DIR"

NODE_BIN="$(command -v node)"
if [ -z "$NODE_BIN" ]; then
  echo "未找到 node，请先安装 Node.js (https://nodejs.org)" >&2
  exit 1
fi

# 判定架构三元组
UNAME_M="$(uname -m)"
if [ "$UNAME_M" = "arm64" ]; then
  TRIPLE="aarch64-apple-darwin"
else
  TRIPLE="x86_64-apple-darwin"
fi

DEST="$BIN_DIR/server-$TRIPLE"
cp "$NODE_BIN" "$DEST"
chmod +x "$DEST"
echo "已生成侧车二进制: $DEST"
echo "（tauri build 时会自动按当前平台选取对应文件）"
