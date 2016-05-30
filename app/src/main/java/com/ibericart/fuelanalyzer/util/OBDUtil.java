package com.ibericart.fuelanalyzer.util;

import com.github.pires.obd.enums.AvailableCommandNames;

/**
 * Contains utility methods for OBD related tasks.
 */
public class ObdUtil {

    public static String lookUpCommand(String commandName) {
        for (AvailableCommandNames item : AvailableCommandNames.values()) {
            if (item.getValue().equals(commandName)) {
                return item.name();
            }
        }
        return commandName;
    }
}
