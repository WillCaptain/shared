package org.twelve.aipp.collaboration;

import java.util.Map;

/** Immutable version reference used when a collaboration message shares a file. */
public record VersionedResource(
        String schema,
        String provider,
        String resourceId,
        String versionId,
        String digest,
        String title,
        String mime,
        Long sizeBytes,
        String currentVersionId,
        String openTool,
        Map<String, Object> openArguments) {

    public static final String SCHEMA = "shared.versioned-resource/v1";
    public static String openToolFor(String provider) {
        return normalizedProvider(provider) + "_resource_version_open";
    }

    public static String proposeToolFor(String provider) {
        return normalizedProvider(provider) + "_resource_version_propose";
    }

    public VersionedResource {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported versioned resource schema");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (versionId == null || versionId.isBlank()) throw new IllegalArgumentException("versionId is required");
        if (digest == null || digest.isBlank()) throw new IllegalArgumentException("digest is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (sizeBytes != null && sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
        if (openTool == null || openTool.isBlank()) throw new IllegalArgumentException("openTool is required");
        if (!openToolFor(provider).equals(openTool)) {
            throw new IllegalArgumentException("openTool does not belong to provider");
        }
        openArguments = openArguments == null ? Map.of() : Map.copyOf(openArguments);
    }

    private static String normalizedProvider(String provider) {
        if (provider == null || !provider.matches("[a-z][a-z0-9-]{0,99}")) {
            throw new IllegalArgumentException("provider is invalid");
        }
        return provider.replace('-', '_');
    }
}
