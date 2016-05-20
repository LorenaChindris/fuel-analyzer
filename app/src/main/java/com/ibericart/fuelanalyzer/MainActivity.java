package com.ibericart.fuelanalyzer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_LIST_DEVICES = 1;

    private Button scanButton;

    private BluetoothAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set the content view
        setContentView(R.layout.activity_main);

        // initialize the button to perform device discovery
        scanButton = (Button) findViewById(R.id.button_devices);
        scanButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // launch the ListDevicesActivity to scan for Bluetooth devices
                Intent intent = new Intent(MainActivity.this, ListDevicesActivity.class);
                startActivityForResult(intent, REQUEST_LIST_DEVICES);
            }
        });

        // get the Bluetooth adapter
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            // the device doesn't support Bluetooth
            // notify the user and finish the activity
            Toast.makeText(getApplicationContext(),
                    R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            scanButton.setEnabled(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LIST_DEVICES) {
            if (resultCode == Activity.RESULT_OK) {
                connect(data);
            }
        }
    }

    /**
     * connect to the selected device
     *
     * @param data An {@link Intent} with a {@link ListDevicesActivity#EXTRA_DEVICE_ADDRESS} extra.
     */
    private void connect(Intent data) {
        // get the MAC address needed to connect to the device
        String address = data.getExtras().getString(ListDevicesActivity.EXTRA_DEVICE_ADDRESS);
        // get the BluetoothDevice object
        BluetoothDevice device = adapter.getRemoteDevice(address);
    }
}
