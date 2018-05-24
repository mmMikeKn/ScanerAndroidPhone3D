package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

// ESP8266 snd..rcv loop = 80..140us (overage 100us)
/* 180 ms = bluetooth 40 ms + getImage 50ms + fixAllocation 20ms + detect line 40ms + calc cloud 30ms
394 ExternalBluetoothDevice: --- sndDoScanStep(false)
434 ExternalBluetoothDevice: rcv data() ALL:02210000
434 CameraFunc: getImage()
484 CameraFunc: getImage() Allocation ready
484 ImageProcessor: ImageProcessor.fixLaserAllocation
504 ImageProcessor: detect laser Line. START:
534 ImageProcessor: detect laser Line. END
544 ImageProcessor: points cloud calc. END
574 ImageProcessor: prepare outImage. END
574 ExternalBluetoothDevice: --- sndDoScanStep(false)
 */

public class ExternalBluetoothDevice implements Runnable {
    private Handler mAppHandler;
    private static OnErrorListener mOnErrorListener;
    private static volatile OnResponseListener mOnResp;
    private final static String LOG_TAG = "ExternalBluetoothDevice";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice = null;
    private BluetoothSocket mBluetoothSocket = null;
    private OutputStream mBluetoothOutStream = null;
    private InputStream mBluetoothInpStream = null;
    private final ConditionVariable mResponseWaiter = new ConditionVariable();

    private Thread mRcvThread = null;
    private boolean mStopRcv = false;
    private byte mCmd;
    private final static byte STX = 0x02;

    private static final byte CMD_RQ_SCAN_INIT_LASER_POSITION = 0x10;
    private static final byte CMD_RESP_SCAN_INIT_LASER_POSITION = 0x20;
    private static final byte CMD_RQ_SCAN_DO_STEP = 0x11;
    private static final byte CMD_RESP_SCAN_DO_STEP = 0x21;
    private static final byte CMD_RQ_LASER_CTRL = 0x12;
    private static final byte CMD_RESP_LASER_CTRL = 0x22;
    private static final byte CMD_RQ_DO_STEP = 0x13;
    private static final byte CMD_RESP_DO_STEP = 0x23;
    private static final byte CMD_RQ_MOTOR_POWER_OFF = 0x14;
    private static final byte CMD_RESP_MOTOR_POWER_OFF = 0x24;

    private static final byte CMD_RQ_VERSION = 0x2E;
    private static final byte CMD_RESP_VERSION = 0x2E;

    private static final byte CMD_RESP_ERR_MSG = 0x2F;


    SparseIntArray maxTimeout = new SparseIntArray() {{
        put(CMD_RESP_SCAN_DO_STEP, 10000);
        put(CMD_RESP_LASER_CTRL, 1000);
        put(CMD_RESP_VERSION, 1000);
        put(CMD_RESP_SCAN_INIT_LASER_POSITION, 20000);
        put(CMD_RESP_MOTOR_POWER_OFF, 1000);
    }};

    public ExternalBluetoothDevice(BluetoothAdapter mBluetoothAdapter, Handler mAppHandler) {
        this.mAppHandler = mAppHandler;
        mOnErrorListener = new OnErrorListener() {

            @Override
            public void onError(RespState state) {
                Log.e(LOG_TAG, "Err:" + state.getErrorMsg());
            }
        };
        HandlerThread handlerThread = new HandlerThread("LaserDeviceThread");
        handlerThread.start();
        this.mBluetoothAdapter = mBluetoothAdapter;
    }


