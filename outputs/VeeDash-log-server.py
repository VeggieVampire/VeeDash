from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote
import json
import os
import re
import shutil
import socket
import time


BASE = Path(__file__).resolve().parent
LOG_DIR = BASE / "VeeDash-logs"
LEGACY_LOG_FILE = BASE / "VeeDash-live-log.txt"
LOG_FILE = LOG_DIR / "VeeDash-live-log.txt"
MESSAGE_FILE = Path(__file__).with_name("VeeDash-message.txt")
CONFIG_FILE = Path(__file__).with_name("VeeDash-config.json")
ASSETS_DIR = Path(__file__).with_name("VeeDash-assets")
LAST_CONTACT = BASE / "VeeDash-last-contact.txt"
AWAY_SECONDS = 45


DEFAULT_CONFIG = {
    "showLog": True,
    "showChat": True,
    "autoReconnect": True,
    "autoDim": True,
    "nightBrightness": 0.40,
    "nightExtraDim": 0.22,
    "backgroundColor": "#000000",
    "accentColor": "#1fb6ff",
    "gaugeFillColor": "#05080c",
    "gaugeAlpha": 0.73,
    "logAlpha": 0.93,
    "chatAlpha": 0.86,
    "toolbarAlpha": 0.80,
    "configAlpha": 0.93,
    "backgroundDimAlpha": 0.80,
    "gauges": [
        {"key": "rpm", "x": 0.18, "y": 0.34, "size": 0.28, "visible": True},
        {"key": "speed", "x": 0.50, "y": 0.34, "size": 0.28, "visible": True},
        {"key": "coolant", "x": 0.82, "y": 0.34, "size": 0.24, "visible": True},
        {"key": "volts", "x": 0.22, "y": 0.72, "size": 0.22, "visible": True},
        {"key": "load", "x": 0.50, "y": 0.72, "size": 0.22, "visible": True},
        {"key": "throttle", "x": 0.78, "y": 0.72, "size": 0.22, "visible": True},
    ],
}


def ensure_config():
    if not CONFIG_FILE.exists():
        DEFAULT_CONFIG["updatedAt"] = time.strftime("%Y-%m-%d %H:%M:%S")
        CONFIG_FILE.write_text(json.dumps(DEFAULT_CONFIG, indent=2), encoding="utf-8")


def file_mtime(path):
    try:
        return int(path.stat().st_mtime)
    except Exception:
        return 0


def ensure_log_dir():
    LOG_DIR.mkdir(parents=True, exist_ok=True)


def safe_stamp(value=None):
    raw = value or datetime.now().isoformat(timespec="seconds")
    return re.sub(r"[^0-9A-Za-z_-]+", "-", raw).strip("-")


def rotate_live_log(reason, ip):
    ensure_log_dir()
    for source in (LOG_FILE, LEGACY_LOG_FILE):
        try:
            if not source.exists() or source.stat().st_size == 0:
                continue
            archive = LOG_DIR / f"VeeDash-log-{safe_stamp()}-{safe_stamp(ip)}-{reason}.txt"
            shutil.copy2(source, archive)
            source.write_text("", encoding="utf-8")
            return archive
        except Exception:
            continue
    LOG_FILE.touch(exist_ok=True)
    return None


def note_contact(ip, route):
    if ip in ("127.0.0.1", "::1"):
        return
    previous_contact = file_mtime(LAST_CONTACT)
    if previous_contact == 0 or time.time() - previous_contact > AWAY_SECONDS:
        archive = rotate_live_log(route, ip)
        if archive:
            MESSAGE_FILE.write_text(f"Car connected to PC.\nPrevious logs saved:\n{archive.name}", encoding="utf-8")
    LAST_CONTACT.write_text(f"{datetime.now().isoformat(timespec='seconds')} from={ip} {route}", encoding="utf-8")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(length).decode("utf-8", "replace")
        fields = parse_qs(body)
        seq = fields.get("seq", [""])[0]
        line = fields.get("line", [""])[0]
        now = datetime.now().isoformat(timespec="seconds")
        note_contact(self.client_address[0], "log")
        ensure_log_dir()
        with LOG_FILE.open("a", encoding="utf-8") as handle:
            handle.write(f"{now} seq={seq} from={self.client_address[0]} {line}\n")
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        route = self.path.split("?", 1)[0]
        if route == "/hello":
            payload = {
                "app": "VeeDash",
                "role": "pc-server",
                "port": self.server.server_address[1],
                "host": local_ip(),
                "updatedAt": config_mtime(),
            }
            self.send_json(payload)
            return
        if route == "/message":
            if not MESSAGE_FILE.exists():
                MESSAGE_FILE.write_text(
                    "I can see the dash logs here. Tap Scan, pick VEEPEAK, then tap AutoTry.",
                    encoding="utf-8",
                )
            text = MESSAGE_FILE.read_text(encoding="utf-8", errors="replace")
            data = text.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if route == "/config":
            ensure_config()
            text = CONFIG_FILE.read_text(encoding="utf-8", errors="replace")
            data = text.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if route == "/asset/background":
            path = background_asset_path()
            if not path or not path.exists():
                self.send_response(404)
                self.end_headers()
                return
            data = path.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", content_type(path))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-VeeDash-Asset", path.name)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if route.startswith("/asset/"):
            name = Path(unquote(route.rsplit("/", 1)[-1])).name
            path = (ASSETS_DIR / name).resolve()
            root = ASSETS_DIR.resolve()
            if not (root == path.parent or root in path.parents) or not path.exists():
                self.send_response(404)
                self.end_headers()
                return
            data = path.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", content_type(path))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-VeeDash-Asset", path.name)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"VeeDash receiver is running. POST /log, GET /message, GET /config\n")

    def send_json(self, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        return


def local_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def config_mtime():
    try:
        return int(CONFIG_FILE.stat().st_mtime)
    except Exception:
        return 0


def background_asset_path():
    ensure_config()
    try:
        data = json.loads(CONFIG_FILE.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        data = {}
    name = data.get("backgroundAsset", "")
    if name:
        candidate = (ASSETS_DIR / Path(name).name).resolve()
        if ASSETS_DIR.resolve() in candidate.parents or candidate.parent == ASSETS_DIR.resolve():
            return candidate
    raw = data.get("backgroundImage", "")
    if raw:
        return Path(raw)
    return None


def content_type(path):
    suffix = path.suffix.lower()
    if suffix == ".gif":
        return "image/gif"
    if suffix in (".jpg", ".jpeg"):
        return "image/jpeg"
    if suffix == ".webp":
        return "image/webp"
    if suffix == ".bmp":
        return "image/bmp"
    return "image/png"


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8766), Handler)
    ensure_config()
    print(f"VeeDash log receiver listening on http://0.0.0.0:8766/log")
    print(f"VeeDash chat message at http://0.0.0.0:8766/message")
    print(f"VeeDash GUI config at http://0.0.0.0:8766/config")
    print(f"VeeDash session discovery at http://{local_ip()}:8766/hello")
    print(f"VeeDash staged background at http://{local_ip()}:8766/asset/background")
    print(f"Writing {LOG_FILE}")
    server.serve_forever()
