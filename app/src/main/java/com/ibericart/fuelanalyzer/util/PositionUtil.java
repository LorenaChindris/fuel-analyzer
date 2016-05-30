package com.ibericart.fuelanalyzer.util;

/**
 * Contains utility methods for positioning tasks.
 */
public class PositionUtil {

    public static String getCompassDirection(float angle) {
        String direction = "";
        if (angle >= 337.5 || angle < 22.5) {
            direction = "N";
        } else if (angle >= 22.5 && angle < 67.5) {
            direction = "NE";
        } else if (angle >= 67.5 && angle < 112.5) {
            direction = "E";
        } else if (angle >= 112.5 && angle < 157.5) {
            direction = "SE";
        } else if (angle >= 157.5 && angle < 202.5) {
            direction = "S";
        } else if (angle >= 202.5 && angle < 247.5) {
            direction = "SW";
        } else if (angle >= 247.5 && angle < 292.5) {
            direction = "W";
        } else if (angle >= 292.5 && angle < 337.5) {
            direction = "NW";
        }
        return direction;
    }
}
