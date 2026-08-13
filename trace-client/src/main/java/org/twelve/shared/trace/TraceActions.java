package org.twelve.shared.trace;

public final class TraceActions {
    private TraceActions() {}

    // Workspace
    public static final String WORKSPACE_SET = "workspace.set";
    public static final String WORKSPACE_SWITCH = "workspace.switch";
    public static final String ONBOARDING_OPEN = "onboarding.open";
    public static final String ONBOARDING_APPLY = "onboarding.apply";

    // Import
    public static final String IMPORT_FOLDER_PICK = "import.folder_pick";
    public static final String IMPORT_START = "import.start";
    public static final String IMPORT_CHUNK = "import.chunk";
    public static final String IMPORT_COMPLETE = "import.complete";
    public static final String IMPORT_CANCEL = "import.cancel";
    public static final String IMPORT_ROLLBACK = "import.rollback";

    // Sync / steward
    public static final String SYNC_PLAN = "sync.plan";
    public static final String SYNC_APPLY = "sync.apply";
    public static final String TRIAGE_PROPOSE = "triage.propose";
    public static final String TRIAGE_OPEN = "triage.open";
    public static final String TRIAGE_APPLY = "triage.apply";
    public static final String LAYOUT_APPLY = "layout.apply";

    // UI / host
    public static final String UI_CLICK = "ui.click";
    public static final String UI_BADGE_CLICK = "ui.badge_click";
    public static final String CANVAS_COLLAPSE = "canvas.collapse";
    public static final String CANVAS_EXPAND = "canvas.expand";
    public static final String SESSION_SWITCH = "session.switch";
    public static final String WIDGET_APPEND = "widget.append";
    public static final String WIDGET_DEDUP = "widget.dedup";
    public static final String TOOL_PROXY = "tool.proxy";
    public static final String TOOL_RESULT = "tool.result";
    public static final String WORLD_EVENT_CREATE = "world_event.create";
    public static final String WORLD_EVENT_STATUS = "world_event.status";
}
