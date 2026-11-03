#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then printf "%s\n" "Run: /opt/homebrew/bin/python3.11 -m venv .venv && .venv/bin/pip install sherpa-onnx numpy" >&2; exit 1; fi
exec .venv/bin/python app.py
