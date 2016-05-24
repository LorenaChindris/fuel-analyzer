package com.ibericart.fuelanalyzer.util;

public interface Constants {

    // intent request codes
    int REQUEST_CONNECT_DEVICE_SECURE = 1;
    int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    int REQUEST_ENABLE_BLUETOOTH = 3;
    int REQUEST_LIST_DEVICES = 4;

    // extras
    String EXTRA_DEVICE_ADDRESS = "device_address";

    String NEW_LINE = "\n";

    int MAC_ADDRESS_LENGTH = 17;

    // message types sent from the BluetoothService Handler
    int MESSAGE_STATE_CHANGE = 1;
    int MESSAGE_READ = 2;
    int MESSAGE_WRITE = 3;
    int MESSAGE_DEVICE_NAME = 4;
    int MESSAGE_TOAST = 5;

    // key names received from the BluetoothService Handler
    String DEVICE_NAME = "device_name";
    String TOAST = "toast";
}
