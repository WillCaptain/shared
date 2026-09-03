package org.twelve.aipp.host;

/** Generic provider-neutral Host contribution interface identifiers and wire fields. */
public interface HostContributionInterfaceSpec {
    String TASK_ADORNMENT_TYPE = "shared.task-adornment.runtime/v1";
    String FLASH_RENDERER_TYPE = "shared.flash-renderer.runtime/v1";
    String HELP_CONTRIBUTION_TYPE = "shared.help-contribution.runtime/v1";
    String HELP_CONTRIBUTIONS_FIELD = "help_contributions";
    String HELP_TOPIC_FIELD = "topic";
    String HELP_MATCH_FIELD = "match";
    String HELP_TITLE_FIELD = "title";
    String HELP_SUMMARY_FIELD = "summary";
    String HELP_STEPS_FIELD = "steps";
    String HELP_ACTIONS_FIELD = "actions";

    String FIELD_TYPE = "type";
    String FIELD_PAYLOAD = "payload";
    String FIELD_SCHEMA_VERSION = "schema_version";
    String FIELD_APP_ID = "app_id";
    String FIELD_MODULE_URL = "module_url";
}