    private void openBluetooth() {
        if (mBluetoothInpStream != null && mBluetoothOutStream != null) {
            return;
        }
        closeBlueTooth();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        //Log.v(LOG_TAG, "Bluetooth list sz:" + pairedDevices.size());
        for (BluetoothDevice device : pairedDevices) {
            Log.v(LOG_TAG, "Bluetooth:'" + device.getName() + "' " + device.getAddress());
            if (device.getName().startsWith(ConfigDDD.bluetoothDeviceNameMask)) {
                mBluetoothDevice = device;
            }
        }
        if (mBluetoothDevice == null) {
            mOnErrorListener.onError(new RespState("There are no bluetooth devices like '" + ConfigDDD.bluetoothDeviceNameMask + "'"));
            return;
        }
        ParcelUuid[] listUUIDs = mBluetoothDevice.getUuids();
        String btName = mBluetoothDevice.getName();
        if (listUUIDs == null || listUUIDs.length == 0) {
            Log.e(LOG_TAG, "mBluetoothDevice.getUuids() empty for '" + btName + "': ");
            mOnErrorListener.onError(new RespState("UUIDs list is empty for '" + btName + "'"));
            return;
        }

        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(listUUIDs[0].getUuid());
            //mBluetoothSocket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(mBluetoothDevice.getUuids()[0].getUuid());
        } catch (IOException e) {
            Log.e(LOG_TAG, "Socket open error for '" + btName + "': ", e);
            mOnErrorListener.onError(new RespState("Socket open error for '" + btName + "': " + e.toString()));
            return;
        }
        try {
            mBluetoothSocket.connect();
            Log.v(LOG_TAG, "mBluetoothSocket connected ");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Socket connect error for '" + btName + "': ", e);
            mOnErrorListener.onError(new RespState("Socket connect error for '" + btName + "': " + e.toString()));
            return;
        }
        try {
            mBluetoothOutStream = mBluetoothSocket.getOutputStream();
            Log.v(LOG_TAG, "mBluetoothSocket.getOutputStream()");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Socket open stream (out) error for '" + btName + "': ", e);
            mOnErrorListener.onError(new RespState("Socket open stream (out) error for '" + btName + "': " + e.toString()));
            return;
        }
        try {
            mBluetoothInpStream = mBluetoothSocket.getInputStream();
            Log.v(LOG_TAG, "mBluetoothSocket.getInputStream()");
            while (mBluetoothInpStream.available() > 0) {
                //noinspection ResultOfMethodCallIgnored
                mBluetoothInpStream.read(); // flush input stream
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Socket open stream (in) error for '" + btName + "': ", e);
            mOnErrorListener.onError(new RespState("Socket open stream (in) error for '" + btName + "': " + e.toString()));
            return;
        }
        mRcvThread = new Thread(this, "BluetoothRcv");
        mStopRcv = false;
        mRcvThread.setDaemon(true);
        mRcvThread.start();
    }

