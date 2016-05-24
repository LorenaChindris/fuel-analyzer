package com.ibericart.fuelanalyzer.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ViewAnimator;

import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.fragment.BluetoothConnectionFragment;
import com.ibericart.fuelanalyzer.util.logger.Log;
import com.ibericart.fuelanalyzer.util.logger.LogFragment;
import com.ibericart.fuelanalyzer.util.logger.LogWrapper;
import com.ibericart.fuelanalyzer.util.logger.MessageOnlyLogFilter;

/**
 * A simple launcher activity containing a summary description, sample log and a custom
 * {@link android.support.v4.app.Fragment} which can display a view.
 * <p>
 * For devices with displays with a width of 720dp or greater, the sample log is always visible,
 * on other devices it's visibility is controlled by an item on the Action Bar.
 */
public class MainActivity extends BaseActivity {

    public static final String TAG = "MainActivity";

    // whether the Log Fragment is currently shown
    private boolean logShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            BluetoothConnectionFragment fragment = new BluetoothConnectionFragment();
            transaction.replace(R.id.sample_content_fragment, fragment);
            transaction.commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem logToggle = menu.findItem(R.id.menu_toggle_log);
        logToggle.setVisible(findViewById(R.id.sample_output) instanceof ViewAnimator);
        logToggle.setTitle(logShown ? R.string.sample_hide_log : R.string.sample_show_log);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_toggle_log:
                logShown = !logShown;
                ViewAnimator output = (ViewAnimator) findViewById(R.id.sample_output);
                if (logShown) {
                    output.setDisplayedChild(1);
                }
                else {
                    output.setDisplayedChild(0);
                }
                supportInvalidateOptionsMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Creates a chain of targets that will receive log data
     */
    @Override
    public void initializeLogging() {
        // wraps Android's native log framework
        LogWrapper logWrapper = new LogWrapper();
        // using the custom Log in front of the native logging chain
        // emulates android.util.log method signatures
        Log.setLogNode(logWrapper);
        // the filter strips out everything except the message text
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // on screen logging via a fragment with a TextView
        LogFragment logFragment = (LogFragment) getSupportFragmentManager()
                .findFragmentById(R.id.log_fragment);
        msgFilter.setNext(logFragment.getLogView());
        Log.i(TAG, "Ready");
    }
}
