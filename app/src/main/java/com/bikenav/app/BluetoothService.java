package com.bikenav.app;

import android.app.*;
import android.bluetooth.*;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.util.UUID;

/**
 * BikeNav Bluetooth Service
 *
 * Foreground service that:
 * 1. Connects to the ESP32 BikeNav device over Bluetooth Classic (SPP)
 * 2. Maintains the connection
 * 3. Sends navigation protocol strings when commanded
 * 4. Auto-reconnects if connection drops
 */
public class BluetoothService extends Service {

    private static final String TAG = "BikeNavBT";

    // SPP UUID — standard Serial Port Profile UUID used by BluetoothSerial on ESP32
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Actions
    public static final String ACTION_CONNECT    = "com.bikenav.BT_CONNECT";
    public static final String ACTION_DISCONNECT = "com.bikenav.BT_DISCONNECT";
    public static final String ACTION_SEND       = "com.bikenav.BT_SEND";

    // Broadcast actions (to UI)
    public static final String ACTION_STATUS       = "com.bikenav.BT_STATUS";
    public static final String ACTION_COMMAND_SENT = "com.bikenav.BT_COMMAND_SENT";
    public static final String ACTION_LOG          = "com.bikenav.BT_LOG";

    // Extras
    public static final String EXTRA_STATUS  = "status";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_MAC     = "mac";
    public static final String EXTRA_LOG     = "log";

    // Notification
    private static final String CHANNEL_ID = "BikeNavBT";
    private static final int NOTIF_ID = 1;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private String targetMac;
    private boolean shouldReconnect = false;

    // Handler for reconnect
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int RECONNECT_DELAY_MS = 5000;

    private final Runnable reconnectRunnable = () -> {
        if (shouldReconnect && targetMac != null) {
            broadcastLog("🔄 Reconnecting...");
            connect(targetMac);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_CONNECT:
                targetMac = intent.getStringExtra(EXTRA_MAC);
                shouldReconnect = true;
                new Thread(() -> connect(targetMac)).start();
                break;

            case ACTION_DISCONNECT:
                shouldReconnect = false;
                handler.removeCallbacks(reconnectRunnable);
                disconnect();
                stopForeground(true);
                stopSelf();
                break;

            case ACTION_SEND:
                String command = intent.getStringExtra(EXTRA_COMMAND);
                if (command != null) {
                    new Thread(() -> sendCommand(command)).start();
                }
                break;
        }

        return START_STICKY;
    }

    private void connect(String mac) {
        broadcastStatus("Connecting...");

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);

            // Cancel discovery if running (important for connection stability)
            bluetoothAdapter.cancelDiscovery();

            // Close existing socket
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }

            // Create SPP socket
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect(); // Blocking

            outputStream = socket.getOutputStream();

            broadcastStatus("✅ Connected to " + device.getName());
            broadcastLog("🟢 Bluetooth connected to " + device.getName());
            updateNotification("Connected to " + device.getName());

        } catch (IOException e) {
            Log.e(TAG, "Connection failed: " + e.getMessage());
            broadcastStatus("❌ Connection failed");
            broadcastLog("❌ Connection failed: " + e.getMessage());

            // Schedule reconnect
            if (shouldReconnect) {
                broadcastLog("⏳ Retrying in 5s...");
                handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
            }
        }
    }

    private void disconnect() {
        try {
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        outputStream = null;
        socket = null;
        broadcastStatus("Disconnected");
        broadcastLog("🔴 Bluetooth disconnected");
    }

    private void sendCommand(String command) {
        if (outputStream == null) {
            broadcastLog("⚠️ Not connected — cannot send: " + command);
            return;
        }

        try {
            // Send command with newline terminator (ESP32 reads until \n)
            String data = command + "\n";
            outputStream.write(data.getBytes());
            outputStream.flush();

            broadcastCommandSent(command);
            Log.d(TAG, "Sent: " + command);

        } catch (IOException e) {
            broadcastLog("❌ Send failed: " + e.getMessage());

            // Connection dropped — try to reconnect
            disconnect();
            if (shouldReconnect && targetMac != null) {
                handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
            }
        }
    }

    // ───── Broadcast helpers ─────

    private void broadcastStatus(String status) {
        Intent i = new Intent(ACTION_STATUS);
        i.putExtra(EXTRA_STATUS, status);
        sendBroadcast(i);
    }

    private void broadcastCommandSent(String command) {
        Intent i = new Intent(ACTION_COMMAND_SENT);
        i.putExtra(EXTRA_COMMAND, command);
        sendBroadcast(i);
    }

    private void broadcastLog(String log) {
        Intent i = new Intent(ACTION_LOG);
        i.putExtra(EXTRA_LOG, log);
        sendBroadcast(i);
    }

    // ───── Notification ─────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "BikeNav Bluetooth",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("BikeNav Bluetooth connection service");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BikeNav Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        Notification n = buildNotification(text);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, n);
        startForeground(NOTIF_ID, n);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shouldReconnect = false;
        handler.removeCallbacks(reconnectRunnable);
        disconnect();
    }
}
