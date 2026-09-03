package org.twelve.aipp.collaboration;

import java.util.Map;

/** A room-shareable reference to one authoritative, revisioned collaboration resource. */
public record CollaborationResource(
        String schema,
        String kind,
        String resourceId,
        String title,
        long revision,
        String openTool,
        Map<String, Object> openArguments,
        Map<String, Object> provenance) {

    public static final String SCHEMA = "shared.collaboration-resource/v1";
    public static final String GRAPH_KIND = "graph";
    public static final String GRAPH_CREATE_TOOL = "collaboration_graph_create";
    public static final String GRAPH_OPEN_TOOL = "collaboration_graph_open";
    public static final String GRAPH_APPLY_TOOL = "collaboration_graph_apply";

    public CollaborationResource {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported collaboration resource schema");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (openTool == null || openTool.isBlank()) throw new IllegalArgumentException("openTool is required");
        openArguments = openArguments == null ? Map.of() : Map.copyOf(openArguments);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
