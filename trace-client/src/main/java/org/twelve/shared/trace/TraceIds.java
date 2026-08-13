package org.twelve.shared.trace;

import java.util.UUID;

public final class TraceIds {
    private TraceIds() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
