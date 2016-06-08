package com.ibericart.fuelanalyzer.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.ObdResetCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;
import com.github.pires.obd.exceptions.UnsupportedCommandException;
import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.activity.ConfigActivity;
import com.ibericart.fuelanalyzer.activity.MainActivity;
import com.ibericart.fuelanalyzer.io.BluetoothManager;
import com.ibericart.fuelanalyzer.io.ObdCommandJob;
import com.ibericart.fuelanalyzer.io.ObdCommandJob.ObdCommandJobState;
import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;

/**
 * This service is primarily responsible for establishing and maintaining a
 * permanent connection between the device where the application runs and
 * an OBD Bluetooth interface.
 * <p />
 * Secondarily, it will serve as a repository of ObdCommandJobs and at the same
 * time the application state-machine.
 * <br />
 * Uses code from https://github.com/pires/android-obd-reader
 */
public class ObdGatewayService extends AbstractGatewayService {

    private static final String TAG = ObdGatewayService.class.getName();

    private static final String EMPTY_STRING = "";
    private static final int OBD_COMMAND_TIMEOUT = 62;
    private static final String DEFAULT_OBD_PROTOCOL = "AUTO";
    private static final String EMAIL_CONTENT_TYPE = "text/plain";
    private static final String DEVELOPER_EMAIL_SUBJECT = "Fuel Analyzer - OBD Reader Debug Logs";
    private static final String FUEL_ANALYZER_LOGCAT = "FuelAnalyzer_logcat_";
    private static final String FUEL_ANALYZER_LOGCAT_EXTENSION = ".txt";
    private static final String LOGS_DIRECTORY = "FuelAnalyzerLogs";
    private static final String PICK_AN_EMAIL_PROVIDER = "Pick an Email provider";
    private static final String LOGCAT_COMMAND = "logcat -f ";

    @Inject
    SharedPreferences preferences;

    private BluetoothDevice device = null;
    private BluetoothSocket socket = null;

    public void startService() throws IOException {
        Log.d(TAG, "Starting service");

        // get the remote Bluetooth device
        final String remoteDevice = preferences.getString(ConfigActivity.BLUETOOTH_LIST_KEY, null);
        if (remoteDevice == null || EMPTY_STRING.equals(remoteDevice)) {
            Toast.makeText(context, getString(R.string.text_bluetooth_nodevice),
                    Toast.LENGTH_SHORT).show();
            // log error
            Log.e(TAG, "No Bluetooth device has been selected");
            // TODO kill this service gracefully
            stopService();
            throw new IOException();
        }
        else {
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            device = bluetoothAdapter.getRemoteDevice(remoteDevice);

            /*
             * Establish Bluetooth connection
             *
             * Because discovery is a heavyweight procedure for the Bluetooth adapter,
             * this method should always be called before attempting to connect to a
             * remote device with connect(). Discovery is not managed by the Activity,
             * but is run as a system service, so an application should always call
             * cancel discovery even if it did not directly request a discovery, just to
             * be sure. If Bluetooth state is not STATE_ON, this API will return false.
             *
             * see
             * http://developer.android.com/reference/android/bluetooth/BluetoothAdapter
             * .html#cancelDiscovery()
             */
            Log.d(TAG, "Stopping Bluetooth discovery");
            bluetoothAdapter.cancelDiscovery();

            showNotification(getString(R.string.notification_action), getString(R.string.service_starting),
                    R.drawable.ic_btcar, true, true, false);

            try {
                startObdConnection();
            }
            catch (Exception e) {
                Log.e(TAG, "There was an error while establishing connection", e);
                // in case of failure stop the service
                stopService();
                throw new IOException();
            }
            showNotification(getString(R.string.notification_action), getString(R.string.service_started),
                    R.drawable.ic_btcar, true, true, false);
        }
    }

    /**
     * Start and configure the connection to the OBD interface.
     * <p/>
     * See http://stackoverflow.com/questions/18657427/ioexception-read-failed-socket-might-closed-bluetooth-on-android-4-3/18786701#18786701
     *
     * @throws IOException
     */
    private void startObdConnection() throws IOException {
        Log.d(TAG, "Starting OBD connection");
        isRunning = true;
        try {
            socket = BluetoothManager.connect(device);
        }
        catch (Exception e) {
            Log.e(TAG, "There was an error while establishing Bluetooth connection. Stopping app", e);
            stopService();
            throw new IOException();
        }

        // configure the connection
        Log.d(TAG, "Queueing jobs for connection configuration");
        queueJob(new ObdCommandJob(new ObdResetCommand()));
        
        // give the adapter enough time to reset before sending the commands
        // otherwise the first startup commands could be ignored
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            Log.e(TAG, "Failed to pause the thread", e);
        }
        
        queueJob(new ObdCommandJob(new EchoOffCommand()));

