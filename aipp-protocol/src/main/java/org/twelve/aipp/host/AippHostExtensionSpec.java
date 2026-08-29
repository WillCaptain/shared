package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Executable contract for declarative AIPP contributions to generic Host surfaces. */
public final class AippHostExtensionSpec {

    public static final int SCHEMA_VERSION = 1;
    public static final String REGISTER_BANNER_ICON = "register_banner_icon";
    public static final String REGISTER_BANNER_TAB = "register_banner_tab";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Pattern TOOL = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Pattern INTERFACE = Pattern.compile("shared\\.[a-z0-9.-]+/v[1-9]\\d*");
    private static final Pattern MODULE = Pattern.compile("/(?:[a-zA-Z0-9._-]+/)*[a-zA-Z0-9._-]+\\.js");

    public Map<String, Object> registerBannerIcon(
            String id, Map<String, String> label, String tool, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_ICON,
                "id", id,
                "label", label,
                "icon", "app",
                "action", Map.of("kind", "tool", "tool", tool),
                "order", order);
        assertValidBannerIcon(toNode(value));
        return value;
    }

    public Map<String, Object> registerBannerTab(
            String id, Map<String, String> label, String tool, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_TAB,
                "id", id,
                "label", label,
                "action", Map.of("kind", "tool", "tool", tool),
                "order", order);
        assertValidBannerTab(toNode(value));
        return value;
    }

    public Map<String, Object> provideInterface(
            String type, String module, String bootstrapTool, int probeIntervalMs) {
        Map<String, Object> value = Map.of(
                "type", type,
                "module", module,
                "bootstrap_tool", bootstrapTool,
                "probe_interval_ms", probeIntervalMs);
        assertValidInterfaceProvider(toNode(value));
        return value;
    }

    public Map<String, Object> extensions(
            List<Map<String, Object>> bannerIcons,
            List<Map<String, Object>> bannerTabs,
            List<Map<String, Object>> interfaceProviders) {
        Map<String, Object> value = Map.of(
                "schema_version", SCHEMA_VERSION,
                "banner_icons", List.copyOf(bannerIcons),
                "banner_tabs", List.copyOf(bannerTabs),
                "interface_providers", List.copyOf(interfaceProviders));
        assertValidHostExtensions(toNode(Map.of("host_extensions", value)));
        return value;
    }

    public void assertValidHostExtensions(JsonNode appManifest) {
        if (appManifest == null || !appManifest.has("host_extensions")) return;
        JsonNode root = requireObject(appManifest.get("host_extensions"), "host_extensions");
        requireExactFields(root,
                Set.of("schema_version", "banner_icons", "banner_tabs", "interface_providers"),
                "host_extensions");
        require(root.path("schema_version").isIntegralNumber()
                        && root.path("schema_version").asInt() == SCHEMA_VERSION,
                "host_extensions.schema_version must be 1");
        JsonNode icons = requireArray(root.get("banner_icons"), "host_extensions.banner_icons");
        JsonNode tabs = requireArray(root.get("banner_tabs"), "host_extensions.banner_tabs");
        JsonNode providers = requireArray(
                root.get("interface_providers"), "host_extensions.interface_providers");
        require(icons.size() <= 8, "an app may register at most 8 banner icons");
        require(tabs.size() <= 8, "an app may register at most 8 banner tabs");
        require(providers.size() <= 8, "an app may provide at most 8 Host interfaces");
        icons.forEach(this::assertValidBannerIcon);
        tabs.forEach(this::assertValidBannerTab);
        providers.forEach(this::assertValidInterfaceProvider);
        assertUniqueTextField(icons, "id", "banner icon id");
        assertUniqueTextField(tabs, "id", "banner tab id");
        assertUniqueTextField(providers, "type", "interface provider type");
    }

    public void assertValidBannerIcon(JsonNode value) {
        JsonNode icon = requireObject(value, "banner icon");
        requireExactFields(icon,
                Set.of("operation", "id", "label", "icon", "action", "order"),
                "banner icon");
        require(REGISTER_BANNER_ICON.equals(requiredText(icon, "operation", "banner icon")),
                "banner icon.operation must be register_banner_icon");
        requireId(requiredText(icon, "id", "banner icon"), "banner icon.id");
        assertLocalizedString(icon.get("label"), "banner icon.label");
        require("app".equals(requiredText(icon, "icon", "banner icon")),
                "banner icon.icon must be 'app'");
        assertToolAction(icon.get("action"), "banner icon.action");
        assertOrder(icon.get("order"), "banner icon.order");
    }

    public void assertValidBannerTab(JsonNode value) {
        JsonNode tab = requireObject(value, "banner tab");
        requireExactFields(tab, Set.of("operation", "id", "label", "action", "order"),
                "banner tab");
        require(REGISTER_BANNER_TAB.equals(requiredText(tab, "operation", "banner tab")),
                "banner tab.operation must be register_banner_tab");
        requireId(requiredText(tab, "id", "banner tab"), "banner tab.id");
        assertLocalizedString(tab.get("label"), "banner tab.label");
        assertToolAction(tab.get("action"), "banner tab.action");
        assertOrder(tab.get("order"), "banner tab.order");
    }

    public void assertValidInterfaceProvider(JsonNode value) {
        JsonNode provider = requireObject(value, "interface provider");
        requireExactFields(provider,
                Set.of("type", "module", "bootstrap_tool", "probe_interval_ms"),
                "interface provider");
        require(INTERFACE.matcher(requiredText(provider, "type", "interface provider")).matches(),
                "interface provider.type is invalid");
        require(MODULE.matcher(requiredText(provider, "module", "interface provider")).matches()
                        && !provider.path("module").asText().contains(".."),
                "interface provider.module must be a safe app-local JavaScript path");
        require(TOOL.matcher(requiredText(provider, "bootstrap_tool", "interface provider")).matches(),
                "interface provider.bootstrap_tool is invalid");
        JsonNode interval = provider.get("probe_interval_ms");
        require(interval != null && interval.isIntegralNumber()
                        && interval.asInt() >= 5_000 && interval.asInt() <= 300_000,
                "interface provider.probe_interval_ms must be between 5000 and 300000");
    }

    private static void assertToolAction(JsonNode value, String label) {
        JsonNode action = requireObject(value, label);
        requireExactFields(action, Set.of("kind", "tool"), label);
        require("tool".equals(requiredText(action, "kind", label)),
                label + ".kind must be tool");
        require(TOOL.matcher(requiredText(action, "tool", label)).matches(),
                label + ".tool is invalid");
    }

    private static void assertLocalizedString(JsonNode value, String label) {
        JsonNode localized = requireObject(value, label);
        require(localized.has("en") && localized.path("en").isTextual()
                        && !localized.path("en").asText().isBlank(),
                label + ".en is required");
        localized.fields().forEachRemaining(entry -> {
            require(entry.getKey().matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*"),
                    label + " contains an invalid locale");
            require(entry.getValue().isTextual() && !entry.getValue().asText().isBlank()
                            && entry.getValue().asText().length() <= 120,
                    label + " values must be non-empty strings up to 120 characters");
        });
    }

    private static void assertOrder(JsonNode value, String label) {
        require(value != null && value.isIntegralNumber()
                        && value.asInt() >= -1000 && value.asInt() <= 1000,
                label + " must be between -1000 and 1000");
    }

    private static void requireId(String value, String label) {
        require(ID.matcher(value).matches(), label + " is invalid");
    }

    private static JsonNode requireObject(JsonNode value, String label) {
        require(value != null && value.isObject(), label + " must be an object");
        return value;
    }

    private static JsonNode requireArray(JsonNode value, String label) {
        require(value != null && value.isArray(), label + " must be an array");
        return value;
    }

    private static String requiredText(JsonNode object, String field, String label) {
        JsonNode value = object.get(field);
        require(value != null && value.isTextual() && !value.asText().isBlank(),
                label + "." + field + " must be a non-empty string");
        return value.asText();
    }

    private static void requireExactFields(JsonNode value, Set<String> expected, String label) {
        Set<String> actual = new java.util.HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        require(expected.equals(actual), label + " fields must be exactly " + expected);
    }

    private static void assertUniqueTextField(JsonNode values, String field, String label) {
        Set<String> seen = new java.util.HashSet<>();
        values.forEach(value -> require(seen.add(value.path(field).asText()),
                "duplicate " + label + ": " + value.path(field).asText()));
    }

    private static JsonNode toNode(Object value) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
