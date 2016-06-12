package com.ibericart.fuelanalyzer.util;

import android.util.Log;

import com.github.pires.obd.enums.AvailableCommandNames;

import java.util.Map;

/**
 * Contains utility methods for OBD related tasks.
 */
public class ObdUtil {

    private static final String TAG = ObdUtil.class.getName();

    // prepare the constants
    // r_d
    private static final double DYNAMIC_ROLLING_RADIUS = 308;
    // i_0
    private static final double FINAL_DRIVE_RATIO = 4.36;

    // i_c = 3600/1000 * PI/30 * r_d/i_0
    private static final double i_c = (3600 * Math.PI * DYNAMIC_ROLLING_RADIUS)
            / (1000 * 30 * FINAL_DRIVE_RATIO);

    private static final double[] TRANSMISSION_RATIO_MIN = new double[] {0, 3.3, 1.7, 1.2, 0.95, 0.7};
    private static final double[] TRANSMISSION_RATIO_MAX = new double[] {0, 3.8, 2.2, 1.5, 1.1, 0.9};

    // engine speed that achieves maximum torque
    private static final int MAX_TORQUE_RPM = 5000;

    // L_iconst
    private static final int[] MAX_LOAD_CONSTANT_SPEED = new int[] {0, 0, 25, 40, 50, 50};
    // O_iconst
    private static final int[] MAX_THROTTLE_CONSTANT_SPEED = new int[] {0, 0, 30, 23, 30, 30};

    // (L/O)_imin
    private static final double[] MIN_LOAD_THROTTLE = new double[] {0, 0, 0, 2, 2.2, 2.2};

    // L_amax
    private static final int MAX_LOAD_MAX_ACCELERATION = 70;
    // O_amax
    private static final int MIN_THROTTLE_MAX_ACCELERATION = 70;

    // O_0
    private static final double INITIAL_THROTTLE_POSITION = 11.5;

    private static final int[] MIN_RPM_NEEDED = new int[] {0, 0, 1000, 1500, 1800, 1800};

    // i_i
    private static final double[] GEAR_RATIO = new double[] {0, 3.545, 1.913, 1.31, 1.027, 0.85};

    public static String lookUpCommand(String commandName) {
        for (AvailableCommandNames item : AvailableCommandNames.values()) {
            if (item.getValue().equals(commandName)) {
                return item.name();
            }
        }
        return commandName;
    }

    public void mockData() {
        // mock OBD data such as MAF in case the command doesn't return a real result
    }

    private static double readDouble(Map<String, String> readings, String key) {
        double result = 0.0;
        if (readings != null && !readings.isEmpty()) {
            String value = readings.get(key);
            if (value != null) {
                try {
                    result = Double.valueOf(value);
                }
                catch (NumberFormatException e) {
                    Log.e(TAG, "Could not parse double for key = " + key + "and value = " + value);
                }
            }
            else {
                Log.e(TAG, "No value for key = " + key);
            }
        }
        else {
            Log.e(TAG, "The readings map is null or empty");
        }
        return result;
    }

