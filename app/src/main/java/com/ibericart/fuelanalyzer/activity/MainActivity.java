package com.ibericart.fuelanalyzer.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.enums.AvailableCommandNames;

import com.google.inject.Inject;

import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.config.ObdConfig;
import com.ibericart.fuelanalyzer.service.AbstractGatewayService;
import com.ibericart.fuelanalyzer.logger.CsvLogWriter;
import com.ibericart.fuelanalyzer.service.MockObdGatewayService;
import com.ibericart.fuelanalyzer.io.ObdCommandJob;
import com.ibericart.fuelanalyzer.service.ObdGatewayService;
import com.ibericart.fuelanalyzer.io.ObdProgressListener;
import com.ibericart.fuelanalyzer.model.ObdReading;
import com.ibericart.fuelanalyzer.logger.TripLog;
import com.ibericart.fuelanalyzer.model.TripRecord;
import com.ibericart.fuelanalyzer.util.Constants;
import com.ibericart.fuelanalyzer.util.ObdUtil;
import com.ibericart.fuelanalyzer.util.PositionUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

import static com.ibericart.fuelanalyzer.activity.ConfigActivity.getGpsDistanceUpdatePeriod;
import static com.ibericart.fuelanalyzer.activity.ConfigActivity.getGpsUpdatePeriod;

/**
 * Main Activity Class
 * Uses code from https://github.com/pires/android-obd-reader
 * and https://github.com/barbeau/gpstest
 * <br />
 * It uses the RoboGuice library to inject dependencies.
 * For this, it extends RoboActivity, which uses Google Guice
 * as a dependency injection framework.
 */
