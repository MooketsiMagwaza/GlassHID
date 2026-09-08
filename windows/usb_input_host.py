#!/usr/bin/env python3
"""Receive A05s input events through an adb USB port-forward and inject them on Windows."""

from __future__ import annotations

import argparse
import base64
import ctypes
from ctypes import wintypes
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

PORT = 27183
DEFAULT_ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
INPUT_KEYBOARD = 1

VK = {
    "BACKSPACE": 0x08, "TAB": 0x09, "ENTER": 0x0D, "ESC": 0x1B,
    "SPACE": 0x20, "PGUP": 0x21, "PGDN": 0x22, "END": 0x23,
    "HOME": 0x24, "LEFT": 0x25, "UP": 0x26, "RIGHT": 0x27,
    "DOWN": 0x28, "PRINTSCREEN": 0x2C, "INSERT": 0x2D, "DELETE": 0x2E,
    "WIN": 0x5B,
    "VOLUME_MUTE": 0xAD, "VOLUME_DOWN": 0xAE, "VOLUME_UP": 0xAF,
    "PAUSE": 0x13, "SCROLLLOCK": 0x91,
    "F1": 0x70, "F2": 0x71, "F3": 0x72, "F4": 0x73,
    "F5": 0x74, "F6": 0x75, "F7": 0x76, "F8": 0x77,
    "F9": 0x78, "F10": 0x79, "F11": 0x7A, "F12": 0x7B,
}
MODIFIER_VK = {"CTRL": 0x11, "SHIFT": 0x10, "ALT": 0x12, "WIN": 0x5B}


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class INPUT_UNION(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", INPUT_UNION)]


class FILETIME(ctypes.Structure):
    _fields_ = [("low", wintypes.DWORD), ("high", wintypes.DWORD)]


class SYSTEM_POWER_STATUS(ctypes.Structure):
    _fields_ = [
        ("ACLineStatus", ctypes.c_ubyte),
        ("BatteryFlag", ctypes.c_ubyte),
        ("BatteryLifePercent", ctypes.c_ubyte),
        ("SystemStatusFlag", ctypes.c_ubyte),
        ("BatteryLifeTime", wintypes.DWORD),
        ("BatteryFullLifeTime", wintypes.DWORD),
    ]


user32 = ctypes.windll.user32


def _filetime_value(value: FILETIME) -> int:
    return (value.high << 32) | value.low


class CpuSampler:
    def __init__(self) -> None:
        self.previous = self._read()

    @staticmethod
    def _read() -> tuple[int, int]:
        idle, kernel, user = FILETIME(), FILETIME(), FILETIME()
        if not ctypes.windll.kernel32.GetSystemTimes(
            ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user)
        ):
            return (0, 0)
        return (_filetime_value(idle), _filetime_value(kernel) + _filetime_value(user))

    def percent(self) -> int:
        current = self._read()
        idle_delta = current[0] - self.previous[0]
        total_delta = current[1] - self.previous[1]
        self.previous = current
        if total_delta <= 0:
            return 0
        return max(0, min(100, round(100 * (total_delta - idle_delta) / total_delta)))


def disk_percent() -> int:
    root = Path(os.environ.get("SystemDrive", "C:") + "\\")
    usage = shutil.disk_usage(root)
    return round(100 * (usage.total - usage.free) / usage.total)


def battery_status() -> tuple[int, int]:
    status = SYSTEM_POWER_STATUS()
    if not ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status)):
        return (-1, 0)
    percent = -1 if status.BatteryLifePercent == 255 else int(status.BatteryLifePercent)
    return (percent, 1 if status.ACLineStatus == 1 else 0)


def cpu_temperature() -> int:
    # Prefer hardware-monitor providers when installed, then try the firmware
    # thermal-zone value exposed by Windows. Blank output means unavailable.
    command = r"""
$values = @()
foreach ($namespace in @('root/LibreHardwareMonitor','root/OpenHardwareMonitor')) {
  try {
    $values += Get-CimInstance -Namespace $namespace -ClassName Sensor -ErrorAction Stop |
      Where-Object { $_.SensorType -eq 'Temperature' -and $_.Name -match 'CPU|Package|Core' } |
      Select-Object -ExpandProperty Value
  } catch {}
}
if ($values.Count -eq 0) {
  try {
    $values += Get-CimInstance -Namespace root/wmi -ClassName MSAcpi_ThermalZoneTemperature -ErrorAction Stop |
      ForEach-Object { ($_.CurrentTemperature / 10) - 273.15 }
  } catch {}
}
if ($values.Count -gt 0) { [math]::Round(($values | Measure-Object -Maximum).Maximum) }
"""
    try:
        result = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", command],
            capture_output=True, text=True, timeout=8,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        return round(float(result.stdout.strip())) if result.stdout.strip() else -1
    except (OSError, subprocess.SubprocessError, ValueError):
        return -1


def set_brightness(delta: int) -> None:
    command = f"""
$level = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightness -ErrorAction Stop | Select-Object -First 1
$method = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods -ErrorAction Stop | Select-Object -First 1
$target = [math]::Max(0, [math]::Min(100, [int]$level.CurrentBrightness + ({delta})))
Invoke-CimMethod -InputObject $method -MethodName WmiSetBrightness -Arguments @{{Timeout=0; Brightness=[byte]$target}} | Out-Null
"""
    subprocess.Popen(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", command],
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )


