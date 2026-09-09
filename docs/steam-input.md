# Steam Input setup

GlassHID presents the phone to Windows as a standard Bluetooth DirectInput
gamepad. Steam supports DirectInput controllers, but Windows must first expose
the gamepad collection and Steam may need to be restarted after it appears.

## 1. Verify Windows before opening Steam

1. On the phone, open **GlassHID → GAMEPAD** and confirm the header says
   **BT · INPUT CONNECTED ✓**.
2. If this is the first build with controller support, remove **Galaxy A05s**
   from Windows Bluetooth settings and pair it again. Windows caches the old
   keyboard-only HID descriptor.
3. Press **Win+R**, enter `joy.cpl`, and open **HID-compliant game controller →
   Properties**.
4. Check the controls:
   - L3 pad: X/Y axes
   - R3 pad: Z/Rz axes
   - D-pad cross: POV hat
   - Face and shoulder controls: numbered buttons

The blue L3/R3 cap moves with the reported axis and returns to center. Active
D-pad arms turn yellow, including two directions for a diagonal. This
makes it easy to tell whether a problem is in GlassHID/Windows or only in the
Steam configuration.

## 2. Enable it in Steam

1. Connect GlassHID before starting Steam. If Steam was already open, choose
   **Steam → Exit**, then reopen it.
2. Open **Steam → Settings → Controller**. Find the generic controller and use
   **Test Device Inputs** when that action is available.
3. In the Library, open the game's **Properties → Controller** and set its
   Steam Input override to **Enable Steam Input**.
4. Open the game's **Controller Layout**, choose **Templates → Gamepad**, then
   apply the layout.

Steam's wording moves occasionally between desktop and Big Picture mode, but
the sequence is the same: verify the device, enable Steam Input for the game,
and apply the Gamepad template. Valve documents DirectInput gamepads as Steam
Input devices and describes Steam Input as the layer that can translate a
controller into the input a game expects.

## GlassHID button map

Open **SET → CONTROLLER LABELS** from the keyboard screen to cycle between
PlayStation, ABXY, and numbered labels. This changes the face buttons and the
Select/PS/Start names together; the HID button numbers stay stable.

| HID input | PS label | Xbox-style label | Number label |
| --- | --- | --- | --- |
| Button 1 | Cross | A | 1 |
| Button 2 | Circle | B | 2 |
| Button 3 | Square | X | 3 |
| Button 4 | Triangle | Y | 4 |
| Button 5 | L1 | LB | 5 |
| Button 6 | R1 | RB | 6 |
| Button 7 | L2 | LT (digital) | 7 |
| Button 8 | R2 | RT (digital) | 8 |
| Button 9 | Select | View/Back | 9 |
| Button 10 | Start | Menu/Start | 10 |
| Button 11 | L3 click | Left-stick click | 11 |
| Button 12 | R3 click | Right-stick click | 12 |
| Button 13 | PS | Guide/Home | 13 |

When Steam asks you to identify inputs, switch the phone to **CONTROLLER LABELS · ABXY**
and use this order:

- A = bottom, B = right, X = left, Y = top
- D-pad = the four arms of the cross
- Left/Right Stick = L3/R3 pads; tap the cap for the stick click
- Back = Select, Start = Start, Guide = PS

## If Steam still shows no controller

- Re-check `joy.cpl`. If it is absent there, remove/re-pair the phone; Steam
  cannot fix a missing Windows gamepad collection.
- Keep GlassHID in the foreground and confirm **INPUT CONNECTED ✓**, not merely
  Windows' Bluetooth **Connected** label.
- Exit Steam completely and launch it again after the phone is connected.
- Try the per-game **Enable Steam Input** override and **Gamepad** template.
- If the game reads DirectInput itself, also try **Disable Steam Input** for
  that one game. Do not enable two remapping layers at once.

Official references: [Steam Input overview](https://partner.steamgames.com/doc/features/steam_controller),
[supported devices](https://partner.steamgames.com/doc/features/steam_controller/device), and
[Steam Input concepts](https://partner.steamgames.com/doc/features/steam_controller/concepts).
