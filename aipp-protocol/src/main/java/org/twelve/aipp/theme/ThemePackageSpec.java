package org.twelve.aipp.theme;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Executable contract for the declarative {@code .ones-theme} package format.
 *
 * <p>The validator treats all packages as untrusted. It validates ZIP metadata,
 * inventory, integrity, typed theme documents, and bounded animation IR. It
 * deliberately does not decode raster images; the Host must dimension-check and
 * re-encode them before installation.
 */
public final class ThemePackageSpec {

    public static final int SCHEMA_VERSION = 1;
    public static final String MIME_TYPE = "application/vnd.ones.theme+zip";

    private static final Pattern PACKAGE_ID = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?){1,7}");
    private static final Pattern PUBLISHER_ID =
            Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?");
    private static final Pattern SEMVER = Pattern.compile(
            "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)"
                    + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private static final Pattern LOCALE =
            Pattern.compile("[a-z]{2,3}(?:-[a-z0-9]{2,8})*");
    private static final Pattern ID =
            Pattern.compile("[a-z0-9](?:[a-z0-9_-]{0,62}[a-z0-9])?");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HEX_COLOR =
            Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern RGB_COLOR =
            Pattern.compile("rgba?\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "schema_version", "package_id", "version", "name", "description",
            "publisher", "min_host_version", "components", "capabilities",
            "license", "integrity");
    private static final Set<String> COMPONENT_FIELDS = Set.of(
            "tokens", "shell", "background", "animation", "animation_fallback", "icon");
    private static final Set<String> COLOR_FIELDS = Set.of(
            "bg", "surface", "surface2", "surface3", "text", "textDim", "textMuted",
            "border", "border2", "accent", "accentHover", "accentGlow", "active",
            "danger", "success", "warning", "info");
    private static final Set<String> TOKEN_FIELDS = Set.of(
            "schema_version", "bg", "surface", "surface2", "surface3", "text",
            "textDim", "textMuted", "border", "border2", "accent", "accentHover",
            "accentGlow", "active", "danger", "success", "warning", "info", "font",
            "fontMono", "fontSize", "fontSizeSm", "fontSizeLg", "radius", "radiusSm",
            "radiusLg", "radiusPill");
    private static final Set<String> ATMOSPHERES =
            Set.of("none", "soft-glow", "aurora", "sakura-mist", "glass-neon");
    private static final Set<String> GLOWS = Set.of("off", "soft", "vivid");
    private static final Set<String> MOTIONS = Set.of("full", "reduced", "off");
    private static final Set<String> BLENDS =
            Set.of("source-over", "lighter", "screen", "multiply");
    private static final Set<String> NODE_TYPES = Set.of(
            "gradient", "particle_emitter", "sprite_emitter", "starfield",
            "scan_lines", "path", "trail", "glow", "transform", "blend",
            "pointer_field", "local_time_curve");
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".webp");
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".js", ".mjs", ".cjs", ".css", ".html", ".htm", ".svg", ".wasm",
            ".class", ".jar", ".exe", ".dll", ".dylib", ".so", ".sh", ".bat",
            ".cmd", ".ps1", ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2",
            ".xz", ".mp3", ".wav", ".ogg", ".mp4", ".webm", ".mov", ".avi",
            ".woff", ".woff2", ".ttf", ".otf");

    private final Limits limits;
    private final ObjectMapper json;

    public ThemePackageSpec() {
        this(Limits.defaults());
    }

    public ThemePackageSpec(Limits limits) {
        this.limits = limits == null ? Limits.defaults() : limits;
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(this.limits.maxJsonDepth())
                        .maxStringLength(this.limits.maxJsonBytes())
                        .build())
                .build();
        this.json = new ObjectMapper(factory);
    }

    public Limits limits() {
        return limits;
    }

    public void assertValidManifest(JsonNode root) {
        requireObject(root, "manifest");
        requireExactFields(root, MANIFEST_FIELDS, "manifest");
        requireVersion(root, "manifest");
        String packageId = requireText(root, "package_id", "manifest");
        require(PACKAGE_ID.matcher(packageId).matches(), "manifest.package_id is invalid");
        requireSemver(root, "version", "manifest");
        requireLocalizedString(root.get("name"), "manifest.name");
        requireLocalizedString(root.get("description"), "manifest.description");

        JsonNode publisher = requireObject(root.get("publisher"), "manifest.publisher");
        requireExactFields(publisher, Set.of("id", "display_name"), "manifest.publisher");
        require(PUBLISHER_ID.matcher(requireText(publisher, "id", "manifest.publisher")).matches(),
                "manifest.publisher.id is invalid");
        requireText(publisher, "display_name", "manifest.publisher");
        requireSemver(root, "min_host_version", "manifest");

        JsonNode components = requireObject(root.get("components"), "manifest.components");
        requireExactFields(components, COMPONENT_FIELDS, "manifest.components");
        requirePath(components, "tokens", false);
        requirePath(components, "shell", false);
        requirePath(components, "background", true);
        requirePath(components, "animation", false);
        requirePath(components, "animation_fallback", false);
        requirePath(components, "icon", true);
        require("theme/tokens.json".equals(components.path("tokens").asText()),
                "components.tokens must be theme/tokens.json");
        require("theme/shell.json".equals(components.path("shell").asText()),
                "components.shell must be theme/shell.json");
        require("animation/program.json".equals(components.path("animation").asText()),
                "components.animation must be animation/program.json");
        require("animation/fallback.json".equals(components.path("animation_fallback").asText()),
                "components.animation_fallback must be animation/fallback.json");
        if (!components.path("background").isNull()) {
            requireImagePath(components.path("background").asText(), "components.background");
            require(components.path("background").asText().startsWith("background/"),
                    "components.background must be under background/");
        }
        if (!components.path("icon").isNull()) {
            requireImagePath(components.path("icon").asText(), "components.icon");
            require(components.path("icon").asText().startsWith("icon/"),
                    "components.icon must be under icon/");
        }

        JsonNode capabilities = requireObject(root.get("capabilities"), "manifest.capabilities");
        requireExactFields(capabilities,
                Set.of("pointer", "local_time", "reduced_motion"), "manifest.capabilities");
        requireBoolean(capabilities, "pointer", "manifest.capabilities");
        requireBoolean(capabilities, "local_time", "manifest.capabilities");
        requireBoolean(capabilities, "reduced_motion", "manifest.capabilities");
        require(capabilities.path("reduced_motion").asBoolean(),
                "manifest.capabilities.reduced_motion must be true in schema v1");

        String license = requirePath(root, "license", false);
        require("LICENSE.txt".equals(license), "manifest.license must be LICENSE.txt");
        String integrity = requirePath(root, "integrity", false);
        require("integrity.json".equals(integrity),
                "manifest.integrity must be integrity.json");
    }

    public void assertValidTokens(JsonNode root) {
        requireObject(root, "tokens");
        requireExactFields(root, TOKEN_FIELDS, "tokens");
        requireVersion(root, "tokens");
        for (String field : COLOR_FIELDS) {
            String value = requireText(root, field, "tokens");
            require(isColor(value), "tokens." + field + " must be a strict color");
        }
        require("system-sans".equals(requireText(root, "font", "tokens")),
                "tokens.font must be system-sans");
        require("system-mono".equals(requireText(root, "fontMono", "tokens")),
                "tokens.fontMono must be system-mono");
        requireIntRange(root, "fontSize", 11, 18, "tokens");
        requireIntRange(root, "fontSizeSm", 9, 16, "tokens");
        requireIntRange(root, "fontSizeLg", 12, 24, "tokens");
        requireIntRange(root, "radius", 0, 32, "tokens");
        requireIntRange(root, "radiusSm", 0, 32, "tokens");
        requireIntRange(root, "radiusLg", 0, 32, "tokens");
        requireIntRange(root, "radiusPill", 32, 999, "tokens");
    }

    public void assertValidShell(JsonNode root, JsonNode manifest) {
        requireObject(root, "shell");
        requireExactFields(root,
                Set.of("schema_version", "dark_mode", "atmosphere", "fx", "background", "icon"),
                "shell");
        requireVersion(root, "shell");
        requireBoolean(root, "dark_mode", "shell");
        require(ATMOSPHERES.contains(requireText(root, "atmosphere", "shell")),
                "shell.atmosphere is invalid");

        JsonNode fx = requireObject(root.get("fx"), "shell.fx");
        requireExactFields(fx, Set.of("glow", "motion"), "shell.fx");
        require(GLOWS.contains(requireText(fx, "glow", "shell.fx")), "shell.fx.glow is invalid");
        require(MOTIONS.contains(requireText(fx, "motion", "shell.fx")), "shell.fx.motion is invalid");

        JsonNode background = requireObject(root.get("background"), "shell.background");
        String backgroundKind = requireText(background, "kind", "shell.background");
        if ("none".equals(backgroundKind)) {
            requireExactFields(background, Set.of("kind"), "shell.background");
        } else {
            require("asset".equals(backgroundKind), "shell.background.kind must be none or asset");
            requireExactFields(background,
                    Set.of("kind", "opacity", "overlay", "focal_x", "focal_y"),
                    "shell.background");
            requireNumberRange(background, "opacity", 0, 1, "shell.background");
            requireNumberRange(background, "overlay", 0, 1, "shell.background");
            requireNumberRange(background, "focal_x", 0, 1, "shell.background");
            requireNumberRange(background, "focal_y", 0, 1, "shell.background");
        }

        JsonNode icon = requireObject(root.get("icon"), "shell.icon");
        requireExactFields(icon, Set.of("kind"), "shell.icon");
        String iconKind = requireText(icon, "kind", "shell.icon");
        require(Set.of("host_default", "asset").contains(iconKind),
                "shell.icon.kind must be host_default or asset");

        if (manifest != null) {
            assertValidManifest(manifest);
            JsonNode components = manifest.path("components");
            boolean hasBackground = components.hasNonNull("background");
            boolean hasIcon = components.hasNonNull("icon");
            require(hasBackground == "asset".equals(backgroundKind),
                    "manifest background component must agree with shell.background.kind");
            require(hasIcon == "asset".equals(iconKind),
                    "manifest icon component must agree with shell.icon.kind");
        }
    }

    public void assertValidAnimation(JsonNode root, boolean fallback) {
        requireObject(root, fallback ? "animation fallback" : "animation");
        requireExactFields(root,
                Set.of("schema_version", "fps", "max_particles", "layers"),
                fallback ? "animation fallback" : "animation");
        requireVersion(root, "animation");
        requireIntRange(root, "fps", 1, 60, "animation");
        int maxParticles = requireIntRange(root, "max_particles", 0,
                fallback ? Math.min(120, limits.maxParticles()) : limits.maxParticles(),
                "animation");
        JsonNode layers = root.get("layers");
        require(layers != null && layers.isArray(), "animation.layers must be an array");
        require(layers.size() <= limits.maxAnimationLayers(), "animation layer limit exceeded");

        Set<String> ids = new HashSet<>();
        int nodeCount = 0;
        int declaredParticles = 0;
        for (JsonNode layer : layers) {
            requireObject(layer, "animation layer");
            requireExactFields(layer, Set.of("id", "blend", "opacity", "nodes"),
                    "animation layer");
            requireId(requireText(layer, "id", "animation layer"), "animation layer id");
            require(BLENDS.contains(requireText(layer, "blend", "animation layer")),
                    "animation layer blend is invalid");
            requireNumberRange(layer, "opacity", 0, 1, "animation layer");
            JsonNode nodes = layer.get("nodes");
            require(nodes != null && nodes.isArray(), "animation layer.nodes must be an array");
            for (JsonNode node : nodes) {
                nodeCount++;
                require(nodeCount <= limits.maxAnimationNodes(), "animation node limit exceeded");
                declaredParticles += validateNode(node, fallback, ids);
            }
        }
        require(declaredParticles <= maxParticles,
                "animation particle declarations exceed max_particles");
    }

    /**
     * Validates a complete ZIP package. The input is consumed but never written
     * to disk or exposed as a static resource.
     */
    public void assertValidPackage(InputStream input) throws IOException {
        require(input != null, "package input is required");
        byte[] archive = readBounded(input, limits.maxCompressedBytes(), "compressed package");
        List<CentralEntry> central = parseCentralDirectory(archive);
        Map<String, byte[]> files = readZipEntries(archive, central);

        JsonNode manifest = parseJson(requiredFile(files, "manifest.json"), "manifest.json");
        assertValidManifest(manifest);
        JsonNode components = manifest.path("components");
        JsonNode tokens = parseJson(requiredComponent(files, components, "tokens"),
                components.path("tokens").asText());
        JsonNode shell = parseJson(requiredComponent(files, components, "shell"),
                components.path("shell").asText());
        JsonNode program = parseJson(requiredComponent(files, components, "animation"),
                components.path("animation").asText());
        JsonNode fallback = parseJson(requiredComponent(files, components, "animation_fallback"),
                components.path("animation_fallback").asText());

        assertValidTokens(tokens);
        assertValidShell(shell, manifest);
        assertValidAnimation(program, false);
        assertValidAnimation(fallback, true);
        assertCapabilitiesMatch(manifest.path("capabilities"), program);

        requireComponentPresence(files, components, "background");
        requireComponentPresence(files, components, "icon");
        requiredFile(files, manifest.path("license").asText());
        JsonNode integrity = parseJson(requiredFile(files, "integrity.json"), "integrity.json");
        assertValidIntegrity(integrity, files);

        byte[] signature = files.get("signature.ed25519");
        if (signature != null) {
            require(signature.length == 64, "signature.ed25519 must contain exactly 64 bytes");
        }
    }

    public void assertValidIntegrity(JsonNode root, Map<String, byte[]> files) {
        requireObject(root, "integrity");
        requireExactFields(root, Set.of("schema_version", "algorithm", "files"), "integrity");
        requireVersion(root, "integrity");
        require("sha256".equals(requireText(root, "algorithm", "integrity")),
                "integrity.algorithm must be sha256");
        JsonNode listed = requireObject(root.get("files"), "integrity.files");

        Set<String> expected = new HashSet<>(files.keySet());
        expected.remove("integrity.json");
        expected.remove("signature.ed25519");
        Set<String> actual = new HashSet<>();
        listed.fields().forEachRemaining(entry -> {
            String path = entry.getKey();
            requireSafePath(path);
            JsonNode metadata = requireObject(entry.getValue(), "integrity.files." + path);
            requireExactFields(metadata, Set.of("sha256", "size"),
                    "integrity.files." + path);
            String hash = requireText(metadata, "sha256", "integrity.files." + path);
            require(SHA256.matcher(hash).matches(),
                    "integrity hash must be 64 lowercase hexadecimal characters: " + path);
            require(metadata.path("size").canConvertToLong() && metadata.path("size").asLong() >= 0,
                    "integrity size must be a non-negative integer: " + path);
            byte[] content = files.get(path);
            require(content != null, "integrity lists missing file: " + path);
            require(content.length == metadata.path("size").asLong(),
                    "integrity size mismatch: " + path);
            require(hash.equals(sha256(content)), "integrity hash mismatch: " + path);
            require(actual.add(path), "duplicate integrity path: " + path);
        });
        require(actual.equals(expected),
                "integrity inventory must list every regular file except integrity/signature");
    }

    private int validateNode(JsonNode node, boolean fallback, Set<String> ids) {
        requireObject(node, "animation node");
        requireExactFields(node, Set.of("id", "type", "params"), "animation node");
        String id = requireText(node, "id", "animation node");
        requireId(id, "animation node id");
        require(ids.add(id), "duplicate animation node id: " + id);
        String type = requireText(node, "type", "animation node");
        require(NODE_TYPES.contains(type), "unsupported animation node type: " + type);
        require(!(fallback && Set.of("pointer_field", "sprite_emitter").contains(type)),
                "animation fallback cannot contain " + type);
        JsonNode params = requireObject(node.get("params"), "animation node.params");

        return switch (type) {
            case "gradient" -> {
                requireExactFields(params,
                        Set.of("kind", "colors", "stops", "x0", "y0", "x1", "y1", "radius"),
                        "gradient params");
                require(Set.of("linear", "radial").contains(requireText(params, "kind", "gradient")),
                        "gradient kind is invalid");
                JsonNode colors = requireArray(params, "colors", "gradient");
                require(colors.size() >= 2 && colors.size() <= 8,
                        "gradient colors must contain 2 through 8 colors");
                colors.forEach(color -> require(color.isTextual() && isColor(color.asText()),
                        "gradient color is invalid"));
                JsonNode stops = requireArray(params, "stops", "gradient");
                require(stops.size() == colors.size(), "gradient stops must match colors");
                double last = -1;
                for (JsonNode stop : stops) {
                    require(stop.isNumber() && finite(stop.asDouble())
                                    && stop.asDouble() >= 0 && stop.asDouble() <= 1,
                            "gradient stop must be between 0 and 1");
                    require(stop.asDouble() >= last, "gradient stops must be ordered");
                    last = stop.asDouble();
                }
                for (String field : List.of("x0", "y0", "x1", "y1", "radius")) {
                    requireNumberRange(params, field, -2, 2, "gradient");
                }
                yield 0;
            }
            case "particle_emitter" -> validateParticleEmitter(params, false);
            case "sprite_emitter" -> validateParticleEmitter(params, true);
            case "starfield" -> {
                requireExactFields(params,
                        Set.of("count", "speed", "size_min", "size_max", "color", "twinkle"),
                        "starfield params");
                int count = requireIntRange(params, "count", 0, limits.maxParticles(), "starfield");
                requireNumberRange(params, "speed", 0, 200, "starfield");
                requireOrderedRange(params, "size_min", "size_max", 0.1, 64, "starfield");
                requireColor(params, "color", "starfield");
                requireNumberRange(params, "twinkle", 0, 1, "starfield");
                yield count;
            }
            case "scan_lines" -> {
                requireExactFields(params,
                        Set.of("spacing", "speed", "width", "color", "glitch"),
                        "scan_lines params");
                requireNumberRange(params, "spacing", 1, 128, "scan_lines");
                requireNumberRange(params, "speed", -500, 500, "scan_lines");
                requireNumberRange(params, "width", 0.1, 32, "scan_lines");
                requireColor(params, "color", "scan_lines");
                requireNumberRange(params, "glitch", 0, 1, "scan_lines");
                yield 0;
            }
            case "path" -> {
                requireExactFields(params,
                        Set.of("points", "closed", "color", "width", "speed"), "path params");
                JsonNode points = requireArray(params, "points", "path");
                require(points.size() >= 2 && points.size() <= 128,
                        "path points must contain 2 through 128 entries");
                for (JsonNode point : points) {
                    require(point.isArray() && point.size() == 2
                                    && point.get(0).isNumber() && point.get(1).isNumber(),
                            "path point must be [x,y]");
                    require(finite(point.get(0).asDouble()) && finite(point.get(1).asDouble())
                                    && Math.abs(point.get(0).asDouble()) <= 2
                                    && Math.abs(point.get(1).asDouble()) <= 2,
                            "path point is out of range");
                }
                requireBoolean(params, "closed", "path");
                requireColor(params, "color", "path");
                requireNumberRange(params, "width", 0.1, 64, "path");
                requireNumberRange(params, "speed", -500, 500, "path");
                yield 0;
            }
            case "trail" -> {
                requireExactFields(params, Set.of("length", "width", "color", "fade"),
                        "trail params");
                requireIntRange(params, "length", 1, 256, "trail");
                requireNumberRange(params, "width", 0.1, 64, "trail");
                requireColor(params, "color", "trail");
                requireNumberRange(params, "fade", 0, 1, "trail");
                yield 0;
            }
            case "glow" -> {
                requireExactFields(params, Set.of("radius", "intensity", "color"), "glow params");
                requireNumberRange(params, "radius", 0, 512, "glow");
                requireNumberRange(params, "intensity", 0, 1, "glow");
                requireColor(params, "color", "glow");
                yield 0;
            }
            case "transform" -> {
                requireExactFields(params,
                        Set.of("translate_x", "translate_y", "rotation", "scale"),
                        "transform params");
                requireNumberRange(params, "translate_x", -2, 2, "transform");
                requireNumberRange(params, "translate_y", -2, 2, "transform");
                requireNumberRange(params, "rotation", -1000, 1000, "transform");
                requireNumberRange(params, "scale", 0, 16, "transform");
                yield 0;
            }
            case "blend" -> {
                requireExactFields(params, Set.of("mode", "opacity"), "blend params");
                require(BLENDS.contains(requireText(params, "mode", "blend")),
                        "blend mode is invalid");
                requireNumberRange(params, "opacity", 0, 1, "blend");
                yield 0;
            }
            case "pointer_field" -> {
                requireExactFields(params, Set.of("radius", "strength", "swirl"),
                        "pointer_field params");
                requireNumberRange(params, "radius", 0, 2, "pointer_field");
                requireNumberRange(params, "strength", -4, 4, "pointer_field");
                requireNumberRange(params, "swirl", -8, 8, "pointer_field");
                yield 0;
            }
            case "local_time_curve" -> {
                requireExactFields(params, Set.of("points"), "local_time_curve params");
                JsonNode points = requireArray(params, "points", "local_time_curve");
                require(points.size() >= 2 && points.size() <= 48,
                        "local_time_curve points must contain 2 through 48 entries");
                int lastMinute = -1;
                for (JsonNode point : points) {
                    require(point.isArray() && point.size() == 2
                                    && point.get(0).canConvertToInt() && point.get(1).isNumber(),
                            "local_time_curve point must be [minute,value]");
                    int minute = point.get(0).asInt();
                    require(minute >= 0 && minute <= 1439 && minute > lastMinute,
                            "local_time_curve minutes must be ordered and within one day");
                    require(finite(point.get(1).asDouble())
                                    && point.get(1).asDouble() >= -2
                                    && point.get(1).asDouble() <= 2,
                            "local_time_curve value is out of range");
                    lastMinute = minute;
                }
                yield 0;
            }
            default -> throw new IllegalArgumentException("unsupported animation node type: " + type);
        };
    }

    private int validateParticleEmitter(JsonNode params, boolean sprite) {
        Set<String> expected = new HashSet<>(Set.of(
                "count", "shape", "color", "size_min", "size_max", "speed_min",
                "speed_max", "lifetime_min", "lifetime_max", "direction", "spread"));
        if (sprite) expected.add("asset");
        requireExactFields(params, expected, sprite ? "sprite_emitter params" : "particle_emitter params");
        int count = requireIntRange(params, "count", 0, limits.maxParticles(),
                sprite ? "sprite_emitter" : "particle_emitter");
        String shape = requireText(params, "shape", "particle emitter");
        require(Set.of("circle", "petal", "star", "square", "sprite").contains(shape),
                "particle emitter shape is invalid");
        require(sprite == "sprite".equals(shape), "sprite_emitter must use sprite shape only");
        if (sprite) requireImagePath(requireText(params, "asset", "sprite_emitter"), "sprite asset");
        requireColor(params, "color", "particle emitter");
        requireOrderedRange(params, "size_min", "size_max", 0.1, 256, "particle emitter");
        requireOrderedRange(params, "speed_min", "speed_max", 0, 1000, "particle emitter");
        requireOrderedRange(params, "lifetime_min", "lifetime_max", 0.05, 120,
                "particle emitter");
        requireNumberRange(params, "direction", -1000, 1000, "particle emitter");
        requireNumberRange(params, "spread", 0, Math.PI * 2, "particle emitter");
        return count;
    }

    private void assertCapabilitiesMatch(JsonNode capabilities, JsonNode animation) {
        boolean pointer = containsNodeType(animation, "pointer_field");
        boolean localTime = containsNodeType(animation, "local_time_curve");
        require(capabilities.path("pointer").asBoolean() == pointer,
                "manifest pointer capability does not match animation IR");
        require(capabilities.path("local_time").asBoolean() == localTime,
                "manifest local_time capability does not match animation IR");
    }

    private static boolean containsNodeType(JsonNode animation, String type) {
        for (JsonNode layer : animation.path("layers")) {
            for (JsonNode node : layer.path("nodes")) {
                if (type.equals(node.path("type").asText())) return true;
            }
        }
        return false;
    }

    private List<CentralEntry> parseCentralDirectory(byte[] archive) {
        int eocd = findSignatureBackwards(archive, 0x06054b50);
        require(eocd >= 0, "ZIP end-of-central-directory record is missing");
        require(readU16(archive, eocd + 4) == 0 && readU16(archive, eocd + 6) == 0,
                "multi-disk ZIP packages are forbidden");
        require(readU16(archive, eocd + 20) == 0, "ZIP archive comments are forbidden");
        int entryCount = readU16(archive, eocd + 10);
        long centralSize = readU32(archive, eocd + 12);
        long centralOffset = readU32(archive, eocd + 16);
        require(entryCount != 0xffff && centralSize != 0xffffffffL && centralOffset != 0xffffffffL,
                "ZIP64 packages are forbidden in schema v1");
        require(entryCount > 0 && entryCount <= limits.maxEntries(), "ZIP entry limit exceeded");
        require(centralOffset + centralSize <= eocd, "invalid ZIP central directory bounds");

        List<CentralEntry> entries = new ArrayList<>();
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        Map<String, Boolean> entryKinds = new HashMap<>();
        int cursor = Math.toIntExact(centralOffset);
        String previous = null;
        for (int i = 0; i < entryCount; i++) {
            require(readU32(archive, cursor) == 0x02014b50L, "invalid ZIP central directory entry");
            int madeBy = readU16(archive, cursor + 4);
            int flags = readU16(archive, cursor + 8);
            int method = readU16(archive, cursor + 10);
            int dosTime = readU16(archive, cursor + 12);
            int dosDate = readU16(archive, cursor + 14);
            long compressed = readU32(archive, cursor + 20);
            long expanded = readU32(archive, cursor + 24);
            int nameLength = readU16(archive, cursor + 28);
            int extraLength = readU16(archive, cursor + 30);
            int commentLength = readU16(archive, cursor + 32);
            long externalAttributes = readU32(archive, cursor + 38);
            int next = cursor + 46 + nameLength + extraLength + commentLength;
            require(nameLength > 0 && next <= archive.length, "invalid ZIP entry metadata");
            String path = decodeUtf8(archive, cursor + 46, nameLength);
            requireSafePath(path);
            require(commentLength == 0, "ZIP entry comments are forbidden: " + path);
            require((flags & 1) == 0, "encrypted ZIP entries are forbidden: " + path);
            require(method == 0 || method == 8, "unsupported ZIP compression method: " + path);
            require(dosTime == 0 && dosDate == 0x21,
                    "ZIP entries must use the canonical 1980-01-01 timestamp: " + path);
            require(compressed <= limits.maxCompressedBytes(), "compressed entry limit exceeded: " + path);
            require(expanded <= limits.maxExpandedBytes(), "expanded entry limit exceeded: " + path);
            if (expanded > 0) {
                require(compressed > 0, "invalid zero compressed size: " + path);
                require(expanded <= compressed * (long) limits.maxCompressionRatio(),
                        "compression ratio limit exceeded: " + path);
            }
            int hostSystem = (madeBy >>> 8) & 0xff;
            int unixType = ((int) (externalAttributes >>> 16)) & 0170000;
            if (hostSystem == 3 && unixType != 0) {
                require(unixType == 0100000 || unixType == 0040000,
                        "non-regular ZIP entry is forbidden: " + path);
            }
            require(exact.add(path), "duplicate ZIP path: " + path);
            require(folded.add(path.toLowerCase(Locale.ROOT)),
                    "case-folded ZIP path collision: " + path);
            String collisionKey = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            Boolean previousDirectory = entryKinds.putIfAbsent(collisionKey, path.endsWith("/"));
            require(previousDirectory == null || previousDirectory == path.endsWith("/"),
                    "ZIP file/directory path collision: " + path);
            require(previous == null || previous.compareTo(path) < 0,
                    "ZIP entries must be in canonical path order");
            previous = path;
            rejectForbiddenExtension(path);
            entries.add(new CentralEntry(path, compressed, expanded, path.endsWith("/")));
            cursor = next;
        }
        require(cursor == centralOffset + centralSize, "ZIP central directory size mismatch");
        return entries;
    }

    private Map<String, byte[]> readZipEntries(byte[] archive, List<CentralEntry> central)
            throws IOException {
        Map<String, CentralEntry> metadata = new HashMap<>();
        central.forEach(entry -> metadata.put(entry.path(), entry));
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                CentralEntry expected = metadata.get(path);
                require(expected != null, "local ZIP entry is absent from central directory: " + path);
                if (entry.isDirectory() || expected.directory()) {
                    require(entry.isDirectory() && expected.directory(),
                            "ZIP file/directory metadata mismatch: " + path);
                    zip.closeEntry();
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(
                        (int) Math.min(expected.expandedSize(), 8192));
                byte[] buffer = new byte[8192];
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    total += read;
                    require(entryBytes <= limits.maxExpandedBytes(),
                            "expanded entry limit exceeded: " + path);
                    require(total <= limits.maxExpandedBytes(), "total expanded package limit exceeded");
                    out.write(buffer, 0, read);
                }
                require(entryBytes == expected.expandedSize(),
                        "ZIP expanded size mismatch: " + path);
                require(files.put(path, out.toByteArray()) == null, "duplicate local ZIP path: " + path);
                zip.closeEntry();
            }
        }
        long expectedFiles = central.stream().filter(entry -> !entry.directory()).count();
        require(files.size() == expectedFiles, "ZIP local/central inventory mismatch");
        require(files.size() <= limits.maxEntries(), "ZIP entry limit exceeded");
        long rasterCount = files.keySet().stream().filter(ThemePackageSpec::isImagePath).count();
        require(rasterCount <= limits.maxRasterAssets(), "raster asset limit exceeded");
        files.forEach((path, content) -> {
            if (path.endsWith(".json")) {
                require(content.length <= limits.maxJsonBytes(), "JSON byte limit exceeded: " + path);
            }
            require(content.length > 0, "empty package file is forbidden: " + path);
        });
        return files;
    }

    private JsonNode parseJson(byte[] content, String path) throws IOException {
        require(content.length <= limits.maxJsonBytes(), "JSON byte limit exceeded: " + path);
        JsonNode node = json.readTree(content);
        require(node != null, "JSON document is empty: " + path);
        return node;
    }

    private static byte[] requiredComponent(
            Map<String, byte[]> files, JsonNode components, String field) {
        require(components.hasNonNull(field), "required component is null: " + field);
        return requiredFile(files, components.path(field).asText());
    }

    private static void requireComponentPresence(
            Map<String, byte[]> files, JsonNode components, String field) {
        if (components.hasNonNull(field)) requiredFile(files, components.path(field).asText());
    }

    private static byte[] requiredFile(Map<String, byte[]> files, String path) {
        byte[] content = files.get(path);
        require(content != null, "required package file is missing: " + path);
        return content;
    }

    private static byte[] readBounded(InputStream input, int limit, String label) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            require(total <= limit, label + " limit exceeded");
            out.write(buffer, 0, read);
        }
        require(total > 0, label + " is empty");
        return out.toByteArray();
    }

    private static void requireLocalizedString(JsonNode node, String label) {
        requireObject(node, label);
        require(node.size() > 0, label + " must not be empty");
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            require(LOCALE.matcher(field.getKey()).matches(), label + " has invalid locale key");
            require(field.getValue().isTextual() && !field.getValue().asText().isBlank(),
                    label + " values must be non-blank strings");
            require(field.getValue().asText().length() <= 1024, label + " value is too long");
        }
        require(node.has("en"), label + ".en is required");
    }

    private static void requireVersion(JsonNode root, String label) {
        require(root.path("schema_version").canConvertToInt()
                        && root.path("schema_version").asInt() == SCHEMA_VERSION,
                label + ".schema_version must be 1");
    }

    private static void requireSemver(JsonNode root, String field, String label) {
        String value = requireText(root, field, label);
        require(SEMVER.matcher(value).matches(), label + "." + field + " must be SemVer");
    }

    private static String requirePath(JsonNode root, String field, boolean nullable) {
        require(root.has(field), field + " path is required");
        if (root.get(field).isNull()) {
            require(nullable, field + " path cannot be null");
            return null;
        }
        String path = requireText(root, field, "path");
        requireSafePath(path);
        return path;
    }

    private static void requireSafePath(String path) {
        require(path != null && !path.isBlank(), "package path must not be blank");
        require(path.equals(Normalizer.normalize(path, Normalizer.Form.NFC)),
                "package path must be NFC-normalized: " + path);
        require(!path.startsWith("/") && !path.startsWith("\\") && !path.contains("\\"),
                "absolute or backslash package path is forbidden: " + path);
        require(!path.matches("^[A-Za-z]:.*"), "drive-prefixed package path is forbidden: " + path);
        require(path.indexOf('\0') < 0 && path.chars().noneMatch(c -> c < 0x20 || c == 0x7f),
                "control character in package path: " + path);
        String candidate = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        require(!candidate.isBlank(), "root directory entry is forbidden");
        for (String segment : candidate.split("/", -1)) {
            require(!segment.isBlank() && !".".equals(segment) && !"..".equals(segment),
                    "unsafe package path segment: " + path);
        }
    }

    private static void rejectForbiddenExtension(String path) {
        if (path.endsWith("/")) return;
        String lower = path.toLowerCase(Locale.ROOT);
        for (String extension : FORBIDDEN_EXTENSIONS) {
            require(!lower.endsWith(extension), "forbidden package file type: " + path);
        }
        boolean allowed = lower.equals("manifest.json")
                || lower.equals("integrity.json")
                || lower.equals("license.txt")
                || lower.equals("signature.ed25519")
                || lower.equals("source/project.json")
                || lower.startsWith("theme/") && lower.endsWith(".json")
                || lower.startsWith("animation/") && lower.endsWith(".json")
                || isAllowedImageLocation(lower);
        require(allowed, "undeclared package file location or type: " + path);
    }

    private static boolean isImagePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static boolean isAllowedImageLocation(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return isImagePath(lower) && (lower.startsWith("background/")
                || lower.startsWith("icon/")
                || lower.startsWith("previews/")
                || lower.startsWith("animation/assets/"));
    }

    private static void requireImagePath(String path, String label) {
        requireSafePath(path);
        require(isImagePath(path), label + " must be PNG, JPEG, or WebP");
    }

    private static void requireExtension(String value, String extension, String label) {
        require(value != null && value.toLowerCase(Locale.ROOT).endsWith(extension),
                label + " must end with " + extension);
    }

    private static void requireColor(JsonNode root, String field, String label) {
        require(isColor(requireText(root, field, label)), label + "." + field + " is invalid");
    }

    private static boolean isColor(String value) {
        if (value == null) return false;
        if (HEX_COLOR.matcher(value).matches()) return true;
        Matcher matcher = RGB_COLOR.matcher(value);
        if (!matcher.matches()) return false;
        boolean alpha = value.regionMatches(true, 0, "rgba", 0, 4);
        String[] parts = matcher.group(1).split(",", -1);
        if (parts.length != (alpha ? 4 : 3)) return false;
        try {
            for (int i = 0; i < 3; i++) {
                String channel = parts[i].trim();
                if (!channel.matches("\\d{1,3}")) return false;
                int number = Integer.parseInt(channel);
                if (number < 0 || number > 255) return false;
            }
            if (alpha) {
                BigDecimal opacity = new BigDecimal(parts[3].trim());
                if (opacity.compareTo(BigDecimal.ZERO) < 0
                        || opacity.compareTo(BigDecimal.ONE) > 0) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static JsonNode requireObject(JsonNode node, String label) {
        require(node != null && node.isObject(), label + " must be an object");
        return node;
    }

    private static JsonNode requireArray(JsonNode root, String field, String label) {
        JsonNode node = root.get(field);
        require(node != null && node.isArray(), label + "." + field + " must be an array");
        return node;
    }

    private static String requireText(JsonNode root, String field, String label) {
        require(root != null && root.has(field) && root.get(field).isTextual()
                        && !root.get(field).asText().isBlank(),
                label + "." + field + " must be a non-blank string");
        return root.get(field).asText();
    }

    private static void requireBoolean(JsonNode root, String field, String label) {
        require(root.has(field) && root.get(field).isBoolean(),
                label + "." + field + " must be a boolean");
    }

    private static int requireIntRange(
            JsonNode root, String field, int min, int max, String label) {
        JsonNode value = root.get(field);
        require(value != null && value.isIntegralNumber() && value.canConvertToInt(),
                label + "." + field + " must be an integer");
        int number = value.asInt();
        require(number >= min && number <= max,
                label + "." + field + " must be between " + min + " and " + max);
        return number;
    }

    private static double requireNumberRange(
            JsonNode root, String field, double min, double max, String label) {
        JsonNode value = root.get(field);
        require(value != null && value.isNumber() && finite(value.asDouble()),
                label + "." + field + " must be a finite number");
        double number = value.asDouble();
        require(number >= min && number <= max,
                label + "." + field + " is out of range");
        return number;
    }

    private static void requireOrderedRange(
            JsonNode root, String minField, String maxField,
            double lower, double upper, String label) {
        double min = requireNumberRange(root, minField, lower, upper, label);
        double max = requireNumberRange(root, maxField, lower, upper, label);
        require(min <= max, label + "." + minField + " must not exceed " + maxField);
    }

    private static void requireId(String id, String label) {
        require(ID.matcher(id).matches(), label + " is invalid");
    }

    private static void requireExactFields(JsonNode node, Set<String> expected, String label) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<String> unknown = new HashSet<>(actual);
        unknown.removeAll(expected);
        require(missing.isEmpty(), label + " missing fields: " + missing);
        require(unknown.isEmpty(), label + " contains unknown fields: " + unknown);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static int findSignatureBackwards(byte[] bytes, int signature) {
        int min = Math.max(0, bytes.length - 65_557);
        for (int i = bytes.length - 22; i >= min; i--) {
            if (readU32(bytes, i) == Integer.toUnsignedLong(signature)) return i;
        }
        return -1;
    }

    private static int readU16(byte[] bytes, int offset) {
        require(offset >= 0 && offset + 2 <= bytes.length, "truncated ZIP metadata");
        return Short.toUnsignedInt(ByteBuffer.wrap(bytes, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static long readU32(byte[] bytes, int offset) {
        require(offset >= 0 && offset + 4 <= bytes.length, "truncated ZIP metadata");
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static String decodeUtf8(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("ZIP path is not valid UTF-8", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record CentralEntry(
            String path, long compressedSize, long expandedSize, boolean directory) {}

    public record Limits(
            int maxCompressedBytes,
            int maxExpandedBytes,
            int maxEntries,
            int maxCompressionRatio,
            int maxJsonBytes,
            int maxJsonDepth,
            int maxRasterAssets,
            int maxRasterPixels,
            int maxRasterDimension,
            int maxAnimationLayers,
            int maxAnimationNodes,
            int maxAnimationDepth,
            int maxParticles) {

        public static Limits defaults() {
            return new Limits(
                    20 * 1024 * 1024,
                    60 * 1024 * 1024,
                    200,
                    50,
                    256 * 1024,
                    32,
                    12,
                    32_000_000,
                    8192,
                    32,
                    512,
                    16,
                    600);
        }
    }
}
