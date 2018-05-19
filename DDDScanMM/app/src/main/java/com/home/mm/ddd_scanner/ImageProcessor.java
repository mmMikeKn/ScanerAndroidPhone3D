package com.home.mm.ddd_scanner;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.home.mm.dddscan.ScriptC_laserLine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

class ImageProcessor {
    final static String RAW_FILE_EXT = ".rawmm";
    final static String IMG_FILE_EXT = "_screenshot.png";
    final static String COLORMAP_PNG_EXT = "_colormap.png";
    final static String COLORMAP_TIFF_EXT = "_colormap.tif";
    private final static String RAW_FILE_HD_CODE = "MMRW";

    private final static String LOG_TAG = "ImageProcessor";

    private ScriptC_laserLine mScript;

    private Allocation mOutputAllocation;
    private Allocation mRedLaserPointsAllocation[];
    private Allocation mGreenLaserPointsAllocation[];
    private Allocation mRedLaserPointsCloudAllocation[];
    private Allocation mGreenLaserPointsCloudAllocation[];
    private Allocation mNoLaserAllocation, mLaserAllocation;
    private byte imagePixels[];
    private RenderScript mRS;
    private double tangentLaserListRed[], tangentLaserListGreen[];
    private int histogramProbeLine;
    private Size mSize;
    private float tmpPointsCloud[];

    ImageProcessor(RenderScript rs) {
        mRS = rs;
        mScript = new ScriptC_laserLine(rs);
        HandlerThread thread = new HandlerThread("ImageProcessor");
        thread.start();
        Log.v(LOG_TAG, "ImageProcessor mHandler init");
    }

    void initAllocations(Allocation inputAllocation, Size size, SurfaceTexture surfaceTexture) {
        mSize = size;
        if (mOutputAllocation == null) {
            Log.v(LOG_TAG, "initAllocations start");
            mRedLaserPointsCloudAllocation = new Allocation[ConfigDDD.fullScanStepsNumber];
            mGreenLaserPointsCloudAllocation = new Allocation[ConfigDDD.fullScanStepsNumber];
            mRedLaserPointsAllocation = new Allocation[ConfigDDD.fullScanStepsNumber];
            mGreenLaserPointsAllocation = new Allocation[ConfigDDD.fullScanStepsNumber];
            for (int i = 0; i < ConfigDDD.fullScanStepsNumber; i++) {
                mRedLaserPointsCloudAllocation[i] = Allocation.createSized(mRS, Element.F32_3(mRS), size.getWidth());
                mGreenLaserPointsCloudAllocation[i] = Allocation.createSized(mRS, Element.F32_3(mRS), size.getWidth());
                mRedLaserPointsAllocation[i] = Allocation.createSized(mRS, Element.I16(mRS), size.getWidth());
                mGreenLaserPointsAllocation[i] = Allocation.createSized(mRS, Element.I16(mRS), size.getWidth());
            }
            tmpPointsCloud = new float[mRedLaserPointsCloudAllocation[0].getType().getCount() * 4];
            mOutputAllocation = Allocation.createTyped(mRS, inputAllocation.getType(), Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);
            mNoLaserAllocation = Allocation.createTyped(mRS, inputAllocation.getType(), Allocation.USAGE_SCRIPT);
            mLaserAllocation = Allocation.createTyped(mRS, inputAllocation.getType(), Allocation.USAGE_SCRIPT);
            surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            mOutputAllocation.setSurface(new Surface(surfaceTexture));
            Log.v(LOG_TAG, "initAllocations end");
        }
    }

    //==============================================================================================

    private final static String plyHeaderColorless = "ply\n" +
            "format %s 1.0\n" +
            "comment %s\n" +
            "element vertex %d\n" +
            "property float x\n" +
            "property float y\n" +
            "property float z\n" +
            "element face 0\n" +
            "property list uchar int vertex_index\n" +
            "end_header\n";

    private final static String plyHeaderColor = "ply\n" +
            "format %s 1.0\n" +
            "comment %s\n" +
            "element vertex %d\n" +
            "property float x\n" +
            "property float y\n" +
            "property float z\n" +
            "property uchar red\n" +
            "property uchar green\n" +
            "property uchar blue\n" +
            "element face 0\n" +
            "property list uchar int vertex_index\n" +
            "end_header\n";

