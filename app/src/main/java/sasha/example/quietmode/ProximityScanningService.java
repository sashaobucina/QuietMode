package sasha.example.quietmode;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

public class ProximityScanningService extends Service {
    private static final String TAG = "ProximityScanningService";
    private static final String PACKAGE_NAME = "sasha.example.quietmode";

    public static final String UI_UPDATE_BROADCAST = PACKAGE_NAME + ".UPDATE_UI_BROADCAST";
    public static final String EXTRA_IS_STOPPED = PACKAGE_NAME + ".IS_STOPPED";
    public static final String ACTION_STOP_SCANNING = PACKAGE_NAME + ".STOP_SCANNING";

    private static final String CHANNEL_ID = PACKAGE_NAME + ".proximity_scanner_channel";
    private static final String CHANNEL_NAME = "Proximity Scanner Channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String SERVICE_PREF = "sasha.example.quietmode.ServicePreference";
    public static final String PREF_KEY = "isRunningKey";

    private static final int Z_INDEX = 2;

    private NotificationManager mNotificationManager;
    private SensorManager mSensorMgr;
    private Sensor mAccelerometerSensor = null;
    private Sensor mProximitySensor = null;

    private boolean mAccelerometerCondition = false;
    private boolean mProximityCondition = false;
    private int mLastNotificationFilter = NotificationManager.INTERRUPTION_FILTER_ALL;

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> accelerometerList = mSensorMgr.getSensorList(Sensor.TYPE_ACCELEROMETER);
        List<Sensor> proximityList = mSensorMgr.getSensorList(Sensor.TYPE_PROXIMITY);

        if (accelerometerList != null && !accelerometerList.isEmpty()) {
            mAccelerometerSensor = accelerometerList.get(0);
        }
        if (proximityList != null && !proximityList.isEmpty()) {
            mProximitySensor = proximityList.get(0);
        }

        if (mAccelerometerSensor == null || mProximitySensor == null) {
            stopForeground(true);
            stopSelf();
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        populatePreference(true);

        if (TextUtils.equals(ACTION_STOP_SCANNING, intent.getAction())) {
            sendUIUpdateRequest(true);
            stopForeground(true);
            stopSelf();
        } else {
            // register listeners
            mSensorMgr.registerListener(mAccelerometerListener, mAccelerometerSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mSensorMgr.registerListener(mAccelerometerListener, mProximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        return START_STICKY;
    }


    private Notification buildForegroundNotification() {
        createNotificationChannel();

        Intent stopIntent = new Intent(this, ProximityScanningService.class);
        stopIntent.setAction(ACTION_STOP_SCANNING);
        PendingIntent stopScanningPendingIntent =
                PendingIntent.getForegroundService(this, 0, stopIntent, 0);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent =
                PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setChannelId(CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Proximity Scanner")
                .setContentText(getString(R.string.notification_message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.stat_sys_upload, getString(R.string.turn_off),
                        stopScanningPendingIntent);

        Notification notification = builder.build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);

        return notification;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        populatePreference(false);
        mSensorMgr.unregisterListener(mAccelerometerListener);
    }

    private void createNotificationChannel() {
        NotificationManager notificationMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );

        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setSound(null, null);

        notificationMgr.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Wont be called since this service is not bound to
        return null;
    }

    private SensorEventListener mAccelerometerListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Sensor sensor = event.sensor;
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float zValue = event.values[Z_INDEX];
                mAccelerometerCondition = ((zValue <= -9) && (zValue >= -10));
            } else if (sensor.getType() == Sensor.TYPE_PROXIMITY) {
                float distance = event.values[0];
                mProximityCondition = (distance == 0.0);
            } else {
                // ignore this event
                Log.e(TAG, "Incorrect sensor change event, shouldn't be listening to this type");
                return;
            }

            int notificationFilter = mNotificationManager.getCurrentInterruptionFilter();
            if (mProximityCondition && mAccelerometerCondition) {
                if (notificationFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
                    turnOnDoNotDisturb(notificationFilter);
                }
            } else {
                if (notificationFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    turnOffDonNotDisturb();
                }
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO: Auto-generated method stub
        }
    };

    private void turnOnDoNotDisturb(int notificationFilter) {
        Log.i(TAG, "Turning on mute mode");
        mLastNotificationFilter = notificationFilter;
        mNotificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
    }

    private void turnOffDonNotDisturb() {
        Log.i(TAG, "Turning off mute mode");
        mNotificationManager.setInterruptionFilter(mLastNotificationFilter);
    }

    private void populatePreference(boolean isRunning) {
        SharedPreferences.Editor editor = getSharedPreferences(SERVICE_PREF, MODE_PRIVATE).edit();
        editor.putBoolean(PREF_KEY, isRunning);
        editor.apply();
    }

    private void sendUIUpdateRequest(boolean isStopping) {
        Intent intent = new Intent(UI_UPDATE_BROADCAST);
        intent.putExtra(EXTRA_IS_STOPPED, isStopping);
        sendBroadcast(intent);
    }
}
