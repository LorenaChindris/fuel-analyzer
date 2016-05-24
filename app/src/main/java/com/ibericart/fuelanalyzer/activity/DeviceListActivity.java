package com.ibericart.fuelanalyzer.activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.util.Constants;

import java.util.Set;

public class DeviceListActivity extends Activity {

    private static final String TAG = "DeviceListActivity";

    private BluetoothAdapter adapter;

    private ArrayAdapter<String> pairedDevicesAdapter;
    private ArrayAdapter<String> newDevicesAdapter;

    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // restore any saved state
        super.onCreate(savedInstanceState);

        // set up the window
        // request the indeterminate progress feature
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        // set the content view
        setContentView(R.layout.list_devices);

        // set the result as CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // initialize the button to perform device discovery
        scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addPairedDevices();
                discoverDevices();
                v.setVisibility(View.GONE);
            }
        });

        // instantiate the array adapters
        // already paired devices
        pairedDevicesAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        // newly discovered devices
        newDevicesAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // retrieve the ListView for paired devices
        // set its adapter
        // set the listener for clicking on a device
        ListView pairedDevicesListView = (ListView) findViewById(R.id.paired_devices);
        pairedDevicesListView.setAdapter(pairedDevicesAdapter);
        pairedDevicesListView.setOnItemClickListener(deviceClickListener);

        // retrieve the ListView for newly discovered devices
        // set its adapter
        // set the listener for clicking on a device
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(newDevicesAdapter);
        newDevicesListView.setOnItemClickListener(deviceClickListener);

        // register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(broadcastReceiver, filter);

        // register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(broadcastReceiver, filter);

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
    protected void onStart() {
        super.onStart();

        if (adapter != null && !adapter.isEnabled()) {
            // prompt the user to enable Bluetooth
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, Constants.REQUEST_ENABLE_BLUETOOTH);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // as device discovery is an expensive task
        // make sure it is turned off when this activity is destroyed
        if (adapter != null) {
            adapter.cancelDiscovery();
        }

        // unregister broadcast receivers
        this.unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_ENABLE_BLUETOOTH) {
            // when the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                Log.d(TAG, "Bluetooth is now enabled");
                // enable the Scan button
                scanButton.setEnabled(true);
            } else {
                // the user didn't enable Bluetooth or an error occurred
                Log.d(TAG, "Bluetooth not enabled");
                scanButton.setEnabled(false);
            }
        }
    }

    private void addPairedDevices() {
        // display the already paired devices
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

        // if there are any paired devices add them to the relevant ArrayAdapter
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + Constants.NEW_LINE + device.getAddress());
            }
        }
        else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            pairedDevicesAdapter.add(noDevices);
        }
    }

    /**
     * Performs device discovery using the BluetoothAdapter previously acquired.
     */
    private void discoverDevices() {
        Log.d(TAG, "discoverDevices()");

        // indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // display the subtitle for new devices
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // if we're already discovering, stop it
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        // request discovery from BluetoothAdapter
        adapter.startDiscovery();
    }

    /**
     * The BroadcastReceiver which adds the newly discovered devices to the relevant ArrayAdapter
     * and updates the activity's title once the discovery process is finished.
     */
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // when discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // if it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    newDevicesAdapter.add(device.getName() + Constants.NEW_LINE + device.getAddress());
                }
                // when discovery is finished, change the Activity title
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (newDevicesAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    newDevicesAdapter.add(noDevices);
                }
            }
        }
    };

    /**
     * Defines the listener which fires when the user clicks a device.
     */
    private final AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // cancel device discovery when we are about to connect to a device
            // we do this because it's an expensive operation and it's not needed anymore
            adapter.cancelDiscovery();

            // to connect to a Bluetooth device we need it's MAC address
            // we retrieve it by parsing the device's name as displayed in the list
            // we need the last 17 chars in the View
            String address = null;
            String info = ((TextView) v).getText().toString();
            if (!info.equals(getResources().getText(R.string.none_found).toString())
                    && !info.equals(getResources().getText(R.string.none_paired))) {
                address = info.substring(info.length() - Constants.MAC_ADDRESS_LENGTH);
            }

            // if we selected a valid device
            if (address != null) {
                // create the result Intent and add the MAC address as extra information
                Intent intent = new Intent();
                intent.putExtra(Constants.EXTRA_DEVICE_ADDRESS, address);

                // set result as OK, passing the new intent and finish this activity
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        }
    };
}
