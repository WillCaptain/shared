package org.twelve.aipp.scheduler;

import java.util.Collection;

/**
 * Shared AIPP-side registration boundary. Framework adapters expose the
 * registered handlers through {@code GET /api/schedules} and dispatch callbacks.
 */
public interface AippScheduleRegistrar {
    Registration register(AippScheduleHandler handler);

    Collection<ScheduleHandlerRegistration> registrations();

    interface Registration extends AutoCloseable {
        ScheduleHandlerRegistration handler();

        @Override
        void close();
    }
}
