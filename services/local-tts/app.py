"""KnowFlow 本地语音合成服务。

两种真实引擎，能力如实上报：
- `sherpa`：sherpa-onnx VITS 模型（需已下载模型目录，质量更好；本机当前未就位）
- `say`：macOS 系统语音（离线、零下载，实测 Tingting 中文约 0.58s 合成 2 句）

边界：只做文本→PCM16；不访问数据库、不保存文本与音频、不做业务状态管理；
只监听 127.0.0.1，所有请求必须带服务端注入的 Bearer token；
按句合成并逐句回传——模型与系统语音都是整句推理，不伪装成逐字流式。
"""
import base64
import hmac
import json
import os
import re
import resource
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_TEXT_CHARS = 2000
MAX_BODY = 64 * 1024
SENTENCE = re.compile(r"[^。！？!?；;\n]+[。！？!?；;\n]?")


def peak_rss():
    value = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return value if sys.platform == "darwin" else value * 1024


def split_sentences(text, limit=60):
    parts = [p.strip() for p in SENTENCE.findall(text) if p.strip()]
    merged, buffer = [], ""
    for part in parts:
        if len(buffer) + len(part) <= limit:
            buffer += part
        else:
            if buffer:
                merged.append(buffer)
            buffer = part
    if buffer:
        merged.append(buffer)
    return merged or [text]


class SayEngine:
    """macOS 系统语音：完全离线，使用本机已安装语音。"""

    name = "say"
    sample_rate = 24000

    def __init__(self, voice, rate=190):
        if not shutil.which("say"):
            raise ValueError("macOS say is required")
        self.voice = voice or "Tingting"
        self.rate = rate

    def load(self):
        return None

    def synthesize(self, text, speed):
        rate = max(80, min(400, int(self.rate * speed)))
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as handle:
            subprocess.run(
                ["say", "-v", self.voice, "-r", str(rate), "-o", handle.name,
                 "--data-format=LEI16@24000", text],
                check=True, timeout=90, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            with wave.open(handle.name, "rb") as stream:
                if stream.getsampwidth() != 2 or stream.getnchannels() != 1:
                    raise ValueError("Unexpected system audio format")
                return stream.readframes(stream.getnframes()), stream.getframerate()


class SherpaEngine:
    """sherpa-onnx VITS；模型目录不存在时不可用。"""

    name = "sherpa"
    sample_rate = 0

    def __init__(self, directory):
        self.directory = directory
        self.tts = None

    @staticmethod
    def available(directory):
        return bool(directory) and (directory / "model.onnx").is_file() and (directory / "tokens.txt").is_file()

    def load(self):
        import sherpa_onnx

        model = self.directory / "model.onnx"
        tokens = self.directory / "tokens.txt"
        lexicon = self.directory / "lexicon.txt"
        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=str(model),
                    lexicon=str(lexicon) if lexicon.is_file() else "",
                    tokens=str(tokens),
                    dict_dir=str(self.directory / "dict") if (self.directory / "dict").is_dir() else "",
                ),
                num_threads=2,
                provider="cpu",
            ),
            max_num_sentences=1,
        )
        self.tts = sherpa_onnx.OfflineTts(config)
        self.sample_rate = int(self.tts.sample_rate)

    def synthesize(self, text, speed):
        import numpy as np

        if self.tts is None:
            self.load()
        output = self.tts.generate(text, sid=0, speed=speed)
        samples = np.asarray(output.samples, dtype=np.float32)
        pcm = np.clip(samples * 32767.0, -32768, 32767).astype("<i2").tobytes()
        return pcm, int(output.sample_rate)


class Runtime:
    def __init__(self, engine, token, enabled=True):
        if not token or not token.isascii() or any(c.isspace() for c in token):
            raise ValueError("A server-side bearer token is required")
        self.engine = engine
        self.token = token
        self.enabled = enabled
        self.lock = threading.Lock()
        self.sample_rate = engine.sample_rate
        self.metrics = {
            "successes": 0,
            "failures": 0,
            "model_load_seconds": None,
            "last_total_seconds": None,
            "last_first_chunk_seconds": None,
            "last_text_chars": None,
            "peak_rss_bytes": peak_rss(),
        }

    def load(self):
        started = time.perf_counter()
        self.engine.load()
        self.sample_rate = self.engine.sample_rate
        self.metrics["model_load_seconds"] = time.perf_counter() - started

    def synthesize(self, text, speed):
        return self.engine.synthesize(text, speed)


