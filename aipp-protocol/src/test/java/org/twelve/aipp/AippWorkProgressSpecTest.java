package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("Host work-progress sys.* widgets")
class AippWorkProgressSpecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AippWorkProgressSpec spec = new AippWorkProgressSpec();

    @Test
    void sysTodoCanvasExampleIsValid() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.todo",
                  "data": {
                    "todo_list_id": "todo_turn_01J",
                    "owner": { "kind": "session", "id": "session-1" },
                    "status": "running",
                    "revision": 2,
                    "items": [
                      { "id": "inspect", "title": "Inspect routing", "status": "done" },
                      { "id": "test", "title": "Run focused tests", "status": "in_progress" }
                    ]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysTodoCanvas(canvas));
    }

    @Test
    void sysDelegationCanvasExampleIsValid() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.delegation",
                  "data": {
                    "delegation_id": "del_01J",
                    "status": "running",
                    "revision": 3,
                    "children": [
                      {
                        "child_id": "child_01J",
                        "task_id": "api-review",
                        "title": "Review API boundary",
                        "status": "running",
                        "activity": "Inspecting dispatch policy",
                        "iterations": 3
                      }
                    ],
                    "actions": ["inspect", "cancel"]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysDelegationCanvas(canvas));
    }

    @Test
    void sysTaskCanvasExampleIsValid() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.task",
                  "data": {
                    "task_id": "task_01J",
                    "status": "parked_waiting_client",
                    "title": "Prepare and publish report",
                    "current_step": 2,
                    "total_steps": 4,
                    "message": "Waiting for a connected client executor",
                    "actions": ["open_task_panel", "cancel"]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysTaskCanvas(canvas));
    }
}
