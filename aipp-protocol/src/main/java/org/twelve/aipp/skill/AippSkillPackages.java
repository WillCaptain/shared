package org.twelve.aipp.skill;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Standard AIPP skill packages under classpath {@code skills/{name}/SKILL.md}.
 *
 * <p>Index fields ({@code name}, {@code description}, {@code allowed_tools}, scope) are read from
 * YAML frontmatter only — never duplicated in Java.
 */
public final class AippSkillPackages {

    public static final String SKILLS_ROOT = "skills";
    public static final String SKILL_MD = "SKILL.md";

    private AippSkillPackages() {}

    /** Discover skill ids that have {@code skills/{id}/SKILL.md} on the classpath. */
    public static List<String> discoverSkillIds(ClassLoader classLoader) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        Enumeration<URL> roots = classLoader.getResources(SKILLS_ROOT);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                try {
                    collectFromDirectory(Path.of(root.toURI()), ids);
                } catch (Exception e) {
                    throw new IOException("Failed to scan skills directory: " + root, e);
                }
            } else if ("jar".equals(root.getProtocol())) {
                collectFromJarRoot(root, ids);
            }
        }
        // Some packagers expose skills/*/SKILL.md without a skills/ directory URL.
        Enumeration<URL> playbooks = classLoader.getResources(SKILLS_ROOT + "/");
        while (playbooks.hasMoreElements()) {
            URL root = playbooks.nextElement();
            if ("jar".equals(root.getProtocol())) {
                collectFromJarRoot(root, ids);
            }
        }
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        return sorted;
    }

    public static String playbookResourcePath(String skillId) {
        return SKILLS_ROOT + "/" + skillId + "/" + SKILL_MD;
    }

    public static String readPlaybook(ClassLoader classLoader, String skillId) throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(playbookResourcePath(skillId))) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Build {@code GET /api/skills} response body from classpath packages. */
    public static Map<String, Object> buildSkillsResponse(
            ClassLoader classLoader, String appId, String version, String defaultOwnerApp)
            throws IOException {
        List<Map<String, Object>> skills = new ArrayList<>();
        for (String id : discoverSkillIds(classLoader)) {
            String md = readPlaybook(classLoader, id);
            if (md == null) continue;
            Map<String, Object> entry = buildIndexEntry(id, md, defaultOwnerApp);
            lintSkillIndexEntry(entry);
            skills.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", appId);
        result.put("version", version);
        result.put("skills", skills);
        return result;
    }

    /** {@code GET /api/skills/{id}/files} response shape. */
    public static Map<String, Object> buildFilesResponse(ClassLoader classLoader, String skillId)
            throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("skill", skillId);
        out.put("path", SKILLS_ROOT + "/" + skillId);
        out.put("files", listPackageFiles(classLoader, skillId));
        return out;
    }

    /** Build {@code GET /api/skills} index entry from {@code SKILL.md} frontmatter. */
    public static Map<String, Object> buildIndexEntry(
            String skillId, String playbookMarkdown, String defaultOwnerApp) {
        Map<String, Object> fm = parseFrontmatter(playbookMarkdown);
        Map<String, Object> entry = new LinkedHashMap<>();
        String name = str(fm.get("name"));
        if (name.isBlank()) name = skillId;
        entry.put("name", name);
        entry.put("description", str(fm.get("description")));
        entry.put("allowed_tools", stringList(fm.get("allowed-tools"), fm.get("allowed_tools")));
        entry.put("playbook_url", "/api/skills/" + skillId + "/playbook");

        String level = firstNonBlank(
                str(fm.get("aipp-level")), str(fm.get("level")), "app");
        entry.put("level", level);

        String ownerApp = firstNonBlank(str(fm.get("aipp-owner-app")), str(fm.get("owner_app")), defaultOwnerApp);
        if (!ownerApp.isBlank()) entry.put("owner_app", ownerApp);

        String ownerWidget = firstNonBlank(str(fm.get("aipp-owner-widget")), str(fm.get("owner_widget")));
        if (!ownerWidget.isBlank()) entry.put("owner_widget", ownerWidget);

        String ownerView = firstNonBlank(str(fm.get("aipp-owner-view")), str(fm.get("owner_view")));
        if (!ownerView.isBlank()) entry.put("owner_view", ownerView);

        String visibleWhen = firstNonBlank(str(fm.get("aipp-visible-when")), str(fm.get("visible_when")), "always");
        entry.put("visible_when", visibleWhen);

        String env = str(fm.get("aipp-env"));
        if (!env.isBlank()) entry.put("env", env);
        List<String> envs = stringList(fm.get("aipp-envs"), fm.get("envs"));
        if (!envs.isEmpty()) entry.put("envs", envs);

        if (Boolean.TRUE.equals(fm.get("aipp-catalog-manual")) || Boolean.TRUE.equals(fm.get("catalog_manual"))) {
            entry.put("catalog_manual", true);
        }
        return entry;
    }

    /** Read-only file tree for UI (paths relative to skill folder). */
    public static List<Map<String, Object>> listPackageFiles(ClassLoader classLoader, String skillId)
            throws IOException {
        String prefix = SKILLS_ROOT + "/" + skillId + "/";
        Map<String, MutableNode> nodes = new TreeMap<>();
        nodes.put("", new MutableNode("", "dir"));

        Enumeration<URL> roots = classLoader.getResources(prefix);
        boolean found = false;
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                found = true;
                try {
                    walkDirectory(Path.of(root.toURI()), Path.of(root.toURI()), nodes);
                } catch (Exception e) {
                    throw new IOException("Failed to list skill files: " + root, e);
                }
            } else if ("jar".equals(root.getProtocol())) {
                found = true;
                collectJarFiles(root, prefix, nodes);
            }
        }
        if (!found) {
            // Fallback: at least SKILL.md if readable
            String md = readPlaybook(classLoader, skillId);
            if (md != null) {
                nodes.put(SKILL_MD, new MutableNode(SKILL_MD, "file"));
            }
        }
        MutableNode root = nodes.get("");
        if (root == null || root.children.isEmpty()) return List.of();
        return root.children.values().stream().map(MutableNode::toMap).toList();
    }

    public static Map<String, Object> parseFrontmatter(String markdown) {
        if (markdown == null || !markdown.startsWith("---")) return Map.of();
        int end = markdown.indexOf("\n---", 3);
        if (end < 0) return Map.of();
        String yaml = markdown.substring(3, end).strip();
        return parseSimpleYaml(yaml);
    }

    static Map<String, Object> parseSimpleYaml(String yaml) {
        Map<String, Object> out = new LinkedHashMap<>();
        String currentListKey = null;
        List<String> currentList = null;
        for (String rawLine : yaml.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("- ") && currentListKey != null && currentList != null) {
                currentList.add(unquote(line.substring(2).strip()));
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (value.isEmpty()) {
                currentListKey = key;
                currentList = new ArrayList<>();
                out.put(key, currentList);
            } else {
                currentListKey = null;
                currentList = null;
                out.put(key, unquote(value));
            }
        }
        return out;
    }

    public static void lintSkillIndexEntry(Map<String, Object> entry) {
        Object nameObj = entry.get("name");
        if (!(nameObj instanceof String name) || name.isBlank()) {
            throw new IllegalStateException("Skill index entry missing 'name'");
        }
        Object descObj = entry.get("description");
        if (!(descObj instanceof String desc) || desc.isBlank()) {
            throw new IllegalStateException("Skill '" + name + "' missing 'description' in SKILL.md frontmatter");
        }
        if (desc.length() > 1024) {
            throw new IllegalStateException(
                    "Skill '" + name + "' description too long (" + desc.length() + " > 1024)");
        }
        if (desc.length() < 40) {
            System.err.println("[skill-lint] WARN skill '" + name
                    + "' description is short (" + desc.length() + " < 40).");
        }
        String lower = desc.toLowerCase(Locale.ROOT);
        boolean hasWhen = lower.contains("use when") || lower.contains("用于")
                || lower.contains("当用户") || lower.contains("when the user")
                || lower.contains("when ");
        if (!hasWhen) {
            System.err.println("[skill-lint] WARN skill '" + name
                    + "' description does not state WHEN to use it.");
        }
        Object tools = entry.get("allowed_tools");
        if (!(tools instanceof List<?> tl) || tl.isEmpty()) {
            throw new IllegalStateException(
                    "Skill '" + name + "' must declare non-empty allowed_tools in SKILL.md frontmatter");
        }
    }

    private static void collectFromDirectory(Path skillsRoot, Set<String> ids) throws IOException {
        if (!Files.isDirectory(skillsRoot)) return;
        try (var stream = Files.list(skillsRoot)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve(SKILL_MD)))
                    .forEach(p -> ids.add(p.getFileName().toString()));
        }
    }

    private static void collectFromJarRoot(URL root, Set<String> ids) throws IOException {
        JarURLConnection conn = (JarURLConnection) root.openConnection();
        String entryPrefix = conn.getEntryName();
        if (entryPrefix == null) entryPrefix = SKILLS_ROOT + "/";
        if (!entryPrefix.endsWith("/")) entryPrefix = entryPrefix + "/";
        try (JarFile jar = conn.getJarFile()) {
            for (JarEntry e : Collections.list(jar.entries())) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!name.startsWith(entryPrefix) || !name.endsWith("/" + SKILL_MD)) continue;
                String rel = name.substring(entryPrefix.length());
                int slash = rel.indexOf('/');
                if (slash <= 0) continue;
                ids.add(rel.substring(0, slash));
            }
        }
    }

    private static void collectJarFiles(URL root, String prefix, Map<String, MutableNode> nodes)
            throws IOException {
        JarURLConnection conn = (JarURLConnection) root.openConnection();
        String entryPrefix = conn.getEntryName();
        if (entryPrefix == null) entryPrefix = prefix;
        if (!entryPrefix.endsWith("/")) entryPrefix = entryPrefix + "/";
        try (JarFile jar = conn.getJarFile()) {
            for (JarEntry e : Collections.list(jar.entries())) {
                if (e.isDirectory()) continue;
                String full = e.getName();
                if (!full.startsWith(entryPrefix)) continue;
                String rel = full.substring(entryPrefix.length());
                if (rel.isBlank()) continue;
                addPath(nodes, rel, "file");
            }
        }
    }

    private static void walkDirectory(Path root, Path current, Map<String, MutableNode> nodes)
            throws IOException {
        if (!Files.isDirectory(current)) return;
        try (var stream = Files.list(current)) {
            for (Path p : stream.toList()) {
                Path rel = root.relativize(p);
                String relStr = rel.toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    addPath(nodes, relStr + "/", "dir");
                    walkDirectory(root, p, nodes);
                } else {
                    addPath(nodes, relStr, "file");
                }
            }
        }
    }

    private static void addPath(Map<String, MutableNode> nodes, String relPath, String kind) {
        String normalized = relPath.endsWith("/") ? relPath.substring(0, relPath.length() - 1) : relPath;
        if (normalized.isEmpty()) return;
        String[] parts = normalized.split("/");
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (!acc.isEmpty()) acc.append('/');
            acc.append(parts[i]);
            String path = acc.toString();
            final String partName = parts[i];
            final String nodeKind = (i == parts.length - 1) ? kind : "dir";
            nodes.computeIfAbsent(path, p -> new MutableNode(partName, nodeKind));
            if (i > 0) {
                String parentPath = acc.substring(0, acc.lastIndexOf("/"));
                MutableNode parent = nodes.computeIfAbsent(parentPath,
                        p -> new MutableNode(parentPath.substring(parentPath.lastIndexOf("/") + 1), "dir"));
                MutableNode child = nodes.get(path);
                parent.children.put(child.name, child);
            } else {
                MutableNode root = nodes.computeIfAbsent("", k -> new MutableNode("", "dir"));
                root.children.put(parts[0], nodes.get(path));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) out.add(o.toString());
                }
                return out;
            }
        }
        return List.of();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String unquote(String s) {
        if (s.length() >= 2) {
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    private static final class MutableNode {
        final String name;
        final String kind;
        final Map<String, MutableNode> children = new TreeMap<>();

        MutableNode(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("kind", kind);
            if (!children.isEmpty()) {
                m.put("children", children.values().stream().map(MutableNode::toMap).toList());
            }
            return m;
        }
    }
}
