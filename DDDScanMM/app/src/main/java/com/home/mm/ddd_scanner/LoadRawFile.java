package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.home.mm.dddscanmm.R;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LoadRawFile extends Activity {
    private ListView lv_FileSystem;
    private String currentPath;
    private final static String KEY_FILENAME = "f";
    private final static String KEY_IMAGE = "i";

    private void refreshFileList(File path) {
        currentPath = path.getAbsolutePath();
        ((TextView) findViewById(R.id.textView_loadRawFilePath)).setText(currentPath);
        if (path == null) {
            path = Environment.getExternalStorageDirectory();
        }
        File[] dirs = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.isDirectory() && file.canRead());
            }
        });
        File[] files = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (!file.isDirectory() && file.canRead() && file.getName().endsWith(ImageProcessor.RAW_FILE_EXT));
            }
        });
        Arrays.sort(dirs);
        Arrays.sort(files);
        ArrayList<Map<String, Object>> fileList = new ArrayList<Map<String, Object>>();
        if (path.getParentFile() != null && path.getParentFile().listFiles() != null) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(KEY_FILENAME, "..");
            m.put(KEY_IMAGE, R.drawable.folder);
            fileList.add(m);
        }
        for (File dir : dirs) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(KEY_FILENAME, dir.getName());
            m.put(KEY_IMAGE, R.drawable.folder);
            fileList.add(m);
        }
        for (File file : files) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(KEY_FILENAME, file.getName());
            m.put(KEY_IMAGE, R.drawable.file);
            fileList.add(m);
        }

        SimpleAdapter adapter = new SimpleAdapter(this, fileList, R.layout.raw_filelist_item_layout, new String[] {
                KEY_FILENAME, KEY_IMAGE}, new int[] { R.id.textView_itemRawFileName, R.id.imageView_itemRaw});
        lv_FileSystem.setAdapter(adapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_raw_file);
        lv_FileSystem = (ListView) findViewById(R.id.listView_RawFileList);
        lv_FileSystem.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String fname = ((Map<String, Object>) lv_FileSystem.getItemAtPosition(position)).get(KEY_FILENAME).toString();
                File f = fname.equals("..") ?
                        new File(currentPath).getParentFile() :
                        new File(currentPath, fname);
                if (f.isDirectory()) {
                    refreshFileList(f);
                } else {
                    Intent intent = new Intent();
                    //Log.v(LOG_TAG, "selected file:'" + f.getAbsoluteFile() + "'");
                    intent.putExtra("fileName", f.getAbsoluteFile().toString()  );
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
        if(ConfigDDD.pathForFilesSave.length() == 0) {
            ConfigDDD.pathForFilesSave = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
        }
        refreshFileList(new File(ConfigDDD.pathForFilesSave));
    }
}
