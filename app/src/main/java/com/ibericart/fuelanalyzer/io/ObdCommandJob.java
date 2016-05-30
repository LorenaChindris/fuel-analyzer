package com.ibericart.fuelanalyzer.io;

import com.github.pires.obd.commands.ObdCommand;

/**
 * This class represents a job that the ObdGatewayService will have to execute and
 * maintain until the job is finished. It is, thereby, the application
 * representation of an ObdCommand instance plus a state that will be
 * interpreted and manipulated by the ObdGatewayService.
 * <br />
 * Uses code from https://github.com/pires/android-obd-reader
 */
public class ObdCommandJob {

    private Long id;
    private ObdCommand command;
    private ObdCommandJobState state;

    /**
     * Default constructor.
     *
     * @param command The ObdCommand to encapsulate.
     */
    public ObdCommandJob(ObdCommand command) {
        this.command = command;
        state = ObdCommandJobState.NEW;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ObdCommand getCommand() {
        return command;
    }

    /**
     * @return The job's current state.
     */
    public ObdCommandJobState getState() {
        return state;
    }

    /**
     * Sets a new job state.
     *
     * @param state The new job state.
     */
    public void setState(ObdCommandJobState state) {
        this.state = state;
    }

    /**
     * The command's state.
     */
    public enum ObdCommandJobState {
        NEW,
        RUNNING,
        FINISHED,
        EXECUTION_ERROR,
        QUEUE_ERROR,
        NOT_SUPPORTED
    }
}
