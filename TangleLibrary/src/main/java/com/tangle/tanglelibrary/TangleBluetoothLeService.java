package com.tangle.tanglelibrary;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TangleBluetoothLeService extends Service {
    private final String TAG = TangleBluetoothLeService.class.getName();

    private boolean isDataSent = true;
    public boolean isConnecting = false;

    static final long x7fffffff = Long.decode("0x7fffffff");
    static final long xfff = Long.decode("0xffffffff");

    private final UUID mDeviceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private final UUID mCharacteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private int connectionState = STATE_DISCONNECTED;
    private ChangeBtStateListener listener;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_DISCONNECTING = 3;
    final int FLAG_SYNC_TIMELINE = 242;

    public void connectBt(BluetoothDevice device) {
        isConnecting = true;
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    public void setChangeBtStateListener(ChangeBtStateListener listener) {
        this.listener = listener;
    }

    public interface ChangeBtStateListener {
        void onChangeBtState(int connectionState);
    }

    public void setConnectionState(int connectionState){
        this.connectionState = connectionState;
        if(listener != null) listener.onChangeBtState(connectionState);
    }

    // Various callback methods defined by the BLE API.
    public final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTING:
                    setConnectionState(STATE_CONNECTING);
                    isConnecting = true;
                    Log.i(TAG,"Connecting to GATT server.");
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
                syncTime();
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
        }

    };

    public int getConnectionState(){
        return connectionState;
    }

    public void getPayloadFromTngl(byte[] tnglCode){
        final long syncTimestamp = getTimestamp();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(FLAG_SYNC_TIMELINE);
            outputStream.write(longToBytes(syncTimestamp, 4));
            outputStream.write(longToBytes(0,4)); // timelineTimestamp
            outputStream.write(1); // timelinePaused 0 = false; 1 = true;
            outputStream.write(tnglCode);
        } catch (Exception e){
            Log.e(TAG, "" + e);
        }
        byte [] payload = outputStream.toByteArray();
        write(payload);

    }

    private void write(byte [] payload) {

        long payloadUuid = (long)(Math.random() * xfff);
        int packetSize = 512;
        int bytesSize = packetSize -12;

        int indexFrom = 0;
        int indexTo = bytesSize;

        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(mDeviceUUID).getCharacteristic(mCharacteristicUUID);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        while(indexFrom < payload.length){
            if(indexTo > payload.length){
                indexTo = payload.length;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(longToBytes(payloadUuid, 4));
                outputStream.write(longToBytes(indexFrom, 4));
                outputStream.write(longToBytes(payload.length, 4));
                outputStream.write(Arrays.copyOfRange(payload, indexFrom, indexTo ));
            } catch (Exception e){
                Log.e(TAG, "" + e);
            }
            byte [] bytes = outputStream.toByteArray();

            try{
                Log.d(TAG, "Tray write: " + logBytes(bytes));
                characteristic.setValue(bytes);
            } catch (Exception e){
                Log.e(TAG,"" + e);
            }

            try {
                isDataSent = false;
                bluetoothGatt.writeCharacteristic(characteristic);
            } catch (Exception e) {
                Log.e(TAG, "Value was not wrote");
            }

            indexFrom += bytesSize;
            indexTo = indexFrom + bytesSize;

        }
    }

    public void syncTime(){

        long sync_timestamp = getTimestamp();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(FLAG_SYNC_TIMELINE);
            outputStream.write(longToBytes(sync_timestamp, 4));
            outputStream.write(longToBytes(0,4)); // timelineTimestamp
            outputStream.write(1); // Timeline paused
        } catch (Exception e){
            Log.e(TAG, "" + e);
        }
        byte [] payload = outputStream.toByteArray();

        write(payload);
    }

    public byte[] integerToByte(List<Integer> data) {
        byte[] byteArray = new byte[data.size()];

        for (int i = 0; i < data.size(); i++)
            byteArray[i] = data.get(i).byteValue();

        return byteArray;
    }
    public byte[] longToBytes(long value, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            result[i] = (byte)(value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    public byte[] doubleToBytes(long value, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            result[i] = (byte)(value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    public ArrayList<Integer> logBytes (byte[] data){
        ArrayList<Integer> bytes= new ArrayList<Integer>(data.length);
        if (data.length > 0) {
            for (byte datum : data) {
                bytes.add(datum & 0xFF);
            }
        }
        return bytes;
    }

    public long getTimestamp (){
        return ((new Date().getTime() % x7fffffff));
    }

    public boolean isDataSent (){
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
