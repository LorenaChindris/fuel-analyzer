package com.ibericart.fuelanalyzer.service;

import android.util.Log;

import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.ObdResetCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;
import com.ibericart.fuelanalyzer.activity.MainActivity;
import com.ibericart.fuelanalyzer.io.ObdCommandJob;
import com.ibericart.fuelanalyzer.io.ObdCommandJob.ObdCommandJobState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * This service is a mocked version of {@link ObdService}.
 * <p />
 * It will serve as a repository of ObdCommandJobs and at the same
 * time the application state-machine.
 * <br />
 * Uses code from https://github.com/pires/android-obd-reader
 */
public class MockObdService extends AbstractService {

    private static final String TAG = MockObdService.class.getName();
    public static final int OBD_COMMAND_TIMEOUT = 62;
    public static final String MOCKED_DATA = "41 00 00 00>41 00 00 00>41 00 00 00>";

    public void startService() {
        Log.d(TAG, "Starting the mocked service");

        // configure the connection
        Log.d(TAG, "Queueing jobs for connection configuration");
        queueJob(new ObdCommandJob(new ObdResetCommand()));
        queueJob(new ObdCommandJob(new EchoOffCommand()));

        // will send second-time based on tests
        // TODO this can be done without having to queue jobs by just issuing
        // TODO command.run(), command.getResult() and validate the result
        queueJob(new ObdCommandJob(new EchoOffCommand()));

        queueJob(new ObdCommandJob(new LineFeedOffCommand()));
        queueJob(new ObdCommandJob(new TimeoutCommand(OBD_COMMAND_TIMEOUT)));

        // set the OBD protocol to AUTO
        queueJob(new ObdCommandJob(new SelectProtocolCommand(ObdProtocols.AUTO)));

        // job for returning dummy data
        queueJob(new ObdCommandJob(new AmbientAirTemperatureCommand()));

        queueCounter = 0L;
        Log.d(TAG, "Initialization jobs queued");

        isRunning = true;
    }

    /**
     * Runs the queue until the service is stopped.
     */
    protected void executeQueue() {
        Log.d(TAG, "Executing queue");
        while (!Thread.currentThread().isInterrupted()) {
            ObdCommandJob job = null;
            try {
                job = jobsQueue.take();
                Log.d(TAG, "Taking job [" + job.getId() + "] from the queue");
                if (job.getState().equals(ObdCommandJobState.NEW)) {
                    Log.d(TAG, "Job is in state NEW; run it");
                    job.setState(ObdCommandJobState.RUNNING);
                    Log.d(TAG, job.getCommand().getName());
                    job.getCommand().run(new ByteArrayInputStream(MOCKED_DATA.getBytes()),
                            new ByteArrayOutputStream());
                }
                else {
                    // log that the job wasn't in state NEW
                    Log.e(TAG, "Job state wasn't in state NEW; it shouldn't be in queue");
                }
            }
            catch (InterruptedException i) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                Log.e(TAG, "Error while executing the commands", e);
                if (job != null) {
                    job.setState(ObdCommandJobState.EXECUTION_ERROR);
                }
                Log.e(TAG, "Failed to run command. -> " + e.getMessage());
            }

            if (job != null) {
                Log.d(TAG, "Job finished");
                job.setState(ObdCommandJobState.FINISHED);
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
     * Stops the queue processing.
     */
    public void stopService() {
        Log.d(TAG, "Stopping service");
        notificationManager.cancel(NOTIFICATION_ID);
        jobsQueue.clear();
        isRunning = false;
        // kill the service
        stopSelf();
    }
}
