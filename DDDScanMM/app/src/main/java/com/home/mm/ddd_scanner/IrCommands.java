package com.home.mm.ddd_scanner;

import android.app.Activity;
import android.hardware.ConsumerIrManager;
import android.os.ConditionVariable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IrCommands {
    private ConsumerIrManager mCIR = null;
    private Activity parentActivity;
    private final static String LOG_TAG = "IrCommands";

    public IrCommands(ConsumerIrManager c) {
        mCIR = c;
    }


    public final static byte CMD_IR_LASERS_OFF = 0;
    public final static byte CMD_IR_LASERS_ON = 1;
    public final static byte CMD_IR_MOTORS_OFF = 2;
    public final static byte CMD_IR_GOTO_START = 3;
    public final static byte CMD_IR_GOTO_TEST_POSITION = 4;
    public final static byte CMD_IR_DO_STEP = 5;
    public final static byte CMD_IR_STEP_RED_RIGHT = 6;
    public final static byte CMD_IR_STEP_RED_LEFT = 7;
    public final static byte CMD_IR_STEP_GREEN_RIGHT = 8;
    public final static byte CMD_IR_STEP_GREEN_LEFT = 9;
    public final ConditionVariable mIrResourceWaiter = new ConditionVariable();
    public final ConditionVariable mIrStartWaiter = new ConditionVariable();

    private HashMap<Byte, String> logCodeName = new HashMap<Byte, String>() {
        {
            put(CMD_IR_LASERS_OFF, "CMD_IR_LASERS_OFF");
            put(CMD_IR_LASERS_ON, "CMD_IR_LASERS_ON");
            put(CMD_IR_MOTORS_OFF, "CMD_IR_MOTORS_OFF");
            put(CMD_IR_GOTO_START, "CMD_IR_GOTO_START");
            put(CMD_IR_GOTO_TEST_POSITION, "CMD_IR_GOTO_TEST_POSITION");
            put(CMD_IR_DO_STEP, "CMD_IR_DO_STEP");
            put(CMD_IR_STEP_RED_RIGHT, "CMD_IR_STEP_RED_RIGHT");
            put(CMD_IR_STEP_RED_LEFT, "CMD_IR_STEP_RED_LEFT");
            put(CMD_IR_STEP_GREEN_RIGHT, "CMD_IR_STEP_GREEN_RIGHT");
            put(CMD_IR_STEP_GREEN_LEFT, "CMD_IR_STEP_GREEN_LEFT");
        }
    };

    // TSOP1736
    private final int IR_FREQUENCY = 36000;
    private final int PATTERN_HD_T = 5000; // transmit time
    private final int PATTERN_HD_S = 500; //  silent time
    private final int PATTERN_BIT_T = 300; // 0.3ms
    private final int PATTERN_BIT_S0 = 300;
    private final int PATTERN_BIT_S1 = 600;
    private final int PATTERN_END_T = 900;

    private List<Integer> buildCmd(byte cmd) {
        List<Integer> res = new ArrayList<Integer>();
        res.add(PATTERN_HD_T);
        res.add(PATTERN_HD_S);
        byte chk = 0;
        for (byte i = 0; i < 4; i++, cmd = (byte) (cmd >> 1)) {
            res.add(PATTERN_BIT_T);
            if ((cmd & 1) == 0) res.add(PATTERN_BIT_S0);
            else {
                res.add(PATTERN_BIT_S1);
                chk++;
            }
        }
        res.add(PATTERN_BIT_T);
        res.add((chk & 1) == 0 ? PATTERN_BIT_S0 : PATTERN_BIT_S1);
        return res;
    }

    private void addByte(List<Integer> res, byte data) {
        Log.v(LOG_TAG, " addByte:" + Integer.toString((int)data & 0x0FF));
        byte chk = 0;
        for (byte i = 0; i < 8; i++, data = (byte) (data >> 1)) {
            res.add(PATTERN_BIT_T);
            if ((data & 1) == 0) res.add(PATTERN_BIT_S0);
            else {
                res.add(PATTERN_BIT_S1);
                chk++;
            }
        }
        res.add(PATTERN_BIT_T);
        res.add((chk & 1) == 0 ? PATTERN_BIT_S0 : PATTERN_BIT_S1);
    }

    public boolean sendIRCmd(int delay, final byte cmd) {
        if (!mCIR.hasIrEmitter()) {
            Log.e(LOG_TAG, "No IR Emitter found");
            FatalErrorDialog.showError("No IR Emitter found");
            return false;
        }
        List<Integer> p = buildCmd(cmd);
        switch (cmd) {
            case CMD_IR_GOTO_START:
                addByte(p, (byte) (ConfigDDD.stepMotorScanFromStepRed >> 8));
                addByte(p, (byte) ConfigDDD.stepMotorScanFromStepRed);
                addByte(p, (byte) (ConfigDDD.stepMotorScanFromStepGreen >> 8));
                addByte(p, (byte) ConfigDDD.stepMotorScanFromStepGreen);
                break;
            case CMD_IR_GOTO_TEST_POSITION:
                int sr = ConfigDDD.stepMotorScanFromStepRed + ConfigDDD.stepMotorRedTestSteps;
                int sg = ConfigDDD.stepMotorScanFromStepGreen + ConfigDDD.stepMotorGreenTestSteps;
                addByte(p, (byte) (sr >> 8));
                addByte(p, (byte) sr);
                addByte(p, (byte) (sg >> 8));
                addByte(p, (byte) sg);
                break;
        }
        p.add(PATTERN_END_T);

        final int[] pattern = new int[p.size()];
        for (int i = 0; i < p.size(); i++) pattern[i] = p.get(i);
        mIrStartWaiter.close();
        new Thread(new Runnable() {
            public void run() {
                mIrResourceWaiter.block(1000);
                String s = "";
                for (int i : pattern) s += " " + i;
                Log.v(LOG_TAG, "Start transmit " + logCodeName.get(cmd) + " data:" + s);
                mIrResourceWaiter.close();
                mIrStartWaiter.open();
                mCIR.transmit(IR_FREQUENCY, pattern);
                mIrResourceWaiter.open();
                Log.v(LOG_TAG, "End transmit " + logCodeName.get(cmd));
            }
        }).start();
        mIrStartWaiter.block(1000);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Log.v(LOG_TAG, " sendIRCmd ", e);
        }
        return true;
    }
}
