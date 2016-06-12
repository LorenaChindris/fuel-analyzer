package com.ibericart.fuelanalyzer.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.ibericart.fuelanalyzer.activity.MainActivity;
import com.google.inject.Inject;
import com.ibericart.fuelanalyzer.io.ObdCommandJob;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import roboguice.service.RoboService;

/**
 * Sets a framework for the service classes.
 * <br />
 * Uses code from https://github.com/pires/android-obd-reader
 */
public abstract class AbstractService extends RoboService {

    public static final int NOTIFICATION_ID = 1;

    private static final String TAG = AbstractService.class.getName();

    private final IBinder binder = new AbstractServiceBinder();

    @Inject
    protected NotificationManager notificationManager;

    protected Context context;

    protected boolean isRunning = false;

    protected Long queueCounter = 0L;

    protected BlockingQueue<ObdCommandJob> jobsQueue = new LinkedBlockingQueue<>();

    // run the executeQueue in a different thread to lighten the UI thread
    Thread serviceThread = new Thread(new Runnable() {

        @Override
        public void run() {
            try {
                executeQueue();
            }
            catch (InterruptedException e) {
                serviceThread.interrupt();
            }
        }
    });

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating service");
        serviceThread.start();
        Log.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying service");
        notificationManager.cancel(NOTIFICATION_ID);
        serviceThread.interrupt();
        Log.d(TAG, "Service destroyed");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean queueEmpty() {
        return jobsQueue.isEmpty();
    }

    /**
     * This method will add a job to the queue while setting its id to the
     * internal queue counter.
     *
     * @param job the job to queue.
     */
    public void queueJob(ObdCommandJob job) {
        queueCounter++;
        Log.d(TAG, "Adding job [" + queueCounter + "] to queue");
        job.setId(queueCounter);
        try {
            jobsQueue.put(job);
            Log.d(TAG, "Job [" + queueCounter + "] queued successfully");
        }
        catch (InterruptedException e) {
            job.setState(ObdCommandJob.ObdCommandJobState.QUEUE_ERROR);
            Log.e(TAG, "Failed to queue job [" + queueCounter + "]");
        }
    }

    /**
     * Show a notification while this service is running.
     */
    protected void showNotification(String contentTitle, String contentText, int icon, boolean ongoing,
                                    boolean notify, boolean vibrate) {
        final PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), 0);
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilder.setContentTitle(contentTitle)
                .setContentText(contentText).setSmallIcon(icon)
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis());
        // can cancel?
        if (ongoing) {
            notificationBuilder.setOngoing(true);
        }
        else {
            notificationBuilder.setAutoCancel(true);
        }
        if (vibrate) {
            notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
        }
        if (notify) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    public void setContext(Context c) {
        context = c;
    }

    abstract protected void executeQueue() throws InterruptedException;

    abstract public void startService() throws IOException;

    abstract public void stopService();

    public class AbstractServiceBinder extends Binder {

        public AbstractService getService() {
            return AbstractService.this;
        }
    }
}
