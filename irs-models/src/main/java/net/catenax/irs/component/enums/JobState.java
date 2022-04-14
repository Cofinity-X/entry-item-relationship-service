//
// Copyright (c) 2021 Copyright Holder (Catena-X Consortium)
//
// See the AUTHORS file(s) distributed with this work for additional
// information regarding authorship.
//
// See the LICENSE file(s) distributed with this work for
// additional information regarding license terms.
//
package net.catenax.irs.component.enums;

import net.catenax.irs.annotations.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Represents the state of the current job
 */
@ExcludeFromCodeCoverageGeneratedReport
public enum JobState {
    UNSAVED(JobStateConstants.UNSAVED),
    INITIAL(JobStateConstants.INITIAL),
    IN_PROGRESS(JobStateConstants.RUNNING),
    TRANSFERS_FINISHED(JobStateConstants.TRANSFERRED),
    COMPLETED(JobStateConstants.COMPLETE),
    CANCELED(JobStateConstants.CANCELED),
    ERROR(JobStateConstants.FAILED);

    private final String value;

    JobState(final String value) {
        this.value = value;
    }

    /**
     * of as a substitute/alias for valueOf handling the default value
     *
     * @param value see {@link #value}
     * @return the corresponding JobState
     */
    public static JobState value(final String value) {
        return JobState.valueOf(value);
    }

    /**
     * @return convert JobState to string value
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * Constants for job states
     */
    public static class JobStateConstants {
        public static final String UNSAVED = "unsaved";
        public static final String INITIAL = "initial";
        public static final String RUNNING = "running";
        public static final String TRANSFERRED = "transferred";
        public static final String COMPLETE = "completed";
        public static final String CANCELED = "canceled";
        public static final String FAILED = "failed";
    }
}