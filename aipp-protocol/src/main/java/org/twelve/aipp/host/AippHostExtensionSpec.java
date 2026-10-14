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
    public static final String REGISTER_ATTACHMENT_SOURCE = "register_attachment_source";
    /** Preferred operation name for typed resource managers (Together + / lifecycle). */
    public static final String REGISTER_RESOURCE = "register_resource";
    /** Legacy alias for {@link #REGISTER_RESOURCE}. */
    public static final String REGISTER_RESOURCE_PROVIDER = "register_resource_provider";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Pattern TOOL = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Pattern WIDGET = Pattern.compile("[a-z][a-z0-9_-]{0,127}");
    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9]{1,16}");
    private static final Pattern INTERFACE = Pattern.compile("shared\\.[a-z0-9.-]+/v[1-9]\\d*");
    private static final Pattern MODULE = Pattern.compile("/(?:[a-zA-Z0-9._-]+/)*[a-zA-Z0-9._-]+\\.js");
    /** Normative resource ops. Legacy {@code thumbnail} may appear but is not required. */
    private static final Set<String> RESOURCE_OPS = Set.of(
            "search", "preview", "edit", "run");
    private static final Set<String> RESOURCE_OPS_WITH_LEGACY_THUMBNAIL = Set.of(
            "search", "thumbnail", "preview", "edit", "run");
    private static final Set<String> RESOURCE_UI_OPS = Set.of("search", "preview", "edit");
    private static final Set<String> COMPOSER_ACTIONS = Set.of("search", "attach", "send_graph");
    private static final Pattern RESOURCE_ICON = Pattern.compile(
            "^(?:app|file|library|graph|<svg\\b[\\s\\S]*</svg>)$");

    public Map<String, Object> registerBannerIcon(
            String id, Map<String, String> label, String tool, int order) {
        return registerBannerIcon(id, label, "app", tool, order);
    }

    public Map<String, Object> registerBannerIcon(
            String id, Map<String, String> label, String icon, String tool, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_ICON,
                "id", id,
                "label", label,
                "icon", icon,
                "action", Map.of("kind", "tool", "tool", tool),
                "order", order);
        assertValidBannerIcon(toNode(value));
        return value;
    }

    public Map<String, Object> registerMainWidgetBannerIcon(
            String id, Map<String, String> label, String icon, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_ICON,
                "id", id,
                "label", label,
                "icon", icon,
                "action", Map.of("kind", "app_main"),
                "order", order);
        assertValidBannerIcon(toNode(value));
        return value;
    }

    public Map<String,Object> registerCountedMainWidgetBannerIcon(
            String id,Map<String,String> label,String icon,String badgeModule,int order){
        Map<String,Object> value=Map.of("operation",REGISTER_BANNER_ICON,"id",id,"label",label,"icon",icon,
                "action",Map.of("kind","app_main"),"badge",Map.of("kind","count","module",badgeModule),"order",order);
        assertValidBannerIcon(toNode(value));return value;
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

    /** Registers a right-banner tab that mounts an app-owned panel module in the Host shell. */
    public Map<String, Object> registerBannerPanelTab(
            String id, Map<String, String> label, String module, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_TAB,
                "id", id,
                "label", label,
                "action", Map.of("kind", "panel", "module", module),
                "order", order);
        assertValidBannerTab(toNode(value));
        return value;
    }

    /** Registers a panel tab whose app-owned provider publishes a numeric badge count. */
    public Map<String, Object> registerCountedBannerPanelTab(
            String id, Map<String, String> label, String module, String badgeModule, int order) {
        return registerCountedBannerPanelTab(id, label, module, badgeModule, false, order);
    }

    public Map<String, Object> registerCountedBannerPanelTab(
            String id, Map<String, String> label, String module, String badgeModule,
            boolean backgroundAgentTurn, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_BANNER_TAB,
                "id", id,
                "label", label,
                "action", Map.of("kind", "panel", "module", module),
                "badge", backgroundAgentTurn
                        ? Map.of("kind", "count", "module", badgeModule,
                                "background_agent_turn", true)
                        : Map.of("kind", "count", "module", badgeModule),
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

    /** Registers an app-owned picker in the Host composer attachment-source menu. */
    public Map<String, Object> registerAttachmentSource(
            String id, Map<String, String> label, String icon,
            String module, boolean multiple, int order) {
        Map<String, Object> value = Map.of(
                "operation", REGISTER_ATTACHMENT_SOURCE,
                "id", id,
                "label", label,
                "icon", icon,
                "module", module,
                "multiple", multiple,
                "order", order);
        assertValidAttachmentSource(toNode(value));
        return value;
    }

    /**
     * Registers typed resource behavior for one or more file extensions.
     * Storage stays note-one; this contribution owns search/preview/edit/run.
     * Prefer {@link #registerResource} when the manager should appear on Together {@code +}.
     */
    public Map<String, Object> registerResourceProvider(
            List<String> extensions,
            Map<String, String> label,
            int order,
            Map<String, Boolean> capabilities,
            Map<String, String> tools,
            Map<String, String> widgets) {
        return registerResource(null, extensions, label, null, null, order, null,
                capabilities, tools, widgets);
    }

    /**
     * Registers a resource manager for Together {@code +} and/or typed extension lifecycle.
     * {@code id}, {@code hint}, and {@code icon} are required together when the manager
     * should appear in the composer panel.
     */
    public Map<String, Object> registerResource(
            String id,
            List<String> extensions,
            Map<String, String> label,
            Map<String, String> hint,
            String icon,
            int order,
            Map<String, String> composer,
            Map<String, Boolean> capabilities,
            Map<String, String> tools,
            Map<String, String> widgets) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("operation", REGISTER_RESOURCE);
        if (id != null && !id.isBlank()) {
            value.put("id", id);
        }
        value.put("extensions", List.copyOf(extensions));
        value.put("label", Map.copyOf(label));
        if (hint != null && !hint.isEmpty()) {
            value.put("hint", Map.copyOf(hint));
        }
        if (icon != null && !icon.isBlank()) {
            value.put("icon", icon);
        }
        value.put("order", order);
        if (composer != null && !composer.isEmpty()) {
            value.put("composer", Map.copyOf(composer));
        }
        value.put("capabilities", Map.copyOf(capabilities));
        value.put("tools", Map.copyOf(tools));
        if (widgets != null && !widgets.isEmpty()) {
            value.put("widgets", Map.copyOf(widgets));
        }
        assertValidResourceProvider(toNode(value));
        return Map.copyOf(value);
    }

    public Map<String, Object> provideInterface(
            String type, String module, String bootstrapTool, int probeIntervalMs,
            String fallbackPolicy) {
        Map<String, Object> value = Map.of(
                "type", type,
                "module", module,
                "bootstrap_tool", bootstrapTool,
                "probe_interval_ms", probeIntervalMs,
                "fallback_policy", fallbackPolicy);
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

    public Map<String, Object> extensions(
            List<Map<String, Object>> bannerIcons,
            List<Map<String, Object>> bannerTabs,
            List<Map<String, Object>> interfaceProviders,
            List<Map<String, Object>> attachmentSources) {
        return extensions(bannerIcons, bannerTabs, interfaceProviders, attachmentSources, List.of());
    }

    /**
     * Full Host extensions block including optional composer attachment sources and
     * once-helper user-facing {@code help_contributions} (AIPP-owned intros / strengths).
     */
    public Map<String, Object> extensions(
            List<Map<String, Object>> bannerIcons,
            List<Map<String, Object>> bannerTabs,
            List<Map<String, Object>> interfaceProviders,
            List<Map<String, Object>> attachmentSources,
            List<Map<String, Object>> helpContributions) {
        return extensions(bannerIcons, bannerTabs, interfaceProviders,
                attachmentSources, helpContributions, List.of());
    }

    public Map<String, Object> extensions(
            List<Map<String, Object>> bannerIcons,
            List<Map<String, Object>> bannerTabs,
            List<Map<String, Object>> interfaceProviders,
            List<Map<String, Object>> attachmentSources,
            List<Map<String, Object>> helpContributions,
            List<Map<String, Object>> resourceProviders) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("schema_version", SCHEMA_VERSION);
        value.put("banner_icons", List.copyOf(bannerIcons));
        value.put("banner_tabs", List.copyOf(bannerTabs));
        value.put("interface_providers", List.copyOf(interfaceProviders));
        if (attachmentSources != null && !attachmentSources.isEmpty()) {
            value.put("attachment_sources", List.copyOf(attachmentSources));
        }
        if (helpContributions != null && !helpContributions.isEmpty()) {
            value.put(HostContributionInterfaceSpec.HELP_CONTRIBUTIONS_FIELD,
                    List.copyOf(helpContributions));
        }
        if (resourceProviders != null && !resourceProviders.isEmpty()) {
            value.put("resource_providers", List.copyOf(resourceProviders));
        }
        assertValidHostExtensions(toNode(Map.of("host_extensions", value)));
        return Map.copyOf(value);
    }

    /**
     * User-facing AIPP introduction for Host once-helper. Distinct from
     * {@code router_promoted_summary} / {@code prompt_contributions} (agent-loop routing).
     * The owning AIPP ships this copy; the Host must not hardcode per-app intros.
     */
    public Map<String, Object> helpContribution(
            String topic,
            List<String> match,
            Map<String, String> title,
            Map<String, String> summary,
            List<Map<String, String>> steps,
            List<Map<String, Object>> actions) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put(HostContributionInterfaceSpec.HELP_TOPIC_FIELD, topic);
        value.put(HostContributionInterfaceSpec.HELP_MATCH_FIELD, List.copyOf(match));
        value.put(HostContributionInterfaceSpec.HELP_TITLE_FIELD, Map.copyOf(title));
        value.put(HostContributionInterfaceSpec.HELP_SUMMARY_FIELD, Map.copyOf(summary));
        if (steps != null && !steps.isEmpty()) {
            value.put(HostContributionInterfaceSpec.HELP_STEPS_FIELD, List.copyOf(steps));
        }
        if (actions != null && !actions.isEmpty()) {
            value.put(HostContributionInterfaceSpec.HELP_ACTIONS_FIELD, List.copyOf(actions));
        }
        assertValidHelpContribution(toNode(value));
        return Map.copyOf(value);
    }

    /** Convenience open-main action for a help contribution card. */
    public Map<String, Object> helpOpenMainAction(Map<String, String> label) {
        return Map.of("kind", "app_main", "label", Map.copyOf(label));
    }

    public void assertValidHostExtensions(JsonNode appManifest) {
        if (appManifest == null || !appManifest.has("host_extensions")) return;
        JsonNode root = requireObject(appManifest.get("host_extensions"), "host_extensions");
        Set<String> rootFields = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(rootFields::add);
        Set<String> requiredFields = Set.of(
                "schema_version", "banner_icons", "banner_tabs", "interface_providers");
        Set<String> allowedFields = Set.of(
                "schema_version", "banner_icons", "banner_tabs", "interface_providers",
                "attachment_sources", HostContributionInterfaceSpec.HELP_CONTRIBUTIONS_FIELD,
                "resource_providers");
        require(rootFields.containsAll(requiredFields) && allowedFields.containsAll(rootFields),
                "host_extensions fields must be schema_version, banner_icons, banner_tabs, "
                        + "interface_providers, and optional attachment_sources/"
                        + "help_contributions/resource_providers");
        require(root.path("schema_version").isIntegralNumber()
                        && root.path("schema_version").asInt() == SCHEMA_VERSION,
                "host_extensions.schema_version must be 1");
        JsonNode icons = requireArray(root.get("banner_icons"), "host_extensions.banner_icons");
        JsonNode tabs = requireArray(root.get("banner_tabs"), "host_extensions.banner_tabs");
        JsonNode providers = requireArray(
                root.get("interface_providers"), "host_extensions.interface_providers");
        JsonNode sources = root.has("attachment_sources")
                ? requireArray(root.get("attachment_sources"), "host_extensions.attachment_sources")
                : toNode(List.of());
        JsonNode help = root.has(HostContributionInterfaceSpec.HELP_CONTRIBUTIONS_FIELD)
                ? requireArray(root.get(HostContributionInterfaceSpec.HELP_CONTRIBUTIONS_FIELD),
                "host_extensions.help_contributions")
                : toNode(List.of());
        JsonNode resources = root.has("resource_providers")
                ? requireArray(root.get("resource_providers"), "host_extensions.resource_providers")
                : toNode(List.of());
        require(icons.size() <= 8, "an app may register at most 8 banner icons");
        require(tabs.size() <= 8, "an app may register at most 8 banner tabs");
        require(providers.size() <= 8, "an app may provide at most 8 Host interfaces");
        require(sources.size() <= 8, "an app may register at most 8 attachment sources");
        require(help.size() <= 8, "an app may declare at most 8 help contributions");
        require(resources.size() <= 8, "an app may register at most 8 resource providers");
        icons.forEach(this::assertValidBannerIcon);
        tabs.forEach(this::assertValidBannerTab);
        providers.forEach(this::assertValidInterfaceProvider);
        sources.forEach(this::assertValidAttachmentSource);
        help.forEach(this::assertValidHelpContribution);
        resources.forEach(this::assertValidResourceProvider);
        assertUniqueTextField(icons, "id", "banner icon id");
        assertUniqueTextField(tabs, "id", "banner tab id");
        assertUniqueTextField(providers, "type", "interface provider type");
        assertUniqueTextField(sources, "id", "attachment source id");
        assertUniqueTextField(help, HostContributionInterfaceSpec.HELP_TOPIC_FIELD, "help topic");
        assertUniqueResourceExtensions(resources);
    }

    public void assertValidResourceProvider(JsonNode value) {
        JsonNode provider = requireObject(value, "resource provider");
        Set<String> fields = new java.util.HashSet<>();
        provider.fieldNames().forEachRemaining(fields::add);
        Set<String> required = Set.of(
                "operation", "extensions", "label", "order", "capabilities", "tools");
        Set<String> allowed = Set.of(
                "operation", "id", "extensions", "label", "hint", "icon", "order",
                "composer", "capabilities", "tools", "widgets");
        require(fields.containsAll(required) && allowed.containsAll(fields),
                "resource provider fields must be operation, extensions, label, order, "
                        + "capabilities, tools, and optional id/hint/icon/composer/widgets");
        String operation = requiredText(provider, "operation", "resource provider");
        require(REGISTER_RESOURCE.equals(operation)
                        || REGISTER_RESOURCE_PROVIDER.equals(operation),
                "resource provider.operation must be register_resource "
                        + "or register_resource_provider");
        boolean hasId = provider.has("id");
        if (hasId) {
            requireId(requiredText(provider, "id", "resource provider"), "resource provider.id");
            require(provider.has("hint"),
                    "resource provider.hint is required when id is present");
            require(provider.has("icon"),
                    "resource provider.icon is required when id is present");
            assertLocalizedString(provider.get("hint"), "resource provider.hint");
            String icon = requiredText(provider, "icon", "resource provider");
            require(icon.length() <= 4096 && RESOURCE_ICON.matcher(icon).matches(),
                    "resource provider.icon must be app|file|library|graph or an inline svg");
        } else {
            require(!provider.has("hint") && !provider.has("icon"),
                    "resource provider hint/icon require id");
        }
        if (provider.has("composer")) {
            JsonNode composer = requireObject(provider.get("composer"),
                    "resource provider.composer");
            Set<String> composerFields = new java.util.HashSet<>();
            composer.fieldNames().forEachRemaining(composerFields::add);
            require(composerFields.equals(Set.of("action")),
                    "resource provider.composer fields must be action only");
            String action = requiredText(composer, "action", "resource provider.composer");
            require(COMPOSER_ACTIONS.contains(action),
                    "resource provider.composer.action must be search, attach, or send_graph");
        }
        JsonNode extensions = requireArray(provider.get("extensions"),
                "resource provider.extensions");
        require(!extensions.isEmpty() && extensions.size() <= 8,
                "resource provider.extensions must have 1..8 entries");
        Set<String> seenExt = new java.util.HashSet<>();
        extensions.forEach(node -> {
            require(node != null && node.isTextual(),
                    "resource provider.extensions entries must be strings");
            String ext = node.asText().trim().toLowerCase(java.util.Locale.ROOT);
            require(EXTENSION.matcher(ext).matches(),
                    "resource provider.extensions entries must look like .anna");
            require(seenExt.add(ext), "duplicate resource provider extension: " + ext);
        });
        assertLocalizedString(provider.get("label"), "resource provider.label");
        assertOrder(provider.get("order"), "resource provider.order");
        JsonNode caps = requireObject(provider.get("capabilities"),
                "resource provider.capabilities");
        Set<String> capFields = new java.util.HashSet<>();
        caps.fieldNames().forEachRemaining(capFields::add);
        require(capFields.containsAll(RESOURCE_OPS)
                        && RESOURCE_OPS_WITH_LEGACY_THUMBNAIL.containsAll(capFields),
                "resource provider.capabilities must declare search, preview, edit, run "
                        + "(optional legacy thumbnail)");
        boolean any = false;
        for (String op : RESOURCE_OPS) {
            require(caps.path(op).isBoolean(),
                    "resource provider.capabilities." + op + " must be boolean");
            any = any || caps.path(op).asBoolean();
        }
        if (caps.has("thumbnail")) {
            require(caps.path("thumbnail").isBoolean(),
                    "resource provider.capabilities.thumbnail must be boolean");
            any = any || caps.path("thumbnail").asBoolean();
        }
        require(any, "resource provider.capabilities must enable at least one op");
        JsonNode tools = requireObject(provider.get("tools"), "resource provider.tools");
        JsonNode widgets = provider.has("widgets")
                ? requireObject(provider.get("widgets"), "resource provider.widgets")
                : toNode(Map.of());
        for (String op : RESOURCE_OPS_WITH_LEGACY_THUMBNAIL) {
            if (!caps.path(op).asBoolean()) continue;
            require(tools.has(op) && tools.path(op).isTextual()
                            && TOOL.matcher(tools.path(op).asText()).matches(),
                    "resource provider.tools." + op + " is required when capability is true");
            if (RESOURCE_UI_OPS.contains(op)) {
                require(widgets.has(op) && widgets.path(op).isTextual()
                                && WIDGET.matcher(widgets.path(op).asText()).matches(),
                        "resource provider.widgets." + op
                                + " is required when capability is true");
            }
        }
        tools.fieldNames().forEachRemaining(name ->
                require(RESOURCE_OPS_WITH_LEGACY_THUMBNAIL.contains(name),
                        "resource provider.tools has unknown op: " + name));
        widgets.fieldNames().forEachRemaining(name ->
                require(RESOURCE_OPS_WITH_LEGACY_THUMBNAIL.contains(name),
                        "resource provider.widgets has unknown op: " + name));
    }

    private static void assertUniqueResourceExtensions(JsonNode providers) {
        Set<String> seenExt = new java.util.HashSet<>();
        Set<String> seenIds = new java.util.HashSet<>();
        providers.forEach(provider -> {
            String id = provider.path("id").asText("").trim();
            if (!id.isEmpty()) {
                require(seenIds.add(id), "duplicate resource provider id: " + id);
            }
            JsonNode extensions = provider.path("extensions");
            if (!extensions.isArray()) return;
            extensions.forEach(node -> {
                String ext = node.asText("").trim().toLowerCase(java.util.Locale.ROOT);
                require(seenExt.add(ext), "duplicate resource provider extension: " + ext);
            });
        });
    }

    public void assertValidHelpContribution(JsonNode value) {
        JsonNode contribution = requireObject(value, "help contribution");
        Set<String> fields = new java.util.HashSet<>();
        contribution.fieldNames().forEachRemaining(fields::add);
        Set<String> required = Set.of(
                HostContributionInterfaceSpec.HELP_TOPIC_FIELD,
                HostContributionInterfaceSpec.HELP_MATCH_FIELD,
                HostContributionInterfaceSpec.HELP_TITLE_FIELD,
                HostContributionInterfaceSpec.HELP_SUMMARY_FIELD);
        Set<String> allowed = Set.of(
                HostContributionInterfaceSpec.HELP_TOPIC_FIELD,
                HostContributionInterfaceSpec.HELP_MATCH_FIELD,
                HostContributionInterfaceSpec.HELP_TITLE_FIELD,
                HostContributionInterfaceSpec.HELP_SUMMARY_FIELD,
                HostContributionInterfaceSpec.HELP_STEPS_FIELD,
                HostContributionInterfaceSpec.HELP_ACTIONS_FIELD,
                "app_id");
        require(fields.containsAll(required) && allowed.containsAll(fields),
                "help contribution fields must be topic, match, title, summary, "
                        + "and optional steps/actions/app_id");
        requireId(requiredText(contribution, HostContributionInterfaceSpec.HELP_TOPIC_FIELD,
                "help contribution"), "help contribution.topic");
        JsonNode match = requireArray(
                contribution.get(HostContributionInterfaceSpec.HELP_MATCH_FIELD),
                "help contribution.match");
        require(!match.isEmpty() && match.size() <= 32,
                "help contribution.match must have 1..32 non-empty strings");
        match.forEach(word -> require(word != null && word.isTextual() && !word.asText().isBlank()
                        && word.asText().length() <= 80,
                "help contribution.match entries must be non-empty strings up to 80 characters"));
        assertHelpLocalizedString(
                contribution.get(HostContributionInterfaceSpec.HELP_TITLE_FIELD),
                "help contribution.title", 120);
        assertHelpLocalizedString(
                contribution.get(HostContributionInterfaceSpec.HELP_SUMMARY_FIELD),
                "help contribution.summary", 400);
        if (contribution.has(HostContributionInterfaceSpec.HELP_STEPS_FIELD)) {
            JsonNode steps = requireArray(
                    contribution.get(HostContributionInterfaceSpec.HELP_STEPS_FIELD),
                    "help contribution.steps");
            require(steps.size() <= 12, "help contribution.steps may have at most 12 items");
            steps.forEach(step -> assertHelpLocalizedString(step, "help contribution.steps[]", 200));
        }
        if (contribution.has(HostContributionInterfaceSpec.HELP_ACTIONS_FIELD)) {
            JsonNode actions = requireArray(
                    contribution.get(HostContributionInterfaceSpec.HELP_ACTIONS_FIELD),
                    "help contribution.actions");
            require(actions.size() <= 8, "help contribution.actions may have at most 8 items");
            actions.forEach(this::assertValidHelpAction);
        }
        if (contribution.has("app_id")) {
            requireId(requiredText(contribution, "app_id", "help contribution"),
                    "help contribution.app_id");
        }
    }

    private void assertValidHelpAction(JsonNode value) {
        JsonNode action = requireObject(value, "help contribution.action");
        Set<String> fields = new java.util.HashSet<>();
        action.fieldNames().forEachRemaining(fields::add);
        require(fields.contains("kind"), "help contribution.action.kind is required");
        String kind = requiredText(action, "kind", "help contribution.action");
        require(Set.of("app_main", "tool", "panel", "finder", "apps_panel").contains(kind),
                "help contribution.action.kind must be app_main, tool, panel, finder, or apps_panel");
        if (action.has("label")) {
            assertHelpLocalizedString(action.get("label"), "help contribution.action.label", 80);
        }
        if ("tool".equals(kind)) {
            require(action.has("tool"), "help contribution.action.tool is required for kind=tool");
        }
        if (action.has("forward_question_as")) {
            require("tool".equals(kind), "help contribution.action.forward_question_as requires kind=tool");
            require(TOOL.matcher(requiredText(action, "forward_question_as",
                    "help contribution.action")).matches(),
                    "help contribution.action.forward_question_as is invalid");
        }
        if ("panel".equals(kind)) {
            require(ID.matcher(requiredText(action, "extension_id",
                    "help contribution.action")).matches(),
                    "help contribution.action.extension_id is invalid");
        }
        Set<String> allowed = Set.of("kind", "label", "tool", "arguments", "app_id", "id",
                "extension_id", "forward_question_as");
        require(allowed.containsAll(fields),
                "help contribution.action fields must be kind and optional label/tool/arguments/"
                        + "app_id/id/extension_id/forward_question_as");
    }

    private static void assertHelpLocalizedString(JsonNode value, String label, int maxLen) {
        JsonNode localized = requireObject(value, label);
        require(localized.has("en") && localized.path("en").isTextual()
                        && !localized.path("en").asText().isBlank(),
                label + ".en is required");
        localized.fields().forEachRemaining(entry -> {
            require(entry.getKey().matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*"),
                    label + " contains an invalid locale");
            require(entry.getValue().isTextual() && !entry.getValue().asText().isBlank()
                            && entry.getValue().asText().length() <= maxLen,
                    label + " values must be non-empty strings up to " + maxLen + " characters");
        });
    }

    public void assertValidAttachmentSource(JsonNode value) {
        JsonNode source = requireObject(value, "attachment source");
        requireExactFields(source,
                Set.of("operation", "id", "label", "icon", "module", "multiple", "order"),
                "attachment source");
        require(REGISTER_ATTACHMENT_SOURCE.equals(
                        requiredText(source, "operation", "attachment source")),
                "attachment source.operation must be register_attachment_source");
        requireId(requiredText(source, "id", "attachment source"), "attachment source.id");
        assertLocalizedString(source.get("label"), "attachment source.label");
        require(Set.of("app", "file", "library").contains(
                        requiredText(source, "icon", "attachment source")),
                "attachment source.icon must be app, file, or library");
        requireSafeModule(requiredText(source, "module", "attachment source"),
                "attachment source.module");
        require(source.path("multiple").isBoolean(),
                "attachment source.multiple must be boolean");
        assertOrder(source.get("order"), "attachment source.order");
    }

    public void assertValidBannerIcon(JsonNode value) {
        JsonNode icon = requireObject(value, "banner icon");
        Set<String> fields=new java.util.HashSet<>();icon.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(Set.of("operation","id","label","icon","action","order"))
                        || fields.equals(Set.of("operation","id","label","icon","action","badge","order")),
                "banner icon fields must be operation, id, label, icon, action, order, and optional badge");
        require(REGISTER_BANNER_ICON.equals(requiredText(icon, "operation", "banner icon")),
                "banner icon.operation must be register_banner_icon");
        requireId(requiredText(icon, "id", "banner icon"), "banner icon.id");
        assertLocalizedString(icon.get("label"), "banner icon.label");
        require(Set.of("app", "shell").contains(requiredText(icon, "icon", "banner icon")),
                "banner icon.icon must be 'app' or 'shell'");
        assertAction(icon.get("action"), "banner icon.action", false);
        if(icon.has("badge"))assertCountBadge(icon.get("badge"));
        assertOrder(icon.get("order"), "banner icon.order");
    }

    public void assertValidBannerTab(JsonNode value) {
        JsonNode tab = requireObject(value, "banner tab");
        Set<String> fields = new java.util.HashSet<>();
        tab.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(Set.of("operation", "id", "label", "action", "order"))
                        || fields.equals(Set.of(
                                "operation", "id", "label", "action", "badge", "order"))
                        || fields.equals(Set.of(
                                "operation", "id", "label", "action", "order", "app_icon"))
                        || fields.equals(Set.of(
                                "operation", "id", "label", "action", "badge", "order", "app_icon")),
                "banner tab fields must be operation, id, label, action, order, "
                        + "and optional badge/app_icon");
        require(REGISTER_BANNER_TAB.equals(requiredText(tab, "operation", "banner tab")),
                "banner tab.operation must be register_banner_tab");
        requireId(requiredText(tab, "id", "banner tab"), "banner tab.id");
        assertLocalizedString(tab.get("label"), "banner tab.label");
        assertAction(tab.get("action"), "banner tab.action", true);
        if (tab.has("badge")) assertCountBadge(tab.get("badge"));
        if (tab.has("app_icon")) {
            String icon = requiredText(tab, "app_icon", "banner tab");
            require(icon.startsWith("<svg") && icon.length() <= 4096,
                    "banner tab.app_icon must be an inline svg up to 4096 characters");
        }
        assertOrder(tab.get("order"), "banner tab.order");
    }

    private static void assertCountBadge(JsonNode value) {
        JsonNode badge = requireObject(value, "banner tab.badge");
        Set<String> fields = new java.util.HashSet<>();
        badge.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(Set.of("kind", "module"))
                        || fields.equals(Set.of("kind", "module", "background_agent_turn")),
                "banner tab.badge fields must be kind, module, and optional background_agent_turn");
        require("count".equals(requiredText(badge, "kind", "banner tab.badge")),
                "banner tab.badge.kind must be count");
        requireSafeModule(requiredText(badge, "module", "banner tab.badge"),
                "banner tab.badge.module");
        require(!badge.has("background_agent_turn") || badge.path("background_agent_turn").isBoolean(),
                "banner tab.badge.background_agent_turn must be boolean");
    }

    public void assertValidInterfaceProvider(JsonNode value) {
        JsonNode provider = requireObject(value, "interface provider");
        Set<String> fields = new java.util.HashSet<>();
        provider.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(Set.of("type", "module", "bootstrap_tool", "probe_interval_ms"))
                        || fields.equals(Set.of("type", "module", "bootstrap_tool",
                                "probe_interval_ms", "fallback_policy")),
                "interface provider fields must be exactly type, module, bootstrap_tool, "
                        + "probe_interval_ms, and optional fallback_policy");
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
        require(!provider.has("fallback_policy") || Set.of("provider", "none").contains(
                        requiredText(provider, "fallback_policy", "interface provider")),
                "interface provider.fallback_policy must be provider or none");
    }

    private static void assertAction(JsonNode value, String label, boolean panelAllowed) {
        JsonNode action = requireObject(value, label);
        String kind = requiredText(action, "kind", label);
        if ("app_main".equals(kind)) {
            requireExactFields(action, Set.of("kind"), label);
            return;
        }
        if ("panel".equals(kind)) {
            require(panelAllowed, label + ".kind panel is only valid for banner tabs");
            requireExactFields(action, Set.of("kind", "module"), label);
            requireSafeModule(requiredText(action, "module", label), label + ".module");
            return;
        }
        require("tool".equals(kind), label + ".kind must be tool or app_main");
        requireExactFields(action, Set.of("kind", "tool"), label);
        require(TOOL.matcher(requiredText(action, "tool", label)).matches(), label + ".tool is invalid");
    }

    private static void requireSafeModule(String module, String label) {
        require(MODULE.matcher(module).matches() && !module.contains(".."),
                label + " must be a safe app-local JavaScript path");
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
