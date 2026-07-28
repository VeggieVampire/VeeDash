import json
import math
import random
import re
import shutil
import socket
import subprocess
import threading
import time
import tkinter as tk
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from tkinter import colorchooser, filedialog, ttk
from urllib.parse import parse_qs, unquote

try:
    from PIL import Image, ImageDraw, ImageSequence, ImageTk
except Exception:
    Image = ImageDraw = ImageSequence = ImageTk = None


BASE = Path(__file__).resolve().parent
CONFIG = BASE / "VeeDash-config.json"
MESSAGE = BASE / "VeeDash-message.txt"
ASSETS = BASE / "VeeDash-assets"
LOG_DIR = BASE / "VeeDash-logs"
LEGACY_LOG_FILE = BASE / "VeeDash-live-log.txt"
LOG_FILE = LOG_DIR / "VeeDash-live-log.txt"
LAST_CLIENT = BASE / "VeeDash-last-client.txt"
LAST_CONTACT = BASE / "VeeDash-last-contact.txt"
LAST_CONFIG_SERVED = BASE / "VeeDash-last-config-served.txt"
LAST_WELCOME = BASE / "VeeDash-last-welcome.txt"
LAST_COMMAND_RUN = BASE / "VeeDash-last-command-run.txt"
COMMAND_OUTPUT = BASE / "VeeDash-command-output.txt"
SERVER_PORT = 8766
AWAY_SECONDS = 45

WELCOME_MESSAGES = [
    "Hello VeeDash, welcome back.\nConnected to the PC editor.\nReady to send the staged dashboard.",
    "Welcome back, VeeDash.\nPC editor is online.\nYour staged dashboard is ready.",
    "Hey VeeDash, connection restored.\nThe editor sees you.\nReady for updates.",
    "VeeDash is back online.\nPC editor connected.\nStanding by with the latest dash.",
    "Hello from the PC editor.\nDash connection is live.\nUpdates are ready.",
    "Good to see you, VeeDash.\nThe PC link is active.\nStaged config is waiting.",
    "VeeDash checked in.\nEditor connection is good.\nReady to pull changes.",
    "Dash is online again.\nPC editor is listening.\nThe newest layout is staged.",
    "Connection made.\nWelcome back to the editor.\nDashboard update is ready.",
    "VeeDash link restored.\nPC editor is ready.\nPull when you are set.",
    "Hello dash.\nThe PC editor found you.\nConfig and assets are ready.",
    "Car dash is back.\nEditor server is connected.\nReady for the next pull.",
    "Welcome online.\nVeeDash can reach the PC.\nStaged dashboard is waiting.",
    "Dash contact received.\nPC editor is active.\nReady to send config.",
    "VeeDash is awake.\nEditor server is here.\nLatest dashboard is ready.",
    "Hello again, VeeDash.\nLocal editor connected.\nReady for layout sync.",
    "PC editor handshake complete.\nDash is online.\nUpdates are standing by.",
    "VeeDash has returned.\nThe editor is ready.\nPull the staged dash anytime.",
    "Connected to PC editor.\nWelcome back.\nDashboard files are ready.",
    "Dash online signal received.\nPC editor is good.\nReady for config sync.",
    "VeeDash connection live.\nEditor is serving.\nNewest dashboard is ready.",
    "Welcome back to the PC.\nDash link is healthy.\nUpdates are staged.",
    "Hello car dash.\nEditor server sees you.\nReady to deliver changes.",
    "VeeDash is connected.\nPC editor is prepared.\nDash config is waiting.",
    "Online again.\nThe editor is ready.\nStaged layout can be pulled.",
    "Dash is reachable.\nPC editor is running.\nNew config is available.",
    "VeeDash hello received.\nEditor connection is live.\nReady for updates.",
    "PC editor is connected.\nDash is back online.\nConfig sync is ready.",
    "Welcome back to VeeDash.\nLocal editor link restored.\nDashboard is staged.",
    "Dash connected to editor.\nEverything is ready.\nPull the latest when needed.",
    "Hello, dashboard.\nPC editor is awake.\nStaged files are ready.",
    "VeeDash has checked in.\nThe editor is online.\nReady to send the dash.",
    "Connection restored.\nEditor server has the latest.\nPull when ready.",
    "Dash link is back.\nPC editor is standing by.\nNewest setup is waiting.",
    "VeeDash came online.\nThe editor noticed.\nReady for sync.",
    "Hello from the editor.\nCar dash is connected.\nStaged config is ready.",
    "The dash is back.\nPC editor is connected.\nReady with the newest layout.",
    "VeeDash reached the PC.\nEditor server is live.\nUpdates are ready.",
    "Welcome back online.\nDashboard can pull now.\nPC editor is ready.",
    "Dash contact confirmed.\nLocal editor is ready.\nConfig is staged.",
    "VeeDash is present.\nPC server connection is live.\nReady for the dash pull.",
    "Hello again from PC.\nDash connection is good.\nLatest files are waiting.",
    "VeeDash online.\nEditor ready.\nDashboard update staged.",
    "The PC editor sees the dash.\nConnection is active.\nReady to sync.",
    "Dashboard link restored.\nEditor is serving files.\nLatest config is ready.",
    "Car radio checked in.\nVeeDash is connected.\nPC editor is ready.",
    "Welcome back, dash.\nEditor server is up.\nChanges are waiting.",
    "VeeDash reconnected.\nThe PC editor is prepared.\nPull the newest config.",
    "Hello from your editor.\nDash is online.\nReady for staged updates.",
    "Online signal received.\nVeeDash can sync now.\nPC editor is ready.",
    "Dash came back to the PC.\nEditor link is healthy.\nReady to send changes.",
    "VeeDash is talking to the PC.\nServer is ready.\nDashboard config is staged.",
    "The editor has contact.\nDash is live.\nUpdates can be pulled.",
    "Welcome back to the local server.\nVeeDash is connected.\nReady for config.",
    "Dash sync path is open.\nPC editor is connected.\nLatest layout is waiting.",
    "VeeDash returned online.\nEditor server is standing by.\nPull when ready.",
    "Hello VeeDash.\nThe PC editor is here.\nNewest staged dash is ready.",
    "Dash connection accepted.\nEditor is running.\nFiles are ready.",
    "VeeDash is back in range.\nPC editor connected.\nReady for dashboard sync.",
    "Connection is back.\nHello from the editor.\nReady with staged changes.",
    "VeeDash checked back in.\nPC server is serving.\nConfig is ready.",
    "Dash online again.\nEditor link is live.\nLatest dashboard is queued.",
    "Welcome, VeeDash.\nPC editor connection restored.\nReady to pull.",
    "The car dash is online.\nEditor server is active.\nDashboard files are ready.",
    "VeeDash contact received.\nPC editor is ready.\nStaged dash is waiting.",
    "Hello, connected dash.\nThe editor has your config ready.\nPull when ready.",
    "PC editor online with VeeDash.\nConnection looks good.\nUpdates are staged.",
    "Dash link confirmed.\nEditor is ready.\nNewest config is available.",
    "VeeDash is connected again.\nLocal PC editor is live.\nReady for sync.",
    "Welcome back to the garage link.\nDash is connected.\nEditor is ready.",
    "The editor noticed VeeDash.\nConnection restored.\nDashboard is waiting.",
    "Dash can see the PC now.\nServer is ready.\nStaged update is available.",
    "VeeDash online check passed.\nEditor is connected.\nReady to send layout.",
    "Hello from the local editor.\nDash came online.\nLatest config is staged.",
    "Dash has rejoined the PC.\nEditor server is ready.\nPull the new setup.",
    "VeeDash connection found.\nPC editor is ready.\nDashboard update waiting.",
    "Welcome back to the editor.\nDash connection is live.\nConfig is staged.",
    "VeeDash is reachable again.\nPC server is serving.\nReady for changes.",
    "Dash is connected to the editor.\nEverything is staged.\nReady when you are.",
    "Hello VeeDash, link is up.\nPC editor is ready.\nPull the dashboard anytime.",
    "VeeDash came online cleanly.\nThe editor is connected.\nUpdates are ready.",
    "PC editor contact made.\nDash is back.\nReady to sync the layout.",
    "Dashboard is online.\nEditor server sees it.\nNewest files are ready.",
    "VeeDash sync window is open.\nPC editor is ready.\nConfig can be pulled.",
    "Welcome back to the dash editor.\nConnection restored.\nLatest setup is ready.",
    "Dash hello received.\nPC editor responded.\nStaged dashboard is ready.",
    "VeeDash is live on the network.\nEditor is ready.\nUpdates are waiting.",
    "Car dash connected.\nLocal editor is online.\nReady to serve config.",
    "Hello, VeeDash connection.\nThe PC editor is standing by.\nPull the staged dash.",
    "Dash found the PC editor.\nConnection is good.\nReady for dashboard changes.",
    "VeeDash is online with the PC.\nServer is ready.\nNewest dash is waiting.",
    "Welcome back, connected dash.\nEditor server is live.\nConfig sync is ready.",
    "The local editor sees VeeDash.\nDash is online.\nStaged update is ready.",
    "VeeDash returned to the network.\nPC editor connected.\nReady to send files.",
    "Dash connection restored to PC.\nEditor is ready.\nPull the latest dash.",
    "Hello from VeeDash editor.\nYour dash is online.\nStaged changes are ready.",
    "PC editor has the dash online.\nConnection confirmed.\nReady for config pull.",
    "VeeDash checked in again.\nThe editor is ready.\nDashboard update is waiting.",
    "Dash is awake and connected.\nPC editor is ready.\nLatest layout is staged.",
    "Welcome back to the dashboard link.\nVeeDash is connected.\nReady for updates.",
]

