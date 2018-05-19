package com.home.mm.ddd_scanner;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.home.mm.dddscan.ScriptC_yuv2rgb;

import java.util.Arrays;
import java.util.Collections;

public class CameraFunctions {
    public static final int AF_REGIONS = 100;
    private final static String LOG_TAG = "CameraFunc";
    private CameraManager mCameraManager = null;
    private String mCameraId = null;
    private CameraDevice mCameraDevice = null;
    private Size mSize = null;
    private CameraCaptureSession mCameraSession = null;
    private Handler mCameraHandler, mCaptureHandler;
    private final ConditionVariable mCloseCameraWaiter = new ConditionVariable();
    private final ConditionVariable mGetCaptureBitmapWaiter = new ConditionVariable();
    private boolean mGetCaptureLocket = true;
    private Surface mPreviewSurface;
    private Surface mAllocationSurface;
    private Bitmap mBitmap = null;
    private Allocation mRGB888Allocation, mInputAllocation;
    private ScriptC_yuv2rgb mScript;
    private RenderScript mRS;

    public CameraFunctions(RenderScript rs) {
        mRS = rs;
        mScript = new ScriptC_yuv2rgb(mRS);
    }

    public Size getSize() {
        return mSize;
    }

    public Allocation getRGB8888Allocation() {
        return mRGB888Allocation;
    }

    public Bitmap getPreviewBitmap() {
        if(ConfigDDD.modeGetRgbAllocation != ConfigDDD.MODE_VIA_TEXTUREVIEW_GETBITMAP) {
            mRGB888Allocation.copyTo(mBitmap);
        }
        return mBitmap;
    }

    public void closeCamera() {
        mCloseCameraWaiter.close();
        if (mCameraDevice != null) {
            Log.v(LOG_TAG, "onPause() mCameraDevice.close()");
            mCameraDevice.close();
        }

        if (!mCloseCameraWaiter.block(4000)) {
            Log.e(LOG_TAG, "Closing camera timeout");
        }
        Log.v(LOG_TAG, "onPause() mCloseCameraWaiter.block() finish");
    }

