# A05s Input Bridge

A completely local keyboard and mouse for the Samsung Galaxy A05s. The phone
does not need Wi-Fi, mobile data, an account, or a cloud service.

The landscape-only interface uses a compact neo-brutalist keyboard. The full
keyboard occupies the canvas; **TRACKPAD ▾** opens the mouse surface from the
top-left without permanently taking space away from the keys. Shift, Caps, Ctrl,
Alt, and Win/Super are functional. Tap a modifier and then a key for a chord;
the active chord is shown beside the app title. Hold Win/Super to send the
Windows key by itself; a normal tap only latches it for a chord.

Every control has visible input feedback: touch/click depresses the face into
its dark shadow, hover/focus lifts it, and phone touches produce haptic feedback.
**SET** opens persistent controls for drag-hold delay, pointer speed, scroll
speed, Backspace/key repeat speed, and haptics. The default drag hold is 500 ms.

Shifted symbols are printed on their keys (`!`, `@`, `#`, and the rest). **F KEYS**
opens F1–F12 plus Print Screen, Scroll Lock, Pause, Insert, Delete, Page Up/Down,
Home, and End. Holding Backspace repeats deletion. On the trackpad, hold then
move—or double-tap and keep the second touch down—to drag. A dedicated vertical
scroll pad provides one-finger scrolling with haptic ticks; two-finger scrolling
on the main pad remains available. The trackpad card's dock button moves it
between the left and right edges.

## Bluetooth mode

Bluetooth mode uses Android's native HID Device profile, so Windows sees the
phone as an ordinary keyboard and mouse. No Windows companion is required.
Volume and mute also work directly over Bluetooth. Laptop brightness and the
live PC statistics use the optional USB cable helper described below because
Bluetooth HID has no return-data channel.

1. Open **A05s Input** on the phone and allow Nearby devices.
2. Tap **Make phone visible**, then pair `Galaxy A05s` in Windows Bluetooth settings.
3. Back in the app, tap the button for the paired computer and select **Bluetooth**.

`windows/Pair-Bluetooth.ps1` is an optional local Windows pairing helper. Run it
while the phone is visible if the normal Add device screen does not find the phone.

Keep the app in the foreground while using the phone as a trackpad.

## System controls and PC monitor

**SYSTEM** opens volume down/mute/up and brightness down/up controls. With
`windows/Start-UsbInput.ps1` running, the top bar and System card update with
Windows CPU load, system-disk usage, CPU temperature when Windows exposes a
sensor, and laptop battery/charging state. The helper talks only over the ADB
USB loopback tunnel; the phone still does not use Wi-Fi. It can stay running
while the app's active input mode is **Bluetooth**.

The launcher icon uses the same mint, cream, coral, heavy-outline, and offset-
shadow visual language as the app.

## USB mode

USB mode sends a tiny line-based protocol through `adb forward`. Its TCP sockets
exist only on each device's loopback interface; the cable carries the data and
the phone never joins a network.

1. Keep USB debugging enabled and connect the cable.
2. Open **A05s Input** and select **USB**.
3. Run `windows/Start-UsbInput.ps1` on Windows. Press Ctrl+C to stop it. The same
   helper also enables PC statistics and reliable laptop-panel brightness in
   Bluetooth mode.

Run `python windows/usb_input_host.py --check` to verify the cable path without
injecting any keyboard or mouse events.

## Build

This project intentionally uses only Android platform APIs and the Python
standard library, so its runtime has no third-party dependencies.
