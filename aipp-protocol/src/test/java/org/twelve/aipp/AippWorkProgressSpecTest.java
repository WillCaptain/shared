package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    "actions": ["open_work_panel", "rerun", "skip", "abort", "cancel"],
                    "items": [
                      { "id": "step-1", "title": "outline_grammar — index", "status": "done" },
                      { "id": "step-2", "title": "outline_parse", "status": "in_progress" }
                    ]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysWorkCanvas(canvas));
    }

    @Test
    void sysWorkAcceptsLongMonotonicRevisionAndShortResult() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.work",
                  "data": {
                    "work_id": "task_01J",
                    "status": "completed",
                    "runner_kind": "agent_child",
                    "revision": 1786690800123,
                    "result_summary": "Bounded result"
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysWorkCanvas(canvas));
    }

    @Test
    void sysWorkAcceptsNavigableWorkspaceThatNeedsApproval() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.work",
                  "data": {
                    "work_id": "task_01J",
                    "status": "running",
                    "runner_kind": "agent_child",
                    "revision": 5,
                    "actions": ["open_work_panel", "cancel"],
                    "workspaces": [{
                      "unit_id": "plan-dag",
                      "orchestration": "plan",
                      "ui_session_id": "ui-plan-1",
                      "status": "awaiting_approval",
                      "needs_attention": true,
                      "attention_reason": "approval_required"
                    }]
                  }
                }
                """);

        assertThatNoException().isThrownBy(() -> spec.assertValidSysWorkCanvas(canvas));
    }

    @Test
    void sysTodoRejectsListStatusOutsideLifecycleContract() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.todo",
                  "data": {
                    "todo_list_id": "todo_turn_01J",
                    "owner": { "kind": "session", "id": "session-1" },
                    "status": "blocked",
                    "revision": 2,
                    "items": [
                      { "id": "inspect", "status": "blocked" }
                    ]
                  }
                }
                """);
        assertThatThrownBy(() -> spec.assertValidSysTodoCanvas(canvas))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sysTodoAcceptsFailedWhenAgentStopped() throws Exception {
        JsonNode canvas = mapper.readTree("""
                {
                  "action": "replace",
                  "widget_type": "sys.todo",
                  "data": {
                    "todo_list_id": "todo_turn_01J",
                    "owner": { "kind": "session", "id": "session-1" },
                    "status": "failed",
                    "revision": 3,
                    "items": [
                      { "id": "inspect", "title": "Inspect routing", "status": "done" },
                      { "id": "write", "title": "Write file", "status": "failed" }
                    ]
                  }
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysTodoCanvas(canvas));
    }
}
