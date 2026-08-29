package org.twelve.aipp.scheduler.spring;

import org.twelve.aipp.scheduler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultAippScheduleRegistrar implements AippScheduleRegistrar {
    private final Map<String,AippScheduleHandler> handlers = new ConcurrentHashMap<>();

    @Override public Registration register(AippScheduleHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String name = AippScheduleSpec.requireHandler(handler.name());
        if (handlers.putIfAbsent(name, handler) != null) throw new IllegalStateException("duplicate schedule handler: " + name);
        return new Registration() {
            public ScheduleHandlerRegistration handler() { return new ScheduleHandlerRegistration(name); }
            public void close() { handlers.remove(name, handler); }
        };
    }

    @Override public Collection<ScheduleHandlerRegistration> registrations() {
        return handlers.keySet().stream().sorted().map(ScheduleHandlerRegistration::new).toList();
    }

    AippScheduleHandler require(String name) {
        AippScheduleHandler handler = handlers.get(AippScheduleSpec.requireHandler(name));
        if (handler == null) throw new NoSuchElementException("unknown schedule handler: " + name);
        return handler;
    }
}