        // will send second-time based on tests
        // TODO this can be done without having to queue jobs by just issuing
        // TODO command.run(), command.getResult() and validate the result
        queueJob(new ObdCommandJob(new EchoOffCommand()));

        queueJob(new ObdCommandJob(new LineFeedOffCommand()));
        queueJob(new ObdCommandJob(new TimeoutCommand(OBD_COMMAND_TIMEOUT)));

        // get protocol from preferences
        final String protocol = preferences.getString(ConfigActivity.PROTOCOLS_LIST_KEY, DEFAULT_OBD_PROTOCOL);
        queueJob(new ObdCommandJob(new SelectProtocolCommand(ObdProtocols.valueOf(protocol))));

        // job for returning dummy data
        queueJob(new ObdCommandJob(new AmbientAirTemperatureCommand()));

        queueCounter = 0L;
        Log.d(TAG, "Initialization jobs queued");
    }

    /**
     * This method will add a job to the queue while setting its id to the
     * internal queue counter.
     *
     * @param job the job to queue.
     */
    @Override
    public void queueJob(ObdCommandJob job) {
        // enforce the imperial units option
        job.getCommand().useImperialUnits(preferences.getBoolean(ConfigActivity.IMPERIAL_UNITS_KEY, false));
        // queue the job
        super.queueJob(job);
    }

    /**
     * Runs the queue until the service is stopped.
     */
    protected void executeQueue() throws InterruptedException {
        Log.d(TAG, "Executing queue");
        while (!Thread.currentThread().isInterrupted()) {
            ObdCommandJob job = null;
            try {
                // take the next job from the queue
                job = jobsQueue.take();

                // display information about the job in the log
                Log.d(TAG, "Taking job [" + job.getId() + "] from the queue");

                if (job.getState().equals(ObdCommandJobState.NEW)) {
                    Log.d(TAG, "Job is in state NEW; run it");
                    job.setState(ObdCommandJobState.RUNNING);
                    if (socket.isConnected()) {
                        job.getCommand().run(socket.getInputStream(), socket.getOutputStream());
                    }
                    else {
                        job.setState(ObdCommandJobState.EXECUTION_ERROR);
                        Log.e(TAG, "The command cannot be run on a closed socket");
                    }
                }
                else {
                    // log that the job wasn't in state NEW
                    Log.e(TAG, "The job wasn't in state NEW; it shouldn't be in the queue");
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (UnsupportedCommandException e) {
                if (job != null) {
                    job.setState(ObdCommandJobState.NOT_SUPPORTED);
                }
                Log.d(TAG, "Command not supported", e);
            }
            catch (Exception e) {
                if (job != null) {
                    job.setState(ObdCommandJobState.EXECUTION_ERROR);
                }
                Log.e(TAG, "Failed to run the command", e);
            }

            if (job != null) {
                final ObdCommandJob job2 = job;
                ((MainActivity) context).runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        ((MainActivity) context).stateUpdate(job2);
                    }
                });
            }
        }
    }

    /**
     * Stops the OBD connection and queue processing.
     */
    public void stopService() {
        Log.d(TAG, "Stopping service");
        notificationManager.cancel(NOTIFICATION_ID);
        jobsQueue.clear();
        isRunning = false;
        if (socket != null) {
            // close the socket
            try {
                socket.close();
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        // kill the service
        stopSelf();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public static void saveLogcatToFile(Context context, String developerEmail) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        emailIntent.setType(EMAIL_CONTENT_TYPE);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{developerEmail});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, DEVELOPER_EMAIL_SUBJECT);

        StringBuilder sb = new StringBuilder();
        sb.append("\nManufacturer: ").append(Build.MANUFACTURER);
        sb.append("\nModel: ").append(Build.MODEL);
        sb.append("\nRelease: ").append(Build.VERSION.RELEASE);

        emailIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());

        String fileName = FUEL_ANALYZER_LOGCAT + System.currentTimeMillis() + FUEL_ANALYZER_LOGCAT_EXTENSION;
        File sdCard = Environment.getExternalStorageDirectory();
        File directory = new File(sdCard.getAbsolutePath() + File.separator + LOGS_DIRECTORY);
        if (directory.mkdirs()) {
            File outputFile = new File(directory, fileName);
            Uri uri = Uri.fromFile(outputFile);
            emailIntent.putExtra(Intent.EXTRA_STREAM, uri);

            Log.d(TAG, "Saving logcat to " + outputFile);
            // emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(emailIntent, PICK_AN_EMAIL_PROVIDER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            try {
                @SuppressWarnings("unused")
                Process process = Runtime.getRuntime().exec(LOGCAT_COMMAND + outputFile.getAbsolutePath());
            }
            catch (IOException e) {
                Log.e(TAG, "Error while saving logcat", e);
            }
        }
    }
}