    public void closeBlueTooth() {
        if (mRcvThread != null) {
            mStopRcv = true;
            mRcvThread.interrupt();
            try {
                mRcvThread.join();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "mRcvThread.join()", e);
            }
            mRcvThread = null;
        }
        try {
            if (mBluetoothOutStream != null) {
                mBluetoothOutStream.close();
                mBluetoothOutStream = null;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "mBluetoothOutStream.close()", e);
        }
        try {
            if (mBluetoothInpStream != null) {
                mBluetoothInpStream.close();
                mBluetoothInpStream = null;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "mBluetoothOutStream.close()", e);
        }
        try {
            if (mBluetoothSocket != null) {
                mBluetoothSocket.close();
                mBluetoothSocket = null;
                Log.v(LOG_TAG, "mBluetoothSocket.close()");
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "mBluetoothSocket.close()", e);
        }
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }


    public void sndGetVersion(OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "sndGetVersion()");
        sendCmd(CMD_RQ_VERSION, null, CMD_RESP_VERSION);
    }

    public void sndInitLaserPosition(final int stepsNumberRed, final int stepsNumberGreen, OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "--- sndInitLaserPosition(R:" + stepsNumberRed + ", G:" + stepsNumberGreen + ")");
        byte val[] = new byte[4];
        val[0] = (byte) (stepsNumberRed >> 8);
        val[1] = (byte) (stepsNumberRed);
        val[2] = (byte) (stepsNumberGreen >> 8);
        val[3] = (byte) (stepsNumberGreen);
        sendCmd(CMD_RQ_SCAN_INIT_LASER_POSITION, val, CMD_RESP_SCAN_INIT_LASER_POSITION);
    }

    public void sndDoScanStep(boolean noDelay, OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "--- sndDoScanStep(" + noDelay + ")");
        final byte ctrl[] = new byte[1];
        ctrl[0] = noDelay ? (byte) 1 : (byte) 0;
        sendCmd(CMD_RQ_SCAN_DO_STEP, ctrl, CMD_RESP_SCAN_DO_STEP);
    }

    public static final byte LASER_MASK_RED = 0x01;
    public static final byte LASER_MASK_GREEN = 0x02;

    public void sndLaserCtrl(final byte msk, OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "--- sndLaserCtrl(" + msk + ") " + this.toString() + " " + mOnResp);
        final byte data[] = new byte[3];
        data[0] = msk;
        data[2] = 120; // 150-ms delay
        sendCmd(CMD_RQ_LASER_CTRL, data, CMD_RESP_LASER_CTRL);
    }

    public void sndMotorsPowerOff(OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "--- sndMotorsPowerOff()");
        sendCmd(CMD_RQ_MOTOR_POWER_OFF, null, CMD_RESP_MOTOR_POWER_OFF);
    }

    private byte getDir(int steps) {
        if (steps == 0) return 0;
        if (steps < 0) return 1;
        return 2;
    }

    public void sndDoStep(int redDir, int greenDir, OnResponseListener listener) {
        mOnResp = listener;
        openBluetooth();
        Log.v(LOG_TAG, "--- sndDoStep(red:" + redDir + ", green:" + greenDir + ")");
        final byte data[] = new byte[2];
        data[0] = getDir(redDir);
        data[1] = getDir(greenDir);
        sendCmd(CMD_RQ_DO_STEP, data, CMD_RESP_DO_STEP);
    }

    public class RespState {
        private boolean isOk = true;
        private String errMsg = "undefined";
        private byte body[] = null;

        public RespState(String errMsg) {
            isOk = false;
            this.errMsg = errMsg;
        }

        public RespState(byte[] body) {
            isOk = true;
            this.body = body;
        }

        public String getErrorMsg() {
            return errMsg;
        }

        @Override
        public String toString() {
            return isOk ? new String(body) : errMsg;
        }
    }


    public interface OnErrorListener {
        void onError(ExternalBluetoothDevice.RespState state);
    }

    public interface OnResponseListener {
        void onResponse(ExternalBluetoothDevice.RespState state);
    }

    //==============================================================================================
    private void execRcvCmd(byte cmd, byte[] body) {
        mCmd = cmd;
        mResponseWaiter.open();

        try {
            final ExternalBluetoothDevice.RespState resp = new ExternalBluetoothDevice.RespState(body);
            if (cmd == CMD_RESP_ERR_MSG) {
                mOnErrorListener.onError(new ExternalBluetoothDevice.RespState(new String(body)));
                return;
            }
            if (mOnResp != null) {
                mAppHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.v(LOG_TAG, "exec OnResponse: " + this.toString() + " " + mOnResp);
                        mOnResp.onResponse(resp);
                    }
                });
            } else {
                Log.e(LOG_TAG, "mOnResp == null");
            }
        } catch (Exception ex) {
            String errMsg = "bluetooth rcv undefined error:" + ex.toString();
            Log.e(LOG_TAG, errMsg, ex);
            mOnErrorListener.onError(new ExternalBluetoothDevice.RespState(errMsg));
        }
    }

    //==============================================================================================
    // STX || cmd[1] || sz[1] || body[sz] || lrc
    private void sendCmd(byte cmd, byte body[], byte cmdResp) {
        if (mBluetoothOutStream == null) return;
        byte rf_TxBuffer[] = new byte[512];
        int sz = body == null ? 0 : body.length;
        rf_TxBuffer[0] = STX;
        rf_TxBuffer[1] = cmd;
        rf_TxBuffer[2] = (byte) sz;
        if (body != null) {
            System.arraycopy(body, 0, rf_TxBuffer, 3, sz);
        }
        rf_TxBuffer[3 + sz] = 0;
        for (int i = 0; i < (sz + 2); i++) {
            rf_TxBuffer[3 + sz] ^= rf_TxBuffer[i + 1];
        }
        try {
            if (maxTimeout.indexOfKey(cmdResp) < 0) {
                mResponseWaiter.close();
            }
            Log.v(LOG_TAG, "sendCmd() start :" + toHexString(rf_TxBuffer, 0, 3 + sz + 1));
            /*
            Попытка найти от чего задержка в 100ms
            for(int i = 0; i < (3 + sz + 1); i++) {
                mBluetoothOutStream.write(rf_TxBuffer[i]);
                mBluetoothOutStream.flush();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
            mBluetoothOutStream.write(rf_TxBuffer, 0, 3 + sz + 1);
            mBluetoothOutStream.flush();
            //Log.v(LOG_TAG, "sendCmd() end");
        } catch (IOException ex) {
            Log.e(LOG_TAG, "sendCmd():", ex);
            mOnErrorListener.onError(new ExternalBluetoothDevice.RespState("Write to bluetooth error:" + ex.getMessage()));
        }
        if (maxTimeout.indexOfKey(cmdResp) < 0) {
            if (mResponseWaiter.block(maxTimeout.get(cmdResp))) {
                if (mCmd != cmdResp) {
                    String errMsg = "Wrong protocol sequence. waiting cmd:" + cmdResp + " rcvCmd:" + mCmd;
                    Log.e(LOG_TAG, errMsg);
                    mOnErrorListener.onError(new ExternalBluetoothDevice.RespState(errMsg));
                }
            } else {
                String errMsg = "Protocol timeout. waiting cmd:" + cmdResp + " timeout:" + maxTimeout.get(cmdResp);
                Log.e(LOG_TAG, errMsg);
                mOnErrorListener.onError(new ExternalBluetoothDevice.RespState(errMsg));
            }
        }
    }

    @Override
    public void run() {
        byte rf_RxBuffer[] = new byte[128];
        int rf_RxBufferPtr = 0;
        int rf_RxDataSz = 0;
        while (!mStopRcv && !Thread.interrupted()) {
            try {
                if (mBluetoothInpStream.available() > 0) {
                    byte c = (byte) mBluetoothInpStream.read();
                    if (rf_RxBufferPtr == 0 && c != STX) {
                        continue;
                    }
                    rf_RxBuffer[rf_RxBufferPtr++] = c;
                    if (rf_RxBufferPtr == 3) {
                        rf_RxDataSz = (int) rf_RxBuffer[2] & 0x0ff;
                        //Log.v(LOG_TAG, "rcv data() HD:" + toHexString(rf_RxBuffer, 0, rf_RxBufferPtr));
                    }
                    int crcOfs = rf_RxDataSz + 3;
                    if (rf_RxBufferPtr > crcOfs) {
                        for (int i = 1; i < crcOfs; i++) {
                            rf_RxBuffer[crcOfs] ^= rf_RxBuffer[i];
                        }
                        if (rf_RxBuffer[crcOfs] != 0) {
                            String errMsg = "Wrong LRC of received data " + toHexString(rf_RxBuffer, 0, rf_RxBufferPtr);
                            mOnErrorListener.onError(new ExternalBluetoothDevice.RespState(errMsg));
                        } else {
                            byte body[] = new byte[rf_RxDataSz];
                            System.arraycopy(rf_RxBuffer, 3, body, 0, rf_RxDataSz);
                            Log.v(LOG_TAG, "rcv data() ALL:" + toHexString(rf_RxBuffer, 0, rf_RxBufferPtr));
                            execRcvCmd(rf_RxBuffer[1], body);
                        }
                        rf_RxBufferPtr = 0;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //-----------------------------
    static private final char[] _hexChars = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};


    public static String toHexString(byte src[], int offset, int len) {
        if (src == null) {
            return "(null)";
        }

        StringBuilder out = new StringBuilder(256);

        for (int i = offset; i < (len + offset) && i < src.length; i++) {
            out.append(_hexChars[(src[i] >> 4) & 0x0f]);
            out.append(_hexChars[src[i] & 0x0f]);
        }
        return out.toString();
    }

}
