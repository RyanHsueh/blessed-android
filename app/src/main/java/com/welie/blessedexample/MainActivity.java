package com.welie.blessedexample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.welie.blessed.BluetoothPeripheral;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    static final int MSG_PERIPHERAL_SCANNING = 1000;
    static final int MSG_PERIPHERAL_DISCOVERED = 1001;
    static final int MSG_PERIPHERAL_CONNECTED = 1002;
    static final int MSG_PERIPHERAL_DISCONNECTED = 1003;
    static final int MSG_PERIPHERAL_CONNECTION_FAIL = 1004;

    private final String TAG = MainActivity.class.getSimpleName();
    private TextView measurementValue;
    private TextView bleStatus;
    private TextView textDeviceInfo;
    private Button btnGetConnected;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_LOCATION_REQUEST = 2;

    private BluetoothHandler bluetoothHandler;

    private MainHandler handler = new MainHandler();
    private class MainHandler extends Handler {

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_PERIPHERAL_SCANNING:
                    bleStatus.setText("SCANNING");
                    break;
                case MSG_PERIPHERAL_DISCOVERED:
                    bleStatus.setText("DISCOVERED");
                    break;
                case MSG_PERIPHERAL_CONNECTED:
                    bleStatus.setText("CONNECTED");

                    BluetoothPeripheral peripheral = (BluetoothPeripheral)msg.obj;
                    textDeviceInfo.setText(String.format("name=%s, mac=%s, state=%d, type=%d", peripheral.getName(), peripheral.getAddress(), peripheral.getState(), peripheral.getType()));
                    break;
                case MSG_PERIPHERAL_DISCONNECTED:
                    bleStatus.setText("DISCONNECTED");
                    textDeviceInfo.setText("");
                    break;
                case MSG_PERIPHERAL_CONNECTION_FAIL:
                    bleStatus.setText("CONNECTION FAIL");
                    break;
                default:
                    bleStatus.setText("UNKNOWN");
            }

            super.handleMessage(msg);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        measurementValue = findViewById(R.id.bloodPressureValue);
        bleStatus = findViewById(R.id.bleStatus);
        textDeviceInfo = findViewById(R.id.textDeviceInfo);
        btnGetConnected = findViewById(R.id.btnGetConnected);
        btnGetConnected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothHandler != null) {
                    Toast.makeText(v.getContext(), "connected devices = " + bluetoothHandler.getConnectedDevices().size(), Toast.LENGTH_LONG).show();
                }
            }
        });

        checkBluetoothEnable();

        if(hasPermissions()) {
            initBluetoothHandler();
        }

        if (bluetoothHandler != null) {
            bluetoothHandler.setMainHandler(handler);
        }
    }

    private void checkBluetoothEnable() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) return;

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void initBluetoothHandler()
    {
        bluetoothHandler = BluetoothHandler.getInstance(getApplicationContext());
        registerReceiver(bloodPressureDataReceiver, new IntentFilter( "BluetoothMeasurement" ));
        registerReceiver(temperatureDataReceiver, new IntentFilter( "TemperatureMeasurement" ));
        registerReceiver(heartRateDataReceiver, new IntentFilter( "HeartRateMeasurement" ));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothHandler != null) bluetoothHandler.release();
        unregisterReceiver(bloodPressureDataReceiver);
        unregisterReceiver(temperatureDataReceiver);
        unregisterReceiver(heartRateDataReceiver);
    }

    private final BroadcastReceiver bloodPressureDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BloodPressureMeasurement measurement = (BloodPressureMeasurement) intent.getSerializableExtra("BloodPressure");
            if (measurement == null) return;

            DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
            String formattedTimestamp = df.format(measurement.timestamp);
            measurementValue.setText(String.format(Locale.ENGLISH, "%.0f/%.0f %s, %.0f bpm\n%s", measurement.systolic, measurement.diastolic, measurement.isMMHG ? "mmHg" : "kpa", measurement.pulseRate, formattedTimestamp));
        }
    };

    private final BroadcastReceiver temperatureDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TemperatureMeasurement measurement = (TemperatureMeasurement) intent.getSerializableExtra("Temperature");
            if (measurement == null) return;

            DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
            String formattedTimestamp = df.format(measurement.timestamp);
            measurementValue.setText(String.format(Locale.ENGLISH, "%.1f %s (%s)\n%s", measurement.temperatureValue, measurement.unit == TemperatureUnit.Celsius ? "celcius" : "fahrenheit", measurement.type, formattedTimestamp));
        }
    };

    private final BroadcastReceiver heartRateDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            HeartRateMeasurement measurement = (HeartRateMeasurement) intent.getSerializableExtra("HeartRate");
            if (measurement == null) return;

            measurementValue.setText(String.format(Locale.ENGLISH, "%d bpm", measurement.pulse));
        }
    };

    private boolean hasPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST);
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, ACCESS_LOCATION_REQUEST);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case ACCESS_LOCATION_REQUEST:
                if(grantResults.length > 0) {
                    if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        initBluetoothHandler();
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }
}
