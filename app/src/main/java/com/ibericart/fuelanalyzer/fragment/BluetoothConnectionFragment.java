package com.ibericart.fuelanalyzer.fragment;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.activity.DeviceListActivity;
import com.ibericart.fuelanalyzer.service.BluetoothService;
import com.ibericart.fuelanalyzer.util.Constants;
import com.ibericart.fuelanalyzer.util.logger.Log;

/**
 * This fragment controls Bluetooth regarding communication with other devices.
 */
public class BluetoothConnectionFragment extends Fragment {

    private static final String TAG = "BluetoothConnectionFragment";

    // layout Views
    private ListView conversationView;
    private EditText outEditText;
    private Button sendButton;

    /**
     * name of the connected device
     */
    private String connectedDeviceName = null;

    /**
     * array adapter for the conversation thread
     */
    private ArrayAdapter<String> conversationArrayAdapter;

    /**
     * string buffer for outgoing messages
     */
    private StringBuffer outStringBuffer;

    /**
     * local Bluetooth adapter
     */
    private BluetoothAdapter adapter = null;

    /**
     * member object for the Bluetooth service
     */
    private BluetoothService service = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // get local Bluetooth adapter
        adapter = BluetoothAdapter.getDefaultAdapter();

        // if the adapter is null, then Bluetooth is not supported
        if (adapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, getResources().getText(R.string.bluetooth_not_supported),
                    Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // if Bluetooth is not ON, request it to be enabled
        // setupCommunication() will then be called during onActivityResult
        if (!adapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BLUETOOTH);
            // // if Bluetooth is ON setup the communication session
        } else if (service == null) {
            setupCommunication();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (service != null) {
            service.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // performing this check inside onResume() covers the case in which Bluetooth
        // wasn't enabled during onStart(), so we were paused to enable it
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns
        if (service != null) {
            // if the state is STATE_NONE
            // we know that we haven't started already the service
            if (service.getState() == BluetoothService.STATE_NONE) {
                // start the Bluetooth service
                service.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_connection, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        conversationView = (ListView) view.findViewById(R.id.in);
        outEditText = (EditText) view.findViewById(R.id.edit_text_out);
        sendButton = (Button) view.findViewById(R.id.button_send);
    }

    /**
     * set up the UI and background operations for chat
     */
    private void setupCommunication() {
        Log.d(TAG, "setupCommunication()");

        // initialize the array adapter for the conversation thread
        conversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);
        conversationView.setAdapter(conversationArrayAdapter);

        // initialize the compose field with a listener for the return key
        outEditText.setOnEditorActionListener(writeListener);

        // initialize the send button with a listener for click events
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    sendMessage(message);
                }
            }
        });

        // initialize the BluetoothService to perform bluetooth connections
        service = new BluetoothService(getActivity(), handler);

        // initialize the buffer for outgoing messages
        outStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (adapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // check that we're actually connected before trying anything
        if (service.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // check that there's actually something to send
        if (message.length() > 0) {
            // get the message bytes and tell the BluetoothService to write
            byte[] send = message.getBytes();
            service.write(send);

            // reset the string buffer to zero and clear the edit text field
            outStringBuffer.setLength(0);
            outEditText.setText(outStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener writeListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // if the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId A string resource ID.
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle The status as a character sequence.
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The handler that gets information back from the BluetoothService
     */
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, connectedDeviceName));
                            conversationArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    conversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    conversationArrayAdapter.add(connectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    connectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.REQUEST_CONNECT_DEVICE_SECURE:
                // when DeviceListActivity returns with a device to connect to
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case Constants.REQUEST_CONNECT_DEVICE_INSECURE:
                // when DeviceListActivity returns with a device to connect to
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case Constants.REQUEST_ENABLE_BLUETOOTH:
                // when the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled
                    // set up a communication session
                    setupCommunication();
                }
                else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "Bluetooth not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establishes connection with a device.
     *
     * @param data   An {@link Intent} with a {@link Constants#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true), Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // get the device MAC address
        String address = data.getExtras().getString(Constants.EXTRA_DEVICE_ADDRESS);
        // get the BluetoothDevice object
        BluetoothDevice device = adapter.getRemoteDevice(address);
        // attempt to connect to the device
        service.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // launch the DeviceListActivity to see devices and perform a scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, Constants.REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // launch the DeviceListActivity to see devices and perform a scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, Constants.REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }
}
