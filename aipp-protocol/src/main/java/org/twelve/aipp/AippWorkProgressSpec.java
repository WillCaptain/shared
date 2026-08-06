package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Executable protocol validator for Host work-progress widgets
 * ({@code sys.todo}, {@code sys.delegation}, {@code sys.task}).
 */
public final class AippWorkProgressSpec {

    private static final Set<String> TODO_LIST_STATUSES = Set.of("running", "completed");
    private static final Set<String> TODO_ITEM_STATUSES = Set.of(
            "pending", "in_progress", "done", "blocked", "cancelled");
    private static final Set<String> DELEGATION_STATUSES = Set.of(
            "running", "partial", "completed", "failed", "cancelled", "timed_out");
    private static final Set<String> DELEGATION_CHILD_STATUSES = Set.of(
            "running", "completed", "blocked", "failed", "cancelled", "timed_out");
    private static final Set<String> DELEGATION_ACTIONS = Set.of("inspect", "cancel");
    private static final Set<String> TASK_STATUSES = Set.of(
            "pending", "running", "parked_waiting_client", "completed", "failed", "cancelled");
    private static final Set<String> TASK_ACTIONS = Set.of("open_task_panel", "cancel");

    public void assertValidSysTodoCanvas(JsonNode canvas) {
        requireObject(canvas, "sys.todo canvas");
        requireEquals(canvas, "widget_type", AippSystemWidget.TODO);
        requireIn(canvas, "action", Set.of("open", "replace"));
        JsonNode data = canvas.get("data");
        requireObject(data, "sys.todo data");
        requireText(data, "todo_list_id");
        requireObject(data.get("owner"), "sys.todo owner");
        requireText(data.get("owner"), "kind");
        requireText(data.get("owner"), "id");
        String listStatus = requireText(data, "status");
        require(TODO_LIST_STATUSES.contains(listStatus), "invalid sys.todo list status: " + listStatus);
        require(data.path("revision").canConvertToInt() && data.path("revision").asInt() >= 1,
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

    public void assertValidSysDelegationCanvas(JsonNode canvas) {
        requireObject(canvas, "sys.delegation canvas");
        requireEquals(canvas, "widget_type", AippSystemWidget.DELEGATION);
        requireIn(canvas, "action", Set.of("open", "replace"));
        JsonNode data = canvas.get("data");
        requireObject(data, "sys.delegation data");
        requireText(data, "delegation_id");
        String status = requireText(data, "status");
        require(DELEGATION_STATUSES.contains(status), "invalid sys.delegation status: " + status);
        require(data.path("revision").canConvertToInt() && data.path("revision").asInt() >= 1,
                "sys.delegation revision must be >= 1");
        require(data.path("children").isArray(), "sys.delegation children must be an array");
        for (JsonNode child : data.get("children")) {
            requireObject(child, "sys.delegation child");
            requireText(child, "child_id");
            requireText(child, "task_id");
            String childStatus = requireText(child, "status");
            require(DELEGATION_CHILD_STATUSES.contains(childStatus),
                    "invalid sys.delegation child status: " + childStatus);
        }
        if (data.has("actions")) {
            require(data.get("actions").isArray(), "sys.delegation actions must be an array");
            for (JsonNode action : data.get("actions")) {
                require(DELEGATION_ACTIONS.contains(action.asText()),
                        "invalid sys.delegation action: " + action.asText());
            }
        }
    }

    public void assertValidSysTaskCanvas(JsonNode canvas) {
        requireObject(canvas, "sys.task canvas");
        requireEquals(canvas, "widget_type", AippSystemWidget.TASK);
        requireIn(canvas, "action", Set.of("open", "replace"));
        JsonNode data = canvas.get("data");
        requireObject(data, "sys.task data");
        requireText(data, "task_id");
        String status = requireText(data, "status");
        require(TASK_STATUSES.contains(status), "invalid sys.task status: " + status);
        if (data.has("actions")) {
            require(data.get("actions").isArray(), "sys.task actions must be an array");
            for (JsonNode action : data.get("actions")) {
                require(TASK_ACTIONS.contains(action.asText()),
                        "invalid sys.task action: " + action.asText());
            }
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
