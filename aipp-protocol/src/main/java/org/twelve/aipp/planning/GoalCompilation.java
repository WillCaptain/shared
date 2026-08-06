package org.twelve.aipp.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Wire model for {@code worldone.goal_compilation/v1}. */
public record GoalCompilation(
        @JsonProperty("schema_version") String schemaVersion,
        String mode,
        @JsonProperty("normalized_goal") String normalizedGoal,
        List<String> constraints,
        @JsonProperty("direct_query") DirectQuery directQuery,
        String question,
        List<String> missing,
        FreePlanDag dag) {

    public static final String SCHEMA_VERSION = "worldone.goal_compilation/v1";

    public GoalCompilation {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    public record DirectQuery(String primary, List<String> alternates) {
        public DirectQuery {
            alternates = alternates == null ? List.of() : List.copyOf(alternates);
        }
    }
}
