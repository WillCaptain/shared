package org.twelve.aipp.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThemePackageSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final LocalDateTime ZIP_EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private final ThemePackageSpec spec = new ThemePackageSpec();

    @Test
    void validatesCompiledStandardPackagesWhenPresent() throws Exception {
        java.nio.file.Path packagesDir = java.nio.file.Path.of(
                "..", "..", "shared", "theme", "packages").normalize();
        if (!java.nio.file.Files.isDirectory(packagesDir)) return;
        try (var stream = java.nio.file.Files.list(packagesDir)) {
            var packages = stream
                    .filter(path -> path.toString().endsWith(".ones-theme"))
                    .sorted()
                    .toList();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    !packages.isEmpty(),
                    "Run shared/theme/generate-standard-theme-packages.mjs --compile first");
            for (var pkg : packages) {
                assertThatNoException().isThrownBy(
                        () -> spec.assertValidPackage(java.nio.file.Files.newInputStream(pkg)));
            }
        }
    }

    @Test
    void standardPackageSourcesPreserveDeclaredThemeDefaultsWhenPresent() throws Exception {
        var themeDir = java.nio.file.Path.of("..", "..", "shared", "theme").normalize();
        var themesPath = themeDir.resolve("aipp-themes.json");
        if (!java.nio.file.Files.isRegularFile(themesPath)) return;

        JsonNode themes = JSON.readTree(java.nio.file.Files.readString(themesPath));
        var presets = themes.path("presets").fields();
        while (presets.hasNext()) {
            var entry = presets.next();
            String presetId = entry.getKey();
            JsonNode preset = entry.getValue();
            if (!preset.path("standard").asBoolean(false)) continue;

            var packageDir = themeDir.resolve("packages/ones.standard." + presetId);
            JsonNode manifest = JSON.readTree(
                    java.nio.file.Files.readString(packageDir.resolve("manifest.json")));
            JsonNode shell = JSON.readTree(
                    java.nio.file.Files.readString(packageDir.resolve("theme/shell.json")));

            JsonNode background = preset.path("background");
            if ("preset".equals(background.path("kind").asText())
                    && !background.path("id").asText().isBlank()) {
                assertThat(manifest.path("components").path("background").isTextual())
                        .as("%s must package its declared background default", presetId)
                        .isTrue();
                assertThat(shell.path("background").path("kind").asText())
                        .as("%s shell background", presetId)
                        .isEqualTo("asset");
            }

            if (preset.path("icon").hasNonNull("id")) {
                assertThat(manifest.path("components").path("icon").isTextual())
                        .as("%s must package its declared icon default", presetId)
                        .isTrue();
                assertThat(shell.path("icon").path("kind").asText())
                        .as("%s shell icon", presetId)
                        .isEqualTo("asset");
            }

            if (!"none".equals(preset.path("bgAnimation").asText("none"))) {
                JsonNode animation = JSON.readTree(java.nio.file.Files.readString(
                        packageDir.resolve("animation/program.json")));
                assertThat(animation.path("layers").isEmpty())
                        .as("%s must package its declared animation default", presetId)
                        .isFalse();
            }
        }
    }

    @Test
    void validatesDeterministicCompleteMikuFixture() throws Exception {
        byte[] first = validPackage();
        byte[] second = validPackage();

        assertThat(first).isEqualTo(second);
        assertThatNoException().isThrownBy(
                () -> spec.assertValidPackage(new ByteArrayInputStream(first)));
    }

    @Test
    void validatedPackageReaderExposesOnlyDefensivePackageLocalData() throws Exception {
        ThemePackageSpec.ValidatedPackage pkg = spec.readValidatedPackage(
                new ByteArrayInputStream(validPackage()));

        assertThat(pkg.manifest().path("schema_version").asInt()).isEqualTo(1);
        assertThat(pkg.tokens().path("accent").asText()).isEqualTo("#39C5BB");
        assertThat(pkg.paths()).contains(
                "theme/tokens.json", "theme/shell.json", "background/background.png");

        byte[] firstRead = pkg.file("icon/icon.png");
        firstRead[0] = 0;
        assertThat(pkg.file("icon/icon.png")[0]).isNotZero();
        ObjectNode changed = (ObjectNode) pkg.tokens();
        changed.put("accent", "#000000");
        assertThat(pkg.tokens().path("accent").asText()).isEqualTo("#39C5BB");
    }

    @Test
    void acceptsGradientLineAndSandboxSafeEffectsCss() throws Exception {
        assertThatNoException().isThrownBy(() -> spec.assertValidEffectsCss("""
                .theme-effects::before {
                  content: "";
                  position: absolute;
                  inset: 0;
                  background: linear-gradient(120deg, transparent, rgba(34,211,182,.2), transparent);
                  animation: confluence-shift 12s ease-in-out infinite alternate;
                }
                @keyframes confluence-shift {
                  from { transform: translate3d(-8%, 0, 0); opacity: .35; }
                  to { transform: translate3d(8%, 0, 0); opacity: .7; }
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ThemePackageSpec.ValidatedPackage pkg = spec.readValidatedPackage(
                new ByteArrayInputStream(validPackage()));
        ObjectNode shell = (ObjectNode) pkg.shell();
        shell.put("atmosphere", "gradient-line");
        ObjectNode chrome = shell.putObject("chrome");
        chrome.put("mode", "luminous-lines");
        chrome.put("line", "#7CFFF4");
        chrome.put("line_alt", "#FF9B62");
        chrome.put("glow", "rgba(53,242,228,0.34)");
        chrome.put("glow_alt", "rgba(255,107,61,0.24)");
        assertThatNoException().isThrownBy(() -> spec.assertValidShell(shell, pkg.manifest()));

        chrome.put("line", "url(https://evil.invalid/line)");
        assertThatThrownBy(() -> spec.assertValidShell(shell, pkg.manifest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNetworkMarkupAndExecutableEffectsCss() {
        for (String unsafe : java.util.List.of(
                "@import 'https://evil.invalid/x.css';",
                ".theme-effects{background:url(https://evil.invalid/x)}",
                "</style><script>alert(1)</script>",
                ".theme-effects{width:expression(alert(1))}",
                ".theme-effects{b\\65havior:url(x)}")) {
            assertThatThrownBy(() -> spec.assertValidEffectsCss(
                    unsafe.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void validatesDocumentsIndependently() throws Exception {
        Map<String, byte[]> files = validFiles();
        JsonNode manifest = JSON.readTree(files.get("manifest.json"));

        assertThatNoException().isThrownBy(() -> spec.assertValidManifest(manifest));
        assertThatNoException().isThrownBy(
                () -> spec.assertValidTokens(JSON.readTree(files.get("theme/tokens.json"))));
        assertThatNoException().isThrownBy(
                () -> spec.assertValidShell(JSON.readTree(files.get("theme/shell.json")), manifest));
        assertThatNoException().isThrownBy(
                () -> spec.assertValidAnimation(JSON.readTree(files.get("animation/program.json")), false));
        assertThatNoException().isThrownBy(
                () -> spec.assertValidAnimation(JSON.readTree(files.get("animation/fallback.json")), true));
    }

    @Test
    void validatesBoundedWindMotionForPackageLocalSpriteOverlays() throws Exception {
        JsonNode program = JSON.readTree("""
                {
                  "schema_version": 1,
                  "fps": 60,
                  "max_particles": 0,
                  "layers": [{
                    "id": "wind_layer",
                    "blend": "source-over",
                    "opacity": 1,
                    "nodes": [{
                      "id": "cape",
                      "type": "sprite_overlay",
                      "params": {
                        "asset": "animation/assets/cape.png",
                        "x": 0,
                        "y": 0.41,
                        "width_min": 420,
                        "width_vw": 80,
                        "width_max": 1672,
                        "opacity": 0.96,
                        "blend": "source-over",
                        "rotation_seconds": 0,
                        "origin_x": 0.9,
                        "origin_y": 0.1,
                        "wind_seconds": 12,
                        "wind_angle": 0.7,
                        "wind_skew": 0.9
                      }
                    }]
                  }]
                }
                """);

        assertThatNoException().isThrownBy(() -> spec.assertValidAnimation(program, false));

        ((ObjectNode) program.path("layers").get(0).path("nodes").get(0).path("params"))
                .put("wind_angle", 30);
        assertThatThrownBy(() -> spec.assertValidAnimation(program, false))
                .hasMessageContaining("wind_angle");
    }

    @Test
    void rejectsUnknownManifestFieldsAndUnsafeTokenValues() throws Exception {
        ObjectNode manifest = (ObjectNode) JSON.readTree(validFiles().get("manifest.json"));
        manifest.put("future_runtime_code", "do-not-ignore");
        assertThatThrownBy(() -> spec.assertValidManifest(manifest))
                .hasMessageContaining("unknown fields");

        ObjectNode tokens = (ObjectNode) JSON.readTree(validFiles().get("theme/tokens.json"));
        tokens.put("accent", "var(--attacker-color)");
        assertThatThrownBy(() -> spec.assertValidTokens(tokens))
                .hasMessageContaining("strict color");
    }

    @Test
    void rejectsAnimationBudgetAndFallbackPointerInput() throws Exception {
        ObjectNode program = (ObjectNode) JSON.readTree(validFiles().get("animation/program.json"));
        program.put("max_particles", 10);
        assertThatThrownBy(() -> spec.assertValidAnimation(program, false))
                .hasMessageContaining("exceed max_particles");

        ObjectNode fallback = (ObjectNode) JSON.readTree(validFiles().get("animation/fallback.json"));
        ((ArrayNode) fallback.withArray("layers").get(0).get("nodes")).add(JSON.readTree("""
                {"id":"mouse","type":"pointer_field",
                 "params":{"radius":0.4,"strength":1.0,"swirl":2.0}}
                """));
        assertThatThrownBy(() -> spec.assertValidAnimation(fallback, true))
                .hasMessageContaining("fallback cannot contain pointer_field");
    }

    @Test
    void rejectsCapabilityMismatchAndIntegrityTampering() throws Exception {
        Map<String, byte[]> mismatch = validFiles();
        ObjectNode manifest = (ObjectNode) JSON.readTree(mismatch.get("manifest.json"));
        ((ObjectNode) manifest.get("capabilities")).put("pointer", false);
        mismatch.put("manifest.json", canonical(manifest));
        mismatch.put("integrity.json", integrityFor(mismatch));
        assertThatThrownBy(() -> spec.assertValidPackage(new ByteArrayInputStream(zip(mismatch))))
                .hasMessageContaining("pointer capability");

        Map<String, byte[]> tampered = validFiles();
        tampered.put("LICENSE.txt", "tampered\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> spec.assertValidPackage(new ByteArrayInputStream(zip(tampered))))
                .hasMessageContaining("integrity");
    }

    @Test
    void rejectsTraversalCaseCollisionAndExecutableEntries() throws Exception {
        assertThatThrownBy(() -> spec.assertValidPackage(
                new ByteArrayInputStream(zip(Map.of("../manifest.json", bytes("{}"))))))
                .hasMessageContaining("unsafe package path");

        Map<String, byte[]> collision = new TreeMap<>();
        collision.put("previews/card.png", ONE_PIXEL_PNG);
        collision.put("previews/Card.png", ONE_PIXEL_PNG);
        assertThatThrownBy(() -> spec.assertValidPackage(new ByteArrayInputStream(zip(collision))))
                .hasMessageContaining("case-folded");

        assertThatThrownBy(() -> spec.assertValidPackage(
                new ByteArrayInputStream(zip(Map.of("theme/payload.js", bytes("alert(1)"))))))
                .hasMessageContaining("forbidden package file type");
    }

    @Test
    void rejectsSymlinkMetadataAndCompressionBomb() throws Exception {
        byte[] regular = zip(Map.of("manifest.json", bytes("{}")));
        byte[] symlink = regular.clone();
        int central = findSignature(symlink, 0x02014b50);
        writeU16(symlink, central + 4, 0x0314);
        writeU32(symlink, central + 38, (0120000L | 0777L) << 16);
        assertThatThrownBy(() -> spec.assertValidPackage(new ByteArrayInputStream(symlink)))
                .hasMessageContaining("non-regular");

        byte[] repeated = new byte[1_000_000];
        assertThatThrownBy(() -> spec.assertValidPackage(
                new ByteArrayInputStream(zip(Map.of("previews/bomb.png", repeated)))))
                .hasMessageContaining("compression ratio");
    }

    @Test
    void exposesNormativeLimits() {
        ThemePackageSpec.Limits limits = spec.limits();
        assertThat(limits.maxCompressedBytes()).isEqualTo(20 * 1024 * 1024);
        assertThat(limits.maxExpandedBytes()).isEqualTo(60 * 1024 * 1024);
        assertThat(limits.maxAnimationNodes()).isEqualTo(512);
        assertThat(limits.maxParticles()).isEqualTo(600);
    }

    @Test
    void shipsParseableMachineReadableSchemaBundle() throws Exception {
        try (var input = ThemePackageSpec.class.getResourceAsStream(
                "/theme-packages/v1/theme-package-v1.schema.json")) {
            assertThat(input).isNotNull();
            JsonNode schema = JSON.readTree(input);
            assertThat(schema.path("$defs").path("manifest").path("type").asText())
                    .isEqualTo("object");
            assertThat(schema.path("$defs").path("animation").path("properties")
                    .path("max_particles").path("maximum").asInt()).isEqualTo(600);
            assertThat(schema.path("$defs").path("animationNode").path("properties")
                    .path("type").path("enum").toString()).contains(
                            "pointer_swirl", "pulse_rings", "magic_mist", "rune_orbit",
                            "light_ribbon", "petal_drift", "aurora_drift", "cyber_scan",
                            "sprite_overlay");
            assertThat(schema.toString()).contains(
                    "wind_seconds", "wind_angle", "wind_skew", "origin_x", "origin_y");
        }
    }

    private static byte[] validPackage() throws Exception {
        return zip(validFiles());
    }

    private static Map<String, byte[]> validFiles() throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("LICENSE.txt", bytes("Original fan-art fixture for protocol tests.\n"));
        files.put("animation/fallback.json", canonical(JSON.readTree("""
                {
                  "schema_version": 1,
                  "fps": 30,
                  "max_particles": 60,
                  "layers": [{
                    "id": "gentle_petals",
                    "blend": "source-over",
                    "opacity": 0.35,
                    "nodes": [{
                      "id": "petals_fallback",
                      "type": "particle_emitter",
                      "params": {
                        "count": 60,
                        "shape": "petal",
                        "color": "#39C5BB",
                        "size_min": 2,
                        "size_max": 6,
                        "speed_min": 3,
                        "speed_max": 14,
                        "lifetime_min": 4,
                        "lifetime_max": 12,
                        "direction": 1.57,
                        "spread": 0.5
                      }
                    }]
                  }]
                }
                """)));
        files.put("animation/program.json", canonical(JSON.readTree("""
                {
                  "schema_version": 1,
                  "fps": 60,
                  "max_particles": 180,
                  "layers": [{
                    "id": "digital_bloom",
                    "blend": "lighter",
                    "opacity": 0.6,
                    "nodes": [
                      {
                        "id": "mouse_swirl",
                        "type": "pointer_field",
                        "params": {"radius": 0.45, "strength": 1.2, "swirl": 3.5}
                      },
                      {
                        "id": "visible_swirl",
                        "type": "pointer_swirl",
                        "params": {
                          "radius": 132, "arms": 3, "turns": 0.75,
                          "speed": 2.4, "width": 2.4, "flatten": 0.72,
                          "spark_count": 24, "color": "#39C5BB",
                          "color_alt": "#E95388", "intensity": 0.82
                        }
                      },
                      {
                        "id": "energy_gate",
                        "type": "pulse_rings",
                        "params": {
                          "x": 0.5, "y": 0.45, "radius_min": 20, "radius_max": 240,
                          "count": 6, "speed": 0.3, "flatten": 0.3, "width": 2,
                          "blur": 12, "color": "#39C5BB", "color_alt": "#E95388",
                          "intensity": 0.8
                        }
                      },
                      {
                        "id": "arcane_mist",
                        "type": "magic_mist",
                        "params": {
                          "x": 0.5, "y": 0.6, "radius": 160, "orbit_radius": 140,
                          "blobs": 7, "speed": 0.2, "blur": 40,
                          "color": "#39C5BB", "color_alt": "#E95388", "intensity": 0.7
                        }
                      },
                      {
                        "id": "arcane_runes",
                        "type": "rune_orbit",
                        "params": {
                          "x": 0.5, "y": 0.6, "radius_min": 40, "radius_max": 200,
                          "rings": 4, "glyphs": 36, "speed": 0.2, "width": 1.5,
                          "flatten": 0.6, "blur": 8, "color": "#39C5BB",
                          "color_alt": "#E95388", "intensity": 0.8
                        }
                      },
                      {
                        "id": "sky_veil",
                        "type": "light_ribbon",
                        "params": {
                          "y": 0.24, "amplitude": 0.06, "wavelength": 1.2,
                          "speed": 0.12, "phase": 0.4, "width": 8, "blur": 24,
                          "color": "#39C5BB", "intensity": 0.6,
                          "packet_count": 5, "packet_size": 4
                        }
                      },
                      {
                        "id": "petals",
                        "type": "particle_emitter",
                        "params": {
                          "count": 180,
                          "shape": "petal",
                          "color": "#39C5BB",
                          "size_min": 2,
                          "size_max": 8,
                          "speed_min": 4,
                          "speed_max": 30,
                          "lifetime_min": 2,
                          "lifetime_max": 12,
                          "direction": 1.57,
                          "spread": 0.8
                        }
                      }
                    ]
                  }]
                }
                """)));
        files.put("background/background.png", ONE_PIXEL_PNG);
        files.put("icon/icon.png", ONE_PIXEL_PNG);
        files.put("manifest.json", canonical(JSON.readTree("""
                {
                  "schema_version": 1,
                  "package_id": "ones.standard.hatsune-miku",
                  "version": "1.0.0",
                  "name": {"en": "Hatsune Miku", "zh": "初音未来"},
                  "description": {
                    "en": "Turquoise virtual-stage colors with an original character portrait.",
                    "zh": "苍绿色虚拟舞台配色与原创角色形象。"
                  },
                  "publisher": {"id": "ones", "display_name": "Ones"},
                  "min_host_version": "1.0.0",
                  "components": {
                    "tokens": "theme/tokens.json",
                    "shell": "theme/shell.json",
                    "background": "background/background.png",
                    "animation": "animation/program.json",
                    "animation_fallback": "animation/fallback.json",
                    "icon": "icon/icon.png"
                  },
                  "capabilities": {
                    "pointer": true,
                    "local_time": false,
                    "reduced_motion": true
                  },
                  "license": "LICENSE.txt",
                  "integrity": "integrity.json"
                }
                """)));
        files.put("theme/shell.json", canonical(JSON.readTree("""
                {
                  "schema_version": 1,
                  "dark_mode": true,
                  "atmosphere": "glass-neon",
                  "fx": {"glow": "soft", "motion": "reduced"},
                  "background": {
                    "kind": "asset",
                    "opacity": 0.55,
                    "overlay": 0.30,
                    "focal_x": 0.50,
                    "focal_y": 0.50
                  },
                  "icon": {"kind": "asset"}
                }
                """)));
        files.put("theme/tokens.json", canonical(JSON.readTree("""
                {
                  "schema_version": 1,
                  "bg": "#061315",
                  "surface": "#0b1d20",
                  "surface2": "#102a2d",
                  "surface3": "#163b3f",
                  "text": "#dcfffb",
                  "textDim": "#91d8d2",
                  "textMuted": "#5f9e9a",
                  "border": "#17494b",
                  "border2": "#237174",
                  "accent": "#39C5BB",
                  "accentHover": "#68E0D7",
                  "accentGlow": "rgba(57,197,187,0.30)",
                  "active": "rgba(57,197,187,0.18)",
                  "danger": "#E95388",
                  "success": "#4BD2A5",
                  "warning": "#F2C25B",
                  "info": "#48BFE3",
                  "font": "system-sans",
                  "fontMono": "system-mono",
                  "fontSize": 13,
                  "fontSizeSm": 11,
                  "fontSizeLg": 14,
                  "radius": 8,
                  "radiusSm": 5,
                  "radiusLg": 11,
                  "radiusPill": 999
                }
                """)));
        files.put("integrity.json", integrityFor(files));
        return files;
    }

    private static byte[] integrityFor(Map<String, byte[]> source) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("schema_version", 1);
        root.put("algorithm", "sha256");
        ObjectNode listed = root.putObject("files");
        new TreeMap<>(source).forEach((path, content) -> {
            if ("integrity.json".equals(path) || "signature.ed25519".equals(path)) return;
            ObjectNode metadata = listed.putObject(path);
            metadata.put("sha256", sha256(content));
            metadata.put("size", content.length);
        });
        return canonical(root);
    }

    private static byte[] canonical(JsonNode node) throws Exception {
        return (JSON.writeValueAsString(node) + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> source) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, java.nio.charset.StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> file : new TreeMap<>(source).entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTimeLocal(ZIP_EPOCH);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int findSignature(byte[] bytes, int signature) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            if (Integer.toUnsignedLong(ByteBuffer.wrap(bytes, i, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt())
                    == Integer.toUnsignedLong(signature)) return i;
        }
        throw new IllegalArgumentException("signature not found");
    }

    private static void writeU16(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) value);
    }

    private static void writeU32(byte[] bytes, int offset, long value) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) value);
    }
}
