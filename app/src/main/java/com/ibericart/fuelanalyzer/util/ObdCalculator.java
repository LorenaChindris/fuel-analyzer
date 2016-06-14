package com.ibericart.fuelanalyzer.util;

import android.util.Log;

import com.github.pires.obd.enums.AvailableCommandNames;

import java.util.Map;

/**
 * Contains utility methods for OBD related tasks.
 */
public class ObdCalculator {

    private static final String TAG = ObdCalculator.class.getName();

    // prepare the default constants

    // r_d
    private static final double DYNAMIC_ROLLING_RADIUS = 0.313;

    // i_0
    private static final double FINAL_DRIVE_RATIO = 4.36;

    // I_C = 3600/1000 * PI/30 * r_d/i_0
    private static final double I_C = (3600 * Math.PI * DYNAMIC_ROLLING_RADIUS)
            / (1000 * 30 * FINAL_DRIVE_RATIO);

    // i_imin
    private static final double[] TRANSMISSION_RATIO_MIN = new double[] {0, 3.57, 1.9, 1.18, 0.86, 0.67};

    // i_imax
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
    private static final int MIN_LOAD_MAX_ACCELERATION = 70;
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

    private static double readDoubleFromSettings(Map<String, String> readings, String key, double defaultValue) {
        double result = 0.0;
        if (readings != null && !readings.isEmpty()) {
            String value = readings.get(key);
            if (value != null) {
                try {
                    result = Double.valueOf(value);
                }
                catch (NumberFormatException e) {
                    Log.e(TAG, "Could not parse double from settings for key = " + key
                            + " and value = " + value);
                }
            }
        }
        if (result == 0.0) {
            result = defaultValue;
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
        // (L/O)i_min, I_C, [i_imin, i_imax]) - not declared parameters

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
        // I_C - engine speed - vehicle speed ratio relative to transmission ratio
        // [i_imin, i_imax] - transmission ratios intervals for determining current gear

        // take the parameters from the settings

        double dynamicRollingRadius = readDoubleFromSettings(params, "dynamic_rolling_radius", DYNAMIC_ROLLING_RADIUS);
        double finalDriveRatio = readDoubleFromSettings(params, "final_drive_ratio", FINAL_DRIVE_RATIO);
        double ic = (3600 * Math.PI * dynamicRollingRadius) / (1000 * 30 * finalDriveRatio);
        double[] transmissionRatioMin = new double[6];
        double[] transmissionRatioMax = new double[6];
        transmissionRatioMin[1] = readDoubleFromSettings(params, "gear_ratio_limits_one_min", TRANSMISSION_RATIO_MIN[1]);
        transmissionRatioMax[1] = readDoubleFromSettings(params, "gear_ratio_limits_one_max", TRANSMISSION_RATIO_MAX[1]);
        transmissionRatioMin[2] = readDoubleFromSettings(params, "gear_ratio_limits_two_min", TRANSMISSION_RATIO_MIN[2]);
        transmissionRatioMax[2] = readDoubleFromSettings(params, "gear_ratio_limits_two_max", TRANSMISSION_RATIO_MAX[2]);
        transmissionRatioMin[3] = readDoubleFromSettings(params, "gear_ratio_limits_three_min", TRANSMISSION_RATIO_MIN[3]);
        transmissionRatioMax[3] = readDoubleFromSettings(params, "gear_ratio_limits_three_max", TRANSMISSION_RATIO_MAX[3]);
        transmissionRatioMin[4] = readDoubleFromSettings(params, "gear_ratio_limits_four_min", TRANSMISSION_RATIO_MIN[4]);
        transmissionRatioMax[4] = readDoubleFromSettings(params, "gear_ratio_limits_four_max", TRANSMISSION_RATIO_MAX[4]);
        transmissionRatioMin[5] = readDoubleFromSettings(params, "gear_ratio_limits_five_min", TRANSMISSION_RATIO_MIN[5]);
        transmissionRatioMax[5] = readDoubleFromSettings(params, "gear_ratio_limits_five_max", TRANSMISSION_RATIO_MAX[5]);
        double maxTorqueRpm = readDoubleFromSettings(params, "max_torque_rpm", MAX_TORQUE_RPM);
        double[] maxLoadConstantSpeed = new double[6];
        maxLoadConstantSpeed[1] = readDoubleFromSettings(params, "max_load_constant_speed_one", MAX_LOAD_CONSTANT_SPEED[1]);
        maxLoadConstantSpeed[2] = readDoubleFromSettings(params, "max_load_constant_speed_two", MAX_LOAD_CONSTANT_SPEED[2]);
        maxLoadConstantSpeed[3] = readDoubleFromSettings(params, "max_load_constant_speed_three", MAX_LOAD_CONSTANT_SPEED[3]);
        maxLoadConstantSpeed[4] = readDoubleFromSettings(params, "max_load_constant_speed_four", MAX_LOAD_CONSTANT_SPEED[4]);
        maxLoadConstantSpeed[5] = readDoubleFromSettings(params, "max_load_constant_speed_five", MAX_LOAD_CONSTANT_SPEED[5]);
        double[] maxThrottleConstantSpeed = new double[6];
        maxThrottleConstantSpeed[1] = readDoubleFromSettings(params, "max_throttle_constant_speed_one", MAX_THROTTLE_CONSTANT_SPEED[1]);
        maxThrottleConstantSpeed[2] = readDoubleFromSettings(params, "max_throttle_constant_speed_two", MAX_THROTTLE_CONSTANT_SPEED[2]);
        maxThrottleConstantSpeed[3] = readDoubleFromSettings(params, "max_throttle_constant_speed_three", MAX_THROTTLE_CONSTANT_SPEED[3]);
        maxThrottleConstantSpeed[4] = readDoubleFromSettings(params, "max_throttle_constant_speed_four", MAX_THROTTLE_CONSTANT_SPEED[4]);
        maxThrottleConstantSpeed[5] = readDoubleFromSettings(params, "max_throttle_constant_speed_five", MAX_THROTTLE_CONSTANT_SPEED[5]);
        double[] minLoadThrottle = new double[6];
        minLoadThrottle[1] = readDoubleFromSettings(params, "min_load_throttle_position_ratio_one", MIN_LOAD_THROTTLE[1]);
        minLoadThrottle[2] = readDoubleFromSettings(params, "min_load_throttle_position_ratio_two", MIN_LOAD_THROTTLE[2]);
        minLoadThrottle[3] = readDoubleFromSettings(params, "min_load_throttle_position_ratio_three", MIN_LOAD_THROTTLE[3]);
        minLoadThrottle[4] = readDoubleFromSettings(params, "min_load_throttle_position_ratio_four", MIN_LOAD_THROTTLE[4]);
        minLoadThrottle[5] = readDoubleFromSettings(params, "min_load_throttle_position_ratio_five", MIN_LOAD_THROTTLE[5]);
        double minLoadMaxAcceleration = readDoubleFromSettings(params, "min_load_max_acceleration", MIN_LOAD_MAX_ACCELERATION);
        double minThrottleMaxAcceleration = readDoubleFromSettings(params, "min_throttle_max_acceleration", MIN_THROTTLE_MAX_ACCELERATION);
        double initialThrottlePosition = readDoubleFromSettings(params, "initial_throttle", INITIAL_THROTTLE_POSITION);
        double[] minRpmNeeded = new double[6];
        minRpmNeeded[1] = readDoubleFromSettings(params, "min_rpm_needed_one", MIN_RPM_NEEDED[1]);
        minRpmNeeded[2] = readDoubleFromSettings(params, "min_rpm_needed_two", MIN_RPM_NEEDED[2]);
        minRpmNeeded[3] = readDoubleFromSettings(params, "min_rpm_needed_three", MIN_RPM_NEEDED[3]);
        minRpmNeeded[4] = readDoubleFromSettings(params, "min_rpm_needed_four", MIN_RPM_NEEDED[4]);
        minRpmNeeded[5] = readDoubleFromSettings(params, "min_rpm_needed_five", MIN_RPM_NEEDED[5]);
        double[] gearRatio = new double[6];
        gearRatio[1] = readDoubleFromSettings(params, "gear_ratios_one", GEAR_RATIO[1]);
        gearRatio[2] = readDoubleFromSettings(params, "gear_ratios_two", GEAR_RATIO[2]);
        gearRatio[3] = readDoubleFromSettings(params, "gear_ratios_three", GEAR_RATIO[3]);
        gearRatio[4] = readDoubleFromSettings(params, "gear_ratios_four", GEAR_RATIO[4]);
        gearRatio[5] = readDoubleFromSettings(params, "gear_ratios_five", GEAR_RATIO[5]);


        // CG = current gear
        int CG = 0;

        // RG = recommended gear
        int RG = 0;

        double L_const = 0.0;
        double O_const = 0.0;
        double n_min = 0.0;

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
        double i = ic * (n / v);

        // D1
        if (i >= transmissionRatioMin[1] && i <= transmissionRatioMax[1]) {
            // P1
            Log.e(TAG, "P1");
            CG = 1;
            // D6
            if (n >= maxTorqueRpm) {
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
        else if (i >= transmissionRatioMin[2] && i <= transmissionRatioMax[2]) {
            // P2
            Log.e(TAG, "P2");
            CG = 2;
            L_const = maxLoadConstantSpeed[2];
            O_const = maxThrottleConstantSpeed[2];
            n_min = minRpmNeeded[2];
        }
        // D3
        else if (i >= transmissionRatioMin[3] && i <= transmissionRatioMax[3]) {
            // P3
            Log.e(TAG, "P3");
            CG = 3;
            L_const = maxLoadConstantSpeed[3];
            O_const = maxThrottleConstantSpeed[3];
            n_min = minRpmNeeded[3];
        }
        // D4
        else if (i >= transmissionRatioMin[4] && i <= transmissionRatioMax[4]) {
            // P4
            Log.e(TAG, "P4");
            CG = 4;
            L_const = maxLoadConstantSpeed[4];
            O_const = maxThrottleConstantSpeed[4];
            n_min = minRpmNeeded[4];
        }
        // D5
        else if (i >= transmissionRatioMin[5] && i <= transmissionRatioMax[5]) {
            // P5
            Log.e(TAG, "P5");
            CG = 5;
            L_const = maxLoadConstantSpeed[5];
            O_const = maxThrottleConstantSpeed[5];
            n_min = minRpmNeeded[5];
        }
        // no gear is currently engaged
        // is this a case of higher motion speed (higher than 40 km/h)
        // or less at the minimum throttle opening (which excludes short-time gear shift)?
        // D7
        else if (O == initialThrottlePosition && v >= 40) { // TODO fix condition
            Log.e(TAG, "D7 - compute the highest possible gear for this speed");
            CG = 3;
            // TODO
            // fuel is wasted here
            // compute the highest possible gear for this speed

            double minValue = Double.MAX_VALUE;
            int K = 5;
            for (int k = 5; k >= CG; k--) {
                double val = gearRatio[k] * v / ic;
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
        if (O == initialThrottlePosition && n >= n_min) { // TODO fix condition
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
                    double val = gearRatio[k] * v / ic;
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
        else if (L >= minLoadMaxAcceleration && (O >= minThrottleMaxAcceleration) && CG != 2) { // TODO fix condition
            Log.e(TAG, "D7 - maximum acceleration mode");
            // P7
            // evaluate the gear which is by the engine speed the closest to
            // the one corresponding to the maximum engine torque

            double minValue = Double.MAX_VALUE;
            int K = CG;
            for (int k = CG; k >= 2; k--) {
                double val = maxTorqueRpm * (gearRatio[k] * v / ic);
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
                if (L/O >= minLoadThrottle[5]) {
                    RG = 5;
                }
                else {
                    RG = 4;
                }
            }
            else if (CG == 4) {
                Log.e(TAG, "CG = " + CG);
                if (L/O >= minLoadThrottle[4]) {
                    RG = 4;
                }
                else {
                    RG = 5;
                }
            }
            else if (CG == 3) {
                Log.e(TAG, "CG = " + CG);
                if (L/O >= minLoadThrottle[3]) {
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
                if (L/O >= minLoadThrottle[3]) {
                    RG = 3;
                }
                else {
                    RG = 2;
                }
            }
            // D15
            else {
                Log.e(TAG, "D15");
                if (minRpmNeeded[3] <= v * gearRatio[3] / ic) {
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
