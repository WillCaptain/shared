package org.twelve.aipp.identity;

/**
 * Provider-neutral identity capability contract.
 *
 * <p>This type intentionally contains names only. Providers implement the capability and Hosts
 * implement transport, validation, and policy outside the shared protocol module.
 */
public interface AippIdentityContract {
    String GET_USER_TOOL_NAME = "get_user";
    String OK_FIELD = "ok";
    String USER_FIELD = "user";
    String USER_ID_FIELD = "id";
    String USER_NAME_FIELD = "name";
}
