package org.twelve.aipp.session;

/** Provider-neutral wire constants for an AIPP-owned collaboration session. */
public interface AippExternalSessionSpec {
    String TYPE = "external_collaboration";
    String API_PREFIX = "/api/collaboration";
    String CONVERSATIONS_PATH = API_PREFIX + "/conversations";
    String RESOURCE_GRANTS_PATH = API_PREFIX + "/resource-grants";
    String UNREAD_CHANGED_EVENT = "aipp:collaboration-unread-changed";
}
