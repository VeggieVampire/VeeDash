package com.local.veedash;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String APP_VERSION = "2026.07.28-1050";
    private static final int PICK_BACKGROUND = 500;
    private static final int REQUEST_PERMS = 501;
    private static final String DEFAULT_PC_HOST = "192.168.0.130";
    private static final int DEFAULT_PC_PORT = 8766;
    private static final String APP_PREFS = "veedash";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private DashboardView dashboard;
    private TextView status;
    private TextView diagView;
    private TextView coachView;
    private LinearLayout configPanel;
    private LinearLayout toolbarView;
    private LinearLayout menuContent;
    private View menuDismissLayer;
    private ScrollView diagScroll;
    private EditText hostEdit;
    private EditText portEdit;
    private Spinner deviceSpinner;
    private TextView deviceReadout;
    private final List<DeviceChoice> deviceChoices = new ArrayList<>();
    private final List<String> deviceLabels = new ArrayList<>();
    private ArrayAdapter<String> deviceListAdapter;
    private ObdLink obdSession;
    private DeviceChoice selectedDevice;
    private int selectedDeviceIndex = -1;
    private BluetoothLeScanner bleScanner;
    private boolean scanning;
    private boolean legacyScanning;
    private boolean classicDiscovering;
    private boolean receiverRegistered;
    private String selectedPin = "1234";
    private static final String[] PIN_OPTIONS = new String[]{"1234", "0000", "1111", "9999", "6789", ""};
    private final Set<String> scanLoggedAddresses = new HashSet<>();
    private final StringBuilder diagLog = new StringBuilder();
    private String dbgPhase = "start";
    private String dbgSelected = "none";
    private String dbgScan = "not run";
    private String dbgServices = "-";
    private String dbgWrite = "-";
    private String dbgNotify = "-";
    private String dbgLastTx = "-";
    private String dbgLastRx = "-";
    private String dbgError = "-";
    private long lastDataLogMs = 0;
    private long remoteLogSeq = 0;
    private boolean destroyed = false;
    private boolean autoReconnectEnabled = true;
    private boolean experimentalObdEnabled = false;
    private boolean pcSampleFetchInFlight = false;
    private boolean pcSampleRunQueued = false;
    private boolean chatPopupEnabled = true;
    private long chatPopupMs = 6500;
    private boolean autoDimEnabled = true;
    private float nightBrightness = 0.40f;
    private float nightExtraDim = 0.22f;
    private boolean lastAutoDimActive = false;
    private boolean intentionalStop = false;
    private boolean connecting = false;
    private boolean keepTryingSavedBluetooth = true;
    private int savedBluetoothRetryCount = 0;
    private String lastConnectedAddress = "";
    private boolean lastConnectedBle = false;
    private String lastConfig = "";
    private String lastConfigVersion = "-";
    private String lastPullCommand = "";
    private String lastPcSampleSeq = "";
    private String lastBackgroundAsset = "";
    private String pcHost = DEFAULT_PC_HOST;
    private int pcPort = DEFAULT_PC_PORT;
    private int remoteFailCount = 0;
    private boolean discoveringPcServer = false;
    private String coachStatus = "Not connected";
    private String coachMessage = "Open VeeDash, tap Scan, pick VEEPEAK, then tap AutoTry.";
    private final List<String> activePollPids = new ArrayList<>();

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override public void run() {
            hideControlsIfIdle();
        }
    };

    private final Runnable hideChatRunnable = new Runnable() {
        @Override public void run() {
            hideChatPopup();
        }
    };

    private final Runnable messagePoller = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            applyAutoDim();
            fetchRemoteMessage();
            fetchRemoteConfig(false);
            pollPcSamplesIfExperimentOn();
            ui.postDelayed(this, 2200);
        }
    };

    private final Runnable savedBluetoothRetryRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || !keepTryingSavedBluetooth || intentionalStop || obdSession != null || connecting) return;
            if (lastConnectedAddress == null || lastConnectedAddress.isEmpty()) return;
            savedBluetoothRetryCount++;
            addDiag("Saved Bluetooth retry #" + savedBluetoothRetryCount);
            autoConnectLastDevice("retry #" + savedBluetoothRetryCount);
            ui.postDelayed(this, 9000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        loadSavedConnection();
        buildUi();
        ensurePermissions();
        loadPairedDevices();
        ui.postDelayed(() -> autoConnectLastDevice("startup"), 1200);
        ui.postDelayed(savedBluetoothRetryRunnable, 9000);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyAutoDim();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacks(messagePoller);
        ui.removeCallbacks(savedBluetoothRetryRunnable);
        ui.removeCallbacks(hideControlsRunnable);
        ui.removeCallbacks(hideChatRunnable);
        stopObd();
        stopDiscovery();
        if (receiverRegistered) {
            try { unregisterReceiver(classicReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        dashboard = new DashboardView(this);
        dashboard.setTapListener(this::revealControlsTemporarily);
        root.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(this);
        toolbarView = toolbar;
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(8, 4, 8, 6);
        toolbar.setBackgroundColor(alphaColor(0x000000, 0.80));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("Not connected");
        status.setPadding(8, 0, 16, 0);

        deviceReadout = new TextView(this);
        deviceReadout.setTextColor(0xffd8f6ff);
        deviceReadout.setTextSize(12);
        deviceReadout.setText("Device: none");
        deviceReadout.setPadding(8, 0, 8, 0);

        deviceSpinner = new Spinner(this);
        deviceSpinner.setVisibility(View.GONE);
        Button autoTry = smallButton("Last");
        Button pull = smallButton("Sync Now");
        Button log = smallButton("Log");
        Button edit = smallButton("Edit");
        Button menu = smallButton("Menu");

        autoTry.setOnClickListener(v -> {
            if (obdSession == null) autoConnectLastDevice("button");
            else stopObd();
        });
        pull.setOnClickListener(v -> pullNow());
        log.setOnClickListener(v -> toggleLog());
        log.setOnLongClickListener(v -> {
            copyLog();
            return true;
        });
        edit.setOnClickListener(v -> {
            dashboard.setEditMode(!dashboard.isEditMode());
            edit.setText(dashboard.isEditMode() ? "Done" : "Edit");
            toast(dashboard.isEditMode() ? "Drag gauges to move them" : "Layout saved");
            if (dashboard.isEditMode()) {
                ui.removeCallbacks(hideControlsRunnable);
            } else {
                ui.postDelayed(hideControlsRunnable, 700);
            }
        });
        menu.setOnClickListener(v -> toggleConfigPanel());

        infoRow.addView(status, new LinearLayout.LayoutParams(0, dp(24), 0.7f));
        infoRow.addView(deviceReadout, new LinearLayout.LayoutParams(0, dp(24), 1.3f));
        infoRow.addView(deviceSpinner, new LinearLayout.LayoutParams(1, 1));
        buttonRow.addView(autoTry, new LinearLayout.LayoutParams(0, dp(42), 1f));
        buttonRow.addView(pull, new LinearLayout.LayoutParams(0, dp(42), 1.35f));
        buttonRow.addView(log, new LinearLayout.LayoutParams(0, dp(42), 1f));
        buttonRow.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1f));
        buttonRow.addView(menu, new LinearLayout.LayoutParams(0, dp(42), 1f));
        toolbar.addView(infoRow, new LinearLayout.LayoutParams(-1, dp(26)));
        toolbar.addView(buttonRow, new LinearLayout.LayoutParams(-1, dp(44)));

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(toolbar, barParams);
        toolbar.setVisibility(View.GONE);

        diagView = new TextView(this);
        diagView.setTextColor(0xffd8f6ff);
        diagView.setTextSize(13);
        diagView.setTypeface(Typeface.MONOSPACE);
        diagView.setPadding(12, 10, 12, 10);
        diagView.setBackgroundColor(alphaColor(0x000000, 0.93));
        diagScroll = new ScrollView(this);
        diagScroll.setFillViewport(false);
        diagScroll.setBackgroundColor(alphaColor(0x000000, 0.93));
        diagScroll.addView(diagView, new ScrollView.LayoutParams(-1, -2));
        diagScroll.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams logParams = new FrameLayout.LayoutParams(-1, dp(255), Gravity.TOP);
        logParams.topMargin = dp(82);
        root.addView(diagScroll, logParams);

        coachView = new TextView(this);
        coachView.setTextColor(Color.WHITE);
        coachView.setTextSize(15);
        coachView.setTypeface(Typeface.DEFAULT_BOLD);
        coachView.setPadding(dp(12), dp(10), dp(12), dp(10));
        coachView.setBackgroundColor(alphaColor(0x06141d, 0.86));
        coachView.setText("STATUS\nNot connected\n\nCHAT\n" + coachMessage);
        coachView.setVisibility(View.GONE);
        coachView.setAlpha(0f);
        coachView.setTranslationY(dp(40));
        FrameLayout.LayoutParams coachParams = new FrameLayout.LayoutParams(dp(360), -2, Gravity.BOTTOM | Gravity.RIGHT);
        coachParams.rightMargin = dp(12);
        coachParams.bottomMargin = dp(12);
        root.addView(coachView, coachParams);

        configPanel = buildConfigPanel();
        configPanel.setVisibility(View.GONE);
        menuDismissLayer = new View(this);
        menuDismissLayer.setBackgroundColor(0x00000000);
        menuDismissLayer.setVisibility(View.GONE);
        menuDismissLayer.setOnClickListener(v -> hideConfigPanel());
        root.addView(menuDismissLayer, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams configParams = new FrameLayout.LayoutParams(dp(560), dp(382), Gravity.TOP | Gravity.RIGHT);
        configParams.topMargin = dp(76);
        configParams.rightMargin = dp(18);
        root.addView(configPanel, configParams);

        setContentView(root);
        addDiag("VeeDash " + APP_VERSION + " started. Android " + Build.VERSION.SDK_INT + ", model " + Build.MODEL);
        addDiag("WiFi log target " + logUrl());
        addDiag("Chat pull target " + messageUrl());
        startRemoteMessagePolling();
    }

    private LinearLayout buildConfigPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(alphaColor(0x101820, 0.93));

        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(22);
        title.setText("MENU");
        panel.addView(title);

        TextView help = new TextView(this);
        help.setTextColor(0xffd8f6ff);
        help.setTextSize(16);
        help.setText("Pick a section. Sync Now and Edit stay on the main bar.");
        panel.addView(help);

        menuContent = new LinearLayout(this);
        menuContent.setOrientation(LinearLayout.VERTICAL);
        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(false);
        menuScroll.addView(menuContent, new ScrollView.LayoutParams(-1, -2));
        panel.addView(menuScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        showMenuHome();
        return panel;
    }

    private void showMenuHome() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuButton("Bluetooth", v -> showBluetoothMenu());
        addMenuButton("Server", v -> showServerMenu());
        addMenuButton("Display", v -> showDisplayMenu());
        addMenuButton("Debug", v -> showDebugMenu());
        addMenuButton("Close", v -> hideConfigPanel());
    }

    private void showBluetoothMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Bluetooth");
        addMenuButton("Auto / Connect last", v -> autoConnectLastDevice("menu"));
        addMenuButton("Select device", v -> showDeviceSelectMenu());
        addMenuButton(obdSession == null ? "Connect selected" : "Disconnect", v -> {
            if (obdSession == null) connectSelectedDevice();
            else stopObd();
        });
        addMenuButton("Scan for adapters", v -> {
            scanAndOpenDevicePicker();
        });
        addMenuButton("Forget adapter", v -> showForgetConfirmMenu());
        addMenuButton("Back", v -> showMenuHome());
    }

    private void showDeviceSelectMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Select device");
        if (deviceChoices.isEmpty()) {
            addMenuButton("Scan for adapters", v -> {
                scanAndOpenDevicePicker();
            });
        } else {
            for (int i = 0; i < deviceChoices.size(); i++) {
                final int index = i;
                String label = deviceLabels.get(i);
                String prefix = index == selectedDeviceIndex ? "Selected: " : "";
                addMenuButton(prefix + label, v -> selectDeviceIndexAndConnect(index));
            }
        }
        addMenuButton("Back", v -> showBluetoothMenu());
    }

    private void showForgetConfirmMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Forget adapter?");
        addMenuButton("Yes, forget saved adapter", v -> {
            forgetSavedDevice();
            showBluetoothMenu();
        });
        addMenuButton("Cancel", v -> showBluetoothMenu());
    }

    private void showServerMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Server");
        hostEdit = new EditText(this);
        hostEdit.setSingleLine(true);
        hostEdit.setTextColor(Color.WHITE);
        hostEdit.setTextSize(20);
        hostEdit.setHintTextColor(0xff9ab7c8);
        hostEdit.setHint("PC IP");
        hostEdit.setText(pcHost);
        menuContent.addView(hostEdit, new LinearLayout.LayoutParams(-1, dp(56)));

        portEdit = new EditText(this);
        portEdit.setSingleLine(true);
        portEdit.setTextColor(Color.WHITE);
        portEdit.setTextSize(20);
        portEdit.setHintTextColor(0xff9ab7c8);
        portEdit.setHint("Port");
        portEdit.setText(String.valueOf(pcPort));
        menuContent.addView(portEdit, new LinearLayout.LayoutParams(-1, dp(56)));

        addMenuButton("Save server", v -> saveServerSettingsFromPanel());
        addMenuButton("Back", v -> showMenuHome());
    }

    private void showDisplayMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Display");
        addMenuButton("Choose background", v -> pickBackground());
        addMenuButton("Reset layout", v -> dashboard.resetLayout());
        addMenuButton("Back", v -> showMenuHome());
    }

    private void showDebugMenu() {
        if (menuContent == null) return;
        menuContent.removeAllViews();
        addMenuHeader("Debug");
        addMenuButton("Show / hide log", v -> toggleLog());
        addMenuButton("Sync Now", v -> pullNow());
        addMenuButton("Safe DTC scan", v -> runExperimentalScan());
        addMenuButton(experimentalObdEnabled ? "Experiment: ON" : "Experiment: OFF", v -> toggleExperimentalObd());
        addMenuButton("Run PC samples", v -> runPcSampleScan());
        addMenuButton("Back", v -> showMenuHome());
    }

    private void addMenuHeader(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xffd8f6ff);
        label.setTextSize(18);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(10), 0, dp(4));
        menuContent.addView(label, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private void addMenuButton(String text, View.OnClickListener listener) {
        Button button = smallButton(text);
        button.setTextSize(18);
        button.setOnClickListener(listener);
        menuContent.addView(button, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(8, 0, 8, 0);
        return button;
    }

    private void revealControlsTemporarily() {
        if (toolbarView != null) toolbarView.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideControlsRunnable);
        ui.postDelayed(hideControlsRunnable, 2300);
    }

    private void hideControlsIfIdle() {
        if (configPanel != null && configPanel.getVisibility() == View.VISIBLE) return;
        if (toolbarView != null) toolbarView.setVisibility(View.GONE);
    }

    private void toggleLog() {
        if (diagScroll == null) return;
        diagScroll.setVisibility(diagScroll.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        refreshDiagView();
    }

    private void pullNow() {
        copyLog();
        sendLogSnapshotToPc();
        fetchRemoteMessage();
        fetchRemoteConfig(true);
        popupChat("Syncing latest dashboard config\nCopied and sent debug log\n" + serverBase());
    }

    private void copyLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("VeeDash log", diagLog.toString()));
        }
    }

    private void sendLogSnapshotToPc() {
        String snapshot = diagLog.toString();
        if (snapshot.trim().isEmpty()) return;
        sendRemoteLog("LOG SNAPSHOT FROM SYNC NOW\n" + snapshot);
    }

    private void runExperimentalScan() {
        if (obdSession == null) {
            popupChat("Safe DTC scan needs an active VeePeak connection first.");
            addDiag("Safe DTC scan skipped: no OBD session");
            return;
        }
        popupChat("Safe DTC scan started.\nRaw replies will go to debug log.");
        addDiag("Safe DTC scan requested");
        obdSession.experimentalScan(defaultSafeDtcCommands(), false);
        hideConfigPanel();
    }

    private void toggleExperimentalObd() {
        experimentalObdEnabled = !experimentalObdEnabled;
        addDiag("Experimental OBD command mode " + (experimentalObdEnabled ? "ON" : "OFF"));
        popupChat(experimentalObdEnabled
                ? "Experiment mode ON.\nPC sample commands can now be sent to OBD."
                : "Experiment mode OFF.\nPC sample commands are blocked.");
        showDebugMenu();
    }

    private void runPcSampleScan() {
        if (obdSession == null) {
            popupChat("PC sample scan needs an active VeePeak connection first.");
            addDiag("PC sample scan skipped: no OBD session");
            return;
        }
        if (!experimentalObdEnabled) {
            popupChat("Experiment mode is OFF.\nTap Experiment: OFF first if you want to allow PC-submitted OBD commands.");
            addDiag("PC sample scan blocked: experiment mode OFF");
            return;
        }
        popupChat("Fetching PC OBD samples.\nRaw replies will go to debug log.");
        addDiag("PC sample scan requested from " + obdSamplesUrl());
        fetchPcSampleCommands(true);
        hideConfigPanel();
    }

    private List<String> defaultSafeDtcCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("0101");
        commands.add("03");
        commands.add("07");
        commands.add("0A");
        commands.add("ATDPN");
        commands.add("ATDP");
        commands.add("0902");
        commands.add("0904");
        commands.add("090A");
        return commands;
    }

    private void pollPcSamplesIfExperimentOn() {
        if (!experimentalObdEnabled || obdSession == null || pcSampleFetchInFlight) return;
        fetchPcSampleCommands(false);
    }

    private void fetchPcSampleCommands(boolean forceRun) {
        if (pcSampleFetchInFlight) return;
        pcSampleFetchInFlight = true;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(cacheBusted(obdSamplesUrl())).openConnection();
                conn.setUseCaches(false);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1600);
                conn.setRequestProperty("Cache-Control", "no-cache");
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line);
                reader.close();
                JSONObject json = new JSONObject(out.toString());
                String seq = json.optString("seq", json.optString("updatedAt", ""));
                if (!forceRun && (!seq.isEmpty() && seq.equals(lastPcSampleSeq) || pcSampleRunQueued)) {
                    noteRemoteOk();
                    return;
                }
                JSONArray array = json.optJSONArray("commands");
                ArrayList<String> commands = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        String cmd = array.optString(i, "").trim().toUpperCase(Locale.US).replace(" ", "");
                        if (!cmd.isEmpty()) commands.add(cmd);
                    }
                }
                if (commands.isEmpty()) commands.addAll(defaultSafeDtcCommands());
                noteRemoteOk();
                if (!seq.isEmpty()) lastPcSampleSeq = seq;
                ui.post(() -> {
                    pcSampleRunQueued = true;
                    addDiag("PC sample commands loaded: " + commands);
                    if (obdSession != null) obdSession.experimentalScan(commands, true);
                });
            } catch (Exception ex) {
                addDiag("PC sample fetch failed: " + ex.getMessage());
                noteRemoteFail("obd-samples");
                ui.post(() -> popupChat("Could not fetch PC OBD samples.\n" + ex.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
                pcSampleFetchInFlight = false;
            }
        }, "VeeDashObdSamplesPull").start();
    }

    private void scanAndOpenDevicePicker() {
        scanAllDevices();
        popupChat("Scanning Bluetooth adapters.\nThe device picker will reopen when scan finishes.");
        ui.postDelayed(() -> {
            if (!destroyed && configPanel != null && configPanel.getVisibility() == View.VISIBLE) showDeviceSelectMenu();
        }, 4200);
    }

    private void cycleDevice(int delta) {
        if (deviceChoices.isEmpty()) {
            addDiag("Device cycle requested but list is empty");
            toast("Tap Scan first");
            return;
        }
        int next = selectedDeviceIndex < 0 ? 0 : selectedDeviceIndex + delta;
        if (next < 0) next = deviceChoices.size() - 1;
        if (next >= deviceChoices.size()) next = 0;
        selectDeviceIndex(next, true);
    }

    private void selectDeviceIndex(int index, boolean updateSpinner) {
        if (index < 0 || index >= deviceChoices.size()) {
            selectedDeviceIndex = -1;
            selectedDevice = null;
            updateDeviceReadout();
            return;
        }
        selectedDeviceIndex = index;
        selectedDevice = deviceChoices.get(index);
        if (updateSpinner && deviceSpinner != null) {
            deviceSpinner.setSelection(index);
        }
        updateDeviceReadout();
        addDiag("Selected #" + (index + 1) + "/" + deviceChoices.size() + " " + currentDeviceLabel());
    }

    private void selectDeviceIndexAndConnect(int index) {
        selectDeviceIndex(index, true);
        if (selectedDevice == null) return;
        rememberSelectedDeviceCandidate();
        popupChat("Selected Bluetooth adapter\n" + currentDeviceLabel() + "\nConnecting now...");
        hideConfigPanel();
        if (obdSession != null) {
            addDiag("Closing current OBD session before selected-device connect");
            obdSession.close();
            obdSession = null;
        }
        connecting = false;
        connectSelectedDevice(true);
    }

    private void loadSavedConnection() {
        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        pcHost = prefs.getString("pcHost", DEFAULT_PC_HOST);
        pcPort = prefs.getInt("pcPort", DEFAULT_PC_PORT);
        lastBackgroundAsset = prefs.getString("lastBackgroundAsset", "");
        lastConnectedAddress = prefs.getString("lastAddress", "");
        lastConnectedBle = prefs.getBoolean("lastBle", false);
        autoReconnectEnabled = prefs.getBoolean("autoReconnect", true);
        addDiag("PC server " + serverBase());
        if (!lastConnectedAddress.isEmpty()) {
            addDiag("Saved connection " + (lastConnectedBle ? "BLE " : "Classic ") + lastConnectedAddress);
        }
    }

    private void saveConnectedDevice() {
        if (selectedDevice == null) return;
        lastConnectedAddress = selectedDevice.device.getAddress();
        lastConnectedBle = selectedDevice.ble;
        keepTryingSavedBluetooth = true;
        savedBluetoothRetryCount = 0;
        getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                .edit()
                .putString("lastAddress", lastConnectedAddress)
                .putBoolean("lastBle", lastConnectedBle)
                .putBoolean("autoReconnect", autoReconnectEnabled)
                .apply();
        addDiag("Saved connected device " + currentDeviceLabel());
        popupChat("Saved adapter connected.\nNext app start will auto-connect to it.");
    }

    private void rememberSelectedDeviceCandidate() {
        if (selectedDevice == null) return;
        lastConnectedAddress = selectedDevice.device.getAddress();
        lastConnectedBle = selectedDevice.ble;
        keepTryingSavedBluetooth = true;
        getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                .edit()
                .putString("lastAddress", lastConnectedAddress)
                .putBoolean("lastBle", lastConnectedBle)
                .putBoolean("autoReconnect", autoReconnectEnabled)
                .apply();
        addDiag("Saved adapter candidate " + currentDeviceLabel());
    }

    private boolean looksLikeObdAdapter(String label) {
        String upper = label == null ? "" : label.toUpperCase(Locale.US);
        return upper.contains("VEEPEAK") || upper.contains("OBD") || upper.contains("ELM");
    }

    private boolean selectSavedDeviceIfPresent() {
        if (lastConnectedAddress == null || lastConnectedAddress.isEmpty()) return false;
        for (int i = 0; i < deviceChoices.size(); i++) {
            DeviceChoice choice = deviceChoices.get(i);
            if (lastConnectedAddress.equals(choice.device.getAddress()) && lastConnectedBle == choice.ble) {
                selectDeviceIndex(i, true);
                return true;
            }
        }
        return false;
    }

    private void autoConnectLastDevice(String reason) {
        if ("button".equals(reason) || "menu".equals(reason)) {
            intentionalStop = false;
            keepTryingSavedBluetooth = true;
        }
        if (!autoReconnectEnabled || destroyed || intentionalStop || obdSession != null || connecting) return;
        if (lastConnectedAddress == null || lastConnectedAddress.isEmpty()) {
            addDiag("Auto-connect skipped: no saved adapter");
            popupChat("No saved adapter yet. Tap Scan, then choose VEEPEAK. It will connect right away.");
            return;
        }
        if (selectSavedDeviceIfPresent()) {
            addDiag("Auto-connect last from list after " + reason);
            popupChat("Auto-connecting to saved adapter\n" + currentDeviceLabel());
            connectSelectedDevice(true);
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            addDiag("Auto-connect skipped: Bluetooth off/missing");
            popupChat("Bluetooth is off. Turn Bluetooth on, then tap Last.");
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(lastConnectedAddress);
            selectedDevice = new DeviceChoice(device, lastConnectedBle);
            selectedDeviceIndex = -1;
            updateDeviceReadout();
            addDiag("Auto-connect direct " + (lastConnectedBle ? "BLE " : "Classic ") + lastConnectedAddress + " after " + reason);
            popupChat("Auto-connecting to saved adapter\n" + (lastConnectedBle ? "BLE " : "Classic ") + lastConnectedAddress);
            connectSelectedDevice(true);
        } catch (Exception ex) {
            addDiag("Auto-connect direct failed: " + ex.getMessage());
            popupChat("Saved adapter could not be opened. Tap Scan, choose VEEPEAK, then Connect.");
            scheduleSavedBluetoothRetry(9000);
        }
    }

    private void scheduleSavedBluetoothRetry(long delayMs) {
        if (destroyed || !keepTryingSavedBluetooth || intentionalStop) return;
        if (lastConnectedAddress == null || lastConnectedAddress.isEmpty()) return;
        setCoachStatus("Failed, will retry in " + Math.max(1, delayMs / 1000) + " sec...");
        ui.removeCallbacks(savedBluetoothRetryRunnable);
        ui.postDelayed(savedBluetoothRetryRunnable, delayMs);
    }

    private void scheduleReconnect(String reason) {
        if (!autoReconnectEnabled || intentionalStop || destroyed) return;
        if (lastConnectedAddress == null || lastConnectedAddress.isEmpty()) return;
        addDiag("Auto reconnect scheduled after " + reason);
        setCoachStatus("Failed, will retry in 3 sec...");
        ui.postDelayed(() -> {
            if (destroyed || intentionalStop || obdSession != null || connecting) return;
            if (!selectSavedDeviceIfPresent()) {
                addDiag("Saved device not in list. Refreshing/scanning before reconnect.");
                loadPairedDevices();
                if (!selectSavedDeviceIfPresent()) scanAllDevices();
                ui.postDelayed(() -> {
                    if (!destroyed && obdSession == null && selectSavedDeviceIfPresent()) connectSelectedDevice(true);
                }, 6500);
            } else {
                connectSelectedDevice(true);
            }
            scheduleSavedBluetoothRetry(9000);
        }, 3500);
    }

    private void updateDeviceReadout() {
        if (deviceReadout == null) return;
        deviceReadout.setText("Device: " + currentDeviceLabel());
    }

    private String currentDeviceLabel() {
        if (selectedDeviceIndex >= 0 && selectedDeviceIndex < deviceLabels.size()) {
            return deviceLabels.get(selectedDeviceIndex);
        }
        if (selectedDevice != null) {
            return labelFor(selectedDevice.device, selectedDevice.ble ? "BLE" : "Classic");
        }
        return "none";
    }

    private void addDiag(String line) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(() -> addDiag(line));
            return;
        }
        updateDebugSummary(line);
        String stamp = String.format(Locale.US, "%1$tH:%1$tM:%1$tS", System.currentTimeMillis());
        diagLog.append(stamp).append("  ").append(line).append('\n');
        if (diagLog.length() > 9000) {
            diagLog.delete(0, diagLog.length() - 9000);
        }
        refreshDiagView();
        sendRemoteLog(stamp + "  " + line);
    }

    private void sendRemoteLog(String line) {
        final long seq = ++remoteLogSeq;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String body = "seq=" + seq + "&line=" + URLEncoder.encode(line, "UTF-8");
                byte[] bytes = body.getBytes("UTF-8");
                conn = (HttpURLConnection) new URL(logUrl()).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1200);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream out = conn.getOutputStream();
                out.write(bytes);
                out.close();
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "VeeDashRemoteLog").start();
    }

    private void startRemoteMessagePolling() {
        updateCoachView();
        ui.postDelayed(messagePoller, 1200);
    }

    private String serverBase() {
        return "http://" + pcHost + ":" + pcPort;
    }

    private String logUrl() {
        return serverBase() + "/log";
    }

    private String messageUrl() {
        return serverBase() + "/message";
    }

    private String configUrl() {
        return serverBase() + "/config";
    }

    private String obdSamplesUrl() {
        return serverBase() + "/obd-samples";
    }

    private String cacheBusted(String url) {
        return url + (url.contains("?") ? "&" : "?") + "pull=" + System.currentTimeMillis();
    }

    private String helloUrl(String host) {
        return "http://" + host + ":" + pcPort + "/hello";
    }

    private String backgroundAssetUrl() {
        return serverBase() + "/asset/background";
    }

    private String assetUrl(String asset) throws Exception {
        return serverBase() + "/asset/" + URLEncoder.encode(asset, "UTF-8").replace("+", "%20");
    }

    private void popupChat(String message) {
        coachMessage = message;
        updateCoachView();
        if (diagScroll != null && diagScroll.getVisibility() == View.VISIBLE) {
            hideChatPopup();
            return;
        }
        showChatPopup();
    }

    private void setCoachStatus(String text) {
        coachStatus = text == null || text.trim().isEmpty() ? "-" : text.trim();
        updateCoachView();
    }

    private void updateCoachView() {
        if (coachView == null) return;
        coachView.setText("STATUS\n" + coachStatus + "\n\nCHAT\n" + coachMessage);
    }

    private void showChatPopup() {
        if (coachView == null || !chatPopupEnabled) return;
        ui.removeCallbacks(hideChatRunnable);
        coachView.bringToFront();
        coachView.setVisibility(View.VISIBLE);
        coachView.animate().cancel();
        coachView.setTranslationY(dp(40));
        coachView.setAlpha(0f);
        coachView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .start();
        ui.postDelayed(hideChatRunnable, chatPopupMs);
    }

    private void hideChatPopup() {
        if (coachView == null || coachView.getVisibility() != View.VISIBLE) return;
        coachView.animate().cancel();
        coachView.animate()
                .translationY(dp(46))
                .alpha(0f)
                .setDuration(420)
                .withEndAction(() -> {
                    if (coachView != null) coachView.setVisibility(View.GONE);
                })
                .start();
    }

    private void toggleConfigPanel() {
        if (configPanel == null) return;
        revealControlsTemporarily();
        String device = lastConnectedAddress == null || lastConnectedAddress.isEmpty()
                ? "Saved device: none"
                : "Saved device: " + (lastConnectedBle ? "BLE " : "Classic ") + lastConnectedAddress;
        popupChat("Config panel open\nPC: " + serverBase() + "\nConfig: " + lastConfigVersion + "\n" + device);
        if (configPanel.getVisibility() == View.VISIBLE) {
            hideConfigPanel();
        } else {
            hideChatPopup();
            showMenuHome();
            if (menuDismissLayer != null) {
                menuDismissLayer.setVisibility(View.VISIBLE);
                menuDismissLayer.bringToFront();
            }
            configPanel.setVisibility(View.VISIBLE);
            configPanel.bringToFront();
        }
    }

    private void hideConfigPanel() {
        if (configPanel != null) configPanel.setVisibility(View.GONE);
        if (menuDismissLayer != null) menuDismissLayer.setVisibility(View.GONE);
        ui.postDelayed(hideControlsRunnable, 700);
    }

    private void saveServerSettingsFromPanel() {
        if (hostEdit == null || portEdit == null) {
            showServerMenu();
            return;
        }
        String host = hostEdit.getText().toString().trim();
        int port = pcPort;
        try {
            port = Integer.parseInt(portEdit.getText().toString().trim());
        } catch (Exception ignored) {}
        if (host.isEmpty()) {
            toast("PC IP required");
            return;
        }
        pcHost = host;
        pcPort = port;
        getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                .edit()
                .putString("pcHost", pcHost)
                .putInt("pcPort", pcPort)
                .apply();
        addDiag("Saved PC server " + serverBase());
        popupChat("Saved PC server\n" + serverBase() + "\nPulling latest config...");
        fetchRemoteMessage();
        fetchRemoteConfig(true);
    }

    private void forgetSavedDevice() {
        keepTryingSavedBluetooth = false;
        ui.removeCallbacks(savedBluetoothRetryRunnable);
        lastConnectedAddress = "";
        lastConnectedBle = false;
        getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                .edit()
                .remove("lastAddress")
                .remove("lastBle")
                .apply();
        addDiag("Forgot saved device");
        popupChat("Saved device cleared. Tap Scan, then pick VEEPEAK.");
    }

    private void fetchRemoteMessage() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(cacheBusted(messageUrl())).openConnection();
                conn.setUseCaches(false);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() > 0) out.append('\n');
                    out.append(line);
                }
                reader.close();
                String message = out.toString().trim();
                noteRemoteOk();
                String pullCommand = pullCommandFromMessage(message);
                String displayMessage = displayMessageFromWire(message);
                if (!pullCommand.isEmpty() && !pullCommand.equals(lastPullCommand)) {
                    lastPullCommand = pullCommand;
                    ui.post(() -> {
                        addDiag("PC push command received: " + pullCommand);
                        popupChat(displayMessage.isEmpty() ? "PC editor pushed a dashboard update.\nPulling newest config now." : displayMessage);
                        fetchRemoteConfig(true);
                    });
                } else if (!displayMessage.isEmpty() && !displayMessage.equals(coachMessage)) {
                    ui.post(() -> {
                        popupChat(displayMessage);
                    });
                }
            } catch (Exception ignored) {
                noteRemoteFail("message");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "VeeDashMessagePull").start();
    }

    private String pullCommandFromMessage(String message) {
        if (message == null || message.isEmpty()) return "";
        String[] lines = message.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("VEEDASH_PULL_NOW=")) return trimmed;
        }
        return "";
    }

    private String displayMessageFromWire(String message) {
        if (message == null || message.isEmpty()) return "";
        String[] lines = message.split("\\r?\\n");
        StringBuilder clean = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("VEEDASH_PULL_NOW=")) continue;
            if (clean.length() > 0) clean.append('\n');
            clean.append(line);
        }
        return clean.toString().trim();
    }

    private void fetchRemoteConfig(boolean forceApply) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(cacheBusted(configUrl())).openConnection();
                conn.setUseCaches(false);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line);
                reader.close();
                String config = out.toString().trim();
                noteRemoteOk();
                if (!config.isEmpty() && (forceApply || !config.equals(lastConfig))) {
                    ui.post(() -> {
                        if (forceApply) popupChat("Connected to PC editor\nFresh-pulling the newest staged dashboard now.");
                        addDiag(forceApply ? "Applying PC config from Sync Now, overriding local dashboard file" : "Applying changed PC config");
                        applyRemoteConfig(config);
                    });
                }
            } catch (Exception ignored) {
                noteRemoteFail("config");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "VeeDashConfigPull").start();
    }

    private void noteRemoteOk() {
        remoteFailCount = 0;
    }

    private void noteRemoteFail(String what) {
        remoteFailCount++;
        if (remoteFailCount >= 3) discoverPcServer(what);
    }

    private void discoverPcServer(String reason) {
        if (discoveringPcServer) return;
        discoveringPcServer = true;
        new Thread(() -> {
            try {
                String prefix = localSubnetPrefix();
                if (prefix == null) {
                    addDiag("PC discovery skipped: no WiFi subnet");
                    return;
                }
                addDiag("PC discovery started after " + reason + " failures on " + prefix + "x");
                for (int i = 1; i <= 254 && !destroyed; i++) {
                    String host = prefix + i;
                    if (host.equals(pcHost)) continue;
                    if (isVeeDashServer(host)) {
                        pcHost = host;
                        getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                                .edit()
                                .putString("pcHost", pcHost)
                                .putInt("pcPort", pcPort)
                                .apply();
                        remoteFailCount = 0;
                        String found = serverBase();
                        ui.post(() -> {
                            addDiag("PC server auto-found " + found);
                            popupChat("Found PC server\n" + found + "\nPulling newest config and staged files.");
                            if (hostEdit != null) hostEdit.setText(pcHost);
                            fetchRemoteMessage();
                            fetchRemoteConfig(true);
                        });
                        return;
                    }
                }
                addDiag("PC discovery finished: server not found");
            } finally {
                discoveringPcServer = false;
            }
        }, "VeeDashPcDiscovery").start();
    }

    private boolean isVeeDashServer(String host) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(helloUrl(host)).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(140);
            conn.setReadTimeout(180);
            InputStream in = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            reader.close();
            return out.toString().contains("\"VeeDash\"") && out.toString().contains("pc-server");
        } catch (Exception ex) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String localSubnetPrefix() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    int dot = ip.lastIndexOf('.');
                    if (dot > 0) return ip.substring(0, dot + 1);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void applyRemoteConfig(String config) {
        try {
            JSONObject json = new JSONObject(config);
            lastConfig = config;
            lastConfigVersion = json.optString("updatedAt", json.optString("version", String.valueOf(System.currentTimeMillis())));
            String backgroundAsset = json.optString("backgroundAsset", json.optString("backgroundImage", ""));
            boolean hasLogOverlay = applyViewOverlay(json, "log", diagScroll, 0.30f, 0.30f, 0.52f, 0.36f);
            boolean hasChatOverlay = applyViewOverlay(json, "chat", coachView, 0.80f, 0.83f, 0.28f, 0.18f);
            if (diagScroll != null) diagScroll.setVisibility(json.optBoolean("showLog", true) && hasLogOverlay ? View.VISIBLE : View.GONE);
            chatPopupEnabled = json.optBoolean("showChat", chatPopupEnabled) && hasChatOverlay;
            chatPopupMs = Math.max(2000, Math.min(15000, Math.round(json.optDouble("chatPopupSeconds", chatPopupMs / 1000.0) * 1000.0)));
            if (!chatPopupEnabled) hideChatPopup();
            autoReconnectEnabled = json.optBoolean("autoReconnect", autoReconnectEnabled);
            autoDimEnabled = json.optBoolean("autoDim", autoDimEnabled);
            nightBrightness = (float) clamp01(json.optDouble("nightBrightness", nightBrightness));
            nightExtraDim = (float) clamp01(json.optDouble("nightExtraDim", nightExtraDim));
            if (json.has("toolbarAlpha")) toolbarView.setBackgroundColor(alphaColor(0x000000, json.optDouble("toolbarAlpha", 0.80)));
            if (json.has("logAlpha")) {
                int logColor = alphaColor(0x000000, json.optDouble("logAlpha", 0.93));
                diagView.setBackgroundColor(logColor);
                if (diagScroll != null) diagScroll.setBackgroundColor(logColor);
            }
            if (json.has("chatAlpha")) coachView.setBackgroundColor(alphaColor(0x06141d, json.optDouble("chatAlpha", 0.86)));
            if (json.has("configAlpha")) configPanel.setBackgroundColor(alphaColor(0x101820, json.optDouble("configAlpha", 0.93)));
            dashboard.applyConfig(json);
            updateActivePollPids(json);
            addDiag("Config visual state: " + dashboard.configSummary());
            if (!backgroundAsset.isEmpty() && !backgroundAsset.equals(lastBackgroundAsset)) {
                fetchRemoteBackground(backgroundAsset);
            }
            JSONArray gauges = json.optJSONArray("gauges");
            if (gauges != null) {
                for (int i = 0; i < gauges.length(); i++) {
                    JSONObject gauge = gauges.optJSONObject(i);
                    if (gauge == null) continue;
                    String key = gauge.optString("key", "");
                    String asset = gauge.optString("imageAsset", "");
                    if (!key.isEmpty() && !asset.isEmpty() && !dashboard.hasGaugeAsset(key, asset)) {
                        fetchGaugeAsset(key, asset);
                    }
                }
            }
            addDiag("Applied PC GUI config");
            popupChat("Connected to PC editor\nNew dashboard loaded.\nVersion: " + lastConfigVersion);
        } catch (Exception ex) {
            addDiag("Config parse failed: " + ex.getMessage());
        }
    }

    private void updateActivePollPids(JSONObject config) {
        ArrayList<String> next = new ArrayList<>();
        JSONArray gauges = config.optJSONArray("gauges");
        if (gauges != null) {
            for (int i = 0; i < gauges.length(); i++) {
                JSONObject gauge = gauges.optJSONObject(i);
                if (gauge == null || !gauge.optBoolean("visible", true)) continue;
                String pid = normalizePollPid(gauge.optString("pid", gauge.optString("key", "")));
                if (!pid.isEmpty() && !next.contains(pid)) next.add(pid);
            }
        }
        if (next.isEmpty()) {
            next.add("rpm");
            next.add("speed");
            next.add("coolant");
            next.add("volts");
            next.add("load");
            next.add("throttle");
        }
        synchronized (activePollPids) {
            activePollPids.clear();
            activePollPids.addAll(next);
        }
        addDiag("Polling PIDs: " + next.toString());
    }

    private List<String> currentPollPids() {
        synchronized (activePollPids) {
            if (activePollPids.isEmpty()) {
                ArrayList<String> defaults = new ArrayList<>();
                defaults.add("rpm");
                defaults.add("speed");
                defaults.add("coolant");
                defaults.add("volts");
                defaults.add("load");
                defaults.add("throttle");
                return defaults;
            }
            return new ArrayList<>(activePollPids);
        }
    }

    private static String normalizePollPid(String value) {
        if (value == null) return "";
        String key = value.trim().toLowerCase(Locale.US).replace(" ", "");
        if (key.startsWith("rpm") || "010c".equals(key)) return "rpm";
        if (key.startsWith("speed") || "010d".equals(key)) return "speed";
        if (key.startsWith("coolant") || "0105".equals(key)) return "coolant";
        if (key.startsWith("volts") || "0142".equals(key)) return "volts";
        if (key.startsWith("load") || "0104".equals(key)) return "load";
        if (key.startsWith("throttle") || "0111".equals(key)) return "throttle";
        String compact = key.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
        if (compact.length() == 2) compact = "01" + compact;
        return compact.matches("01[0-9A-F]{2}") ? compact : "";
    }

    private boolean applyViewOverlay(JSONObject config, String type, View view, float defaultX, float defaultY, float defaultW, float defaultH) {
        if (view == null) return false;
        JSONObject overlay = findOverlay(config, type);
        if (overlay == null || !overlay.optBoolean("visible", true)) return false;
        int screenW = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        float x = (float) clamp01(overlay.optDouble("x", defaultX));
        float y = (float) clamp01(overlay.optDouble("y", defaultY));
        float w = (float) clamp01(overlay.optDouble("w", defaultW));
        float h = (float) clamp01(overlay.optDouble("h", defaultH));
        int width = Math.max(dp(90), Math.round(screenW * w));
        int height = Math.max(dp(50), Math.round(screenH * h));
        int left = Math.max(0, Math.min(screenW - width, Math.round(screenW * x - width / 2f)));
        int top = Math.max(0, Math.min(screenH - height, Math.round(screenH * y - height / 2f)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        params.leftMargin = left;
        params.topMargin = top;
        view.setLayoutParams(params);
        return true;
    }

    private JSONObject findOverlay(JSONObject config, String type) {
        JSONArray overlays = config.optJSONArray("overlays");
        if (overlays == null) return null;
        for (int i = 0; i < overlays.length(); i++) {
            JSONObject overlay = overlays.optJSONObject(i);
            if (overlay == null) continue;
            String overlayType = overlay.optString("type", overlay.optString("key", ""));
            if (type.equals(overlayType)) return overlay;
        }
        return null;
    }

    private void applyAutoDim() {
        boolean night = isNightMode();
        boolean active = autoDimEnabled && night;
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = active ? Math.max(0.05f, nightBrightness) : -1f;
        getWindow().setAttributes(params);
        if (dashboard != null) dashboard.setAutoDim(active, active ? nightExtraDim : 0f);
        if (active != lastAutoDimActive) {
            lastAutoDimActive = active;
            addDiag("Auto dim " + (active ? "ON" : "OFF") + ". Android nightMode=" + night);
            popupChat(active ? "Auto dim is ON\nCar lights/night mode detected." : "Auto dim is OFF\nDay mode detected.");
        }
    }

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void fetchRemoteBackground(String assetKey) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(backgroundAssetUrl()).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() != 200) {
                    addDiag("Background asset not available: HTTP " + conn.getResponseCode());
                    return;
                }
                File file = new File(getFilesDir(), "background");
                InputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream out = new FileOutputStream(file);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                lastBackgroundAsset = assetKey;
                getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                        .edit()
                        .putString("lastBackgroundAsset", lastBackgroundAsset)
                        .apply();
                ui.post(() -> {
                    dashboard.loadBackground();
                    addDiag("Background staged from PC: " + assetKey);
                    popupChat("New staged background loaded\n" + assetKey + "\nConfig: " + lastConfigVersion);
                });
            } catch (Exception ex) {
                addDiag("Background pull failed: " + ex.getMessage());
                noteRemoteFail("background");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "VeeDashBackgroundPull").start();
    }

    private void fetchGaugeAsset(String gaugeKey, String asset) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(assetUrl(asset)).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() != 200) {
                    addDiag("Gauge asset not available: " + gaugeKey + " HTTP " + conn.getResponseCode());
                    return;
                }
                File file = new File(getFilesDir(), "gauge_" + gaugeKey);
                InputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream out = new FileOutputStream(file);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                ui.post(() -> {
                    dashboard.loadGaugeArt(gaugeKey);
                    addDiag("Gauge art staged: " + gaugeKey + " " + asset);
                    popupChat("Dial art loaded\n" + gaugeKey + ": " + asset);
                });
            } catch (Exception ex) {
                addDiag("Gauge asset pull failed: " + gaugeKey + " " + ex.getMessage());
                noteRemoteFail("gauge asset");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "VeeDashGaugeAssetPull").start();
    }

    private void refreshDiagView() {
        if (diagView == null) return;
        String text = diagLog.toString();
        diagView.setText(debugHeader() + newestLogLines(text, 140));
        if (diagScroll != null && diagScroll.getVisibility() == View.VISIBLE) {
            diagScroll.post(() -> diagScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private int alphaColor(int rgb, double alpha) {
        int a = (int) Math.round(clamp01(alpha) * 255.0);
        return (a << 24) | (rgb & 0x00ffffff);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String newestLogLines(String text, int maxLines) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\n");
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, lines.length - maxLines);
        for (int i = start; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            out.append(lines[i]).append('\n');
        }
        return out.toString();
    }

    private String debugHeader() {
        return "DEBUG STATUS\n"
                + "version: " + APP_VERSION + "\n"
                + "phase: " + dbgPhase + "\n"
                + "selected: " + dbgSelected + "\n"
                + "scan: " + dbgScan + "\n"
                + "services: " + dbgServices + "\n"
                + "write: " + shortUuid(dbgWrite) + "  notify: " + shortUuid(dbgNotify) + "\n"
                + "last tx: " + dbgLastTx + "\n"
                + "last rx: " + dbgLastRx + "\n"
                + "error: " + dbgError + "\n"
                + "wifi log: " + logUrl() + "\n"
                + "pc config: " + lastConfigVersion + "\n"
                + "device #: " + (selectedDeviceIndex < 0 ? "none" : (selectedDeviceIndex + 1) + "/" + deviceChoices.size()) + "\n"
                + "NEWEST LOGS\n";
    }

    private String shortUuid(String value) {
        if (value == null || value.equals("-")) return "-";
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private void updateDebugSummary(String line) {
        if (line == null) return;
        if (line.contains("BLE scan started using modern")) {
            dbgPhase = "scanning";
            dbgScan = "modern running";
        } else if (line.contains("Legacy BLE scan started")) {
            dbgPhase = "legacy scanning";
            dbgScan = "legacy running";
        } else if (line.startsWith("BLE result:")) {
            dbgScan = "found " + compactLine(line.replace("BLE result:", ""));
        } else if (line.startsWith("BLE result(")) {
            dbgScan = "found " + compactLine(line);
        } else if (line.startsWith("Classic startDiscovery")) {
            dbgPhase = "classic discovery";
            dbgScan = compactLine(line);
        } else if (line.startsWith("Classic found:")) {
            dbgScan = compactLine(line);
        } else if (line.startsWith("Classic discovery finished")) {
            dbgScan = compactLine(line);
        } else if (line.startsWith("Classic bond state")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Bond state:")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Pairing request")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("setPin")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Trying secure") || line.startsWith("Trying insecure")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Secure RFCOMM") || line.startsWith("Insecure RFCOMM")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("AutoTry")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Bond state before AutoTry")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("createBond")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Legacy startLeScan")) {
            dbgScan = compactLine(line);
        } else if (line.startsWith("Location services OFF")) {
            dbgError = compactLine(line);
        } else if (line.startsWith("Added BLE")) {
            dbgSelected = compactLine(line.replace("Added", ""));
        } else if (line.startsWith("Connecting to")) {
            dbgPhase = "connecting";
            dbgSelected = compactLine(line.replace("Connecting to", ""));
        } else if (line.startsWith("BLE state")) {
            dbgPhase = compactLine(line);
        } else if (line.startsWith("Services discovered")) {
            dbgServices = compactLine(line.replace("Services discovered", ""));
            dbgPhase = "services discovered";
        } else if (line.startsWith("Using write=")) {
            int notifyIndex = line.indexOf(" notify=");
            if (notifyIndex > 0) {
                dbgWrite = line.substring("Using write=".length(), notifyIndex).trim();
                dbgNotify = line.substring(notifyIndex + " notify=".length()).trim();
            }
            dbgPhase = "pipe selected";
        } else if (line.startsWith("BLE TX ")) {
            dbgLastTx = compactLine(line.replace("BLE TX", ""));
        } else if (line.startsWith("BLE RX ")) {
            dbgLastRx = compactLine(line.replace("BLE RX", ""));
        } else if (line.startsWith("BLE connected")) {
            dbgPhase = "connected";
            dbgError = "-";
        } else if (line.startsWith("Values:") || line.startsWith("DATA ")) {
            dbgPhase = "reading data";
            dbgLastRx = compactLine(line);
        } else if (line.toLowerCase(Locale.US).contains("error")
                || line.toLowerCase(Locale.US).contains("failed")
                || line.toLowerCase(Locale.US).contains("timeout")) {
            dbgError = compactLine(line);
            dbgPhase = "problem";
        }
    }

    private String compactLine(String text) {
        String out = text == null ? "-" : text.replace('\n', ' ').replace('\r', ' ').trim();
        return out.length() > 70 ? out.substring(0, 70) : out;
    }

    private void ensurePermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (!needed.isEmpty()) {
            addDiag("Requesting permissions: " + needed);
            requestPermissions(needed.toArray(new String[0]), REQUEST_PERMS);
        } else {
            addDiag("Bluetooth/location permissions already granted or not required");
        }
        if (Build.VERSION.SDK_INT >= 23 && !isLocationEnabled()) {
            addDiag("Location services are OFF. Android may block BLE scan.");
        }
    }

    private boolean isLocationEnabled() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) return false;
        try {
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            addDiag("Location state check failed: " + ex.getMessage());
            return false;
        }
    }

    private void loadPairedDevices() {
        deviceChoices.clear();
        deviceLabels.clear();
        addDiag("Refreshing paired Classic devices");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            addDiag("No Bluetooth adapter reported by Android");
            deviceLabels.add("No Bluetooth adapter");
        } else if (!adapter.isEnabled()) {
            addDiag("Bluetooth adapter exists but is disabled");
            deviceLabels.add("Bluetooth is off");
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } else {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            addDiag("Bluetooth enabled. Paired devices: " + bonded.size());
            for (BluetoothDevice device : bonded) {
                addDiag("Paired: " + labelFor(device, "Classic"));
                addClassicDevice(device, "Classic");
            }
            if (deviceLabels.isEmpty()) deviceLabels.add("Tap Scan for Veepeak BLE+");
        }

        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceLabels);
        deviceListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(deviceListAdapter);
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < deviceChoices.size() && position != selectedDeviceIndex) {
                    selectDeviceIndex(position, false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                selectedDevice = null;
                selectedDeviceIndex = -1;
                updateDeviceReadout();
            }
        });
        if (selectSavedDeviceIfPresent()) {
            addDiag("Selected saved device on refresh");
        } else if (!deviceChoices.isEmpty()) {
            selectDeviceIndex(0, false);
        } else {
            selectDeviceIndex(-1, false);
        }
    }

    private String labelFor(BluetoothDevice device, String type) {
        String name = device.getName();
        if (name == null || name.trim().isEmpty()) name = "Unknown";
        return type + "  " + name + "  " + device.getAddress();
    }

    private void addClassicDevice(BluetoothDevice device, String type) {
        if (containsDevice(device.getAddress(), false)) return;
        if (deviceChoices.isEmpty() && deviceLabels.size() == 1 && deviceLabels.get(0).startsWith("Tap Scan")) {
            deviceLabels.clear();
        }
        deviceChoices.add(new DeviceChoice(device, false));
        deviceLabels.add(labelFor(device, type));
        if (deviceListAdapter != null) deviceListAdapter.notifyDataSetChanged();
        addDiag("Added " + labelFor(device, type));
        maybeAutoSelect(deviceChoices.size() - 1);
    }

    private void maybeAutoSelect(int index) {
        if (index < 0 || index >= deviceChoices.size()) return;
        String label = deviceLabels.get(index).toUpperCase(Locale.US);
        if (selectedDeviceIndex < 0 || label.contains("VEEPEAK") || label.contains("OBD")) {
            selectDeviceIndex(index, true);
        } else {
            updateDeviceReadout();
        }
        if (lastConnectedAddress != null && !lastConnectedAddress.isEmpty()) {
            selectSavedDeviceIfPresent();
        }
    }

    private void connectSelectedDevice() {
        connectSelectedDevice(false);
    }

    private void connectSelectedDevice(boolean autoTry) {
        if (selectedDevice == null) {
            addDiag("Connect pressed with no selected device");
            toast("Tap Scan, then choose VEEPEAK");
            return;
        }
        boolean forceAutoTry = !selectedDevice.ble && (autoTry
                || selectedDevice.device.getBondState() != BluetoothDevice.BOND_BONDED
                || looksLikeObdAdapter(currentDeviceLabel()));
        if (forceAutoTry && !autoTry) {
            addDiag("Using AutoTry path for selected OBD adapter");
        }
        rememberSelectedDeviceCandidate();
        intentionalStop = false;
        connecting = true;
        addDiag((forceAutoTry ? "AutoTry connecting to " : "Connecting to ") + currentDeviceLabel());
        status.setText(forceAutoTry ? "AutoTry..." : selectedDevice.ble ? "Connecting BLE..." : "Connecting...");
        setCoachStatus(forceAutoTry ? "AutoTry..." : selectedDevice.ble ? "Connecting BLE..." : "Connecting...");
        ObdLink.Listener listener = new ObdLink.Listener() {
            @Override public void onStatus(String text) {
                ui.post(() -> {
                    status.setText(text);
                    setCoachStatus(text);
                    addDiag(text);
                    if (text.toUpperCase(Locale.US).contains("CONNECTED")) {
                        connecting = false;
                        saveConnectedDevice();
                    }
                });
            }
            @Override public void onValues(Map<String, Float> values) {
                ui.post(() -> {
                    savedBluetoothRetryCount = 0;
                    dashboard.updateValues(values);
                    addDataDiag(values);
                });
            }
            @Override public void onStopped(String reason) {
                ui.post(() -> {
                    connecting = false;
                    status.setText(reason);
                    setCoachStatus(reason);
                    addDiag(reason);
                    obdSession = null;
                    scheduleReconnect(reason);
                });
            }
            @Override public void onExperimentalScanDone(boolean pcSubmitted) {
                if (pcSubmitted) {
                    ui.post(() -> {
                        pcSampleRunQueued = false;
                        addDiag("PC sample scan complete. Auto-syncing debug log to PC.");
                        sendLogSnapshotToPc();
                    });
                }
            }
        };
        obdSession = selectedDevice.ble
                ? new BleObdSession(this, selectedDevice.device, listener, this::addDiag, this::currentPollPids)
                : new ObdSession(selectedDevice.device, listener, this::addDiag, selectedPin, forceAutoTry, this::currentPollPids);
        obdSession.start();
    }

    private void addDataDiag(Map<String, Float> values) {
        if (values == null || values.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastDataLogMs < 950) return;
        lastDataLogMs = now;
        addDiag(formatDataLine(values));
    }

    private String formatDataLine(Map<String, Float> values) {
        StringBuilder out = new StringBuilder("DATA");
        appendDataValue(out, values, "rpm", "rpm", "%.0f");
        appendDataValue(out, values, "speed", "mph", "%.0f");
        appendDataValue(out, values, "speedKph", "kphRaw", "%.0f");
        appendDataValue(out, values, "coolant", "coolantF", "%.0f");
        appendDataValue(out, values, "volts", "volts", "%.1f");
        appendDataValue(out, values, "load", "load", "%.0f");
        appendDataValue(out, values, "throttle", "throttle", "%.0f");
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            String key = entry.getKey();
            if ("rpm".equals(key) || "speed".equals(key) || "speedKph".equals(key)
                    || "coolant".equals(key) || "volts".equals(key)
                    || "load".equals(key) || "throttle".equals(key)) continue;
            out.append(' ')
                    .append(key)
                    .append('=')
                    .append(String.format(Locale.US, "%.1f", entry.getValue()));
        }
        return out.toString();
    }

    private void appendDataValue(StringBuilder out, Map<String, Float> values, String key, String label, String fmt) {
        Float value = values.get(key);
        if (value == null) return;
        out.append(' ')
                .append(label)
                .append('=')
                .append(String.format(Locale.US, fmt, value));
    }

    private void scanAllDevices() {
        addDiag("Scan requested: Classic discovery + BLE");
        startClassicDiscovery();
        scanBleDevices();
    }

    private void startClassicDiscovery() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            addDiag("Classic discovery unavailable: Bluetooth off/missing");
            return;
        }
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            filter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(classicReceiver, filter);
            receiverRegistered = true;
        }
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        classicDiscovering = adapter.startDiscovery();
        addDiag("Classic startDiscovery returned " + classicDiscovering);
        if (classicDiscovering) status.setText("Scanning Classic + BLE...");
    }

    private void stopDiscovery() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try {
                if (adapter.isDiscovering()) adapter.cancelDiscovery();
            } catch (Exception ignored) {}
        }
        classicDiscovering = false;
    }

    private final BroadcastReceiver classicReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                addDiag("Classic found: " + labelFor(device, "ClassicFound") + " rssi=" + rssi);
                addClassicDevice(device, "ClassicFound");
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                addDiag("Bond state: " + (device == null ? "unknown" : labelFor(device, "Classic")) + " " + previous + " -> " + state);
            } else if ("android.bluetooth.device.action.PAIRING_REQUEST".equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addDiag("Pairing request for " + (device == null ? "unknown" : labelFor(device, "Classic")) + " using PIN " + (selectedPin.isEmpty() ? "off" : selectedPin));
                if (device != null && !selectedPin.isEmpty()) {
                    trySetPin(device, selectedPin);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                classicDiscovering = false;
                addDiag("Classic discovery finished. Device choices: " + deviceChoices.size());
            }
        }
    };

    private void trySetPin(BluetoothDevice device, String pin) {
        try {
            Method setPin = BluetoothDevice.class.getMethod("setPin", byte[].class);
            boolean pinOk = (Boolean) setPin.invoke(device, new Object[]{pin.getBytes("UTF-8")});
            addDiag("setPin(" + pin + ") returned " + pinOk);
            try {
                Method confirm = BluetoothDevice.class.getMethod("setPairingConfirmation", boolean.class);
                addDiag("setPairingConfirmation returned " + confirm.invoke(device, true));
            } catch (Exception ex) {
                addDiag("setPairingConfirmation unavailable: " + ex.getMessage());
            }
        } catch (Exception ex) {
            addDiag("setPin failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void scanBleDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            addDiag("Scan requested but Bluetooth is disabled/unavailable");
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }
        if (Build.VERSION.SDK_INT < 21) {
            addDiag("Scan failed: Android version below BLE scan support");
            toast("BLE scan needs Android 5 or newer");
            return;
        }
        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            addDiag("Scan failed: getBluetoothLeScanner returned null");
            toast("BLE scanner unavailable");
            return;
        }
        if (scanning) {
            addDiag("Stopping previous BLE scan");
            stopAnyScan(adapter);
            scanning = false;
        }
        status.setText("Scanning BLE...");
        if (Build.VERSION.SDK_INT >= 23 && !isLocationEnabled()) {
            addDiag("Location services OFF. Turn on Location if scan fails.");
        }
        addDiag("BLE scan started using modern scanner");
        scanLoggedAddresses.clear();
        scanning = true;
        bleScanner.startScan(scanCallback);
        ui.postDelayed(() -> {
            if (scanning) {
                stopAnyScan(adapter);
                scanning = false;
                status.setText("Scan done");
                addDiag("BLE scan done. Device choices: " + deviceChoices.size());
            }
        }, 9000);
    }

    private void startLegacyScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        addDiag("Legacy BLE scan started");
        status.setText("Legacy BLE scan...");
        scanning = true;
        legacyScanning = true;
        scanLoggedAddresses.clear();
        boolean ok = adapter.startLeScan(legacyScanCallback);
        addDiag("Legacy startLeScan returned " + ok);
        ui.postDelayed(() -> {
            if (legacyScanning) {
                adapter.stopLeScan(legacyScanCallback);
                legacyScanning = false;
                scanning = false;
                status.setText("Legacy scan done");
                addDiag("Legacy BLE scan done. Device choices: " + deviceChoices.size());
            }
        }, 9000);
    }

    private void stopAnyScan(BluetoothAdapter adapter) {
        try {
            if (bleScanner != null) bleScanner.stopScan(scanCallback);
        } catch (Exception ex) {
            addDiag("Modern stopScan error: " + ex.getMessage());
        }
        try {
            if (legacyScanning && adapter != null) adapter.stopLeScan(legacyScanCallback);
        } catch (Exception ex) {
            addDiag("Legacy stopLeScan error: " + ex.getMessage());
        }
        legacyScanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            String address = device.getAddress();
            if (containsDevice(address, true)) return;
            if (name == null || name.trim().isEmpty()) {
                name = result.getScanRecord() == null ? "" : result.getScanRecord().getDeviceName();
            }
            handleScanDevice(device, name, result.getRssi(), "modern");
        }

        @Override public void onScanFailed(int errorCode) {
            ui.post(() -> {
                status.setText("Scan failed: " + errorCode);
                addDiag("BLE scan failed with code " + errorCode);
                scanning = false;
                if (bleScanner != null) {
                    try { bleScanner.stopScan(scanCallback); } catch (Exception ignored) {}
                }
                if (errorCode == 3) {
                    addDiag("Code 3 means Android scanner registration failed; trying legacy scan");
                    ui.postDelayed(() -> startLegacyScan(), 700);
                }
            });
        }
    };

    private final BluetoothAdapter.LeScanCallback legacyScanCallback = (device, rssi, scanRecord) -> {
        String name = device.getName();
        ui.post(() -> handleScanDevice(device, name, rssi, "legacy"));
    };

    private void handleScanDevice(BluetoothDevice device, String name, int rssi, String source) {
        String address = device.getAddress();
        if (containsDevice(address, true)) return;
        if (name == null) name = "";
        if (!scanLoggedAddresses.contains(address)) {
            scanLoggedAddresses.add(address);
            addDiag("BLE result(" + source + "): name=" + name + " address=" + address + " rssi=" + rssi);
        }
        addBleDevice(device);
    }

    private boolean containsDevice(String address, boolean ble) {
        for (DeviceChoice choice : deviceChoices) {
            if (choice.device.getAddress().equals(address) && choice.ble == ble) return true;
        }
        return false;
    }

    private void addBleDevice(BluetoothDevice device) {
        if (containsDevice(device.getAddress(), true)) return;
        if (deviceChoices.isEmpty() && deviceLabels.size() == 1 && deviceLabels.get(0).startsWith("Tap Scan")) {
            deviceLabels.clear();
        }
        deviceChoices.add(new DeviceChoice(device, true));
        deviceLabels.add(labelFor(device, "BLE"));
        deviceListAdapter.notifyDataSetChanged();
        status.setText("Found " + labelFor(device, "BLE"));
        addDiag("Added " + labelFor(device, "BLE"));
        maybeAutoSelect(deviceChoices.size() - 1);
    }

    private void stopObd() {
        intentionalStop = false;
        keepTryingSavedBluetooth = true;
        if (obdSession != null) {
            obdSession.close();
            obdSession = null;
            status.setText("Disconnected");
        }
        scheduleSavedBluetoothRetry(3000);
    }

    private void pickBackground() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_BACKGROUND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND && resultCode == RESULT_OK && data != null) {
            try {
                copyBackground(data.getData());
                dashboard.loadBackground();
            } catch (IOException ex) {
                toast("Could not load image: " + ex.getMessage());
            }
        }
    }

    private void copyBackground(Uri uri) throws IOException {
        File out = new File(getFilesDir(), "background");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static class DashboardView extends View {
        private static final String PREFS = "dashboard";
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final SharedPreferences prefs;
        private final List<Gauge> gauges = new ArrayList<>();
        private final List<OverlayItem> overlays = new ArrayList<>();
        private final Map<String, Float> values = new LinkedHashMap<>();
        private final Map<String, ArrayList<Float>> history = new LinkedHashMap<>();
        private Bitmap background;
        private Movie animatedBackground;
        private long animatedBackgroundStart;
        private boolean editMode;
        private Runnable tapListener;
        private int accentColor = 0xff1fb6ff;
        private int gaugeFillColor = 0xff05080c;
        private int backgroundColor = Color.BLACK;
        private float gaugeAlpha = 0.73f;
        private float backgroundDimAlpha = 0.80f;
        private boolean autoDimActive;
        private float autoDimAlpha;
        private Gauge active;
        private long lastTapTime;
        private Gauge lastTapGauge;

        public DashboardView(Context context) {
            super(context);
            prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            paint.setSubpixelText(true);
            paint.setLinearText(false);
            paint.setHinting(Paint.HINTING_ON);
            backgroundColor = prefs.getInt("backgroundColor", Color.BLACK);
            accentColor = prefs.getInt("accentColor", 0xff1fb6ff);
            gaugeFillColor = prefs.getInt("gaugeFillColor", 0xff05080c);
            gaugeAlpha = prefs.getFloat("gaugeAlpha", 0.73f);
            backgroundDimAlpha = prefs.getFloat("backgroundDimAlpha", 0.80f);
            setBackgroundColor(backgroundColor);
            resetDefaultGauges(false);
            loadBackground();
            for (Gauge gauge : gauges) loadGaugeArt(gauge.key);
        }

        public boolean isEditMode() {
            return editMode;
        }

        public void setEditMode(boolean editMode) {
            this.editMode = editMode;
            if (!editMode) saveLayout();
            invalidate();
        }

        public void setTapListener(Runnable tapListener) {
            this.tapListener = tapListener;
        }

        public void updateValues(Map<String, Float> next) {
            values.putAll(next);
            for (Map.Entry<String, Float> entry : next.entrySet()) {
                ArrayList<Float> series = history.get(entry.getKey());
                if (series == null) {
                    series = new ArrayList<>();
                    history.put(entry.getKey(), series);
                }
                series.add(entry.getValue());
                while (series.size() > 48) series.remove(0);
            }
            invalidate();
        }

        public void setAutoDim(boolean active, float alpha) {
            autoDimActive = active;
            autoDimAlpha = clamp(alpha, 0f, 0.85f);
            invalidate();
        }

        public void resetLayout() {
            resetDefaultGauges(true);
            invalidate();
        }

        public void applyConfig(JSONObject json) throws Exception {
            if (json.has("backgroundColor")) backgroundColor = Color.parseColor(json.getString("backgroundColor"));
            if (json.has("accentColor")) accentColor = Color.parseColor(json.getString("accentColor"));
            if (json.has("gaugeFillColor")) gaugeFillColor = Color.parseColor(json.getString("gaugeFillColor"));
            gaugeAlpha = clamp((float) json.optDouble("gaugeAlpha", gaugeAlpha), 0f, 1f);
            backgroundDimAlpha = clamp((float) json.optDouble("backgroundDimAlpha", backgroundDimAlpha), 0f, 1f);
            JSONArray arr = json.optJSONArray("gauges");
            if (arr != null) {
                gauges.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.getJSONObject(i);
                    String pid = normalizeGaugePid(g.optString("pid", g.optString("key", "rpm")));
                    Gauge gauge = new Gauge(
                            g.optString("key", pid),
                            g.optString("label", defaultGaugeLabel(pid)),
                            pid,
                            clamp((float) g.optDouble("x", 0.5), 0.06f, 0.94f),
                            clamp((float) g.optDouble("y", 0.5), 0.12f, 0.92f),
                            clamp((float) g.optDouble("size", 0.22), 0.06f, 0.48f),
                            g.optBoolean("visible", true));
                    if (g.has("x")) gauge.x = clamp((float) g.getDouble("x"), 0.06f, 0.94f);
                    if (g.has("y")) gauge.y = clamp((float) g.getDouble("y"), 0.12f, 0.92f);
                    if (g.has("size")) gauge.size = clamp((float) g.getDouble("size"), 0.06f, 0.48f);
                    if (g.has("mode")) gauge.mode = g.optString("mode", gauge.mode);
                    if (g.has("visible")) gauge.visible = g.optBoolean("visible", true);
                    if (g.has("showBorder")) gauge.showBorder = g.optBoolean("showBorder", gauge.showBorder);
                    if (g.has("layer")) gauge.layer = g.optInt("layer", gauge.layer);
                    if (g.has("barThickness")) gauge.barThickness = clamp((float) g.optDouble("barThickness", gauge.barThickness), 0.08f, 0.50f);
                    if (g.has("imageAsset")) gauge.imageAsset = g.optString("imageAsset", "");
                    JSONObject reactive = g.optJSONObject("reactive");
                    if (reactive != null) {
                        gauge.reactiveGrow = reactive.optBoolean("grow", gauge.reactiveGrow);
                        gauge.reactiveImageGrow = reactive.optBoolean("imageGrow", gauge.reactiveImageGrow);
                        gauge.reactiveTint = reactive.optBoolean("tint", gauge.reactiveTint);
                        gauge.valueMin = (float) reactive.optDouble("valueMin", gauge.valueMin);
                        gauge.valueMax = (float) reactive.optDouble("valueMax", gauge.valueMax);
                        gauge.scaleMax = clamp((float) reactive.optDouble("scaleMax", gauge.scaleMax), 1f, 1.8f);
                        gauge.midAt = (float) reactive.optDouble("midAt", gauge.midAt);
                        gauge.highAt = (float) reactive.optDouble("highAt", gauge.highAt);
                        gauge.lowColor = parseColor(reactive.optString("lowColor", ""), gauge.lowColor);
                        gauge.midColor = parseColor(reactive.optString("midColor", ""), gauge.midColor);
                        gauge.highColor = parseColor(reactive.optString("highColor", ""), gauge.highColor);
                    }
                    gauges.add(gauge);
                    loadGaugeArt(gauge.key);
                }
            }
            JSONArray overlays = json.optJSONArray("overlays");
            this.overlays.clear();
            if (overlays != null) {
                for (int i = 0; i < overlays.length(); i++) {
                    JSONObject overlay = overlays.optJSONObject(i);
                    if (overlay == null) continue;
                    String type = overlay.optString("type", overlay.optString("key", ""));
                    if ("clock".equals(type) || "date".equals(type)) {
                        OverlayItem item = new OverlayItem();
                        item.key = overlay.optString("key", type);
                        item.type = type;
                        item.x = clamp((float) overlay.optDouble("x", 0.5), 0.02f, 0.98f);
                        item.y = clamp((float) overlay.optDouble("y", 0.5), 0.06f, 0.96f);
                        item.w = clamp((float) overlay.optDouble("w", "clock".equals(type) ? 0.24f : 0.24f), 0.08f, 0.90f);
                        item.h = clamp((float) overlay.optDouble("h", "clock".equals(type) ? 0.12f : 0.10f), 0.05f, 0.70f);
                        item.visible = overlay.optBoolean("visible", true);
                        item.showBorder = overlay.optBoolean("showBorder", true);
                        item.layer = overlay.optInt("layer", "clock".equals(type) ? 60 : 59);
                        item.mode = overlay.optString("mode", "clock".equals(type) ? "time" : "yyyy_mm_dd");
                        this.overlays.add(item);
                    }
                }
            }
            setBackgroundColor(backgroundColor);
            saveLayout();
            invalidate();
        }

        public String configSummary() {
            Gauge rpm = gaugeByKey("rpm");
            String rpmText = rpm == null ? "rpm=missing" : String.format(Locale.US, "rpm x=%.3f y=%.3f size=%.3f visible=%s", rpm.x, rpm.y, rpm.size, rpm.visible);
            return String.format(Locale.US, "accent=#%06X fill=#%06X bg=#%06X gaugeAlpha=%.2f %s",
                    accentColor & 0x00ffffff,
                    gaugeFillColor & 0x00ffffff,
                    backgroundColor & 0x00ffffff,
                    gaugeAlpha,
                    rpmText);
        }

        public boolean hasGaugeAsset(String key, String asset) {
            Gauge gauge = gaugeByKey(key);
            if (gauge == null || asset == null || asset.isEmpty()) return true;
            File file = new File(getContext().getFilesDir(), "gauge_" + key);
            return asset.equals(gauge.loadedAsset) && file.exists() && (gauge.image != null || gauge.movie != null);
        }

        public void loadGaugeArt(String key) {
            Gauge gauge = gaugeByKey(key);
            if (gauge == null) return;
            File file = new File(getContext().getFilesDir(), "gauge_" + key);
            gauge.image = null;
            gauge.movie = null;
            gauge.movieStart = 0;
            gauge.loadedAsset = "";
            if (file.exists()) {
                try {
                    InputStream movieIn = new BufferedInputStream(new java.io.FileInputStream(file));
                    gauge.movie = Movie.decodeStream(movieIn);
                    movieIn.close();
                } catch (Exception ignored) {}
                if (gauge.movie == null) {
                    gauge.image = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
                if (gauge.image != null || gauge.movie != null) gauge.loadedAsset = gauge.imageAsset == null ? "" : gauge.imageAsset;
            }
            invalidate();
        }

        public void loadBackground() {
            File file = new File(getContext().getFilesDir(), "background");
            background = null;
            animatedBackground = null;
            animatedBackgroundStart = 0;
            if (file.exists()) {
                try {
                    InputStream movieIn = new BufferedInputStream(new java.io.FileInputStream(file));
                    animatedBackground = Movie.decodeStream(movieIn);
                    movieIn.close();
                } catch (Exception ignored) {}
                if (animatedBackground == null) {
                    background = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }
            invalidate();
        }

        private void resetDefaultGauges(boolean save) {
            gauges.clear();
            gauges.add(loadGauge("rpm", "RPM", "rpm", 0.18f, 0.34f, 0.28f));
            gauges.add(loadGauge("speed", "MPH", "speed", 0.50f, 0.34f, 0.28f));
            gauges.add(loadGauge("coolant", "Coolant", "coolant", 0.82f, 0.34f, 0.24f));
            gauges.add(loadGauge("volts", "Volts", "volts", 0.22f, 0.72f, 0.22f));
            gauges.add(loadGauge("load", "Load", "load", 0.50f, 0.72f, 0.22f));
            gauges.add(loadGauge("throttle", "Throttle", "throttle", 0.78f, 0.72f, 0.22f));
            if (save) saveLayout();
        }

        private String normalizeGaugePid(String value) {
            String key = value == null ? "" : value.toLowerCase(Locale.US);
            if (key.startsWith("rpm")) return "rpm";
            if (key.startsWith("speed")) return "speed";
            if (key.startsWith("coolant")) return "coolant";
            if (key.startsWith("volts")) return "volts";
            if (key.startsWith("load")) return "load";
            if (key.startsWith("throttle")) return "throttle";
            return normalizePollPid(key);
        }

        private String defaultGaugeLabel(String pid) {
            if ("rpm".equals(pid)) return "RPM";
            if ("speed".equals(pid)) return "MPH";
            if ("coolant".equals(pid)) return "Coolant";
            if ("volts".equals(pid)) return "Volts";
            if ("load".equals(pid)) return "Load";
            if ("throttle".equals(pid)) return "Throttle";
            if ("0101".equals(pid)) return "Monitor";
            if ("0103".equals(pid)) return "Fuel Sys";
            if ("0106".equals(pid)) return "STFT B1";
            if ("0107".equals(pid)) return "LTFT B1";
            if ("010B".equals(pid)) return "MAP";
            if ("010E".equals(pid)) return "Timing";
            if ("010F".equals(pid)) return "IAT";
            if ("0113".equals(pid)) return "O2 Present";
            if ("0115".equals(pid)) return "O2 S2";
            if ("011C".equals(pid)) return "OBD Std";
            if ("011F".equals(pid)) return "Run Time";
            if ("0120".equals(pid)) return "PIDs 21-40";
            return pid == null ? "" : pid.toUpperCase(Locale.US);
        }

        private Gauge loadGauge(String key, String label, String pid, float x, float y, float size) {
            Gauge gauge = new Gauge(
                    key,
                    label,
                    pid,
                    prefs.getFloat(key + "_x", x),
                    prefs.getFloat(key + "_y", y),
                    prefs.getFloat(key + "_size", size),
                    prefs.getBoolean(key + "_visible", true));
            gauge.imageAsset = prefs.getString(key + "_asset", "");
            gauge.mode = prefs.getString(key + "_mode", "number");
            return gauge;
        }

        private void saveLayout() {
            SharedPreferences.Editor editor = prefs.edit();
            for (Gauge gauge : gauges) {
                editor.putFloat(gauge.key + "_x", gauge.x);
                editor.putFloat(gauge.key + "_y", gauge.y);
                editor.putFloat(gauge.key + "_size", gauge.size);
                editor.putBoolean(gauge.key + "_visible", gauge.visible);
                editor.putString(gauge.key + "_asset", gauge.imageAsset == null ? "" : gauge.imageAsset);
                editor.putString(gauge.key + "_mode", gauge.mode == null ? "number" : gauge.mode);
            }
            editor.putInt("backgroundColor", backgroundColor);
            editor.putInt("accentColor", accentColor);
            editor.putInt("gaugeFillColor", gaugeFillColor);
            editor.putFloat("gaugeAlpha", gaugeAlpha);
            editor.putFloat("backgroundDimAlpha", backgroundDimAlpha);
            editor.apply();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawBackground(canvas);
            for (int layer = 0; layer <= 120; layer++) {
                for (Gauge gauge : gauges) if (gauge.layer == layer) drawGauge(canvas, gauge);
                for (OverlayItem overlay : overlays) if (overlay.layer == layer) drawOverlay(canvas, overlay);
            }
            drawAutoDim(canvas);
            if (editMode) drawEditHint(canvas);
        }

        private void drawAutoDim(Canvas canvas) {
            if (!autoDimActive || autoDimAlpha <= 0f) return;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(0x000000, Math.round(autoDimAlpha * 255f)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        private void drawBackground(Canvas canvas) {
            if (animatedBackground != null) {
                if (animatedBackgroundStart == 0) animatedBackgroundStart = System.currentTimeMillis();
                int duration = animatedBackground.duration();
                if (duration <= 0) duration = 1000;
                animatedBackground.setTime((int) ((System.currentTimeMillis() - animatedBackgroundStart) % duration));
                float scale = Math.max(getWidth() / Math.max(1f, animatedBackground.width()), getHeight() / Math.max(1f, animatedBackground.height()));
                canvas.save();
                canvas.translate((getWidth() - animatedBackground.width() * scale) / 2f, (getHeight() - animatedBackground.height() * scale) / 2f);
                canvas.scale(scale, scale);
                animatedBackground.draw(canvas, 0, 0);
                canvas.restore();
                postInvalidateDelayed(40);
            } else if (background != null) {
                RectF dst = coverRect(background.getWidth(), background.getHeight(), getWidth(), getHeight());
                canvas.drawBitmap(background, null, dst, paint);
            } else {
                canvas.drawColor(backgroundColor);
            }
            int topAlpha = Math.round(backgroundDimAlpha * 120f);
            int bottomAlpha = Math.round(backgroundDimAlpha * 255f);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), alphaColor(0x000000, topAlpha), alphaColor(0x000000, bottomAlpha), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }

        private RectF coverRect(float bw, float bh, float vw, float vh) {
            float scale = Math.max(vw / bw, vh / bh);
            float w = bw * scale;
            float h = bh * scale;
            return new RectF((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f);
        }

        private void drawGauge(Canvas canvas, Gauge gauge) {
            if (!gauge.visible) return;
            float cx = gauge.x * getWidth();
            float cy = gauge.y * getHeight();
            Float value = values.get(gauge.pid);
            float valueForStyle = value == null ? 0f : value;
            if ("bar".equals(gauge.mode)) {
                drawBarGauge(canvas, gauge, cx, cy, value, valueForStyle);
                return;
            }
            float r = gauge.size * Math.min(getWidth(), getHeight()) * reactiveScale(gauge, valueForStyle);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(gauge == active && editMode ? alphaColor(0x17324d, 0xdd) : alphaColor(gaugeFillColor, Math.round(gaugeAlpha * 255f)));
            canvas.drawCircle(cx, cy, r, paint);
            drawGaugeArt(canvas, gauge, cx, cy, r);
            drawReactiveTint(canvas, gauge, cx, cy, r, valueForStyle);
            if (gauge.showBorder) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(3, r * 0.04f));
                paint.setColor(accentColor);
                canvas.drawCircle(cx, cy, r * 0.94f, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            String number = value == null ? "--" : formatValue(gauge.pid, value);
            boolean graphMode = "graph".equals(gauge.mode) || "both".equals(gauge.mode);
            boolean ringMode = "ring".equals(gauge.mode);
            if (graphMode) drawGraph(canvas, gauge, cx, cy, r, "both".equals(gauge.mode));
            if (ringMode) drawMeter(canvas, gauge, cx, cy, r, valueForStyle, true);
            if (!"graph".equals(gauge.mode)) {
                drawOutlinedText(canvas, number, cx, cy + r * 0.04f, r * 0.40f, Color.WHITE, r * 0.035f);
            }
            drawOutlinedText(canvas, gauge.label, cx, cy + r * 0.40f, r * 0.16f, 0xffe6f8ff, r * 0.014f);
        }

        private void drawBarGauge(Canvas canvas, Gauge gauge, float cx, float cy, Float value, float valueForStyle) {
            float base = gauge.size * Math.min(getWidth(), getHeight()) * reactiveScale(gauge, valueForStyle);
            float barW = base * 2.15f;
            float barH = Math.max(6f, base * gauge.barThickness);
            float radius = barH / 2f;
            float lo = gauge.valueMin;
            float hi = gauge.valueMax <= lo ? lo + 1f : gauge.valueMax;
            float pct = clamp((valueForStyle - lo) / (hi - lo), 0f, 1f);
            int fillColor = gauge.reactiveTint ? reactiveTintColor(gauge, valueForStyle) : accentColor;

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(gaugeFillColor, Math.round(gaugeAlpha * 200f)));
            RectF back = new RectF(cx - barW / 2f, cy - barH / 2f, cx + barW / 2f, cy + barH / 2f);
            canvas.drawRoundRect(back, radius, radius, paint);

            if (gauge.showBorder) {
                paint.setColor(alphaColor(0xffffff, 0x35));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, barH * 0.20f));
                canvas.drawRoundRect(back, radius, radius, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            RectF amount = new RectF(back.left, back.top, back.left + barW * pct, back.bottom);
            canvas.drawRoundRect(amount, radius, radius, paint);

            String number = value == null ? "--" : formatValue(gauge.pid, value);
            drawOutlinedText(canvas, number, cx, cy - barH * 0.95f, Math.max(18f, base * 0.36f), Color.WHITE, base * 0.025f);
            drawOutlinedText(canvas, gauge.label, cx, cy + barH * 1.85f, Math.max(11f, base * 0.16f), 0xffe6f8ff, base * 0.012f);

            if (gauge == active && editMode) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, barH * 0.16f));
                paint.setColor(0xffffffff);
                RectF select = new RectF(back.left - 5f, cy - base * 0.58f, back.right + 5f, cy + base * 0.42f);
                canvas.drawRoundRect(select, radius, radius, paint);
            }
        }

        private void drawOutlinedText(Canvas canvas, String text, float x, float y, float size, int fillColor, float outlineWidth) {
            paint.setShader(null);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(size);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, outlineWidth));
            paint.setColor(0xee000000);
            canvas.drawText(text, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(0f);
            paint.setColor(fillColor);
            paint.setShadowLayer(Math.max(2f, size * 0.08f), 0f, Math.max(1f, size * 0.03f), 0xcc000000);
            canvas.drawText(text, x, y, paint);
            paint.clearShadowLayer();
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawOverlay(Canvas canvas, OverlayItem overlay) {
            if (!overlay.visible) return;
            if ("clock".equals(overlay.type)) drawClock(canvas, overlay);
            else if ("date".equals(overlay.type)) drawDate(canvas, overlay);
        }

        private void drawClock(Canvas canvas, OverlayItem overlay) {
            float cx = overlay.x * getWidth();
            float cy = overlay.y * getHeight();
            float w = overlay.w * getWidth();
            float h = overlay.h * getHeight();
            RectF box = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(0x06141d, 0xcc));
            canvas.drawRoundRect(box, Math.max(3f, h * 0.08f), Math.max(3f, h * 0.08f), paint);
            if (overlay.showBorder) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, h * 0.035f));
                paint.setColor(accentColor);
                canvas.drawRoundRect(box, Math.max(3f, h * 0.08f), Math.max(3f, h * 0.08f), paint);
            }

            String[] lines = clockLines(overlay.mode);
            float primarySize = Math.max(16f, h * (lines[1].isEmpty() ? 0.50f : 0.42f));
            float primaryY = lines[1].isEmpty() ? cy + primarySize * 0.34f : cy - h * 0.02f;
            drawOutlinedText(canvas, lines[0], cx, primaryY, primarySize, Color.WHITE, primarySize * 0.08f);
            if (!lines[1].isEmpty()) {
                drawOutlinedText(canvas, lines[1], cx, cy + h * 0.32f, Math.max(10f, h * 0.17f), 0xffd8f6ff, h * 0.01f);
            }
            postInvalidateDelayed("seconds".equals(overlay.mode) ? 250 : 15000);
        }

        private void drawDate(Canvas canvas, OverlayItem overlay) {
            float cx = overlay.x * getWidth();
            float cy = overlay.y * getHeight();
            float w = overlay.w * getWidth();
            float h = overlay.h * getHeight();
            RectF box = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(0x06141d, 0xcc));
            canvas.drawRoundRect(box, Math.max(3f, h * 0.08f), Math.max(3f, h * 0.08f), paint);
            if (overlay.showBorder) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, h * 0.035f));
                paint.setColor(accentColor);
                canvas.drawRoundRect(box, Math.max(3f, h * 0.08f), Math.max(3f, h * 0.08f), paint);
            }
            drawOutlinedText(canvas, dateText(overlay.mode), cx, cy + h * 0.16f, Math.max(12f, h * 0.42f), Color.WHITE, h * 0.035f);
        }

        private String dateText(String mode) {
            Date now = new Date();
            if ("mm_dd_yyyy".equals(mode)) {
                return formatClock("MM/dd/yyyy", now);
            }
            if ("weekday_date".equals(mode)) {
                return formatClock("EEEE MMM d", now);
            }
            if ("short_date".equals(mode)) {
                return formatClock("MMM d", now);
            }
            return formatClock("yyyy/MM/dd", now);
        }

        private String[] clockLines(String mode) {
            Date now = new Date();
            if ("time_date".equals(mode)) {
                return new String[] { formatClock("h:mm", now), formatClock("EEE MMM d", now) };
            }
            if ("yyyy_mm_dd_time".equals(mode)) {
                return new String[] { formatClock("yyyy/MM/dd", now), formatClock("HH:mm", now) };
            }
            if ("yyyy_mm_dd_ampm".equals(mode)) {
                return new String[] { formatClock("yyyy/MM/dd", now), formatClock("h:mm a", now) };
            }
            if ("date".equals(mode)) {
                return new String[] { formatClock("MMM d", now), formatClock("yyyy", now) };
            }
            if ("seconds".equals(mode)) {
                return new String[] { formatClock("h:mm:ss", now), formatClock("a", now) };
            }
            if ("compact".equals(mode)) {
                return new String[] { formatClock("HHmm", now), "" };
            }
            return new String[] { formatClock("h:mm", now), formatClock("a", now) };
        }

        private String formatClock(String pattern, Date date) {
            return new SimpleDateFormat(pattern, Locale.US).format(date);
        }

        private void drawGraph(Canvas canvas, Gauge gauge, float cx, float cy, float r, boolean compact) {
            ArrayList<Float> series = history.get(gauge.pid);
            if (series == null || series.size() < 2) return;
            float lo = gauge.valueMin;
            float hi = gauge.valueMax <= lo ? lo + 1f : gauge.valueMax;
            float left = cx - r * 0.62f;
            float right = cx + r * 0.62f;
            float top = compact ? cy + r * 0.13f : cy - r * 0.24f;
            float bottom = compact ? cy + r * 0.30f : cy + r * 0.22f;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(0x000000, compact ? 0x55 : 0x33));
            canvas.drawRoundRect(left, top, right, bottom, r * 0.04f, r * 0.04f, paint);
            if (gauge.showBorder) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, r * 0.015f));
                paint.setColor(alphaColor(0xffffff, compact ? 0x22 : 0x55));
                canvas.drawRoundRect(left, top, right, bottom, r * 0.04f, r * 0.04f, paint);
            }
            Path line = new Path();
            int count = series.size();
            for (int i = 0; i < count; i++) {
                float pct = clamp((series.get(i) - lo) / (hi - lo), 0f, 1f);
                float x = left + (right - left) * i / Math.max(1, count - 1);
                float y = bottom - (bottom - top) * pct;
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            paint.setStrokeWidth(Math.max(3f, r * (compact ? 0.028f : 0.035f)));
            paint.setColor(accentColor);
            canvas.drawPath(line, paint);
        }

        private void drawMeter(Canvas canvas, Gauge gauge, float cx, float cy, float r, float value, boolean ring) {
            float lo = gauge.valueMin;
            float hi = gauge.valueMax <= lo ? lo + 1f : gauge.valueMax;
            float pct = clamp((value - lo) / (hi - lo), 0f, 1f);
            paint.setShader(null);
            if (ring) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(5f, r * 0.07f));
                RectF oval = new RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f);
                paint.setColor(alphaColor(0xffffff, 0x40));
                canvas.drawArc(oval, 135, 270, false, paint);
                paint.setColor(accentColor);
                canvas.drawArc(oval, 135, 270 * pct, false, paint);
            } else {
                float left = cx - r * 0.62f;
                float top = cy + r * 0.18f;
                float right = cx + r * 0.62f;
                float bottom = cy + r * 0.30f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(alphaColor(0xffffff, 0x35));
                canvas.drawRoundRect(left, top, right, bottom, r * 0.06f, r * 0.06f, paint);
                paint.setColor(accentColor);
                canvas.drawRoundRect(left, top, left + (right - left) * pct, bottom, r * 0.06f, r * 0.06f, paint);
            }
        }

        private void drawReactiveTint(Canvas canvas, Gauge gauge, float cx, float cy, float r, float value) {
            if (!gauge.reactiveTint) return;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alphaColor(reactiveTintColor(gauge, value), 0x66));
            canvas.drawCircle(cx, cy, r, paint);
        }

        private float reactiveScale(Gauge gauge, float value) {
            if (!gauge.reactiveGrow) return 1f;
            float hi = gauge.valueMax <= gauge.valueMin ? gauge.valueMin + 1f : gauge.valueMax;
            float pct = clamp((value - gauge.valueMin) / (hi - gauge.valueMin), 0f, 1f);
            return 1f + pct * (gauge.scaleMax - 1f);
        }

        private int reactiveTintColor(Gauge gauge, float value) {
            if (value >= gauge.highAt) return gauge.highColor;
            if (value >= gauge.midAt) return gauge.midColor;
            return gauge.lowColor;
        }

        private void drawGaugeArt(Canvas canvas, Gauge gauge, float cx, float cy, float r) {
            if (gauge.image == null && gauge.movie == null) return;
            Float rawValue = values.get(gauge.pid);
            float imageScale = gauge.reactiveImageGrow ? reactiveScale(gauge, rawValue == null ? 0f : rawValue) : 1f;
            float imageR = r * imageScale;
            int save = canvas.save();
            clipPath.reset();
            clipPath.addCircle(cx, cy, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            if (gauge.movie != null) {
                if (gauge.movieStart == 0) gauge.movieStart = System.currentTimeMillis();
                int duration = gauge.movie.duration();
                if (duration <= 0) duration = 1000;
                gauge.movie.setTime((int) ((System.currentTimeMillis() - gauge.movieStart) % duration));
                float scale = Math.max((imageR * 2f) / Math.max(1f, gauge.movie.width()), (imageR * 2f) / Math.max(1f, gauge.movie.height()));
                canvas.translate(cx - gauge.movie.width() * scale / 2f, cy - gauge.movie.height() * scale / 2f);
                canvas.scale(scale, scale);
                gauge.movie.draw(canvas, 0, 0);
                postInvalidateDelayed(40);
            } else {
                RectF dst = coverRect(gauge.image.getWidth(), gauge.image.getHeight(), imageR * 2f, imageR * 2f);
                dst.offset(cx - imageR, cy - imageR);
                canvas.drawBitmap(gauge.image, null, dst, paint);
            }
            canvas.restoreToCount(save);
        }

        private String formatValue(String pid, float value) {
            if ("volts".equals(pid)) return String.format(Locale.US, "%.1f", value);
            if ("0106".equals(pid) || "0107".equals(pid) || "0115".equals(pid)) return String.format(Locale.US, "%.1f", value);
            if ("010E".equals(pid)) return String.format(Locale.US, "%.1f", value);
            return String.format(Locale.US, "%.0f", value);
        }

        private void drawEditHint(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(0xddffffff);
            paint.setTextSize(22);
            canvas.drawText("Edit mode: drag gauges. Double tap a gauge to resize.", 16, getHeight() - 20, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!editMode) {
                if (event.getAction() == MotionEvent.ACTION_UP && tapListener != null) tapListener.run();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                active = nearestGauge(event.getX(), event.getY());
                long now = System.currentTimeMillis();
                if (active != null && active == lastTapGauge && now - lastTapTime < 350) {
                    active.size = active.size > 0.25f ? 0.18f : active.size + 0.04f;
                    saveLayout();
                    invalidate();
                    lastTapTime = 0;
                    return true;
                }
                lastTapGauge = active;
                lastTapTime = now;
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && active != null) {
                active.x = clamp(event.getX() / Math.max(1, getWidth()), 0.06f, 0.94f);
                active.y = clamp(event.getY() / Math.max(1, getHeight()), 0.12f, 0.92f);
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                saveLayout();
                return true;
            }
            return true;
        }

        private Gauge nearestGauge(float x, float y) {
            Gauge best = null;
            float bestDist = Float.MAX_VALUE;
            for (Gauge gauge : gauges) {
                float dx = x - gauge.x * getWidth();
                float dy = y - gauge.y * getHeight();
                float d = dx * dx + dy * dy;
                if (d < bestDist) {
                    bestDist = d;
                    best = gauge;
                }
            }
            return best;
        }

        private Gauge gaugeByKey(String key) {
            for (Gauge gauge : gauges) {
                if (gauge.key.equals(key)) return gauge;
            }
            return null;
        }

        private float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private int alphaColor(int rgb, float alphaByte) {
            int a = Math.max(0, Math.min(255, Math.round(alphaByte)));
            return (a << 24) | (rgb & 0x00ffffff);
        }

        private int parseColor(String text, int fallback) {
            try {
                if (text == null || text.trim().isEmpty()) return fallback;
                return Color.parseColor(text);
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    private static class Gauge {
        final String key;
        final String label;
        final String pid;
        float x;
        float y;
        float size;
        boolean visible;
        int layer = 20;
        float barThickness = 0.20f;
        boolean showBorder = true;
        String imageAsset = "";
        String mode = "number";
        String loadedAsset = "";
        Bitmap image;
        Movie movie;
        long movieStart;
        boolean reactiveGrow = false;
        boolean reactiveImageGrow = false;
        boolean reactiveTint = false;
        float valueMin = 0f;
        float valueMax = 100f;
        float scaleMax = 1.25f;
        float midAt = 50f;
        float highAt = 85f;
        int lowColor = 0xff1fb6ff;
        int midColor = 0xffffd166;
        int highColor = 0xffff3b30;

        Gauge(String key, String label, String pid, float x, float y, float size) {
            this(key, label, pid, x, y, size, true);
        }

        Gauge(String key, String label, String pid, float x, float y, float size, boolean visible) {
            this.key = key;
            this.label = label;
            this.pid = pid;
            this.x = x;
            this.y = y;
            this.size = size;
            this.visible = visible;
            applyReactiveDefaults();
        }

        private void applyReactiveDefaults() {
            if ("rpm".equals(pid)) {
                reactiveTint = true; valueMax = 6500f; midAt = 3500f; highAt = 5500f;
            } else if ("speed".equals(pid)) {
                valueMax = 120f; midAt = 45f; highAt = 80f; scaleMax = 1.20f;
            } else if ("coolant".equals(pid)) {
                valueMin = 60f; valueMax = 115f; midAt = 92f; highAt = 105f; reactiveTint = true; scaleMax = 1.20f;
            } else if ("volts".equals(pid)) {
                reactiveTint = true; valueMin = 11.5f; valueMax = 15f; midAt = 12.4f; highAt = 15f; scaleMax = 1.15f;
                lowColor = 0xffff3b30; midColor = 0xff1fb6ff; highColor = 0xffffd166;
            } else if ("load".equals(pid)) {
                reactiveTint = true; valueMin = 0f; valueMax = 100f; midAt = 65f; highAt = 90f;
            } else if ("throttle".equals(pid)) {
                reactiveTint = true; valueMin = 0f; valueMax = 100f; midAt = 50f; highAt = 85f;
            }
        }
    }

    private static class OverlayItem {
        String key = "";
        String type = "";
        float x = 0.5f;
        float y = 0.5f;
        float w = 0.24f;
        float h = 0.12f;
        boolean visible = true;
        boolean showBorder = true;
        int layer = 60;
        String mode = "";
    }

    private static class DeviceChoice {
        final BluetoothDevice device;
        final boolean ble;

        DeviceChoice(BluetoothDevice device, boolean ble) {
            this.device = device;
            this.ble = ble;
        }
    }

    private interface ObdLink {
        void start();
        void close();
        void experimentalScan(List<String> commands, boolean pcSubmitted);

        interface Listener {
            void onStatus(String text);
            void onValues(Map<String, Float> values);
            void onStopped(String reason);
            void onExperimentalScanDone(boolean pcSubmitted);
        }
    }

    private interface DiagSink {
        void log(String line);
    }

    private interface PollPidSource {
        List<String> pids();
    }

    private static List<String> decodeDtcReply(String raw, String responseHeader) {
        List<String> codes = new ArrayList<>();
        if (raw == null) return codes;
        String hex = raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
        int start = hex.indexOf(responseHeader);
        while (start >= 0) {
            int pos = start + responseHeader.length();
            while (pos + 4 <= hex.length()) {
                int a;
                int b;
                try {
                    a = Integer.parseInt(hex.substring(pos, pos + 2), 16);
                    b = Integer.parseInt(hex.substring(pos + 2, pos + 4), 16);
                } catch (Exception ex) {
                    break;
                }
                pos += 4;
                if (a == 0 && b == 0) continue;
                char system = "PCBU".charAt((a & 0xC0) >> 6);
                int firstDigit = (a & 0x30) >> 4;
                String code = String.format(Locale.US, "%c%d%02X%02X", system, firstDigit, a & 0x0F, b);
                if (!codes.contains(code)) codes.add(code);
            }
            start = hex.indexOf(responseHeader, start + responseHeader.length());
        }
        return codes;
    }

    private static String formatDtcSummary(List<String> stored, List<String> pending, List<String> permanent) {
        StringBuilder out = new StringBuilder();
        if (!stored.isEmpty()) out.append("Stored: ").append(joinCodes(stored)).append('\n');
        if (!pending.isEmpty()) out.append("Pending: ").append(joinCodes(pending)).append('\n');
        if (!permanent.isEmpty()) out.append("Permanent: ").append(joinCodes(permanent)).append('\n');
        if (out.length() == 0) {
            out.append("No standard engine OBD-II DTCs returned.\n");
        }
        out.append("EPS/steering faults on Honda may live in the EPS module. Use a scanner that reads Honda EPS codes.");
        return out.toString().trim();
    }

    private static String joinCodes(List<String> codes) {
        StringBuilder out = new StringBuilder();
        for (String code : codes) {
            if (out.length() > 0) out.append(", ");
            out.append(code);
        }
        return out.toString();
    }

    private static class ObdSession extends Thread implements ObdLink {
        private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        private final BluetoothDevice device;
        private final ObdLink.Listener listener;
        private final DiagSink diag;
        private final PollPidSource pollPidSource;
        private final String selectedPin;
        private final boolean autoTry;
        private BluetoothSocket socket;
        private InputStream input;
        private OutputStream output;
        private volatile boolean running = true;
        private volatile boolean experimentalScanRequested = false;
        private volatile boolean experimentalScanFromPc = false;
        private List<String> experimentalCommands = new ArrayList<>();

        ObdSession(BluetoothDevice device, ObdLink.Listener listener, DiagSink diag, String selectedPin, boolean autoTry, PollPidSource pollPidSource) {
            this.device = device;
            this.listener = listener;
            this.diag = diag;
            this.selectedPin = selectedPin;
            this.autoTry = autoTry;
            this.pollPidSource = pollPidSource;
        }

        @Override
        public void run() {
            try {
                listener.onStatus("Opening " + device.getName());
                diag.log("Classic socket open using SPP UUID " + SPP);
                if (autoTry) {
                    runAutoClassic();
                    return;
                }
                diag.log("Classic bond state before connect: " + device.getBondState());
                if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                    diag.log("Calling createBond for Classic device");
                    try {
                        boolean bondStarted = device.createBond();
                        diag.log("createBond returned " + bondStarted);
                        waitForBond();
                    } catch (Exception ex) {
                        diag.log("createBond failed: " + ex.getMessage());
                    }
                }
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                    throw new IOException("Pairing still in progress. Tap Auto to try Veepeak PINs.");
                }
                socket = connectClassicSocket();
                input = new BufferedInputStream(socket.getInputStream());
                output = socket.getOutputStream();
                initElm();
                listener.onStatus("Connected");
                checkStandardDtcs();
                while (running) {
                    runExperimentalScanIfRequested();
                    Map<String, Float> values = pollValues();
                    listener.onValues(values);
                    Thread.sleep(350);
                }
                listener.onStopped("Disconnected");
            } catch (Exception ex) {
                diag.log("Classic exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                listener.onStopped("OBD error: " + ex.getMessage());
            } finally {
                close();
            }
        }

        public void close() {
            running = false;
            closeSocketOnly();
        }

        public void experimentalScan(List<String> commands, boolean pcSubmitted) {
            experimentalCommands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
            experimentalScanFromPc = pcSubmitted;
            experimentalScanRequested = true;
        }

        private void waitForBond() throws InterruptedException {
            long until = System.currentTimeMillis() + 45000;
            while (running && device.getBondState() == BluetoothDevice.BOND_BONDING && System.currentTimeMillis() < until) {
                diag.log("Waiting for bond...");
                Thread.sleep(1000);
            }
            diag.log("Classic bond state after wait: " + device.getBondState());
        }

        private void runAutoClassic() throws IOException, InterruptedException {
            String[] pins = selectedPin == null || selectedPin.isEmpty()
                    ? PIN_OPTIONS
                    : mergePins(selectedPin, PIN_OPTIONS);
            IOException last = null;
            listener.onStatus("Trying direct socket fallback...");
            diag.log("AutoTry no-pair socket pass");
            last = tryAllClassicSockets();
            if (last == null) {
                listener.onStatus("Connected");
                checkStandardDtcs();
                while (running) {
                    runExperimentalScanIfRequested();
                    Map<String, Float> values = pollValues();
                    listener.onValues(values);
                    Thread.sleep(350);
                }
                listener.onStopped("Disconnected");
                return;
            }
            for (String pin : pins) {
                if (!running) break;
                String pinLabel = pin.isEmpty() ? "PIN off" : "PIN " + pin;
                listener.onStatus("Trying " + pinLabel + "...");
                diag.log("AutoTry " + pinLabel);
                prepareBond(pin);
                last = tryAllClassicSockets();
                if (last == null) {
                    listener.onStatus("Connected");
                    checkStandardDtcs();
                    while (running) {
                        runExperimentalScanIfRequested();
                        Map<String, Float> values = pollValues();
                        listener.onValues(values);
                        Thread.sleep(350);
                    }
                    listener.onStopped("Disconnected");
                    return;
                }
            }
            throw last == null ? new IOException("AutoTry exhausted without connection") : last;
        }

        private String[] mergePins(String first, String[] rest) {
            List<String> out = new ArrayList<>();
            out.add(first);
            for (String pin : rest) {
                if (!out.contains(pin)) out.add(pin);
            }
            return out.toArray(new String[0]);
        }

        private void prepareBond(String pin) throws InterruptedException {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) adapter.cancelDiscovery();
            diag.log("Bond state before AutoTry bond: " + device.getBondState());
            if (!pin.isEmpty()) setPinReflect(pin);
            if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                try {
                    diag.log("AutoTry createBond");
                    diag.log("createBond returned " + device.createBond());
                    waitForBond();
                } catch (Exception ex) {
                    diag.log("AutoTry createBond failed: " + ex.getMessage());
                }
            }
        }

        private void setPinReflect(String pin) {
            try {
                Method setPin = BluetoothDevice.class.getMethod("setPin", byte[].class);
                Object ok = setPin.invoke(device, new Object[]{pin.getBytes("UTF-8")});
                diag.log("AutoTry setPin(" + pin + ") returned " + ok);
            } catch (Exception ex) {
                diag.log("AutoTry setPin failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        private IOException tryAllClassicSockets() {
            IOException last = null;
            String[] methods = new String[]{"insecure-spp", "secure-spp", "insecure-ch1", "secure-ch1", "insecure-ch2", "secure-ch2", "insecure-ch3", "secure-ch3", "insecure-ch4", "secure-ch4"};
            for (String method : methods) {
                if (!running) break;
                try {
                    closeSocketOnly();
                    listener.onStatus("Trying socket " + method + "...");
                    diag.log("AutoTry socket " + method);
                    socket = createSocket(method);
                    socket.connect();
                    input = new BufferedInputStream(socket.getInputStream());
                    output = socket.getOutputStream();
                    initElm();
                    diag.log("AutoTry success with " + method);
                    return null;
                } catch (Exception ex) {
                    last = ex instanceof IOException ? (IOException) ex : new IOException(ex);
                    listener.onStatus("Failed socket " + method + ", trying next...");
                    diag.log("AutoTry failed " + method + ": " + ex.getMessage());
                    closeSocketOnly();
                }
            }
            return last;
        }

        private BluetoothSocket createSocket(String methodName) throws Exception {
            if ("secure-spp".equals(methodName)) return device.createRfcommSocketToServiceRecord(SPP);
            if ("insecure-spp".equals(methodName)) return device.createInsecureRfcommSocketToServiceRecord(SPP);
            boolean insecure = methodName.startsWith("insecure");
            int channel = Integer.parseInt(methodName.substring(methodName.length() - 1));
            Method method = BluetoothDevice.class.getMethod(insecure ? "createInsecureRfcommSocket" : "createRfcommSocket", int.class);
            return (BluetoothSocket) method.invoke(device, channel);
        }

        private void closeSocketOnly() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            socket = null;
            input = null;
            output = null;
        }

        private BluetoothSocket connectClassicSocket() throws IOException {
            IOException secureError = null;
            try {
                diag.log("Trying secure RFCOMM socket");
                BluetoothSocket secure = device.createRfcommSocketToServiceRecord(SPP);
                secure.connect();
                diag.log("Secure RFCOMM connected");
                return secure;
            } catch (IOException ex) {
                secureError = ex;
                diag.log("Secure RFCOMM failed: " + ex.getMessage());
                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            }

            try {
                diag.log("Trying insecure RFCOMM socket");
                BluetoothSocket insecure = device.createInsecureRfcommSocketToServiceRecord(SPP);
                insecure.connect();
                diag.log("Insecure RFCOMM connected");
                return insecure;
            } catch (IOException ex) {
                diag.log("Insecure RFCOMM failed: " + ex.getMessage());
                if (secureError != null) throw secureError;
                throw ex;
            }
        }

        private void initElm() throws IOException, InterruptedException {
            command("ATZ", 1200);
            command("ATE0", 200);
            command("ATL0", 200);
            command("ATS0", 200);
            command("ATH0", 200);
            command("ATSP0", 600);
            command("0100", 800);
        }

        private void checkStandardDtcs() throws IOException, InterruptedException {
            listener.onStatus("Checking standard OBD-II fault codes...");
            String storedRaw = command("03", 1200);
            String pendingRaw = command("07", 1200);
            String permanentRaw = command("0A", 1200);
            List<String> stored = decodeDtcReply(storedRaw, "43");
            List<String> pending = decodeDtcReply(pendingRaw, "47");
            List<String> permanent = decodeDtcReply(permanentRaw, "4A");
            diag.log("DTC raw stored=" + compact(storedRaw));
            diag.log("DTC raw pending=" + compact(pendingRaw));
            diag.log("DTC raw permanent=" + compact(permanentRaw));
            String summary = formatDtcSummary(stored, pending, permanent);
            diag.log("DTC " + summary.replace('\n', ' '));
            listener.onStatus("DTC check done. See debug log.");
        }

        private void runExperimentalScanIfRequested() throws IOException, InterruptedException {
            if (!experimentalScanRequested) return;
            experimentalScanRequested = false;
            List<String> commands = experimentalCommands == null || experimentalCommands.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(experimentalCommands);
            listener.onStatus((experimentalScanFromPc ? "PC sample" : "Safe DTC") + " scan running...");
            diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " scan start. commands=" + commands);
            int dataReplies = 0;
            int noDataReplies = 0;
            int okReplies = 0;
            int failedReplies = 0;
            for (String cmd : commands) {
                if (!running) break;
                try {
                    String reply = command(cmd, 1400);
                    String compactReply = compact(reply);
                    String upper = compactReply.toUpperCase(Locale.US);
                    if (upper.contains("NO DATA")) noDataReplies++;
                    else if ("OK".equals(upper)) okReplies++;
                    else if (!upper.trim().isEmpty()) dataReplies++;
                    diag.log((experimentalScanFromPc ? "PC SAMPLE " : "SAFE DTC ") + cmd + " => " + compactReply);
                } catch (Exception ex) {
                    failedReplies++;
                    diag.log((experimentalScanFromPc ? "PC SAMPLE " : "SAFE DTC ") + cmd + " failed: " + ex.getMessage());
                }
                Thread.sleep(80);
            }
            String summary = "data=" + dataReplies + " noData=" + noDataReplies + " ok=" + okReplies + " failed=" + failedReplies;
            diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " scan done. " + summary);
            if (running) {
                try {
                    diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " restoring ELM defaults after scan.");
                    initElm();
                } catch (Exception ex) {
                    diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " restore failed: " + ex.getMessage());
                }
            }
            listener.onStatus((experimentalScanFromPc ? "PC sample" : "Safe DTC") + " done: " + summary);
            listener.onExperimentalScanDone(experimentalScanFromPc);
        }

        private Map<String, Float> pollValues() throws IOException, InterruptedException {
            Map<String, Float> out = new LinkedHashMap<>();
            for (String pid : pollPidSource.pids()) {
                pollOne(pid, out, 260);
            }
            return out;
        }

        private void pollOne(String pid, Map<String, Float> out, long waitMs) throws IOException, InterruptedException {
            if ("rpm".equals(pid)) parseRpm(command("010C", waitMs), out);
            else if ("speed".equals(pid)) parseSpeed(command("010D", waitMs), out);
            else if ("coolant".equals(pid)) parseMode01("coolant", command("0105", waitMs), out, -40f, 1f);
            else if ("volts".equals(pid)) parseVolts(command("0142", waitMs), out);
            else if ("load".equals(pid)) parseMode01("load", command("0104", waitMs), out, 0f, 100f / 255f);
            else if ("throttle".equals(pid)) parseMode01("throttle", command("0111", waitMs), out, 0f, 100f / 255f);
            else parseGenericPid(pid, command(pid, waitMs), out);
        }

        private String command(String text, long waitMs) throws IOException, InterruptedException {
            drainInput();
            output.write((text + "\r").getBytes("US-ASCII"));
            output.flush();
            long until = System.currentTimeMillis() + waitMs;
            StringBuilder sb = new StringBuilder();
            while (System.currentTimeMillis() < until && running) {
                while (input.available() > 0) {
                    char c = (char) input.read();
                    if (c == '>') {
                        diag.log("ELM " + text + " => " + compact(sb.toString()));
                        return sb.toString();
                    }
                    sb.append(c);
                }
                Thread.sleep(12);
            }
            diag.log("ELM " + text + " timeout/partial => " + compact(sb.toString()));
            return sb.toString();
        }

        private String compact(String raw) {
            return raw.replace('\r', ' ').replace('\n', ' ').trim();
        }

        private void drainInput() throws IOException {
            while (input.available() > 0) input.read();
        }

        private void parseRpm(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "410C", 2);
            if (bytes != null) {
                float value = ((bytes[0] * 256f) + bytes[1]) / 4f;
                out.put("rpm", value);
                out.put("010C", value);
            }
        }

        private void parseMode01(String key, String raw, Map<String, Float> out, float offset, float scale) {
            String header = "41" + commandPidForKey(key);
            int[] bytes = payload(raw, header, 1);
            if (bytes != null) {
                float value = offset + bytes[0] * scale;
                out.put(key, value);
                out.put(headerToConfigPid(header), value);
            }
        }

        private void parseSpeed(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "410D", 1);
            if (bytes != null) {
                out.put("speedKph", (float) bytes[0]);
                out.put("speed", bytes[0] * 0.621371f);
                out.put("010D", bytes[0] * 0.621371f);
            }
        }

        private void parseVolts(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "4142", 2);
            if (bytes != null) {
                float value = ((bytes[0] * 256f) + bytes[1]) / 1000f;
                out.put("volts", value);
                out.put("0142", value);
            }
        }

        private void parseGenericPid(String pid, String raw, Map<String, Float> out) {
            String command = normalizeCommandPid(pid);
            if (command.isEmpty()) return;
            int[] bytes = payload(raw, "41" + command.substring(2), 4);
            if (bytes == null) bytes = payload(raw, "41" + command.substring(2), 1);
            if (bytes == null || bytes.length == 0) return;
            float value = bytes[0];
            if ("0103".equals(command) && bytes.length >= 2) value = bytes[0] * 256f + bytes[1];
            else if ("0106".equals(command) || "0107".equals(command)) value = (bytes[0] - 128f) * 100f / 128f;
            else if ("010B".equals(command)) value = bytes[0];
            else if ("010E".equals(command)) value = bytes[0] / 2f - 64f;
            else if ("010F".equals(command)) value = bytes[0] - 40f;
            else if ("0115".equals(command) && bytes.length >= 2) value = bytes[1] * 100f / 128f - 100f;
            else if ("011F".equals(command) && bytes.length >= 2) value = bytes[0] * 256f + bytes[1];
            out.put(command, value);
        }

        private String commandPidForKey(String key) {
            if ("coolant".equals(key)) return "05";
            if ("load".equals(key)) return "04";
            if ("throttle".equals(key)) return "11";
            return "00";
        }

        private String headerToConfigPid(String header) {
            if ("4105".equals(header)) return "0105";
            if ("4104".equals(header)) return "0104";
            if ("4111".equals(header)) return "0111";
            return header;
        }

        private String normalizeCommandPid(String pid) {
            if (pid == null) return "";
            String compact = pid.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
            if (compact.length() == 2) compact = "01" + compact;
            return compact.matches("01[0-9A-F]{2}") ? compact : "";
        }

        private int[] payload(String raw, String header, int count) {
            String hex = raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
            int start = hex.indexOf(header);
            if (start < 0) return null;
            start += header.length();
            if (hex.length() < start + count * 2) return null;
            int[] bytes = new int[count];
            for (int i = 0; i < count; i++) {
                bytes[i] = Integer.parseInt(hex.substring(start + i * 2, start + i * 2 + 2), 16);
            }
            return bytes;
        }
    }

    private static class BleObdSession extends Thread implements ObdLink {
        private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        private final Context context;
        private final BluetoothDevice device;
        private final ObdLink.Listener listener;
        private final DiagSink diag;
        private final PollPidSource pollPidSource;
        private final Object lock = new Object();
        private BluetoothGatt gatt;
        private BluetoothGattCharacteristic writeChar;
        private BluetoothGattCharacteristic notifyChar;
        private final StringBuilder rx = new StringBuilder();
        private volatile boolean running = true;
        private volatile boolean ready;
        private volatile boolean experimentalScanRequested = false;
        private volatile boolean experimentalScanFromPc = false;
        private List<String> experimentalCommands = new ArrayList<>();

        BleObdSession(Context context, BluetoothDevice device, ObdLink.Listener listener, DiagSink diag, PollPidSource pollPidSource) {
            this.context = context.getApplicationContext();
            this.device = device;
            this.listener = listener;
            this.diag = diag;
            this.pollPidSource = pollPidSource;
        }

        @Override
        public void run() {
            try {
                listener.onStatus("Opening BLE " + safeName(device));
                diag.log("BLE connectGatt address=" + device.getAddress());
                gatt = device.connectGatt(context, false, callback);
                waitUntilReady();
                initElm();
                listener.onStatus("BLE connected");
                checkStandardDtcs();
                while (running) {
                    runExperimentalScanIfRequested();
                    listener.onValues(pollValues());
                    Thread.sleep(420);
                }
                listener.onStopped("Disconnected");
            } catch (Exception ex) {
                diag.log("BLE exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                listener.onStopped("BLE error: " + ex.getMessage());
            } finally {
                close();
            }
        }

        public void close() {
            running = false;
            synchronized (lock) {
                lock.notifyAll();
            }
            if (gatt != null) {
                try { gatt.disconnect(); } catch (Exception ignored) {}
                try { gatt.close(); } catch (Exception ignored) {}
            }
        }

        public void experimentalScan(List<String> commands, boolean pcSubmitted) {
            experimentalCommands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
            experimentalScanFromPc = pcSubmitted;
            experimentalScanRequested = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        private final BluetoothGattCallback callback = new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                diag.log("BLE state status=" + status + " newState=" + newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    listener.onStatus("Discovering BLE services...");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    running = false;
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }

            @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                diag.log("Services discovered status=" + status + ", count=" + gatt.getServices().size());
                logServices(gatt);
                findPipe(gatt);
                if (writeChar != null && notifyChar != null) {
                    diag.log("Using write=" + writeChar.getUuid() + " notify=" + notifyChar.getUuid());
                    gatt.setCharacteristicNotification(notifyChar, true);
                    BluetoothGattDescriptor desc = notifyChar.getDescriptor(CCCD);
                    if (desc != null) {
                        diag.log("Writing CCCD descriptor");
                        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(desc);
                    } else {
                        diag.log("Notify characteristic has no CCCD descriptor");
                    }
                    synchronized (lock) {
                        ready = true;
                        lock.notifyAll();
                    }
                } else {
                    diag.log("No BLE serial pipe. write=" + writeChar + " notify=" + notifyChar);
                    listener.onStatus("No BLE serial service found");
                    running = false;
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }

            @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] value = characteristic.getValue();
                diag.log("BLE notify " + characteristic.getUuid() + " bytes=" + (value == null ? 0 : value.length));
                appendRx(value);
            }

            @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                byte[] value = characteristic.getValue();
                diag.log("BLE read " + characteristic.getUuid() + " status=" + status + " bytes=" + (value == null ? 0 : value.length));
                appendRx(value);
            }
        };

        private void logServices(BluetoothGatt gatt) {
            for (BluetoothGattService service : gatt.getServices()) {
                diag.log("Service " + service.getUuid());
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    diag.log("  Char " + c.getUuid() + " props=" + c.getProperties());
                }
            }
        }

        private void findPipe(BluetoothGatt gatt) {
            UUID[] notifyIds = new UUID[]{
                    UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
            };
            UUID[] writeIds = new UUID[]{
                    UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
            };
            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    if (containsUuid(writeIds, c.getUuid())) writeChar = c;
                    if (containsUuid(notifyIds, c.getUuid())) notifyChar = c;
                }
            }
            if (writeChar != null && notifyChar != null) return;

            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    int props = c.getProperties();
                    boolean writable = (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                            || (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
                    boolean notifiable = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                            || (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                    if (writable && writeChar == null) writeChar = c;
                    if (notifiable && notifyChar == null) notifyChar = c;
                    if (writable && notifiable) {
                        writeChar = c;
                        notifyChar = c;
                        return;
                    }
                }
            }
        }

        private boolean containsUuid(UUID[] ids, UUID id) {
            for (UUID candidate : ids) {
                if (candidate.equals(id)) return true;
            }
            return false;
        }

        private void waitUntilReady() throws InterruptedException, IOException {
            long until = System.currentTimeMillis() + 15000;
            synchronized (lock) {
                while (running && !ready && System.currentTimeMillis() < until) {
                    lock.wait(250);
                }
            }
            if (!ready) throw new IOException("BLE service timeout");
        }

        private void appendRx(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return;
            synchronized (lock) {
                rx.append(new String(bytes));
                lock.notifyAll();
            }
        }

        private void initElm() throws IOException, InterruptedException {
            command("ATZ", 1600);
            command("ATE0", 300);
            command("ATL0", 300);
            command("ATS0", 300);
            command("ATH0", 300);
            command("ATSP0", 900);
            command("0100", 900);
        }

        private void checkStandardDtcs() throws IOException, InterruptedException {
            listener.onStatus("Checking standard OBD-II fault codes...");
            String storedRaw = command("03", 1500);
            String pendingRaw = command("07", 1500);
            String permanentRaw = command("0A", 1500);
            List<String> stored = decodeDtcReply(storedRaw, "43");
            List<String> pending = decodeDtcReply(pendingRaw, "47");
            List<String> permanent = decodeDtcReply(permanentRaw, "4A");
            diag.log("DTC raw stored=" + compact(storedRaw));
            diag.log("DTC raw pending=" + compact(pendingRaw));
            diag.log("DTC raw permanent=" + compact(permanentRaw));
            String summary = formatDtcSummary(stored, pending, permanent);
            diag.log("DTC " + summary.replace('\n', ' '));
            listener.onStatus("DTC check done. See debug log.");
        }

        private void runExperimentalScanIfRequested() throws IOException, InterruptedException {
            if (!experimentalScanRequested) return;
            experimentalScanRequested = false;
            List<String> commands = experimentalCommands == null || experimentalCommands.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(experimentalCommands);
            listener.onStatus((experimentalScanFromPc ? "PC sample" : "Safe DTC") + " scan running...");
            diag.log((experimentalScanFromPc ? "PC SAMPLE BLE" : "SAFE DTC BLE") + " scan start. commands=" + commands);
            int dataReplies = 0;
            int noDataReplies = 0;
            int okReplies = 0;
            int failedReplies = 0;
            for (String cmd : commands) {
                if (!running) break;
                try {
                    String reply = command(cmd, 1700);
                    String compactReply = compact(reply);
                    String upper = compactReply.toUpperCase(Locale.US);
                    if (upper.contains("NO DATA")) noDataReplies++;
                    else if ("OK".equals(upper)) okReplies++;
                    else if (!upper.trim().isEmpty()) dataReplies++;
                    diag.log((experimentalScanFromPc ? "PC SAMPLE " : "SAFE DTC ") + cmd + " => " + compactReply);
                } catch (Exception ex) {
                    failedReplies++;
                    diag.log((experimentalScanFromPc ? "PC SAMPLE " : "SAFE DTC ") + cmd + " failed: " + ex.getMessage());
                }
                Thread.sleep(120);
            }
            String summary = "data=" + dataReplies + " noData=" + noDataReplies + " ok=" + okReplies + " failed=" + failedReplies;
            diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " scan done. " + summary);
            if (running) {
                try {
                    diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " restoring ELM defaults after scan.");
                    initElm();
                } catch (Exception ex) {
                    diag.log((experimentalScanFromPc ? "PC SAMPLE" : "SAFE DTC") + " restore failed: " + ex.getMessage());
                }
            }
            listener.onStatus((experimentalScanFromPc ? "PC sample" : "Safe DTC") + " done: " + summary);
            listener.onExperimentalScanDone(experimentalScanFromPc);
        }

        private Map<String, Float> pollValues() throws IOException, InterruptedException {
            Map<String, Float> out = new LinkedHashMap<>();
            for (String pid : pollPidSource.pids()) {
                pollOne(pid, out, 340);
            }
            return out;
        }

        private void pollOne(String pid, Map<String, Float> out, long waitMs) throws IOException, InterruptedException {
            if ("rpm".equals(pid)) parseRpm(command("010C", waitMs), out);
            else if ("speed".equals(pid)) parseSpeed(command("010D", waitMs), out);
            else if ("coolant".equals(pid)) parseMode01("coolant", command("0105", waitMs), out, -40f, 1f);
            else if ("volts".equals(pid)) parseVolts(command("0142", waitMs), out);
            else if ("load".equals(pid)) parseMode01("load", command("0104", waitMs), out, 0f, 100f / 255f);
            else if ("throttle".equals(pid)) parseMode01("throttle", command("0111", waitMs), out, 0f, 100f / 255f);
            else parseGenericPid(pid, command(pid, waitMs), out);
        }

        private String command(String text, long waitMs) throws IOException, InterruptedException {
            synchronized (lock) {
                rx.setLength(0);
            }
            byte[] bytes = (text + "\r").getBytes("US-ASCII");
            int props = writeChar.getProperties();
            writeChar.setWriteType((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeChar.setValue(bytes);
            diag.log("BLE TX " + text + " via " + writeChar.getUuid());
            if (!gatt.writeCharacteristic(writeChar)) throw new IOException("BLE write failed");

            long until = System.currentTimeMillis() + waitMs;
            synchronized (lock) {
                while (running && System.currentTimeMillis() < until) {
                    if (rx.indexOf(">") >= 0) break;
                    lock.wait(30);
                }
                String reply = rx.toString();
                diag.log("BLE RX " + text + " => " + compact(reply));
                return reply;
            }
        }

        private String compact(String raw) {
            String out = raw.replace('\r', ' ').replace('\n', ' ').trim();
            return out.length() > 180 ? out.substring(0, 180) + "..." : out;
        }

        private String safeName(BluetoothDevice device) {
            String name = device.getName();
            return name == null ? device.getAddress() : name;
        }

        private void parseRpm(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "410C", 2);
            if (bytes != null) {
                float value = ((bytes[0] * 256f) + bytes[1]) / 4f;
                out.put("rpm", value);
                out.put("010C", value);
            }
        }

        private void parseMode01(String key, String raw, Map<String, Float> out, float offset, float scale) {
            String header = "41" + commandPidForKey(key);
            int[] bytes = payload(raw, header, 1);
            if (bytes != null) {
                float value = offset + bytes[0] * scale;
                out.put(key, value);
                out.put(headerToConfigPid(header), value);
            }
        }

        private void parseSpeed(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "410D", 1);
            if (bytes != null) {
                out.put("speedKph", (float) bytes[0]);
                out.put("speed", bytes[0] * 0.621371f);
                out.put("010D", bytes[0] * 0.621371f);
            }
        }

        private void parseVolts(String raw, Map<String, Float> out) {
            int[] bytes = payload(raw, "4142", 2);
            if (bytes != null) {
                float value = ((bytes[0] * 256f) + bytes[1]) / 1000f;
                out.put("volts", value);
                out.put("0142", value);
            }
        }

        private void parseGenericPid(String pid, String raw, Map<String, Float> out) {
            String command = normalizeCommandPid(pid);
            if (command.isEmpty()) return;
            int[] bytes = payload(raw, "41" + command.substring(2), 4);
            if (bytes == null) bytes = payload(raw, "41" + command.substring(2), 1);
            if (bytes == null || bytes.length == 0) return;
            float value = bytes[0];
            if ("0103".equals(command) && bytes.length >= 2) value = bytes[0] * 256f + bytes[1];
            else if ("0106".equals(command) || "0107".equals(command)) value = (bytes[0] - 128f) * 100f / 128f;
            else if ("010B".equals(command)) value = bytes[0];
            else if ("010E".equals(command)) value = bytes[0] / 2f - 64f;
            else if ("010F".equals(command)) value = bytes[0] - 40f;
            else if ("0115".equals(command) && bytes.length >= 2) value = bytes[1] * 100f / 128f - 100f;
            else if ("011F".equals(command) && bytes.length >= 2) value = bytes[0] * 256f + bytes[1];
            out.put(command, value);
        }

        private String commandPidForKey(String key) {
            if ("coolant".equals(key)) return "05";
            if ("load".equals(key)) return "04";
            if ("throttle".equals(key)) return "11";
            return "00";
        }

        private String headerToConfigPid(String header) {
            if ("4105".equals(header)) return "0105";
            if ("4104".equals(header)) return "0104";
            if ("4111".equals(header)) return "0111";
            return header;
        }

        private String normalizeCommandPid(String pid) {
            if (pid == null) return "";
            String compact = pid.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
            if (compact.length() == 2) compact = "01" + compact;
            return compact.matches("01[0-9A-F]{2}") ? compact : "";
        }

        private int[] payload(String raw, String header, int count) {
            String hex = raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
            int start = hex.indexOf(header);
            if (start < 0) return null;
            start += header.length();
            if (hex.length() < start + count * 2) return null;
            int[] bytes = new int[count];
            for (int i = 0; i < count; i++) {
                bytes[i] = Integer.parseInt(hex.substring(start + i * 2, start + i * 2 + 2), 16);
            }
            return bytes;
        }
    }
}
