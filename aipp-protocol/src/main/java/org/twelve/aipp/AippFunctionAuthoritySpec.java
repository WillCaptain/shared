package org.twelve.aipp;

/**
 * Provider-neutral function-authority wire contract.
 *
 * <p>This shared type intentionally contains constants only. Catalog ownership,
 * validation, grant evaluation, and persistence belong to the authority provider.
 */
public interface AippFunctionAuthoritySpec {

    String REGISTER_FUNCTION_TOOL = "register_function";
    String ASSIGN_FUNCTION_TOOL = "assign_function";
    String CHECK_FUNCTION_TOOL = "check_function";
    String CHECK_FUNCTIONS_TOOL = "check_functions";

    String FIELD_FUNCTION_ID = "function_id";
    String FIELD_APP_ID = "app_id";
    String FIELD_NAME = "name";
    String FIELD_KIND = "kind";
    String FIELD_GATES_APP = "gates_app";
    String FIELD_REQUIRES_AUTHORITY = "requires_authority";
    String FIELD_ALLOWED = "allowed";
}
