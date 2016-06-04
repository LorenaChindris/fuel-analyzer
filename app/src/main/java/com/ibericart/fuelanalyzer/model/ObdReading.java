package com.ibericart.fuelanalyzer.model;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO (data transaction object) for OBD readings.
 * Uses code from https://github.com/pires/android-obd-reader
 */
@SuppressWarnings("unused")
public class ObdReading {

    private double latitude, longitude, altitude;

    private long timestamp;

    // vehicle identification number
    private String vin;

    private Map<String, String> readings;

    public ObdReading() {
        readings = new HashMap<>();
    }

    public ObdReading(double latitude, double longitude, double altitude, long timestamp,
                      String vin, Map<String, String> readings) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
        this.vin = vin;
        this.readings = readings;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vehicleId) {
        this.vin = vehicleId;
    }

    public Map<String, String> getReadings() {
        return readings;
    }

    public void setReadings(Map<String, String> readings) {
        this.readings = readings;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("lat: ").append(latitude).append("; ")
                .append("lon: ").append(longitude).append("; ")
                .append("alt: ").append(altitude).append("; ")
                .append("vin: ").append(vin).append("; ")
                .append("readings: ")
                .append(readings.toString().substring(10).replace("}", "").replace(",", ";"));
        return sb.toString();
    }
}
