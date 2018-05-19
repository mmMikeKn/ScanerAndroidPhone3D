package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.home.mm.dddscanmm.R;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;

public class ConfigDDDActivity extends Activity {
    private final static String LOG_TAG = "ConfigDDDActivity";
    ListView lv_FileSystem;

    private boolean refreshFileList(File path) {
        if (path == null) {
            Toast.makeText(ConfigDDDActivity.this, "Can't browse", Toast.LENGTH_LONG).show();
            return false;
        }
        File[] dirs = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.isDirectory() && file.canRead());
            }
        });
        if (dirs == null) {
            Toast.makeText(ConfigDDDActivity.this, "Can't browse:" + path.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return false;
        }
        Arrays.sort(dirs);
        int i = 0;
        String[] fileList;
        if (path.getParentFile() == null || path.getParentFile().listFiles() == null) {
            fileList = new String[dirs.length];
        } else {
            fileList = new String[dirs.length + 1];
            fileList[i++] = "..";
        }
        for (File dir : dirs) {
            //if(Environment.isExternalStorageRemovable(dir)) fileList[i++] = "SD:["+dir.getName()+"]";
            fileList[i++] = dir.getName();
        }
        lv_FileSystem.setAdapter(new ArrayAdapter<String>(this,
                R.layout.filepath_list_item, fileList) {
            @Override
            public View getView(int pos, View view, ViewGroup parent) {
                view = super.getView(pos, view, parent);
                ((TextView) view).setSingleLine(true);
                return view;
            }
        });
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_ddd);
        TabHost tabHost = (TabHost) findViewById(R.id.tab_host);
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec("1");
        tabSpec.setIndicator("Scan\nalgorithm");
        tabSpec.setContent(R.id.configureTabScanAlg);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("2");
        //tabSpec.setIndicator(getLayoutInflater().inflate(R.layout.tab_hd_layout_cfg_camera_resolution, null));
        tabSpec.setIndicator("Camera\nresolution");
        tabSpec.setContent(R.id.configureTabCameraResolutionSelector);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("3");
        tabSpec.setIndicator("Scanner\nmetric");
        tabSpec.setContent(R.id.configureTabMetric);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("4");
        tabSpec.setIndicator("External\ncontroller");
        tabSpec.setContent(R.id.configureTabExtHwd);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("5");
        tabSpec.setIndicator("Camera\nmode");
        tabSpec.setContent(R.id.configureTabCameraMode);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("6");
        tabSpec.setIndicator("Result\nfiles");
        tabSpec.setContent(R.id.configureTabSaveResult);
        tabHost.addTab(tabSpec);

        tabHost.setCurrentTabByTag("1");
        for (int i = 0; i < tabHost.getTabWidget().getChildCount(); i++) {
            TextView tv = (TextView) tabHost.getTabWidget().getChildAt(i).findViewById(android.R.id.title);
            tv.setPadding(15, 15, 15, 30);
            //String s = tv1.getText().toString();
            tv.setSingleLine(false);
            //tv1.setText(s);
            tv.setShadowLayer(3, 3, 3, Color.BLACK);
        }

//----------------------------------
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics mCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (mCharacteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                String info = "hwd:";
                switch (mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                        info += "LEGACY";
                        break;
                    case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                        info += "LIMITED";
                        break;
                    case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                        info += "FULL";
                        break;
                    default:

                }

                TextView tv = (TextView) findViewById(R.id.configCameraIdTextView);
                tv.setText(info);
                final ListView lv = (ListView) findViewById(R.id.cameraResolutionListView);
                Size[] sizes = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
                ArrayList<String> l = new ArrayList<String>();
                for (Size sz : sizes) {
                    if (CameraFunctions.AF_REGIONS < sz.getHeight() / 2 || CameraFunctions.AF_REGIONS < sz.getWidth() / 2) {
                        l.add(sz.toString());
                    }
                }

                lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, l.toArray(new String[l.size()])));
                if (ConfigDDD.cameraResolutionIndex < 0 || ConfigDDD.cameraResolutionIndex > l.size()) {
                    ConfigDDD.cameraResolutionIndex = 0;
                }
                lv.setItemChecked(ConfigDDD.cameraResolutionIndex, true);
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        ConfigDDD.cameraResolutionIndex = position;
                    }
                });

                break;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "select camera resolution:", e);
            ((TextView) findViewById(R.id.configCameraIdTextView)).setText("filling camera resolution list error:" + e.toString());
        }
//----------------------------------
        final ListView lv_modeGetRgbAllocation = (ListView) findViewById(R.id.listView_modeGetRgbAllocation);
        String[] modeGetRgbAllocation_list = {
                "Via Texture View getBitmap()",
                "Via Allocation surface YUV_420_888",
                "Via Allocation surface XXX",
        };
        lv_modeGetRgbAllocation.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, modeGetRgbAllocation_list));
        lv_modeGetRgbAllocation.setItemChecked(ConfigDDD.modeGetRgbAllocation, true);
        lv_modeGetRgbAllocation.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ConfigDDD.modeGetRgbAllocation = position;
            }
        });
        final ListView lv_modeCapture = (ListView) findViewById(R.id.listView_modeCapture);
        String[] modeCapture_list = {
                "Via preview",
                "Via capture"
        };
        lv_modeCapture.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, modeCapture_list));
        lv_modeCapture.setItemChecked(ConfigDDD.modeCapture, true);
        lv_modeCapture.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ConfigDDD.modeCapture = position;
            }
        });
