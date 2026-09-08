package com.nido.a05sinput;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PORT = 27183;
    private static final int REQUEST_BT = 100;
    private static final int MODE_OFF = 0;
    private static final int MODE_BLUETOOTH = 1;
    private static final int MODE_USB = 2;
    private static final int INK = Color.rgb(24, 24, 24);
    private static final int PAPER = Color.rgb(247, 243, 234);
    private static final int GREEN = Color.rgb(139, 214, 170);
    private static final int YELLOW = Color.rgb(250, 204, 80);
    private static final int CORAL = Color.rgb(255, 126, 103);
    private static final int BLUE = Color.rgb(139, 188, 255);

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final ExecutorService usbWriter = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<UsbClient> usbClients = new CopyOnWriteArrayList<>();
    private TextView status;
    private TextView systemStatsView;
    private LinearLayout pairedDevices;
    private volatile int mode = MODE_OFF;
    private volatile String pcStats = "LAPTOP BAT —";
    private ServerSocket usbServer;

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice hidHost;
    private boolean hidRegistered;
    private boolean hidBinding;
    private boolean reconnectScheduled;
    private boolean destroyed;
    private boolean shiftOn;
    private boolean capsOn;
    private boolean ctrlOn;
    private boolean altOn;
    private boolean winOn;
    private long nextHidKeyAt;
    private int mouseButtons;
    private boolean trackpadOnRight;
    private PopupWindow trackpadPopup;
    private PopupWindow toolsPopup;
    private PopupWindow systemPopup;
    private PopupWindow settingsPopup;
    private final Map<String, TextView> settingValueViews = new HashMap<>();
    private int dragHoldMs = 500;
    private int pointerPercent = 100;
    private int scrollPercent = 100;
    private int repeatMs = 55;
    private boolean hapticsOn = true;
    private final List<Button> shiftButtons = new ArrayList<>();
    private final List<Button> capsButtons = new ArrayList<>();
    private final List<Button> ctrlButtons = new ArrayList<>();
    private final List<Button> altButtons = new ArrayList<>();
    private final List<Button> winButtons = new ArrayList<>();

    private static final byte[] HID_DESCRIPTOR = hex(
            // Keyboard, report 1: modifiers + six simultaneous keys.
            "05010906A1018501050719E029E715002501750195088102" +
            "95017508810195067508150025650507190029658100C0" +
            // Mouse, report 2: five buttons + relative X/Y + wheel.
            "05010902A10185020901A100050919012905150025019505" +
            "7501810295017503810105010930093109381581257F7508" +
            "95038106C0C0" +
            // Consumer control, report 3: volume, mute, and display brightness.
            "050C0901A1018503150026FF0319002AFF03751095018100C0");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        adapter = BluetoothAdapter.getDefaultAdapter();
        loadSettings();
        setContentView(buildUi());
        startUsbServer();
        requestBluetoothPermission();
    }

    private View buildUi() {
        int pad = dp(8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(PAPER);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button trackpadMenu = neoButton("TRACKPAD ▾", GREEN);
        trackpadMenu.setOnClickListener(this::showTrackpadPopup);
        top.addView(trackpadMenu, new LinearLayout.LayoutParams(dp(116), dp(54)));

        Button toolsMenu = neoButton("TOOLS ▾", YELLOW);
        toolsMenu.setOnClickListener(this::showToolsPopup);
        top.addView(toolsMenu, new LinearLayout.LayoutParams(dp(96), dp(54)));

        Button settingsMenu = neoButton("SET", PAPER);
        settingsMenu.setOnClickListener(this::showSettingsPopup);
        top.addView(settingsMenu, new LinearLayout.LayoutParams(dp(60), dp(54)));

        TextView title = text("A05s", 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(dp(70), dp(54)));

        status = text("Starting local input services…", 11);
        status.setTextSize(10);
        status.setTextColor(INK);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setMaxLines(2);
        top.addView(status, new LinearLayout.LayoutParams(0, dp(54), 1));

        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton off = radio("Off", MODE_OFF);
        RadioButton bluetooth = radio("BT", MODE_BLUETOOTH);
        RadioButton usb = radio("USB", MODE_USB);
        modes.addView(off);
        modes.addView(bluetooth);
        modes.addView(usb);
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            mode = checkedId;
            getSharedPreferences("controls", MODE_PRIVATE).edit()
                    .putInt("active_mode", mode).apply();
            if (mode == MODE_BLUETOOTH) connectPreferredHost();
            updateStatus();
        });
        if (mode == MODE_BLUETOOTH) bluetooth.setChecked(true);
        else if (mode == MODE_USB) usb.setChecked(true);
        else off.setChecked(true);
        top.addView(modes, new LinearLayout.LayoutParams(dp(165), dp(54)));

        Button pair = neoButton("PAIR", BLUE);
        pair.setOnClickListener(this::showBluetoothPopup);
        top.addView(pair, new LinearLayout.LayoutParams(dp(64), dp(54)));
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(0, dp(5), 0, 0);

        LinearLayout r1 = keyboardRow();
        addSpecialKey(r1, "Esc", "ESC", 0x29, 1.1f, CORAL);
        addPrintableKey(r1, "1\n!", "1", "!", 1); addPrintableKey(r1, "2\n@", "2", "@", 1);
        addPrintableKey(r1, "3\n#", "3", "#", 1); addPrintableKey(r1, "4\n$", "4", "$", 1);
        addPrintableKey(r1, "5\n%", "5", "%", 1); addPrintableKey(r1, "6\n^", "6", "^", 1);
        addPrintableKey(r1, "7\n&", "7", "&", 1); addPrintableKey(r1, "8\n*", "8", "*", 1);
        addPrintableKey(r1, "9\n(", "9", "(", 1); addPrintableKey(r1, "0\n)", "0", ")", 1);
        addPrintableKey(r1, "−\n_", "-", "_", 1); addPrintableKey(r1, "=\n+", "=", "+", 1);
        addRepeatingSpecialKey(r1, "Back", "BACKSPACE", 0x2A, 1.65f, CORAL);

        LinearLayout r2 = keyboardRow();
        addSpecialKey(r2, "Tab", "TAB", 0x2B, 1.4f, BLUE);
        for (String letter : new String[]{"Q","W","E","R","T","Y","U","I","O","P"}) addLetterKey(r2, letter);
        addPrintableKey(r2, "[\n{", "[", "{", 1); addPrintableKey(r2, "]\n}", "]", "}", 1);
        addPrintableKey(r2, "\\\n|", "\\", "|", 1.3f);

        LinearLayout r3 = keyboardRow();
        addModifierKey(r3, "Caps", "CAPS", 1.65f);
        for (String letter : new String[]{"A","S","D","F","G","H","J","K","L"}) addLetterKey(r3, letter);
        addPrintableKey(r3, ";\n:", ";", ":", 1); addPrintableKey(r3, "'\n\"", "'", "\"", 1);
        addSpecialKey(r3, "Enter", "ENTER", 0x28, 2.05f, YELLOW);

        LinearLayout r4 = keyboardRow();
        addModifierKey(r4, "Shift", "SHIFT", 1.2f);
        for (String letter : new String[]{"Z","X","C","V","B","N","M"}) addLetterKey(r4, letter);
        addPrintableKey(r4, ",\n<", ",", "<", 1); addPrintableKey(r4, ".\n>", ".", ">", 1);
        addPrintableKey(r4, "/\n?", "/", "?", 1);
        addSpecialKey(r4, "↑", "UP", 0x52, 1, BLUE);
        addModifierKey(r4, "Shift", "SHIFT", 2.1f);

        LinearLayout r5 = keyboardRow();
        addModifierKey(r5, "Ctrl", "CTRL", 1.4f);
        addModifierKey(r5, "Win\nSuper", "WIN", 1.45f);
        addModifierKey(r5, "Alt", "ALT", 1.25f);
        addSpecialKey(r5, "Home", "HOME", 0x4A, 1.25f, BLUE);
        addSpecialKey(r5, "Space", "SPACE", 0x2C, 6.2f, YELLOW);
        addSpecialKey(r5, "End", "END", 0x4D, 1.25f, BLUE);
        addSpecialKey(r5, "←", "LEFT", 0x50, 1, BLUE);
        addSpecialKey(r5, "↓", "DOWN", 0x51, 1, BLUE);
        addSpecialKey(r5, "→", "RIGHT", 0x4F, 1, BLUE);

        keyboard.addView(r1, rowParams()); keyboard.addView(r2, rowParams());
        keyboard.addView(r3, rowParams()); keyboard.addView(r4, rowParams());
        keyboard.addView(r5, rowParams());
        root.addView(keyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void showTrackpadPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(GREEN));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView dockLabel = text(trackpadOnRight ? "RIGHT DOCK" : "LEFT DOCK", 12);
        dockLabel.setTypeface(Typeface.DEFAULT_BOLD);
        dockLabel.setTextColor(INK);
        header.addView(dockLabel, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button moveSide = neoButton(trackpadOnRight ? "← LEFT" : "RIGHT →", BLUE);
        moveSide.setOnClickListener(v -> {
            trackpadOnRight = !trackpadOnRight;
            if (trackpadPopup != null) trackpadPopup.dismiss();
            showTrackpadPopup(anchor);
        });
        header.addView(moveSide, new LinearLayout.LayoutParams(dp(105), dp(42)));
        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout surfaces = new LinearLayout(this);
        surfaces.setOrientation(LinearLayout.HORIZONTAL);

        TextView trackpad = text("TRACKPAD\nTap · hold/double-tap drag", 15);
        trackpad.setTypeface(Typeface.DEFAULT_BOLD);
        trackpad.setGravity(Gravity.CENTER);
        trackpad.setTextColor(PAPER);
        trackpad.setBackground(rounded(INK));
        trackpad.setOnTouchListener(new TrackpadListener());
        trackpad.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.animate().scaleX(1.015f).scaleY(1.015f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
        surfaces.addView(trackpad, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        TextView scrollPad = text("SCROLL\n▲\n↕\n▼", 12);
        scrollPad.setTypeface(Typeface.DEFAULT_BOLD);
        scrollPad.setGravity(Gravity.CENTER);
        scrollPad.setTextColor(INK);
        scrollPad.setBackground(rounded(BLUE));
        scrollPad.setOnTouchListener(new ScrollPadListener());
        scrollPad.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.animate().scaleX(1.025f).scaleY(1.015f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
        LinearLayout.LayoutParams scrollPadParams = new LinearLayout.LayoutParams(
                dp(62), LinearLayout.LayoutParams.MATCH_PARENT);
        scrollPadParams.setMargins(dp(6), 0, 0, 0);
        surfaces.addView(scrollPad, scrollPadParams);

        card.addView(surfaces, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout clicks = new LinearLayout(this);
        Button left = neoButton("LEFT", YELLOW);
        left.setOnClickListener(v -> click("left"));
        Button right = neoButton("RIGHT", CORAL);
        right.setOnClickListener(v -> click("right"));
        clicks.addView(left, weighted()); clicks.addView(right, weighted());
        card.addView(clicks, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        trackpadPopup = new PopupWindow(card, dp(338), dp(285), true);
        trackpadPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        trackpadPopup.setOutsideTouchable(true);
        trackpadPopup.setElevation(dp(12));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int xOffset = trackpadOnRight
                ? getResources().getDisplayMetrics().widthPixels - dp(338) - location[0]
                : 0;
        trackpadPopup.showAsDropDown(anchor, xOffset, dp(4));
    }

    private void showSystemPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(BLUE));

        systemStatsView = text(pcStats, 13);
        systemStatsView.setTypeface(Typeface.DEFAULT_BOLD);
        systemStatsView.setTextColor(INK);
        systemStatsView.setGravity(Gravity.CENTER);
        systemStatsView.setMaxLines(2);
        card.addView(systemStatsView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout volume = keyboardRow();
        addSystemKey(volume, "VOL −", "VOLUME_DOWN", 0xEA, CORAL);
        addSystemKey(volume, "MUTE", "VOLUME_MUTE", 0xE2, YELLOW);
        addSystemKey(volume, "VOL +", "VOLUME_UP", 0xE9, GREEN);
        card.addView(volume, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout brightness = keyboardRow();
        addSystemKey(brightness, "BRIGHT −", "BRIGHTNESS_DOWN", 0x70, CORAL);
        addSystemKey(brightness, "BRIGHT +", "BRIGHTNESS_UP", 0x6F, GREEN);
        card.addView(brightness, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        systemPopup = new PopupWindow(card, dp(310), dp(176), true);
        systemPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        systemPopup.setOutsideTouchable(true);
        systemPopup.setElevation(dp(12));
        systemPopup.setOnDismissListener(() -> systemStatsView = null);
        systemPopup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showToolsPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        Button functions = neoButton("F KEYS + NAV", PAPER);
        functions.setOnClickListener(v -> {
            toolsPopup.dismiss();
            uiHandler.post(() -> showFunctionPopup(anchor));
        });
        card.addView(functions, new LinearLayout.LayoutParams(dp(190), dp(52)));

        Button system = neoButton("SYSTEM", BLUE);
        system.setOnClickListener(v -> {
            toolsPopup.dismiss();
            uiHandler.post(() -> showSystemPopup(anchor));
        });
        card.addView(system, new LinearLayout.LayoutParams(dp(190), dp(52)));

        toolsPopup = new PopupWindow(card, dp(206), dp(120), true);
        toolsPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        toolsPopup.setOutsideTouchable(true);
        toolsPopup.setElevation(dp(12));
        toolsPopup.showAsDropDown(anchor, 0, dp(4));
    }

    private void addSystemKey(LinearLayout row, String label, String name, int usage, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> sendSystemControl(name, usage));
        row.addView(key, weighted());
    }

    private void loadSettings() {
        SharedPreferences values = getSharedPreferences("controls", MODE_PRIVATE);
        mode = values.getInt("active_mode", MODE_OFF);
        dragHoldMs = values.getInt("drag_hold_ms", 500);
        pointerPercent = values.getInt("pointer_percent", 100);
        scrollPercent = values.getInt("scroll_percent", 100);
        repeatMs = values.getInt("repeat_ms", 55);
        hapticsOn = values.getBoolean("haptics", true);
    }

    private void saveSettings() {
        getSharedPreferences("controls", MODE_PRIVATE).edit()
                .putInt("drag_hold_ms", dragHoldMs)
                .putInt("pointer_percent", pointerPercent)
                .putInt("scroll_percent", scrollPercent)
                .putInt("repeat_ms", repeatMs)
                .putBoolean("haptics", hapticsOn)
                .apply();
    }

    private void showSettingsPopup(View anchor) {
        settingValueViews.clear();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        TextView title = text("INPUT SETTINGS", 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(INK);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        card.addView(settingRow("DRAG", "Drag hold"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("POINTER", "Pointer speed"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("SCROLL", "Scroll speed"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("REPEAT", "Key repeat"), new LinearLayout.LayoutParams(-1, dp(46)));

        Button haptics = neoButton("HAPTICS · " + (hapticsOn ? "ON" : "OFF"), GREEN);
        settingValueViews.put("HAPTICS", haptics);
        haptics.setOnClickListener(v -> {
            hapticsOn = !hapticsOn;
            saveSettings();
            refreshSettingValues();
            haptic(v, HapticFeedbackConstants.CLOCK_TICK);
        });
        card.addView(haptics, new LinearLayout.LayoutParams(-1, dp(48)));

        settingsPopup = new PopupWindow(card, dp(315), dp(280), true);
        settingsPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        settingsPopup.setOutsideTouchable(true);
        settingsPopup.setElevation(dp(12));
        settingsPopup.setOnDismissListener(settingValueViews::clear);
        settingsPopup.showAsDropDown(anchor, 0, dp(4));
        refreshSettingValues();
    }

    private LinearLayout settingRow(String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 12);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(INK);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

        Button minus = neoButton("−", CORAL);
        minus.setOnClickListener(v -> adjustSetting(key, -1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(46), dp(42)));

        TextView value = text("", 12);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.CENTER);
        value.setTextColor(INK);
        settingValueViews.put(key, value);
        row.addView(value, new LinearLayout.LayoutParams(dp(72), -1));

        Button plus = neoButton("+", GREEN);
        plus.setOnClickListener(v -> adjustSetting(key, 1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(42)));
        return row;
    }

    private void adjustSetting(String key, int direction) {
        switch (key) {
            case "DRAG":
                dragHoldMs = Math.max(350, Math.min(1000, dragHoldMs + direction * 50));
                break;
            case "POINTER":
                pointerPercent = Math.max(50, Math.min(200, pointerPercent + direction * 10));
                break;
            case "SCROLL":
                scrollPercent = Math.max(50, Math.min(200, scrollPercent + direction * 10));
                break;
            case "REPEAT":
                repeatMs = Math.max(35, Math.min(145, repeatMs + direction * 10));
                break;
        }
        saveSettings();
        refreshSettingValues();
    }

    private void refreshSettingValues() {
        if (settingValueViews.containsKey("DRAG"))
            settingValueViews.get("DRAG").setText(dragHoldMs + " ms");
        if (settingValueViews.containsKey("POINTER"))
            settingValueViews.get("POINTER").setText(pointerPercent + "%");
        if (settingValueViews.containsKey("SCROLL"))
            settingValueViews.get("SCROLL").setText(scrollPercent + "%");
        if (settingValueViews.containsKey("REPEAT"))
            settingValueViews.get("REPEAT").setText(repeatMs + " ms");
        if (settingValueViews.containsKey("HAPTICS"))
            settingValueViews.get("HAPTICS").setText("HAPTICS · " + (hapticsOn ? "ON" : "OFF"));
    }

    private void showFunctionPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        LinearLayout functions = keyboardRow();
        for (int number = 1; number <= 12; number++) {
            addSpecialKey(functions, "F" + number, "F" + number,
                    0x39 + number, 1, PAPER);
        }
        card.addView(functions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout navigation = keyboardRow();
        addSpecialKey(navigation, "PrtSc", "PRINTSCREEN", 0x46, 1.35f, BLUE);
        addSpecialKey(navigation, "ScrLk", "SCROLLLOCK", 0x47, 1.35f, BLUE);
        addSpecialKey(navigation, "Pause", "PAUSE", 0x48, 1.35f, BLUE);
        addSpecialKey(navigation, "Ins", "INSERT", 0x49, 1, PAPER);
        addSpecialKey(navigation, "Del", "DELETE", 0x4C, 1, CORAL);
        addSpecialKey(navigation, "PgUp", "PGUP", 0x4B, 1.2f, PAPER);
        addSpecialKey(navigation, "PgDn", "PGDN", 0x4E, 1.2f, PAPER);
        addSpecialKey(navigation, "Home", "HOME", 0x4A, 1.3f, PAPER);
        addSpecialKey(navigation, "End", "END", 0x4D, 1.1f, PAPER);
        card.addView(navigation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        PopupWindow popup = new PopupWindow(card, dp(750), dp(132), true);
        popup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showBluetoothPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(BLUE));

        Button visible = neoButton("MAKE PHONE VISIBLE", YELLOW);
        visible.setOnClickListener(v -> {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(intent);
        });
        card.addView(visible, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        Button settings = neoButton("BLUETOOTH SETTINGS", PAPER);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        card.addView(settings, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        pairedDevices = new LinearLayout(this);
        pairedDevices.setOrientation(LinearLayout.VERTICAL);
        card.addView(pairedDevices);
        refreshBondedDevices();

        PopupWindow popup = new PopupWindow(card, dp(320),
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.showAsDropDown(anchor, -dp(235), dp(4));
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BT);
        } else {
            bindHidProfile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            bindHidProfile();
        } else {
            updateStatus();
        }
    }

    private void bindHidProfile() {
        if (destroyed || hidBinding || hid != null || adapter == null) {
            updateStatus();
            return;
        }
        hidBinding = true;
        boolean requested = adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                hidBinding = false;
                hid = (BluetoothHidDevice) proxy;
                registerHid();
            }
            @Override public void onServiceDisconnected(int profile) {
                hidBinding = false;
                hid = null;
                hidRegistered = false;
                hidHost = null;
                Log.w("A05sInput", "Bluetooth HID profile disconnected");
                updateStatus();
                scheduleBluetoothReconnect();
            }
        }, BluetoothProfile.HID_DEVICE);
        if (!requested) {
            hidBinding = false;
            Log.w("A05sInput", "Bluetooth HID profile bind was rejected");
            scheduleBluetoothReconnect();
        }
    }

    private void registerHid() {
        if (hid == null) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "A05s Input", "Offline keyboard and mouse", "Local", (byte) 0xC0, HID_DESCRIPTOR);
        boolean requested = hid.registerApp(sdp, null, null, getMainExecutor(), new BluetoothHidDevice.Callback() {
            @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                hidRegistered = registered;
                Log.d("A05sInput", "Bluetooth HID app registered=" + registered);
                if (pluggedDevice != null) {
                    hidHost = pluggedDevice;
                    rememberHost(pluggedDevice);
                }
                refreshBondedDevices();
                updateStatus();
                if (registered && mode == MODE_BLUETOOTH && hidHost == null) {
                    connectPreferredHost();
                }
            }
            @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                Log.d("A05sInput", "Bluetooth HID state=" + state + " host=" + safeName(device));
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    hidHost = device;
                    rememberHost(device);
                    reconnectScheduled = false;
                }
                if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(hidHost)) {
                    hidHost = null;
                    scheduleBluetoothReconnect();
                }
                updateStatus();
            }
        });
        if (!requested) Log.w("A05sInput", "Bluetooth HID app registration was rejected");
    }

    private void rememberHost(BluetoothDevice device) {
        if (!hasBtPermission() || device == null) return;
        getSharedPreferences("controls", MODE_PRIVATE).edit()
                .putString("last_hid_host", device.getAddress()).apply();
    }

    private void connectPreferredHost() {
        if (destroyed || mode != MODE_BLUETOOTH || !hasBtPermission() ||
                adapter == null || hid == null || !hidRegistered || hidHost != null) return;
        String address = getSharedPreferences("controls", MODE_PRIVATE)
                .getString("last_hid_host", null);
        if (address == null) return;
        try {
            BluetoothDevice preferred = adapter.getRemoteDevice(address);
            Log.d("A05sInput", "Connecting preferred HID host " + safeName(preferred));
            if (!hid.connect(preferred)) {
                Log.w("A05sInput", "Bluetooth HID connect request was rejected");
                scheduleBluetoothReconnect();
            }
        } catch (IllegalArgumentException e) {
            Log.w("A05sInput", "Saved Bluetooth host address is invalid", e);
        }
    }

    private void scheduleBluetoothReconnect() {
        if (destroyed || mode != MODE_BLUETOOTH || reconnectScheduled) return;
        reconnectScheduled = true;
        uiHandler.postDelayed(() -> {
            reconnectScheduled = false;
            if (destroyed || mode != MODE_BLUETOOTH) return;
            if (hid == null) bindHidProfile();
            else connectPreferredHost();
        }, 1200);
    }

    private void refreshBondedDevices() {
        runOnUiThread(() -> {
            if (pairedDevices == null) return;
            pairedDevices.removeAllViews();
            if (!hasBtPermission() || adapter == null) return;
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            for (BluetoothDevice device : devices) {
                Button connect = button("Connect Bluetooth: " + device.getName());
                connect.setOnClickListener(v -> {
                    rememberHost(device);
                    if (hid != null && hidRegistered && !hid.connect(device))
                        Log.w("A05sInput", "Bluetooth HID connect request was rejected");
                    updateStatus();
                });
                pairedDevices.addView(connect);
            }
        });
    }

    private void startUsbServer() {
        io.execute(() -> {
            try {
                usbServer = new ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"));
                updateStatus();
                while (!usbServer.isClosed()) {
                    UsbClient client = new UsbClient(usbServer.accept());
                    if (client.write("HELLO A05S_INPUT 1")) {
                        usbClients.add(client);
                        io.execute(() -> readUsbClient(client));
                    } else client.close();
                    updateStatus();
                }
            } catch (IOException e) {
                updateStatus();
            }
        });
    }

    private void readUsbClient(UsbClient client) {
        try {
            String line;
            while ((line = client.readLine()) != null) handleHostLine(line);
        } catch (IOException e) {
            Log.d("A05sInput", "Laptop battery link disconnected");
        } finally {
            usbClients.remove(client);
            client.close();
            pcStats = "LAPTOP BAT —";
            updateStatus();
        }
    }

    private void handleHostLine(String line) {
        if (!line.startsWith("BATTERY ")) return;
        String[] values = line.substring(8).trim().split("\\s+");
        if (values.length < 2) return;
        pcStats = "LAPTOP BAT " + metric(values[0], "%") +
                ("1".equals(values[1]) ? " ⚡" : "");
        updateStatus();
    }

    private String metric(String raw, String suffix) {
        try {
            int value = Integer.parseInt(raw);
            return value < 0 ? "—" : value + suffix;
        } catch (NumberFormatException e) {
            return "—";
        }
    }

    private void broadcast(String line) {
        if (mode != MODE_USB) return;
        usbWriter.execute(() -> sendToUsbClients(line));
    }

    private boolean sendToUsbClients(String line) {
        boolean delivered = false;
        for (UsbClient client : new ArrayList<>(usbClients)) {
            if (client.write(line)) {
                delivered = true;
            } else {
                usbClients.remove(client);
                client.close();
            }
        }
        updateStatus();
        return delivered;
    }

    private void sendText(String value) {
        if (mode == MODE_USB) {
            String encoded = Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            broadcast("TEXT " + encoded);
        } else if (mode == MODE_BLUETOOTH) {
            for (char c : value.toCharArray()) sendBluetoothChar(c);
        }
    }

    private void sendBluetoothChar(char c) {
        KeyStroke key = KeyStroke.forChar(c);
        if (key == null) return;
        sendBluetoothKey(key.modifier, key.usage);
    }

    private void sendSpecial(String name, int usage) {
        if (mode == MODE_USB) broadcast("KEY " + name);
        else if (mode == MODE_BLUETOOTH) sendBluetoothKey(0, usage);
    }

    private void pressSpecial(String name, int usage) {
        int modifier = activeBluetoothModifier();
        if (modifier == 0) {
            sendSpecial(name, usage);
        } else if (mode == MODE_USB) {
            broadcast("HOTKEY " + activeChordPrefix() + name);
        } else if (mode == MODE_BLUETOOTH) {
            sendBluetoothKey(modifier, usage);
        }
        clearOneShotModifiers();
    }

    private void pressPrintable(String normal, String shifted) {
        boolean letter = normal.length() == 1 && Character.isLetter(normal.charAt(0));
        if (ctrlOn || altOn || winOn) {
            KeyStroke key = KeyStroke.forChar(normal.charAt(0));
            if (key != null) {
                if (mode == MODE_USB) {
                    broadcast("HOTKEY " + activeChordPrefix() + normal.toUpperCase());
                } else if (mode == MODE_BLUETOOTH) {
                    sendBluetoothKey(activeBluetoothModifier(), key.usage);
                }
            }
            clearOneShotModifiers();
            return;
        }

        boolean useShift = shiftOn;
        String value;
        if (letter) value = (capsOn ^ useShift) ? normal.toUpperCase() : normal.toLowerCase();
        else value = useShift ? shifted : normal;
        sendText(value);
        if (shiftOn) {
            shiftOn = false;
            refreshModifierStyles();
        }
    }

    private int activeBluetoothModifier() {
        return (ctrlOn ? 0x01 : 0) | (shiftOn ? 0x02 : 0) |
                (altOn ? 0x04 : 0) | (winOn ? 0x08 : 0);
    }

    private String activeChordPrefix() {
        StringBuilder value = new StringBuilder();
        if (ctrlOn) value.append("CTRL+");
        if (shiftOn) value.append("SHIFT+");
        if (altOn) value.append("ALT+");
        if (winOn) value.append("WIN+");
        return value.toString();
    }

    private void clearOneShotModifiers() {
        shiftOn = false;
        ctrlOn = false;
        altOn = false;
        winOn = false;
        refreshModifierStyles();
    }

    private void sendStandaloneWindowsKey() {
        if (mode == MODE_USB) broadcast("KEY WIN");
        else if (mode == MODE_BLUETOOTH) sendBluetoothKey(0x08, 0);
    }

    private void sendBluetoothKey(int modifier, int usage) {
        if (hid == null || hidHost == null) return;
        BluetoothHidDevice targetHid = hid;
        BluetoothDevice targetHost = hidHost;
        byte[] modifiersDown = new byte[]{(byte) modifier, 0, 0, 0, 0, 0, 0, 0};
        byte[] chordDown = new byte[]{(byte) modifier, 0, (byte) usage, 0, 0, 0, 0, 0};
        byte[] allUp = new byte[8];
        long start;
        synchronized (this) {
            start = Math.max(SystemClock.uptimeMillis(), nextHidKeyAt);
            nextHidKeyAt = start + (modifier != 0 && usage != 0 ? 190 : 130);
        }
        Log.d("A05sInput", "BT key modifier=0x" + Integer.toHexString(modifier) +
                " usage=0x" + Integer.toHexString(usage));
        if (modifier != 0 && usage != 0) {
            // Send a real four-stage chord. Some Windows Bluetooth stacks miss a
            // modifier that first appears in the same report as the target key.
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, modifiersDown), start);
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, chordDown), start + 45);
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, modifiersDown), start + 115);
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, allUp), start + 160);
        } else {
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, chordDown), start);
            uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 1, allUp), start + 85);
        }
    }

    private void sendHidReport(BluetoothHidDevice targetHid, BluetoothDevice targetHost,
                               int reportId, byte[] report) {
        if (!targetHid.sendReport(targetHost, reportId, report)) {
            Log.w("A05sInput", "Bluetooth rejected HID report " + reportId);
            if (targetHost.equals(hidHost)) hidHost = null;
            targetHid.disconnect(targetHost);
            updateStatus();
            scheduleBluetoothReconnect();
        }
    }

    private void sendSystemControl(String name, int consumerUsage) {
        // Laptop brightness is deliberately cable-only. Windows Bluetooth HID
        // implementations interpret brightness usages inconsistently, and socket
        // writes must never block Android's UI thread.
        if (name.startsWith("BRIGHTNESS") || mode != MODE_BLUETOOTH) {
            usbWriter.execute(() -> sendToUsbClients("MEDIA " + name));
        } else {
            sendBluetoothConsumer(consumerUsage);
        }
    }

    private void sendBluetoothConsumer(int usage) {
        if (hid == null || hidHost == null) return;
        BluetoothHidDevice targetHid = hid;
        BluetoothDevice targetHost = hidHost;
        byte[] down = new byte[]{(byte) (usage & 0xFF), (byte) ((usage >> 8) & 0xFF)};
        byte[] up = new byte[2];
        long start;
        synchronized (this) {
            start = Math.max(SystemClock.uptimeMillis(), nextHidKeyAt);
            nextHidKeyAt = start + 130;
        }
        Log.d("A05sInput", "BT consumer usage=0x" + Integer.toHexString(usage));
        uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 3, down), start);
        uiHandler.postAtTime(() -> sendHidReport(targetHid, targetHost, 3, up), start + 85);
    }

    private void move(int dx, int dy) {
        if (mode == MODE_USB) broadcast("MOVE " + dx + " " + dy);
        else if (mode == MODE_BLUETOOTH) sendBluetoothMouse(mouseButtons, dx, dy, 0);
    }

    private void scroll(int amount) {
        if (mode == MODE_USB) broadcast("SCROLL " + amount);
        else if (mode == MODE_BLUETOOTH) sendBluetoothMouse(mouseButtons, 0, 0, amount);
    }

    private void click(String which) {
        setMouseButton(which, true);
        uiHandler.postDelayed(() -> setMouseButton(which, false), 38);
    }

    private void setMouseButton(String which, boolean down) {
        int mask = which.equals("right") ? 2 : 1;
        if (down) mouseButtons |= mask;
        else mouseButtons &= ~mask;
        if (mode == MODE_USB) {
            broadcast("BUTTON " + which.toUpperCase() + " " + (down ? "DOWN" : "UP"));
        } else if (mode == MODE_BLUETOOTH) {
            sendBluetoothMouse(mouseButtons, 0, 0, 0);
        }
    }

    private void sendBluetoothMouse(int buttons, int dx, int dy, int wheel) {
        if (hid == null || hidHost == null) return;
        boolean emptyReport = dx == 0 && dy == 0 && wheel == 0;
        while (dx != 0 || dy != 0 || wheel != 0) {
            int x = clamp(dx), y = clamp(dy), w = clamp(wheel);
            sendHidReport(hid, hidHost, 2,
                    new byte[]{(byte) buttons, (byte) x, (byte) y, (byte) w});
            dx -= x; dy -= y; wheel -= w;
        }
        if (emptyReport) {
            sendHidReport(hid, hidHost, 2, new byte[]{(byte) buttons, 0, 0, 0});
        }
    }

    private void updateStatus() {
        if (status == null) return;
        runOnUiThread(() -> {
            String selected = mode == MODE_USB ? "USB" : mode == MODE_BLUETOOTH ? "BT" : "OFF";
            String connection;
            if (mode == MODE_USB) {
                connection = usbServer == null ? "starting" :
                        (usbClients.isEmpty() ? "waiting for PC" : "PC connected");
            } else if (mode == MODE_BLUETOOTH) {
                connection = hidHost == null ? (hidRegistered ? "ready to pair" : "starting")
                        : safeName(hidHost);
                if (connection.startsWith("DESKTOP-")) connection = connection.substring(8);
            } else {
                connection = usbClients.isEmpty() ? "local" : "PC linked";
            }
            String detail = pcStats;
            if (activeBluetoothModifier() != 0) {
                String chord = activeChordPrefix();
                detail = "NEXT · " + chord.substring(0, chord.length() - 1);
            }
            status.setText(selected + " · " + connection + "\n" + detail);
            if (systemStatsView != null) systemStatsView.setText(pcStats);
        });
    }

    private String safeName(BluetoothDevice device) {
        if (!hasBtPermission()) return "paired host";
        String name = device.getName();
        return name == null ? device.getAddress() : name;
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
        if (hid != null && hidRegistered) hid.unregisterApp();
        if (adapter != null && hid != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        try { if (usbServer != null) usbServer.close(); } catch (IOException ignored) { }
        for (UsbClient client : usbClients) client.close();
        io.shutdownNow();
        usbWriter.shutdownNow();
    }

    private static final class UsbClient {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        UsbClient(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        synchronized boolean write(String line) {
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException e) {
                Log.w("A05sInput", "USB client disconnected", e);
                return false;
            }
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private LinearLayout keyboardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        params.setMargins(0, dp(1), 0, dp(1));
        return params;
    }

    private LinearLayout.LayoutParams keyParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), dp(1), dp(2), dp(1));
        return params;
    }

    private void addLetterKey(LinearLayout row, String letter) {
        addPrintableKey(row, letter, letter.toLowerCase(), letter.toUpperCase(), 1);
    }

    private void addPrintableKey(LinearLayout row, String label, String normal, String shifted, float weight) {
        Button key = neoButton(label, PAPER);
        key.setOnClickListener(v -> pressPrintable(normal, shifted));
        row.addView(key, keyParams(weight));
    }

    private void addSpecialKey(LinearLayout row, String label, String name, int usage,
                               float weight, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> pressSpecial(name, usage));
        row.addView(key, keyParams(weight));
    }

    private void addRepeatingSpecialKey(LinearLayout row, String label, String name, int usage,
                                        float weight, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> pressSpecial(name, usage));
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> {
            if (key.isPressed()) {
                pressSpecial(name, usage);
                handler.postDelayed(repeat[0], repeatMs);
            }
        };
        key.setOnLongClickListener(v -> {
            repeat[0].run();
            return true;
        });
        row.addView(key, keyParams(weight));
    }

    private void addModifierKey(LinearLayout row, String label, String modifier, float weight) {
        Button key = neoButton(label, PAPER);
        switch (modifier) {
            case "SHIFT": shiftButtons.add(key); break;
            case "CAPS": capsButtons.add(key); break;
            case "CTRL": ctrlButtons.add(key); break;
            case "ALT": altButtons.add(key); break;
            case "WIN": winButtons.add(key); break;
        }
        key.setOnClickListener(v -> {
            switch (modifier) {
                case "SHIFT": shiftOn = !shiftOn; break;
                case "CAPS": capsOn = !capsOn; break;
                case "CTRL": ctrlOn = !ctrlOn; break;
                case "ALT": altOn = !altOn; break;
                case "WIN": winOn = !winOn; break;
            }
            refreshModifierStyles();
        });
        if (modifier.equals("WIN")) {
            key.setOnLongClickListener(v -> {
                winOn = false;
                sendStandaloneWindowsKey();
                refreshModifierStyles();
                return true;
            });
        }
        row.addView(key, keyParams(weight));
    }

    private void refreshModifierStyles() {
        styleModifiers(shiftButtons, shiftOn);
        styleModifiers(capsButtons, capsOn);
        styleModifiers(ctrlButtons, ctrlOn);
        styleModifiers(altButtons, altOn);
        styleModifiers(winButtons, winOn);
        updateStatus();
    }

    private void styleModifiers(List<Button> buttons, boolean active) {
        for (Button button : buttons) button.setBackground(interactiveNeoBackground(active ? GREEN : PAPER));
    }

    private RadioButton radio(String label, int id) {
        RadioButton result = new RadioButton(this);
        result.setText(label);
        result.setId(id);
        result.setTextSize(12);
        result.setTextColor(INK);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setButtonTintList(ColorStateList.valueOf(INK));
        result.setPadding(0, 0, dp(3), 0);
        return result;
    }

    private Button button(String label) {
        return neoButton(label, PAPER);
    }

    private Button neoButton(String label, int color) {
        Button result = new Button(this);
        result.setText(label);
        result.setAllCaps(false);
        result.setTextSize(12);
        result.setTextColor(INK);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setGravity(Gravity.CENTER);
        result.setSingleLine(!label.contains("\n"));
        result.setLineSpacing(0, 0.82f);
        result.setPadding(dp(5), 0, dp(5), dp(4));
        result.setMinHeight(0);
        result.setMinWidth(0);
        result.setBackground(interactiveNeoBackground(color));
        result.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                haptic(view, HapticFeedbackConstants.KEYBOARD_TAP);
                view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(55).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
            }
            return false;
        });
        result.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.animate().scaleX(1.035f).scaleY(1.035f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
        return result;
    }

    private TextView text(String value, int sp) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(sp);
        return result;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(16));
        if (color != Color.TRANSPARENT) shape.setStroke(dp(2), INK);
        return shape;
    }

    private LayerDrawable neoBackground(int color) {
        GradientDrawable shadow = rounded(INK);
        GradientDrawable face = rounded(color);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, face});
        layers.setLayerInset(0, dp(5), dp(5), 0, 0);
        layers.setLayerInset(1, 0, 0, dp(5), dp(5));
        return layers;
    }

    private LayerDrawable neoPressedBackground(int color) {
        GradientDrawable shadow = rounded(INK);
        GradientDrawable face = rounded(color);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, face});
        layers.setLayerInset(0, dp(5), dp(5), 0, 0);
        layers.setLayerInset(1, dp(4), dp(4), dp(1), dp(1));
        return layers;
    }

    private LayerDrawable neoHoverBackground(int color) {
        GradientDrawable shadow = rounded(INK);
        GradientDrawable face = rounded(color);
        face.setStroke(dp(3), INK);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, face});
        layers.setLayerInset(0, dp(7), dp(7), 0, 0);
        layers.setLayerInset(1, 0, 0, dp(7), dp(7));
        return layers;
    }

    private StateListDrawable interactiveNeoBackground(int color) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, neoPressedBackground(color));
        states.addState(new int[]{android.R.attr.state_hovered}, neoHoverBackground(color));
        states.addState(new int[]{android.R.attr.state_focused}, neoHoverBackground(color));
        states.addState(new int[]{}, neoBackground(color));
        return states;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void haptic(View view, int feedback) {
        if (hapticsOn) view.performHapticFeedback(feedback);
    }

    private static int clamp(int value) {
        return Math.max(-127, Math.min(127, value));
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private final class TrackpadListener implements View.OnTouchListener {
        private float lastX, lastY;
        private float lastTapX, lastTapY;
        private long downAt;
        private long lastTapAt;
        private float travel;
        private boolean fingerDown;
        private boolean dragging;
        private boolean twoFinger;
        private View activeView;
        private final Runnable longPress = () -> {
            if (fingerDown && !twoFinger && travel < dp(12) && activeView != null) {
                beginDrag(activeView);
            }
        };

        private void beginDrag(View view) {
            if (dragging) return;
            dragging = true;
            uiHandler.removeCallbacks(longPress);
            setMouseButton("left", true);
            haptic(view, HapticFeedbackConstants.LONG_PRESS);
            view.setBackground(rounded(Color.rgb(72, 72, 72)));
        }

        private void finishGesture(View view) {
            uiHandler.removeCallbacks(longPress);
            fingerDown = false;
            if (dragging) setMouseButton("left", false);
            dragging = false;
            twoFinger = false;
            view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
            view.setBackground(rounded(INK));
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            float x = 0, y = 0;
            for (int i = 0; i < event.getPointerCount(); i++) {
                x += event.getX(i); y += event.getY(i);
            }
            x /= event.getPointerCount(); y /= event.getPointerCount();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                    view.animate().scaleX(0.992f).scaleY(0.992f).setDuration(55).start();
                    view.setBackground(rounded(Color.rgb(45, 45, 45)));
                    long now = SystemClock.uptimeMillis();
                    fingerDown = true;
                    dragging = false;
                    twoFinger = false;
                    activeView = view;
                    lastX = x; lastY = y; travel = 0; downAt = now;
                    if (now - lastTapAt < 330 &&
                            Math.abs(x - lastTapX) + Math.abs(y - lastTapY) < dp(64)) {
                        lastTapAt = 0;
                        beginDrag(view);
                    } else {
                        uiHandler.postDelayed(longPress, dragHoldMs);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    twoFinger = true;
                    uiHandler.removeCallbacks(longPress);
                    if (dragging) {
                        setMouseButton("left", false);
                        dragging = false;
                    }
                    lastX = x; lastY = y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int rawDx = Math.round(x - lastX);
                    int rawDy = Math.round(y - lastY);
                    int dx = Math.round(rawDx * pointerPercent / 100f);
                    int dy = Math.round(rawDy * pointerPercent / 100f);
                    travel += Math.abs(rawDx) + Math.abs(rawDy);
                    if (!dragging && travel >= dp(12)) uiHandler.removeCallbacks(longPress);
                    if (event.getPointerCount() >= 2)
                        scroll(clamp(Math.round(-rawDy * scrollPercent / 300f)));
                    else move(dx, dy);
                    lastX = x; lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean wasDragging = dragging;
                    boolean wasTwoFinger = twoFinger;
                    finishGesture(view);
                    long releasedAt = SystemClock.uptimeMillis();
                    if (!wasDragging && !wasTwoFinger && travel < dp(12) && releasedAt - downAt < 350) {
                        click("left");
                        lastTapAt = releasedAt;
                        lastTapX = x;
                        lastTapY = y;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    finishGesture(view);
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class ScrollPadListener implements View.OnTouchListener {
        private float lastY;
        private float remainder;

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = event.getY();
                    remainder = 0;
                    haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                    view.animate().scaleX(0.965f).scaleY(0.985f).setDuration(55).start();
                    view.setBackground(rounded(Color.rgb(104, 157, 222)));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    remainder += event.getY() - lastY;
                    lastY = event.getY();
                    int stepSize = dp(Math.max(4, 1000 / scrollPercent));
                    int steps = (int) (remainder / stepSize);
                    if (steps != 0) {
                        scroll(clamp(-steps));
                        remainder -= steps * stepSize;
                        haptic(view, HapticFeedbackConstants.CLOCK_TICK);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
                    view.setBackground(rounded(BLUE));
                    return true;
                default:
                    return true;
            }
        }
    }

    private static final class KeyStroke {
        final int modifier;
        final int usage;

        KeyStroke(int modifier, int usage) {
            this.modifier = modifier;
            this.usage = usage;
        }

        static KeyStroke forChar(char c) {
            if (c >= 'a' && c <= 'z') return new KeyStroke(0, 0x04 + c - 'a');
            if (c >= 'A' && c <= 'Z') return new KeyStroke(0x02, 0x04 + c - 'A');
            if (c >= '1' && c <= '9') return new KeyStroke(0, 0x1E + c - '1');
            if (c == '0') return new KeyStroke(0, 0x27);
            switch (c) {
                case '\n': return new KeyStroke(0, 0x28);
                case '\t': return new KeyStroke(0, 0x2B);
                case ' ': return new KeyStroke(0, 0x2C);
                case '-': return new KeyStroke(0, 0x2D);
                case '_': return new KeyStroke(0x02, 0x2D);
                case '=': return new KeyStroke(0, 0x2E);
                case '+': return new KeyStroke(0x02, 0x2E);
                case '[': return new KeyStroke(0, 0x2F);
                case '{': return new KeyStroke(0x02, 0x2F);
                case ']': return new KeyStroke(0, 0x30);
                case '}': return new KeyStroke(0x02, 0x30);
                case '\\': return new KeyStroke(0, 0x31);
                case ';': return new KeyStroke(0, 0x33);
                case ':': return new KeyStroke(0x02, 0x33);
                case '\'': return new KeyStroke(0, 0x34);
                case '"': return new KeyStroke(0x02, 0x34);
                case '`': return new KeyStroke(0, 0x35);
                case '~': return new KeyStroke(0x02, 0x35);
                case ',': return new KeyStroke(0, 0x36);
                case '<': return new KeyStroke(0x02, 0x36);
                case '.': return new KeyStroke(0, 0x37);
                case '>': return new KeyStroke(0x02, 0x37);
                case '/': return new KeyStroke(0, 0x38);
                case '?': return new KeyStroke(0x02, 0x38);
                case '!': return new KeyStroke(0x02, 0x1E);
                case '@': return new KeyStroke(0x02, 0x1F);
                case '#': return new KeyStroke(0x02, 0x20);
                case '$': return new KeyStroke(0x02, 0x21);
                case '%': return new KeyStroke(0x02, 0x22);
                case '^': return new KeyStroke(0x02, 0x23);
                case '&': return new KeyStroke(0x02, 0x24);
                case '*': return new KeyStroke(0x02, 0x25);
                case '(': return new KeyStroke(0x02, 0x26);
                case ')': return new KeyStroke(0x02, 0x27);
                default: return null;
            }
        }
    }
}