def make_server(runtime, host="127.0.0.1", port=8010):
    if host != "127.0.0.1":
        raise ValueError("Only 127.0.0.1 is supported")

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def setup(self):
            super().setup()
            self.connection.settimeout(60)

        def log_message(self, *args):
            pass  # 不记录文本、音频、token 或上游错误

        def authorized(self):
            return hmac.compare_digest(
                self.headers.get("Authorization", "").encode(),
                ("Bearer " + runtime.token).encode(),
            )

        def reply(self, status, body):
            data = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if not self.authorized():
                return self.reply(401, {"error": "Unauthorized"})
            if self.path != "/health":
                return self.reply(404, {"error": "Not found"})
            runtime.metrics["peak_rss_bytes"] = peak_rss()
            self.reply(200, {
                "enabled": runtime.enabled,
                "loaded": runtime.engine is not None and runtime.sample_rate > 0,
                "sampleRate": runtime.sample_rate,
                "engine": runtime.engine.name,
                "streaming": "sentence",
                "metrics": dict(runtime.metrics),
            })

        def do_POST(self):
            if not self.authorized():
                return self.reply(401, {"error": "Unauthorized"})
            if self.path != "/v1/audio/speech":
                return self.reply(404, {"error": "Not found"})
            if not runtime.enabled:
                return self.reply(503, {"error": "TTS disabled"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > MAX_BODY:
                    return self.reply(400, {"error": "Invalid body"})
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                text = (payload.get("text") or "").strip()
                speed = float(payload.get("speed") or 1.0)
                if not text or len(text) > MAX_TEXT_CHARS:
                    return self.reply(400, {"error": "Invalid text"})
                if not 0.5 <= speed <= 2.0:
                    return self.reply(400, {"error": "Invalid speed"})
            except (ValueError, TypeError):
                return self.reply(400, {"error": "Invalid request"})
            if not runtime.lock.acquire(blocking=False):
                return self.reply(429, {"error": "TTS busy"})
            started = time.perf_counter()
            try:
                sentences = split_sentences(text)
                self.send_response(200)
                self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
                self.send_header("Transfer-Encoding", "chunked")
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                first = None
                for index, sentence in enumerate(sentences):
                    pcm, rate = runtime.synthesize(sentence, speed)
                    if first is None:
                        first = time.perf_counter()
                    line = json.dumps({
                        "sequence": index,
                        "sampleRate": rate,
                        "audio": base64.b64encode(pcm).decode(),
                        "last": index == len(sentences) - 1,
                    }).encode() + b"\n"
                    self.wfile.write(("%X\r\n" % len(line)).encode() + line + b"\r\n")
                    self.wfile.flush()
                self.wfile.write(b"0\r\n\r\n")
                runtime.metrics["successes"] += 1
                runtime.metrics.update(
                    last_total_seconds=time.perf_counter() - started,
                    last_first_chunk_seconds=(first - started) if first else None,
                    last_text_chars=len(text),
                    peak_rss_bytes=peak_rss(),
                )
            except (BrokenPipeError, ConnectionResetError):
                runtime.metrics["failures"] += 1
            except Exception:
                runtime.metrics["failures"] += 1
                try:
                    self.reply(503, {"error": "TTS unavailable"})
                except OSError:
                    pass
            finally:
                runtime.lock.release()

    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    return server


def select_engine():
    requested = os.environ.get("KNOWFLOW_LOCAL_TTS_ENGINE", "auto").lower()
    directory = Path(os.environ.get("KNOWFLOW_LOCAL_TTS_MODEL_DIR", ""))
    voice = os.environ.get("KNOWFLOW_LOCAL_TTS_VOICE", "Tingting")
    if requested in ("auto", "sherpa") and SherpaEngine.available(directory):
        return SherpaEngine(directory)
    if requested == "sherpa":
        raise ValueError("sherpa engine requested but model directory is incomplete")
    if requested in ("auto", "say"):
        return SayEngine(voice)
    raise ValueError("Unknown KNOWFLOW_LOCAL_TTS_ENGINE")


if __name__ == "__main__":
    enabled = os.environ.get("KNOWFLOW_LOCAL_TTS_ENABLED", "false").lower() == "true"
    runtime = Runtime(select_engine(), os.environ.get("KNOWFLOW_LOCAL_TTS_TOKEN", ""), enabled=enabled)
    if enabled:
        runtime.load()
    make_server(runtime, port=int(os.environ.get("KNOWFLOW_LOCAL_TTS_PORT", "8010"))).serve_forever()