def telemetry_loop(connection: socket.socket, stop: threading.Event) -> None:
    cpu = CpuSampler()
    temperature = -1
    next_temperature = 0.0
    while not stop.wait(1.25):
        try:
            battery, plugged = battery_status()
            line = f"STATS {cpu.percent()} {disk_percent()} {temperature} {battery} {plugged}\n"
            connection.sendall(line.encode("ascii"))
            now = time.monotonic()
            if now >= next_temperature:
                temperature = cpu_temperature()
                next_temperature = now + 15
        except (ConnectionError, OSError):
            return


def send_unicode(text: str) -> None:
    raw = text.encode("utf-16-le")
    for index in range(0, len(raw), 2):
        scan = int.from_bytes(raw[index:index + 2], "little")
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            event = INPUT(type=INPUT_KEYBOARD)
            event.ki = KEYBDINPUT(0, scan, flags, 0, None)
            user32.SendInput(1, ctypes.byref(event), ctypes.sizeof(INPUT))


def send_key(name: str) -> None:
    code = VK.get(name)
    if code is None:
        return
    user32.keybd_event(code, 0, 0, 0)
    user32.keybd_event(code, 0, KEYEVENTF_KEYUP, 0)


def send_hotkey(chord: str) -> None:
    names = chord.split("+")
    if not names:
        return
    modifier_codes = [MODIFIER_VK[name] for name in names[:-1] if name in MODIFIER_VK]
    key_name = names[-1]
    key_code = VK.get(key_name)
    if key_code is None and len(key_name) == 1 and key_name.isalnum():
        key_code = ord(key_name.upper())
    if key_code is None:
        return
    for code in modifier_codes:
        user32.keybd_event(code, 0, 0, 0)
    user32.keybd_event(key_code, 0, 0, 0)
    user32.keybd_event(key_code, 0, KEYEVENTF_KEYUP, 0)
    for code in reversed(modifier_codes):
        user32.keybd_event(code, 0, KEYEVENTF_KEYUP, 0)


def handle(line: str, dry_run: bool) -> None:
    parts = line.strip().split()
    if not parts:
        return
    if dry_run:
        print(f"received: {line.strip()}")
        return
    command = parts[0]
    if command == "MOVE" and len(parts) == 3:
        user32.mouse_event(MOUSEEVENTF_MOVE, int(parts[1]), int(parts[2]), 0, 0)
    elif command == "SCROLL" and len(parts) == 2:
        user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, int(parts[1]) * 120, 0)
    elif command == "CLICK" and len(parts) == 2:
        if parts[1] == "LEFT":
            user32.mouse_event(MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)
        elif parts[1] == "RIGHT":
            user32.mouse_event(MOUSEEVENTF_RIGHTDOWN | MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0)
    elif command == "BUTTON" and len(parts) == 3:
        flags = {
            ("LEFT", "DOWN"): MOUSEEVENTF_LEFTDOWN,
            ("LEFT", "UP"): MOUSEEVENTF_LEFTUP,
            ("RIGHT", "DOWN"): MOUSEEVENTF_RIGHTDOWN,
            ("RIGHT", "UP"): MOUSEEVENTF_RIGHTUP,
        }.get((parts[1], parts[2]))
        if flags is not None:
            user32.mouse_event(flags, 0, 0, 0, 0)
    elif command == "TEXT" and len(parts) == 2:
        send_unicode(base64.b64decode(parts[1]).decode("utf-8"))
    elif command == "KEY" and len(parts) == 2:
        send_key(parts[1])
    elif command == "HOTKEY" and len(parts) == 2:
        send_hotkey(parts[1])
    elif command == "MEDIA" and len(parts) == 2:
        print(f"System control: {parts[1]}", flush=True)
        if parts[1] == "BRIGHTNESS_DOWN":
            set_brightness(-10)
        elif parts[1] == "BRIGHTNESS_UP":
            set_brightness(10)
        else:
            send_key(parts[1])


def configure_adb(adb: Path) -> None:
    if not adb.exists():
        raise FileNotFoundError(f"adb not found: {adb}")
    subprocess.run([str(adb), "start-server"], check=True, capture_output=True)
    devices = subprocess.run(
        [str(adb), "devices"], check=True, capture_output=True, text=True
    ).stdout
    if "\tdevice" not in devices:
        raise RuntimeError("No authorized Android device is connected over USB.")
    subprocess.run(
        [str(adb), "forward", f"tcp:{PORT}", f"tcp:{PORT}"],
        check=True, capture_output=True,
    )
    subprocess.run(
        [str(adb), "shell", "am", "start", "-n", "com.nido.a05sinput/.MainActivity"],
        check=True, capture_output=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, default=DEFAULT_ADB)
    parser.add_argument("--check", action="store_true", help="Verify the USB link without injecting input")
    args = parser.parse_args()

    configure_adb(args.adb)
    print("A05s local input and PC telemetry helper ready. Press Ctrl+C to stop.")
    while True:
        try:
            with socket.create_connection(("127.0.0.1", PORT), timeout=3) as connection:
                connection.settimeout(None)
                with connection.makefile("r", encoding="utf-8") as incoming:
                    hello = incoming.readline().strip()
                    if hello != "HELLO A05S_INPUT 1":
                        raise ConnectionError(f"Unexpected phone response: {hello!r}")
                    print("Phone connected over USB; no Wi-Fi is in use.")
                    if args.check:
                        return 0
                    telemetry_stop = threading.Event()
                    telemetry = threading.Thread(
                        target=telemetry_loop,
                        args=(connection, telemetry_stop),
                        daemon=True,
                    )
                    telemetry.start()
                    try:
                        for line in incoming:
                            handle(line, dry_run=False)
                    finally:
                        telemetry_stop.set()
        except (ConnectionError, OSError) as error:
            print(f"Waiting for phone app: {error}", file=sys.stderr)
            time.sleep(1)
        except KeyboardInterrupt:
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
