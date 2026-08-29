package org.twelve.aipp.scheduler;

/** Business callback implemented by an AIPP and registered by name. */
public interface AippScheduleHandler {
    String name();

    ScheduleFireResult onFire(ScheduleFireRequest request);
}
