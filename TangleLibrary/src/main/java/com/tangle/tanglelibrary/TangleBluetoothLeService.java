package com.tangle.tanglelibrary;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

public class TangleBluetoothLeService extends Service {
    private final String TAG = TangleBluetoothLeService.class.getName();
    public final int STATE_DISCONNECTED = 0;
    public final int STATE_CONNECTING = 1;
    public final int STATE_CONNECTED = 2;
    public final int STATE_DISCONNECTING = 3;
    final int FLAG_TNGL_BYTES = 251;
    final int FLAG_SET_TIMELINE = 252;
    final int FLAG_EMIT_EVENT = 253;

    private boolean isDataSent = true;
    private boolean isSynchronized = false;
    public boolean isConnecting = false;
    private boolean paused = true;
    private long time = 0;
    private long startTime;
    private long lastPauseTime;
    private long pauseTime = 0;

    static final long x7fffffff = Long.decode("0x7fffffff");
    static final long xfff = Long.decode("0xffffffff");

    private final UUID mDeviceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private final UUID terminalCharacteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private final UUID syncCharacteristicUUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private int connectionState = STATE_DISCONNECTED;
    private TangleBluetoothLeService.ChangeBtStateListener listener;


    public void connectBt(BluetoothDevice device) {
        isConnecting = true;
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    public void setChangeBtStateListener(TangleBluetoothLeService.ChangeBtStateListener listener) {
        this.listener = listener;
    }

    public interface ChangeBtStateListener {
        void onChangeBtState(int connectionState);
    }

    public void setConnectionState(int connectionState) {
        this.connectionState = connectionState;
        if (listener != null) listener.onChangeBtState(connectionState);
    }

    // Various callback methods defined by the BLE API.
    public final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTING:
                    setConnectionState(STATE_CONNECTING);
                    isConnecting = true;
                    Log.i(TAG, "Connecting to GATT server.");
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    setConnectionState(STATE_CONNECTED);
                    isConnecting = false;
                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" + bluetoothGatt.discoverServices());
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    setConnectionState(STATE_DISCONNECTED);
                    isConnecting = false;
                    Log.i(TAG, "Disconnected from GATT server.");
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                    break;
                case BluetoothProfile.STATE_DISCONNECTING:
                    setConnectionState(STATE_DISCONNECTING);
                    isConnecting = false;
                    Log.i(TAG, "Disconnecting from GATT server.");
                    break;
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                new Thread(() -> {
                    syncClock();
                    while (!isSynchronized) {
                        Log.i(TAG, "OnServiceDiscovered: Waiting for free corridor");
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    syncTimeline();
                }).start();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X ", byteChar));
                    String extraData = new String(data) + "\n" + stringBuilder.toString();
                    Log.d(TAG, extraData);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                Log.d(TAG, "Wrote: " + logBytes(data));
                isDataSent = true;
            }
            if (data[0] == 0) {
                isSynchronized = true;
            }
        }

    };

    public int getConnectionState() {
        return connectionState;
    }

    public void getPayloadFromTngl(byte[] tnglCode) {
        final long syncTimestamp = getClockTimestamp();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Timeline bytes */
            outputStream.write(FLAG_SET_TIMELINE);
            outputStream.write(longToBytes(syncTimestamp, 4));
            outputStream.write(longToBytes(0, 4)); // timelineTimestamp
            /* Timeline flag */
            outputStream.write(getTimelineFlag(0, 0)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;

            outputStream.write(tnglCode); // tngl bytes
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();
        write(payload);

    }

    public void getPayloadFromTngl(byte[] tnglCode, Long timeline_timestamp, boolean timeline_paused) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Timeline bytes */
            outputStream.write(FLAG_SET_TIMELINE);
            outputStream.write(longToBytes(getClockTimestamp(), 4));
            outputStream.write(longToBytes(timeline_timestamp, 4)); // timelineTimestamp
            /* Timeline flag */
            outputStream.write(getTimelineFlag(0, timeline_paused ? 1 : 0)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;

            outputStream.write(tnglCode); // tngl bytes
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();
        write(payload);

    }

    public void write(byte[] payload) {

        long payloadUuid = (long) (Math.random() * xfff);
        int packetSize = 512;
        int bytesSize = packetSize - 12;

        int indexFrom = 0;
        int indexTo = bytesSize;

        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(mDeviceUUID).getCharacteristic(terminalCharacteristicUUID);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        while (indexFrom < payload.length) {
            if (indexTo > payload.length) {
                indexTo = payload.length;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(longToBytes(payloadUuid, 4));
                outputStream.write(longToBytes(indexFrom, 4));
                outputStream.write(longToBytes(payload.length, 4));
                outputStream.write(Arrays.copyOfRange(payload, indexFrom, indexTo));
            } catch (Exception e) {
                Log.e(TAG, "" + e);
            }
            byte[] bytes = outputStream.toByteArray();

            try {
                Log.d(TAG, "Tray write: " + logBytes(bytes));
                characteristic.setValue(bytes);
            } catch (Exception e) {
                Log.e(TAG, "" + e);
            }
            new Thread(() -> {
                while (!isDataSent) {
                    Log.i(TAG, "write: Waiting for corridor");
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    isDataSent = false;
                    bluetoothGatt.writeCharacteristic(characteristic);
                } catch (Exception e) {
                    Log.e(TAG, "Value was not wrote");
                }
            }).start();

            indexFrom += bytesSize;
            indexTo = indexFrom + bytesSize;

        }
    }

    public void syncClock() {
        long clock_timestamp = getClockTimestamp();
        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(mDeviceUUID).getCharacteristic(syncCharacteristicUUID);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(longToBytes(clock_timestamp, 4));
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] bytes = outputStream.toByteArray();

        try {
            Log.d(TAG, "Tray write: " + logBytes(bytes));
            characteristic.setValue(bytes);
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }

        try {
            isDataSent = false;
            bluetoothGatt.writeCharacteristic(characteristic);
            new Thread(() -> {
                while (!isDataSent) {
                    Log.i(TAG, "syncClock: Waiting for freeCorridor");
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                characteristic.setValue(new byte[]{0});
                isDataSent = false;
                bluetoothGatt.writeCharacteristic(characteristic);
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Value was not wrote");
        }
    }

    public void syncTimeline() {

        long clock_timestamp = getClockTimestamp();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Timeline bytes */
            outputStream.write(FLAG_SET_TIMELINE);
            outputStream.write(longToBytes(clock_timestamp, 4));
            outputStream.write(longToBytes(0, 4)); // timelineTimestamp
            /* Timeline flag */
            outputStream.write(getTimelineFlag(0, 1)); // 0 = main timeline, Timeline paused 1 = paused | 0 = play
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();

        write(payload);
    }

    public void setTimeline(Long timeline_timestamp, boolean timeline_paused) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Timeline bytes */
            outputStream.write(FLAG_SET_TIMELINE);
            outputStream.write(longToBytes(getClockTimestamp(), 4));
            outputStream.write(longToBytes(timeline_timestamp, 4)); // timelineTimestamp
            /* Timeline flag */
            outputStream.write(getTimelineFlag(0, timeline_paused ? 1 : 0)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();

        write(payload);
    }

    public long startTimeline() {

        if (paused) {

            paused = false;
            startTime = SystemClock.elapsedRealtime();
            if (pauseTime == 0) {
                time = pauseTime;
            }

            Log.d(TAG, "startTime: " + (time / 1000));
            long clock_timestamp = getClockTimestamp();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                /* Timeline bytes */
                outputStream.write(FLAG_SET_TIMELINE);
                outputStream.write(longToBytes(clock_timestamp, 4));
                outputStream.write(longToBytes(time, 4)); // timelineTimestamp
                /* Timeline flag */
                outputStream.write(getTimelineFlag(0, 0)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;
            } catch (Exception e) {
                Log.e(TAG, "" + e);
            }
            byte[] payload = outputStream.toByteArray();

            write(payload);
        }
        return time;
    }

    public long pauseTimeline() {

        if (!paused) {
            paused = true;
            lastPauseTime = SystemClock.elapsedRealtime();
            pauseTime = lastPauseTime - startTime;
            time += pauseTime;
            Log.d(TAG, "pauseTime: " + (time / 1000));

            long clock_timestamp = getClockTimestamp();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                /* Timeline bytes */
                outputStream.write(FLAG_SET_TIMELINE);
                outputStream.write(longToBytes(clock_timestamp, 4));
                outputStream.write(longToBytes(time, 4)); // timelineTimestamp
                /* Timeline flag */
                outputStream.write(getTimelineFlag(0, 1)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;
            } catch (Exception e) {
                Log.e(TAG, "" + e);
            }
            byte[] payload = outputStream.toByteArray();

            write(payload);
        }

        return time;
    }

    public long stopTimeline() {

        paused = true;

        pauseTime = 0;
        time = 0;
        Log.d(TAG, "stopTime: " + (time / 1000));

        long clock_timestamp = getClockTimestamp();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Timeline bytes */
            outputStream.write(FLAG_SET_TIMELINE);
            outputStream.write(longToBytes(clock_timestamp, 4));
            outputStream.write(longToBytes(time, 4)); // timelineTimestamp
            /* Timeline flag */
            outputStream.write(getTimelineFlag(0, 1)); // 0 = main timeline, timelinePaused 0 = false; 1 = true;
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();

        write(payload);
        return time;
    }

    public void emitEvent(int device_id, int code, int parameter, long timeline_timestamp) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            /* Events bytes */
            outputStream.write(FLAG_EMIT_EVENT);
            outputStream.write(device_id);
            outputStream.write(code);
            outputStream.write(parameter);
            /* Timeline timestamp */
            outputStream.write(longToBytes(timeline_timestamp, 4)); // timelineTimestamp
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        byte[] payload = outputStream.toByteArray();

        write(payload);
    }

    public byte getTimelineFlag(int timelineIndex, int timelinePaused) {
        byte timeline_index = (byte) (timelineIndex & 0b00001111);
        byte timeline_paused = (byte) ((timelinePaused << 4) & 0b00010000);
        return (byte) (timeline_paused | timeline_index);
    }

    public byte[] integerToByte(int value, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    public byte[] longToBytes(long value, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    public byte[] doubleToBytes(long value, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    public ArrayList<Integer> logBytes(byte[] data) {
        ArrayList<Integer> bytes = new ArrayList<Integer>(data.length);
        if (data.length > 0) {
            for (byte datum : data) {
                bytes.add(datum & 0xFF);
            }
        }
        return bytes;
    }

    public long getClockTimestamp() {
        return ((new Date().getTime() % x7fffffff));
    }

    public boolean isDataSent() {
        return isDataSent;
    }

    public void close() {
        Log.d(TAG, "Call close");
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