    private void savePLY(int scanSteps, String fileName, String name,
                         Allocation laserPointsCloud[], Allocation laserPointsAllocation[],
                         OnLongWorkListener listener) throws IOException {
        fileName += '_' + name + ".ply";
        Log.v(LOG_TAG, String.format("calc points cloud %s size START for %d", name, scanSteps));
        int pointsCloudSz = 0;
        for (int i = 0; i < scanSteps; i++) {
            laserPointsCloud[i].copyTo(tmpPointsCloud);
            for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                if (tmpPointsCloud[j + 2] > 0) pointsCloudSz++;
                //if (i == 0 && j < 8)                    Log.v(LOG_TAG, "Data: " + tmpPointsCloud[j] + " " + tmpPointsCloud[j + 1] + " " + tmpPointsCloud[j + 2] + " " + tmpPointsCloud[j + 3] + " ");
            }
        }
        short tmpLaserPoints[] = null;
        //byte pixel[] = new byte[4];
        if (ConfigDDD.isColorPLY) {
            tmpLaserPoints = new short[laserPointsAllocation[0].getType().getCount()];
        }
        Log.v(LOG_TAG, String.format("Save points cloud %s. START %s", name, fileName));
/* too slow
        DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), 1024*8));
        os.write(String.format(plyHeader, name, pointsCloudSz).getBytes());
        for (int i = 0; i < lastScanStepCounter; i++) {
            if((i%10) == 0) {
                listener.onChangeState(String.format("Save PLY %s %d/%d", name, i, lastScanStepCounter));
            }
            a[i].copyTo(tmpPointsCloud);
            for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                if (tmpPointsCloud[j + 2] > 0) {
                    os.write(Float.toString(tmpPointsCloud[j]).getBytes());
                    os.write(' ');
                    os.write(Float.toString(tmpPointsCloud[j+1]).getBytes());
                    os.write(' ');
                    os.write(Float.toString(tmpPointsCloud[j+2]).getBytes());
                    os.write('\n');
                }
            }
        }
        os.close();
*/
        if (ConfigDDD.isBinaryPLY) {
            DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), 1024 * 8));
            os.write(String.format(ConfigDDD.isColorPLY ? plyHeaderColor : plyHeaderColorless, "binary_big_endian", name, pointsCloudSz).getBytes());
            for (int i = 0; i < scanSteps; i++) {
                if ((i % 10) == 0) {
                    listener.onChangeState(String.format("Save PLY(b) %s %d/%d", name, i, scanSteps));
                }
                laserPointsCloud[i].copyTo(tmpPointsCloud);
                if (ConfigDDD.isColorPLY) laserPointsAllocation[i].copyTo(tmpLaserPoints);
                for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                    if (tmpPointsCloud[j + 2] > 0) {
                        os.writeFloat(tmpPointsCloud[j]);
                        os.writeFloat(tmpPointsCloud[j + 1]);
                        os.writeFloat(tmpPointsCloud[j + 2]);
                        if (ConfigDDD.isColorPLY) {
                            int row = j / 4;
                            int col = tmpLaserPoints[row];
                            int ofs = (col + row * mNoLaserAllocation.getType().getX()) * 4;
                            os.writeByte(imagePixels[ofs]);
                            os.writeByte(imagePixels[ofs + 1]);
                            os.writeByte(imagePixels[ofs + 2]);
                        }
                    }
                }
            }
            os.close();
        } else {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
            pw.printf(ConfigDDD.isColorPLY ? plyHeaderColor : plyHeaderColorless, "ascii", name, pointsCloudSz);
            for (int i = 0; i < scanSteps; i++) {
                if ((i % 10) == 0) {
                    listener.onChangeState(String.format("Save PLY(a) %s %d/%d", name, i, scanSteps));
                }
                laserPointsCloud[i].copyTo(tmpPointsCloud);
                if (ConfigDDD.isColorPLY) laserPointsAllocation[i].copyTo(tmpLaserPoints);
                for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                    if (tmpPointsCloud[j + 2] > 0) {
                        pw.println(tmpPointsCloud[j] + " " + tmpPointsCloud[j + 1] + " " + tmpPointsCloud[j + 2]);
                        if (ConfigDDD.isColorPLY) {
                            int row = j / 4;
                            int col = tmpLaserPoints[row];
                            int ofs = (col + row * mNoLaserAllocation.getType().getX()) * 4;
                            pw.println(tmpPointsCloud[j] + " " + tmpPointsCloud[j + 1] + " " + tmpPointsCloud[j + 2]
                                    + " " + ((int) imagePixels[ofs] & 0x0ff)
                                    + " " + ((int) imagePixels[ofs + 1] & 0x0ff)
                                    + " " + ((int) imagePixels[ofs + 2] & 0x0ff));
                        } else {
                            pw.println(tmpPointsCloud[j] + " " + tmpPointsCloud[j + 1] + " " + tmpPointsCloud[j + 2]);
                        }
                    }
                }
            }
            pw.close();
        }
        Log.v(LOG_TAG, "Save points cloud END");
    }

    interface OnLongWorkListener {
        void onChangeState(String msg);

        void onError(String msg);

        void onFinish();
    }

    void saveDataFiles(final int scanSteps, final boolean isRecalculate, final String fullFileMask, final OnLongWorkListener listener) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                String fileName = "--";
                try {
                    if (ConfigDDD.isColorPLY) {
                        if (!isRecalculate) {
                            listener.onChangeState("Save screenshot file");
                            final Bitmap bitmap = Bitmap.createBitmap(mNoLaserAllocation.getType().getX(), mNoLaserAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
                            mNoLaserAllocation.copyTo(bitmap);
                            OutputStream os = new FileOutputStream(fullFileMask + IMG_FILE_EXT);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.close();
                        }
                        imagePixels = new byte[mNoLaserAllocation.getBytesSize()];
                        mNoLaserAllocation.copyTo(imagePixels);
                        //DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fullFileMask + "_kill.me.test"), 1024 * 8));
                        //os.write(imagePixels);
                        //os.close();
                    }
                    savePLY(scanSteps, fullFileMask, "RED", mRedLaserPointsCloudAllocation, mRedLaserPointsAllocation, listener);
                    savePLY(scanSteps, fullFileMask, "GREEN", mGreenLaserPointsCloudAllocation, mGreenLaserPointsAllocation, listener);
                    if (!isRecalculate) {
                        fileName = fullFileMask + RAW_FILE_EXT;
                        Log.v(LOG_TAG, "save raw data START: " + fileName);
                        DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
                        os.write(RAW_FILE_HD_CODE.getBytes());
                        os.writeShort((short) scanSteps);
                        os.writeShort((short) mGreenLaserPointsAllocation[0].getType().getCount());
                        short tmp[] = new short[mRedLaserPointsAllocation[0].getType().getCount()];
                        for (int i = 0; i < scanSteps; i++) {
                            listener.onChangeState("Save RAW data " + i + "/" + scanSteps);
                            mGreenLaserPointsAllocation[i].copyTo(tmp);
                            for (short v : tmp) {
                                os.writeShort(v);
                            }
                            mRedLaserPointsAllocation[i].copyTo(tmp);
                            for (short v : tmp) {
                                os.writeShort(v);
                            }
                        }
                        os.close();
                        Log.v(LOG_TAG, "save raw data END");
                    }
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Save file error", ex);
                    listener.onError("Error save file:" + fileName + "\n" + ex.toString());
                }
                listener.onFinish();
            }
        }).start();
    }

    void loadOldRawData(final String fname, final OnLongWorkListener listener) {
        new Thread(new Runnable() {
            public void run() {
                String fileName = fname;
                try {
                    if (ConfigDDD.isColorPLY) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        String tmp = fileName;
                        fileName = fileName.substring(0, fileName.length() - RAW_FILE_EXT.length()) + IMG_FILE_EXT;
                        if (!new File(fileName).exists()) {
                            throw new IllegalStateException("Can't recalculate in Color PLY mode");
                        }
                        listener.onChangeState("Loading screen shot for Color PLY. " + fileName);
                        Bitmap bitmap = BitmapFactory.decodeFile(fileName, options);
                        mNoLaserAllocation.copyFrom(bitmap);
                        fileName = tmp;
                    }

                    listener.onChangeState("Loading raw data. " + fileName);
                    DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName)));
                    byte[] hd = new byte[RAW_FILE_HD_CODE.length()];
                    if (is.read(hd) != hd.length) {
                        throw new Exception("Wrong file format (hd size)");
                    }
                    if (!new String(hd).startsWith(RAW_FILE_HD_CODE)) {
                        throw new Exception("Wrong file format (hd code)");
                    }
                    byte hdSize[] = new byte[2 + 2];
                    if (is.read(hdSize) != hdSize.length) {
                        throw new Exception("Wrong file format (hd2 size)");
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(hdSize);
                    short steps = buffer.getShort();
                    short points = buffer.getShort();
                    String fileInfo = String.format("[%d x %d]", steps, points);
                    if (mGreenLaserPointsAllocation[0].getType().getCount() != points ||
                            mGreenLaserPointsAllocation.length < steps) {
                        throw new Exception(String.format("Wrong file file data size %s != [%d, %d]",
                                fileInfo,
                                mGreenLaserPointsAllocation.length,
                                mGreenLaserPointsAllocation[0].getType().getCount()));
                    }
                    short tmp[] = new short[mGreenLaserPointsAllocation[0].getType().getCount()];
                    for (int i = 0; i < steps; i++) {
                        for (int j = 0; j < tmp.length; j++) {
                            tmp[j] = is.readShort();
                        }
                        mGreenLaserPointsAllocation[i].copyFrom(tmp);
                        for (int j = 0; j < tmp.length; j++) {
                            tmp[j] = is.readShort();
                        }
                        mRedLaserPointsAllocation[i].copyFrom(tmp);
                    }
                    is.close();
                    for (int step = 0; step < steps; step++) {
                        if ((step % 20) == 0) {
                            listener.onChangeState("calc points cloud:" + step);
                        }
                        mScript.invoke_setupInputFrames(mLaserAllocation, ConfigDDD.cameraViewTangent);
                        mScript.set_tangentRedLaserValue(tangentLaserListRed[step]);
                        mScript.set_tangentGreenLaserValue(tangentLaserListGreen[step]);
                        mScript.set_gRedLaserPoints(mRedLaserPointsAllocation[step]);
                        mScript.set_gGreenLaserPoints(mGreenLaserPointsAllocation[step]);
                        mRS.finish();
                        setThreshold2RS();
                        mScript.forEach_getRedLaserPointsCloud(mRedLaserPointsCloudAllocation[step], mRedLaserPointsCloudAllocation[step]);
                        mScript.forEach_getGreenLaserPointsCloud(mGreenLaserPointsCloudAllocation[step], mGreenLaserPointsCloudAllocation[step]);
                    }
                    buildColorMap(steps, listener);
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Load file error", ex);
                    listener.onError("Error load file:" + fileName + "\n" + ex.getMessage());
                }
            }
        }).start();
    }


    void setupInternalValues() {
        tangentLaserListRed = new double[ConfigDDD.fullScanStepsNumber];
        tangentLaserListGreen = new double[ConfigDDD.fullScanStepsNumber];
        double anglePerStep = Math.PI * 2 / ConfigDDD.stepMotorStepsPer2PI;
        double stepMotorInitAngleRed = ConfigDDD.stepMotorStartAngleRed / 180.0 * Math.PI;
        double stepMotorInitAngleGreen = ConfigDDD.stepMotorStartAngleGreen / 180.0 * Math.PI;
        for (int i = 0; i < ConfigDDD.fullScanStepsNumber; i++) {
            double a = stepMotorInitAngleRed + anglePerStep * i;
            tangentLaserListRed[i] = Math.tan(a - Math.PI / 2);
            a = stepMotorInitAngleGreen + anglePerStep * i;
            tangentLaserListGreen[i] = Math.tan(a - Math.PI / 2);
        }
        mScript.set_probeWidth(ConfigDDD.laserDetectProbeWidth);
        mScript.set_thresholdLevel(ConfigDDD.laserDetectThresholdLevel * ConfigDDD.laserDetectProbeWidth);
        mScript.set_laserGreenL(ConfigDDD.laserGreenL);
        mScript.set_laserRedL(ConfigDDD.laserRedL);
    }

    void fixNoLaserAllocation(Allocation inputAllocation) {
        Log.v(LOG_TAG, "ImageProcessor.fixNoLaserAllocation start");
        mNoLaserAllocation.copyFrom(inputAllocation);
        mRS.finish();
        Log.v(LOG_TAG, "ImageProcessor.fixNoLaserAllocation end");
    }

    private void setThreshold2RS(){
        mScript.set_thresholdMinZ(ConfigDDD.scanDistanceThreshold);
        mScript.set_thresholdMaxZ(ConfigDDD.scanDistanceThreshold + ConfigDDD.depthScanThreshold);
        mScript.set_thresholdMinX(-ConfigDDD.widthScanThreshold / 2);
        mScript.set_thresholdMaxX(ConfigDDD.widthScanThreshold / 2);
        mScript.set_thresholdMinY(-ConfigDDD.heightScanThreshold / 2);
        mScript.set_thresholdMaxY(ConfigDDD.heightScanThreshold / 2);
    }

    void detectLaserLine(int stepRed, int stepGreen, Allocation inputAllocation) {
        Log.v(LOG_TAG, "ImageProcessor.fixLaserAllocation start calc step R:" + stepRed + " stepG:" + stepGreen);
        mScript.invoke_setupInputFrames(mNoLaserAllocation, ConfigDDD.cameraViewTangent);
        if (ConfigDDD.notLaserStepDivider != 0) {
            mScript.forEach_subImage(inputAllocation, mLaserAllocation);
        } else {
            mScript.forEach_copyImage(inputAllocation, mLaserAllocation);
        }
        mRS.finish();
        mScript.invoke_setupInputFrames(mLaserAllocation, ConfigDDD.cameraViewTangent);
        Log.v(LOG_TAG, "detect laser Line. START:");
        mScript.forEach_getRedLaserLine(mRedLaserPointsAllocation[stepRed], mRedLaserPointsAllocation[stepRed]);
        mScript.forEach_getGreenLaserLine(mGreenLaserPointsAllocation[stepGreen], mGreenLaserPointsAllocation[stepGreen]);
        mRS.finish();
        Log.v(LOG_TAG, "detect laser Line. END");
        mScript.set_tangentRedLaserValue(tangentLaserListRed[stepRed]);
        mScript.set_tangentGreenLaserValue(tangentLaserListGreen[stepGreen]);
        mScript.set_gRedLaserPoints(mRedLaserPointsAllocation[stepRed]);
        mScript.set_gGreenLaserPoints(mGreenLaserPointsAllocation[stepGreen]);
        setThreshold2RS();

        mScript.forEach_getRedLaserPointsCloud(mRedLaserPointsCloudAllocation[stepRed], mRedLaserPointsCloudAllocation[stepRed]);
        mScript.forEach_getGreenLaserPointsCloud(mGreenLaserPointsCloudAllocation[stepGreen], mGreenLaserPointsCloudAllocation[stepGreen]);
        mRS.finish();
        Log.v(LOG_TAG, "points cloud calc. END");
        mScript.set_histogramProbeLine(histogramProbeLine = mSize.getWidth() / 2);
        mScript.forEach_setOutputImage(mLaserAllocation, mOutputAllocation);
        mOutputAllocation.ioSend();
        mRS.finish();

        // mRedLaserPointsAllocation[lastScanStepCounter].copyTo(tmpLaserPointsData);
        //Log.v(LOG_TAG, "CALC outImage  END R:"+tmpLaserPointsData[mScript.get_histogramProbeLine()]);
        Log.v(LOG_TAG, "prepare outImage. END");
    }

    class PointInfo {
        String msg;
        int point;
    }

    private PointInfo getPointForTest(short px) {
        float x = tmpPointsCloud[histogramProbeLine * 4];
        float y = tmpPointsCloud[histogramProbeLine * 4 + 1];
        float z = tmpPointsCloud[histogramProbeLine * 4 + 2];

        PointInfo res = new PointInfo();
        res.point = px;
        if (z < ConfigDDD.scanDistanceThreshold || z > (ConfigDDD.scanDistanceThreshold + ConfigDDD.depthScanThreshold) ||
                x < -ConfigDDD.widthScanThreshold / 2 || x > ConfigDDD.widthScanThreshold / 2 ||
                y < -ConfigDDD.heightScanThreshold / 2 || y > ConfigDDD.heightScanThreshold / 2) {
            res.msg = String.format("[!] P=%-4d X=%-6.1f\tY=%-6.1f\tZ=%-6.1f", px, x, y, z);
        } else res.msg = String.format("P=%-4d X=%-6.1f\tY=%-6.1f\tZ=%-6.1f", px, x, y, z);
        return res;
    }

    @TargetApi(Build.VERSION_CODES.M)
    PointInfo getRedPointForTest(int step) {
        mRedLaserPointsCloudAllocation[step].copyTo(tmpPointsCloud);
        short tmp[] = new short[1];
        mRedLaserPointsAllocation[step].copy1DRangeTo(histogramProbeLine, 1, tmp);
        return getPointForTest(tmp[0]);
    }

    @TargetApi(Build.VERSION_CODES.M)
    PointInfo getGreenPointForTest(int step) {
        mGreenLaserPointsCloudAllocation[step].copyTo(tmpPointsCloud);
        short tmp[] = new short[1];
        mGreenLaserPointsAllocation[step].copy1DRangeTo(histogramProbeLine, 1, tmp);
        return getPointForTest(tmp[0]);
    }

    void buildColorMap(int scanSteps, final OnLongWorkListener listener) {
        mRS.finish();
        listener.onChangeState("colormap building");
        Log.v(LOG_TAG, "CALC color map START for " + scanSteps + " steps");
        mScript.forEach_clearImage(mOutputAllocation, mOutputAllocation);
        mRS.finish();
        float minX, maxX, minY, maxY, minZ = 100000, maxZ = 0;
        maxX = maxY = -10;
        minX = minY = 10;
        int cnt = 0;
        double sumZ = 0;
        for (int i = 0; i < scanSteps; i++) {
            mRedLaserPointsCloudAllocation[i].copyTo(tmpPointsCloud);
            for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                float z = tmpPointsCloud[j + 2];
                if (z > 0) {
                    float x = tmpPointsCloud[j], y = tmpPointsCloud[j + 1];
                    if (x > maxX) maxX = x;
                    if (x < minX) minX = x;
                    if (y > maxY) maxY = y;
                    if (y < minY) minY = y;
                    if (z > maxZ) maxZ = z;
                    if (z < minZ) minZ = z;
                    sumZ += z;
                    cnt++;
                }
            }
            mGreenLaserPointsCloudAllocation[i].copyTo(tmpPointsCloud);
            for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                float z = tmpPointsCloud[j + 2];
                if (z > 0) {
                    float x = tmpPointsCloud[j], y = tmpPointsCloud[j + 1];
                    if (x > maxX) maxX = x;
                    if (x < minX) minX = x;
                    if (y > maxY) maxY = y;
                    if (y < minY) minY = y;
                    if (z > maxZ) maxZ = z;
                    if (z < minZ) minZ = z;
                    sumZ += z;
                    cnt++;
                }
            }
        }
        if (maxZ == minZ) {
            maxZ = 10;
            minZ = 0;
        }
        float kx = mSize.getHeight() / (maxX - minX);
        float ky = mSize.getWidth() / (maxY - minY);
        float kZ1 = 0x0FF / (maxZ - minZ);
        float kZ2 = 1 / 0.1f; //(0.1 mm - 1 color bit);
        mScript.set_kRatioZ8(kZ1 < kZ2 ? kZ1 : kZ2);
        mScript.set_kRatioZ16(0x0FFFF / (maxZ - minZ));
        mScript.set_kRatioXY(kx < ky ? kx : ky);
        mScript.set_minX(minX);
        mScript.set_minY(minY);
        mScript.set_maxZ(maxZ);
        Log.v(LOG_TAG, "CALC color map" +
                " X=" + minX + ".." + maxX +
                " Y=" + minY + ".." + maxY +
                " Z=" + minZ + ".." + maxZ +
                " cnt=" + cnt +
                " ovrZ=" + (sumZ / cnt));
        Log.v(LOG_TAG, "CALC color map" +
                " Width=" + mSize.getWidth() +
                " Height=" + mSize.getHeight() +
                " kX=" + kx +
                " kY=" + ky +
                " kXY=" + mScript.get_kRatioXY() +
                " kZ1=" + kZ1 +
                " kZ2=" + kZ2 +
                " kZ8=" + mScript.get_kRatioZ8() +
                " kZ16=" + mScript.get_kRatioZ16());
