package com.ibericart.fuelanalyzer.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.ibericart.fuelanalyzer.util.logger.Log;
import com.ibericart.fuelanalyzer.util.logger.LogWrapper;

/**
 * Base launcher activity, to handle most of the common tasks.
 */
public class BaseActivity extends FragmentActivity {

    public static final String TAG = "SampleActivityBase";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected  void onStart() {
        super.onStart();
        initializeLogging();
    }

    /**
     * Set up targets to receive log data */
    public void initializeLogging() {
        // Using the custom Log
        // in front of the normal logging chain
        // the custom logging emulates android.util.log method signatures
        // and wraps around Android's native logging framework
        LogWrapper logWrapper = new LogWrapper();
        Log.setLogNode(logWrapper);
        Log.i(TAG, "Ready");
    }
}
