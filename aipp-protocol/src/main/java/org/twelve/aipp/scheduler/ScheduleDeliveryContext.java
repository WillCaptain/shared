package org.twelve.aipp.scheduler;

import java.util.function.Supplier;

/** Authenticated Host-to-AIPP delivery identity, scoped to one callback thread. */
public final class ScheduleDeliveryContext {
    private static final ThreadLocal<Value> CURRENT = new ThreadLocal<>();

    private ScheduleDeliveryContext() {}

    public static Value require() {
        Value value = CURRENT.get();
        if (value == null) throw new IllegalStateException("schedule delivery context is unavailable");
        return value;
    }

    public static <T> T callAs(String appId, String userId, String deliveryId, Supplier<T> work) {
        if (work == null) throw new IllegalArgumentException("work is required");
        Value before = CURRENT.get();
        CURRENT.set(new Value(appId, userId, deliveryId));
        try { return work.get(); }
        finally { if (before == null) CURRENT.remove(); else CURRENT.set(before); }
    }

    public record Value(String appId, String userId, String deliveryId) {
        public Value {
            if (appId == null || appId.isBlank()) throw new IllegalArgumentException("appId is required");
            if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
            if (deliveryId == null || deliveryId.isBlank()) throw new IllegalArgumentException("deliveryId is required");
            appId = appId.trim(); userId = userId.trim(); deliveryId = deliveryId.trim();
        }
    }
}
