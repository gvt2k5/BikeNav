package com.bikenav.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvLastCommand;
    private TextView tvLog;
    private Button btnConnect;
    private Button btnDisconnect;
    private ScrollView scrollLog;

    private BluetoothAdapter bluetoothAdapter;
    private static final String TARGET_DEVICE_NAME = "BikeNav";

    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case BluetoothService.ACTION_STATUS:
                    updateStatus(intent.getStringExtra(BluetoothService.EXTRA_STATUS));
                    break;
                case BluetoothService.ACTION_COMMAND_SENT:
                    String cmd = intent.getStringExtra(BluetoothService.EXTRA_COMMAND);
                    updateLastCommand(cmd);
                    appendLog("📤 Sent: " + cmd);
                    break;
                case NavNotificationListenerService.ACTION_NAV_DATA:
                    String raw = intent.getStringExtra(NavNotificationListenerService.EXTRA_RAW_TEXT);
                    String parsed = intent.getStringExtra(NavNotificationListenerService.EXTRA_PARSED);
                    appendLog("🗺 Maps: " + raw);
                    if (parsed != null) appendLog("✅ Parsed: " + parsed);
                    break;
                case BluetoothService.ACTION_LOG:
                    appendLog(intent.getStringExtra(BluetoothService.EXTRA_LOG));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus      = findViewById(R.id.tvStatus);
        tvLastCommand = findViewById(R.id.tvLastCommand);
        tvLog         = findViewById(R.id.tvLog);
        btnConnect    = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        scrollLog     = findViewById(R.id.scrollLog);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bluetoothPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean granted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                if (Boolean.TRUE.equals(granted)) {
                    doConnect();
                } else {
                    Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        );

        btnConnect.setOnClickListener(v -> connectToBikeNav());
        btnDisconnect.setOnClickListener(v -> disconnectFromBikeNav());

        findViewById(R.id.btnGrantNotification).setOnClickListener(v -> {
            if (!isNotificationListenerEnabled()) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } else {
                Toast.makeText(this, "✅ Notification access already granted!", Toast.LENGTH_SHORT).show();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothService.ACTION_STATUS);
        filter.addAction(BluetoothService.ACTION_COMMAND_SENT);
        filter.addAction(BluetoothService.ACTION_LOG);
        filter.addAction(NavNotificationListenerService.ACTION_NAV_DATA);
        registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        checkNotificationPermission();
        updateStatus("Idle — not connected");
        appendLog("BikeNav started. Tap 'Connect' to begin.");
    }

    private void connectToBikeNav() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                });
                return;
            }
        }

        doConnect();
    }

    private void doConnect() {
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            BluetoothDevice target = null;
            List<String> deviceNames = new ArrayList<>();
            List<BluetoothDevice> deviceList = new ArrayList<>();

            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                deviceNames.add((name != null ? name : "Unknown") + "\n" + device.getAddress());
                deviceList.add(device);
                if (TARGET_DEVICE_NAME.equals(name)) {
                    target = device;
                }
            }

            if (target != null) {
                startBluetoothService(target.getAddress());
            } else if (!deviceList.isEmpty()) {
                String[] names = deviceNames.toArray(new String[0]);
                List<BluetoothDevice> finalList = deviceList;
                new AlertDialog.Builder(this)
                    .setTitle("Select Bluetooth Device")
                    .setItems(names, (dialog, which) ->
                        startBluetoothService(finalList.get(which).getAddress()))
                    .show();
            } else {
                Toast.makeText(this, "No paired devices found. Pair your ESP32 first.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Bluetooth permission error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startBluetoothService(String macAddress) {
        Intent intent = new Intent(this, BluetoothService.class);
        intent.setAction(BluetoothService.ACTION_CONNECT);
        intent.putExtra(BluetoothService.EXTRA_MAC, macAddress);
        startForegroundService(intent);
        appendLog("🔵 Connecting to " + macAddress + "...");
    }

    private void disconnectFromBikeNav() {
        Intent intent = new Intent(this, BluetoothService.class);
        intent.setAction(BluetoothService.ACTION_DISCONNECT);
        startService(intent);
    }

    private void updateStatus(String status) {
        tvStatus.setText("Status: " + status);
    }

    private void updateLastCommand(String cmd) {
        tvLastCommand.setText("Last: " + cmd);
    }

    private void appendLog(String text) {
        if (text == null) return;
        String current = tvLog.getText().toString();
        String timestamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString();
        tvLog.setText(current + "\n[" + timestamp + "] " + text);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void checkNotificationPermission() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Notification Access Required")
                .setMessage("BikeNav needs notification access to read Google Maps directions automatically.")
                .setPositiveButton("Grant Access", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(uiReceiver);
    }
}
