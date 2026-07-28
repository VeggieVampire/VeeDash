import json
import math
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
LOG_FILE = BASE / "VeeDash-live-log.txt"
LAST_CLIENT = BASE / "VeeDash-last-client.txt"
LAST_CONTACT = BASE / "VeeDash-last-contact.txt"
LAST_CONFIG_SERVED = BASE / "VeeDash-last-config-served.txt"
LAST_WELCOME = BASE / "VeeDash-last-welcome.txt"
SERVER_PORT = 8766
AWAY_SECONDS = 45

GAUGE_KEYS = ["rpm", "speed", "coolant", "volts", "load", "throttle"]
SAMPLE = {
    "rpm": 820,
    "speed": 0,
    "coolant": 72,
    "volts": 14.2,
    "load": 31,
    "throttle": 14,
}

DEFAULT = {
    "dashClientIp": "",
    "showLog": True,
    "showChat": True,
    "chatPopupSeconds": 6.5,
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
    "backgroundImage": "",
    "backgroundAsset": "",
    "gauges": [
        {"key": "rpm", "x": 0.18, "y": 0.34, "size": 0.28, "visible": True, "mode": "both", "layer": 20, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.25, "valueMin": 0, "valueMax": 6000, "tint": False, "midAt": 2500, "highAt": 4500, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
        {"key": "speed", "x": 0.50, "y": 0.34, "size": 0.28, "visible": True, "mode": "number", "layer": 21, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.20, "valueMin": 0, "valueMax": 120, "tint": False, "midAt": 45, "highAt": 80, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
        {"key": "coolant", "x": 0.82, "y": 0.34, "size": 0.24, "visible": True, "mode": "both", "layer": 22, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.20, "valueMin": 60, "valueMax": 115, "tint": True, "midAt": 92, "highAt": 105, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
        {"key": "volts", "x": 0.22, "y": 0.72, "size": 0.22, "visible": True, "mode": "graph", "layer": 23, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.15, "valueMin": 11.5, "valueMax": 15.0, "tint": False, "midAt": 13.0, "highAt": 14.7, "lowColor": "#ff3b30", "midColor": "#1fb6ff", "highColor": "#ffd166"}},
        {"key": "load", "x": 0.50, "y": 0.72, "size": 0.22, "visible": True, "mode": "both", "layer": 24, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.25, "valueMin": 0, "valueMax": 100, "tint": False, "midAt": 55, "highAt": 85, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
        {"key": "throttle", "x": 0.78, "y": 0.72, "size": 0.22, "visible": True, "mode": "both", "layer": 25, "imageAsset": "", "reactive": {"grow": False, "scaleMax": 1.25, "valueMin": 0, "valueMax": 100, "tint": False, "midAt": 35, "highAt": 70, "lowColor": "#1fb6ff", "midColor": "#ffd166", "highColor": "#ff3b30"}},
    ],
    "overlays": [
        {"key": "chat", "x": 0.80, "y": 0.83, "w": 0.28, "h": 0.18, "visible": True, "layer": 80},
        {"key": "log", "x": 0.30, "y": 0.30, "w": 0.52, "h": 0.36, "visible": True, "layer": 70},
    ],
}


def deep_default():
    return json.loads(json.dumps(DEFAULT))


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
    existing = {g.get("key"): g for g in data.get("gauges", [])}
    data["gauges"] = []
    for default_g in DEFAULT["gauges"]:
        g = dict(default_g)
        g.update(existing.get(g["key"], {}))
        g.setdefault("mode", "number")
        g.setdefault("layer", default_g["layer"])
        g.setdefault("imageAsset", "")
        reactive = dict(default_g.get("reactive", {}))
        reactive.update(g.get("reactive", {}))
        g["reactive"] = reactive
        data["gauges"].append(g)
    existing_o = {o.get("key"): o for o in data.get("overlays", [])}
    data["overlays"] = []
    for default_o in DEFAULT["overlays"]:
        o = dict(default_o)
        o.update(existing_o.get(o["key"], {}))
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
    if not LOG_FILE.exists():
        return ""
    pattern = re.compile(r"\bfrom=(\d{1,3}(?:\.\d{1,3}){3})\b")
    try:
        lines = LOG_FILE.read_text(encoding="utf-8", errors="replace").splitlines()
    except Exception:
        return ""
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


def note_dash_contact(ip, route):
    if ip in ("127.0.0.1", "::1"):
        return
    now = datetime.now().isoformat(timespec="seconds")
    previous_contact = file_mtime(LAST_CONTACT)
    previous_welcome = file_mtime(LAST_WELCOME)
    LAST_CLIENT.write_text(ip, encoding="utf-8")
    stamp(LAST_CONTACT, f"{now} from={ip} {route}")
    if previous_contact == 0 or time.time() - previous_contact > AWAY_SECONDS:
        if previous_welcome < previous_contact or time.time() - previous_welcome > AWAY_SECONDS:
            MESSAGE.write_text(
                "Hello VeeDash, welcome back.\n"
                "Connected to the PC editor.\n"
                "Ready to send the staged dashboard.",
                encoding="utf-8",
            )
            stamp(LAST_WELCOME, f"{now} welcomed {ip}")


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
        with LOG_FILE.open("a", encoding="utf-8") as handle:
            handle.write(f"{now} seq={seq} from={self.client_address[0]} {line}\n")
        note_dash_contact(self.client_address[0], "log")
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
        left_canvas = tk.Canvas(left_outer, width=280, highlightthickness=0)
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

        ttk.Label(left, text="Connection").pack(anchor="w")
        ttk.Label(left, textvariable=self.server_address, foreground="#0a7").pack(anchor="w")
        ttk.Label(left, text="Client IP").pack(anchor="w", pady=(6, 0))
        dash_ip_entry = ttk.Entry(left, textvariable=self.dash_client_ip, width=26)
        dash_ip_entry.pack(fill=tk.X, pady=(0, 4))
        dash_ip_entry.bind("<Return>", lambda _e: self.save_network_fields())
        dash_ip_entry.bind("<FocusOut>", lambda _e: self.save_network_fields())
        network_buttons = ttk.Frame(left)
        network_buttons.pack(fill=tk.X, pady=(0, 6))
        ttk.Button(network_buttons, text="Use last", command=self.use_last_client_ip).pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 3))
        ttk.Button(network_buttons, text="Save IP", command=self.save_network_fields).pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(3, 0))
        ttk.Label(left, textvariable=self.info_text, foreground="#06c", wraplength=250).pack(anchor="w", pady=(0, 8))

        ttk.Label(left, text="Layer item").pack(anchor="w")
        self.item_box = ttk.Combobox(left, textvariable=self.selected, values=self.item_keys(), state="readonly", width=26)
        self.item_box.pack(fill=tk.X, pady=(0, 8))
        self.item_box.bind("<<ComboboxSelected>>", lambda _e: self.load_selected())

        ttk.Label(left, text="Display mode").pack(anchor="w")
        self.mode_box = ttk.Combobox(left, textvariable=self.mode, values=["number", "graph", "both", "ring", "bar"], state="readonly")
        self.mode_box.pack(fill=tk.X, pady=(0, 8))
        self.mode_box.bind("<<ComboboxSelected>>", lambda _e: self.changed())

        for name in ("x", "y"):
            self.vars[name] = tk.DoubleVar()
        for name, label, lo, hi in [("size", "SIZE", 0.08, 0.40), ("layer", "LAYER", 0, 100)]:
            ttk.Label(left, text=label).pack(anchor="w")
            var = tk.DoubleVar()
            self.vars[name] = var
            ttk.Scale(left, from_=lo, to=hi, variable=var, command=lambda _v: self.changed()).pack(fill=tk.X)

        self.visible = tk.BooleanVar(value=True)
        ttk.Checkbutton(left, text="Visible", variable=self.visible, command=self.changed).pack(anchor="w", pady=8)

        ttk.Separator(left).pack(fill=tk.X, pady=10)
        ttk.Label(left, text="Dial response").pack(anchor="w")
        self.reactive_grow = tk.BooleanVar(value=False)
        self.reactive_tint = tk.BooleanVar(value=False)
        ttk.Checkbutton(left, text="Grow with value", variable=self.reactive_grow, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(left, text="Tint by thresholds", variable=self.reactive_tint, command=self.changed).pack(anchor="w")
        for name, label, lo, hi in [
            ("valueMin", "Low/min value", 0, 8000),
            ("valueMax", "High/max value", 1, 8000),
            ("scaleMax", "Max grow size", 1.0, 1.8),
            ("midAt", "Mid tint starts", 0, 8000),
            ("highAt", "High tint starts", 0, 8000),
        ]:
            var = tk.DoubleVar()
            self.reactive_vars[name] = var
            row = ttk.Frame(left)
            row.pack(fill=tk.X, pady=(2, 0))
            ttk.Label(row, text=label).pack(side=tk.LEFT, anchor="w")
            entry = ttk.Entry(row, textvariable=var, width=8)
            entry.pack(side=tk.RIGHT)
            entry.bind("<Return>", lambda _e: self.changed())
            entry.bind("<FocusOut>", lambda _e: self.changed())
            ttk.Scale(left, from_=lo, to=hi, variable=var, command=lambda _v: self.changed()).pack(fill=tk.X)
        ttk.Button(left, text="Low tint color", command=lambda: self.pick_reactive_color("lowColor")).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Mid tint color", command=lambda: self.pick_reactive_color("midColor")).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="High tint color", command=lambda: self.pick_reactive_color("highColor")).pack(fill=tk.X, pady=2)

        self.show_log = tk.BooleanVar(value=self.data.get("showLog", True))
        self.show_chat = tk.BooleanVar(value=self.data.get("showChat", True))
        self.chat_popup_seconds = tk.DoubleVar(value=float(self.data.get("chatPopupSeconds", 6.5)))
        self.auto_reconnect = tk.BooleanVar(value=self.data.get("autoReconnect", True))
        self.auto_dim = tk.BooleanVar(value=self.data.get("autoDim", True))
        ttk.Checkbutton(left, text="Show debug log", variable=self.show_log, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(left, text="Show chat box", variable=self.show_chat, command=self.changed).pack(anchor="w")
        ttk.Label(left, text="Chat popup seconds").pack(anchor="w")
        ttk.Scale(left, from_=2.0, to=15.0, variable=self.chat_popup_seconds, command=lambda _v: self.changed()).pack(fill=tk.X)
        ttk.Checkbutton(left, text="Auto reconnect", variable=self.auto_reconnect, command=self.changed).pack(anchor="w")
        ttk.Checkbutton(left, text="Auto dim with lights", variable=self.auto_dim, command=self.changed).pack(anchor="w")

        ttk.Separator(left).pack(fill=tk.X, pady=10)
        ttk.Button(left, text="Motion GIF / image background", command=self.pick_background).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Clear image background", command=self.clear_background).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Selected dial image/GIF", command=self.pick_gauge_image).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(left, text="Clear selected dial image", command=self.clear_gauge_image).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Accent color", command=lambda: self.pick_color("accentColor")).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(left, text="Background color", command=lambda: self.pick_color("backgroundColor")).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Gauge fill color", command=lambda: self.pick_color("gaugeFillColor")).pack(fill=tk.X, pady=2)

        ttk.Separator(left).pack(fill=tk.X, pady=10)
        ttk.Label(left, text="Transparency").pack(anchor="w")
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
            ttk.Label(left, text=label).pack(anchor="w")
            var = tk.DoubleVar(value=float(self.data.get(key, DEFAULT[key])))
            self.alpha_vars[key] = var
            ttk.Scale(left, from_=0.0, to=1.0, variable=var, command=lambda _v: self.alpha_changed()).pack(fill=tk.X)

        ttk.Separator(left).pack(fill=tk.X, pady=10)
        ttk.Button(left, text="Bring forward", command=lambda: self.bump_layer(5)).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Send backward", command=lambda: self.bump_layer(-5)).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Reset default layout", command=self.reset_default).pack(fill=tk.X, pady=(10, 2))
        ttk.Button(left, text="Push dash now", command=self.push_dash_now).pack(fill=tk.X, pady=2)

        ttk.Label(left, text="Dash chat message").pack(anchor="w", pady=(12, 2))
        self.message = tk.Text(left, height=4, width=30)
        self.message.pack(fill=tk.X)
        if MESSAGE.exists():
            self.message.insert("1.0", MESSAGE.read_text(encoding="utf-8", errors="replace"))
        ttk.Button(left, text="Send chat", command=self.save_message).pack(fill=tk.X, pady=4)

        ttk.Separator(left).pack(fill=tk.X, pady=10)
        ttk.Label(left, text="Run local program").pack(anchor="w")
        self.command_text = tk.Text(left, height=3, width=30)
        self.command_text.pack(fill=tk.X)
        self.command_text.insert("1.0", "python --version")
        ttk.Button(left, text="Run command", command=self.run_command).pack(fill=tk.X, pady=4)
        self.command_output = tk.Text(left, height=6, width=30)
        self.command_output.pack(fill=tk.X)
        self.command_output.insert("1.0", "Command output will appear here.")

        self.canvas = tk.Canvas(right, width=800, height=480, bg="#111111", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True)
        self.canvas.bind("<ButtonPress-1>", self.start_drag)
        self.canvas.bind("<B1-Motion>", self.drag)
        self.canvas.bind("<ButtonRelease-1>", self.end_drag)
        ttk.Label(right, text="Drag items to move. Drag the small white corner handle to resize. Dial images/GIFs are staged to the dash and clipped to fill the whole dial.").pack(anchor="w")

    def item_keys(self):
        return GAUGE_KEYS + ["chat", "log"]

    def item(self, key=None):
        key = key or self.selected.get()
        for g in self.data["gauges"]:
            if g["key"] == key:
                return g, "gauge"
        for o in self.data["overlays"]:
            if o["key"] == key:
                return o, "overlay"
        return self.data["gauges"][0], "gauge"

    def load_selected(self):
        item, kind = self.item()
        self.mode_box.configure(state="readonly" if kind == "gauge" else "disabled")
        self.mode.set(item.get("mode", "number") if kind == "gauge" else "number")
        self.vars["x"].set(item.get("x", 0.5))
        self.vars["y"].set(item.get("y", 0.5))
        self.vars["size"].set(item.get("size", item.get("w", 0.22)))
        self.vars["layer"].set(item.get("layer", 20))
        self.visible.set(item.get("visible", True))
        reactive = item.get("reactive", {}) if kind == "gauge" else {}
        self.reactive_grow.set(bool(reactive.get("grow", False)))
        self.reactive_tint.set(bool(reactive.get("tint", False)))
        for key, var in self.reactive_vars.items():
            var.set(float(reactive.get(key, DEFAULT["gauges"][0]["reactive"].get(key, 0))))
        self.draw_preview()

    def changed(self):
        item, kind = self.item()
        item["x"] = round(self.vars["x"].get(), 3)
        item["y"] = round(self.vars["y"].get(), 3)
        item["visible"] = bool(self.visible.get())
        item["layer"] = int(round(self.vars["layer"].get()))
        if kind == "gauge":
            item["size"] = round(self.vars["size"].get(), 3)
            item["mode"] = self.mode.get()
            reactive = item.setdefault("reactive", {})
            reactive["grow"] = bool(self.reactive_grow.get())
            reactive["tint"] = bool(self.reactive_tint.get())
            for key, var in self.reactive_vars.items():
                reactive[key] = round(float(var.get()), 2)
        else:
            item["w"] = round(self.vars["size"].get(), 3)
            item["h"] = round(max(0.10, self.vars["size"].get() * 0.55), 3)
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
        item["layer"] = int(item.get("layer", 20)) + delta
        self.vars["layer"].set(item["layer"])
        self.save_all(silent=True)
        self.draw_preview()

    def save_message(self):
        MESSAGE.write_text(self.message.get("1.0", "end").strip(), encoding="utf-8")

    def run_command(self):
        command = self.command_text.get("1.0", "end").strip()
        if not command:
            self.set_command_output("No command entered.")
            return
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
        self.after(0, lambda: self.set_command_output(text))

    def set_command_output(self, text):
        self.command_output.delete("1.0", "end")
        self.command_output.insert("1.0", text)

    def save_all(self, silent=False):
        self.data["dashClientIp"] = self.dash_client_ip.get().strip()
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
        self.after(80, self.tick)

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
            elif item["key"] == "chat" and self.data.get("showChat", True):
                self.draw_box(item, w, h, "STATUS\nConnected\n\nCHAT\nMessage from PC", chat_fill)
            elif item["key"] == "log" and self.data.get("showLog", True):
                self.draw_box(item, w, h, "DEBUG STATUS\nphase: connected\nrpm: 820\nvolts: 14.2", log_fill)

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
        outline = "#ffffff" if item["key"] == self.selected.get() else "#345"
        self.canvas.create_rectangle(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2, fill=color, outline=outline, width=2)
        self.canvas.create_text(cx - iw / 2 + 10, cy - ih / 2 + 8, text=text, anchor="nw", fill="white", font=("Segoe UI", 9, "bold"))
        if item["key"] == self.selected.get():
            hx, hy = self.resize_handle(item, "overlay", w, h)
            self.canvas.create_rectangle(hx - 6, hy - 6, hx + 6, hy + 6, fill="#ffffff", outline="#111111")

    def draw_gauge(self, g, w, h, accent, fill):
        value = SAMPLE.get(g["key"], 0)
        reactive = g.get("reactive", {})
        scale = self.reactive_scale(reactive, value)
        tint = self.reactive_tint_color(reactive, value)
        r = g.get("size", 0.22) * min(w, h) * scale
        x = g.get("x", 0.5) * w
        y = g.get("y", 0.5) * h
        selected = g["key"] == self.selected.get()
        width = 5 if selected else 3
        mode = g.get("mode", "number")
        if mode in ("number", "both", "ring") or g.get("imageAsset"):
            self.canvas.create_oval(x-r, y-r, x+r, y+r, fill=fill, outline=accent, width=width)
            dial_image = self.dial_preview_image(g, int(max(8, r * 2)))
            if dial_image:
                self.canvas.create_image(x, y, image=dial_image)
            if tint:
                self.canvas.create_oval(x-r, y-r, x+r, y+r, fill=tint, outline="", stipple="gray25")
        if mode in ("graph", "both"):
            self.draw_graph(x, y, r, accent)
        if mode == "bar":
            self.draw_bar(x, y, r, accent, fill, value)
        if mode in ("number", "both", "ring", "bar"):
            self.canvas.create_text(x, y - r * 0.06, text=f"{value:.0f}", fill="white", font=("Segoe UI", int(max(14, r * 0.32)), "bold"))
        self.canvas.create_text(x, y + r * 0.38, text=g["key"].upper(), fill="#cfefff", font=("Segoe UI", int(max(9, r * 0.13)), "bold"))
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

    def draw_graph(self, x, y, r, accent):
        points = []
        for i in range(34):
            px = x - r * 0.75 + i * (r * 1.5 / 33)
            py = y + math.sin(i * 0.55 + time.time() * 2.0) * r * 0.22
            points.extend([px, py])
        self.canvas.create_rectangle(x-r*0.78, y-r*0.38, x+r*0.78, y+r*0.38, outline="#28455a", fill="#061016")
        self.canvas.create_line(points, fill=accent, width=3, smooth=True)

    def draw_bar(self, x, y, r, accent, fill, value):
        self.canvas.create_rectangle(x-r, y-r*0.30, x+r, y+r*0.30, fill=fill, outline=accent, width=3)
        pct = max(0.0, min(1.0, value / 100.0))
        self.canvas.create_rectangle(x-r, y-r*0.30, x-r + pct * r * 2, y+r*0.30, fill=accent, outline="")


if __name__ == "__main__":
    app = Editor()
    app.bind("<Configure>", lambda _e: app.draw_preview())
    app.mainloop()
