package org.twelve.aipp.contacts;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public, provider-neutral Contacts capability owned by Ones shared protocol. */
public final class ContactsCapabilitySpec {
    public static final String INTERFACE_TYPE="shared.contacts/v1";
    public static final String SEARCH_TOOL="contacts_search";
    public static final String RESOLVE_TOOL="contacts_resolve";
    public static final int SCHEMA_VERSION=1;
    public static final Set<String> STANDARD_TAGS=Set.of("friend","coworker");
    private ContactsCapabilitySpec(){}

    public static Map<String,Object> effect(String appId,String moduleUrl){
        if(appId==null||appId.isBlank())throw new IllegalArgumentException("app_id is required");
        if(moduleUrl==null||moduleUrl.isBlank())throw new IllegalArgumentException("module_url is required");
        return Map.of("type",INTERFACE_TYPE,"payload",Map.of(
                "schema_version",SCHEMA_VERSION,"app_id",appId.trim(),"module_url",moduleUrl.trim(),
                "operations",List.of(SEARCH_TOOL,RESOLVE_TOOL)));
    }

    public static void assertContact(Map<String,?> contact){
        if(contact==null||text(contact.get("user_id")).isBlank())throw new IllegalArgumentException("contact.user_id is required");
        if(text(contact.get("display_name")).isBlank())throw new IllegalArgumentException("contact.display_name is required");
        Object tags=contact.get("tags");if(tags!=null&&!(tags instanceof List<?>))throw new IllegalArgumentException("contact.tags must be an array");
    }
    private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
}