/*
        int dbg_row_red = -1;
        int dbg_row_green = -1;
        //dbg_row_red = 960;
        if (dbg_row_red > 0) {
            float kRatioZ = mScript.get_kRatioZ16();
            float kRatioXY = mScript.get_kRatioXY();
            for (int i = 0; i < scanSteps; i++) {
                mRedLaserPointsCloudAllocation[i].copyTo(tmpPointsCloud);
                for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                    float z = tmpPointsCloud[j + 2];
                    float x = tmpPointsCloud[j];
                    if (dbg_row_red == j && z > 0) {
                        int imgZ = (int) ((maxZ - z) * kRatioZ);
                        int imgX = (int) ((x - minX) * kRatioXY);
                        Log.v(LOG_TAG, "RED row x:" + x + " y:" + tmpPointsCloud[j + 1] + " z:" + z + " img(" + imgX + "):" + imgZ);
                    }
                }
            }
        }
        if (dbg_row_red > 0) {
            float kRatioZ = mScript.get_kRatioZ();
            //float kRatioXY =mScript.get_kRatioXY16();
            for (int i = 0; i < scanSteps; i++) {
                mGreenLaserPointsCloudAllocation[i].copyTo(tmpPointsCloud);
                for (int j = 0; j < tmpPointsCloud.length; j += 4) {
                    float z = tmpPointsCloud[j + 2];
                    if (dbg_row_green == j && z > 0) {
                        int imgZ = (int) ((maxZ - z) * kRatioZ);
                        Log.v(LOG_TAG, "GREEN row x:" + tmpPointsCloud[j] + " y:" + tmpPointsCloud[j + 1] + " z:" + z + " img:" + Integer.toHexString(imgZ));
                    }
                }
            }
        }
*/

        mScript.set_gColorMapImage(mOutputAllocation);
        mRS.finish();
        Log.v(LOG_TAG, "CALC color map calculation finish");
        if (ConfigDDD.colorMapMode == ConfigDDD.MODE_COLORMAP_PNG) {
            Log.v(LOG_TAG, "CALC color map PNG calculation start");
            for (int i = 0; i < scanSteps; i++) {
                mScript.forEach_setColorMapImage8(mGreenLaserPointsCloudAllocation[i]);
                mScript.forEach_setColorMapImage8(mRedLaserPointsCloudAllocation[i]);
            }
            mRS.finish();
            Log.v(LOG_TAG, "CALC color map blur");
            mScript.set_maxGapSize(ConfigDDD.grayScaleFillCapSize);
            mScript.forEach_fillGap8(mOutputAllocation, mOutputAllocation);
            mOutputAllocation.ioSend();
        } else {
            Log.v(LOG_TAG, "CALC color map TIFF16 calculation start");
            for (int i = 0; i < scanSteps; i++) {
                mScript.forEach_setColorMapImage16(mGreenLaserPointsCloudAllocation[i]);
                mScript.forEach_setColorMapImage16(mRedLaserPointsCloudAllocation[i]);
            }
            mRS.finish();
            Log.v(LOG_TAG, "CALC color map blur");
            mScript.set_maxGapSize(ConfigDDD.grayScaleFillCapSize);
            mScript.forEach_fillGap16(mOutputAllocation, mOutputAllocation);
            mOutputAllocation.ioSend();
        }
        Log.v(LOG_TAG, "CALC color map  END");
        listener.onFinish();
    }


    void saveColorMap8(final String fullFileMask, final TextureView view, final ImageProcessor.OnLongWorkListener listener) {
        new Thread(new Runnable() {
            String fileName = fullFileMask + COLORMAP_PNG_EXT;

            public void run() {
                try {
                    Log.v(LOG_TAG, "Save colormap: " + fileName);
                    listener.onChangeState("Save...\n" + fileName);
                    final Bitmap bitmap = Bitmap.createBitmap(mOutputAllocation.getType().getX(), mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
                    view.getBitmap(bitmap);
                    OutputStream os = new FileOutputStream(fileName);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    os.close();
                    listener.onFinish();
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Load file error", ex);
                    listener.onError("Error load file:" + fileName + "\n" + ex.getMessage());
                }
            }
        }).start();
    }


    //==============================================================================================
    private static void tiff_Int32(DataOutputStream out, int val) throws IOException {
        out.write((byte) val);
        out.write((byte) (val >> 8));
        out.write((byte) (val >> 16));
        out.write((byte) (val >> 24));
    }

    private static void tiff_Int16(DataOutputStream out, int val) throws IOException {
        out.write((byte) val);
        out.write((byte) (val >> 8));
    }

    private static void tiff_IFD(DataOutputStream out, int tag, int type, int count, int val) throws IOException {
        tiff_Int16(out, tag);
        tiff_Int16(out, type);
        tiff_Int32(out, count);
        tiff_Int32(out, val);
    }

    void saveColorMap16(final String fullFileMask, final TextureView view, final ImageProcessor.OnLongWorkListener listener) {
        new Thread(new Runnable() {
            String fileName = fullFileMask + COLORMAP_TIFF_EXT;

            public void run() {
                try {
                    Log.v(LOG_TAG, "Save colormap: " + fileName);
                    listener.onChangeState("Save...\n" + fileName);
                    final Bitmap bitmap = Bitmap.createBitmap(mOutputAllocation.getType().getX(), mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
                    view.getBitmap(bitmap);

                    String descr = "scan";
                    int width = bitmap.getWidth(), height = bitmap.getHeight();
                    DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
                    os.write(new byte[]{0x49, 0x49, 0x2a, 0x00});
                    int ofs = width * height * 2 + 8;
                    tiff_Int32(os, ofs);
                    for (int h = 0; h < height; h++) { // 0..1920
                        if (h % 100 == 0)
                            listener.onChangeState("save colormap line [" + h + "/" + height + "]");
                        for (int w = 0; w < width; w++) { // 0..1024
                            //short val = (short) (h * (0x0ffff / (double) height));
                            int val = bitmap.getPixel(w, h);
                            //tiff_Int16(os, val);
                            os.write((byte) val);
                            os.write((byte) (val >> 8));
                        }
                    }
                    /* Types
                     The field types and their sizes are:
                     1 = BYTE 8-bit unsigned integer.
                     2 = ASCII 8-bit byte that contains a 7-bit ASCII code; the last byte
                     must be NUL (binary zero).
                     3 = SHORT 16-bit (2-byte) unsigned integer.
                     4 = LONG 32-bit (4-byte) unsigned integer.
                     5 = RATIONAL Two LONGs: the first represents the numerator of a
                     fraction; the second, the denominator.
                     The value of the Count part of an ASCII field entry includes the NUL.   */
                    int IFD_cnt = 13;
                    ofs += 2 + IFD_cnt * 12 + 4;
                    tiff_Int16(os, IFD_cnt); // IDF count
                    tiff_IFD(os, 0x0100, 3, 1, width);
                    tiff_IFD(os, 0x0101, 3, 1, height);
                    tiff_IFD(os, 0x0102, 3, 1, 16); // BitsPerSample
                    tiff_IFD(os, 0x0103, 3, 1, 1); // Compression = 01 Uncompressed
                    tiff_IFD(os, 0x0106, 3, 1, 1); //  PhotometricInterpretation = 01 BlackIsZero
                    tiff_IFD(os, 0x0111, 4, 1, 8); //  StripOffsets = 8   - The offset is specified with respect to the beginning of the TIFF file
                    tiff_IFD(os, 0x0115, 3, 1, 1); ////SamplesPerPixel = 1 (If SamplesPerPixel is 1, PlanarConfiguration is irrelevant, and need not be included.)
                    tiff_IFD(os, 0x0117, 4, 1, height * width * 2); //StripByteCounts
                    tiff_IFD(os, 0x011A, 5, 1, ofs); //XResolution px/cm
                    ofs += 8;
                    tiff_IFD(os, 0x011B, 5, 1, ofs); //YResolution px/cm
                    ofs += 8;
                    tiff_IFD(os, 0x011C, 3, 1, 1); // PlanarConfiguration = 1
                    tiff_IFD(os, 0x011E, 2, descr.length() + 1, ofs); // ImageDescription
                    tiff_IFD(os, 0x0128, 3, 1, 3); // ResolutionUnit = 3 = Centimeter.
                    os.writeInt(0); // no next IFD
//  int r1 = 1677721600; //width * 1000;
//  int r2 = 2097152; //Math.round(width_mm * 100.0f);
                    int r1 = width * 1000;
                    int r2 = Math.round(1000 * 100.0f);
                    tiff_Int32(os, r1);
                    tiff_Int32(os, r2);
                    tiff_Int32(os, r1);
                    tiff_Int32(os, r2);
                    os.write(descr.getBytes("cp1251"));
                    os.writeByte(0);
                    os.close();
                    listener.onFinish();
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Load file error", ex);
                    listener.onError("Error load file:" + fileName + "\n" + ex.getMessage());
                }
            }
        }).start();
    }
}
