package com.bikenav.app;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * BikeNav Notification Listener Service
 *
 * Listens for Google Maps navigation notifications and parses them into
 * the BikeNav protocol: DIRECTION|DISTANCE|ETA
 *
 * Google Maps notification structure (as of 2024):
 *   Title:   "Turn left"  /  "In 200 m, turn right"  /  "Head north"
 *   Text:    "8:42 PM • via Main St"
 *   SubText: distance or ETA info
 */
public class NavNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "BikeNavListener";

    // Google Maps package name
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";

    // Broadcast actions
    public static final String ACTION_NAV_DATA = "com.bikenav.NAV_DATA";
    public static final String EXTRA_RAW_TEXT = "raw_text";
    public static final String EXTRA_PARSED = "parsed";

    // Throttle: don't send same command repeatedly
    private String lastSentCommand = "";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // Only process Google Maps notifications
        if (!MAPS_PACKAGE.equals(packageName)) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        // Extract notification text fields
        String title = getString(extras, Notification.EXTRA_TITLE);
        String text = getString(extras, Notification.EXTRA_TEXT);
        String subText = getString(extras, Notification.EXTRA_SUB_TEXT);
        String bigText = getString(extras, Notification.EXTRA_BIG_TEXT);

        Log.d(TAG, "Maps Notification — Title: " + title + " | Text: " + text + " | Sub: " + subText);

        if (title == null || title.isEmpty()) return;

        // Parse direction and distance from title
        String direction = parseDirection(title);
        String distance = parseDistance(title);
        String eta = parseETA(text != null ? text : "");

        // Build protocol string
        String protocol = direction + "|" + distance + "|" + eta;

        // Broadcast raw data to UI
        Intent uiBroadcast = new Intent(ACTION_NAV_DATA);
        uiBroadcast.putExtra(EXTRA_RAW_TEXT, title + " | " + text);
        uiBroadcast.putExtra(EXTRA_PARSED, protocol);
        sendBroadcast(uiBroadcast);

        // Send to ESP32 via BluetoothService (only if changed)
        if (!protocol.equals(lastSentCommand)) {
            lastSentCommand = protocol;
            Intent btIntent = new Intent(this, BluetoothService.class);
            btIntent.setAction(BluetoothService.ACTION_SEND);
            btIntent.putExtra(BluetoothService.EXTRA_COMMAND, protocol);
            startService(btIntent);
        }
    }

    /**
     * Maps Google Maps instruction text → BikeNav direction codes
     *
     * Google Maps uses phrases like:
     *   "Turn left", "Turn right", "Continue straight",
     *   "Keep left", "Take the exit", "Make a U-turn",
     *   "At the roundabout, take...", "You have arrived"
     */
    private String parseDirection(String title) {
        String lower = title.toLowerCase().trim();

        // Arrived
        if (lower.contains("arrived") || lower.contains("destination") || lower.contains("you have arrived"))
            return "ARRIVED";

        // U-turn
        if (lower.contains("u-turn") || lower.contains("uturn") || lower.contains("make a u"))
            return "UTURN";

        // Roundabout
        if (lower.contains("roundabout"))
            return "ROUNDABOUT";

        // Exit
        if (lower.contains("take the exit") || lower.contains("take exit") || lower.contains("exit"))
            return "EXIT";

        // Sharp turns (must check before regular turns)
        if (lower.contains("sharp left"))  return "SHARP_LEFT";
        if (lower.contains("sharp right")) return "SHARP_RIGHT";

        // Slight turns (must check before regular turns)
        if (lower.contains("slight left"))  return "SLIGHT_LEFT";
        if (lower.contains("slight right")) return "SLIGHT_RIGHT";

        // Keep left/right
        if (lower.contains("keep left"))  return "KEEP_LEFT";
        if (lower.contains("keep right")) return "KEEP_RIGHT";

        // Regular turns
        if (lower.contains("turn left") || lower.contains("left"))   return "LEFT";
        if (lower.contains("turn right") || lower.contains("right"))  return "RIGHT";

        // Straight
        if (lower.contains("straight") || lower.contains("continue") || lower.contains("head"))
            return "STRAIGHT";

        // Fallback: straight
        return "STRAIGHT";
    }

    /**
     * Extracts distance from notification title.
     *
     * Google Maps formats:
     *   "In 200 m, turn left"
     *   "In 1.2 km, turn right"
     *   "Turn left"  ← no distance prefix
     */
    private String parseDistance(String title) {
        // Pattern: "In X m," or "In X km,"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "in\\s+(\\d+(?:\\.\\d+)?)\\s*(m|km|mi|ft)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2);
        }

        // Arrived or no distance
        if (title.toLowerCase().contains("arrived") || title.toLowerCase().contains("destination")) {
            return "0m";
        }

        return "---";
    }

    /**
     * Extracts ETA from notification text.
     *
     * Google Maps formats:
     *   "8:42 PM • via Main St"
     *   "Arrive by 9:10 PM"
     *   "DONE" for arrived
     */
    private String parseETA(String text) {
        if (text == null || text.isEmpty()) return "---";

        if (text.toLowerCase().contains("arrived") || text.toLowerCase().contains("destination")) {
            return "DONE";
        }

        // Pattern: time like "8:42 PM" or "21:42"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim().toUpperCase();
        }

        return "---";
    }

    private String getString(Bundle extras, String key) {
        Object val = extras.get(key);
        return val != null ? val.toString() : "";
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: could send a "navigation ended" signal
    }
}
