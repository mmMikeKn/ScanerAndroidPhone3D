package com.home.mm.ddd_scanner;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.hardware.camera2.CameraManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.RenderScript;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.support.v4.app.ActivityCompat;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.home.mm.dddscanmm.R;

public class MainActivity extends Activity {
    private final static String LOG_TAG = "Main";
    private TextureView mPreView = null;
    private TextureView mProcessorView = null;
    private Handler mAppHandler;

    private ImageProcessor mImageProcessor;
    private ExternalBluetoothDevice mExternalBluetoothDevice;
    private IrCommands mIrCommands;

    private final static int MODE_PREVIEW = 0;
    private final static int MODE_SCAN = 1;
    private final static int MODE_SAVE = 2;
    private final static int MODE_TEST = 3;
    private int mMode;
    private int scanStepCounter;
    private String rawFileNameForRecalculate;
    private boolean isRecalculateMode;

    private Button mBtnStart, mBtnFinish, mBtnSave, mBtnCancel, mBtnConfigure, mBtnVersion;
    private Button mBtnReCalculate, mBtnStartTestMode, mBtnLaserGotoTestPosition;
    private Button mBtnLaserOn, mBtnLaserOff, mBtnLaserGotoStart, mBtnLaserMotorPowerOff;
    private Button mButtonRedTestAngleMinus, mButtonRedTestAnglePlus, mButtonRedTestGotoLeft, mButtonRedTestGotoRight, mButtonRedTestGotoCenter;
    private Button mButtonGreenTestAngleMinus, mButtonGreenTestAnglePlus, mButtonGreenTestGotoLeft, mButtonGreenTestGotoRight, mButtonGreenTestGotoCenter;
    private Button mButtonCancelTestMode;

    private TextView mTextViewProcessMsg;
    private TextView mTextViewTestInfoRed, mTextViewTestInfoGreen, mTextViewScrInfo, mTextViewTestInfoRedAdd, mTextViewTestInfoGreenAdd;
    private List<TextView> uiModePreview = new ArrayList<>();
    private List<TextView> uiModeTest = new ArrayList<>();
    private List<TextView> uiModeSave = new ArrayList<>();
    private final static double TEST_DD_ANGLE = 0.1;
    private final static int TEST_DD_STEPS = 200;
    private final static int TEST_MODE_NONE = 0;
    private final static int TEST_MODE_GOTO_CENTER = 1;
    private final static int TEST_MODE_GOTO_LEFT = 2;
    private final static int TEST_MODE_GOTO_RIGHT = 3;
    private int testModeRed, testModeGreen;
    private int testModeStepCntRed, testModeStepCntGreen;


    private CameraFunctions mCameraFunctions;
    private RenderScript mRS;