    public static int evaluateOptimalGear(Map<String, String> readings) {
        // a1: the group of parameters taken from the OBD reading - vehicle operation parameters
        // in real time (L, O, n, v)

        // L - calculated load value
        // O - throttle opening
        // n - engine speed (RPM - rotations per minute)
        // v - vehicle speed

        // b1: the group of values declared by the manufacturer, considered not derived constants
        // (i_i, i_0, r_d)

        // i_i - gear ratios
        // i_0 - final drive ratio
        // r_d - dynamic rolling radius

        // b2: the result of the model preparatory phase (L_iconst, O_iconst, L_amax, O_amax,
        // (L/O)i_min, i_c, [i_imin, i_imax]) - not declared parameters

        // n_imin - minimum engine speed for each gear
        // n_Memax - engine speed at which the maximum torque is reached
        // O_0 - initial throttle position (corresponding to the initial position of the accelerator
        // pedal)
        // L_iconst - maximum calculated load value - constant speed driving mode in each gear
        // O_iconst - maximum value of throttle opening - constant speed driving mode in each gear
        // L_amax - maximum calculated load value - maximum acceleration mode
        // O_amax - minimum throttle opening value - maximum acceleration mode
        // limit values of derived value L/O for each gear for acceleration modes which exclude top
        // gear (L/O)_imin
        // i_c - engine speed - vehicle speed ratio relative to transmission ratio
        // [i_imin, i_imax] - transmission ratios intervals for determining current gear

        // CG = current gear
        int CG = 0;

        // RG = recommended gear
        int RG = 0;

        int L_const = 0;
        int O_const = 0;
        int n_min = 0;

        // v = vehicle speed
        double v = readDouble(readings, AvailableCommandNames.SPEED.name());
        // if the vehicle is stopped there is no need to evaluate the recommended gear
        // D0
        if (v == 0) {
            Log.d(TAG, "Vehicle speed is 0");
            return 0;
        }

        // n = engine speed
        double n = readDouble(readings, AvailableCommandNames.ENGINE_RPM.name());
        if (n == 0) {
            Log.d(TAG, "Engine speed is 0");
            return 0;
        }

        // L = calculated load value
        double L = readDouble(readings, AvailableCommandNames.ENGINE_LOAD.name());
        if (L == 0) {
            Log.d(TAG, "Engine load is 0");
            return 0;
        }

        // O = throttle opening
        double O = readDouble(readings, AvailableCommandNames.THROTTLE_POS.name());
        if (O == 0) {
            Log.d(TAG, "Throttle position is 0");
            return 0;
        }

        // P0
        // evaluate the current transmission ratio
        double i = i_c * (n / v);

        // D1
        if (i >= TRANSMISSION_RATIO_MIN[1] && i <= TRANSMISSION_RATIO_MAX[1]) {
            // P1
            CG = 1;
            // D6
            if (n >= MAX_TORQUE_RPM) {
                // O1
                RG = 2;
            }
            else {
                RG = 1;
            }
        }
        // D2
        else if (i >= TRANSMISSION_RATIO_MIN[2] && i <= TRANSMISSION_RATIO_MAX[2]) {
            // P2
            CG = 2;
            L_const = MAX_LOAD_CONSTANT_SPEED[2];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[2];
            n_min = MIN_RPM_NEEDED[2];
        }
        // D3
        else if (i >= TRANSMISSION_RATIO_MIN[3] && i <= TRANSMISSION_RATIO_MAX[3]) {
            // P3
            CG = 3;
            L_const = MAX_LOAD_CONSTANT_SPEED[3];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[3];
            n_min = MIN_RPM_NEEDED[3];
        }
        // D4
        else if (i >= TRANSMISSION_RATIO_MIN[4] && i <= TRANSMISSION_RATIO_MAX[4]) {
            // P4
            CG = 4;
            L_const = MAX_LOAD_CONSTANT_SPEED[4];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[4];
            n_min = MIN_RPM_NEEDED[4];
        }
        // D5
        else if (i >= TRANSMISSION_RATIO_MIN[5] && i <= TRANSMISSION_RATIO_MAX[5]) {
            // P5
            CG = 5;
            L_const = MAX_LOAD_CONSTANT_SPEED[5];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[5];
            n_min = MIN_RPM_NEEDED[5];
        }
        else if (v >= 40) { // TODO fix condition
            CG = 3;
            // TODO min stuff
            // evaluate k
            // RG = k
        }
        // D8
        if (n >= n_min) { // TODO fix condition
            // O4
            RG = CG;
        }
        // D9
        else if (L <= L_const) { // TODO fix condition
            // D10
            if (n < n_min) {
                // O5
                RG = CG != 2 ? (CG - 1) : CG;
            }
            else {
                // TODO min stuff
                // evaluate k
                // RG = k
            }
        }
        // D11
        else if (L >= MAX_LOAD_MAX_ACCELERATION && (O >= MIN_THROTTLE_MAX_ACCELERATION) && CG != 2) { // TODO fix condition
            // TODO min stuff
            // evaluate k
            // O6
            // RG = k
        }
        // D12
        else if (v >= 100) {
            // O7
            RG = 5;
        }
        // D13
        else if (v >= 50 && v <= 100) {
            if (CG == 5) {
                if (L/O >= MIN_LOAD_THROTTLE[5]) {
                    RG = 5;
                }
                else {
                    RG = 4;
                }
            }
            else if (CG == 4) {
                if (L/O >= MIN_LOAD_THROTTLE[4]) {
                    RG = 4;
                }
                else {
                    RG = 5;
                }
            }
            else if (CG == 3) {
                if (L/O >= MIN_LOAD_THROTTLE[3]) {
                    RG = 3;
                }
                else {
                    RG = 4;
                }
            }
            else {
                RG = 3;
            }
        }
        else {
            if (CG == 3) {
                if (L/O >= MIN_LOAD_THROTTLE[3]) {
                    RG = 3;
                }
                else {
                    RG = 2;
                }
            }
            // D15
            else {
                if (MIN_RPM_NEEDED[3] <= v * GEAR_RATIO[3] / i_c) {
                    RG = 3;
                }
                else {
                    RG = 2;
                }
            }
        }
        return RG;
    }
}