    public boolean selectCamera(CameraManager cameraManager) {
        mCameraManager = cameraManager;
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics mCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Log.v(LOG_TAG, "Camera ID:'" + cameraId +
                        "' LENS_FACING:" + mCharacteristics.get(CameraCharacteristics.LENS_FACING) +
                        " INFO_SUPPORTED_HARDWARE_LEVEL:" + mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
                if (mCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    Size[] sizes = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
                    mSize = sizes[ConfigDDD.cameraResolutionIndex];
                    StringBuilder sb = new StringBuilder("Camera ID:'" + cameraId + "' sizes:");
                    for (Size s : sizes) {
                        sb.append(s.toString());
                        sb.append(" ");
                    }
                    Log.v(LOG_TAG, sb.toString());
                    Log.v(LOG_TAG, "selected size:"+mSize.toString());
                    mCameraId = cameraId;
//                mBitmap = Bitmap.createBitmap(mSize.getWidth(), mSize.getHeight(), Bitmap.Config.ARGB_8888);
                    mBitmap = Bitmap.createBitmap(mSize.getHeight(), mSize.getWidth(), Bitmap.Config.ARGB_8888);
                    if (ConfigDDD.modeGetRgbAllocation != ConfigDDD.MODE_VIA_TEXTUREVIEW_GETBITMAP) {
                        if (ConfigDDD.modeGetRgbAllocation == ConfigDDD.MODE_VIA_ALLOCATION_SURFACE_NORMAL) {
                            Type.Builder yuvTypeBuilder = new Type.Builder(mRS, Element.YUV(mRS));
                            yuvTypeBuilder.setX(mSize.getWidth());
                            yuvTypeBuilder.setY(mSize.getHeight());
                            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
                            Log.v(LOG_TAG, "Allocation.createTyped() for MODE_VIA_ALLOCATION_SURFACE NORMAL");
                            mInputAllocation = Allocation.createTyped(mRS, yuvTypeBuilder.create(),
                                    Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
                        } else {
                            Type.Builder yuvTypeBuilder = new Type.Builder(mRS, Element.U8_3(mRS));
                            yuvTypeBuilder.setX(mSize.getWidth());
                            yuvTypeBuilder.setY(mSize.getHeight());
                            //yuvTypeBuilder.setYuvFormat(ImageFormat.);
                            Log.v(LOG_TAG, "Allocation.createTyped() for MODE_VIA_ALLOCATION_SURFACE XXX");
                            mInputAllocation = Allocation.createTyped(mRS, yuvTypeBuilder.create(),
                                    Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
                        }
                        Type.Builder rgbTypeBuilder = new Type.Builder(mRS, Element.RGBA_8888(mRS));
                        rgbTypeBuilder.setX(mSize.getHeight());
                        rgbTypeBuilder.setY(mSize.getWidth());
                        mRGB888Allocation = Allocation.createTyped(mRS, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT);
                        new ProcessingInputAllocation();
                    }
                    return true;
                }
            }
            FatalErrorDialog.showError("There is no LENS_FACING_BACK camera with SUPPORTED_HARDWARE_LEVEL");
        } catch (Exception e) {
            Log.e(LOG_TAG, "selectCamera:", e);
            FatalErrorDialog.showError("selectCamera:"+e.toString());
        }
        return false;
    }

    public void openCamera(Surface previewSurface) {
        this.mPreviewSurface = previewSurface;
        HandlerThread thread = new HandlerThread("CameraWaitingCaptureThread");
        thread.start();
        mCaptureHandler = new Handler(thread.getLooper());
        HandlerThread cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        mCameraHandler = new Handler(cameraThread.getLooper());
        mCameraHandler.post(new Runnable() {
            public void run() {
                try {
                    mCameraManager.openCamera(mCameraId,
                            new CameraDevice.StateCallback() {

                                @Override
                                public void onOpened(@NonNull CameraDevice camera) {
                                    mCameraDevice = camera;
                                    Log.v(LOG_TAG, "Camera onOpen()");
                                    try {
                                        if (ConfigDDD.modeGetRgbAllocation != ConfigDDD.MODE_VIA_TEXTUREVIEW_GETBITMAP) {
                                            Log.v(LOG_TAG, "Camera createCaptureSession() MODE_VIA_ALLOCATION_SURFACE");
                                            mAllocationSurface = mInputAllocation.getSurface();
                                            mCameraDevice.createCaptureSession(
                                                    Arrays.asList(mPreviewSurface, mAllocationSurface),
                                                    mCameraSessionListener, mCameraHandler);
                                        } else {
                                            Log.v(LOG_TAG, "Camera createCaptureSession() MODE_VIA_TEXTUREVIEW_GETBITMAP");
                                            mCameraDevice.createCaptureSession(
                                                    Collections.singletonList(mPreviewSurface),
                                                    mCameraSessionListener, mCameraHandler);
                                        }
                                    } catch (CameraAccessException e) {
                                        FatalErrorDialog.showError(e.toString());
                                    }
                                }

                                @Override
                                public void onClosed(@NonNull CameraDevice camera) {
                                    Log.v(LOG_TAG, "Camera onClosed()");
                                    mCloseCameraWaiter.open();
                                }

                                @Override
                                public void onDisconnected(@NonNull CameraDevice camera) {
                                    Log.v(LOG_TAG, "Camera onDisconnected()");
                                    camera.close();
                                    mCameraDevice = null;
                                    FatalErrorDialog.showError("The camera device has been disconnected.");
                                }

                                @Override
                                public void onError(@NonNull CameraDevice camera, int error) {
                                    camera.close();
                                    mCameraDevice = null;
                                    FatalErrorDialog.showError("The camera encountered an error:" + error);
                                }

                            },
                            mCameraHandler);
                } catch (CameraAccessException e) {
                    Log.e(LOG_TAG, "mCameraManager.openCamera", e);
                    FatalErrorDialog.showError("mCameraManager.openCamera" + e.toString());
                } catch (SecurityException e) {
                    Log.e(LOG_TAG, "mCameraManager.openCamera", e);
                    FatalErrorDialog.showError("mCameraManager.openCamera:"+e.toString());
                }
            }
        });
    }

    public boolean getImage(final TextureView textureView) {
     //   Log.v(LOG_TAG, "getImage() thread:'"+Thread.currentThread().toString()+"' start:", new Exception());
        Log.v(LOG_TAG, "getImage() thread:'"+Thread.currentThread().toString()+"' start");
        if (ConfigDDD.modeCapture == ConfigDDD.MODE_CAPTURE_VIA_CAPTURE) {
            try {
                mGetCaptureBitmapWaiter.close();
                Log.v(LOG_TAG, "CameraSession.capture()");
                mCameraSession.capture(getCaptureRequestBuilder(true).build(),
                        new CameraCaptureSession.CaptureCallback() {

                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                            @NonNull CaptureResult partialResult) {
                                Log.v(LOG_TAG, "onCaptureCompleted() mCameraSession.capture CONTROL_AF_STATE:" + partialResult.get(CaptureResult.CONTROL_AF_STATE));
                                //CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
                            }

                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                           @NonNull TotalCaptureResult result) {
                                Log.v(LOG_TAG, "onCaptureCompleted() mCameraSession.capture CONTROL_AF_STATE:" + result.get(CaptureResult.CONTROL_AF_STATE));
                                mGetCaptureBitmapWaiter.open();
                            }

                            @Override
                            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                        @NonNull CaptureFailure failure) {
                                Log.v(LOG_TAG, "onCaptureFailed() mCameraSession.capture ");
                                FatalErrorDialog.showError("onCaptureFailed()");
                            }
                        }
                        , mCameraHandler);
            } catch (CameraAccessException e) {
                Log.e(LOG_TAG, "CameraSession.capture", e);
                FatalErrorDialog.showError("CameraSession.capture():"+e.toString());
                return false;
            }
        }
        if (ConfigDDD.modeGetRgbAllocation != ConfigDDD.MODE_VIA_TEXTUREVIEW_GETBITMAP) {
            Log.v(LOG_TAG, "getImage() wait Allocation START");
            mGetCaptureBitmapWaiter.close();
            mGetCaptureLocket = false;
            if (!mGetCaptureBitmapWaiter.block(500)) {
                Log.e(LOG_TAG, "waiting image in allocation timeout");
            }
            Log.v(LOG_TAG, "getImage() wait Allocation END");
            return true;
        } else {
            if (ConfigDDD.modeCapture == ConfigDDD.MODE_CAPTURE_VIA_CAPTURE) {
                if (!mGetCaptureBitmapWaiter.block(1000)) {
                    Log.e(LOG_TAG, "waiting bitmap timeout");
                }
            }
            Log.v(LOG_TAG, "getImage() getBitmap()");
            textureView.getBitmap(mBitmap);
            //Log.v(LOG_TAG, "getImage() image ready");
            if (mRGB888Allocation == null) {
                mRGB888Allocation = Allocation.createFromBitmap(mRS, mBitmap,
                        Allocation.MipmapControl.MIPMAP_NONE,
                        Allocation.USAGE_SCRIPT);
            } else {
                mRGB888Allocation.copyFrom(mBitmap);
            }
            Log.v(LOG_TAG, "getImage() Allocation ready");
            return true;
        }
    }

    //----------------------------------------------------------------------------------------------------

    private CaptureRequest.Builder getCaptureRequestBuilder(boolean makeCapture) throws CameraAccessException {
        CaptureRequest.Builder previewBuilder = mCameraDevice.createCaptureRequest(
                makeCapture ? CameraDevice.TEMPLATE_STILL_CAPTURE : CameraDevice.TEMPLATE_PREVIEW);
        previewBuilder.addTarget(mPreviewSurface);
        if (ConfigDDD.modeGetRgbAllocation != ConfigDDD.MODE_VIA_TEXTUREVIEW_GETBITMAP) {
            previewBuilder.addTarget(mAllocationSurface);
        }

        previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        //previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        previewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        Rect rect = new Rect(mSize.getWidth() / 2 - AF_REGIONS, mSize.getHeight() / 2 - AF_REGIONS, mSize.getWidth() / 2 + 100, mSize.getHeight() / 2 + 100);
//        Log.v(LOG_TAG, "CONTROL_AF_REGIONS:" + rect);
        MeteringRectangle meteringRectangle = new MeteringRectangle(rect, 500);
        previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meteringRectangle});
