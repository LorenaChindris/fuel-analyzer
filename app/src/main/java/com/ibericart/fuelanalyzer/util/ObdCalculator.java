package com.ibericart.fuelanalyzer.util;

import android.util.Log;

import com.github.pires.obd.enums.AvailableCommandNames;

import java.util.Map;

/**
 * Contains utility methods for OBD related tasks.
 */
public class ObdCalculator {

    private static final String TAG = ObdCalculator.class.getName();

    // prepare the constants
    // r_d
    private static final double DYNAMIC_ROLLING_RADIUS = 0.313;
    // i_0
    private static final double FINAL_DRIVE_RATIO = 4.36;

    // i_c = 3600/1000 * PI/30 * r_d/i_0
    private static final double i_c = (3600 * Math.PI * DYNAMIC_ROLLING_RADIUS)
            / (1000 * 30 * FINAL_DRIVE_RATIO);

    private static final double[] TRANSMISSION_RATIO_MIN = new double[] {0, 3.57, 1.9, 1.18, 0.86, 0.67};
    private static final double[] TRANSMISSION_RATIO_MAX = new double[] {0, 3.97, 2.3, 1.38, 1, 0.81};

    // engine speed that achieves maximum torque
    // n_Memax
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
    private static final double[] GEAR_RATIO = new double[] {0, 3.77, 2.1, 1.28, 0.93, 0.74};

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
            Log.d(TAG, "Key = " + key + " Value = " + value);
            if (value != null) {
                if (AvailableCommandNames.SPEED.name().equals(key)) {
                    value = value.substring(0, value.length() - 4);
                }
                else if (AvailableCommandNames.ENGINE_RPM.name().equals(key)
                        || AvailableCommandNames.THROTTLE_POS.name().equals(key)) {
                    value = value.substring(0, value.length() - 3);
                }
                else if (AvailableCommandNames.ENGINE_LOAD.name().equals(key)) {
                    value = value.substring(0, value.length() - 1);
                }
                try {
                    result = Double.valueOf(value);
                }
                catch (NumberFormatException e) {
                    Log.e(TAG, "Could not parse double for key = " + key + " and value = " + value);
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

    public static int evaluateOptimalGear(Map<String, String> readings, Map<String, String> params) {
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
            Log.e(TAG, "Vehicle speed is 0");
            return 0;
        }

        // n = engine speed
        double n = readDouble(readings, AvailableCommandNames.ENGINE_RPM.name());
        if (n == 0) {
            Log.e(TAG, "Engine speed is 0");
            return 0;
        }

        // L = calculated load value
        double L = readDouble(readings, AvailableCommandNames.ENGINE_LOAD.name());
        if (L == 0) {
            Log.e(TAG, "Engine load is 0");
            return 0;
        }

        // O = throttle opening
        double O = readDouble(readings, AvailableCommandNames.THROTTLE_POS.name());
        if (O == 0) {
            Log.e(TAG, "Throttle position is 0");
            return 0;
        }

        // P0
        // evaluate the current transmission ratio
        double i = i_c * (n / v);

        // D1
        if (i >= TRANSMISSION_RATIO_MIN[1] && i <= TRANSMISSION_RATIO_MAX[1]) {
            // P1
            Log.e(TAG, "P1");
            CG = 1;
            // D6
            if (n >= MAX_TORQUE_RPM) {
                // O1
                RG = 2;
            }
            else {
                RG = 1;
            }
            Log.e(TAG, "RG = " + RG);
            return RG;
        }
        // D2
        else if (i >= TRANSMISSION_RATIO_MIN[2] && i <= TRANSMISSION_RATIO_MAX[2]) {
            // P2
            Log.e(TAG, "P2");
            CG = 2;
            L_const = MAX_LOAD_CONSTANT_SPEED[2];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[2];
            n_min = MIN_RPM_NEEDED[2];
        }
        // D3
        else if (i >= TRANSMISSION_RATIO_MIN[3] && i <= TRANSMISSION_RATIO_MAX[3]) {
            // P3
            Log.e(TAG, "P3");
            CG = 3;
            L_const = MAX_LOAD_CONSTANT_SPEED[3];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[3];
            n_min = MIN_RPM_NEEDED[3];
        }
        // D4
        else if (i >= TRANSMISSION_RATIO_MIN[4] && i <= TRANSMISSION_RATIO_MAX[4]) {
            // P4
            Log.e(TAG, "P4");
            CG = 4;
            L_const = MAX_LOAD_CONSTANT_SPEED[4];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[4];
            n_min = MIN_RPM_NEEDED[4];
        }
        // D5
        else if (i >= TRANSMISSION_RATIO_MIN[5] && i <= TRANSMISSION_RATIO_MAX[5]) {
            // P5
            Log.e(TAG, "P5");
            CG = 5;
            L_const = MAX_LOAD_CONSTANT_SPEED[5];
            O_const = MAX_THROTTLE_CONSTANT_SPEED[5];
            n_min = MIN_RPM_NEEDED[5];
        }
        // no gear is currently engaged
        // is this a case of higher motion speed (higher than 40 km/h)
        // or less at the minimum throttle opening (which excludes short-time gear shift)?
        // D7
        else if (O == 0 && v >= 40) { // TODO fix condition
            Log.e(TAG, "D7 - compute the highest possible gear for this speed");
            CG = 3;
            // TODO
            // fuel is wasted here
            // compute the highest possible gear for this speed

            double minValue = Double.MAX_VALUE;
            int K = 5;
            for (int k = 5; k >= CG; k--) {
                double val = GEAR_RATIO[k] * v / i_c;
                if (val < minValue) {
                    minValue = val;
                    K = k;
                }
            }
            RG = K;
            // O3
            Log.e(TAG, "O3 RG = " + RG);
            return RG;
        }
        // no gear is recommended
        else {
            // O2
            Log.e(TAG, "O2 - no gear is recommended");
            return 0;
        }
        // D8
        if (O == 0 && n >= n_min) { // TODO fix condition
            // O4
            Log.e(TAG, "O4 - RG = " + CG);
            RG = CG;
            return RG;
        }
        // D9
        // approximately constant speed
        else if (L <= L_const && O <= O_const) { // TODO fix condition
            Log.e(TAG, "D9 - approximately constant speed");
            // D10
            if (n < n_min) {
                // O5
                RG = CG != 2 ? (CG - 1) : CG;
                Log.e(TAG, "O5 - RG = " + RG);
                return RG;
            }
            else {
                // TODO
                // compute the highest possible gear for this speed

                double minValue = Double.MAX_VALUE;
                int K = 5;
                for (int k = 5; k >= CG; k--) {
                    double val = GEAR_RATIO[k] * v / i_c;
                    if (val < minValue) {
                        minValue = val;
                        K = k;
                    }
                }
                RG = K;
                // O3
                Log.e(TAG, "O3 - compute the highest possible value for this speed - RG = " + RG);
                return RG;
            }
        }
        // D11
        // maximum acceleration mode
        else if (L >= MAX_LOAD_MAX_ACCELERATION && (O >= MIN_THROTTLE_MAX_ACCELERATION) && CG != 2) { // TODO fix condition
            Log.e(TAG, "D7 - maximum acceleration mode");
            // P7
            // evaluate the gear which is by the engine speed the closest to
            // the one corresponding to the maximum engine torque

            double minValue = Double.MAX_VALUE;
            int K = CG;
            for (int k = CG; k >= 2; k--) {
                double val = MAX_TORQUE_RPM * (GEAR_RATIO[k] * v / i_c);
                if (val < minValue) {
                    minValue = val;
                    K = k;
                }
            }
            // O6
            RG = K;
            Log.e(TAG, "O6 - RG = " + RG);
            return RG;
        }
        // middle and low degrees of acceleration
        // D12
        else if (v >= 100) {
            // O7
            RG = 5;
            Log.e(TAG, "O7 - RG = " + RG);
        }
        // D13
        else if (v >= 50 && v <= 100) {
            Log.e(TAG, "D13");
            if (CG == 5) {
                Log.e(TAG, "CG = " + CG);
                if (L/O >= MIN_LOAD_THROTTLE[5]) {
                    RG = 5;
                }
                else {
                    RG = 4;
                }
            }
            else if (CG == 4) {
                Log.e(TAG, "CG = " + CG);
                if (L/O >= MIN_LOAD_THROTTLE[4]) {
                    RG = 4;
                }
                else {
                    RG = 5;
                }
            }
            else if (CG == 3) {
                Log.e(TAG, "CG = " + CG);
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
            Log.e(TAG, "Speed less than 50 km/h");
            if (CG == 3) {
                Log.e(TAG, "CG = " + CG);
                if (L/O >= MIN_LOAD_THROTTLE[3]) {
                    RG = 3;
                }
                else {
                    RG = 2;
                }
            }
            // D15
            else {
                Log.e(TAG, "D15");
                if (MIN_RPM_NEEDED[3] <= v * GEAR_RATIO[3] / i_c) {
                    RG = 3;
                }
                else {
                    RG = 2;
                }
            }
        }
        Log.e(TAG, "RG = " + RG);
        return RG;
    }
}
