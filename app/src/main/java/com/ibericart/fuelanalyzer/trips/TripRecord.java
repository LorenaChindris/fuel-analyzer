package com.ibericart.fuelanalyzer.trips;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TripRecord {

    // record id for database use (primary key)
    private Integer id;

    // the date the trip started
    private Date startDate;

    // the date the trip ended
    private Date endDate;

    private Integer engineRpmMax = 0;

    private Integer speed = 0;

    private String engineRuntime;

    public TripRecord() {
        startDate = new Date();
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

    public Integer getID() {
        return id;
    }

    public void setID(Integer id) {
        this.id = id;
    }

    public Date getStartDate() {
        return startDate;
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

    public String getStartDateString() {
        //todo
        //return dateFormatter.format(this.startDate);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        return sdf.format(this.startDate);
    }

    public String getEngineRuntime() {
        return engineRuntime;
    }

    public void setEngineRuntime(String value) {
        if (!value.equals("00:00:00")) {
            this.engineRuntime = value;
        }
    }
}
