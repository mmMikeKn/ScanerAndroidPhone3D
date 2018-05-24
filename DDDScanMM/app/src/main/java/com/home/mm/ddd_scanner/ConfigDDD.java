package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.EditText;

import com.home.mm.dddscanmm.R;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class ConfigDDD {
    private final static String LOG_TAG = "ConfigDDD";
    // get allocation via TextureView.getBitmap() (TextureView mast be visible!!!)
    // MODE_CAPTURE_VIA_CAPTURE - getImage() 200..250ms for all(Samsung S5)
    // MODE_CAPTURE_VIA_PREVIEW - getImage() 50..70ms for all (Samsung S5)
    public final static int MODE_VIA_TEXTUREVIEW_GETBITMAP = 0;

    // get Allocation via Allocation.getSurface()
    // MODE_CAPTURE_VIA_PREVIEW - // getImage() 60..80 ms for all (render script part: 40..50ms) (Samsung S5)
    public final static int MODE_VIA_ALLOCATION_SURFACE_NORMAL = 1;
    //public final static int MODE_VIA_ALLOCATION_SURFACE_XXX = 2; //samsung s5 android v5.0 dos not support YUV_420_888
    public static int modeGetRgbAllocation = MODE_VIA_TEXTUREVIEW_GETBITMAP;
//-----------------------------------------------------

    public final static int MODE_CAPTURE_VIA_CAPTURE = 1; // slow method 200-250ms (getBitmap)
    public final static int MODE_CAPTURE_VIA_PREVIEW = 0; // fast method 50-60ms (getBitmap)
    public static int modeCapture = MODE_CAPTURE_VIA_PREVIEW;
    //-----------------------------------------------------
    public static boolean isBluetoothDeviceMode = true;
    public static String bluetoothDeviceNameMask = "HC-0";
    public static int IRCtrlStepTimeMs = 100;
    public static int IRCtrlLasersTimeMs = 50;
    //-----------------------------------------------------
    public static int cameraResolutionIndex = 0;
    //-----------------------------------------------------
    public static int fullScanStepsNumber = 1300;
    //-----------------------------------------------------
    public static int notLaserStepDivider = 20;
    public static int laserDetectProbeWidth = 13;
    public static int laserDetectThresholdLevel = 300;
    public static int grayScaleFillCapSize = 8;

    // -------- camera view angle
    public static double cameraViewTangent = 238.0/691.0;
    // --------
    public static int scanDistanceThreshold = 1200;
    public static int depthScanThreshold = 1000;
    public static int widthScanThreshold = 1300;
    public static int heightScanThreshold = 1300;

    //---------
    public static float stepMotorStartAngleRed = 88.0f;
    public static float stepMotorStartAngleGreen = 87.15f;
    public static int stepMotorRedTestSteps = 500; //800*0.05625 = 45
    public static int stepMotorGreenTestSteps = 500;
    public static int stepMotorStepsPer2PI = 400*2*8; // (35BYGHM302-06LA 0.3A, 0.9°+ half step + pulley 15->120=x8) 0.05625
    public static int stepMotorScanFromStepGreen = 650;
    public static int stepMotorScanFromStepRed = 595;
    // ---------
    public static float laserGreenL = 597;
    public static float laserRedL = 594;
    // ---------
    public static boolean isBinaryPLY = true;
    public static boolean isColorPLY = true;
    public static boolean isFixedFileName = true;
    public final static int MODE_COLORMAP_TIFF = 0;
    public final static int MODE_COLORMAP_PNG = 1;
    public final static int MODE_COLORMAP_NONE = 2;
    public static int colorMapMode = MODE_COLORMAP_NONE;
    public static String pathForFilesSave = "";


    public static String configName = "ConfigDDD";

    public static void loadConfig(SharedPreferences settings) throws Exception {
        Class aClass = ConfigDDD.class;
        Field[] fields = aClass.getFields();
        for (Field field : fields) {
            if ((field.getModifiers() & Modifier.FINAL) != 0) continue;
            String name = field.getName();
            String type = field.getType().getCanonicalName();
            if (type.equals("int")) {
                field.set(null, settings.getInt(name, field.getInt(aClass)));
            } else if (type.equals("double")) {
                field.set(null, Double.parseDouble(settings.getString(name, Double.toString(field.getDouble(aClass)))));
            } else if (type.equals("float")) {
                field.set(null, settings.getFloat(name, field.getFloat(aClass)));
            } else if (type.contains("java.lang.String")) {
                String s = settings.getString(name, field.get(aClass).toString());
                if (s != null && !s.isEmpty())
                    field.set(null, s);
            } else if (type.equals("boolean")) {
                field.set(null, settings.getBoolean(name, field.getBoolean(aClass)));
            }
        }
    }

    public static void saveConfig(SharedPreferences settings) throws Exception {
        SharedPreferences.Editor editor = settings.edit();
        Class aClass = ConfigDDD.class;
        Field[] fields = ConfigDDD.class.getFields();
        for (Field field : fields) {
            String name = field.getName();
            String type = field.getType().getCanonicalName();
            if (type.equals("int")) {
                editor.putInt(name, field.getInt(aClass));
            } else if (type.equals("double")) {
                editor.putString(name, Double.toString(field.getDouble(aClass)));
            } else if (type.equals("float")) {
                editor.putFloat(name, field.getFloat(aClass));
            } else if (type.contains("java.lang.String")) {
                editor.putString(name, field.get(aClass).toString());
            } else if (type.equals("boolean")) {
                editor.putBoolean(name, field.getBoolean(aClass));
            }
        }
        editor.apply();
    }


    private static HashMap<String, Integer> getFields() throws IllegalAccessException {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        for (Field field : R.id.class.getFields()) {
            if (field.getType().isPrimitive() && field.getType().equals(Integer.TYPE)) {
                map.put(field.getName(), field.getInt(R.id.class));
            }
        }
        return map;
    }

    public static void setEditorFields(Activity activity) throws Exception {
        Class aClassSrc = ConfigDDD.class;
        Field[] fieldsSrc = aClassSrc.getFields();

        HashMap<String, Integer> map = getFields();
        for (Field field : fieldsSrc) {
            if ((field.getModifiers() & Modifier.FINAL) != 0) continue;
            String name = "editText_" + field.getName();
            if (!map.containsKey(name)) continue;
            String type = field.getType().getCanonicalName();
            String value = "";
            if (type.equals("int")) {
                value = Integer.toString(field.getInt(aClassSrc));
            } else if (type.equals("double")) {
                value = Double.toString(field.getDouble(aClassSrc));
            } else if (type.equals("float")) {
                value = Float.toString(field.getFloat(aClassSrc));
            } else if (type.contains("java.lang.String")) {
                value = field.get(aClassSrc).toString();
            }
            ((EditText) activity.findViewById(map.get(name))).setText(value);
        }
    }

    public static void getEditorFields(Activity activity) throws Exception {
        Class aClassSrc = ConfigDDD.class;
        Field[] fieldsSrc = aClassSrc.getFields();

        HashMap<String, Integer> map = getFields();
        for (Field field : fieldsSrc) {
            if ((field.getModifiers() & Modifier.FINAL) != 0) continue;
            try {
                String name = "editText_" + field.getName();
                if (!map.containsKey(name)) continue;
                String type = field.getType().getCanonicalName();
                String value = ((EditText) activity.findViewById(map.get(name))).getText().toString();
                if (type.equals("int")) {
                    field.setInt(aClassSrc, Integer.parseInt(value));
                } else if (type.equals("double")) {
                    field.setDouble(aClassSrc, Double.parseDouble(value));
                } else if (type.equals("float")) {
                    field.setFloat(aClassSrc, Float.parseFloat(value));
                } else if (type.contains("java.lang.String")) {
                    field.set(aClassSrc, value);
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, "save config:" + field.getName(), ex);
            }
        }
    }
}
