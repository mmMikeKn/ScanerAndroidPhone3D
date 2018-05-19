package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Dialog;
import android.os.Handler;

public class FatalErrorDialog extends DialogFragment {
    private String mMessage;

    public static Handler mFatalErrMsgHandler;
    public static FragmentManager mFragmentManager;

    public static void showError(final String msg) {
        mFatalErrMsgHandler.post(new Runnable() {

            @Override
            public void run() {
                FatalErrorDialog dlg = new FatalErrorDialog();
                dlg.mMessage = msg;
                dlg.show(mFragmentManager, "errDlg");
            }
        });

    }


    public FatalErrorDialog() {
        mMessage = "";
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        return new AlertDialog.Builder(activity)
                .setMessage(mMessage)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                       // activity.finish();
                    }
                })
                .create();
    }
}