GAUGE_KEYS = ["rpm", "speed", "coolant", "volts", "load", "throttle"]
PID_CATALOG = [
    ("0101", "Monitor status"),
    ("0103", "Fuel system status"),
    ("load", "Engine load - 0104"),
    ("coolant", "Coolant temp - 0105"),
    ("0106", "Short-term fuel trim bank 1"),
    ("0107", "Long-term fuel trim bank 1"),
    ("010B", "Intake manifold pressure"),
    ("rpm", "RPM - 010C"),
    ("speed", "Vehicle speed - 010D"),
    ("010E", "Timing advance"),
    ("010F", "Intake air temp"),
    ("throttle", "Throttle position - 0111"),
    ("0113", "Oxygen sensors present"),
    ("0115", "O2 sensor 2 data"),
    ("011C", "OBD standards / protocol type"),
    ("011F", "Run time since engine start"),
    ("0120", "Supported PIDs 21-40"),
    ("volts", "Control module voltage - 0142"),
]
GAUGE_LABELS = {
    "rpm": "RPM",
    "speed": "MPH",
    "coolant": "Coolant",
    "volts": "Volts",
    "load": "Load",
    "throttle": "Throttle",
    "0101": "Monitor",
    "0103": "Fuel Sys",
    "0106": "STFT B1",
    "0107": "LTFT B1",
    "010B": "MAP",
    "010E": "Timing",
    "010F": "IAT",
    "0113": "O2 Present",
    "0115": "O2 S2",
    "011C": "OBD Std",
    "011F": "Run Time",
    "0120": "PIDs 21-40",
}
AUTO_TINT_PRESETS = {
    "rpm": {"tint": True, "grow": False, "valueMin": 0, "valueMax": 6500, "scaleMax": 1.25, "midAt": 3500, "highAt": 5500, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"},
    "coolant": {"tint": True, "grow": False, "valueMin": 60, "valueMax": 115, "scaleMax": 1.20, "midAt": 92, "highAt": 105, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"},
    "volts": {"tint": True, "grow": False, "valueMin": 11.5, "valueMax": 15.0, "scaleMax": 1.15, "midAt": 12.4, "highAt": 15.0, "lowColor": "#ff3b30", "midColor": "#1fb6ff", "highColor": "#ffd166"},
    "load": {"tint": True, "grow": False, "valueMin": 0, "valueMax": 100, "scaleMax": 1.25, "midAt": 65, "highAt": 90, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"},
    "throttle": {"tint": True, "grow": False, "valueMin": 0, "valueMax": 100, "scaleMax": 1.25, "midAt": 50, "highAt": 85, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"},
}
SAMPLE = {
    "rpm": 820,
    "speed": 0,
    "coolant": 72,
    "volts": 14.2,
    "load": 31,
    "throttle": 14,
    "0101": 128,
    "0103": 2,
    "0106": -1.6,
    "0107": 0.8,
    "010B": 38,
    "010E": 8,
    "010F": 34,
    "0113": 2,
    "0115": 1.2,
    "011C": 6,
    "011F": 530,
    "0120": 190,
}

DEFAULT = {
    "dashClientIp": "",
    "showLog": True,
    "showChat": True,
    "chatPopupSeconds": 6.5,
    "autoReconnect": True,
    "autoDim": True,
    "runCommandOnConnect": False,
    "onlineCommand": "python --version",
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
    "backgroundImage": "",
    "backgroundAsset": "",
    "gauges": [
        {"key": "rpm", "x": 0.18, "y": 0.34, "size": 0.28, "visible": True, "mode": "both", "layer": 20, "imageAsset": "", "reactive": AUTO_TINT_PRESETS["rpm"]},
        {"key": "speed", "x": 0.50, "y": 0.34, "size": 0.28, "visible": True, "mode": "number", "layer": 21, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.20, "valueMin": 0, "valueMax": 120, "tint": False, "midAt": 45, "highAt": 80, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
        {"key": "coolant", "x": 0.82, "y": 0.34, "size": 0.24, "visible": True, "mode": "both", "layer": 22, "imageAsset": "", "reactive": AUTO_TINT_PRESETS["coolant"]},
        {"key": "volts", "x": 0.22, "y": 0.72, "size": 0.22, "visible": True, "mode": "graph", "layer": 23, "imageAsset": "", "reactive": AUTO_TINT_PRESETS["volts"]},
        {"key": "load", "x": 0.50, "y": 0.72, "size": 0.22, "visible": True, "mode": "both", "layer": 24, "imageAsset": "", "reactive": AUTO_TINT_PRESETS["load"]},
        {"key": "throttle", "x": 0.78, "y": 0.72, "size": 0.22, "visible": True, "mode": "both", "layer": 25, "imageAsset": "", "reactive": AUTO_TINT_PRESETS["throttle"]},
    ],
    "overlays": [
        {"key": "chat", "type": "chat", "x": 0.80, "y": 0.83, "w": 0.28, "h": 0.18, "visible": True, "layer": 80},
        {"key": "log", "type": "log", "x": 0.30, "y": 0.30, "w": 0.52, "h": 0.36, "visible": True, "layer": 70},
        {"key": "clock", "type": "clock", "x": 0.82, "y": 0.12, "w": 0.24, "h": 0.12, "visible": True, "layer": 60, "mode": "time"},
        {"key": "date", "type": "date", "x": 0.82, "y": 0.23, "w": 0.24, "h": 0.10, "visible": True, "layer": 59, "mode": "yyyy_mm_dd"},
    ],
}


def deep_default():
    return json.loads(json.dumps(DEFAULT))


def normalize_pid_static(value):
    pid = (value or "").strip().lower().replace(" ", "")
    aliases = {
        "0104": "load",
        "0105": "coolant",
        "010c": "rpm",
        "010d": "speed",
        "0111": "throttle",
        "0142": "volts",
    }
    if pid in aliases:
        return aliases[pid]
    if pid in GAUGE_KEYS or pid in {key for key, _label in PID_CATALOG}:
        return pid
    compact = re.sub(r"[^0-9a-fA-F]", "", pid).upper()
    if len(compact) == 2:
        compact = "01" + compact
    if re.fullmatch(r"01[0-9A-F]{2}", compact):
        return compact
    return "rpm"


def load_config():
    data = deep_default()
    if CONFIG.exists():
        try:
            saved = json.loads(CONFIG.read_text(encoding="utf-8"))
            data.update(saved)
        except Exception:
            pass
    normalize(data)
    save_config(data)
    return data


def normalize(data):
    for key, value in DEFAULT.items():
        if key not in ("gauges", "overlays"):
            data.setdefault(key, value)
    if not data.get("backgroundAsset") and data.get("backgroundImage"):
        data["backgroundAsset"] = Path(data["backgroundImage"]).name
    source_gauges = data.get("gauges") if "gauges" in data else DEFAULT["gauges"]
    data["gauges"] = []
    for index, source in enumerate(source_gauges):
        pid = source.get("pid", source.get("key", DEFAULT["gauges"][0]["key"]))
        pid = normalize_pid_static(pid)
        default_g = next((item for item in DEFAULT["gauges"] if item["key"] == pid), DEFAULT["gauges"][0])
        g = dict(default_g)
        g.update(source)
        g.setdefault("key", f"{pid}_{index + 1}")
        g.setdefault("pid", pid)
        g.setdefault("label", GAUGE_LABELS.get(pid, pid.upper()))
        g.setdefault("mode", "number")
        g.setdefault("layer", default_g["layer"])
        g.setdefault("barThickness", 0.20)
        g.setdefault("showBorder", True)
        g.setdefault("imageAsset", "")
        reactive = dict(default_g.get("reactive", {}))
        reactive.update(g.get("reactive", {}))
        g["reactive"] = reactive
        data["gauges"].append(g)
    source_overlays = data.get("overlays") if "overlays" in data else DEFAULT["overlays"]
    data["overlays"] = []
    for index, source in enumerate(source_overlays):
        default_o = next((item for item in DEFAULT["overlays"] if item["key"] == source.get("key")), DEFAULT["overlays"][0])
        o = dict(default_o)
        o.update(source)
        o.setdefault("key", f"overlay_{index + 1}")
        o.setdefault("type", o.get("key", "box").split("_", 1)[0])
        o.setdefault("showBorder", True)
        if o.get("type") == "clock":
            o.setdefault("mode", "time")
        if o.get("type") == "date":
            o.setdefault("mode", "yyyy_mm_dd")
        data["overlays"].append(o)


def save_config(data):
    data["updatedAt"] = time.strftime("%Y-%m-%d %H:%M:%S")
    CONFIG.write_text(json.dumps(data, indent=2), encoding="utf-8")


def ensure_config():
    if not CONFIG.exists():
        save_config(deep_default())


def local_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def latest_logged_client_ip():
    pattern = re.compile(r"\bfrom=(\d{1,3}(?:\.\d{1,3}){3})\b")
    for path in (LOG_FILE, LEGACY_LOG_FILE):
        if not path.exists():
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except Exception:
            continue
        for line in reversed(lines):
            match = pattern.search(line)
            if match and match.group(1) != "127.0.0.1":
                return match.group(1)
    return ""


def config_mtime():
    try:
        return int(CONFIG.stat().st_mtime)
    except Exception:
        return 0


def file_mtime(path):
    try:
        return int(path.stat().st_mtime)
    except Exception:
        return 0


def stamp(path, text=""):
    path.write_text(text or datetime.now().isoformat(timespec="seconds"), encoding="utf-8")


def ensure_log_dir():
    LOG_DIR.mkdir(parents=True, exist_ok=True)


def safe_stamp_for_filename(value=None):
    raw = value or datetime.now().isoformat(timespec="seconds")
    return re.sub(r"[^0-9A-Za-z_-]+", "-", raw).strip("-")


def rotate_live_log(reason, ip):
    ensure_log_dir()
    candidates = [LOG_FILE, LEGACY_LOG_FILE]
    for source in candidates:
        try:
            if not source.exists() or source.stat().st_size == 0:
                continue
            archive = LOG_DIR / f"VeeDash-log-{safe_stamp_for_filename()}-{safe_stamp_for_filename(ip)}-{reason}.txt"
            shutil.copy2(source, archive)
            source.write_text("", encoding="utf-8")
            return archive
        except Exception:
            continue
    LOG_FILE.touch(exist_ok=True)
    return None


def note_dash_contact(ip, route):
    if ip in ("127.0.0.1", "::1"):
        return
    now = datetime.now().isoformat(timespec="seconds")
    previous_contact = file_mtime(LAST_CONTACT)
    previous_welcome = file_mtime(LAST_WELCOME)
    was_away = previous_contact == 0 or time.time() - previous_contact > AWAY_SECONDS
    LAST_CLIENT.write_text(ip, encoding="utf-8")
    stamp(LAST_CONTACT, f"{now} from={ip} {route}")
    if was_away:
        archive = rotate_live_log(route, ip)
        if previous_welcome < previous_contact or time.time() - previous_welcome > AWAY_SECONDS:
            message = random.choice(WELCOME_MESSAGES)
            if archive:
                message += f"\nPrevious logs saved:\n{archive.name}"
            MESSAGE.write_text(message, encoding="utf-8")
            stamp(LAST_WELCOME, f"{now} welcomed {ip}")
        maybe_run_online_command(ip, route)


def maybe_run_online_command(ip, route):
    try:
        data = json.loads(CONFIG.read_text(encoding="utf-8", errors="replace")) if CONFIG.exists() else {}
    except Exception:
        data = {}
    if not data.get("runCommandOnConnect", False):
        return
    command = str(data.get("onlineCommand", "")).strip()
    if not command:
        return
    now = datetime.now().isoformat(timespec="seconds")
    stamp(LAST_COMMAND_RUN, f"{now} from={ip} route={route} command={command}")
    threading.Thread(target=run_online_command_worker, args=(command, ip, route, now), daemon=True).start()


def run_online_command_worker(command, ip, route, started):
    header = f"{started} car online from {ip} via {route}\n$ {command}\n"
    COMMAND_OUTPUT.write_text(header + "Running...\n", encoding="utf-8")
    try:
        result = subprocess.run(
            command,
            cwd=str(BASE),
            shell=True,
            text=True,
            capture_output=True,
            timeout=60,
        )
        output = [header, f"exit code: {result.returncode}"]
        if result.stdout:
            output.append("\nSTDOUT\n" + result.stdout.strip())
        if result.stderr:
            output.append("\nSTDERR\n" + result.stderr.strip())
        COMMAND_OUTPUT.write_text("\n".join(output).strip(), encoding="utf-8")
    except subprocess.TimeoutExpired:
        COMMAND_OUTPUT.write_text(header + "Timed out after 60 seconds.", encoding="utf-8")
    except Exception as ex:
        COMMAND_OUTPUT.write_text(header + f"Failed: {ex}", encoding="utf-8")


def background_asset_path():
    ensure_config()
    try:
        data = json.loads(CONFIG.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        data = {}
    name = data.get("backgroundAsset", "")
    if name:
        candidate = (ASSETS / Path(name).name).resolve()
        root = ASSETS.resolve()
        if root == candidate.parent or root in candidate.parents:
            return candidate
    raw = data.get("backgroundImage", "")
    if raw:
        return Path(raw)
    return None


def asset_path(name):
    candidate = (ASSETS / Path(unquote(name)).name).resolve()
    root = ASSETS.resolve()
    if (root == candidate.parent or root in candidate.parents) and candidate.exists():
        return candidate
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


class VeeDashHandler(BaseHTTPRequestHandler):
    def is_local_client(self):
        return self.client_address[0] in ("127.0.0.1", "::1")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(length).decode("utf-8", "replace")
        fields = parse_qs(body)
        seq = fields.get("seq", [""])[0]
        line = fields.get("line", [""])[0]
        now = datetime.now().isoformat(timespec="seconds")
        note_dash_contact(self.client_address[0], "log")
        ensure_log_dir()
        with LOG_FILE.open("a", encoding="utf-8") as handle:
            handle.write(f"{now} seq={seq} from={self.client_address[0]} {line}\n")
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        route = self.path.split("?", 1)[0]
        if route == "/hello":
            now = datetime.now().isoformat(timespec="seconds")
            note_dash_contact(self.client_address[0], "hello")
            self.send_json({
                "app": "VeeDash",
                "role": "pc-server",
                "port": SERVER_PORT,
                "host": local_ip(),
                "updatedAt": config_mtime(),
            })
            return
        if route == "/message":
            if not MESSAGE.exists():
                MESSAGE.write_text("PC editor is running. Config/chat/assets are served from this window.", encoding="utf-8")
            self.send_text(MESSAGE.read_text(encoding="utf-8", errors="replace"), "text/plain; charset=utf-8")
            return
        if route == "/config":
            ensure_config()
            now = datetime.now().isoformat(timespec="seconds")
            note_dash_contact(self.client_address[0], "config")
            if not self.is_local_client():
                stamp(LAST_CONFIG_SERVED, f"{now} served config to {self.client_address[0]} version={config_mtime()}")
            self.send_text(CONFIG.read_text(encoding="utf-8", errors="replace"), "application/json; charset=utf-8")
            return
        if route == "/asset/background":
            path = background_asset_path()
            self.send_file(path)
            return
        if route.startswith("/asset/"):
            self.send_file(asset_path(route.rsplit("/", 1)[-1]))
            return
        self.send_text("VeeDash editor server is running. POST /log, GET /message, GET /config, GET /asset/name\n", "text/plain; charset=utf-8")

    def send_json(self, payload):
        self.send_text(json.dumps(payload), "application/json; charset=utf-8")

    def send_text(self, text, mime):
        data = text.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_file(self, path):
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

    def log_message(self, _format, *args):
        return


def cover_resize(image, width, height):
    src_w, src_h = image.size
    scale = max(width / max(1, src_w), height / max(1, src_h))
    new_w = max(1, int(src_w * scale))
    new_h = max(1, int(src_h * scale))
    resized = image.resize((new_w, new_h), Image.LANCZOS)
    left = max(0, (new_w - width) // 2)
    top = max(0, (new_h - height) // 2)
    return resized.crop((left, top, left + width, top + height))


class Editor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("VeeDash PC Editor")
        self.geometry("1120x720")
        self.data = load_config()
        self.selected = tk.StringVar(value="rpm")
        self.mode = tk.StringVar(value="number")
        self.item_label = tk.StringVar(value="")
        self.item_pid = tk.StringVar(value="rpm")
        self.vars = {}
        self.reactive_vars = {}
        self.alpha_vars = {}
        self.drag_key = None
        self.drag_mode = "move"
        self.drag_dx = 0
        self.drag_dy = 0
        self.preview_images = {}
        self.preview_refs = []
        self.preview_last = 0
        self.gif_frames = []
        self.gif_index = 0
        self.gif_last = 0
        self.command_output_mtime = 0
        self.server_address = tk.StringVar(value="")
        self.dash_client_ip = tk.StringVar(value=str(self.data.get("dashClientIp", "")))
        self.info_text = tk.StringVar(value="Starting editor server.")
        self.http_server = None
        self.start_server()
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.build()
        self.load_selected()
        self.load_gif()
        self.tick()

    def build(self):
        left_outer = ttk.Frame(self)
        left_outer.pack(side=tk.LEFT, fill=tk.Y)
        left_canvas = tk.Canvas(left_outer, width=320, highlightthickness=0)
        left_scroll = ttk.Scrollbar(left_outer, orient="vertical", command=left_canvas.yview)
        left_canvas.configure(yscrollcommand=left_scroll.set)
        left_canvas.pack(side=tk.LEFT, fill=tk.Y)
        left_scroll.pack(side=tk.RIGHT, fill=tk.Y)
        left = ttk.Frame(left_canvas, padding=10)
        left_window = left_canvas.create_window((0, 0), window=left, anchor="nw")
        left.bind("<Configure>", lambda _e: left_canvas.configure(scrollregion=left_canvas.bbox("all")))
        left_canvas.bind("<Configure>", lambda e: left_canvas.itemconfigure(left_window, width=e.width))
        left_canvas.bind_all("<MouseWheel>", lambda e: left_canvas.yview_scroll(int(-1 * (e.delta / 120)), "units"))
        right = ttk.Frame(self, padding=10)
        right.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)

        toolbar = ttk.Frame(right)
        toolbar.pack(fill=tk.X, pady=(0, 8))
        self.add_menu = tk.Menu(toolbar, tearoff=False)
        self.add_menu.add_command(label="Gauge", command=self.add_gauge)
        self.add_menu.add_command(label="Chat popup", command=lambda: self.add_overlay("chat"))
        self.add_menu.add_command(label="Debug log", command=lambda: self.add_overlay("log"))
        self.add_menu.add_command(label="Clock", command=lambda: self.add_overlay("clock"))
        self.add_menu.add_command(label="Date", command=lambda: self.add_overlay("date"))
        add_button = ttk.Menubutton(toolbar, text="Add", menu=self.add_menu)
        add_button.pack(side=tk.LEFT, padx=(0, 4))
        ttk.Button(toolbar, text="Delete", command=self.delete_selected).pack(side=tk.LEFT, padx=4)
        ttk.Button(toolbar, text="Push dash", command=self.push_dash_now).pack(side=tk.LEFT, padx=4)
        ttk.Button(toolbar, text="Reset", command=self.reset_default).pack(side=tk.LEFT, padx=4)
        self.asset_menu = tk.Menu(toolbar, tearoff=False)
        self.asset_menu.add_command(label="Set background GIF/image", command=self.pick_background)
        self.asset_menu.add_command(label="Clear background", command=self.clear_background)
        self.asset_menu.add_separator()
        self.asset_menu.add_command(label="Set selected dial GIF/image", command=self.pick_gauge_image)
        self.asset_menu.add_command(label="Clear selected dial image", command=self.clear_gauge_image)
        ttk.Menubutton(toolbar, text="Images", menu=self.asset_menu).pack(side=tk.LEFT, padx=4)
        self.color_menu = tk.Menu(toolbar, tearoff=False)
        self.color_menu.add_command(label="Accent color", command=lambda: self.pick_color("accentColor"))
        self.color_menu.add_command(label="Background color", command=lambda: self.pick_color("backgroundColor"))
        self.color_menu.add_command(label="Gauge fill color", command=lambda: self.pick_color("gaugeFillColor"))
        ttk.Menubutton(toolbar, text="Colors", menu=self.color_menu).pack(side=tk.LEFT, padx=4)

        ttk.Label(left, textvariable=self.info_text, foreground="#06c", wraplength=290).pack(anchor="w", pady=(0, 8))
        notebook = ttk.Notebook(left)
        notebook.pack(fill=tk.BOTH, expand=True)

        item_tab = ttk.Frame(notebook, padding=8)
        visual_tab = ttk.Frame(notebook, padding=8)
        dash_tab = ttk.Frame(notebook, padding=8)
        network_tab = ttk.Frame(notebook, padding=8)
        automation_tab = ttk.Frame(notebook, padding=8)
        notebook.add(item_tab, text="Item")
        notebook.add(visual_tab, text="Visuals")
        notebook.add(dash_tab, text="Dash")
        notebook.add(network_tab, text="Network")
        notebook.add(automation_tab, text="Auto")

        ttk.Label(network_tab, text="Connection").pack(anchor="w")
        ttk.Label(network_tab, textvariable=self.server_address, foreground="#0a7").pack(anchor="w")
        ttk.Label(network_tab, text="Client IP").pack(anchor="w", pady=(6, 0))
        dash_ip_entry = ttk.Entry(network_tab, textvariable=self.dash_client_ip, width=26)
        dash_ip_entry.pack(fill=tk.X, pady=(0, 4))
        dash_ip_entry.bind("<Return>", lambda _e: self.save_network_fields())
        dash_ip_entry.bind("<FocusOut>", lambda _e: self.save_network_fields())
        network_buttons = ttk.Frame(network_tab)
        network_buttons.pack(fill=tk.X, pady=(0, 6))
        ttk.Button(network_buttons, text="Use last", command=self.use_last_client_ip).pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 3))
        ttk.Button(network_buttons, text="Save IP", command=self.save_network_fields).pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(3, 0))

        ttk.Label(item_tab, text="Selected item").pack(anchor="w")
        self.item_box = ttk.Combobox(item_tab, textvariable=self.selected, values=self.item_keys(), state="readonly", width=26)
        self.item_box.pack(fill=tk.X, pady=(0, 8))
        self.item_box.bind("<<ComboboxSelected>>", lambda _e: self.load_selected())

        ttk.Label(item_tab, text="Display name").pack(anchor="w")
        self.label_entry = ttk.Entry(item_tab, textvariable=self.item_label)
        self.label_entry.pack(fill=tk.X, pady=(0, 8))
        self.label_entry.bind("<Return>", lambda _e: self.changed())
        self.label_entry.bind("<FocusOut>", lambda _e: self.changed())

        ttk.Label(item_tab, text="Data source").pack(anchor="w")
        self.pid_box = ttk.Combobox(item_tab, textvariable=self.item_pid, values=self.data_source_labels(), state="normal")
        self.pid_box.pack(fill=tk.X, pady=(0, 8))
        self.pid_box.bind("<<ComboboxSelected>>", lambda _e: self.changed())

        ttk.Label(item_tab, text="Display mode").pack(anchor="w")
        self.mode_box = ttk.Combobox(item_tab, textvariable=self.mode, values=["number", "graph", "both", "ring", "bar"], state="readonly")
        self.mode_box.pack(fill=tk.X, pady=(0, 8))
        self.mode_box.bind("<<ComboboxSelected>>", lambda _e: self.changed())

        for name in ("x", "y"):
            self.vars[name] = tk.DoubleVar()
        for name, label, lo, hi in [("size", "SIZE", 0.08, 0.40), ("barThickness", "BAR THICKNESS", 0.08, 0.50), ("layer", "LAYER", 0, 100)]:
            ttk.Label(item_tab, text=label).pack(anchor="w")
            var = tk.DoubleVar()
            self.vars[name] = var
            ttk.Scale(item_tab, from_=lo, to=hi, variable=var, command=lambda _v: self.changed()).pack(fill=tk.X)

        self.visible = tk.BooleanVar(value=True)
        ttk.Checkbutton(item_tab, text="Visible", variable=self.visible, command=self.changed).pack(anchor="w", pady=8)
        self.show_border = tk.BooleanVar(value=True)
        ttk.Checkbutton(item_tab, text="Show border", variable=self.show_border, command=self.changed).pack(anchor="w")
        ttk.Button(item_tab, text="Bring forward", command=lambda: self.bump_layer(5)).pack(fill=tk.X, pady=2)
        ttk.Button(item_tab, text="Send backward", command=lambda: self.bump_layer(-5)).pack(fill=tk.X, pady=2)

        ttk.Label(item_tab, text="Dial response").pack(anchor="w", pady=(12, 0))
        self.reactive_grow = tk.BooleanVar(value=False)
        self.reactive_tint = tk.BooleanVar(value=False)
        ttk.Checkbutton(item_tab, text="Grow with value", variable=self.reactive_grow, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(item_tab, text="Tint by thresholds", variable=self.reactive_tint, command=self.changed).pack(anchor="w")
        ttk.Button(item_tab, text="Auto tint selected gauge", command=self.apply_auto_tint_selected).pack(fill=tk.X, pady=(6, 2))
        ttk.Button(item_tab, text="Auto tint all known gauges", command=self.apply_auto_tint_all).pack(fill=tk.X, pady=2)
        for name, label, lo, hi in [
            ("valueMin", "Low/min value", 0, 8000),
            ("valueMax", "High/max value", 1, 8000),
            ("scaleMax", "Max grow size", 1.0, 1.8),
            ("midAt", "Mid tint starts", 0, 8000),
            ("highAt", "High tint starts", 0, 8000),
        ]:
            var = tk.DoubleVar()
            self.reactive_vars[name] = var
            row = ttk.Frame(item_tab)
            row.pack(fill=tk.X, pady=(2, 0))
            ttk.Label(row, text=label).pack(side=tk.LEFT, anchor="w")
            entry = ttk.Entry(row, textvariable=var, width=8)
            entry.pack(side=tk.RIGHT)
            entry.bind("<Return>", lambda _e: self.changed())
            entry.bind("<FocusOut>", lambda _e: self.changed())
            ttk.Scale(item_tab, from_=lo, to=hi, variable=var, command=lambda _v: self.changed()).pack(fill=tk.X)
        ttk.Button(item_tab, text="Low tint color", command=lambda: self.pick_reactive_color("lowColor")).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(item_tab, text="Mid tint color", command=lambda: self.pick_reactive_color("midColor")).pack(fill=tk.X, pady=2)
        ttk.Button(item_tab, text="High tint color", command=lambda: self.pick_reactive_color("highColor")).pack(fill=tk.X, pady=2)

        self.show_log = tk.BooleanVar(value=self.data.get("showLog", True))
        self.show_chat = tk.BooleanVar(value=self.data.get("showChat", True))
        self.chat_popup_seconds = tk.DoubleVar(value=float(self.data.get("chatPopupSeconds", 6.5)))
        self.auto_reconnect = tk.BooleanVar(value=self.data.get("autoReconnect", True))
        self.auto_dim = tk.BooleanVar(value=self.data.get("autoDim", True))
        self.run_command_on_connect = tk.BooleanVar(value=self.data.get("runCommandOnConnect", False))
        ttk.Checkbutton(dash_tab, text="Show debug log", variable=self.show_log, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(dash_tab, text="Show chat box", variable=self.show_chat, command=self.changed).pack(anchor="w")
        ttk.Label(dash_tab, text="Chat popup seconds").pack(anchor="w")
        ttk.Scale(dash_tab, from_=2.0, to=15.0, variable=self.chat_popup_seconds, command=lambda _v: self.changed()).pack(fill=tk.X)
        ttk.Checkbutton(dash_tab, text="Auto reconnect", variable=self.auto_reconnect, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(dash_tab, text="Auto dim with lights", variable=self.auto_dim, command=self.changed).pack(anchor="w")

        ttk.Label(visual_tab, text="Transparency").pack(anchor="w")
        for key, label in [
            ("gaugeAlpha", "Gauge fill"),
            ("backgroundDimAlpha", "Background dim"),
            ("logAlpha", "Debug log"),
            ("chatAlpha", "Chat popup"),
            ("toolbarAlpha", "Top buttons"),
            ("configAlpha", "Config panel"),
            ("nightBrightness", "Night screen brightness"),
            ("nightExtraDim", "Night extra dim"),
        ]:
            ttk.Label(visual_tab, text=label).pack(anchor="w")
            var = tk.DoubleVar(value=float(self.data.get(key, DEFAULT[key])))
            self.alpha_vars[key] = var
            ttk.Scale(visual_tab, from_=0.0, to=1.0, variable=var, command=lambda _v: self.alpha_changed()).pack(fill=tk.X)

        ttk.Button(visual_tab, text="Motion GIF / image background", command=self.pick_background).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(visual_tab, text="Clear image background", command=self.clear_background).pack(fill=tk.X, pady=2)
        ttk.Button(visual_tab, text="Selected dial image/GIF", command=self.pick_gauge_image).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(visual_tab, text="Clear selected dial image", command=self.clear_gauge_image).pack(fill=tk.X, pady=2)
        ttk.Button(visual_tab, text="Accent color", command=lambda: self.pick_color("accentColor")).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(visual_tab, text="Background color", command=lambda: self.pick_color("backgroundColor")).pack(fill=tk.X, pady=2)
        ttk.Button(visual_tab, text="Gauge fill color", command=lambda: self.pick_color("gaugeFillColor")).pack(fill=tk.X, pady=2)

        ttk.Label(dash_tab, text="Dash chat message").pack(anchor="w", pady=(12, 2))
        self.message = tk.Text(dash_tab, height=5, width=30)
        self.message.pack(fill=tk.X)
        if MESSAGE.exists():
            self.message.insert("1.0", MESSAGE.read_text(encoding="utf-8", errors="replace"))
        ttk.Button(dash_tab, text="Send chat", command=self.save_message).pack(fill=tk.X, pady=4)

        ttk.Label(automation_tab, text="Run local program").pack(anchor="w")
        self.command_text = tk.Text(automation_tab, height=4, width=30)
        self.command_text.pack(fill=tk.X)
        self.command_text.insert("1.0", str(self.data.get("onlineCommand", "python --version")))
        ttk.Checkbutton(automation_tab, text="Run when car comes online", variable=self.run_command_on_connect, command=self.save_command_settings).pack(anchor="w", pady=(4, 0))
        ttk.Button(automation_tab, text="Run command", command=self.run_command).pack(fill=tk.X, pady=4)
        self.command_output = tk.Text(automation_tab, height=10, width=30)
        self.command_output.pack(fill=tk.X)
        if COMMAND_OUTPUT.exists():
            self.command_output.insert("1.0", COMMAND_OUTPUT.read_text(encoding="utf-8", errors="replace"))
        else:
            self.command_output.insert("1.0", "Command output will appear here.")

        self.canvas = tk.Canvas(right, width=800, height=480, bg="#111111", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True)
        self.canvas.bind("<ButtonPress-1>", self.start_drag)
        self.canvas.bind("<B1-Motion>", self.drag)
        self.canvas.bind("<ButtonRelease-1>", self.end_drag)
        ttk.Label(right, text="Drag items to move. Drag the small white corner handle to resize. Dial images/GIFs are staged to the dash and clipped to fill the whole dial.").pack(anchor="w")

    def item_keys(self):
        return [g["key"] for g in self.data["gauges"]] + [o["key"] for o in self.data["overlays"]]

    def data_source_labels(self):
        return [f"{label} ({key})" for key, label in PID_CATALOG]

    def label_to_pid(self, label):
        match = re.search(r"\(([^)]+)\)\s*$", label or "")
        pid = match.group(1).strip().lower() if match else (label or "").strip().lower()
        return self.normalize_pid_value(pid)

    def pid_to_label(self, pid):
        pid = self.normalize_pid_value(pid)
        for key, label in PID_CATALOG:
            if key == pid:
                return f"{label} ({key})"
        return pid.upper()

    def normalize_pid_value(self, value):
        return normalize_pid_static(value)

    def item(self, key=None):
        key = key or self.selected.get()
        for g in self.data["gauges"]:
            if g["key"] == key:
                return g, "gauge"
        for o in self.data["overlays"]:
            if o["key"] == key:
                return o, "overlay"
        if self.data["gauges"]:
            return self.data["gauges"][0], "gauge"
        if self.data["overlays"]:
            return self.data["overlays"][0], "overlay"
        return {"key": "", "x": 0.5, "y": 0.5, "w": 0.2, "h": 0.1, "visible": True, "layer": 20}, "overlay"

    def load_selected(self):
        item, kind = self.item()
        if kind == "gauge":
            pid = item.get("pid", item.get("key", "rpm"))
            if pid not in GAUGE_KEYS:
                pid = "rpm"
            self.item_label.set(item.get("label", GAUGE_LABELS.get(pid, pid.upper())))
            self.item_pid.set(self.pid_to_label(pid))
            self.label_entry.configure(state="normal")
            self.pid_box.configure(state="normal")
            self.mode_box.configure(values=["number", "graph", "both", "ring", "bar"], state="readonly")
            self.mode.set(item.get("mode", "number"))
        elif item.get("type", item.get("key")) == "clock":
            self.item_label.set(item.get("key", "clock"))
            self.item_pid.set("")
            self.label_entry.configure(state="disabled")
            self.pid_box.configure(state="disabled")
            self.mode_box.configure(values=["time", "time_date", "yyyy_mm_dd_time", "yyyy_mm_dd_ampm", "date", "seconds", "compact"], state="readonly")
            self.mode.set(item.get("mode", "time"))
        elif item.get("type", item.get("key")) == "date":
            self.item_label.set(item.get("key", "date"))
            self.item_pid.set("")
            self.label_entry.configure(state="disabled")
            self.pid_box.configure(state="disabled")
            self.mode_box.configure(values=["yyyy_mm_dd", "mm_dd_yyyy", "weekday_date", "short_date"], state="readonly")
            self.mode.set(item.get("mode", "yyyy_mm_dd"))
        else:
            self.item_label.set(item.get("key", "overlay"))
            self.item_pid.set("")
            self.label_entry.configure(state="disabled")
            self.pid_box.configure(state="disabled")
            self.mode_box.configure(state="disabled")
            self.mode.set("number")
        self.vars["x"].set(item.get("x", 0.5))
        self.vars["y"].set(item.get("y", 0.5))
        self.vars["size"].set(item.get("size", item.get("w", 0.22)))
        self.vars["barThickness"].set(item.get("barThickness", 0.20))
        self.vars["layer"].set(item.get("layer", 20))
        self.visible.set(item.get("visible", True))
        self.show_border.set(item.get("showBorder", True))
        reactive = item.get("reactive", {}) if kind == "gauge" else {}
        self.reactive_grow.set(bool(reactive.get("grow", False)))
        self.reactive_tint.set(bool(reactive.get("tint", False)))
        for key, var in self.reactive_vars.items():
            var.set(float(reactive.get(key, DEFAULT["gauges"][0]["reactive"].get(key, 0))))
        self.draw_preview()

    def changed(self):
        item, kind = self.item()
        if not item.get("key"):
            return
        item["x"] = round(self.vars["x"].get(), 3)
        item["y"] = round(self.vars["y"].get(), 3)
        item["visible"] = bool(self.visible.get())
        item["showBorder"] = bool(self.show_border.get())
        item["layer"] = int(round(self.vars["layer"].get()))
        if kind == "gauge":
            pid = self.label_to_pid(self.item_pid.get())
            item["pid"] = pid
            label = self.item_label.get().strip()
            item["label"] = label or GAUGE_LABELS.get(pid, pid.upper())
            item["size"] = round(self.vars["size"].get(), 3)
            item["barThickness"] = round(max(0.08, min(0.50, float(self.vars["barThickness"].get()))), 3)
            item["mode"] = self.mode.get()
            reactive = item.setdefault("reactive", {})
            reactive["grow"] = bool(self.reactive_grow.get())
            reactive["tint"] = bool(self.reactive_tint.get())
            for key, var in self.reactive_vars.items():
                reactive[key] = round(float(var.get()), 2)
        else:
            item["w"] = round(self.vars["size"].get(), 3)
            item["h"] = round(max(0.10, self.vars["size"].get() * 0.55), 3)
            if item.get("type", item.get("key")) in ("clock", "date"):
                item["mode"] = self.mode.get()
        self.data["showLog"] = bool(self.show_log.get())
        self.data["showChat"] = bool(self.show_chat.get())
        self.data["chatPopupSeconds"] = round(max(2.0, min(15.0, float(self.chat_popup_seconds.get()))), 1)
        self.data["autoReconnect"] = bool(self.auto_reconnect.get())
        self.data["autoDim"] = bool(self.auto_dim.get())
        self.save_all(silent=True)
        self.draw_preview()

    def alpha_changed(self):
        for key, var in self.alpha_vars.items():
            self.data[key] = round(max(0.0, min(1.0, float(var.get()))), 3)
        self.save_all(silent=True)
        self.draw_preview()

    def pick_color(self, key):
        color = colorchooser.askcolor(color=self.data.get(key, "#000000"))[1]
        if color:
            self.data[key] = color
            self.save_all(silent=True)
            self.draw_preview()

    def pick_reactive_color(self, key):
        item, kind = self.item()
        if kind != "gauge":
            return
        reactive = item.setdefault("reactive", {})
        color = colorchooser.askcolor(color=reactive.get(key, "#1fb6ff"))[1]
        if color:
            reactive[key] = color
            self.save_all(silent=True)
            self.draw_preview()

    def apply_auto_tint_selected(self):
        item, kind = self.item()
        if kind != "gauge":
            self.info_text.set("Auto tint works on gauges.")
            return
        if self.apply_auto_tint(item):
            self.load_selected()
            self.save_all(silent=True)
            self.info_text.set(f"Auto tint applied to {item.get('label', item.get('key', 'gauge'))}.")
        else:
            self.info_text.set("No auto tint preset for that data source.")

    def apply_auto_tint_all(self):
        count = 0
        for gauge in self.data.get("gauges", []):
            if self.apply_auto_tint(gauge):
                count += 1
        self.load_selected()
        self.save_all(silent=True)
        self.info_text.set(f"Auto tint applied to {count} known gauge(s).")

    def apply_auto_tint(self, gauge):
        pid = gauge.get("pid", gauge.get("key", "")).lower()
        preset = AUTO_TINT_PRESETS.get(pid)
        if not preset:
            return False
        reactive = gauge.setdefault("reactive", {})
        reactive.update(json.loads(json.dumps(preset)))
        return True

    def pick_background(self):
        path = filedialog.askopenfilename(filetypes=[("Images", "*.gif *.png *.jpg *.jpeg *.webp *.bmp"), ("All files", "*.*")])
        if not path:
            return
        ASSETS.mkdir(exist_ok=True)
        src = Path(path)
        dest = ASSETS / src.name
        if src.resolve() != dest.resolve():
            shutil.copy2(src, dest)
        self.data["backgroundImage"] = str(dest)
        self.data["backgroundAsset"] = dest.name
        self.load_gif()
        self.save_all(silent=True)
        self.draw_preview()

    def pick_gauge_image(self):
        item, kind = self.item()
        if kind != "gauge":
            return
        path = filedialog.askopenfilename(filetypes=[("Images", "*.gif *.png *.jpg *.jpeg *.webp *.bmp"), ("All files", "*.*")])
        if not path:
            return
        ASSETS.mkdir(exist_ok=True)
        src = Path(path)
        dest = ASSETS / src.name
        if src.resolve() != dest.resolve():
            shutil.copy2(src, dest)
        item["imageAsset"] = dest.name
        self.preview_images.pop(item["key"], None)
        self.save_all(silent=True)
        self.draw_preview()

    def clear_gauge_image(self):
        item, kind = self.item()
        if kind != "gauge":
            return
        item["imageAsset"] = ""
        self.preview_images.pop(item["key"], None)
        self.save_all(silent=True)
        self.draw_preview()

    def clear_background(self):
        self.data["backgroundImage"] = ""
        self.data["backgroundAsset"] = ""
        self.gif_frames = []
        self.save_all(silent=True)
        self.draw_preview()

    def load_gif(self):
        self.gif_frames = []
        path = self.data.get("backgroundImage", "")
        if not path:
            return
        try:
            i = 0
            while True:
                frame = tk.PhotoImage(file=path, format=f"gif -index {i}")
                self.gif_frames.append(frame)
                i += 1
        except Exception:
            if not self.gif_frames:
                try:
                    self.gif_frames.append(tk.PhotoImage(file=path))
                except Exception:
                    self.gif_frames = []

    def bump_layer(self, delta):
        item, _kind = self.item()
        if not item.get("key"):
            return
        item["layer"] = int(item.get("layer", 20)) + delta
        self.vars["layer"].set(item["layer"])
        self.save_all(silent=True)
        self.draw_preview()

    def refresh_item_box(self):
        values = self.item_keys()
        self.item_box.configure(values=values)
        if values and self.selected.get() not in values:
            self.selected.set(values[0])
        elif not values:
            self.selected.set("")

    def unique_key(self, base):
        base = re.sub(r"[^0-9A-Za-z_]+", "_", base).strip("_").lower() or "item"
        existing = set(self.item_keys())
        if base not in existing:
            return base
        index = 2
        while f"{base}_{index}" in existing:
            index += 1
        return f"{base}_{index}"

    def add_gauge(self):
        current, kind = self.item()
        default_pid = current.get("pid", current.get("key", "rpm")) if kind == "gauge" else "rpm"
        default_pid = self.normalize_pid_value(default_pid)
        details = self.ask_gauge_details(default_pid)
        if not details:
            return
        name, pid = details
        template = next((g for g in DEFAULT["gauges"] if g["key"] == pid), DEFAULT["gauges"][0])
        item = json.loads(json.dumps(template))
        item["pid"] = pid
        item["key"] = self.unique_key(name or pid)
        item["label"] = name or GAUGE_LABELS.get(pid, pid.upper())
        item["barThickness"] = 0.20
        item["showBorder"] = True
        item["x"] = 0.5
        item["y"] = 0.5
        item["layer"] = max([g.get("layer", 20) for g in self.data["gauges"]] + [20]) + 1
        self.data["gauges"].append(item)
        self.selected.set(item["key"])
        self.refresh_item_box()
        self.load_selected()
        self.save_all(silent=True)

    def ask_gauge_details(self, default_pid):
        dialog = tk.Toplevel(self)
        dialog.title("Add gauge")
        dialog.transient(self)
        dialog.grab_set()
        dialog.resizable(False, False)
        name_var = tk.StringVar(value=GAUGE_LABELS.get(default_pid, default_pid.upper()))
        pid_var = tk.StringVar(value=self.pid_to_label(default_pid))
        result = {"value": None}

        frame = ttk.Frame(dialog, padding=14)
        frame.pack(fill=tk.BOTH, expand=True)
        ttk.Label(frame, text="Gauge name").pack(anchor="w")
        name_entry = ttk.Entry(frame, textvariable=name_var, width=34)
        name_entry.pack(fill=tk.X, pady=(0, 10))
        ttk.Label(frame, text="Data source").pack(anchor="w")
        pid_box = ttk.Combobox(frame, textvariable=pid_var, values=self.data_source_labels(), state="normal", width=34)
        pid_box.pack(fill=tk.X, pady=(0, 12))

        buttons = ttk.Frame(frame)
        buttons.pack(fill=tk.X)

        def accept():
            pid = self.label_to_pid(pid_var.get())
            name = name_var.get().strip() or GAUGE_LABELS.get(pid, pid.upper())
            result["value"] = (name, pid)
            dialog.destroy()

        def cancel():
            dialog.destroy()

        ttk.Button(buttons, text="Add", command=accept).pack(side=tk.RIGHT, padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=cancel).pack(side=tk.RIGHT)
        dialog.bind("<Return>", lambda _e: accept())
        dialog.bind("<Escape>", lambda _e: cancel())
        name_entry.focus_set()
        self.wait_window(dialog)
        return result["value"]

    def add_overlay(self, overlay_type):
        template = next((o for o in DEFAULT["overlays"] if o["key"] == overlay_type), DEFAULT["overlays"][0])
        item = json.loads(json.dumps(template))
        item["key"] = self.unique_key(overlay_type)
        item["type"] = overlay_type
        item["showBorder"] = True
        item["x"] = 0.5
        item["y"] = 0.5
        item["layer"] = max([o.get("layer", 50) for o in self.data["overlays"]] + [50]) + 1
        self.data["overlays"].append(item)
        self.selected.set(item["key"])
        self.refresh_item_box()
        self.load_selected()
        self.save_all(silent=True)

    def delete_selected(self):
        key = self.selected.get()
        before_gauges = len(self.data["gauges"])
        self.data["gauges"] = [g for g in self.data["gauges"] if g.get("key") != key]
        self.data["overlays"] = [o for o in self.data["overlays"] if o.get("key") != key]
        if before_gauges != len(self.data["gauges"]):
            self.preview_images = {k: v for k, v in self.preview_images.items() if not (isinstance(k, tuple) and k[0] == key)}
        self.refresh_item_box()
        self.load_selected()
        self.save_all(silent=True)

    def save_message(self):
        MESSAGE.write_text(self.message.get("1.0", "end").strip(), encoding="utf-8")

    def save_command_settings(self):
        self.data["runCommandOnConnect"] = bool(self.run_command_on_connect.get())
        self.data["onlineCommand"] = self.command_text.get("1.0", "end").strip()
        save_config(self.data)

    def run_command(self):
        command = self.command_text.get("1.0", "end").strip()
        if not command:
            self.set_command_output("No command entered.")
            return
        self.save_command_settings()
        self.set_command_output(f"Running from {BASE}:\n{command}\n\n")
        threading.Thread(target=self.run_command_worker, args=(command,), daemon=True).start()

    def run_command_worker(self, command):
        try:
            result = subprocess.run(
                command,
                cwd=str(BASE),
                shell=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
            output = []
            output.append(f"$ {command}")
            output.append(f"exit code: {result.returncode}")
            if result.stdout:
                output.append("\nSTDOUT\n" + result.stdout.strip())
            if result.stderr:
                output.append("\nSTDERR\n" + result.stderr.strip())
            text = "\n".join(output).strip()
        except subprocess.TimeoutExpired:
            text = f"$ {command}\nTimed out after 60 seconds."
        except Exception as ex:
            text = f"$ {command}\nFailed: {ex}"
        COMMAND_OUTPUT.write_text(text, encoding="utf-8")
        self.after(0, lambda: self.set_command_output(text))

    def set_command_output(self, text):
        self.command_output.delete("1.0", "end")
        self.command_output.insert("1.0", text)

    def save_all(self, silent=False):
        self.data["dashClientIp"] = self.dash_client_ip.get().strip()
        if hasattr(self, "run_command_on_connect"):
            self.data["runCommandOnConnect"] = bool(self.run_command_on_connect.get())
        if hasattr(self, "command_text"):
            self.data["onlineCommand"] = self.command_text.get("1.0", "end").strip()
        save_config(self.data)
        if not silent:
            self.save_message()

    def push_dash_now(self):
        stamp_value = datetime.now().isoformat(timespec="seconds")
        self.data["dashClientIp"] = self.dash_client_ip.get().strip()
        self.data["forcePushAt"] = stamp_value
        save_config(self.data)
        message = "Connected to PC editor. Force loading the newest staged dashboard now."
        MESSAGE.write_text(message, encoding="utf-8")
        self.message.delete("1.0", "end")
        self.message.insert("1.0", message)
        self.info_text.set("Push queued. Waiting for the dash to pull the forced config.")

    def save_network_fields(self):
        self.data["dashClientIp"] = self.dash_client_ip.get().strip()
        self.save_all(silent=True)

    def last_client_ip(self):
        if LAST_CLIENT.exists():
            ip = LAST_CLIENT.read_text(encoding="utf-8", errors="replace").strip()
            if ip:
                return ip
        return latest_logged_client_ip()

    def marker_text(self, path):
        if path.exists():
            return path.read_text(encoding="utf-8", errors="replace").strip()
        return ""

    def use_last_client_ip(self):
        ip = self.last_client_ip()
        if ip:
            self.dash_client_ip.set(ip)
            self.save_network_fields()

    def auto_save_dash_ip(self):
        if self.dash_client_ip.get().strip():
            return
        ip = self.last_client_ip()
        if ip:
            self.dash_client_ip.set(ip)
            self.save_network_fields()

    def reset_default(self):
        self.data = deep_default()
        self.dash_client_ip.set(DEFAULT["dashClientIp"])
        self.show_log.set(True)
        self.show_chat.set(True)
        self.chat_popup_seconds.set(DEFAULT["chatPopupSeconds"])
        self.auto_reconnect.set(True)
        self.auto_dim.set(True)
        for key, var in self.alpha_vars.items():
            var.set(DEFAULT[key])
        self.selected.set("rpm")
        self.load_selected()
        self.load_gif()
        self.save_all(silent=True)

    def start_drag(self, event):
        resize = self.hit_resize(event.x, event.y)
        key = resize or self.hit_test(event.x, event.y)
        if key:
            self.selected.set(key)
            self.load_selected()
            item, _kind = self.item(key)
            w = max(1, self.canvas.winfo_width())
            h = max(1, self.canvas.winfo_height())
            self.drag_key = key
            self.drag_mode = "resize" if resize else "move"
            self.drag_dx = event.x - item.get("x", 0.5) * w
            self.drag_dy = event.y - item.get("y", 0.5) * h

    def drag(self, event):
        if not self.drag_key:
            return
        item, kind = self.item(self.drag_key)
        w = max(1, self.canvas.winfo_width())
        h = max(1, self.canvas.winfo_height())
        if self.drag_mode == "resize":
            cx = item.get("x", 0.5) * w
            cy = item.get("y", 0.5) * h
            if kind == "gauge":
                item["size"] = round(min(0.48, max(0.06, math.hypot(event.x - cx, event.y - cy) / min(w, h))), 3)
                self.vars["size"].set(item["size"])
            else:
                item["w"] = round(min(0.90, max(0.10, abs(event.x - cx) * 2 / w)), 3)
                item["h"] = round(min(0.70, max(0.08, abs(event.y - cy) * 2 / h)), 3)
                self.vars["size"].set(item["w"])
        else:
            item["x"] = round(min(0.98, max(0.02, (event.x - self.drag_dx) / w)), 3)
            item["y"] = round(min(0.96, max(0.08, (event.y - self.drag_dy) / h)), 3)
            self.vars["x"].set(item["x"])
            self.vars["y"].set(item["y"])
        self.save_all(silent=True)
        self.draw_preview()

    def end_drag(self, _event):
        self.drag_key = None
        self.drag_mode = "move"

    def hit_resize(self, x, y):
        w = max(1, self.canvas.winfo_width())
        h = max(1, self.canvas.winfo_height())
        hits = []
        for item, kind in self.all_items():
            if not item.get("visible", True):
                continue
            hx, hy = self.resize_handle(item, kind, w, h)
            if hx - 14 <= x <= hx + 14 and hy - 14 <= y <= hy + 14:
                hits.append((item.get("layer", 0), item["key"]))
        return sorted(hits)[-1][1] if hits else None

    def resize_handle(self, item, kind, w, h):
        cx = item.get("x", 0.5) * w
        cy = item.get("y", 0.5) * h
        if kind == "gauge":
            r = item.get("size", 0.22) * min(w, h)
            return cx + r * 0.72, cy + r * 0.72
        iw = item.get("w", 0.25) * w
        ih = item.get("h", 0.14) * h
        return cx + iw / 2, cy + ih / 2

    def hit_test(self, x, y):
        w = max(1, self.canvas.winfo_width())
        h = max(1, self.canvas.winfo_height())
        hits = []
        for item, kind in self.all_items():
            if not item.get("visible", True):
                continue
            cx = item.get("x", 0.5) * w
            cy = item.get("y", 0.5) * h
            if kind == "gauge":
                r = item.get("size", 0.22) * min(w, h)
                ok = (x - cx) ** 2 + (y - cy) ** 2 <= r ** 2
            else:
                iw = item.get("w", 0.25) * w
                ih = item.get("h", 0.14) * h
                ok = cx - iw / 2 <= x <= cx + iw / 2 and cy - ih / 2 <= y <= cy + ih / 2
            if ok:
                hits.append((item.get("layer", 0), item["key"]))
        return sorted(hits)[-1][1] if hits else None

    def all_items(self):
        out = [(g, "gauge") for g in self.data["gauges"]]
        out.extend((o, "overlay") for o in self.data["overlays"])
        return sorted(out, key=lambda pair: pair[0].get("layer", 0))

    def tick(self):
        self.auto_save_dash_ip()
        self.server_address.set(f"Server: {local_ip()}:{SERVER_PORT}")
        last_ip = self.last_client_ip()
        target_ip = self.dash_client_ip.get().strip()
        config_time = config_mtime()
        served_time = file_mtime(LAST_CONFIG_SERVED)
        contact = self.marker_text(LAST_CONTACT)
        served = self.marker_text(LAST_CONFIG_SERVED)
        if self.http_server and target_ip and served_time >= config_time and served:
            self.info_text.set(f"Sent the newest dashboard to {target_ip}. Waiting for the next edit or dash pull.")
        elif self.http_server and target_ip and contact:
            self.info_text.set(f"Connected to dash at {target_ip}. The next config pull will receive the staged dashboard.")
        elif self.http_server and target_ip:
            detail = f" Last seen was {last_ip}." if last_ip and last_ip != target_ip else ""
            self.info_text.set(f"Ready. Staged dashboard is waiting for {target_ip}.{detail}")
        elif self.http_server:
            self.info_text.set("Server is running. Enter the client IP or click Use last when the dash appears.")
        else:
            self.info_text.set("Server is not running, so the dash cannot pull updates yet.")
        if self.gif_frames and time.time() - self.gif_last > 0.11:
            self.gif_index = (self.gif_index + 1) % len(self.gif_frames)
            self.gif_last = time.time()
            self.draw_preview()
        if self.preview_images and time.time() - self.preview_last > 0.11:
            self.gif_index = (self.gif_index + 1) % 100000
            self.preview_last = time.time()
            self.draw_preview()
        self.refresh_command_output()
        self.after(80, self.tick)

    def refresh_command_output(self):
        if not hasattr(self, "command_output"):
            return
        mtime = file_mtime(COMMAND_OUTPUT)
        if not mtime or mtime == self.command_output_mtime:
            return
        self.command_output_mtime = mtime
        self.set_command_output(COMMAND_OUTPUT.read_text(encoding="utf-8", errors="replace"))

    def start_server(self):
        try:
            self.http_server = ThreadingHTTPServer(("0.0.0.0", SERVER_PORT), VeeDashHandler)
            thread = threading.Thread(target=self.http_server.serve_forever, name="VeeDashEditorServer", daemon=True)
            thread.start()
            self.info_text.set("Server is running. Waiting for the dash to contact this PC.")
        except OSError as ex:
            self.info_text.set(f"Server cannot start: port {SERVER_PORT} is busy.")
            MESSAGE.write_text(f"Editor server could not start on port {SERVER_PORT}: {ex}", encoding="utf-8")

    def close(self):
        if self.http_server:
            self.http_server.shutdown()
            self.http_server.server_close()
        self.destroy()

    def draw_preview(self):
        self.canvas.delete("all")
        self.preview_refs = []
        w = max(1, self.canvas.winfo_width())
        h = max(1, self.canvas.winfo_height())
        bg = self.data.get("backgroundColor", "#000000")
        accent = self.data.get("accentColor", "#1fb6ff")
        fill = self.blend(self.data.get("gaugeFillColor", "#05080c"), self.data.get("gaugeAlpha", 0.73), bg)
        log_fill = self.blend("#000000", self.data.get("logAlpha", 0.93), bg)
        chat_fill = self.blend("#06141d", self.data.get("chatAlpha", 0.86), bg)
        self.canvas.create_rectangle(0, 0, w, h, fill=bg, outline="")
        if self.gif_frames:
            frame = self.gif_frames[self.gif_index % len(self.gif_frames)]
            self.canvas.create_image(w / 2, h / 2, image=frame)
        dim = int(max(0, min(1, self.data.get("backgroundDimAlpha", 0.80))) * 100)
        self.canvas.create_text(12, 10, text=f"dim {dim}% | gauge {int(self.data.get('gaugeAlpha', 0.73) * 100)}%", anchor="nw", fill="#a8dfff", font=("Segoe UI", 9, "bold"))
        for item, kind in self.all_items():
            if not item.get("visible", True):
                continue
            if kind == "gauge":
                self.draw_gauge(item, w, h, accent, fill)
            elif item.get("type", item["key"]) == "chat" and self.data.get("showChat", True):
                self.draw_box(item, w, h, "STATUS\nConnected\n\nCHAT\nMessage from PC", chat_fill)
            elif item.get("type", item["key"]) == "log" and self.data.get("showLog", True):
                self.draw_box(item, w, h, "DEBUG STATUS\nphase: connected\nrpm: 820\nvolts: 14.2", log_fill)
            elif item.get("type", item["key"]) == "clock":
                self.draw_clock(item, w, h, chat_fill, accent)
            elif item.get("type", item["key"]) == "date":
                self.draw_date(item, w, h, chat_fill, accent)

    def blend(self, fg, alpha, bg):
        try:
            alpha = max(0.0, min(1.0, float(alpha)))
            fr, fg2, fb = self.hex_rgb(fg)
            br, bg2, bb = self.hex_rgb(bg)
            r = round(fr * alpha + br * (1.0 - alpha))
            g = round(fg2 * alpha + bg2 * (1.0 - alpha))
            b = round(fb * alpha + bb * (1.0 - alpha))
            return f"#{r:02x}{g:02x}{b:02x}"
        except Exception:
            return fg

    def hex_rgb(self, color):
        color = str(color).lstrip("#")
        if len(color) == 3:
            color = "".join(ch * 2 for ch in color)
        return int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16)

    def draw_box(self, item, w, h, text, color):
        cx = item.get("x", 0.5) * w
        cy = item.get("y", 0.5) * h
        iw = item.get("w", 0.28) * w
        ih = item.get("h", 0.16) * h
        outline = "#ffffff" if item["key"] == self.selected.get() else ("#345" if item.get("showBorder", True) else "")
        self.canvas.create_rectangle(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2, fill=color, outline=outline, width=2)
        self.canvas.create_text(cx - iw / 2 + 10, cy - ih / 2 + 8, text=text, anchor="nw", fill="white", font=("Segoe UI", 9, "bold"))
        if item["key"] == self.selected.get():
            hx, hy = self.resize_handle(item, "overlay", w, h)
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")

    def draw_clock(self, item, w, h, color, accent):
        cx = item.get("x", 0.5) * w
        cy = item.get("y", 0.5) * h
        iw = item.get("w", 0.24) * w
        ih = item.get("h", 0.12) * h
        mode = item.get("mode", "time")
        now = datetime.now()
        if mode == "time_date":
            primary = now.strftime("%I:%M").lstrip("0")
            secondary = now.strftime("%a %b %d")
        elif mode == "yyyy_mm_dd_time":
            primary = now.strftime("%Y/%m/%d")
            secondary = now.strftime("%H:%M")
        elif mode == "yyyy_mm_dd_ampm":
            primary = now.strftime("%Y/%m/%d")
            secondary = now.strftime("%I:%M %p").lstrip("0")
        elif mode == "date":
            primary = now.strftime("%b %d")
            secondary = now.strftime("%Y")
        elif mode == "seconds":
            primary = now.strftime("%I:%M:%S").lstrip("0")
            secondary = now.strftime("%p")
        elif mode == "compact":
            primary = now.strftime("%H%M")
            secondary = ""
        else:
            primary = now.strftime("%I:%M").lstrip("0")
            secondary = now.strftime("%p")
        outline = "#ffffff" if item["key"] == self.selected.get() else ("#345" if item.get("showBorder", True) else "")
        self.canvas.create_rectangle(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2, fill=color, outline=outline, width=2)
        self.canvas.create_text(cx, cy - ih * 0.04, text=primary, anchor="center", fill="white", font=("Segoe UI", int(max(14, ih * 0.38)), "bold"))
        if secondary:
            self.canvas.create_text(cx, cy + ih * 0.30, text=secondary, anchor="center", fill=accent, font=("Segoe UI", int(max(8, ih * 0.16)), "bold"))
        if item["key"] == self.selected.get():
            hx, hy = self.resize_handle(item, "overlay", w, h)
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")

    def draw_date(self, item, w, h, color, accent):
        cx = item.get("x", 0.5) * w
        cy = item.get("y", 0.5) * h
        iw = item.get("w", 0.24) * w
        ih = item.get("h", 0.10) * h
        mode = item.get("mode", "yyyy_mm_dd")
        now = datetime.now()
        if mode == "mm_dd_yyyy":
            text = now.strftime("%m/%d/%Y")
        elif mode == "weekday_date":
            text = now.strftime("%A %b %d")
        elif mode == "short_date":
            text = now.strftime("%b %d")
        else:
            text = now.strftime("%Y/%m/%d")
        outline = "#ffffff" if item["key"] == self.selected.get() else ("#345" if item.get("showBorder", True) else "")
        self.canvas.create_rectangle(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2, fill=color, outline=outline, width=2)
        self.canvas.create_text(cx, cy, text=text, anchor="center", fill="white", font=("Segoe UI", int(max(12, ih * 0.38)), "bold"))
        if item["key"] == self.selected.get():
            hx, hy = self.resize_handle(item, "overlay", w, h)
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")

    def draw_gauge(self, g, w, h, accent, fill):
        pid = g.get("pid", g["key"])
        value = SAMPLE.get(pid, 0)
        reactive = g.get("reactive", {})
        scale = self.reactive_scale(reactive, value)
        tint = self.reactive_tint_color(reactive, value)
        r = g.get("size", 0.22) * min(w, h) * scale
        x = g.get("x", 0.5) * w
        y = g.get("y", 0.5) * h
        selected = g["key"] == self.selected.get()
        width = 5 if selected else 3
        mode = g.get("mode", "number")
        if mode == "bar":
            self.draw_bar(g, x, y, r, accent, fill, value, selected)
            return
        if mode in ("number", "both", "ring") or g.get("imageAsset"):
            self.canvas.create_oval(x-r, y-r, x+r, y+r, fill=fill, outline=accent if g.get("showBorder", True) else "", width=width)
            dial_image = self.dial_preview_image(g, int(max(8, r * 2)))
            if dial_image:
                self.canvas.create_image(x, y, image=dial_image)
            if tint:
                self.canvas.create_oval(x-r, y-r, x+r, y+r, fill=tint, outline="", stipple="gray25")
        if mode in ("graph", "both"):
            self.draw_graph(x, y, r, accent, g.get("showBorder", True))
        if mode in ("number", "both", "ring"):
            self.canvas.create_text(x, y - r * 0.06, text=f"{value:.0f}", fill="white", font=("Segoe UI", int(max(14, r * 0.32)), "bold"))
        self.canvas.create_text(x, y + r * 0.38, text=g.get("label", GAUGE_LABELS.get(pid, pid.upper())), fill="#cfefff", font=("Segoe UI", int(max(9, r * 0.13)), "bold"))
        if selected:
            self.canvas.create_rectangle(x-r-4, y-r-4, x+r+4, y+r+4, outline="#ffffff", dash=(4, 3))
            hx, hy = self.resize_handle(g, "gauge", w, h)
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")

    def reactive_scale(self, reactive, value):
        if not reactive.get("grow", False):
            return 1.0
        lo = float(reactive.get("valueMin", 0))
        hi = float(reactive.get("valueMax", 100))
        if hi <= lo:
            hi = lo + 1
        pct = max(0.0, min(1.0, (float(value) - lo) / (hi - lo)))
        return 1.0 + pct * (float(reactive.get("scaleMax", 1.25)) - 1.0)

    def reactive_tint_color(self, reactive, value):
        if not reactive.get("tint", False):
            return None
        value = float(value)
        if value >= float(reactive.get("highAt", 999999)):
            return reactive.get("highColor", "#ff3b30")
        if value >= float(reactive.get("midAt", 999999)):
            return reactive.get("midColor", "#ffd166")
        return reactive.get("lowColor", "#1fb6ff")

    def dial_preview_image(self, gauge, size):
        if Image is None or ImageTk is None:
            return None
        asset = gauge.get("imageAsset", "")
        if not asset:
            return None
        path = ASSETS / Path(asset).name
        if not path.exists():
            return None
        key = (gauge["key"], str(path), size)
        cached = self.preview_images.get(key)
        if not cached:
            try:
                frames = []
                source = Image.open(path)
                for frame in ImageSequence.Iterator(source):
                    frames.append(frame.convert("RGBA").copy())
                    if len(frames) >= 80:
                        break
                cached = frames or [source.convert("RGBA")]
                self.preview_images[key] = cached
            except Exception:
                return None
        frame = cached[self.gif_index % len(cached)].copy()
        frame = cover_resize(frame, size, size)
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
        frame.putalpha(mask)
        photo = ImageTk.PhotoImage(frame)
        self.preview_refs.append(photo)
        return photo

    def draw_graph(self, x, y, r, accent, show_border=True):
        points = []
        for i in range(34):
            px = x - r * 0.75 + i * (r * 1.5 / 33)
            py = y + math.sin(i * 0.55 + time.time() * 2.0) * r * 0.22
            points.extend([px, py])
        self.canvas.create_rectangle(x-r*0.78, y-r*0.38, x+r*0.78, y+r*0.38, outline="#28455a" if show_border else "", fill="#061016")
        self.canvas.create_line(points, fill=accent, width=3, smooth=True)

    def draw_bar(self, g, x, y, r, accent, fill, value, selected):
        reactive = g.get("reactive", {})
        lo = float(reactive.get("valueMin", 0))
        hi = float(reactive.get("valueMax", 100))
        if hi <= lo:
            hi = lo + 1
        pct = max(0.0, min(1.0, (float(value) - lo) / (hi - lo)))
        tint = self.reactive_tint_color(reactive, value) or accent
        bar_w = r * 2.15
        bar_h = max(6, r * float(g.get("barThickness", 0.20)))
        x1 = x - bar_w / 2
        x2 = x + bar_w / 2
        y1 = y - bar_h / 2
        y2 = y + bar_h / 2
        self.canvas.create_rectangle(x1, y1, x2, y2, fill=fill, outline="#d8f6ff" if g.get("showBorder", True) else "", width=2)
        self.canvas.create_rectangle(x1, y1, x1 + bar_w * pct, y2, fill=tint, outline="")
        self.canvas.create_text(x, y - bar_h * 0.95, text=f"{value:.0f}", fill="white", font=("Segoe UI", int(max(14, r * 0.36)), "bold"))
        pid = g.get("pid", g["key"])
        self.canvas.create_text(x, y + bar_h * 1.85, text=g.get("label", GAUGE_LABELS.get(pid, pid.upper())), fill="#cfefff", font=("Segoe UI", int(max(9, r * 0.16)), "bold"))
        if selected:
            self.canvas.create_rectangle(x1 - 5, y - r * 0.58, x2 + 5, y + r * 0.42, outline="#ffffff", dash=(4, 3))
            hx, hy = self.resize_handle(g, "gauge", max(1, self.canvas.winfo_width()), max(1, self.canvas.winfo_height()))
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")


if __name__ == "__main__":
    app = Editor()
    app.bind("<Configure>", lambda _e: app.draw_preview())
    app.mainloop()