//        previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, null);
        previewBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, null);
        previewBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, null);

        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        return previewBuilder;
    }


    private CameraCaptureSession.StateCallback mCameraSessionListener =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.v(LOG_TAG, "CameraCaptureSession.StateCallback onConfigured()");
                    mCameraSession = session;
                    if (null == mCameraDevice) {
                        Log.v(LOG_TAG, "CameraCaptureSession.StateCallback onConfigured() mCameraDevice == null");
                        return;
                    }
                    try {
                        Log.v(LOG_TAG, "setRepeatingRequest");
                        mCameraSession.setRepeatingRequest(getCaptureRequestBuilder(false).build(),
                                new CameraCaptureSession.CaptureCallback() {

                                    @Override
                                    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                                    @NonNull CaptureResult partialResult) {
                                        //            Log.v(LOG_TAG, "onCaptureCompleted() CONTROL_AF_STATE:" + partialResult.get(CaptureResult.CONTROL_AF_STATE));
                                        //CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
                                    }

                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                                   @NonNull TotalCaptureResult result) {
                                        //            Log.v(LOG_TAG, "onCaptureCompleted() CONTROL_AF_STATE:" + result.get(CaptureResult.CONTROL_AF_STATE));
                                    }

                                    @Override
                                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                                                @NonNull CaptureFailure failure) {
                                        Log.v(LOG_TAG, "onCaptureFailed()");
                                        FatalErrorDialog.showError("onCaptureFailed()");
                                    }
                                }
                                , mCameraHandler);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "setRepeatingRequest", e);
                        FatalErrorDialog.showError("setRepeatingRequest:"+e.toString());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    FatalErrorDialog.showError("Unable to configure the capture session");
                }
            };

    //---------------------------
    class ProcessingInputAllocation implements Runnable, Allocation.OnBufferAvailableListener {
        private int mPendingFrames = 0;

        public ProcessingInputAllocation() {
            mInputAllocation.setOnBufferAvailableListener(this);
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            synchronized (this) {
                mPendingFrames++;
                mCaptureHandler.post(this);
            }
        }

        @Override
        public void run() {
            int pendingFrames;
            synchronized (this) {
                pendingFrames = mPendingFrames;
                mPendingFrames = 0;
                mCaptureHandler.removeCallbacks(this);
            }
            for (int i = 0; i < pendingFrames; i++) {
                mInputAllocation.ioReceive();
            }
            if (mGetCaptureLocket) {
                return;
            }
            mGetCaptureLocket = true;

            Log.v(LOG_TAG, "toRGB START");
            if (ConfigDDD.modeGetRgbAllocation == ConfigDDD.MODE_VIA_ALLOCATION_SURFACE_NORMAL) {
                Log.v(LOG_TAG, "ImageProcessor.toRGB set_gInputFrame");
                mScript.set_gInputAllocaton(mInputAllocation);
                Log.v(LOG_TAG, "ImageProcessor.toRGB");
                mScript.forEach_yuv2rgb(mRGB888Allocation, mRGB888Allocation);
            } else {
                //Log.i(LOG_TAG, "mInputAllocation.getElement().getType().getYuv():" + mInputAllocation.getType().getYuv());
                mScript.set_gInputAllocaton(mInputAllocation);
                //Log.v(LOG_TAG, "ImageProcessor.toRGBTest sz" + inputAllocation.getElement().getBytesSize());
                //Log.v(LOG_TAG, "ImageProcessor.toRGBTest vs" + inputAllocation.getElement().getVectorSize());
                //Log.v(LOG_TAG, "ImageProcessor.toRGBTest dt" + inputAllocation.getElement().getDataType().toString());
                //Log.v(LOG_TAG, "ImageProcessor.toRGBTest dt" + inputAllocation.getElement().getDataKind().toString());
                //Log.v(LOG_TAG, "toRGB_xxx START");
                mScript.forEach_yuv2rgb_XXX(mRGB888Allocation, mRGB888Allocation);
            }
            mRS.finish();
            Log.v(LOG_TAG, "toRGB FINISH");
            mGetCaptureBitmapWaiter.open();
        }
    }
}
