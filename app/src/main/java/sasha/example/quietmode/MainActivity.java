package sasha.example.quietmode;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.util.Log;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BbrySilencer";
    private static final boolean buildVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

    private NotificationManager mNotificationManager;

    private TextView mFace;
    private Button mGrantBtn;
    private Switch mScanToggle;

    BroadcastReceiver mUIUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!TextUtils.equals(action, ProximityScanningService.UI_UPDATE_BROADCAST)) {
                Log.e(TAG, "Receiving intent action not registered for");
                return;
            }
            boolean isStopping =
                    intent.getBooleanExtra(ProximityScanningService.EXTRA_IS_STOPPED, true);
            mScanToggle.setChecked(!isStopping);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFace = (TextView) findViewById(R.id.face);
        mGrantBtn = (Button) findViewById(R.id.permission_dialog_btn);
        mGrantBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showPermissionsDialog();
            }
        });
        mScanToggle = (Switch) findViewById(R.id.scan_toggle);
        mScanToggle.setChecked(isServiceRunning());
        mScanToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i("ProximityScanningService", "Switch event: " + isChecked);
                if (isChecked) {
                    createScanningService();
                } else {
                    stopScanningService();
                }
            }
        });

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        SensorManager sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Sensor> accelerometerSensorList = sensorMgr.getSensorList(Sensor.TYPE_ACCELEROMETER);
        List<Sensor> proximitySensorList = sensorMgr.getSensorList(Sensor.TYPE_PROXIMITY);

        Sensor accelerometerSensor = null;
        Sensor proximitySensor = null;
        if (!accelerometerSensorList.isEmpty()) {
            accelerometerSensor = accelerometerSensorList.get(0);
        }
        if (!proximitySensorList.isEmpty()) {
            proximitySensor = proximitySensorList.get(0);
        }

        // check if all necessary features are supported on the device
        if (accelerometerSensor == null || proximitySensor == null || !buildVersion) {
            Log.e(TAG, "Finishing activity, necessary hardware/permissions are not supported");

            if (isServiceRunning()) {
                stopScanningService();
            }
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mUIUpdateReceiver, new IntentFilter(ProximityScanningService.UI_UPDATE_BROADCAST));

        if (!mNotificationManager.isNotificationPolicyAccessGranted()) {
            showPermissionsDialog();

            if (isServiceRunning()) {
                stopScanningService();
            }
        } else {
            setActivatedView();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(mUIUpdateReceiver);
    }

    private void showPermissionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyDialogTheme);

        final AlertDialog alertDialog = builder.setTitle(R.string.require_permission_title)
                .setMessage(R.string.require_permission_msg)
                .setPositiveButton(R.string.permission_grant, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // send intent to grant Do not disturb permissions for app
                        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.permission_deny, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGrantBtn.setVisibility(View.VISIBLE);
                        mScanToggle.setVisibility(View.GONE);
                        mFace.setText(R.string.deactivated_str);
                        mFace.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorPrimaryDark, null));
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorPrimaryDark, null));
            }
        });
        alertDialog.show();
    }

    private void setActivatedView() {
        mFace.setText(R.string.activated_str);
        mFace.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        mGrantBtn.setVisibility(View.GONE);
        mScanToggle.setVisibility(View.VISIBLE);
    }

    private void createScanningService() {
        Intent intent = new Intent(this, ProximityScanningService.class);
        startForegroundService(intent);
    }

    private void stopScanningService() {
        Intent intent = new Intent(this, ProximityScanningService.class);
        stopService(intent);

    }

    private boolean isServiceRunning() {
        SharedPreferences prefs =
                getSharedPreferences(ProximityScanningService.SERVICE_PREF, MODE_PRIVATE);
        return prefs.getBoolean(ProximityScanningService.PREF_KEY, false);
    }
}
