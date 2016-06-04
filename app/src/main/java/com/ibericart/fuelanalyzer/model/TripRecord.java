package com.ibericart.fuelanalyzer.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Used for holding trip information such as start and end date, speed, rpm and runtime.
 */
public class TripRecord {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    private static final String ZERO_TIMER = "00:00:00";

    // record id for database use (primary key)
    private Integer id;

    // the date the trip started
    private Date startDate;

    // the date the trip ended
    private Date endDate;

    private Integer speed = 0;

    private Integer engineRpmMax = 0;

    // the amount of time this trip took
    private String engineRuntime;

    public TripRecord() {
        startDate = new Date();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getStartDate() {
        return startDate;
    }

    public String getStartDateFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.US);
        return sdf.format(this.startDate);
    }

    public void setStartDate(Date date) {
        this.startDate = date;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date date) {
        this.endDate = date;
    }

    public Integer getSpeedMax() {
        return speed;
    }

    public void setSpeedMax(int value) {
        if (this.speed < value)
            speed = value;
    }

    public void setSpeedMax(String value) {
        setSpeedMax(Integer.parseInt(value));
    }

    public Integer getEngineRpmMax() {
        return this.engineRpmMax;
    }

    public void setEngineRpmMax(Integer value) {
        if (this.engineRpmMax < value) {
            this.engineRpmMax = value;
        }
    }

    public void setEngineRpmMax(String value) {
        setEngineRpmMax(Integer.parseInt(value));
    }

    public String getEngineRuntime() {
        return engineRuntime;
    }

    public void setEngineRuntime(String value) {
        if (!value.equals(ZERO_TIMER)) {
            this.engineRuntime = value;
        }
    }
}
