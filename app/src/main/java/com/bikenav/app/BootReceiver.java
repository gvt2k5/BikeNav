package com.bikenav.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * BootReceiver — auto-starts BikeNav Bluetooth service on device boot
 * if the user had previously connected (MAC saved in SharedPreferences).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("BikeNavPrefs", Context.MODE_PRIVATE);
        String savedMac = prefs.getString("last_mac", null);

        if (savedMac != null) {
            Intent serviceIntent = new Intent(context, BluetoothService.class);
            serviceIntent.setAction(BluetoothService.ACTION_CONNECT);
            serviceIntent.putExtra(BluetoothService.EXTRA_MAC, savedMac);
            context.startForegroundService(serviceIntent);
        }
    }
}
