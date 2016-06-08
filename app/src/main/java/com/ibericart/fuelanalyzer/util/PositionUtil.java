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

    /**
     * Uses the Haversine formula to calculate the distance between two points.
     *
     * @param latitude1  The first point's latitude.
     * @param longitude1 The first point's longitude.
     * @param latitude2  The second point's latitude.
     * @param longitude2 The second point's longitude.
     * @return The distance between the two points in meters.
     */
    public static double calculateDistance(double latitude1, double longitude1, double latitude2, double longitude2) {
        /*

         Haversine formula:
         A = sin²(Δlat/2) + cos(lat1).cos(lat2).sin²(Δlong/2)
         C = 2.atan2(√a, √(1−a))
         D = R.c
         R = radius of earth, 6371 km.
         All angles are in radians

         */

        double deltaLatitude = Math.toRadians(Math.abs(latitude1 - latitude2));
        double deltaLongitude = Math.toRadians(Math.abs(longitude1 - longitude2));
        double latitude1Rad = Math.toRadians(latitude1);
        double latitude2Rad = Math.toRadians(latitude2);

        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + (Math.cos(latitude1Rad) * Math.cos(latitude2Rad)
                    * Math.pow(Math.sin(deltaLongitude / 2), 2));

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // distance in meters
        return 6371 * c * 1000;
    }
}