    private static final String[] APP_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private boolean hasPermissions() {
        for (String permission : APP_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    //================================================================================================
    private void fixNoLaserImage() {
        mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
        mImageProcessor.fixNoLaserAllocation(mCameraFunctions.getRGB8888Allocation());
    }

    private boolean chkScanMode() {
        if (scanStepCounter >= ConfigDDD.fullScanStepsNumber) {
            Log.v(LOG_TAG, "Stop scanning going to MODE_SAVE on fullScanStepsNumber");
            mMode = MODE_SAVE;
        }
        if (mCameraFunctions == null) {
            Log.v(LOG_TAG, "Stop scanning on mCameraFunctions == null");
            return false;
        }
        if (mMode != MODE_SCAN) {
            if (mMode == MODE_SAVE) {
                lasersOff();
                motorsOff();
                mAppHandler.post(new Runnable() {
                    public void run() {
                        Log.v(LOG_TAG, "Stop scanning going to MODE_SAVE");
                        mBtnFinish.setVisibility(View.INVISIBLE);
                        mTextViewProcessMsg.setVisibility(View.VISIBLE);
                        mTextViewProcessMsg.setText("");
                        new Thread(new Runnable() {
                            public void run() {
                                mImageProcessor.buildColorMap(scanStepCounter, new OnCalcPointCloud());
                            }
                        }).start();
                    }
                });
            }
            return false;
        }
        return true;
    }

    private void startScanForIRCtrlMode(boolean fixNoLaser) {
        Log.v(LOG_TAG, "----- startScanForIRCtrlMode step:" + scanStepCounter);
        if (!chkScanMode()) return;
        if (fixNoLaser) {
            if (!mIrCommands.sendIRCmd(ConfigDDD.IRCtrlLasersTimeMs, IrCommands.CMD_IR_LASERS_OFF))
                return;
            if (!mCameraFunctions.getImage(mPreView)) return;
            fixNoLaserImage();
            if (mIrCommands.sendIRCmd(ConfigDDD.IRCtrlLasersTimeMs, IrCommands.CMD_IR_LASERS_ON))
                startScanForIRCtrlMode(false);
        } else {
            if (!mCameraFunctions.getImage(mPreView)) return;
            mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
            mImageProcessor.detectLaserLine(scanStepCounter, scanStepCounter, mCameraFunctions.getRGB8888Allocation());
            scanStepCounter++;
            if (mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_DO_STEP))
                mAppHandler.postDelayed(new Runnable() {
                    public void run() {
                        startScanForIRCtrlMode(ConfigDDD.notLaserStepDivider != 0 && (scanStepCounter % ConfigDDD.notLaserStepDivider) == 0);
                    }
                }, ConfigDDD.IRCtrlStepTimeMs);
        }
    }

    private void startScanForExternalBluetooth(boolean fixNoLaser) {
        Log.v(LOG_TAG, "----- startScanForExternalBluetooth step:" + scanStepCounter);
        if (!chkScanMode()) return;
        if (fixNoLaser) {
            mExternalBluetoothDevice.sndLaserCtrl((byte) 0, new ExternalBluetoothDevice.OnResponseListener() {
                @Override
                public void onResponse(ExternalBluetoothDevice.RespState state) {
                    if (!mCameraFunctions.getImage(mPreView)) return;
                    fixNoLaserImage();
                    mExternalBluetoothDevice.sndLaserCtrl((byte) 3, new ExternalBluetoothDevice.OnResponseListener() {
                        @Override
                        public void onResponse(ExternalBluetoothDevice.RespState state) {
                            startScanForExternalBluetooth(false);
                        }
                    });
                }
            });
        } else {
            if (!mCameraFunctions.getImage(mPreView)) return;
            mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
            mImageProcessor.detectLaserLine(scanStepCounter, scanStepCounter, mCameraFunctions.getRGB8888Allocation());
            scanStepCounter++;
            mExternalBluetoothDevice.sndDoScanStep(false, new ExternalBluetoothDevice.OnResponseListener() {
                @Override
                public void onResponse(ExternalBluetoothDevice.RespState state) {
                    startScanForExternalBluetooth(ConfigDDD.notLaserStepDivider != 0 && (scanStepCounter % ConfigDDD.notLaserStepDivider) == 0);
                }
            });
        }
    }

    //================================================================================================
    @SuppressLint("DefaultLocale")
    private String getTestModeDescription(int mode, int cnt) {
        switch (mode) {
            case TEST_MODE_GOTO_CENTER:
                return String.format("\ngoto center (%d)", cnt);
            case TEST_MODE_GOTO_LEFT:
                return String.format("\ngoto left (%d)", Math.abs(cnt));
            case TEST_MODE_GOTO_RIGHT:
                return String.format("\ngoto right (%d)", Math.abs(cnt));
        }
        return "";
    }

    @SuppressLint("DefaultLocale")
    private void testExec(final Runnable r) {
        if (mCameraFunctions == null) return;
        if (mMode != MODE_TEST) {
            startPreview();
            return;
        }
        Log.v(LOG_TAG, "testExec start");
        mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
        mImageProcessor.detectLaserLine(ConfigDDD.stepMotorRedTestSteps, ConfigDDD.stepMotorGreenTestSteps, mCameraFunctions.getRGB8888Allocation());
        ImageProcessor.PointInfo pRed = mImageProcessor.getRedPointForTest(ConfigDDD.stepMotorRedTestSteps);
        ImageProcessor.PointInfo pGreen = mImageProcessor.getGreenPointForTest(ConfigDDD.stepMotorGreenTestSteps);
        mTextViewTestInfoRed.setText(pRed.msg);
        mTextViewTestInfoGreen.setText(pGreen.msg);
        mTextViewScrInfo.setText(String.format("screen: %dx%d tangent: %1.6f",
                mCameraFunctions.getSize().getHeight(), mCameraFunctions.getSize().getWidth(),
                ConfigDDD.cameraViewTangent));
        int centerPoint = mCameraFunctions.getSize().getHeight() / 2;
        //-------------------- Red --------------
        if (testModeRed == TEST_MODE_GOTO_CENTER) {
            testModeStepCntRed = centerPoint - pRed.point;
        }
        if (Math.abs(testModeStepCntRed) == 0) testModeRed = TEST_MODE_NONE;
        double daRed = ConfigDDD.stepMotorRedTestSteps * 360.0 / ConfigDDD.stepMotorStepsPer2PI;
        mTextViewTestInfoRedAdd.setText(String.format("[%d/%3.2f] a: %3.2f%s\n",
                ConfigDDD.stepMotorRedTestSteps,
                daRed,
                ConfigDDD.stepMotorStartAngleRed + daRed,
                getTestModeDescription(testModeRed, testModeStepCntRed)));
        //---------------------Green -------------------
        if (testModeGreen == TEST_MODE_GOTO_CENTER) {
            testModeStepCntGreen = centerPoint - pGreen.point;
        }
        if (Math.abs(testModeStepCntGreen) == 0) testModeGreen = TEST_MODE_NONE;
        double daGreen = ConfigDDD.stepMotorGreenTestSteps * 360.0 / ConfigDDD.stepMotorStepsPer2PI;
        mTextViewTestInfoGreenAdd.setText(String.format("[%d/%3.2f] a: %3.2f%s",
                ConfigDDD.stepMotorGreenTestSteps,
                daGreen,
                ConfigDDD.stepMotorStartAngleGreen + daGreen,
                getTestModeDescription(testModeGreen, testModeStepCntGreen)));
        //----------------------------------------------
        scanStepCounter++;
        if (testModeRed != TEST_MODE_NONE || testModeGreen != TEST_MODE_NONE) {
            if (ConfigDDD.isBluetoothDeviceMode) {
                mExternalBluetoothDevice.sndDoStep(testModeStepCntRed, testModeStepCntGreen, new ExternalBluetoothDevice.OnResponseListener() {
                    @Override
                    public void onResponse(ExternalBluetoothDevice.RespState state) {
                        if (testModeStepCntRed < 0) {
                            testModeStepCntRed++;
                            ConfigDDD.stepMotorRedTestSteps--;
                        } else if (testModeStepCntRed > 0) {
                            testModeStepCntRed--;
                            ConfigDDD.stepMotorRedTestSteps++;
                        } else testModeRed = TEST_MODE_NONE;
                        if (testModeStepCntGreen < 0) {
                            testModeStepCntGreen++;
                            ConfigDDD.stepMotorGreenTestSteps++;
                        } else if (testModeStepCntGreen > 0) {
                            testModeStepCntGreen--;
                            ConfigDDD.stepMotorGreenTestSteps--;
                        } else testModeGreen = TEST_MODE_NONE;
                        mAppHandler.post(r);
                    }
                });
                return;
            } else {
                if (testModeStepCntRed < 0) {
                    mIrCommands.sendIRCmd(ConfigDDD.IRCtrlStepTimeMs, IrCommands.CMD_IR_STEP_RED_LEFT);
                    testModeStepCntRed++;
                    ConfigDDD.stepMotorRedTestSteps--;
                } else if (testModeStepCntRed > 0) {
                    mIrCommands.sendIRCmd(ConfigDDD.IRCtrlStepTimeMs, IrCommands.CMD_IR_STEP_RED_RIGHT);
                    testModeStepCntRed--;
                    ConfigDDD.stepMotorRedTestSteps++;
                } else testModeRed = TEST_MODE_NONE;
                if (testModeStepCntGreen < 0) {
                    mIrCommands.sendIRCmd(ConfigDDD.IRCtrlStepTimeMs, IrCommands.CMD_IR_STEP_GREEN_LEFT);
                    testModeStepCntGreen++;
                    ConfigDDD.stepMotorGreenTestSteps++;
                } else if (testModeStepCntGreen > 0) {
                    mIrCommands.sendIRCmd(ConfigDDD.IRCtrlStepTimeMs, IrCommands.CMD_IR_STEP_GREEN_RIGHT);
                    testModeStepCntGreen--;
                    ConfigDDD.stepMotorGreenTestSteps--;
                } else testModeGreen = TEST_MODE_NONE;
            }
        }
        if (ConfigDDD.stepMotorRedTestSteps > ConfigDDD.fullScanStepsNumber) {
            ConfigDDD.stepMotorRedTestSteps = ConfigDDD.fullScanStepsNumber - 1;
            testModeRed = TEST_MODE_NONE;
        }
        if (ConfigDDD.stepMotorGreenTestSteps > ConfigDDD.fullScanStepsNumber) {
            ConfigDDD.stepMotorGreenTestSteps = ConfigDDD.fullScanStepsNumber - 1;
            testModeGreen = TEST_MODE_NONE;
        }
        Log.v(LOG_TAG, "testExec end");
        mAppHandler.post(r);
    }

    private void startTestForIRCtrlMode(final boolean fixNoLaser) {
        Log.v(LOG_TAG, "----- startTestForIRCtrlMode step:" + scanStepCounter);
        if (mCameraFunctions == null) {
            Log.v(LOG_TAG, "Stop scanning on mCameraFunctions == null");
            return;
        }
        if (fixNoLaser) {
            if (!mIrCommands.sendIRCmd(ConfigDDD.IRCtrlLasersTimeMs, IrCommands.CMD_IR_LASERS_OFF))
                return;
            if (!mCameraFunctions.getImage(mPreView)) return;
            fixNoLaserImage();
            if (!mIrCommands.sendIRCmd(ConfigDDD.IRCtrlLasersTimeMs, IrCommands.CMD_IR_LASERS_ON))
                return;
            startTestForIRCtrlMode(false);
        } else {
            if (!mCameraFunctions.getImage(mPreView)) return;
            testExec(new Runnable() {
                @Override
                public void run() {
                    startTestForIRCtrlMode(ConfigDDD.notLaserStepDivider != 0 && (scanStepCounter % ConfigDDD.notLaserStepDivider) == 0);
                }
            });
        }
    }

    private void startTestForExternalBluetoothDevice(final boolean fixNoLaser) {
        Log.v(LOG_TAG, "----- startTestForExternalBluetoothDevice step:" + scanStepCounter + " fixNoLaser:" + fixNoLaser);
        if (mCameraFunctions == null) {
            Log.v(LOG_TAG, "Stop scanning on mCameraFunctions == null");
            return;
        }
        if (fixNoLaser) {
            mExternalBluetoothDevice.sndLaserCtrl((byte) 0, new ExternalBluetoothDevice.OnResponseListener() {
                @Override
                public void onResponse(ExternalBluetoothDevice.RespState state) {
                    if (!mCameraFunctions.getImage(mPreView)) {
                        Log.v(LOG_TAG, "getImage return false;");
                        return;
                    }
                    fixNoLaserImage();
                    mExternalBluetoothDevice.sndLaserCtrl((byte) (ExternalBluetoothDevice.LASER_MASK_GREEN | ExternalBluetoothDevice.LASER_MASK_RED),
                            new ExternalBluetoothDevice.OnResponseListener() {
                                @Override
                                public void onResponse(ExternalBluetoothDevice.RespState state) {
                                    startTestForExternalBluetoothDevice(false);
                                }
                            }
                    );
                }
            });
        } else {
            if (!mCameraFunctions.getImage(mPreView)) return;
            testExec(new Runnable() {

                @Override
                public void run() {
                    startTestForExternalBluetoothDevice(ConfigDDD.notLaserStepDivider != 0 && (scanStepCounter % ConfigDDD.notLaserStepDivider) == 0);
                }
            });
        }
    }
    //================================================================================================


    private void setPreViewTiny() {
        FrameLayout fl = (FrameLayout) findViewById(R.id.preViewLayout);
        Display display = getWindowManager().getDefaultDisplay();
        Point sz = new Point();
        display.getSize(sz);
        fl.getLayoutParams().height = sz.y / 8;
        fl.getLayoutParams().width = sz.x / 8;
        fl.requestLayout();
    }

    private void showExtHwdError(final ExternalBluetoothDevice.RespState state) {
        mAppHandler.post(new Runnable() {
            public void run() {
                Log.e(LOG_TAG, "Err:" + state.getErrorMsg());
                startPreview();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("External laser scanner error.")
                        .setMessage("Err:" + state.getErrorMsg())
                        .setCancelable(true);
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private void configureUI() {
        Log.v(LOG_TAG, "configureUI() mMode:" + mMode);
        if (uiModePreview.isEmpty()) {
            uiModePreview.add(mBtnStart);
            uiModePreview.add(mBtnStartTestMode);
            uiModePreview.add(mBtnLaserGotoTestPosition);
            uiModePreview.add(mBtnLaserGotoStart);
            uiModePreview.add(mBtnLaserOn);
            uiModePreview.add(mBtnLaserOff);
            uiModePreview.add(mBtnLaserMotorPowerOff);
            uiModePreview.add(mBtnReCalculate);
            uiModePreview.add(mBtnConfigure);
            uiModePreview.add(mBtnVersion);
        }
        if (uiModeSave.isEmpty()) {
            uiModeSave.add(mBtnSave);
            uiModeSave.add(mBtnCancel);
        }
        if (uiModeTest.isEmpty()) {
            uiModeTest.add(mTextViewTestInfoGreen);
            uiModeTest.add(mTextViewScrInfo);

            uiModeTest.add(mTextViewTestInfoRed);
            uiModeTest.add(mButtonRedTestAngleMinus);
            uiModeTest.add(mButtonRedTestAnglePlus);
            uiModeTest.add(mButtonRedTestGotoLeft);
            uiModeTest.add(mButtonRedTestGotoRight);
            uiModeTest.add(mButtonRedTestGotoCenter);
            uiModeTest.add(mTextViewTestInfoRedAdd);

            uiModeTest.add(mTextViewTestInfoGreen);
            uiModeTest.add(mButtonGreenTestAngleMinus);
            uiModeTest.add(mButtonGreenTestAnglePlus);
            uiModeTest.add(mButtonGreenTestGotoLeft);
            uiModeTest.add(mButtonGreenTestGotoRight);
            uiModeTest.add(mButtonGreenTestGotoCenter);
            uiModeTest.add(mTextViewTestInfoGreenAdd);

            uiModeTest.add(mButtonCancelTestMode);
        }
        //------------------

        for (TextView item : uiModePreview) {
            item.setVisibility(mMode == MODE_PREVIEW ? View.VISIBLE : View.INVISIBLE);
        }
        for (TextView item : uiModeSave) {
            item.setVisibility(mMode == MODE_SAVE ? View.VISIBLE : View.INVISIBLE);
        }
        for (TextView item : uiModeTest) {
            item.setVisibility(mMode == MODE_TEST ? View.VISIBLE : View.INVISIBLE);
        }
        if (!ConfigDDD.isBluetoothDeviceMode) {
            mBtnVersion.setVisibility(View.INVISIBLE);
        }
        mBtnFinish.setVisibility(mMode == MODE_TEST || mMode == MODE_SCAN ? View.VISIBLE : View.INVISIBLE);
        mTextViewProcessMsg.setVisibility(View.INVISIBLE);
    }

    private void startPreview() {
        mMode = MODE_PREVIEW;
        isRecalculateMode = false;
        configureUI();
        FrameLayout fl = (FrameLayout) findViewById(R.id.preViewLayout);
        Display display = getWindowManager().getDefaultDisplay();
        Point sz = new Point();
        display.getSize(sz);
        fl.getLayoutParams().height = sz.y;
        fl.getLayoutParams().width = sz.x;
        fl.requestLayout();
    }

    private class OnCalcPointCloud implements ImageProcessor.OnLongWorkListener {

        @Override
        public void onChangeState(final String msg) {
            mAppHandler.post(new Runnable() {
                public void run() {
                    mTextViewProcessMsg.setText(msg);
                }
            });
        }

        @Override
        public void onError(final String msg) {
            Log.e(LOG_TAG, "calcData Err:" + msg);
            mAppHandler.post(new Runnable() {
                public void run() {
                    FatalErrorDialog.showError(msg);
                    startPreview();
                }
            });
        }

        @Override
        public void onFinish() {
            mAppHandler.post(new Runnable() {
                public void run() {
                    mBtnSave.setVisibility(View.VISIBLE);
                    mBtnCancel.setVisibility(View.VISIBLE);
                    mTextViewProcessMsg.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    private void recalculateRawData() {
        mMode = MODE_SAVE;
        configureUI();
        setPreViewTiny();
        mBtnSave.setVisibility(View.INVISIBLE);
        mBtnCancel.setVisibility(View.INVISIBLE);
        mTextViewProcessMsg.setVisibility(View.VISIBLE);
        mTextViewProcessMsg.setText("");

        mImageProcessor.setupInternalValues();
        mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
        isRecalculateMode = true;
        mImageProcessor.loadOldRawData(rawFileNameForRecalculate, new OnCalcPointCloud());
    }

    //==============================================================================================

    private void saveColorMapFile(String fullFileMask, ImageProcessor.OnLongWorkListener listener) {
        if (ConfigDDD.colorMapMode == ConfigDDD.MODE_COLORMAP_PNG) {
            mImageProcessor.saveColorMap8(fullFileMask, mProcessorView, listener);
        } else if (ConfigDDD.colorMapMode == ConfigDDD.MODE_COLORMAP_TIFF) {
            mImageProcessor.saveColorMap16(fullFileMask, mProcessorView, listener);
        } else {
            listener.onFinish();
        }
    }


    private void saveResult() {
        if (ConfigDDD.pathForFilesSave.length() == 0)
            ConfigDDD.pathForFilesSave = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
        mTextViewProcessMsg.setText("");
        mBtnSave.setVisibility(View.INVISIBLE);
        mBtnCancel.setVisibility(View.INVISIBLE);
        mTextViewProcessMsg.setVisibility(View.VISIBLE);
        final String fullFileMask = isRecalculateMode ?
                rawFileNameForRecalculate.substring(0, rawFileNameForRecalculate.lastIndexOf(".")) :
                ConfigDDD.pathForFilesSave + (ConfigDDD.isFixedFileName ? "/scannerMM" : new SimpleDateFormat("yyMddHHmmss").format(new Date()));
        saveColorMapFile(fullFileMask, new OnCalcPointCloud() {
            @Override
            public void onFinish() {
                mImageProcessor.saveDataFiles(scanStepCounter, isRecalculateMode, fullFileMask, new OnCalcPointCloud() {
                    @Override
                    public void onFinish() {
                        mAppHandler.post(new Runnable() {
                            public void run() {
                                startPreview();
                            }
                        });
                    }
                });
            }
        });
    }

    private void openBlueTooth() {
        if (ConfigDDD.isBluetoothDeviceMode) {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) {
                FatalErrorDialog.showError("Device does not support Bluetooth");
            } else if (!bt.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
            mExternalBluetoothDevice = new ExternalBluetoothDevice(bt, this.mAppHandler);
            mExternalBluetoothDevice.setOnErrorListener(new ExternalBluetoothDevice.OnErrorListener() {
                @Override
                public void onError(ExternalBluetoothDevice.RespState state) {
                    showExtHwdError(state);
                }
            });
        }
    }

    private void lasersOff() {
        if (ConfigDDD.isBluetoothDeviceMode) mExternalBluetoothDevice.sndLaserCtrl((byte) 0, null);
        else mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_LASERS_OFF);
    }

    private void motorsOff() {
        if (ConfigDDD.isBluetoothDeviceMode) mExternalBluetoothDevice.sndMotorsPowerOff(null);
        else mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_MOTORS_OFF);
    }
    //================================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(LOG_TAG, "-------- onCreate()");

        try {
            ConfigDDD.loadConfig(getSharedPreferences(ConfigDDD.configName, 0));
        } catch (Exception e) {
            Log.e(LOG_TAG, "load config.", e);
            FatalErrorDialog.showError(e.toString());
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Log.v(LOG_TAG, "onCreate() End of configure RenderScript");

        FatalErrorDialog.mFatalErrMsgHandler = mAppHandler = new Handler(Looper.getMainLooper());
        FatalErrorDialog.mFragmentManager = getFragmentManager();
        setContentView(R.layout.activity_main);
        //---------------------------------------------------------
        openBlueTooth();
        mIrCommands = new IrCommands((ConsumerIrManager) this.getSystemService(Context.CONSUMER_IR_SERVICE));
        //---------------------------------------------------------
        mPreView = (TextureView) findViewById(R.id.preView);
        mPreView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //Log.v(LOG_TAG, "onSurfaceTextureUpdated()");
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.v(LOG_TAG, "onSurfaceTextureSizeChanged()");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.v(LOG_TAG, "onSurfaceTextureDestroyed()");
                return true;
            }

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.v(LOG_TAG, "onSurfaceTextureAvailable()");
                if (mCameraFunctions != null && mCameraFunctions.selectCamera((CameraManager) getSystemService(CAMERA_SERVICE))) {
                    SurfaceTexture texture = mPreView.getSurfaceTexture();
                    texture.setDefaultBufferSize(mCameraFunctions.getSize().getWidth(), mCameraFunctions.getSize().getHeight());
                    mCameraFunctions.openCamera(new Surface(texture));
                    if (!mCameraFunctions.getImage(mPreView)) return;
                    mImageProcessor.initAllocations(mCameraFunctions.getRGB8888Allocation(), mCameraFunctions.getSize(), mProcessorView.getSurfaceTexture());
                }
            }
        });
        //---------------------------------------------------------
        mTextViewProcessMsg = (TextView) findViewById(R.id.textViewsSaveMsg);
        mTextViewTestInfoRed = (TextView) findViewById(R.id.textViewTestInfoRed);
        mTextViewTestInfoGreen = (TextView) findViewById(R.id.textViewTestInfoGreen);
        mTextViewScrInfo = (TextView) findViewById(R.id.textViewScrInfo);
        mTextViewTestInfoRedAdd = (TextView) findViewById(R.id.textViewTestInfoRedAdd);
        mTextViewTestInfoGreenAdd = (TextView) findViewById(R.id.textViewTestInfoGreenAdd);
        mProcessorView = (TextureView) findViewById(R.id.procView);
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, APP_PERMISSIONS, 1);
        }

        mBtnVersion = (Button) findViewById(R.id.buttonGetHwdVersion);
        mBtnVersion.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mExternalBluetoothDevice != null)
                    mExternalBluetoothDevice.sndGetVersion(
                            new ExternalBluetoothDevice.OnResponseListener() {
                                @Override
                                public void onResponse(final ExternalBluetoothDevice.RespState state) {
                                    Toast.makeText(MainActivity.this, state.toString(), Toast.LENGTH_LONG).show();
                                }
                            }
                    );
            }
        });

        //---------------------------------------------------------
        mBtnSave = (Button) findViewById(R.id.buttonSave);
        mBtnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveResult();
            }
        });
        //---------------------------------------------------------
        mBtnStart = (Button) findViewById(R.id.buttonStart);
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMode = MODE_SCAN;
                configureUI();
                setPreViewTiny();
                mProcessorView.setVisibility(View.VISIBLE);
                scanStepCounter = 0;
                mImageProcessor.setupInternalValues();
                Log.v(LOG_TAG, "Start scanner mode");
                if (ConfigDDD.isBluetoothDeviceMode) startScanForExternalBluetooth(true);
                else startScanForIRCtrlMode(true);
            }
        });
        mBtnStartTestMode = (Button) findViewById(R.id.buttonStartTestMode);
        mBtnStartTestMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMode = MODE_TEST;
                configureUI();
                setPreViewTiny();
                Log.v(LOG_TAG, "Start test mode");
                testModeRed = testModeGreen = TEST_MODE_NONE;
                scanStepCounter = 0;
                mImageProcessor.setupInternalValues();
                mAppHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ConfigDDD.isBluetoothDeviceMode)
                            startTestForExternalBluetoothDevice(ConfigDDD.notLaserStepDivider != 0);
                        else startTestForIRCtrlMode(ConfigDDD.notLaserStepDivider != 0);
                    }
                });
            }
        });

        //---------------------------------------------------------
        mBtnFinish = (Button) findViewById(R.id.buttonFinish);
        mBtnFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMode == MODE_TEST) {
                    mMode = MODE_PREVIEW;
                    try {
                        ConfigDDD.saveConfig(getSharedPreferences(ConfigDDD.configName, 0));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "save config.", e);
                        FatalErrorDialog.showError("Save config error");
                    }
                } else {
                    mMode = MODE_SAVE;
                }
                Log.v(LOG_TAG, "Stop scanning");
            }
        });
        //---------------------------------------------------------
        mBtnCancel = (Button) findViewById(R.id.buttonCancel);
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPreview();
            }
        });
        mBtnConfigure = (Button) findViewById(R.id.buttonConfigure);
        mBtnConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ConfigDDDActivity.class);
                startActivity(intent);
            }
        });

        mBtnReCalculate = (Button) findViewById(R.id.buttonReCalculate);
        mBtnReCalculate.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LoadRawFile.class);
                startActivityForResult(intent, 0);
            }
        });

        mBtnLaserOn = (Button) findViewById(R.id.buttonLaserOn);
        mBtnLaserOn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ConfigDDD.isBluetoothDeviceMode) mExternalBluetoothDevice.sndLaserCtrl(
                        (byte) (ExternalBluetoothDevice.LASER_MASK_GREEN | ExternalBluetoothDevice.LASER_MASK_RED),
                        null);
                else mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_LASERS_ON);
            }
        });

        mBtnLaserOff = (Button) findViewById(R.id.buttonLaserOff);
        mBtnLaserOff.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                lasersOff();
            }
        });
        mBtnLaserGotoStart = (Button) findViewById(R.id.buttonGotoStart);
        mBtnLaserGotoStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ConfigDDD.isBluetoothDeviceMode)
                    mExternalBluetoothDevice.sndInitLaserPosition(ConfigDDD.stepMotorScanFromStepRed, ConfigDDD.stepMotorScanFromStepGreen, null);
                else mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_GOTO_START);
                Toast.makeText(MainActivity.this, "Start angle setup", Toast.LENGTH_SHORT).show();
            }
        });

        mBtnLaserGotoTestPosition = (Button) findViewById(R.id.buttonGotoTestPosition);
        mBtnLaserGotoTestPosition.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (ConfigDDD.isBluetoothDeviceMode)
                    mExternalBluetoothDevice.sndInitLaserPosition(
                            ConfigDDD.stepMotorScanFromStepRed + ConfigDDD.stepMotorRedTestSteps,
                            ConfigDDD.stepMotorScanFromStepGreen + ConfigDDD.stepMotorGreenTestSteps, null);
                else mIrCommands.sendIRCmd(0, IrCommands.CMD_IR_GOTO_TEST_POSITION);
                Toast.makeText(MainActivity.this, "Test angle setup", Toast.LENGTH_SHORT).show();
            }
        });

        mBtnLaserMotorPowerOff = (Button) findViewById(R.id.buttonMotorPowerOff);
        mBtnLaserMotorPowerOff.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                motorsOff();
            }
        });
        //---------------------------------------------------------
        mButtonCancelTestMode = (Button) findViewById(R.id.buttonCancelTestMode);
        mButtonCancelTestMode.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mMode = MODE_PREVIEW;
            }
        });

        mButtonRedTestAngleMinus = (Button) findViewById(R.id.buttonRedTestAngleMinus);
        mButtonRedTestAngleMinus.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ConfigDDD.stepMotorStartAngleRed -= TEST_DD_ANGLE*10;
                mImageProcessor.setupInternalValues();
                return true;
            }
        });
        mButtonRedTestAngleMinus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ConfigDDD.stepMotorStartAngleRed -= TEST_DD_ANGLE;
                mImageProcessor.setupInternalValues();
            }
        });
        mButtonRedTestAnglePlus = (Button) findViewById(R.id.buttonRedTestAnglePlus);
        mButtonRedTestAnglePlus.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ConfigDDD.stepMotorStartAngleRed += TEST_DD_ANGLE*10;
                mImageProcessor.setupInternalValues();
                return true;
            }
        });
        mButtonRedTestAnglePlus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ConfigDDD.stepMotorStartAngleRed += TEST_DD_ANGLE;
                mImageProcessor.setupInternalValues();
            }
        });
        mButtonRedTestGotoLeft = (Button) findViewById(R.id.buttonRedTestGotoLeft);
        mButtonRedTestGotoLeft.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeRed != TEST_MODE_NONE) {
                    testModeRed = TEST_MODE_NONE;
                } else {
                    testModeRed = TEST_MODE_GOTO_LEFT;
                    testModeStepCntRed = -TEST_DD_STEPS;
                }
            }
        });
        mButtonRedTestGotoRight = (Button) findViewById(R.id.buttonRedTestGotoRight);
        mButtonRedTestGotoRight.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeRed != TEST_MODE_NONE) {
                    testModeRed = TEST_MODE_NONE;
                } else {
                    testModeRed = TEST_MODE_GOTO_RIGHT;
                    testModeStepCntRed = TEST_DD_STEPS;
                }
            }
        });
        mButtonRedTestGotoCenter = (Button) findViewById(R.id.buttonRedTestGotoCenter);
        mButtonRedTestGotoCenter.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeRed != TEST_MODE_NONE) {
                    testModeRed = TEST_MODE_NONE;
                } else {
                    testModeRed = TEST_MODE_GOTO_CENTER;
                }
            }
        });
        //---------------------------------------------------------
        mButtonGreenTestAngleMinus = (Button) findViewById(R.id.buttonGreenTestAngleMinus);
        mButtonGreenTestAngleMinus.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ConfigDDD.stepMotorStartAngleGreen -= TEST_DD_ANGLE*10;
                mImageProcessor.setupInternalValues();
                return true;
            }
        });
        mButtonGreenTestAngleMinus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ConfigDDD.stepMotorStartAngleGreen -= TEST_DD_ANGLE;
                mImageProcessor.setupInternalValues();
            }
        });
        mButtonGreenTestAnglePlus = (Button) findViewById(R.id.buttonGreenTestAnglePlus);
        mButtonGreenTestAnglePlus.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ConfigDDD.stepMotorStartAngleGreen += TEST_DD_ANGLE*10;
                mImageProcessor.setupInternalValues();
                return true;
            }
        });
        mButtonGreenTestAnglePlus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ConfigDDD.stepMotorStartAngleGreen += TEST_DD_ANGLE;
                mImageProcessor.setupInternalValues();
            }
        });
        mButtonGreenTestGotoLeft = (Button) findViewById(R.id.buttonGreenTestGotoLeft);
        mButtonGreenTestGotoLeft.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeGreen != TEST_MODE_NONE) {
                    testModeGreen = TEST_MODE_NONE;
                } else {
                    testModeGreen = TEST_MODE_GOTO_LEFT;
                    testModeStepCntGreen = -TEST_DD_STEPS;
                }
            }
        });
        mButtonGreenTestGotoRight = (Button) findViewById(R.id.buttonGreenTestGotoRight);
        mButtonGreenTestGotoRight.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeGreen != TEST_MODE_NONE) {
                    testModeGreen = TEST_MODE_NONE;
                } else {
                    testModeGreen = TEST_MODE_GOTO_RIGHT;
                    testModeStepCntGreen = TEST_DD_STEPS;
                }
            }
        });
        mButtonGreenTestGotoCenter = (Button) findViewById(R.id.buttonGreenTestGotoCenter);
        mButtonGreenTestGotoCenter.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testModeGreen != TEST_MODE_NONE) {
                    testModeGreen = TEST_MODE_NONE;
                } else {
                    testModeGreen = TEST_MODE_GOTO_CENTER;
                }
            }
        });
        //-------------------------------------------------------
        startPreview();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 1.0f;
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null || resultCode != RESULT_OK) {
            return;
        }
        rawFileNameForRecalculate = data.getStringExtra("fileName");
        Log.v(LOG_TAG, "selected file:" + rawFileNameForRecalculate);
        mAppHandler.postDelayed(new Runnable() {
            public void run() {
                if (mCameraFunctions == null || !mCameraFunctions.getImage(mPreView)) return;
                mAppHandler.post(new Runnable() {
                    public void run() {
                        recalculateRawData();
                    }
                });
            }
        }, 500);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.v(LOG_TAG, "-------- onStart()");
        mRS = RenderScript.create(this);
        mImageProcessor = new ImageProcessor(mRS);
        mCameraFunctions = new CameraFunctions(mRS);
        openBlueTooth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(LOG_TAG, "-------- onResume()");
    }

    @Override
    protected void onPause() {
        Log.v(LOG_TAG, "-------- onPause()");
        if (ConfigDDD.isBluetoothDeviceMode && mExternalBluetoothDevice != null) {
            try {
                mExternalBluetoothDevice.closeBlueTooth();
                mExternalBluetoothDevice = null;
            } catch (Exception ex) {
                Log.e(LOG_TAG, "mExternalBluetoothDevice.closeBlueTooth()", ex);
            }
        }
        mMode = MODE_PREVIEW;
        if (mCameraFunctions != null) mCameraFunctions.closeCamera();
        mImageProcessor = null;
        mCameraFunctions = null;
        mRS.finish();
        super.onPause();
    }
}