//----------------------------------
        final EditText editText_pathForFilesSave = (EditText) findViewById(R.id.editText_pathForFilesSave);
        editText_pathForFilesSave.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String s = editText_pathForFilesSave.getText().toString();
                if(s.isEmpty()) {
                    s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
                }
                File f = new File(s);
                if (refreshFileList(f)) {
                    ConfigDDD.pathForFilesSave = f.getAbsolutePath();
                    editText_pathForFilesSave.setText(ConfigDDD.pathForFilesSave);
                }
                return true;
            }
        });

        lv_FileSystem = (ListView) findViewById(R.id.listView_fileSystem);
        if (ConfigDDD.pathForFilesSave.length() == 0) {
            ConfigDDD.pathForFilesSave = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
        }

        lv_FileSystem.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = (String) lv_FileSystem.getItemAtPosition(position);
                //Log.v(LOG_TAG, "selected file:'" + fname + "'");
                File f;
                if ("..".equals(name)) {
                    f = new File(ConfigDDD.pathForFilesSave);
                    if (f == null) {
                        Toast.makeText(ConfigDDDActivity.this, "Can't browse '..'", Toast.LENGTH_LONG).show();
                        return;
                    }
                    f = f.getParentFile();
                } else {
                    f = new File(ConfigDDD.pathForFilesSave, name);
                }
                if (refreshFileList(f)) {
                    ConfigDDD.pathForFilesSave = f.getAbsolutePath();
                    editText_pathForFilesSave.setText(ConfigDDD.pathForFilesSave);
                }
            }
        });
        if (!refreshFileList(new File(ConfigDDD.pathForFilesSave))) {
            ConfigDDD.pathForFilesSave = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
            refreshFileList(new File(ConfigDDD.pathForFilesSave));
        }

//----------------------------------
        RadioButton aRadioButton_bluetooth = (RadioButton) findViewById(R.id.radioButtonBluetooth);
        aRadioButton_bluetooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigDDD.isBluetoothDeviceMode = isChecked;
                findViewById(R.id.editText_bluetoothDeviceNameMask).setVisibility(ConfigDDD.isBluetoothDeviceMode ? View.VISIBLE : View.INVISIBLE);
            }
        });
        aRadioButton_bluetooth.setChecked(ConfigDDD.isBluetoothDeviceMode);
        ((RadioButton) findViewById(R.id.radioButtonIR)).setChecked(!ConfigDDD.isBluetoothDeviceMode);
        findViewById(R.id.editText_bluetoothDeviceNameMask).setVisibility(ConfigDDD.isBluetoothDeviceMode ? View.VISIBLE : View.INVISIBLE);
//----------------------------------
        CheckBox aCheckBox_isBinaryPLY = (CheckBox) findViewById(R.id.checkBox_isBinaryPLY);
        aCheckBox_isBinaryPLY.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigDDD.isBinaryPLY = isChecked;
            }
        });
        aCheckBox_isBinaryPLY.setChecked(ConfigDDD.isBinaryPLY);
        //------
        CheckBox aCheckBox_isColorPLY = (CheckBox) findViewById(R.id.checkBox_isColorPLY);
        aCheckBox_isColorPLY.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigDDD.isColorPLY = isChecked;
            }
        });
        aCheckBox_isColorPLY.setChecked(ConfigDDD.isColorPLY);
        //------
        CheckBox aCheckBox_isFixedFileName = (CheckBox) findViewById(R.id.checkBox_isFixedFileName);
        aCheckBox_isFixedFileName.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigDDD.isFixedFileName = isChecked;
            }
        });
        aCheckBox_isFixedFileName.setChecked(ConfigDDD.isFixedFileName);
        //------
        RadioButton aRadioButton_ColorMapBmp = (RadioButton) findViewById(R.id.radioButtonBmp);
        RadioButton aRadioButton_ColorMapTiff = (RadioButton) findViewById(R.id.radioButtonTiff);
        RadioButton aRadioButton_ColorMapNone = (RadioButton) findViewById(R.id.radioButtonNone);
        aRadioButton_ColorMapBmp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) ConfigDDD.colorMapMode = ConfigDDD.MODE_COLORMAP_PNG;
            }
        });
        aRadioButton_ColorMapTiff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) ConfigDDD.colorMapMode = ConfigDDD.MODE_COLORMAP_TIFF;
            }
        });
        aRadioButton_ColorMapNone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) ConfigDDD.colorMapMode = ConfigDDD.MODE_COLORMAP_NONE;
            }
        });
        switch(ConfigDDD.colorMapMode) {
            case ConfigDDD.MODE_COLORMAP_PNG:
                aRadioButton_ColorMapBmp.setChecked(true);
                break;
            case ConfigDDD.MODE_COLORMAP_TIFF:
                aRadioButton_ColorMapTiff.setChecked(true);
                break;
            default:
                aRadioButton_ColorMapNone.setChecked(true);
                break;
        }
//----------------------------------
        try {
            ConfigDDD.setEditorFields(this);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error fill EditText fields", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            ConfigDDD.getEditorFields(this);
            ConfigDDD.saveConfig(getSharedPreferences(ConfigDDD.configName, 0));
            //     Toast.makeText(getApplicationContext(), "Config saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(LOG_TAG, "save config.", e);
            AlertDialog.Builder builder = new AlertDialog.Builder(ConfigDDDActivity.this);
            builder.setTitle("Error save configure")
                    .setMessage(e.toString())
                    .setCancelable(true);
            AlertDialog alert = builder.create();
            alert.show();
        }
    }
}
