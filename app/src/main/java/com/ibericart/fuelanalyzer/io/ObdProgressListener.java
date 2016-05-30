package com.ibericart.fuelanalyzer.io;

public interface ObdProgressListener {

    void stateUpdate(final ObdCommandJob job);
}
