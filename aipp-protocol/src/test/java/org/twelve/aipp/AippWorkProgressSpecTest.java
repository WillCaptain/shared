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
    void sysWorkCanvasExampleIsValid() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.work",
                  "data": {
                    "work_id": "task_01J",
                    "status": "needs_review",
                    "runner_kind": "step_director",
                    "title": "Publish report",
                    "revision": 4,
                    "task_ui_session_id": "ui-task-1",
                    "actions": ["open_work_panel", "rerun", "skip", "abort", "cancel"]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysWorkCanvas(canvas));
    }
}
