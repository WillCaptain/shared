package org.twelve.aipp.collaboration;

import java.util.List;
import java.util.Map;

/** Authoritative, audience-filtered facts supplied to an agent collaboration turn. */
public record CollaborationContext(
        String schema,
        Session session,
        Principal actor,
        Principal owner,
        List<Member> members,
        List<Map<String, Object>> resourceBindings,
        List<Map<String, Object>> scopedGrants,
        Map<String, Object> providerCapabilities,
        String audience,
        String locale,
        String timeZone,
        Map<String, Object> awayPolicy,
        Map<String, Object> agentBudget,
        List<Map<String, Object>> recentMessages) {

    public static final String SCHEMA = "shared.collaboration-context/v1";

    public CollaborationContext {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported collaboration context schema");
        if (session == null || actor == null || owner == null) throw new IllegalArgumentException("session, actor, and owner are required");
        members = members == null ? List.of() : List.copyOf(members);
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
        scopedGrants = scopedGrants == null ? List.of() : List.copyOf(scopedGrants);
        providerCapabilities = providerCapabilities == null ? Map.of() : Map.copyOf(providerCapabilities);
        awayPolicy = awayPolicy == null ? Map.of() : Map.copyOf(awayPolicy);
        agentBudget = agentBudget == null ? Map.of() : Map.copyOf(agentBudget);
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    public record Session(String id, String kind, String parentId, String title, long revision, boolean leaf) {}
    public record Principal(String id, String name, String kind, String role, String ownerId) {}
    public record Member(Principal human, Principal ones, String membershipRole) {}
}