@ContentView(R.layout.main)
public class MainActivity extends RoboActivity
        implements ObdProgressListener, LocationListener, GpsStatus.Listener {

    private static final String TAG = MainActivity.class.getName();

    private static final int NO_BLUETOOTH_ID = 0;
    private static final int BLUETOOTH_DISABLED = 1;
    private static final int START_LIVE_DATA = 2;
    private static final int STOP_LIVE_DATA = 3;
    private static final int SETTINGS = 4;
    private static final int TABLE_ROW_MARGIN = 7;
    private static final int NO_ORIENTATION_SENSOR = 8;
    private static final int NO_GPS_SUPPORT = 9;
    private static final int TRIPS_LIST = 10;
    private static final int SAVE_TRIP_NOT_AVAILABLE = 11;
    private static final int REQUEST_ENABLE_BT = 1234;

    // default VIN (vehicle identification number)
    private static final String DEFAULT_VIN = "UNDEFINED_VIN";

    private static final String CSV_LOG_FILE_NAME_DATE_FORMAT = "_dd_MM_yyyy_HH_mm_ss";
    private static final String CSV_LOG_FILE_NAME_PREFIX = "log";
    private static final String CSV_LOG_FILE_NAME_EXTENSION = ".csv";

    private static final String SEND_LOGS_MESSAGE = "Found issues?\nPlease send us the logs.\nSend Logs?";
    private static final String YES_LABEL = "Yes";
    private static final String NO_LABEL = "No";

    private static final String KEY_VALUE_SEPARATOR = ": ";

    private static final int KILOMETER = 1000;
    private static final String DECIMAL_FORMAT_PATTERN = "00.00";

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }

    // used for saving the state of Bluetooth when the application is launched
    // this way we can restore its state when the application is shut down
    private static boolean bluetoothDefaultIsEnabled = false;

    public Map<String, String> commandResult = new HashMap<>();

    boolean gpsIsStarted = false;

    private LocationManager locationManager;
    private LocationProvider locationProvider;
    private CsvLogWriter csvLogWriter;

    private Location lastLocation;
    private Location previousLocation;

    // distance traveled in this trip
    // measured in meters
    private double totalTraveled;

    private TripLog tripLog;
    private TripRecord currentTrip;

    @InjectView(R.id.compass_text)
    private TextView compass;

    @InjectView(R.id.distance_text)
    private TextView distance;

    @InjectView(R.id.BT_STATUS)
    private TextView bluetoothStatusTextView;

    @InjectView(R.id.OBD_STATUS)
    private TextView obdStatusTextView;

    @InjectView(R.id.GPS_POS)
    private TextView gpsStatusTextView;

    @InjectView(R.id.vehicle_view)
    private LinearLayout vehicleViewLinearLayout;

    @InjectView(R.id.data_table)
    private TableLayout dataTableLayout;

    @Inject
    @SuppressWarnings("unused")
    private SensorManager sensorManager;

    @Inject
    @SuppressWarnings("unused")
    private PowerManager powerManager;

    @Inject
    @SuppressWarnings("unused")
    private SharedPreferences preferences;

    private boolean isServiceBound;

    private AbstractGatewayService service;

    private Sensor orientationSensor = null;

    private PowerManager.WakeLock wakeLock = null;

    private boolean prerequisites = true;

    private final SensorEventListener compassDirectionListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
            // updates the text view which displays
            // the compass direction as detected by the sensor
            updateTextView(compass, PositionUtil.getCompassDirection(event.values[0]));
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private final Runnable queueCommands = new Runnable() {

        public void run() {
            if (service != null && service.isRunning() && service.queueEmpty()) {
                queueCommands();

                double lat = 0;
                double lon = 0;
                double alt = 0;
                final int posLen = 7;

                if (gpsIsStarted && lastLocation != null) {
                    lat = lastLocation.getLatitude();
                    lon = lastLocation.getLongitude();
                    alt = lastLocation.getAltitude();

                    StringBuilder sb = new StringBuilder();
                    sb.append("Lat: ");
                    sb.append(String.valueOf(lastLocation.getLatitude()).substring(0, posLen));
                    sb.append(" Lon: ");
                    sb.append(String.valueOf(lastLocation.getLongitude()).substring(0, posLen));
                    sb.append(" Alt: ");
                    sb.append(String.valueOf(lastLocation.getAltitude()));
                    gpsStatusTextView.setText(sb.toString());
                }

                if (preferences.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {
                    // write the current reading to a CSV file
                    final String vin = preferences.getString(ConfigActivity.VEHICLE_ID_KEY, DEFAULT_VIN);
                    Map<String, String> temp = new HashMap<>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
                    csvLogWriter.writeLineCSV(reading);
                }
                commandResult.clear();
            }
            // run again after the period defined in the preferences
            new Handler().postDelayed(queueCommands, ConfigActivity.getObdUpdatePeriod(preferences));
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, "The OBD service is bound");
            isServiceBound = true;
            service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
            service.setContext(MainActivity.this);
            Log.d(TAG, "Starting live data service");
            try {
                service.startService();
                if (prerequisites) {
                    bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_connected));
                }
            }
            catch (IOException e) {
                Log.e(TAG, "Failed starting live data service");
                bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_error_connecting));
                unbindService();
            }
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        // this method is ONLY called when the connection to the service is lost unexpectedly
        // and NOT when the client unbinds (http://developer.android.com/guide/components/bound-services.html)
        // the isServiceBound member should also be set to false when we unbind from the service
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "The OBD service is unbound");
            isServiceBound = false;
        }
    };

    @Override
    public void stateUpdate(final ObdCommandJob job) {
        final String commandName = job.getCommand().getName();
        String commandResult;
        final String commandId = ObdUtil.lookUpCommand(commandName);

        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
            commandResult = job.getCommand().getResult();
            if (commandResult != null) {
                obdStatusTextView.setText(commandResult.toLowerCase());
            }
        }
        else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NOT_SUPPORTED)) {
            commandResult = getString(R.string.status_obd_no_support);
        }
        else {
            commandResult = job.getCommand().getFormattedResult();
            obdStatusTextView.setText(getString(R.string.status_obd_data));
        }

        if (vehicleViewLinearLayout.findViewWithTag(commandId) != null) {
            TextView existingTextView = (TextView) vehicleViewLinearLayout.findViewWithTag(commandId);
            existingTextView.setText(commandResult);
        }
        else {
            addTableRow(commandId, commandName, commandResult);
        }
        this.commandResult.put(commandId, commandResult);
        updateTripStatistics(job, commandId);
    }

    private static boolean checkLocationPermissions(final Context context) {
        return (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
                && (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void showPermissionDialog() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, Constants.PERMISSION_LOCATION_REQUEST_CODE);
    }

    private boolean gpsInit() throws SecurityException {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            if (checkLocationPermissions(this)) {
                locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
                if (locationProvider != null) {
                    locationManager.addGpsStatusListener(this);
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        gpsStatusTextView.setText(getString(R.string.status_gps_ready));
                        return true;
                    }
                }
            }
            else {
                showPermissionDialog();
            }
        }
        else {
            gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
            Toast.makeText(this, getResources().getText(R.string.text_no_gps_support),
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to retrieve the GPS provider");
        }
        // TODO disable GPS controls into Preferences
        return false;
    }

    private void updateTripStatistics(final ObdCommandJob job, final String commandId) {
        if (currentTrip != null) {
            if (commandId.equals(AvailableCommandNames.SPEED.toString())) {
                SpeedCommand command = (SpeedCommand) job.getCommand();
                currentTrip.setSpeedMax(command.getMetricSpeed());
            }
            else if (commandId.equals(AvailableCommandNames.ENGINE_RPM.toString())) {
                RPMCommand command = (RPMCommand) job.getCommand();
                currentTrip.setEngineRpmMax(command.getRPM());
            }
            else if (commandId.endsWith(AvailableCommandNames.ENGINE_RUNTIME.toString())) {
                RuntimeCommand command = (RuntimeCommand) job.getCommand();
                currentTrip.setEngineRuntime(command.getFormattedResult());
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate()");

        // retrieve the adapter and get the state of Bluetooth (enabled / disabled)
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDefaultIsEnabled = bluetoothAdapter.isEnabled();
        }
        else {
            Toast.makeText(this, getResources().getText(R.string.text_no_bluetooth_id),
                    Toast.LENGTH_SHORT).show();
        }

        // TODO reimplement this using the new approach
        // TODO https://developer.android.com/reference/android/hardware/SensorManager.html#getOrientation%28float[],%20float[]%29
        // get the orientation sensor
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
        if (sensors.size() > 0) {
            orientationSensor = sensors.get(0);
        }
        else {
            Toast.makeText(this, getResources().getText(R.string.text_no_orientation_sensor),
                    Toast.LENGTH_SHORT).show();
        }

        // create a log instance
        tripLog = TripLog.getInstance(this.getApplicationContext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");

        sensorManager.registerListener(compassDirectionListener, orientationSensor,
                SensorManager.SENSOR_DELAY_UI);
        // TODO use android.view.WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON
        // TODO instead of PowerManager.SCREEN_DIM_WAKE_LOCK
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, Constants.APP_NAME_TAG);

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        prerequisites = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        if (!prerequisites && preferences.getBoolean(ConfigActivity.ENABLE_BT_KEY, false)) {
            prerequisites = bluetoothAdapter != null && bluetoothAdapter.enable();
        }

        gpsInit();

        if (!prerequisites) {
            Toast.makeText(this, getResources().getText(R.string.text_bluetooth_disabled),
                    Toast.LENGTH_SHORT).show();

            // ask the user to enable Bluetooth
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);

            // in case the user chooses not to enable Bluetooth
            // the status text view will state that
            bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
        }
        else {
            bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_ok));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "onPause()");

        releaseWakeLock();
    }

    @Override
    protected void onDestroy() throws SecurityException {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");

        if (locationManager != null) {
            locationManager.removeGpsStatusListener(this);
            locationManager.removeUpdates(this);
        }

        releaseWakeLock();

        if (isServiceBound) {
            unbindService();
        }

        endTrip();

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // disable Bluetooth only if it is currently enabled
        // and it was disabled when we launched the application
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && !bluetoothDefaultIsEnabled) {
            bluetoothAdapter.disable();
        }
    }

    /**
     * If the wake lock is held, release it.
     * The lock will be held when the service is running.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, START_LIVE_DATA, 0, getString(R.string.menu_start_live_data));
        menu.add(0, STOP_LIVE_DATA, 0, getString(R.string.menu_stop_live_data));
        menu.add(0, TRIPS_LIST, 0, getString(R.string.menu_trip_list));
        menu.add(0, SETTINGS, 0, getString(R.string.menu_settings));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case STOP_LIVE_DATA:
                stopLiveData();
                return true;
            case SETTINGS:
                updateConfig();
                return true;
            case TRIPS_LIST:
                listTrips();
                return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem startItem = menu.findItem(START_LIVE_DATA);
        MenuItem stopItem = menu.findItem(STOP_LIVE_DATA);
        MenuItem settingsItem = menu.findItem(SETTINGS);

        if (service != null && service.isRunning()) {
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            settingsItem.setEnabled(false);
        }
        else {
            startItem.setEnabled(true);
            stopItem.setEnabled(false);
            settingsItem.setEnabled(true);
        }

        return true;
    }

    private void startLiveData() {
        Log.d(TAG, "Starting live data");

        // start with an empty table
        dataTableLayout.removeAllViews();
        bindService();

        currentTrip = tripLog.startTrip();
        if (currentTrip == null) {
            Toast.makeText(this, getResources().getText(R.string.text_save_trip_not_available),
                    Toast.LENGTH_SHORT).show();
        }

        // start command execution
        new Handler().post(queueCommands);

        // start GPS if it is enabled in the settings
        // otherwise display the appropriate status
        if (preferences.getBoolean(ConfigActivity.ENABLE_GPS_KEY, false)) {
            gpsStart();
        }
        else {
            gpsStatusTextView.setText(getString(R.string.status_gps_not_used));
        }

        // acquire the wake lock
        // this way the screen won't turn off until the wake lock is released
        wakeLock.acquire();

        // if full logging is enabled in the settings
        // create the logger which will log to a CSV (comma separated values) file
        if (preferences.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {
            long mils = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat(CSV_LOG_FILE_NAME_DATE_FORMAT);
            try {
                csvLogWriter = new CsvLogWriter(CSV_LOG_FILE_NAME_PREFIX
                        + sdf.format(new Date(mils)).toString()
                        + CSV_LOG_FILE_NAME_EXTENSION,
                        preferences.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                getString(R.string.default_dirname_full_logging)));
            }
            catch (FileNotFoundException | RuntimeException e) {
                Log.e(TAG, "Can't enable logging to file.", e);
            }
        }
    }

    private void stopLiveData() {
        Log.d(TAG, "Stopping live data");

        gpsStop();
        unbindService();
        endTrip();
        releaseWakeLock();

        final String developerEmail = preferences.getString(ConfigActivity.DEV_EMAIL_KEY, null);
        if (developerEmail != null) {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            ObdGatewayService.saveLogcatToFile(getApplicationContext(), developerEmail);
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            break;
                    }
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(SEND_LOGS_MESSAGE)
                    .setPositiveButton(YES_LABEL, dialogClickListener)
                    .setNegativeButton(NO_LABEL, dialogClickListener)
                    .show();
        }

        if (csvLogWriter != null) {
            csvLogWriter.close();
        }
    }

    private void updateConfig() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    private void listTrips() {
        startActivity(new Intent(this, TripListActivity.class));
    }

    private void endTrip() {
        if (currentTrip != null) {
            currentTrip.setEndDate(new Date());
            tripLog.updateRecord(currentTrip);
        }
    }

    private void addTableRow(String id, String key, String val) {
        TableRow tableRow = new TableRow(this);
        MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(TABLE_ROW_MARGIN, TABLE_ROW_MARGIN, TABLE_ROW_MARGIN, TABLE_ROW_MARGIN);
        tableRow.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setGravity(Gravity.RIGHT);
        name.setText(key + KEY_VALUE_SEPARATOR);

        TextView value = new TextView(this);
        value.setGravity(Gravity.LEFT);
        value.setText(val);
        value.setTag(id);

        tableRow.addView(name);
        tableRow.addView(value);
        dataTableLayout.addView(tableRow, params);
    }

    /**
     * Places the selected OBD commands in the job queue.
     */
    private void queueCommands() {
        if (isServiceBound) {
            for (ObdCommand command : ObdConfig.getCommands()) {
                if (preferences.getBoolean(command.getName(), true))
                    service.queueJob(new ObdCommandJob(command));
            }
        }
    }

    private void bindService() {
        if (!isServiceBound) {
            Log.d(TAG, "Binding the OBD service");
            if (prerequisites) {
                bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_connecting));
                Intent serviceIntent = new Intent(this, ObdGatewayService.class);
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
            else {
                // TODO understand why do we use a mocked service?
                bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
                Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private void unbindService() {
        if (isServiceBound) {
            if (service.isRunning()) {
                service.stopService();
                if (prerequisites) {
                    bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_ok));
                }
            }
            Log.d(TAG, "Unbinding the OBD service");
            unbindService(serviceConnection);
            isServiceBound = false;
            obdStatusTextView.setText(getString(R.string.status_obd_disconnected));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // save the location just retrieved in the lastLocation variable
        lastLocation = location;
        // add the distance just traveled to the total distance
        setDistanceTraveled(lastLocation);
        // updates the text view which displays
        // the distance traveled in this trip as calculated
        // using the gps coordinates
        NumberFormat numberFormat = new DecimalFormat(DECIMAL_FORMAT_PATTERN);
        String formattedDistance = numberFormat.format(totalTraveled / KILOMETER);
        updateTextView(distance, String.valueOf(formattedDistance)
                + getResources().getText(R.string.text_km));
    }

    private void setDistanceTraveled(Location location) {
        if (previousLocation == null) {
            previousLocation = location;
        }
        double distance = PositionUtil.calculateDistance(
                previousLocation.getLatitude(),
                previousLocation.getLongitude(),
                location.getLatitude(),
                location.getLongitude());
        previousLocation = location;
        totalTraveled = totalTraveled + distance;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                gpsStatusTextView.setText(getString(R.string.status_gps_started));
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                gpsStatusTextView.setText(getString(R.string.status_gps_fix));
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                // TODO connected or ready?
                bluetoothStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            }
            else {
                Toast.makeText(this, R.string.text_bluetooth_disabled, Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private synchronized void gpsStart() throws SecurityException {
        if (!gpsIsStarted && locationProvider != null
                && locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(locationProvider.getName(),
                    getGpsUpdatePeriod(preferences), getGpsDistanceUpdatePeriod(preferences), this);
            gpsIsStarted = true;
        } else {
            gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
        }
    }

    private synchronized void gpsStop() throws SecurityException {
        if (gpsIsStarted) {
            locationManager.removeUpdates(this);
            gpsIsStarted = false;
            gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
        }
    }

    private void updateTextView(final TextView textView, final String text) {
        new Handler().post(new Runnable() {
            public void run() {
                textView.setText(text);
            }
        });
    }
}
