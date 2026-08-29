package org.twelve.aipp.scheduler;

import java.util.List;
import java.util.Optional;

/**
 * Host-side scheduling port shared by Host implementations and in-process adapters.
 * Ownership is always supplied by authenticated context, never by request payload.
 */
public interface HostScheduler {
    ScheduleJob upsert(ScheduleOwner owner, ScheduleJobRequest request);

    boolean cancel(ScheduleOwner owner, String jobKey);

    Optional<ScheduleJob> get(ScheduleOwner owner, String jobKey);

    List<ScheduleJob> list(ScheduleOwner owner, String jobKeyPrefix);
}
