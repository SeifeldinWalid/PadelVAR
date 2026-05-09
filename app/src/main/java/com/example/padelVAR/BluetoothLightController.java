package com.example.padelVAR;

// ════════════════════════════════════════════════════════════════
// BluetoothLightController.java
// Controls padel court lights via Bluetooth (HC-05 / HC-06 or
// any BT Serial device).  Sends simple text commands:
//   "LIGHT_ON"  → full brightness
//   "LIGHT_DIM" → half brightness
//   "LIGHT_OFF" → switch off
// ════════════════════════════════════════════════════════════════

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothLightController {

    private static final String TAG = "BTLightController";

    // Standard UUID for Bluetooth Serial Port Profile (SPP)
    // Works with HC-05, HC-06, ESP32 BT classic modules
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Commands sent to the microcontroller / smart light relay
    public static final String CMD_ON  = "LIGHT_ON";
    public static final String CMD_DIM = "LIGHT_DIM";
    public static final String CMD_OFF = "LIGHT_OFF";

    private final BluetoothAdapter btAdapter;
    private BluetoothSocket    socket;
    private OutputStream       outputStream;
    private boolean            connected = false;
    private final ConnectionCallback callback;

    // ────────────────────────────────────────────────────────────
    // Callback interface — your Activity implements this
    // ────────────────────────────────────────────────────────────
    public interface ConnectionCallback {
        void onConnected(String deviceName);   // BT link established
        void onDisconnected();                 // link lost or manually closed
        void onError(String message);          // any failure
    }

    // ────────────────────────────────────────────────────────────
    // Constructor
    // ────────────────────────────────────────────────────────────
    public BluetoothLightController(ConnectionCallback callback) {
        this.callback  = callback;
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ────────────────────────────────────────────────────────────
    // State checks
    // ────────────────────────────────────────────────────────────
    public boolean isBluetoothAvailable() { return btAdapter != null; }

    public boolean isBluetoothEnabled()   { return btAdapter != null && btAdapter.isEnabled(); }

    public boolean isConnected()          { return connected; }

    // ────────────────────────────────────────────────────────────
    // Return paired devices so the Activity can show a picker
    // Returns null if permission is missing on Android 12+
    // ────────────────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Set<BluetoothDevice> getPairedDevices(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
        }
        return btAdapter.getBondedDevices();
    }

    // ────────────────────────────────────────────────────────────
    // Connect to a specific BluetoothDevice (runs on a background thread)
    // ────────────────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void connectToDevice(BluetoothDevice device, Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onError("Bluetooth CONNECT permission not granted");
                return;
            }
        }

        new Thread(() -> {
            try {
                // Close any old socket first
                if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }

                // Create RFCOMM socket using the standard SPP UUID
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);

                // Stop discovery to free up bandwidth for connection
                btAdapter.cancelDiscovery();

                socket.connect();                          // blocks until connected
                outputStream = socket.getOutputStream();
                connected = true;

                // Post success back to the UI thread
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onConnected(device.getName()));

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                connected = false;
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Cannot connect: " + e.getMessage()));
            }
        }).start();
    }

    // ────────────────────────────────────────────────────────────
    // Send a raw text command (runs on background thread)
    // ────────────────────────────────────────────────────────────
    public void sendCommand(String command) {
        if (!connected || outputStream == null) {
            callback.onError("No device connected");
            return;
        }
        new Thread(() -> {
            try {
                // Append newline so the microcontroller knows where the command ends
                outputStream.write((command + "\n").getBytes());
                outputStream.flush();
                Log.d(TAG, "Sent command: " + command);
            } catch (IOException e) {
                Log.e(TAG, "Send failed: " + e.getMessage());
                connected = false;
                new Handler(Looper.getMainLooper()).post(callback::onDisconnected);
            }
        }).start();
    }

    // ────────────────────────────────────────────────────────────
    // Convenience wrappers for the three light states
    // ────────────────────────────────────────────────────────────
    public void lightsOn()  { sendCommand(CMD_ON);  }
    public void lightsDim() { sendCommand(CMD_DIM); }
    public void lightsOff() { sendCommand(CMD_OFF); }

    // ────────────────────────────────────────────────────────────
    // Close the connection (call from onDestroy)
    // ────────────────────────────────────────────────────────────
    public void disconnect() {
        connected = false;
        try {
            if (outputStream != null) outputStream.close();
            if (socket       != null) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Disconnect error: " + e.getMessage());
        }
        new Handler(Looper.getMainLooper()).post(callback::onDisconnected);
    }
}