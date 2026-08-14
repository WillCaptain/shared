package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Executable protocol validator for Host work-progress widgets
 * ({@code sys.todo}, {@code sys.work}).
 */
public final class AippWorkProgressSpec {

    private static final Set<String> TODO_LIST_STATUSES = Set.of("running", "completed");
    private static final Set<String> TODO_OWNER_KINDS = Set.of("session", "dag_node");
    private static final Set<String> TODO_ITEM_STATUSES = Set.of(
            "pending", "in_progress", "done", "blocked", "cancelled");
    private static final Set<String> WORK_STATUSES = Set.of(
            "queued", "running", "parked_waiting_client", "needs_review",
            "completed", "partial", "failed", "cancelled", "timed_out");
    private static final Set<String> WORK_RUNNERS = Set.of("step_director", "agent_child");
    private static final Set<String> WORK_ACTIONS = Set.of(
            "open_work_panel", "cancel", "rerun", "skip", "abort");

    public void assertValidSysTodoCanvas(JsonNode canvas) {
        requireObject(canvas, "sys.todo canvas");
        requireEquals(canvas, "widget_type", AippSystemWidget.TODO);
        requireIn(canvas, "action", Set.of("open", "replace"));
        JsonNode data = canvas.get("data");
        requireObject(data, "sys.todo data");
        requireText(data, "todo_list_id");
        requireObject(data.get("owner"), "sys.todo owner");
        String ownerKind = requireText(data.get("owner"), "kind");
        require(TODO_OWNER_KINDS.contains(ownerKind),
                "invalid sys.todo owner kind: " + ownerKind);
        requireText(data.get("owner"), "id");
        String listStatus = requireText(data, "status");
        require(TODO_LIST_STATUSES.contains(listStatus), "invalid sys.todo list status: " + listStatus);
        require(data.path("revision").isIntegralNumber() && data.path("revision").asLong() >= 1,
                "sys.todo revision must be >= 1");
        require(data.path("items").isArray(), "sys.todo items must be an array");
        for (JsonNode item : data.get("items")) {
            requireObject(item, "sys.todo item");
            requireText(item, "id");
            String itemStatus = requireText(item, "status");
            require(TODO_ITEM_STATUSES.contains(itemStatus),
                    "invalid sys.todo item status: " + itemStatus);
        }
    }

    public void assertValidSysWorkCanvas(JsonNode canvas) {
        requireObject(canvas, "sys.work canvas");
        requireEquals(canvas, "widget_type", AippSystemWidget.WORK);
        requireIn(canvas, "action", Set.of("open", "replace"));
        JsonNode data = canvas.get("data");
        requireObject(data, "sys.work data");
        requireText(data, "work_id");
        String status = requireText(data, "status");
        require(WORK_STATUSES.contains(status), "invalid sys.work status: " + status);
        String runner = requireText(data, "runner_kind");
        require(WORK_RUNNERS.contains(runner), "invalid sys.work runner_kind: " + runner);
        require(data.path("revision").isIntegralNumber() && data.path("revision").asLong() >= 1,
                "sys.work revision must be >= 1");
        if (data.has("actions")) {
            require(data.get("actions").isArray(), "sys.work actions must be an array");
            for (JsonNode action : data.get("actions")) {
                require(WORK_ACTIONS.contains(action.asText()),
                        "invalid sys.work action: " + action.asText());
            }
        }
        if (data.has("items")) {
            require(data.get("items").isArray(), "sys.work items must be an array");
            for (JsonNode item : data.get("items")) {
                requireObject(item, "sys.work item");
                requireText(item, "id");
                String itemStatus = requireText(item, "status");
                require(TODO_ITEM_STATUSES.contains(itemStatus),
                        "invalid sys.work item status: " + itemStatus);
            }
        }
        if (data.has("result_summary")) {
            String summary = requireText(data, "result_summary");
            require(summary.length() <= 240,
                    "sys.work result_summary must be <= 240 characters");
        }
    }

    private static void requireObject(JsonNode node, String label) {
        require(node != null && node.isObject(), label + " must be an object");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    private static void requireEquals(JsonNode node, String field, String expected) {
        requireText(node, field);
        require(expected.equals(node.get(field).asText()),
                field + " must be " + expected + " but was " + node.get(field).asText());
    }

    private static void requireIn(JsonNode node, String field, Set<String> allowed) {
        String value = requireText(node, field);
        require(allowed.contains(value), field + " must be one of " + allowed + " but was " + value);
    }

    private static String requireText(JsonNode node, String field) {
        require(node != null && node.has(field) && node.get(field).isTextual()
                        && !node.get(field).asText().isBlank(),
                field + " must be a non-blank string");
        return node.get(field).asText();
    }
}
